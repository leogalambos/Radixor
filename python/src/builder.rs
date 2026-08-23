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

// Port of the Radixor Java trie compilation pipeline
// (org.egothor.stemmer.FrequencyTrie.Builder + org.egothor.stemmer.trie.*):
// mutable trie build -> bottom-up reduction -> freeze to an immutable compiled trie.
//
// Faithful port notes:
//   * Build semantics mirror StemmerPatchTrieLoader.load: for each dictionary
//     entry we optionally insert the stem mapped to the NOOP patch "Na" (when
//     store_original) and every variant != stem mapped to
//     encode_patch(variant, stem, backward).
//   * Keys are indexed per WordTraversalDirection: BACKWARD consumes characters
//     the sequence end (logicalIndex = len-1-offset), FORWARD from index zero.
//   * Reduction hardcodes the production configuration verified from the Java
//     source: ReductionMode = MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS,
//     dominantWinnerMinPercent = 75, dominantWinnerOverSecondRatio = 3,
//     contractUniformSubtrees = true (metadataForCompilation always applies
//     ReductionSettings.withUniformSubtreeContraction).
//   * All character/patch data is handled as UTF-16 code units (Java `char`),
//     exactly as the runtime trie.rs expects.
#![allow(dead_code)]

use std::cell::RefCell;
use std::collections::{BTreeMap, HashMap};
use std::rc::Rc;
use std::sync::Arc;

use crate::dict::DictEntry;
use crate::encoder::encode_patch;
use crate::patch::PatchCommand;
use crate::trie::{CaseMode, DiacriticMode, FrequencyTrie, TraversalDirection, TrieMetadata};

/// Canonical no-op patch command (PatchCommandEncoder.NOOP_PATCH = "Na").
const NOOP_PATCH: &str = "Na";

/// dominantWinnerMinPercent (ReductionSettings.DEFAULT_DOMINANT_WINNER_MIN_PERCENT).
const DOMINANT_WINNER_MIN_PERCENT: i64 = 75;

/// dominantWinnerOverSecondRatio (ReductionSettings.DEFAULT_DOMINANT_WINNER_OVER_SECOND_RATIO).
const DOMINANT_WINNER_OVER_SECOND_RATIO: i64 = 3;

// Ordered value-count map (Java LinkedHashMap<V, Integer> semantics)

/// Insertion-ordered map from a patch-command string to its accumulated local
/// frequency. Mirrors the `LinkedHashMap<V, Integer>` used for `valueCounts` on
/// mutable nodes and `localCounts` on reduced nodes.
#[derive(Clone, Default)]
struct OrderedCounts {
    entries: Vec<(String, i32)>,
    index: HashMap<String, usize>,
}

impl OrderedCounts {
    fn new() -> Self {
        OrderedCounts {
            entries: Vec::new(),
            index: HashMap::new(),
        }
    }

    fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }

    fn len(&self) -> usize {
        self.entries.len()
    }

    /// Adds `count` to `value`, preserving first-seen insertion order. This is
    /// both the build-time `put` accumulation and the reduction-time
    /// `mergeLocalCounts` aggregation.
    fn add(&mut self, value: &str, count: i32) {
        if let Some(&position) = self.index.get(value) {
            self.entries[position].1 += count;
        } else {
            let position = self.entries.len();
            self.index.insert(value.to_string(), position);
            self.entries.push((value.to_string(), count));
        }
    }
}

// MutableNode (org.egothor.stemmer.trie.MutableNode)

/// Mutable build-time node: children indexed by transition character plus the
/// local terminal value counts stored exactly at this node.
struct MutableNode {
    children: BTreeMap<u16, MutableNode>,
    value_counts: OrderedCounts,
}

impl MutableNode {
    fn new() -> Self {
        MutableNode {
            children: BTreeMap::new(),
            value_counts: OrderedCounts::new(),
        }
    }
}

/// Stores a value at the node addressed by `key`, incrementing its local
/// frequency by one. Mirrors `FrequencyTrie.Builder.put`.
fn put(root: &mut MutableNode, key: &[u16], value: &str, backward: bool) {
    let length = key.len();
    let mut current = root;
    for offset in 0..length {
        // WordTraversalDirection.logicalIndex(length, offset).
        let logical_index = if backward {
            length - 1 - offset
        } else {
            offset
        };
        let edge = key[logical_index];
        current = current
            .children
            .entry(edge)
            .or_insert_with(MutableNode::new);
    }
    current.value_counts.add(value, 1);
}

