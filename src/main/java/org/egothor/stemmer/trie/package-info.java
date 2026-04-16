/*******************************************************************************
 * Copyright (C) 2026, Leo Galambos
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
/**
 * Provides internal trie infrastructure used by
 * {@link org.egothor.stemmer.FrequencyTrie} compilation, reduction,
 * canonicalization, and binary reconstruction.
 *
 * <p>
 * This subpackage contains the implementation-level data structures that
 * support transformation of mutable build-time trie content into a compact
 * immutable compiled representation. The types in this package are primarily
 * intended for cooperation within the stemming implementation and are not
 * designed as a general-purpose public extension surface.
 * </p>
 *
 * <p>
 * Trie construction begins with mutable nodes represented by
 * {@link org.egothor.stemmer.trie.MutableNode}, which store child transitions
 * and local terminal value frequencies in insertion-preserving maps. Local node
 * value distributions are analyzed through
 * {@link org.egothor.stemmer.trie.LocalValueSummary}, which derives the
 * deterministically ordered local values, aligned counts, total local
 * frequency, and dominant-value metadata required by reduction logic.
 * Deterministic local ordering is supported by
 * {@link org.egothor.stemmer.trie.SortableValue}.
 * </p>
 *
 * <p>
 * Subtree reduction is driven by
 * {@link org.egothor.stemmer.trie.ReductionSignature}, which captures the
 * semantic identity of a full subtree under the active reduction strategy.
 * Depending on the selected reduction settings, local subtree semantics are
 * represented by ranked, unordered, or dominant-value descriptors via
 * {@link org.egothor.stemmer.trie.RankedLocalDescriptor},
 * {@link org.egothor.stemmer.trie.UnorderedLocalDescriptor}, and
 * {@link org.egothor.stemmer.trie.DominantLocalDescriptor}. Child structure is
 * incorporated into the signature through
 * {@link org.egothor.stemmer.trie.ChildDescriptor}, ensuring that canonical
 * equivalence covers both local node content and all reachable descendants.
 * </p>
 *
 * <p>
 * Canonicalization of semantically equivalent subtrees is coordinated by
 * {@link org.egothor.stemmer.trie.ReductionContext}, which maintains the
 * signature-to-node mapping for canonical reduced nodes. Canonical merged
 * subtrees are represented by {@link org.egothor.stemmer.trie.ReducedNode},
 * whose aggregated local counts and canonical child references serve as the
 * intermediate form between mutable construction and immutable freezing.
 * </p>
 *
 * <p>
 * The final read-optimized structure is represented by
 * {@link org.egothor.stemmer.trie.CompiledNode}. Compiled nodes expose compact
 * aligned arrays of sorted edge labels, child references, ordered values, and
 * ordered counts for efficient lookup and serialization. During binary
 * deserialization, unresolved intermediate payload is carried in
 * {@link org.egothor.stemmer.trie.NodeData} until canonical node references are
 * re-linked into the final compiled form.
 * </p>
 *
 * <p>
 * Several accessors in this subpackage intentionally expose internal mutable or
 * array-backed state directly in order to avoid unnecessary copying on
 * performance-sensitive internal paths. Such APIs are intended strictly for
 * tightly related trie infrastructure within the implementation and must be
 * treated as internal-use contracts.
 * </p>
 *
 * <p>
 * In summary, this subpackage contains the internal semantic model and storage
 * forms that allow the stemming implementation to move efficiently between
 * build-time mutation, reduction-time canonical equivalence, and runtime
 * immutable lookup.
 * </p>
 */
package org.egothor.stemmer.trie;
