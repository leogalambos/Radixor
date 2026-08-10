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

use crate::patch::PatchCommand;
use std::borrow::Cow;
use std::sync::Arc;
use unicode_general_category::{get_general_category, GeneralCategory};
use unicode_normalization::UnicodeNormalization;

#[derive(Debug, Clone)]
pub enum TraversalDirection {
    Backward,
    Forward,
}

#[derive(Debug, Clone)]
pub enum CaseMode {
    LowercaseWithLocaleRoot,
    AsIs,
}

#[derive(Debug, Clone)]
pub enum DiacriticMode {
    AsIs,
    Remove,
}

#[derive(Debug, Clone)]
pub struct TrieMetadata {
    pub traversal: TraversalDirection,
    pub case_mode: CaseMode,
    pub diacritic_mode: DiacriticMode,
}

/// Compiled patch-command trie in a flat, cache-friendly CSR layout.
///
/// Instead of a graph of heap-allocated, reference-counted nodes (which forces
/// a pointer chase and a likely cache miss at every character step), the whole
/// trie is stored as a handful of contiguous arrays indexed by node id:
///
/// * `edge_start[i] .. edge_start[i+1]` slices `edge_labels` / `edge_targets`
///   for node `i` (labels sorted ascending, so child lookup is a binary search
///   over a contiguous, cache-hot slice — no pointer chasing, no atomics),
/// * `accepts[i]` marks a contracted accepting leaf,
/// * `value_start[i] .. value_start[i+1]` slices `values` (best value first).
///
/// Node 0 is the root. Shared (deduplicated) subtrees simply reference the same
/// node id, so structural sharing from reduction is preserved without `Arc`.
pub struct FrequencyTrie {
    edge_start: Vec<u32>,
    edge_labels: Vec<u16>,
    edge_targets: Vec<u32>,
    accepts: Vec<bool>,
    value_start: Vec<u32>,
    values: Vec<Arc<PatchCommand>>,
    // Adaptive child lookup (mirrors the Java CompiledNode fanout strategy):
    // high-fanout nodes whose child labels span a small contiguous range get a
    // dense direct-index table (O(1) child access); sparse nodes fall back to
    // binary search over `edge_labels`. A node `i` is dense iff
    // `dense_start[i+1] > dense_start[i]`; then `dense_targets[dense_start[i] +
    // (label - dense_base[i])]` holds `child_id + 1` (0 = no such edge).
    dense_start: Vec<u32>,
    dense_base: Vec<u16>,
    dense_targets: Vec<u32>,
    pub metadata: TrieMetadata,
}

impl FrequencyTrie {
    #[allow(clippy::too_many_arguments)]
    pub fn new(
        edge_start: Vec<u32>,
        edge_labels: Vec<u16>,
        edge_targets: Vec<u32>,
        accepts: Vec<bool>,
        value_start: Vec<u32>,
        values: Vec<Arc<PatchCommand>>,
        dense_start: Vec<u32>,
        dense_base: Vec<u16>,
        dense_targets: Vec<u32>,
        metadata: TrieMetadata,
    ) -> Self {
        FrequencyTrie {
            edge_start,
            edge_labels,
            edge_targets,
            accepts,
            value_start,
            values,
            dense_start,
            dense_base,
            dense_targets,
            metadata,
        }
    }

