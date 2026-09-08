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
 * Selects which stored command(s) the {@code get} / {@code getAll} family of
 * {@link FrequencyTrie} returns along a key's trie path.
 *
 * <p>
 * A key path may pass through several value-bearing nodes: shallow contracted
 * accepting nodes (generalizations that accept remaining input) and, when the
 * key is fully consumed, a deep exact terminal (the most specific match). This
 * policy decides which of those the lookup selects.
 * </p>
 *
 * <p>
 * The policy is a read-time concern and is never persisted: the same compiled
 * trie can be queried under any mode. It is applied through
 * {@link FrequencyTrie#withLookupMode(LookupMode)}, which returns a lightweight
 * view sharing the underlying compiled structure.
 * </p>
 *
 * <p>
 * Modes are immutable and thread-safe. They affect only lookup selection, not
 * key normalization, value ranking within a node, trie reduction, fingerprints,
 * or the persisted binary representation.
 * </p>
 *
 * @apiNote Use {@link #LAST} for exact overrides inserted beneath a contracted
 *          generalization. Use {@link #ALL} when callers need both the override
 *          and its applicable generalized fallbacks.
 */
public enum LookupMode {

    /**
     * The first (shallowest) accepting node on the path wins and short-circuits
     * descent. This is the historical default and the only mode whose results
     * match the legacy lookup behavior.
     */
    FIRST,

    /**
     * The last (deepest, most specific) match closest to the input wins. Descent
     * continues past accepting nodes whenever a deeper edge exists; the deepest
     * accepting ancestor is used only as a fallback when the specific path
     * dead-ends or the exact terminal stores no value.
     */
    LAST,

    /**
     * Collect all applicable commands along the path — every accepting node plus
     * the exact terminal — most specific first, least specific (root-ward) last.
     *
     * <p>
     * This affects the multi-result operations ({@link FrequencyTrie#getAll(String)}
     * and visitor overloads). Scalar {@link FrequencyTrie#get(String)} selects the
     * single most specific command, identical to {@link #LAST}.
     * </p>
     */
    ALL
}
