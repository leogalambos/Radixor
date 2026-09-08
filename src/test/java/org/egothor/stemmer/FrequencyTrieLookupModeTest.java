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
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import org.egothor.stemmer.trie.CompiledNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link LookupMode} command-selection policy on a compiled trie
 * that contains an accepting node with child edges — the shape produced when a
 * custom pair is added through a contracted generalization.
 *
 * <p>
 * Fixture (BACKWARD): {@code root -s-> A(accepts, value "S") -x-> B(value
 * "SPECIFIC")}. The backward key {@code "xs"} passes through the accepting node
 * {@code A} and then descends the specific edge to {@code B}.
 * </p>
 */
@DisplayName("FrequencyTrie lookup modes")
@Tag("unit")
@Tag("query")
@Tag("frequency-trie")
class FrequencyTrieLookupModeTest {

    /**
     * Dense-lookup threshold used by manually assembled compiled nodes.
     */
    private static final int DEPTH = CompiledNode.DEFAULT_MAX_EXPANDED_INDEX;

    /**
     * Minimal string codec used to verify binary round-trips of the test fixture.
     */
    private static final FrequencyTrie.ValueStreamCodec<String> STRING_CODEC =
            new FrequencyTrie.ValueStreamCodec<String>() {

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
     * Creates a generic compiled-node array for manually assembled graph fixtures.
     *
     * @param nodes nodes to expose through the fixture array
     * @param <V>   stored value type
     * @return the supplied varargs array
     */
    @SafeVarargs
    private static <V> CompiledNode<V>[] nodes(final CompiledNode<V>... nodes) {
        return nodes;
    }

    /**
     * Creates a backward trie whose accepting {@code -s} node also has a deeper
     * exact {@code xs} override.
     *
     * @return immutable lookup-mode fixture
     */
    private static FrequencyTrie<String> fixture() {
        final CompiledNode<String> specific = new CompiledNode<>(new char[0], nodes(),
                new String[] { "SPECIFIC" }, false, DEPTH, 1);
        final CompiledNode<String> accepting = new CompiledNode<>(new char[] { 'x' }, nodes(specific),
                new String[] { "S" }, true, DEPTH, 3);
        final CompiledNode<String> root = new CompiledNode<>(new char[] { 's' }, nodes(accepting),
                new String[0], false, DEPTH);
        final TrieMetadata metadata = TrieMetadata.current(FrequencyTrie.currentFormatVersion(),
                WordTraversalDirection.BACKWARD,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
        return FrequencyTrie.fromCompiled(String[]::new, root, metadata);
    }

    /**
     * Verifies legacy short-circuit selection at the first accepting node.
     */
    @Test
    @DisplayName("FIRST selects the shallowest accepting generalization")
    void firstSelectsShallowestAccepting() {
        final FrequencyTrie<String> trie = fixture();
        assertAll(
                () -> assertEquals(LookupMode.FIRST, trie.lookupMode()),
                () -> assertEquals("S", trie.get("s")),
                () -> assertEquals("S", trie.get("xs"), "accepting node short-circuits descent"),
                () -> assertArrayEquals(new String[] { "S" }, trie.getAll("xs")));
    }

    /**
     * Verifies most-specific selection and fallback to the deepest accepting
     * ancestor when descent cannot continue.
     */
    @Test
    @DisplayName("LAST selects the deepest, most specific match")
    void lastSelectsMostSpecific() {
        final FrequencyTrie<String> trie = fixture().withLookupMode(LookupMode.LAST);
        assertAll(
                () -> assertEquals(LookupMode.LAST, trie.lookupMode()),
                () -> assertEquals("SPECIFIC", trie.get("xs")),
                () -> assertEquals("S", trie.get("s"), "no deeper edge: the accepting value applies"),
                () -> assertEquals("S", trie.get("ys"), "dead-end below the accepting node falls back to it"),
                () -> assertArrayEquals(new String[] { "SPECIFIC" }, trie.getAll("xs")));
    }

    /**
     * Verifies multi-node collection order, scalar behavior, and aligned occurrence
     * counts under {@link LookupMode#ALL}.
     */
    @Test
    @DisplayName("ALL collects every candidate, most specific first")
    void allCollectsCandidatesMostSpecificFirst() {
        final FrequencyTrie<String> trie = fixture().withLookupMode(LookupMode.ALL);
        assertAll(
                () -> assertEquals(LookupMode.ALL, trie.lookupMode()),
                () -> assertArrayEquals(new String[] { "SPECIFIC", "S" }, trie.getAll("xs")),
                () -> assertEquals("SPECIFIC", trie.get("xs"), "scalar get selects the most specific"),
                () -> assertEquals(List.of(new ValueCount<>("SPECIFIC", 1), new ValueCount<>("S", 3)),
                        trie.getEntries("xs")));
    }

    /**
     * Verifies that the version 7 binary format preserves an accepting node with
     * child edges and that every lookup policy remains usable after loading.
     *
     * @throws IOException if serialization or deserialization unexpectedly fails
     */
    @Test
    @DisplayName("accepting node with child edges survives a writeTo/readFrom round-trip")
    void acceptingWithChildrenSurvivesSerialization() throws IOException {
        // Proves the relaxed v7 invariant is coherent: the writer emits an
        // accepting node that also has edges, and the reader accepts it. Since the
        // Python TrieBuilder writes the identical v7 layout, this transitively
        // validates that Java can read Python-produced custom dictionaries.
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        fixture().writeTo(out, STRING_CODEC);
        final FrequencyTrie<String> restored = FrequencyTrie.readFrom(
                new ByteArrayInputStream(out.toByteArray()), String[]::new, STRING_CODEC);
        assertAll(
                () -> assertEquals("S", restored.get("xs"), "FIRST default preserved"),
                () -> assertEquals("SPECIFIC", restored.withLookupMode(LookupMode.LAST).get("xs")),
                () -> assertArrayEquals(new String[] { "SPECIFIC", "S" },
                        restored.withLookupMode(LookupMode.ALL).getAll("xs")));
    }

    /**
     * Verifies constant-structure view creation, same-mode identity reuse, and
     * immutability of the original view's policy.
     */
    @Test
    @DisplayName("withLookupMode returns a shared view and leaves the original unchanged")
    void withLookupModeReturnsSharedView() {
        final FrequencyTrie<String> first = fixture();
        assertAll(
                () -> assertSame(first, first.withLookupMode(LookupMode.FIRST), "unchanged mode returns this"),
                () -> assertEquals(LookupMode.LAST, first.withLookupMode(LookupMode.LAST).lookupMode()),
                () -> assertEquals(LookupMode.FIRST, first.lookupMode(), "original view is unaffected"),
                () -> assertEquals("S", first.get("xs"), "original still uses FIRST semantics"));
    }
}
