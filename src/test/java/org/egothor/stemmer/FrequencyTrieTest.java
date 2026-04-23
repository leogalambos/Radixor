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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FrequencyTrie}.
 *
 * <p>
 * The suite validates lookup semantics, deterministic value ordering, reduction
 * behavior, counted insertion, and binary persistence. Tests intentionally
 * verify both leaf and internal-node storage because the trie permits values at
 * any node in the path.
 */
@Tag("unit")
@Tag("trie")
@Tag("frequency-trie")
@DisplayName("FrequencyTrie")
class FrequencyTrieTest {

    /**
     * Codec used by persistence tests for {@link String} values.
     */
    private static final FrequencyTrie.ValueStreamCodec<String> STRING_CODEC = new FrequencyTrie.ValueStreamCodec<String>() {

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
     * Creates a builder using the ranked get-all reduction mode.
     *
     * @return new builder
     */
    private static FrequencyTrie.Builder<String> rankedBuilder() {
        return new FrequencyTrie.Builder<String>(String[]::new,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
    }

    /**
     * Verifies that the builder rejects {@code null} constructor arguments.
     */
    @Test
    @DisplayName("Builder rejects null constructor arguments")
    void builderRejectsNullConstructorArguments() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new FrequencyTrie.Builder<String>(null,
                                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS)),
                () -> assertThrows(NullPointerException.class,
                        () -> new FrequencyTrie.Builder<String>(String[]::new, (ReductionMode) null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new FrequencyTrie.Builder<String>(String[]::new, (ReductionSettings) null)));
    }