    /// Normalize a lookup key (used by the rare diacritic-removal path and by
    /// `stem_all`). Borrows the input when no transformation is needed.
    fn normalize_key<'a>(&self, word: &'a str) -> Cow<'a, str> {
        let lowered: Cow<'a, str> =
            if matches!(self.metadata.case_mode, CaseMode::LowercaseWithLocaleRoot)
                && word.chars().any(|c| c.is_uppercase())
            {
                Cow::Owned(word.to_lowercase())
            } else {
                Cow::Borrowed(word)
            };
        if matches!(self.metadata.diacritic_mode, DiacriticMode::Remove) {
            Cow::Owned(strip_diacritics(&lowered))
        } else {
            lowered
        }
    }

    /// Encode the normalized lookup key into `key_buf` in a single pass over the
    /// input: lowercasing (when configured) is folded into the UTF-16 encoding
    /// so the UTF-8 input is decoded only once and no intermediate `String` is
    /// allocated. The diacritic-removal path (unused by the bundled models)
    /// falls back to the general `normalize_key`.
    #[inline]
    fn encode_key(&self, word: &str, key_buf: &mut Vec<u16>) {
        key_buf.clear();
        if matches!(self.metadata.diacritic_mode, DiacriticMode::Remove) {
            let normalized = self.normalize_key(word);
            key_buf.extend(normalized.encode_utf16());
            return;
        }
        if matches!(self.metadata.case_mode, CaseMode::LowercaseWithLocaleRoot) {
            let mut unit = [0u16; 2];
            for c in word.chars() {
                if c.is_ascii() {
                    // ASCII fast path: lowercasing requires a single branch.
                    key_buf.push(c.to_ascii_lowercase() as u16);
                } else if c.is_lowercase() {
                    // Already lowercase (e.g. lowercase Cyrillic/Greek): encode
                    // directly and skip the costly Unicode special-casing.
                    key_buf.extend_from_slice(c.encode_utf16(&mut unit));
                } else {
                    for lc in c.to_lowercase() {
                        key_buf.extend_from_slice(lc.encode_utf16(&mut unit));
                    }
                }
            }
        } else {
            key_buf.extend(word.encode_utf16());
        }
    }

    /// Find the child of `node` on `label` via binary search over the node's
    /// contiguous, ascending edge-label slice. Uses unchecked indexing on
    /// provably in-range offsets to drop bounds checks from the hot loop.
    #[inline]
    fn child(&self, node: usize, label: u16) -> Option<usize> {
        // Dense high-fanout node: O(1) direct index.
        // SAFETY: node and node+1 index dense_start (len = num_nodes+1).
        let ds = unsafe { *self.dense_start.get_unchecked(node) } as usize;
        let de = unsafe { *self.dense_start.get_unchecked(node + 1) } as usize;
        if de > ds {
            let base = unsafe { *self.dense_base.get_unchecked(node) };
            let idx = label.wrapping_sub(base) as usize;
            if idx < de - ds {
                // SAFETY: ds + idx < de <= dense_targets.len().
                let t = unsafe { *self.dense_targets.get_unchecked(ds + idx) };
                if t != 0 {
                    return Some((t - 1) as usize);
                }
            }
            return None;
        }
        // Sparse node: binary search over the contiguous ascending edge slice.
        // SAFETY: node and node+1 index edge_start (len = num_nodes+1).
        let lo = unsafe { *self.edge_start.get_unchecked(node) } as usize;
        let hi = unsafe { *self.edge_start.get_unchecked(node + 1) } as usize;
        // SAFETY: lo <= hi <= edge_labels.len() by construction.
        let labels = unsafe { self.edge_labels.get_unchecked(lo..hi) };
        match labels.binary_search(&label) {
            // SAFETY: lo+pos < hi <= edge_targets.len().
            Ok(pos) => Some(unsafe { *self.edge_targets.get_unchecked(lo + pos) } as usize),
            Err(_) => None,
        }
    }

    /// Walk the trie for `key`, returning the accepting/terminal node id.
    #[inline]
    fn find_node(&self, key: &[u16]) -> Option<usize> {
        let mut node = 0usize;
        match self.metadata.traversal {
            TraversalDirection::Backward => {
                for &label in key.iter().rev() {
                    if unsafe { *self.accepts.get_unchecked(node) } {
                        return Some(node);
                    }
                    node = self.child(node, label)?;
                }
            }
            TraversalDirection::Forward => {
                for &label in key.iter() {
                    if unsafe { *self.accepts.get_unchecked(node) } {
                        return Some(node);
                    }
                    node = self.child(node, label)?;
                }
            }
        }
        Some(node)
    }

    #[inline]
    fn preferred_value(&self, node: usize) -> Option<&Arc<PatchCommand>> {
        let start = self.value_start[node] as usize;
        let end = self.value_start[node + 1] as usize;
        if start == end {
            None
        } else {
            Some(&self.values[start])
        }
    }

    /// Stem into caller-owned scratch buffers and return the produced length
    /// without allocating an output String. This also supports diagnostics
    /// that isolate the algorithm from output-String allocation.
    pub fn stem_len_into(
        &self,
        word: &str,
        key_buf: &mut Vec<u16>,
        out_buf: &mut Vec<u16>,
    ) -> Option<usize> {
        self.encode_key(word, key_buf);
        let node = self.find_node(key_buf)?;
        let patch = self.preferred_value(node)?;
        patch.apply_into(key_buf, out_buf);
        Some(out_buf.len())
    }

    /// Diagnostic: only normalize + UTF-16 encode the key.
    pub fn bench_encode(&self, word: &str, key_buf: &mut Vec<u16>) -> usize {
        self.encode_key(word, key_buf);
        key_buf.len()
    }

    /// Diagnostic: normalize + encode + trie walk (no patch apply).
    pub fn bench_find(&self, word: &str, key_buf: &mut Vec<u16>) -> bool {
        self.encode_key(word, key_buf);
        self.find_node(key_buf).is_some()
    }

    /// Return all stems in frequency order.
    pub fn stem_all(&self, word: &str) -> Vec<String> {
        let mut key_u16: Vec<u16> = Vec::new();
        self.encode_key(word, &mut key_u16);
        match self.find_node(&key_u16) {
            None => Vec::new(),
            Some(node) => {
                let start = self.value_start[node] as usize;
                let end = self.value_start[node + 1] as usize;
                self.values[start..end]
                    .iter()
                    .map(|p| String::from_utf16_lossy(&p.apply(&key_u16)))
                    .collect()
            }
        }
    }
}

pub fn strip_diacritics(s: &str) -> String {
    s.nfd()
        .filter(|ch| !matches!(get_general_category(*ch), GeneralCategory::NonspacingMark))
        .collect()
}
