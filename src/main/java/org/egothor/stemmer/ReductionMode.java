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
package org.egothor.stemmer;

/**
 * Defines the subtree reduction strategy applied during trie compilation.
 *
 * <p>
 * All reduction modes operate on the full subtree semantics, not only on the
 * local content of a single node. This is important because trie values may be
 * stored on both internal nodes and leaf nodes.
 */
@SuppressWarnings("PMD.LongVariable")
public enum ReductionMode {

    /**
     * Merges subtrees whose {@code getAll()} results are equivalent for every
     * reachable key suffix and whose local result ordering is the same.
     *
     * <p>
     * This mode ignores absolute frequencies when comparing subtree signatures, but
     * preserves the value order returned by {@code getAll()}.
     */
    MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS,

    /**
     * Merges subtrees whose {@code getAll()} results are equivalent for every
     * reachable key suffix, regardless of the local ordering of values.
     *
     * <p>
     * This mode ignores both absolute frequencies and local result ordering when
     * comparing subtree signatures.
     */
    MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS,

    /**
     * Merges subtrees whose preferred {@code get()} results are equivalent for
     * every reachable key suffix, provided that the locally dominant winner
     * satisfies the configured dominance constraints.
     *
     * <p>
     * If a node does not satisfy the dominance constraints, the implementation
     * falls back to ranked {@code getAll()} semantics for that node in order to
     * avoid unsafe over-reduction.
     */
    MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS
}
