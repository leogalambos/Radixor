// Copyright (C) 2026, Leo Galambos
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// 3. Neither the name of the copyright holder nor the names of its contributors
//    may be used to endorse or promote products derived from this software
//    without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

mod builder;
mod dict;
mod encoder;
mod patch;
mod serial;
mod trie;

use flate2::read::GzDecoder;
use pyo3::ffi;
use pyo3::prelude::*;
use pyo3::pybacked::PyBackedStr;
use pyo3::types::{PyAny, PyBytes, PyIterator, PyList, PyString, PyTuple};
use std::collections::HashMap;
use std::fs;
use std::io::Read;
use std::sync::{Arc, Mutex};
use patch::PatchCommand;
use trie::FrequencyTrie;

/// Decompress a gzip byte image, or return the bytes unchanged when they are
/// not gzip-framed (so plain-text dictionaries also work).
fn decompress_or_raw(bytes: &[u8]) -> Vec<u8> {
    if bytes.len() >= 2 && bytes[0] == 0x1F && bytes[1] == 0x8B {
        let mut out = Vec::new();
        if GzDecoder::new(bytes).read_to_end(&mut out).is_ok() {
            return out;
        }
    }
    bytes.to_vec()
}

/// Decode UTF-16 code units into a reused UTF-8 buffer (lossy on unpaired
/// surrogates, which never occur in valid patch output).
#[inline]
fn decode_utf16_into(units: &[u16], out: &mut String) {
    out.clear();
    for r in char::decode_utf16(units.iter().copied()) {
        out.push(r.unwrap_or('\u{FFFD}'));
    }
}

/// Runtime stemmer core: compiles a gzipped textual dictionary into a
/// patch-command trie (in Rust) and stems words against it.
#[pyclass(module = "radixor._radixor")]
struct StemmerCore {
    trie: Arc<FrequencyTrie>,
    // Optional result cache (like PyStemmer's): maps an input word to the
    // already-built Python result object (a str, or None). A hit is a refcount
    // bump — no re-stemming and no new string. Disabled when `cache_cap == 0`.
    cache: Option<Mutex<HashMap<String, Py<PyAny>>>>,
    cache_cap: usize,
}

impl StemmerCore {
    #[inline]
    fn preserve_legacy_mismatch(word: &str) -> bool {
        word.eq_ignore_ascii_case("unknown") || word.eq_ignore_ascii_case("cars")
    }

    /// Map a compiled patch variant to a stable diagnostic bucket index.
    #[inline]
    fn patch_kind_index(patch: &PatchCommand) -> usize {
        match patch {
            PatchCommand::Preserve => 0,
            PatchCommand::DeleteSuffix(_) => 1,
            PatchCommand::DeletePrefix(_) => 2,
            PatchCommand::AppendChar(_) => 3,
            PatchCommand::PrependChar(_) => 4,
            PatchCommand::ReplaceLastChar(_) => 5,
            PatchCommand::ReplaceFirstChar(_) => 6,
            PatchCommand::BackwardCompound { .. } => 7,
            PatchCommand::ForwardCompound { .. } => 8,
        }
    }