// ReducedNode (org.egothor.stemmer.trie.ReducedNode)

/// Canonical reduced node used during subtree merging. Reduced nodes are shared:
/// there is exactly one instance per reduction signature, referenced through
/// `Rc` so that identical subtrees share a single instance (and therefore a
/// single frozen `Arc<CompiledNode>`).
struct ReducedNode {
    /// Canonical reduction signature (see `compute_signature`).
    signature: String,
    /// Aggregated local value counts.
    local_counts: OrderedCounts,
    /// Canonical children by edge, naturally sorted ascending by the BTreeMap.
    children: BTreeMap<u16, Rc<RefCell<ReducedNode>>>,
    /// Whether this node is a contracted accepting leaf.
    accepts: bool,
}

impl ReducedNode {
    /// Merges additional local counts into this canonical node.
    fn merge_local_counts(&mut self, additional: &OrderedCounts) {
        for (value, count) in &additional.entries {
            self.local_counts.add(value, *count);
        }
    }

    /// Merges child references into this canonical node. For nodes with the same
    /// reduction signature the child edge sets and child signatures are
    /// compatible, so this only verifies canonical identity and stores it.
    fn merge_children(&mut self, additional: &BTreeMap<u16, Rc<RefCell<ReducedNode>>>) {
        for (edge, child) in additional {
            match self.children.get(edge) {
                Some(existing) => {
                    if !Rc::ptr_eq(existing, child) {
                        panic!("Incompatible canonical child encountered during reduction.");
                    }
                }
                None => {
                    self.children.insert(*edge, Rc::clone(child));
                }
            }
        }
    }
}

// LocalValueSummary (org.egothor.stemmer.trie.LocalValueSummary)

/// Deterministic local terminal value summary of a node.
struct LocalValueSummary {
    /// Locally stored values ordered by descending frequency, then shorter text,
    /// then lexicographic (UTF-16) text, then first-seen insertion order.
    ordered_values: Vec<String>,
    /// Frequencies aligned with `ordered_values` (needed for v7 serialization).
    ordered_counts: Vec<i32>,
    total_count: i64,
    dominant_value: Option<String>,
    dominant_count: i64,
    second_count: i64,
}

impl LocalValueSummary {
    /// Builds a summary from local counts, applying the exact Java ordering.
    fn of(counts: &OrderedCounts) -> Self {
        struct Sortable {
            value: String,
            count: i32,
            // Java String.length() and String.compareTo operate on UTF-16 code
            // units, so text ordering must compare the u16 sequence, never UTF-8.
            text16: Vec<u16>,
            insertion_order: usize,
        }

        let mut entries: Vec<Sortable> = counts
            .entries
            .iter()
            .enumerate()
            .map(|(insertion_order, (value, count))| Sortable {
                value: value.clone(),
                count: *count,
                text16: value.encode_utf16().collect(),
                insertion_order,
            })
            .collect();

        entries.sort_by(|left, right| {
            // 1. descending frequency
            right
                .count
                .cmp(&left.count)
                // 2. shorter text wins
                .then_with(|| left.text16.len().cmp(&right.text16.len()))
                // 3. lexicographically lower text (UTF-16 code units) wins
                .then_with(|| left.text16.cmp(&right.text16))
                // 4. stable first-seen insertion order
                .then_with(|| left.insertion_order.cmp(&right.insertion_order))
        });

        let ordered_values: Vec<String> = entries.iter().map(|entry| entry.value.clone()).collect();
        let ordered_counts: Vec<i32> = entries.iter().map(|entry| entry.count).collect();
        let total_count: i64 = entries.iter().map(|entry| entry.count as i64).sum();
        let dominant_value = entries.first().map(|entry| entry.value.clone());
        let dominant_count = entries.first().map(|entry| entry.count as i64).unwrap_or(0);
        let second_count = entries.get(1).map(|entry| entry.count as i64).unwrap_or(0);

        LocalValueSummary {
            ordered_values,
            ordered_counts,
            total_count,
            dominant_value,
            dominant_count,
            second_count,
        }
    }

