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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;

/**
 * Property-based tests for the compiled trie abstraction.
 *
 * <p>
 * These properties focus on deterministic compilation, observable lookup
 * alignment, binary persistence stability, and safe reconstruction back into a
 * writable builder. Together they guard the most valuable invariants of the
 * core algorithm without overfitting to particular fixture data.
 */
@Label("FrequencyTrie properties")
@Tag("unit")
@Tag("property")
@Tag("trie")
class FrequencyTrieProperties extends PropertyBasedTestSupport {

    /**
     * Binary codec used by generic trie round-trip assertions.
     */
    private static final FrequencyTrie.ValueStreamCodec<String> STRING_CODEC = new FrequencyTrie.ValueStreamCodec<>() {

        @Override
        public void write(final DataOutputStream dataOutput, final String value) throws IOException {
            dataOutput.writeUTF(value);
        }

        @Override
        public String read(final DataInputStream dataInput) throws IOException {
            return dataInput.readUTF();
        }
    };

    /**
     * Verifies that compiling the same insertion scenario repeatedly yields the
     * same observable lookups.
     *
     * @param scenario      generated trie scenario
     * @param reductionMode reduction mode
     */
    @Property(tries = 80)
    @Label("compilation should be deterministic for the same insertion scenario")
    void compilationShouldBeDeterministicForTheSameInsertionScenario(
            @ForAll("trieScenarios") final TrieScenario scenario, @ForAll final ReductionMode reductionMode) {
        final FrequencyTrie<String> first = buildTrie(scenario, reductionMode);
        final FrequencyTrie<String> second = buildTrie(scenario, reductionMode);

        for (String key : scenario.observedKeys()) {
            assertTrieStateEquals(first, second, key);
        }
    }

    /**
     * Verifies that {@link FrequencyTrie#get(String)},
     * {@link FrequencyTrie#getAll(String)}, and
     * {@link FrequencyTrie#getEntries(String)} remain aligned for every probed key.
     *
     * @param scenario      generated trie scenario
     * @param reductionMode reduction mode
     */
    @Property(tries = 80)
    @Label("get, getAll, and getEntries should stay semantically aligned")
    void getGetAllAndGetEntriesShouldStaySemanticallyAligned(@ForAll("trieScenarios") final TrieScenario scenario,
            @ForAll final ReductionMode reductionMode) {
        final FrequencyTrie<String> trie = buildTrie(scenario, reductionMode);

        for (String key : scenario.observedKeys()) {
            final String preferred = trie.get(key);
            final String[] allValues = trie.getAll(key);
            final List<ValueCount<String>> entries = trie.getEntries(key);

            assertEquals(allValues.length, entries.size(), "getAll() and getEntries() must have equal cardinality.");

            if (allValues.length == 0) {
                assertNull(preferred, "get() must return null when no terminal value exists.");
                assertTrue(entries.isEmpty(), "getEntries() must be empty when getAll() is empty.");
                continue;
            }

            assertEquals(allValues[0], preferred, "get() must expose the preferred first getAll() value.");

            int previousCount = Integer.MAX_VALUE;
            for (int index = 0; index < entries.size(); index++) {
                final ValueCount<String> entry = entries.get(index);
                assertEquals(allValues[index], entry.value(), "entry ordering must match getAll() ordering.");
                assertTrue(entry.count() >= 1, "stored frequencies must remain positive.");
                assertTrue(entry.count() <= previousCount, "entry counts must be ordered descending.");
                previousCount = entry.count();
            }
        }
    }

    /**
     * Verifies that binary serialization and deserialization preserve all
     * observable lookup semantics for generated scenarios.
     *
     * @param scenario      generated trie scenario
     * @param reductionMode reduction mode
     */
    @Property(tries = 40)
    @Label("binary round-trip should preserve observable trie semantics")
    void binaryRoundTripShouldPreserveObservableTrieSemantics(@ForAll("trieScenarios") final TrieScenario scenario,
            @ForAll final ReductionMode reductionMode) {
        final FrequencyTrie<String> original = buildTrie(scenario, reductionMode);
        final FrequencyTrie<String> roundTripped = roundTrip(original);

        for (String key : scenario.observedKeys()) {
            assertTrieStateEquals(original, roundTripped, key);
        }
    }

    /**
     * Verifies that reconstructing a writable builder from a compiled trie and
     * recompiling it preserves observable lookup semantics.
     *
     * @param scenario      generated trie scenario
     * @param reductionMode reduction mode
     */
    @Property(tries = 60)
    @Label("builder reconstruction should preserve observable trie semantics")
    void builderReconstructionShouldPreserveObservableTrieSemantics(
            @ForAll("trieScenarios") final TrieScenario scenario, @ForAll final ReductionMode reductionMode) {
        final FrequencyTrie<String> original = buildTrie(scenario, reductionMode);
        final FrequencyTrie<String> rebuilt = FrequencyTrieBuilders
                .copyOf(original, STRING_ARRAY_FACTORY, reductionMode).build();

        for (String key : scenario.observedKeys()) {
            assertEquals(original.get(key), rebuilt.get(key), "preferred lookup must survive reconstruction.");
            assertArrayEquals(original.getAll(key), rebuilt.getAll(key),
                    "complete ordered result set must survive reconstruction.");
        }
    }

    /**
     * Asserts full observable trie equality for one key.
     *
     * @param expected expected trie
     * @param actual   actual trie
     * @param key      key to probe
     */
    private static void assertTrieStateEquals(final FrequencyTrie<String> expected, final FrequencyTrie<String> actual,
            final String key) {
        assertEquals(expected.get(key), actual.get(key), "preferred lookup drifted.");
        assertArrayEquals(expected.getAll(key), actual.getAll(key), "ordered result set drifted.");
        assertIterableEquals(expected.getEntries(key), actual.getEntries(key), "entry list drifted.");
    }

    /**
     * Round-trips one trie through its binary representation.
     *
     * @param trie trie to persist and reload
     * @return reloaded trie
     */
    private static FrequencyTrie<String> roundTrip(final FrequencyTrie<String> trie) {
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
                trie.writeTo(dataOutputStream, STRING_CODEC);
            }

            try (DataInputStream dataInputStream = new DataInputStream(
                    new ByteArrayInputStream(byteArrayOutputStream.toByteArray()))) {
                return FrequencyTrie.readFrom(dataInputStream, STRING_ARRAY_FACTORY, STRING_CODEC);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unexpected binary round-trip failure.", exception);
        }
    }
}