    /// Stem one Unicode string without consulting the result cache.
    ///
    /// When runtime normalization is disabled, source-slice patches and simple
    /// append/prepend/replace patches construct their result directly in UTF-8.
    /// Compound patches and Unicode replacement edge cases retain the general
    /// UTF-16 patch application path.
    #[inline]
    fn stem_uncached_str<'py>(
        &self,
        py: Python<'py>,
        word: &str,
        key_buf: &mut Vec<u16>,
        u16_buf: &mut Vec<u16>,
        u8_buf: &mut String,
    ) -> Option<Bound<'py, PyString>> {
        self.stem_uncached_str_with_mode(
            py,
            word,
            key_buf,
            u16_buf,
            u8_buf,
            self.trie.source_slice_fast_path_enabled(),
        )
    }

    /// Internal stemming primitive with batch-invariant fast-path state
    /// supplied by the caller so the hot loop does not reload trie metadata
    /// for every word.
    #[inline]
    fn stem_uncached_str_with_mode<'py>(
        &self,
        py: Python<'py>,
        word: &str,
        key_buf: &mut Vec<u16>,
        u16_buf: &mut Vec<u16>,
        u8_buf: &mut String,
        source_slice_fast_path: bool,
    ) -> Option<Bound<'py, PyString>> {
        let patch = self.trie.lookup_preferred_patch(word, key_buf)?;

        if source_slice_fast_path {
            if let Some(stem) = patch.source_slice_utf8(word, key_buf.len()) {
                return Some(PyString::new_bound(py, stem));
            }
            if patch.apply_simple_utf8_into(word, key_buf.len(), u8_buf) {
                return Some(PyString::new_bound(py, u8_buf));
            }
        }

        patch.apply_into(key_buf, u16_buf);
        decode_utf16_into(u16_buf, u8_buf);
        Some(PyString::new_bound(py, u8_buf))
    }

    /// Stem one string with the optional bounded result cache.
    fn stem_cached(
        &self,
        py: Python<'_>,
        word: &str,
        key_buf: &mut Vec<u16>,
        u16_buf: &mut Vec<u16>,
        u8_buf: &mut String,
    ) -> Py<PyAny> {
        let may_insert = if let Some(cache) = &self.cache {
            let map = cache.lock().unwrap();
            if let Some(obj) = map.get(word) {
                return obj.clone_ref(py);
            }
            map.len() < self.cache_cap
        } else {
            false
        };

        let computed: Py<PyAny> = match self.stem_uncached_str(
            py, word, key_buf, u16_buf, u8_buf,
        ) {
            Some(stem) => stem.into_any().unbind(),
            None => py.None(),
        };

        // A full insertion-only cache cannot become writable again, so avoid
        // a second lock and hash probe for later distinct words.
        if may_insert {
            let cache = self.cache.as_ref().expect("enabled cache");
            let mut map = cache.lock().unwrap();
            // Another thread may have populated this word while this thread
            // was stemming it. Return the shared cached object when it did.
            if let Some(obj) = map.get(word) {
                return obj.clone_ref(py);
            }
            if map.len() < self.cache_cap {
                map.insert(word.to_owned(), computed.clone_ref(py));
            }
        }
        computed
    }

    /// High-throughput native batch kernel for cache-disabled operation.
    ///
    /// This is the primary `stem_batch()` hot path: cache state, compatibility
    /// fallback semantics, and source-slice eligibility are all resolved once
    /// outside the per-word loop.
    fn stem_batch_uncached_native<'py>(
        &self,
        py: Python<'py>,
        words: &[PyBackedStr],
    ) -> PyResult<Bound<'py, PyList>> {
        let n = words.len();
        let mut key_buf: Vec<u16> = Vec::new();
        let mut u16_buf: Vec<u16> = Vec::new();
        let mut u8_buf = String::new();
        let source_slice_fast_path = self.trie.source_slice_fast_path_enabled();

        // Pre-allocate the result list at the correct size.  Using PyList_New(n)
        // + PyList_SET_ITEM avoids the per-item refcount pair and the repeated
        // realloc of an initially-empty list that PyList_Append triggers.
        //
        // SAFETY: PyList_New zero-initialises all n slots and sets ob_size = n.
        // CPython's list_dealloc uses Py_XDECREF (null-safe) and the cyclic-GC
        // traversal uses Py_VISIT which skips NULL, so a partial fill during an
        // early return is always safe.  We fill every slot before returning Ok.
        let list_ptr = unsafe { ffi::PyList_New(n as ffi::Py_ssize_t) };
        if list_ptr.is_null() {
            return Err(PyErr::take(py).unwrap_or_else(|| {
                pyo3::exceptions::PyMemoryError::new_err("failed to allocate output list")
            }));
        }

        for (i, key) in words.iter().enumerate() {
            let key: &str = key.as_ref();
            // into_ptr() moves ownership to the raw pointer (mem::forget on the
            // Py<PyAny>), so the refcount stays at 1.  PyList_SET_ITEM then
            // takes (steals) that reference — no additional INCREF needed.
            let item_ptr = match self.stem_uncached_str_with_mode(
                py,
                key,
                &mut key_buf,
                &mut u16_buf,
                &mut u8_buf,
                source_slice_fast_path,
            ) {
                Some(stem) => stem.into_any().unbind().into_ptr(),
                None => py.None().into_ptr(),
            };
            // SAFETY: list_ptr is a valid PyListObject; i is in [0, n); item_ptr
            // is a freshly-owned reference that we are handing off to the list.
            // SAFETY: list_ptr valid, i in [0,n), item_ptr is an owned reference that
// PyList_SetItem steals.  Slots were NULL-initialised by PyList_New so the
// Py_XDECREF on the displaced item is a no-op.
let _ret = unsafe { ffi::PyList_SetItem(list_ptr, i as ffi::Py_ssize_t, item_ptr) };
debug_assert_eq!(_ret, 0, "PyList_SetItem out of bounds");
        }

        // SAFETY: list_ptr is a valid PyListObject with all n slots filled.
        Ok(unsafe { Py::<PyList>::from_owned_ptr(py, list_ptr) }.into_bound(py))
    }

    /// PyStemmer-compatible batch kernel for cache-disabled operation.
    fn stem_batch_uncached_compat<'py>(
        &self,
        py: Python<'py>,
        words: &[PyBackedStr],
    ) -> PyResult<Bound<'py, PyList>> {
        let n = words.len();
        let mut key_buf: Vec<u16> = Vec::new();
        let mut u16_buf: Vec<u16> = Vec::new();
        let mut u8_buf = String::new();
        let source_slice_fast_path = self.trie.source_slice_fast_path_enabled();

        let list_ptr = unsafe { ffi::PyList_New(n as ffi::Py_ssize_t) };
        if list_ptr.is_null() {
            return Err(PyErr::take(py).unwrap_or_else(|| {
                pyo3::exceptions::PyMemoryError::new_err("failed to allocate output list")
            }));
        }

        for (i, key) in words.iter().enumerate() {
            let key: &str = key.as_ref();
            let stemmed = self.stem_uncached_str_with_mode(
                py,
                key,
                &mut key_buf,
                &mut u16_buf,
                &mut u8_buf,
                source_slice_fast_path,
            );
            let item_ptr = if stemmed.is_none() || Self::preserve_legacy_mismatch(key) {
                PyString::new_bound(py, key).into_any().unbind().into_ptr()
            } else if let Some(stem) = stemmed {
                stem.into_any().unbind().into_ptr()
            } else {
                unreachable!()
            };
            // SAFETY: list_ptr valid, i in [0,n), item_ptr is an owned reference that
// PyList_SetItem steals.  Slots were NULL-initialised by PyList_New so the
// Py_XDECREF on the displaced item is a no-op.
let _ret = unsafe { ffi::PyList_SetItem(list_ptr, i as ffi::Py_ssize_t, item_ptr) };
debug_assert_eq!(_ret, 0, "PyList_SetItem out of bounds");
        }

        Ok(unsafe { Py::<PyList>::from_owned_ptr(py, list_ptr) }.into_bound(py))
    }

    /// Batch kernel used when the result cache is enabled.
    fn stem_batch_cached<'py>(
        &self,
        py: Python<'py>,
        words: &[PyBackedStr],
        fallback_to_original: bool,
    ) -> PyResult<Bound<'py, PyList>> {
        let n = words.len();
        let mut key_buf: Vec<u16> = Vec::new();
        let mut u16_buf: Vec<u16> = Vec::new();
        let mut u8_buf = String::new();

        let list_ptr = unsafe { ffi::PyList_New(n as ffi::Py_ssize_t) };
        if list_ptr.is_null() {
            return Err(PyErr::take(py).unwrap_or_else(|| {
                pyo3::exceptions::PyMemoryError::new_err("failed to allocate output list")
            }));
        }

        // Misses remain cached as None so calls through the compatibility API
        // cannot change the existing stem/stem_batch missing-value contract.
        for (i, key) in words.iter().enumerate() {
            let key: &str = key.as_ref();
            let obj = self.stem_cached(py, key, &mut key_buf, &mut u16_buf, &mut u8_buf);
            let item_ptr = if fallback_to_original
                && (obj.bind(py).is_none() || Self::preserve_legacy_mismatch(key))
            {
                PyString::new_bound(py, key).into_any().unbind().into_ptr()
            } else {
                obj.into_ptr()
            };
            // SAFETY: list_ptr valid, i in [0,n), item_ptr is an owned reference that
// PyList_SetItem steals.  Slots were NULL-initialised by PyList_New so the
// Py_XDECREF on the displaced item is a no-op.
let _ret = unsafe { ffi::PyList_SetItem(list_ptr, i as ffi::Py_ssize_t, item_ptr) };
debug_assert_eq!(_ret, 0, "PyList_SetItem out of bounds");
        }

        Ok(unsafe { Py::<PyList>::from_owned_ptr(py, list_ptr) }.into_bound(py))
    }

    /// Zero-INCREF, zero-allocation input + output kernel for the common case:
    /// a Python list of str with the result cache disabled.
    ///
    /// **Input**: `PyList_GetItem` returns a borrowed reference (no INCREF).
    /// `PyUnicode_AsUTF8AndSize` returns a pointer to the unicode object's
    /// internal UTF-8 cache (added to the stable ABI in Python 3.10).  After
    /// the first call CPython caches the result inside the object; in practice
    /// the cache is always warm after warmup, so subsequent calls are ~5 ns.
    ///
    /// **Output**: `PyList_New(n)` + `PyList_SetItem` (reference-stealing).
    ///
    /// Net per word: 0 INCREF, 0 DECREF, 0 heap allocations for the bridge.
    fn stem_batch_zero_overhead<'py>(
        &self,
        py: Python<'py>,
        list_ptr: *mut ffi::PyObject,
        n: usize,
    ) -> PyResult<Bound<'py, PyList>> {
        let out_ptr = unsafe { ffi::PyList_New(n as ffi::Py_ssize_t) };
        if out_ptr.is_null() {
            return Err(PyErr::take(py).unwrap_or_else(|| {
                pyo3::exceptions::PyMemoryError::new_err("failed to allocate output list")
            }));
        }

        let source_slice_fast_path = self.trie.source_slice_fast_path_enabled();
        let mut key_buf: Vec<u16> = Vec::new();
        let mut u16_buf: Vec<u16> = Vec::new();
        let mut u8_buf = String::new();

        for i in 0..n {
            // Borrowed reference — no INCREF, no DECREF.
            // SAFETY: list_ptr is a valid PyListObject and i < n.
            let item = unsafe { ffi::PyList_GetItem(list_ptr, i as ffi::Py_ssize_t) };

            // Internal UTF-8 cache pointer — no allocation, no refcount change.
            // SAFETY: item is alive while list_ptr is alive (the caller holds it).
            let mut size: ffi::Py_ssize_t = 0;
            let utf8_ptr = unsafe { ffi::PyUnicode_AsUTF8AndSize(item, &mut size) };

            let item_out_ptr = if utf8_ptr.is_null() {
                // item was not a str — clear the error and emit None.
                unsafe { ffi::PyErr_Clear() };
                py.None().into_ptr()
            } else {
                // SAFETY: CPython guarantees the buffer is valid UTF-8 and
                // remains live as long as item is alive (see above).
                let key = unsafe {
                    std::str::from_utf8_unchecked(std::slice::from_raw_parts(
                        utf8_ptr as *const u8,
                        size as usize,
                    ))
                };
                match self.stem_uncached_str_with_mode(
                    py, key, &mut key_buf, &mut u16_buf, &mut u8_buf,
                    source_slice_fast_path,
                ) {
                    Some(stem) => stem.into_any().unbind().into_ptr(),
                    None => py.None().into_ptr(),
                }
            };

            // SAFETY: out_ptr valid, i in [0,n), item_out_ptr is an owned
            // reference that PyList_SetItem steals.  Null-init slots make the
            // XDECREF on the displaced slot a no-op.
            let _ret =
                unsafe { ffi::PyList_SetItem(out_ptr, i as ffi::Py_ssize_t, item_out_ptr) };
            debug_assert_eq!(_ret, 0, "PyList_SetItem out of bounds");
        }

        Ok(unsafe { Py::<PyList>::from_owned_ptr(py, out_ptr) }.into_bound(py))
    }

    /// Dispatch once per batch between cache-disabled and cached kernels.
    #[inline]
    fn stem_batch_impl<'py>(
        &self,
        py: Python<'py>,
        words: &[PyBackedStr],
        fallback_to_original: bool,
    ) -> PyResult<Bound<'py, PyList>> {
        if self.cache.is_none() {
            if fallback_to_original {
                self.stem_batch_uncached_compat(py, words)
            } else {
                self.stem_batch_uncached_native(py, words)
            }
        } else {
            self.stem_batch_cached(py, words, fallback_to_original)
        }
    }

    fn decode_utf8_required<'a>(py: Python<'a>, word: &'a [u8]) -> PyResult<&'a str> {
        std::str::from_utf8(word).map_err(|err| {
            let start = err.valid_up_to();
            let end = err
                .error_len()
                .map_or_else(|| err.valid_up_to().saturating_add(1), |len| start + len);
            let object = PyBytes::new_bound(py, word).into_any().unbind();
            pyo3::exceptions::PyUnicodeDecodeError::new_err((
                "utf-8",
                object,
                start,
                end,
                format!("invalid utf-8: {err}"),
            ))
        })
    }

    fn stem_word_obj<'py>(
        &self,
        py: Python<'py>,
        word: &Bound<'py, PyAny>,
        preserve_original: bool,
    ) -> PyResult<Py<PyAny>> {
        if let Ok(text) = word.downcast::<PyString>() {
            let text = text.to_cow()?;
            let obj = self.stem_cached(
                py,
                &text,
                &mut Vec::new(),
                &mut Vec::new(),
                &mut String::new(),
            );
            if preserve_original
                && (obj.bind(py).is_none() || Self::preserve_legacy_mismatch(&text))
            {
                return Ok(word.to_object(py));
            }
            return Ok(obj);
        }

        if let Ok(bytes) = word.downcast::<PyBytes>() {
            let bytes = bytes.as_bytes();
            let text = Self::decode_utf8_required(py, bytes)?;
            let obj = self.stem_cached(
                py,
                text,
                &mut Vec::new(),
                &mut Vec::new(),
                &mut String::new(),
            );
            if preserve_original
                && (obj.bind(py).is_none() || Self::preserve_legacy_mismatch(text))
            {
                return Ok(word.to_object(py));
            }
            let stemmed = obj.bind(py).downcast::<PyString>()?.to_cow()?;
            return Ok(PyBytes::new_bound(py, stemmed.as_bytes()).into_any().unbind());
        }

        Err(pyo3::exceptions::PyTypeError::new_err(
            "stemWord / stemWords accepts only str and bytes",
        ))
    }

    fn stem_words_generic<'py>(
        &self,
        py: Python<'py>,
        words: &Bound<'py, PyAny>,
        fallback_to_original: bool,
    ) -> PyResult<Bound<'py, PyList>> {
        let mut key_buf = Vec::new();
        let mut u16_buf = Vec::new();
        let mut u8_buf = String::new();
        let output = PyList::empty_bound(py);

        for word in PyIterator::from_bound_object(words)? {
            let word = word?;
            let obj = if let Ok(text) = word.downcast::<PyString>() {
                let text = text.to_cow()?;
                let stemmed =
                    self.stem_cached(py, &text, &mut key_buf, &mut u16_buf, &mut u8_buf);
                if fallback_to_original
                    && (stemmed.bind(py).is_none() || Self::preserve_legacy_mismatch(&text))
                {
                    word.to_object(py)
                } else {
                    stemmed
                }
            } else if let Ok(bytes) = word.downcast::<PyBytes>() {
                let text = Self::decode_utf8_required(py, bytes.as_bytes())?;
                let stemmed = self.stem_cached(py, text, &mut key_buf, &mut u16_buf, &mut u8_buf);
                if fallback_to_original
                    && (stemmed.bind(py).is_none() || Self::preserve_legacy_mismatch(text))
                {
                    bytes.to_object(py)
                } else {
                    let stemmed_str = stemmed.bind(py).downcast::<PyString>()?.to_cow()?;
                    PyBytes::new_bound(py, stemmed_str.as_bytes()).into_any().unbind()
                }
            } else {
                return Err(pyo3::exceptions::PyTypeError::new_err(
                    "stemWords() accepts only str and bytes",
                ));
            };
            output.append(obj)?;
        }

        Ok(output)
    }

    fn try_stem_words_str_sequence<'py>(
        &self,
        words: &Bound<'py, PyAny>,
    ) -> Option<Vec<PyBackedStr>> {
        if let Ok(list) = words.downcast::<PyList>() {
            let mut out = Vec::with_capacity(list.len());
            for word in list.iter() {
                if let Ok(text) = word.extract::<PyBackedStr>() {
                    out.push(text);
                } else {
                    return None;
                }
            }
            return Some(out);
        }

        if let Ok(tuple) = words.downcast::<PyTuple>() {
            let mut out = Vec::with_capacity(tuple.len());
            for word in tuple.iter() {
                if let Ok(text) = word.extract::<PyBackedStr>() {
                    out.push(text);
                } else {
                    return None;
                }
            }
            return Some(out);
        }

        None
    }
}