    /// Whether the dominant value satisfies both configured dominance
    /// constraints (percent AND ratio), matching
    /// `LocalValueSummary.hasQualifiedDominantWinner`.
    fn has_qualified_dominant_winner(&self) -> bool {
        if self.dominant_value.is_none() {
            return false;
        }

        let percent_satisfied =
            self.dominant_count * 100 >= self.total_count * DOMINANT_WINNER_MIN_PERCENT;

        let ratio_satisfied = if self.second_count == 0 {
            true
        } else {
            self.dominant_count >= self.second_count * DOMINANT_WINNER_OVER_SECOND_RATIO
        };

        percent_satisfied && ratio_satisfied
    }
}

// ReductionSignature (org.egothor.stemmer.trie.ReductionSignature and friends)

/// Appends `text` to `buffer` using a length-prefixed, collision-free encoding
/// so arbitrary UTF-16 patch strings can be embedded without ambiguity.
fn push_len_prefixed(buffer: &mut String, text: &str) {
    buffer.push_str(&text.len().to_string());
    buffer.push('#');
    buffer.push_str(text);
}

/// Produces the canonical reduction signature of a subtree as an unambiguous
/// hashable string. Two subtrees receive equal signatures exactly when the Java
/// `ReductionSignature.equals` would consider them equal:
///
///   * local descriptor — for DOMINANT mode this is the dominant descriptor
///     (only the dominant value) when the summary has a qualified dominant
///     winner, otherwise the ranked descriptor (the full ordered value list),
///   * whether the node accepts remaining input,
///   * the sorted list of (edge label, child signature) pairs.
fn compute_signature(
    summary: &LocalValueSummary,
    children: &BTreeMap<u16, Rc<RefCell<ReducedNode>>>,
    accepts: bool,
) -> String {
    let mut signature = String::new();

    // Local descriptor. 'D' and 'R' markers keep a DominantLocalDescriptor
    // distinct from a RankedLocalDescriptor holding the same single value,
    // exactly as the Java class-based equality does.
    if summary.has_qualified_dominant_winner() {
        signature.push('D');
        push_len_prefixed(&mut signature, summary.dominant_value.as_ref().unwrap());
    } else {
        signature.push('R');
        signature.push_str(&summary.ordered_values.len().to_string());
        signature.push(';');
        for value in &summary.ordered_values {
            push_len_prefixed(&mut signature, value);
        }
    }

    // acceptsRemainingInput.
    signature.push(if accepts { 'A' } else { 'a' });

    // Child descriptors in sorted edge order (BTreeMap iterates ascending).
    signature.push_str(&children.len().to_string());
    signature.push(';');
    for (label, child) in children {
        signature.push_str(&label.to_string());
        signature.push(':');
        push_len_prefixed(&mut signature, &child.borrow().signature);
    }

    signature
}

/// Returns aggregated single-value local counts when the supplied internal
/// subtree can be contracted into an accepting leaf, otherwise `None`.
///
/// Contraction applies (matching `FrequencyTrie.Builder.contractUniformSubtree`)
/// when the node has at least one child, every child is a single-value leaf with
/// no further children, and all those child values plus the local value (if any)
/// are the same single value. The contracted count is always 1.
fn contract_uniform_subtree(
    local_counts: &OrderedCounts,
    children: &BTreeMap<u16, Rc<RefCell<ReducedNode>>>,
) -> Option<OrderedCounts> {
    if children.is_empty() {
        return None;
    }

    let mut uniform_value: Option<String> = None;
    let mut value_seen = false;

    if !local_counts.is_empty() {
        if local_counts.len() != 1 {
            return None;
        }
        uniform_value = Some(local_counts.entries[0].0.clone());
        value_seen = true;
    }

    for child in children.values() {
        let child_ref = child.borrow();
        let is_single_value_leaf =
            child_ref.children.is_empty() && child_ref.local_counts.len() == 1;
        if !is_single_value_leaf {
            return None;
        }
        let child_value = child_ref.local_counts.entries[0].0.clone();
        if value_seen && uniform_value.as_deref() != Some(child_value.as_str()) {
            return None;
        }
        uniform_value = Some(child_value);
        value_seen = true;
    }

    if !value_seen {
        return None;
    }

    let mut contracted = OrderedCounts::new();
    contracted.add(uniform_value.as_ref().unwrap(), 1);
    Some(contracted)
}

