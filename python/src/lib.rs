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
use pyo3::prelude::*;
use pyo3::pybacked::PyBackedStr;
use pyo3::types::{PyList, PyString};
use std::collections::HashMap;
use std::fs;
use std::io::Read;
use std::sync::{Arc, Mutex};
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

        let computed: Py<PyAny> = match self.trie.stem_len_into(word, key_buf, u16_buf) {
            Some(_) => {
                decode_utf16_into(u16_buf, u8_buf);
                PyString::new_bound(py, u8_buf).into_any().unbind()
            }
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

    fn stem_batch_impl<'py>(
        &self,
        py: Python<'py>,
        words: &[PyBackedStr],
        fallback_to_original: bool,
    ) -> PyResult<Bound<'py, PyList>> {
        let mut key_buf: Vec<u16> = Vec::new();
        let mut u16_buf: Vec<u16> = Vec::new();
        let mut u8_buf = String::new();
        let list = PyList::empty_bound(py);

        // Misses remain cached as None so calls through the compatibility API
        // cannot change the existing stem/stem_batch missing-value contract.
        for w in words {
            let key: &str = w;
            let obj = self.stem_cached(py, key, &mut key_buf, &mut u16_buf, &mut u8_buf);
            if fallback_to_original && obj.bind(py).is_none() {
                list.append(PyString::new_bound(py, key))?;
            } else {
                list.append(obj.bind(py))?;
            }
        }
        Ok(list)
    }
}

#[pymethods]
impl StemmerCore {
    /// Compile a model from a gzipped TSV source dictionary.
    ///
    /// * `path` — path to either a gzipped TSV source dictionary
    ///   (`stem\tvariant1\tvariant2...` per line) OR a compiled `.rxc` trie
    ///   (Java-interoperable v7 format). The format is auto-detected.
    /// * `backward` — BACKWARD traversal (all languages except the
    ///   right-to-left fa/he/yi, which use FORWARD). Ignored for compiled input
    ///   (baked into the file).
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
        let trie = if serial::is_v7_stream(&decompressed) {
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
    fn stem_word(&self, py: Python<'_>, word: &str) -> Py<PyAny> {
        let obj = self.stem_cached(
            py,
            word,
            &mut Vec::new(),
            &mut Vec::new(),
            &mut String::new(),
        );
        if obj.bind(py).is_none() {
            PyString::new_bound(py, word).into_any().unbind()
        } else {
            obj
        }
    }

    fn stem_batch<'py>(
        &self,
        py: Python<'py>,
        words: Vec<PyBackedStr>,
    ) -> PyResult<Bound<'py, PyList>> {
        self.stem_batch_impl(py, &words, false)
    }

    /// PyStemmer-compatible batch API. Unrecognized words keep their position
    /// in the result and are returned unchanged.
    #[pyo3(name = "stemWords")]
    fn stem_words<'py>(
        &self,
        py: Python<'py>,
        words: Vec<PyBackedStr>,
    ) -> PyResult<Bound<'py, PyList>> {
        self.stem_batch_impl(py, &words, true)
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

    fn stem_all_batch(&self, words: Vec<PyBackedStr>) -> Vec<Vec<String>> {
        words.iter().map(|w| self.trie.stem_all(w)).collect()
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