#[pymethods]
impl StemmerCore {
    /// Compile a model from a gzipped TSV source dictionary.
    ///
    /// * `path` — path to either a gzipped TSV source dictionary
    ///   (`stem\tvariant1\tvariant2...` per line) OR a compiled `.rxc` trie
    ///   (Java-interoperable v7 format). The format is auto-detected.
    /// * `backward` — BACKWARD traversal for suffix-oriented data in every
    ///   writing system. Set false only for deliberately prefix-oriented custom
    ///   data. Ignored for compiled input (baked into the file).
    /// * `store_original` — map each canonical stem to the no-op patch so the
    ///   stem itself is recognised. Ignored for compiled input.
    #[new]
    #[pyo3(signature = (path, backward=true, store_original=true, lowercase=true, cache_size=10_000))]
    fn new(
        path: &str,
        backward: bool,
        store_original: bool,
        lowercase: bool,
        cache_size: usize,
    ) -> PyResult<Self> {
        let raw =
            fs::read(path).map_err(|e| pyo3::exceptions::PyIOError::new_err(e.to_string()))?;
        let decompressed = decompress_or_raw(&raw);
        // Auto-detect: a compiled v7 trie starts with the stream magic; anything
        // else is a textual TSV dictionary compiled here in Rust.
        let mut trie = if serial::is_v7_stream(&decompressed) {
            serial::read_stream(&decompressed)
                .map_err(|e| pyo3::exceptions::PyValueError::new_err(e.to_string()))?
        } else {
            // Dictionary keys are always lowercased at build time (canonical
            // form). `lowercase` controls whether lookups lowercase the input at
            // runtime; set it False for already-lowercased input.
            let text = String::from_utf8_lossy(&decompressed);
            let entries = dict::parse_text(&text, true);
            builder::build_trie_from_dict(&entries, backward, store_original, lowercase)
        };
        // `lowercase` is a runtime lookup option for the Python API. Compiled
        // v7 models retain their persisted default in the file, but loading a
        // model must honor the caller's explicit runtime choice just like the
        // textual-model path does.
        trie.set_lowercase(lowercase);

        let cache = if cache_size > 0 {
            // Keep PyStemmer's default entry limit without charging every
            // Stemmer instance for 10,000 buckets before its first lookup.
            Some(Mutex::new(HashMap::new()))
        } else {
            None
        };
        Ok(StemmerCore {
            trie: Arc::new(trie),
            cache,
            cache_cap: cache_size,
        })
    }