/// Reduces a mutable node to a canonical reduced node (bottom-up).
///
/// The order of operations mirrors the Java `reduce`:
///   1. reduce every child first,
///   2. try `contractUniformSubtree` (always enabled here),
///   3. compute the local summary and reduction signature,
///   4. deduplicate through the context map, merging counts and children into
///      an existing canonical node when the signature already exists.
fn reduce(
    node: &MutableNode,
    context: &mut HashMap<String, Rc<RefCell<ReducedNode>>>,
) -> Rc<RefCell<ReducedNode>> {
    let mut reduced_children: BTreeMap<u16, Rc<RefCell<ReducedNode>>> = BTreeMap::new();
    for (edge, child) in node.children.iter() {
        let reduced_child = reduce(child, context);
        reduced_children.insert(*edge, reduced_child);
    }

    let mut local_counts = node.value_counts.clone();
    let mut accepts_remaining_input = false;

    // contractUniformSubtrees is always true for the production configuration.
    if let Some(contracted) = contract_uniform_subtree(&local_counts, &reduced_children) {
        local_counts = contracted;
        reduced_children = BTreeMap::new();
        accepts_remaining_input = true;
    }

    let summary = LocalValueSummary::of(&local_counts);
    let signature = compute_signature(&summary, &reduced_children, accepts_remaining_input);

    if let Some(canonical) = context.get(&signature).cloned() {
        {
            let mut canonical_mut = canonical.borrow_mut();
            canonical_mut.merge_local_counts(&local_counts);
            canonical_mut.merge_children(&reduced_children);
        }
        return canonical;
    }

    let canonical = Rc::new(RefCell::new(ReducedNode {
        signature: signature.clone(),
        local_counts,
        children: reduced_children,
        accepts: accepts_remaining_input,
    }));
    context.insert(signature, Rc::clone(&canonical));
    canonical
}

// Freeze (FrequencyTrie.Builder.freeze -> flat CSR arrays)

/// Maximum contiguous child-label span for which a node uses a dense
/// direct-index table instead of binary search (mirrors the Java
/// CompiledNode `maxExpandedIndex` fanout strategy).
pub(crate) const MAX_DENSE_SPAN: usize = 512;

/// Frozen arrays of the compiled trie in CSR layout (see trie.rs).
pub(crate) struct FrozenTrie {
    pub(crate) edge_start: Vec<u32>,
    pub(crate) edge_labels: Vec<u16>,
    pub(crate) edge_targets: Vec<u32>,
    pub(crate) accepts: Vec<bool>,
    pub(crate) value_start: Vec<u32>,
    pub(crate) values: Vec<Arc<PatchCommand>>,
    /// Patch strings parallel to `values` (needed only for serialization).
    pub(crate) value_strings: Vec<String>,
    /// Frequencies parallel to `values` (needed only for v7 serialization).
    pub(crate) value_counts: Vec<i32>,
    pub(crate) dense_start: Vec<u32>,
    pub(crate) dense_base: Vec<u16>,
    pub(crate) dense_targets: Vec<u32>,
}

/// Per-node build record collected during interning, in node-id order.
#[derive(Default)]
struct NodeBuild {
    edges: Vec<u16>,
    targets: Vec<u32>,
    accepts: bool,
    values: Vec<Arc<PatchCommand>>,
    value_strings: Vec<String>,
    value_counts: Vec<i32>,
}

