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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.IntFunction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Verifies the value-update operations on {@link FrequencyTrie.Builder}:
 * {@code putDominant}, {@code set}, {@code putIfAbsent}, and the two
 * {@code remove} overloads.
 */
@DisplayName("FrequencyTrie.Builder update operations")
@Tag("unit")
@Tag("construction")
@Tag("frequency-trie")
class FrequencyTrieBuilderUpdateTest {

    /**
     * Typed array factory shared by the test builders.
     */
    private static final IntFunction<String[]> FACTORY = String[]::new;

    /**
     * Creates an empty builder using ranked reduction so every alternative remains
     * directly observable to assertions.
     *
     * @return empty ranked-result trie builder
     */
    private static FrequencyTrie.Builder<String> builder() {
        return new FrequencyTrie.Builder<String>(FACTORY,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
    }

    /**
     * Verifies that dominant insertion changes the scalar winner without deleting
     * existing alternatives.
     */
    @Test
    @DisplayName("putDominant makes a value dominant while keeping the alternatives")
    void putDominantKeepsAlternatives() {
        final FrequencyTrie<String> trie = builder()
                .put("k", "a", 5)
                .put("k", "b", 2)
                .putDominant("k", "c")
                .build();
        assertAll(
                () -> assertEquals("c", trie.get("k"), "the promoted value is dominant"),
                () -> assertArrayEquals(new String[] { "c", "a", "b" }, trie.getAll("k"),
                        "alternatives are retained, ranked below the promoted value"));
    }

    /**
     * Verifies that promoting an existing value raises it above a previously more
     * frequent candidate.
     */
    @Test
    @DisplayName("putDominant overrides an existing higher-frequency value")
    void putDominantOverridesHigherFrequency() {
        final FrequencyTrie<String> trie = builder()
                .put("k", "a", 5)
                .put("k", "b", 2)
                .putDominant("k", "b")
                .build();
        assertEquals("b", trie.get("k"));
    }

    /**
     * Verifies authoritative replacement of all node-local values.
     */
    @Test
    @DisplayName("set replaces all values with a single one")
    void setReplacesAllValues() {
        final FrequencyTrie<String> trie = builder()
                .put("k", "a", 5)
                .put("k", "b", 2)
                .set("k", "c")
                .build();
        assertAll(
                () -> assertEquals("c", trie.get("k")),
                () -> assertArrayEquals(new String[] { "c" }, trie.getAll("k"), "prior values are discarded"));
    }

    /**
     * Verifies that conditional insertion changes only nodes without local values.
     */
    @Test
    @DisplayName("putIfAbsent stores only when the node has no value")
    void putIfAbsentOnlyWhenEmpty() {
        final FrequencyTrie<String> trie = builder()
                .put("k", "a", 5)
                .putIfAbsent("k", "b")   // no-op: "k" already has a value
                .putIfAbsent("m", "x")   // stored: "m" is new
                .build();
        assertAll(
                () -> assertEquals("a", trie.get("k"), "existing value is untouched"),
                () -> assertArrayEquals(new String[] { "a" }, trie.getAll("k")),
                () -> assertEquals("x", trie.get("m"), "absent key gains the value"));
    }

    /**
     * Verifies exact-node removal, missing-key no-op behavior, and isolation from
     * unrelated keys.
     */
    @Test
    @DisplayName("remove(key) clears the exact node and is a no-op for a missing key")
    void removeKeyClearsExactNode() {
        final FrequencyTrie<String> trie = builder()
                .put("kk", "a", 5)
                .put("mm", "b", 2)
                .remove("kk")
                .remove("absent")   // no-op, no exception
                .build();
        assertAll(
                () -> assertNull(trie.get("kk"), "removed key resolves to null"),
                () -> assertEquals("b", trie.get("mm"), "other keys are unaffected"));
    }

    /**
     * Verifies selective removal of one value while retaining the node's remaining
     * alternatives.
     */
    @Test
    @DisplayName("remove(key, value) drops one value and keeps the rest")
    void removeKeyValueDropsOne() {
        final FrequencyTrie<String> trie = builder()
                .put("k", "a", 5)
                .put("k", "b", 2)
                .remove("k", "a")
                .remove("k", "absent")   // no-op
                .build();
        assertAll(
                () -> assertEquals("b", trie.get("k")),
                () -> assertArrayEquals(new String[] { "b" }, trie.getAll("k")));
    }

    /**
     * Verifies that every update operation returns the same builder instance for
     * fluent composition.
     */
    @Test
    @DisplayName("update operations return the builder for chaining")
    void updatesAreChainable() {
        final FrequencyTrie.Builder<String> b = builder();
        assertAll(
                () -> assertSame(b, b.putDominant("k", "a")),
                () -> assertSame(b, b.set("k", "a")),
                () -> assertSame(b, b.putIfAbsent("k", "a")),
                () -> assertSame(b, b.remove("k")),
                () -> assertSame(b, b.remove("k", "a")));
    }

    /**
     * Verifies that both raw accumulation and dominant promotion fail explicitly
     * instead of wrapping signed 32-bit persisted counts.
     */
    @Test
    @DisplayName("frequency updates reject integer overflow")
    void frequencyUpdatesRejectOverflow() {
        final FrequencyTrie.Builder<String> accumulated = builder().put("k", "a", Integer.MAX_VALUE);
        final FrequencyTrie.Builder<String> dominant = builder()
                .put("k", "a", Integer.MAX_VALUE)
                .put("k", "b");

        assertAll(
                () -> assertThrows(ArithmeticException.class, () -> accumulated.put("k", "a")),
                () -> assertThrows(ArithmeticException.class, () -> dominant.putDominant("k", "b")));
    }
}