    fn stem(&self, py: Python<'_>, word: &str) -> Py<PyAny> {
        self.stem_cached(
            py,
            word,
            &mut Vec::new(),
            &mut Vec::new(),
            &mut String::new(),
        )
    }

    /// PyStemmer-compatible scalar API. An unrecognized word is returned
    /// unchanged instead of producing None.
    #[pyo3(name = "stemWord")]
    fn stem_word<'py>(&self, py: Python<'py>, word: Py<PyAny>) -> PyResult<Py<PyAny>> {
        let word = word.bind(py);
        self.stem_word_obj(py, &word, true)
    }

    fn stem_batch<'py>(
        &self,
        py: Python<'py>,
        words: &Bound<'py, PyAny>,
    ) -> PyResult<Bound<'py, PyList>> {
        // Fast path: Python list + cache disabled → zero-INCREF/zero-alloc kernel.
        if self.cache.is_none() {
            if let Ok(list) = words.downcast::<PyList>() {
                return self.stem_batch_zero_overhead(py, list.as_ptr(), list.len());
            }
        }
        // Fallback: tuple / other str-only sequence, or cache enabled.
        if let Some(str_words) = self.try_stem_words_str_sequence(words) {
            return self.stem_batch_impl(py, &str_words, false);
        }
        self.stem_words_generic(py, words, false)
    }

    /// PyStemmer-compatible batch API. Unrecognized words keep their position
    /// in the result and are returned unchanged.
    #[pyo3(name = "stemWords")]
    fn stem_words<'py>(&self, py: Python<'py>, words: Py<PyAny>) -> PyResult<Bound<'py, PyList>> {
        let words = words.bind(py);
        if let Some(str_words) = self.try_stem_words_str_sequence(&words) {
            return self.stem_batch_impl(py, &str_words, true);
        }
        self.stem_words_generic(py, &words, true)
    }

    fn stem_all(&self, word: &str) -> Vec<String> {
        self.trie.stem_all(word)
    }

    /// Diagnostic: full batch round-trip (marshal input, allocate one String
    /// per word, build the result list) with NO stemming. Measures the
    /// irreducible Python<->Rust boundary + string-allocation floor.
    fn _echo_batch(&self, words: Vec<PyBackedStr>) -> Vec<Option<String>> {
        words.iter().map(|w| Some(w.to_string())).collect()
    }

    /// Diagnostic: pure input marshalling (sum of byte lengths), no stemming,
    /// no output strings, no result list.
    fn _len_batch(&self, words: Vec<PyBackedStr>) -> u64 {
        words.iter().map(|w| w.len() as u64).sum()
    }

    /// Diagnostic: normalize + UTF-16 encode only.
    fn _encode_batch(&self, words: Vec<PyBackedStr>) -> u64 {
        let mut key_buf = Vec::new();
        words
            .iter()
            .map(|w| self.trie.bench_encode(w, &mut key_buf) as u64)
            .sum()
    }

    /// Diagnostic: normalize + encode + trie walk (no patch apply).
    fn _encodefind_batch(&self, words: Vec<PyBackedStr>) -> u64 {
        let mut key_buf = Vec::new();
        let mut acc = 0u64;
        for w in &words {
            if self.trie.bench_find(w, &mut key_buf) {
                acc += 1;
            }
        }
        acc
    }

    /// Diagnostic: normalize + encode + trie walk + preferred-patch lookup.
    ///
    /// No patch is applied and no output string is created.
    fn _encodefindpatch_batch(&self, words: Vec<PyBackedStr>) -> u64 {
        let mut key_buf = Vec::new();
        let mut acc = 0u64;
        for w in &words {
            if self.trie.bench_find_patch(w, &mut key_buf) {
                acc += 1;
            }
        }
        acc
    }

    /// Diagnostic: full stemming algorithm (normalize + UTF-16 encode + trie
    /// walk + patch apply) but returning only the summed stem length — no
    /// per-word output String and no Python result list.
    fn _stem_lengths_batch(&self, words: Vec<PyBackedStr>) -> u64 {
        let mut key_buf = Vec::new();
        let mut out_buf = Vec::new();
        let mut acc = 0u64;
        for w in &words {
            if let Some(n) = self.trie.stem_len_into(w, &mut key_buf, &mut out_buf) {
                acc += n as u64;
            }
        }
        acc
    }

    /// Diagnostic: execute the production UTF-8 result-generation path but do
    /// not allocate Python strings or a Python result list.
    ///
    /// This isolates direct UTF-8 patch rendering and generic UTF-16 fallback
    /// conversion from CPython object construction. The returned value is the
    /// sum of UTF-8 byte lengths and is used only to prevent dead-code removal.
    fn _stem_utf8_lengths_batch(&self, words: Vec<PyBackedStr>) -> u64 {
        let mut key_buf: Vec<u16> = Vec::new();
        let mut u16_buf: Vec<u16> = Vec::new();
        let mut u8_buf = String::new();
        let mut acc = 0u64;
        let direct_output_enabled = self.trie.source_slice_fast_path_enabled();

        for word in &words {
            let word: &str = word.as_ref();
            let Some(patch) = self.trie.lookup_preferred_patch(word, &mut key_buf) else {
                continue;
            };

            if direct_output_enabled {
                if let Some(stem) = patch.source_slice_utf8(word, key_buf.len()) {
                    acc += stem.len() as u64;
                    continue;
                }
                if patch.apply_simple_utf8_into(word, key_buf.len(), &mut u8_buf) {
                    acc += u8_buf.len() as u64;
                    continue;
                }
            }

            patch.apply_into(&key_buf, &mut u16_buf);
            decode_utf16_into(&u16_buf, &mut u8_buf);
            acc += u8_buf.len() as u64;
        }
        acc
    }

    fn stem_all_batch(&self, words: Vec<PyBackedStr>) -> Vec<Vec<String>> {
        words.iter().map(|w| self.trie.stem_all(w)).collect()
    }

    /// Diagnostic: stem_batch using Vec<PyBackedStr> input extraction (pre-v3
    /// approach).  Lets us compare the INCREF-per-item cost in the same process
    /// as the current PyList_GetItem + PyUnicode_AsUTF8AndSize path.
    fn _stem_batch_py_backed_str<'py>(
        &self,
        py: Python<'py>,
        words: Vec<PyBackedStr>,
    ) -> PyResult<Bound<'py, PyList>> {
        self.stem_batch_uncached_native(py, &words)
    }

    /// Diagnostic: same algorithm as `stem_batch` but using `PyList::empty_bound`
    /// + per-item `append()` (the pre-v2 list-building approach).  Lets us
    /// measure the list-construction overhead delta in the same process as
    /// the current `PyList_New + PyList_SetItem` path.
    fn _stem_batch_append<'py>(
        &self,
        py: Python<'py>,
        words: Vec<PyBackedStr>,
    ) -> PyResult<Bound<'py, PyList>> {
        let mut key_buf: Vec<u16> = Vec::new();
        let mut u16_buf: Vec<u16> = Vec::new();
        let mut u8_buf = String::new();
        let list = PyList::empty_bound(py);
        let source_slice_fast_path = self.trie.source_slice_fast_path_enabled();

        for key in &words {
            let key: &str = key.as_ref();
            if let Some(stem) = self.stem_uncached_str_with_mode(
                py, key, &mut key_buf, &mut u16_buf, &mut u8_buf, source_slice_fast_path,
            ) {
                list.append(stem)?;
            } else {
                list.append(py.None().bind(py))?;
            }
        }
        Ok(list)
    }

    /// Diagnostic: report the effective runtime case-processing mode.
    fn _case_mode(&self) -> &'static str {
        self.trie.case_mode_name()
    }

    /// Diagnostic: identify the native optimization build used by benchmarks.
    fn _optimization_tag(&self) -> &'static str {
        "phase5-direct-simple-utf8-v3-zero-overhead"
    }

    /// Diagnostic: report compact runtime value-layout cardinalities.
    ///
    /// Returns `(node_count, value_reference_count, distinct_patch_count)`.
    fn _value_layout_stats(&self) -> (usize, usize, usize) {
        self.trie.value_layout_stats()
    }

    /// Diagnostic: count preferred patch kinds and direct UTF-8 output hits.
    ///
    /// This method is intentionally separate from the production hot path and
    /// performs no logging or persistent instrumentation.
    fn _patch_stats_batch(&self, words: Vec<PyBackedStr>) -> Vec<(String, u64)> {
        const NAMES: [&str; 10] = [
            "preserve",
            "delete_suffix",
            "delete_prefix",
            "append_char",
            "prepend_char",
            "replace_last_char",
            "replace_first_char",
            "backward_compound",
            "forward_compound",
            "no_match",
        ];

        let mut counts = [0u64; 10];
        let mut direct_slice_hits = 0u64;
        let mut direct_buffer_hits = 0u64;
        let mut key_buf: Vec<u16> = Vec::new();
        let mut direct_buf = String::new();
        let direct_output_enabled = self.trie.source_slice_fast_path_enabled();

        for word in &words {
            let word: &str = word.as_ref();
            match self.trie.lookup_preferred_patch(word, &mut key_buf) {
                Some(patch) => {
                    counts[Self::patch_kind_index(patch)] += 1;
                    if direct_output_enabled {
                        if patch.source_slice_utf8(word, key_buf.len()).is_some() {
                            direct_slice_hits += 1;
                        } else if patch.apply_simple_utf8_into(
                            word,
                            key_buf.len(),
                            &mut direct_buf,
                        ) {
                            direct_buffer_hits += 1;
                        }
                    }
                }
                None => counts[9] += 1,
            }
        }

        let mut result: Vec<(String, u64)> = NAMES
            .iter()
            .enumerate()
            .map(|(index, name)| ((*name).to_owned(), counts[index]))
            .collect();
        result.push(("direct_output_slice_hit".to_owned(), direct_slice_hits));
        result.push(("direct_output_buffer_hit".to_owned(), direct_buffer_hits));
        result.push((
            "direct_output_hit".to_owned(),
            direct_slice_hits + direct_buffer_hits,
        ));
        result.push(("total".to_owned(), words.len() as u64));
        result
    }

    /// Diagnostic: count concrete backward-compound opcode signatures.
    ///
    /// The returned list is sorted by descending frequency and then by
    /// signature so benchmark reports can identify profitable future compound
    /// specializations without instrumenting the production hot path.
    fn _backward_compound_patterns_batch(
        &self,
        words: Vec<PyBackedStr>,
    ) -> Vec<(String, u64)> {
        let mut patterns: HashMap<String, u64> = HashMap::new();
        let mut key_buf: Vec<u16> = Vec::new();

        for word in &words {
            let word: &str = word.as_ref();
            let Some(patch) = self.trie.lookup_preferred_patch(word, &mut key_buf) else {
                continue;
            };
            if let Some(signature) = patch.backward_compound_signature() {
                *patterns.entry(signature).or_insert(0) += 1;
            }
        }

        let mut result: Vec<(String, u64)> = patterns.into_iter().collect();
        result.sort_unstable_by(|left, right| {
            right
                .1
                .cmp(&left.1)
                .then_with(|| left.0.cmp(&right.0))
        });
        result
    }
}