/// Assigns a stable node id to each distinct canonical reduced node and records
/// its edges (ascending), child ids, and best-first values.
///
/// Shared canonical reduced nodes (identical `Rc` allocations) are interned once
/// — the analogue of the Java `IdentityHashMap<ReducedNode, CompiledNode>` cache
/// — so structural sharing from reduction is preserved as shared node ids. Equal
/// patch strings are compiled once and shared through `patch_cache`.
fn intern(
    node: &Rc<RefCell<ReducedNode>>,
    index_of: &mut HashMap<usize, u32>,
    nodes: &mut Vec<NodeBuild>,
    patch_cache: &mut HashMap<String, Arc<PatchCommand>>,
    backward: bool,
) -> u32 {
    let identity = Rc::as_ptr(node) as usize;
    if let Some(&existing) = index_of.get(&identity) {
        return existing;
    }
    let id = nodes.len() as u32;
    index_of.insert(identity, id);
    nodes.push(NodeBuild::default()); // reserve this id's slot before recursing

    let node_ref = node.borrow();
    let summary = LocalValueSummary::of(&node_ref.local_counts);

    // BTreeMap iterates ascending by edge label, so edges stay sorted.
    let mut edges: Vec<u16> = Vec::with_capacity(node_ref.children.len());
    let mut targets: Vec<u32> = Vec::with_capacity(node_ref.children.len());
    for (edge, child) in node_ref.children.iter() {
        edges.push(*edge);
        targets.push(intern(child, index_of, nodes, patch_cache, backward));
    }

    let values: Vec<Arc<PatchCommand>> = summary
        .ordered_values
        .iter()
        .map(|patch| {
            Arc::clone(
                patch_cache
                    .entry(patch.clone())
                    .or_insert_with(|| Arc::new(PatchCommand::parse(patch, backward))),
            )
        })
        .collect();

    nodes[id as usize] = NodeBuild {
        edges,
        targets,
        accepts: node_ref.accepts,
        values,
        value_strings: summary.ordered_values.clone(),
        value_counts: summary.ordered_counts.clone(),
    };
    id
}

/// Freezes the reduced graph rooted at `root` (node id 0) into flat CSR arrays.
fn freeze(root: &Rc<RefCell<ReducedNode>>, backward: bool) -> FrozenTrie {
    let mut index_of: HashMap<usize, u32> = HashMap::new();
    let mut nodes: Vec<NodeBuild> = Vec::new();
    let mut patch_cache: HashMap<String, Arc<PatchCommand>> = HashMap::new();
    intern(root, &mut index_of, &mut nodes, &mut patch_cache, backward);

    let node_count = nodes.len();
    let mut edge_start: Vec<u32> = Vec::with_capacity(node_count + 1);
    let mut edge_labels: Vec<u16> = Vec::new();
    let mut edge_targets: Vec<u32> = Vec::new();
    let mut accepts: Vec<bool> = Vec::with_capacity(node_count);
    let mut value_start: Vec<u32> = Vec::with_capacity(node_count + 1);
    let mut values: Vec<Arc<PatchCommand>> = Vec::new();
    let mut value_strings: Vec<String> = Vec::new();
    let mut value_counts: Vec<i32> = Vec::new();
    let mut dense_start: Vec<u32> = Vec::with_capacity(node_count + 1);
    let mut dense_base: Vec<u16> = Vec::with_capacity(node_count);
    let mut dense_targets: Vec<u32> = Vec::new();

    edge_start.push(0);
    value_start.push(0);
    dense_start.push(0);
    for nb in &nodes {
        edge_labels.extend_from_slice(&nb.edges);
        edge_targets.extend_from_slice(&nb.targets);
        edge_start.push(edge_labels.len() as u32);
        accepts.push(nb.accepts);
        for v in &nb.values {
            values.push(Arc::clone(v));
        }
        for v in &nb.value_strings {
            value_strings.push(v.clone());
        }
        value_counts.extend_from_slice(&nb.value_counts);
        value_start.push(values.len() as u32);

        // Decide dense vs sparse child lookup by fanout/span.
        let count = nb.edges.len();
        let mut dense = false;
        if count >= 2 {
            let first = nb.edges[0] as usize;
            let last = nb.edges[count - 1] as usize; // edges are ascending
            let span = last - first + 1;
            if span <= MAX_DENSE_SPAN {
                let base = nb.edges[0];
                let seg = dense_targets.len();
                dense_targets.resize(seg + span, 0);
                for (k, &label) in nb.edges.iter().enumerate() {
                    dense_targets[seg + (label - base) as usize] = nb.targets[k] + 1;
                }
                dense_base.push(base);
                dense_start.push(dense_targets.len() as u32);
                dense = true;
            }
        }
        if !dense {
            dense_base.push(0);
            dense_start.push(dense_targets.len() as u32); // span 0 => sparse
        }
    }

    FrozenTrie {
        edge_start,
        edge_labels,
        edge_targets,
        accepts,
        value_start,
        values,
        value_strings,
        value_counts,
        dense_start,
        dense_base,
        dense_targets,
    }
}