    /**
     * Verifies that the builder rejects {@code null} put arguments.
     */
    @Test
    @DisplayName("Builder rejects null put arguments")
    void builderRejectsNullPutArguments() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        assertAll(() -> assertThrows(NullPointerException.class, () -> builder.put(null, "x")),
                () -> assertThrows(NullPointerException.class, () -> builder.put("x", null)),
                () -> assertThrows(NullPointerException.class, () -> builder.put(null, "x", 1)),
                () -> assertThrows(NullPointerException.class, () -> builder.put("x", null, 1)));
    }

    /**
     * Verifies that counted insertion rejects non-positive counts.
     */
    @Test
    @DisplayName("Builder rejects non-positive counted insertion")
    void builderRejectsNonPositiveCountedInsertion() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        assertAll(() -> assertThrows(IllegalArgumentException.class, () -> builder.put("x", "v", 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> builder.put("x", "v", -1)));
    }

    /**
     * Verifies that lookup methods reject {@code null} keys.
     */
    @Test
    @DisplayName("Trie rejects null lookup keys")
    void trieRejectsNullLookupKeys() {
        final FrequencyTrie<String> trie = rankedBuilder().build();

        assertAll(() -> assertThrows(NullPointerException.class, () -> trie.get(null)),
                () -> assertThrows(NullPointerException.class, () -> trie.getAll(null)),
                () -> assertThrows(NullPointerException.class, () -> trie.getEntries(null)));
    }

    /**
     * Verifies lookup behavior for an empty trie.
     */
    @Test
    @DisplayName("Empty trie returns null, empty array, and empty entries")
    void emptyTrieReturnsNullEmptyArrayAndEmptyEntries() {
        final FrequencyTrie<String> trie = rankedBuilder().build();

        assertAll(() -> assertNull(trie.get("missing")), () -> assertArrayEquals(new String[0], trie.getAll("missing")),
                () -> assertEquals(List.of(), trie.getEntries("missing")));
    }

    /**
     * Verifies that an empty key stores values directly at the root node.
     */
    @Test
    @DisplayName("Empty key stores values at the root node")
    void emptyKeyStoresValuesAtRootNode() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("", "root");
        builder.put("", "root");
        builder.put("", "alternate");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertEquals("root", trie.get("")),
                () -> assertArrayEquals(new String[] { "root", "alternate" }, trie.getAll("")),
                () -> assertEquals(List.of(new ValueCount<String>("root", 2), new ValueCount<String>("alternate", 1)),
                        trie.getEntries("")));
    }

    /**
     * Verifies that values stored on an internal node remain local to that node.
     */
    @Test
    @DisplayName("Internal-node values remain local to that node")
    void internalNodeValuesRemainLocalToThatNode() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("run", "verb");
        builder.put("run", "verb");
        builder.put("run", "noun");

        builder.put("runner", "noun");
        builder.put("runner", "agent");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertEquals("verb", trie.get("run")),
                () -> assertArrayEquals(new String[] { "verb", "noun" }, trie.getAll("run")),
                () -> assertEquals("noun", trie.get("runner")),
                () -> assertArrayEquals(new String[] { "noun", "agent" }, trie.getAll("runner")));
    }

    /**
     * Verifies that a missing path below an existing prefix returns empty results.
     */
    @Test
    @DisplayName("Missing path below existing prefix returns empty results")
    void missingPathBelowExistingPrefixReturnsEmptyResults() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("run", "verb");
        builder.put("runner", "noun");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertNull(trie.get("rune")), () -> assertArrayEquals(new String[0], trie.getAll("rune")),
                () -> assertEquals(List.of(), trie.getEntries("rune")));
    }

    /**
     * Verifies that values are returned in descending frequency order.
     */
    @Test
    @DisplayName("getAll returns values ordered by descending local frequency")
    void getAllReturnsValuesOrderedByDescendingLocalFrequency() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("house", "noun");
        builder.put("house", "noun");
        builder.put("house", "noun");
        builder.put("house", "verb");
        builder.put("house", "adjective");
        builder.put("house", "verb");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertEquals("noun", trie.get("house")),
                () -> assertArrayEquals(new String[] { "noun", "verb", "adjective" }, trie.getAll("house")),
                () -> assertEquals(List.of(new ValueCount<String>("noun", 3), new ValueCount<String>("verb", 2),
                        new ValueCount<String>("adjective", 1)), trie.getEntries("house")));
    }

    /**
     * Verifies that counted insertion aggregates local frequencies correctly.
     */
    @Test
    @DisplayName("Counted insertion aggregates frequencies correctly")
    void countedInsertionAggregatesFrequenciesCorrectly() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("stem", "noun", 3);
        builder.put("stem", "verb", 2);
        builder.put("stem", "noun", 4);

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertEquals("noun", trie.get("stem")),
                () -> assertArrayEquals(new String[] { "noun", "verb" }, trie.getAll("stem")),
                () -> assertEquals(List.of(new ValueCount<String>("noun", 7), new ValueCount<String>("verb", 2)),
                        trie.getEntries("stem")));
    }

    /**
     * Verifies that {@link FrequencyTrie#getAll(String)} returns a defensive copy.
     */
    @Test
    @DisplayName("getAll returns a defensive copy")
    void getAllReturnsDefensiveCopy() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("alpha", "x");
        builder.put("alpha", "y");

        final FrequencyTrie<String> trie = builder.build();

        final String[] first = trie.getAll("alpha");
        first[0] = "mutated";

        final String[] second = trie.getAll("alpha");

        assertArrayEquals(new String[] { "x", "y" }, second);
    }

    /**
     * Verifies that {@link FrequencyTrie#getEntries(String)} returns an immutable
     * list.
     */
    @Test
    @DisplayName("getEntries returns immutable list")
    void getEntriesReturnsImmutableList() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("alpha", "x");
        builder.put("alpha", "x");
        builder.put("alpha", "y");

        final FrequencyTrie<String> trie = builder.build();
        final List<ValueCount<String>> entries = trie.getEntries("alpha");

        assertThrows(UnsupportedOperationException.class, () -> entries.add(new ValueCount<String>("z", 1)));
    }

    /**
     * Verifies that equal frequencies prefer the shorter string representation.
     */
    @Test
    @DisplayName("Equal frequencies prefer shorter string representation")
    void equalFrequenciesPreferShorterStringRepresentation() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("k", "longer");
        builder.put("k", "x");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertEquals("x", trie.get("k")),
                () -> assertArrayEquals(new String[] { "x", "longer" }, trie.getAll("k")),
                () -> assertEquals(List.of(new ValueCount<String>("x", 1), new ValueCount<String>("longer", 1)),
                        trie.getEntries("k")));
    }

    /**
     * Verifies that equal frequencies and equal string lengths prefer the
     * lexicographically lower string representation.
     */
    @Test
    @DisplayName("Equal frequencies and lengths prefer lexicographically lower string")
    void equalFrequenciesAndLengthsPreferLexicographicallyLowerString() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("k", "bb");
        builder.put("k", "aa");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertEquals("aa", trie.get("k")),
                () -> assertArrayEquals(new String[] { "aa", "bb" }, trie.getAll("k")),
                () -> assertEquals(List.of(new ValueCount<String>("aa", 1), new ValueCount<String>("bb", 1)),
                        trie.getEntries("k")));
    }

    /**
     * Verifies that if textual representations are equal, first-seen order remains
     * stable.
     */
    @Test
    @DisplayName("Equal textual representations preserve first-seen order")
    void equalTextualRepresentationsPreserveFirstSeenOrder() {
        final FrequencyTrie.Builder<Object> builder = new FrequencyTrie.Builder<Object>(Object[]::new,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        final Object first = new Object() {
            @Override
            public String toString() {
                return "same";
            }
        };

        final Object second = new Object() {
            @Override
            public String toString() {
                return "same";
            }
        };

        builder.put("k", first);
        builder.put("k", second);

        final FrequencyTrie<Object> trie = builder.build();

        assertAll(() -> assertSame(first, trie.get("k")),
                () -> assertArrayEquals(new Object[] { first, second }, trie.getAll("k")));
    }

    /**
     * Verifies ranked reduction. Equivalent ranked local results should merge even
     * if absolute frequencies differ.
     */
    @Test
    @Tag("reduction")
    @DisplayName("Ranked reduction merges subtrees with equivalent ranked getAll semantics")
    void rankedReductionMergesEquivalentRankedGetAllSubtrees() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("ab", "X");
        builder.put("ab", "X");
        builder.put("ab", "Y");

        builder.put("cb", "X");
        builder.put("cb", "Y");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertEquals("X", trie.get("ab")),
                () -> assertArrayEquals(new String[] { "X", "Y" }, trie.getAll("ab")),
                () -> assertEquals("X", trie.get("cb")),
                () -> assertArrayEquals(new String[] { "X", "Y" }, trie.getAll("cb")));
    }

    /**
     * Verifies that ranked reduction does not merge nodes when ranked ordering
     * differs.
     */
    @Test
    @Tag("reduction")
    @DisplayName("Ranked reduction keeps nodes separate when getAll ordering differs")
    void rankedReductionKeepsNodesSeparateWhenOrderingDiffers() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("ab", "X");
        builder.put("ab", "X");
        builder.put("ab", "Y");

        builder.put("cb", "Y");
        builder.put("cb", "Y");
        builder.put("cb", "X");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertArrayEquals(new String[] { "X", "Y" }, trie.getAll("ab")),
                () -> assertArrayEquals(new String[] { "Y", "X" }, trie.getAll("cb")));
    }

    /**
     * Verifies that unordered reduction may merge nodes even when ranked ordering
     * differs, because only the value set matters to the signature.
     */
    @Test
    @Tag("reduction")
    @DisplayName("Unordered reduction merges nodes with the same getAll value set")
    void unorderedReductionMergesNodesWithSameGetAllValueSet() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(String[]::new,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS);

        builder.put("ab", "X");
        builder.put("ab", "X");
        builder.put("ab", "Y");

        builder.put("cb", "Y");
        builder.put("cb", "Y");
        builder.put("cb", "X");

        final FrequencyTrie<String> trie = builder.build();

        final String[] ab = trie.getAll("ab");
        final String[] cb = trie.getAll("cb");

        assertAll(() -> assertNotNull(ab), () -> assertNotNull(cb), () -> assertArrayEquals(ab, cb),
                () -> assertEquals(trie.get("ab"), trie.get("cb")));
    }

    /**
     * Verifies that dominant reduction merges nodes when the local winner satisfies
     * the configured dominance conditions.
     */
    @Test
    @Tag("reduction")
    @DisplayName("Dominant reduction merges nodes with a qualified dominant winner")
    void dominantReductionMergesQualifiedDominantWinnerNodes() {
        final ReductionSettings settings = new ReductionSettings(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS, 75, 3);

        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(String[]::new, settings);

        builder.put("ab", "X");
        builder.put("ab", "X");
        builder.put("ab", "X");
        builder.put("ab", "Y");

        builder.put("cb", "X");
        builder.put("cb", "X");
        builder.put("cb", "X");
        builder.put("cb", "Z");

        final FrequencyTrie<String> trie = builder.build();

        final String[] ab = trie.getAll("ab");
        final String[] cb = trie.getAll("cb");

        assertAll(() -> assertEquals("X", trie.get("ab")), () -> assertEquals("X", trie.get("cb")),
                () -> assertArrayEquals(ab, cb), () -> assertEquals(3, ab.length));
    }

    /**
     * Verifies that dominant reduction does not over-reduce nodes whose local
     * winner is not dominant enough.
     */
    @Test
    @Tag("reduction")
    @DisplayName("Dominant reduction falls back when winner is not dominant enough")
    void dominantReductionFallsBackWhenWinnerIsNotDominantEnough() {
        final ReductionSettings settings = new ReductionSettings(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS, 75, 3);

        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(String[]::new, settings);

        builder.put("ab", "X");
        builder.put("ab", "X");
        builder.put("ab", "Y");

        builder.put("cb", "X");
        builder.put("cb", "Z");
        builder.put("cb", "Z");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertEquals("X", trie.get("ab")),
                () -> assertArrayEquals(new String[] { "X", "Y" }, trie.getAll("ab")),
                () -> assertEquals("Z", trie.get("cb")),
                () -> assertArrayEquals(new String[] { "Z", "X" }, trie.getAll("cb")));
    }

    /**
     * Verifies that local values on internal nodes participate in reduction.
     */
    @Test
    @Tag("reduction")
    @DisplayName("Reduction takes internal-node local values into account")
    void reductionTakesInternalNodeLocalValuesIntoAccount() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("a", "prefix-a");
        builder.put("a", "prefix-a");
        builder.put("ab", "leaf");

        builder.put("c", "prefix-c");
        builder.put("c", "prefix-c");
        builder.put("cb", "leaf");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertEquals("prefix-a", trie.get("a")), () -> assertEquals("prefix-c", trie.get("c")),
                () -> assertArrayEquals(new String[] { "leaf" }, trie.getAll("ab")),
                () -> assertArrayEquals(new String[] { "leaf" }, trie.getAll("cb")));
    }

    /**
     * Verifies that equivalent descendants do not override differing internal-node
     * semantics.
     */
    @Test
    @Tag("reduction")
    @DisplayName("Equivalent descendants do not override differing internal-node semantics")
    void equivalentDescendantsDoNotOverrideDifferingInternalNodeSemantics() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("a", "left");
        builder.put("ab", "child");

        builder.put("c", "right");
        builder.put("cb", "child");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertEquals("left", trie.get("a")), () -> assertEquals("right", trie.get("c")),
                () -> assertArrayEquals(new String[] { "child" }, trie.getAll("ab")),
                () -> assertArrayEquals(new String[] { "child" }, trie.getAll("cb")));
    }

    /**
     * Verifies that subtree reduction materially decreases compiled trie size for a
     * dataset with repeated equivalent suffix structures.
     */
    @Test
    @Tag("reduction")
    @DisplayName("Reduction materially decreases compiled trie size for repeated equivalent suffixes")
    void reductionMateriallyDecreasesCompiledTrieSizeForRepeatedEquivalentSuffixes() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        for (int index = 0; index < 20; index++) {
            final String prefix = "p" + index;

            builder.put(prefix, "prefix");
            builder.put(prefix + "x", "mid");
            builder.put(prefix + "xy", "leaf");
            builder.put(prefix + "xz", "leaf-alt");
        }

        final int buildTimeSize = builder.buildTimeSize();
        final FrequencyTrie<String> trie = builder.build();
        final int compiledSize = trie.size();
        final double reductionRatio = 1.0d - ((double) compiledSize / (double) buildTimeSize);

        assertAll(() -> assertEquals("prefix", trie.get("p0")), () -> assertEquals("mid", trie.get("p0x")),
                () -> assertArrayEquals(new String[] { "leaf" }, trie.getAll("p0xy")),
                () -> assertArrayEquals(new String[] { "leaf-alt" }, trie.getAll("p0xz")),
                () -> assertEquals("prefix", trie.get("p19")), () -> assertEquals("mid", trie.get("p19x")),
                () -> assertArrayEquals(new String[] { "leaf" }, trie.getAll("p19xy")),
                () -> assertArrayEquals(new String[] { "leaf-alt" }, trie.getAll("p19xz")),
                () -> assertTrue(buildTimeSize > 0,
                        () -> "Build-time size must be positive, but was " + buildTimeSize + '.'),
                () -> assertTrue(compiledSize > 0,
                        () -> "Compiled trie size must be positive, but was " + compiledSize + '.'),
                () -> assertTrue(compiledSize < buildTimeSize,
                        () -> "Reduction must decrease the node count. Build-time size=" + buildTimeSize
                                + ", compiled size=" + compiledSize + '.'),
                () -> assertTrue(reductionRatio > 0.0d,
                        () -> "Reduction ratio must be positive, but was " + reductionRatio + '.'),
                () -> assertTrue(reductionRatio >= 0.50d,
                        () -> "Expected at least 50% reduction, but build-time size was " + buildTimeSize
                                + " and compiled size was " + compiledSize + ", giving ratio " + reductionRatio + '.'));
    }

    /**
     * Verifies that serialization preserves trie semantics and canonical size.
     *
     * @throws IOException if test I/O fails unexpectedly
     */
    @Test
    @Tag("persistence")
    @DisplayName("writeTo and readFrom round-trip trie content")
    void writeToAndReadFromRoundTripTrieContent() throws IOException {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("", "root", 2);
        builder.put("run", "verb", 3);
        builder.put("run", "noun", 1);
        builder.put("runner", "noun", 2);
        builder.put("cab", "X", 2);
        builder.put("cab", "Y", 1);
        builder.put("dab", "X", 1);
        builder.put("dab", "Y", 1);

        final FrequencyTrie<String> original = builder.build();

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        original.writeTo(outputStream, STRING_CODEC);

        final FrequencyTrie<String> restored = FrequencyTrie
                .readFrom(new ByteArrayInputStream(outputStream.toByteArray()), String[]::new, STRING_CODEC);

        assertAll(() -> assertEquals(original.size(), restored.size()),
                () -> assertEquals(original.get(""), restored.get("")),
                () -> assertArrayEquals(original.getAll(""), restored.getAll("")),
                () -> assertEquals(original.get("run"), restored.get("run")),
                () -> assertArrayEquals(original.getAll("run"), restored.getAll("run")),
                () -> assertEquals(original.getEntries("run"), restored.getEntries("run")),
                () -> assertEquals(original.get("runner"), restored.get("runner")),
                () -> assertArrayEquals(original.getAll("runner"), restored.getAll("runner")),
                () -> assertEquals(original.getEntries("runner"), restored.getEntries("runner")),
                () -> assertEquals(original.get("cab"), restored.get("cab")),
                () -> assertArrayEquals(original.getAll("cab"), restored.getAll("cab")),
                () -> assertEquals(original.getEntries("cab"), restored.getEntries("cab")),
                () -> assertEquals(original.get("dab"), restored.get("dab")),
                () -> assertArrayEquals(original.getAll("dab"), restored.getAll("dab")),
                () -> assertEquals(original.getEntries("dab"), restored.getEntries("dab")),
                () -> assertNull(restored.get("missing")),
                () -> assertArrayEquals(new String[0], restored.getAll("missing")),
                () -> assertEquals(List.of(), restored.getEntries("missing")));
    }

    /**
     * Verifies that persistence methods reject {@code null} arguments.
     *
     * @throws IOException if test I/O fails unexpectedly
     */
    @Test
    @Tag("persistence")
    @DisplayName("writeTo and readFrom reject null arguments")
    void writeToAndReadFromRejectNullArguments() throws IOException {
        final FrequencyTrie<String> trie = rankedBuilder().build();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] serializedEmptyTrie;

        trie.writeTo(outputStream, STRING_CODEC);
        serializedEmptyTrie = outputStream.toByteArray();

        assertAll(() -> assertThrows(NullPointerException.class, () -> trie.writeTo(null, STRING_CODEC)),
                () -> assertThrows(NullPointerException.class, () -> trie.writeTo(new ByteArrayOutputStream(), null)),
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrie.readFrom(null, String[]::new, STRING_CODEC)),
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrie.readFrom(new ByteArrayInputStream(serializedEmptyTrie), null,
                                STRING_CODEC)),
                () -> assertThrows(NullPointerException.class, () -> FrequencyTrie
                        .readFrom(new ByteArrayInputStream(serializedEmptyTrie), String[]::new, null)));
    }

    /**
     * Verifies that deserialization rejects an invalid stream magic header.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom rejects invalid stream magic header")
    void readFromRejectsInvalidStreamMagicHeader() {
        final byte[] bytes = createSerializedStream(0x12345678, 1, 1, 0, new NodeWriter[0]);

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertTrue(exception.getMessage().contains("Unsupported trie stream header"));
    }

    /**
     * Verifies that deserialization rejects an unsupported stream version.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom rejects unsupported stream version")
    void readFromRejectsUnsupportedStreamVersion() {
        final byte[] bytes = createSerializedStream(0x45475452, 999, 1, 0, new NodeWriter[0]);

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertTrue(exception.getMessage().contains("Unsupported trie stream version"));
    }

    /**
     * Verifies that deserialization rejects a negative node count.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom rejects negative node count")
    void readFromRejectsNegativeNodeCount() {
        final byte[] bytes = createSerializedStream(0x45475452, 1, -1, 0, new NodeWriter[0]);

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertTrue(exception.getMessage().contains("Negative node count"));
    }

    /**
     * Verifies that deserialization rejects an invalid root node identifier.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom rejects invalid root node identifier")
    void readFromRejectsInvalidRootNodeIdentifier() {
        final byte[] bytes = createSerializedStream(0x45475452, 1, 1, 1, new NodeWriter[] { dataOutput -> {
            dataOutput.writeInt(0);
            dataOutput.writeInt(0);
        } });

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertTrue(exception.getMessage().contains("Invalid root node id"));
    }

    /**
     * Verifies that deserialization rejects unsorted or duplicate serialized edge
     * labels because compiled lookup relies on binary search over a strictly
     * ascending edge array.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom rejects non-ascending serialized edge labels")
    void readFromRejectsNonAscendingSerializedEdgeLabels() {
        final byte[] bytes = createSerializedStream(0x45475452, 1, 1, 0, new NodeWriter[] { dataOutput -> {
            dataOutput.writeInt(2);
            dataOutput.writeChar('b');
            dataOutput.writeInt(0);
            dataOutput.writeChar('a');
            dataOutput.writeInt(0);
            dataOutput.writeInt(0);
        } });

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertTrue(exception.getMessage().contains("Edge labels must be strictly ascending"));
    }

    /**
     * Verifies that deserialization rejects non-positive stored counts.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom rejects non-positive stored counts")
    void readFromRejectsNonPositiveStoredCounts() {
        final byte[] bytes = createSerializedStream(0x45475452, 1, 1, 0, new NodeWriter[] { dataOutput -> {
            dataOutput.writeInt(0);
            dataOutput.writeInt(1);
            dataOutput.writeUTF("value");
            dataOutput.writeInt(0);
        } });

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertTrue(exception.getMessage().contains("Non-positive stored count"));
    }

    /**
     * Writes one node body into a synthetic serialized trie stream.
     */
    @FunctionalInterface
    private interface NodeWriter {

        /**
         * Writes one serialized node body.
         *
         * @param dataOutput output stream
         * @throws IOException if writing fails
         */
        void write(DataOutputStream dataOutput) throws IOException;
    }

    /**
     * Creates a synthetic serialized trie stream.
     *
     * @param magic      stream magic
     * @param version    stream version
     * @param nodeCount  declared node count
     * @param rootNodeId declared root node identifier
     * @param nodes      node body writers
     * @return serialized bytes
     */
    private static byte[] createSerializedStream(final int magic, final int version, final int nodeCount,
            final int rootNodeId, final NodeWriter[] nodes) {
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

            dataOutputStream.writeInt(magic);
            dataOutputStream.writeInt(version);
            dataOutputStream.writeInt(nodeCount);
            dataOutputStream.writeInt(rootNodeId);

            for (NodeWriter node : nodes) {
                node.write(dataOutputStream);
            }

            dataOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected I/O while building synthetic trie stream.", exception);
        }
    }
}