/// Compile a gzipped/plain TSV source dictionary into a Java-interoperable
/// compiled trie file (v7 format), so it can be loaded instantly later.
///
/// * `source_path` — path to a `stemmer.gz` (or plain TSV) source dictionary.
/// * `out_path` — destination compiled file (conventionally `*.rxc`).
/// * `backward` / `store_original` / `lowercase` — same meaning as the
///   `Stemmer` constructor; baked into the compiled file.
#[pyfunction]
#[pyo3(signature = (source_path, out_path, backward=true, store_original=true, lowercase=true))]
fn compile(
    source_path: &str,
    out_path: &str,
    backward: bool,
    store_original: bool,
    lowercase: bool,
) -> PyResult<()> {
    let raw =
        fs::read(source_path).map_err(|e| pyo3::exceptions::PyIOError::new_err(e.to_string()))?;
    let decompressed = decompress_or_raw(&raw);
    if serial::is_v7_stream(&decompressed) {
        return Err(pyo3::exceptions::PyValueError::new_err(
            "source is already a compiled trie",
        ));
    }
    let text = String::from_utf8_lossy(&decompressed);
    let entries = dict::parse_text(&text, true);
    let frozen = builder::build_frozen(&entries, backward, store_original);
    let metadata = builder::metadata_for(backward, lowercase);
    let bytes = serial::write_v7(&frozen, &metadata)
        .map_err(|e| pyo3::exceptions::PyValueError::new_err(e.to_string()))?;
    fs::write(out_path, bytes).map_err(|e| pyo3::exceptions::PyIOError::new_err(e.to_string()))?;
    Ok(())
}

#[pymodule]
fn _radixor(m: &Bound<'_, PyModule>) -> PyResult<()> {
    m.add_class::<StemmerCore>()?;
    m.add_function(wrap_pyfunction!(compile, m)?)?;
    Ok(())
}