pub(crate) fn metadata_for(backward: bool, lowercase: bool) -> TrieMetadata {
    TrieMetadata {
        traversal: if backward {
            TraversalDirection::Backward
        } else {
            TraversalDirection::Forward
        },
        case_mode: if lowercase {
            CaseMode::LowercaseWithLocaleRoot
        } else {
            CaseMode::AsIs
        },
        diacritic_mode: DiacriticMode::AsIs,
    }
}

/// Build the reduced+frozen trie arrays from dictionary entries (shared by the
/// in-memory builder and the compiler).
pub(crate) fn build_frozen(
    entries: &[DictEntry],
    backward: bool,
    store_original: bool,
) -> FrozenTrie {
    let mut root = MutableNode::new();
    for entry in entries {
        let stem16: Vec<u16> = entry.stem.encode_utf16().collect();
        if store_original {
            put(&mut root, &stem16, NOOP_PATCH, backward);
        }
        for variant in &entry.variants {
            if variant != &entry.stem {
                let variant16: Vec<u16> = variant.encode_utf16().collect();
                let patch = encode_patch(&variant16, &stem16, backward);
                put(&mut root, &variant16, &patch, backward);
            }
        }
    }
    let mut context: HashMap<String, Rc<RefCell<ReducedNode>>> = HashMap::new();
    let reduced_root = reduce(&root, &mut context);
    freeze(&reduced_root, backward)
}

/// Convert serialized/build-order patch strings into compact runtime patch ids.
///
/// The frozen builder representation keeps patch text parallel to value
/// occurrences because the v7 writer needs that exact ordering. The runtime
/// trie instead stores each parsed command once and represents every node value
/// with a compact `u32` identifier.
fn compact_runtime_values(
    value_strings: &[String],
    backward: bool,
) -> (Vec<u32>, Vec<PatchCommand>) {
    let mut patch_ids: HashMap<&str, u32> = HashMap::new();
    let mut value_ids: Vec<u32> = Vec::with_capacity(value_strings.len());
    let mut patches: Vec<PatchCommand> = Vec::new();

    for value in value_strings {
        let patch_id = if let Some(&existing) = patch_ids.get(value.as_str()) {
            existing
        } else {
            let id = patches.len() as u32;
            patches.push(PatchCommand::parse(value, backward));
            patch_ids.insert(value.as_str(), id);
            id
        };
        value_ids.push(patch_id);
    }

    (value_ids, patches)
}

/// Convert the frozen build representation into the compact runtime trie.
fn frozen_into_trie(frozen: FrozenTrie, metadata: TrieMetadata) -> FrequencyTrie {
    let backward = matches!(metadata.traversal, TraversalDirection::Backward);
    let (value_ids, patches) = compact_runtime_values(&frozen.value_strings, backward);

    FrequencyTrie::new(
        frozen.edge_start,
        frozen.edge_labels,
        frozen.edge_targets,
        frozen.accepts,
        frozen.value_start,
        value_ids,
        patches,
        frozen.dense_start,
        frozen.dense_base,
        frozen.dense_targets,
        metadata,
    )
}

// Public entry point

/// Compiles dictionary entries into a read-only patch-command trie, faithfully
/// reproducing the Java `StemmerPatchTrieLoader.load` build followed by
/// `FrequencyTrie.Builder.build` (reduce + freeze).
///
/// * `backward` — `true` selects BACKWARD traversal for suffix-oriented data;
///   `false` selects FORWARD for deliberately prefix-oriented custom data.
/// * `store_original` — when `true`, each stem is inserted mapped to the NOOP
///   patch `"Na"` so the stem itself is recognised.
pub fn build_trie_from_dict(
    entries: &[DictEntry],
    backward: bool,
    store_original: bool,
    lowercase: bool,
) -> FrequencyTrie {
    let frozen = build_frozen(entries, backward, store_original);
    frozen_into_trie(frozen, metadata_for(backward, lowercase))
}
