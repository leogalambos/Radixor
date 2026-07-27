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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.egothor.stemmer.trie.CompiledNode;
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
@Tag("lookup")
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
     * Codec that records the number of encoded and decoded string values.
     */
    private static final class CountingStringCodec implements FrequencyTrie.ValueStreamCodec<String> {

        /**
         * Number of completed write invocations.
         */
        private int writeCount;

        /**
         * Number of completed read invocations.
         */
        private int readCount;

        /**
         * Writes one string and records the invocation.
         *
         * @param dataOutput destination stream
         * @param value      value to encode
         * @throws IOException if writing fails
         */
        @Override
        public void write(final DataOutputStream dataOutput, final String value) throws IOException {
            dataOutput.writeUTF(value);
            this.writeCount++;
        }

        /**
         * Reads one string and records the invocation.
         *
         * @param dataInput source stream
         * @return decoded string
         * @throws IOException if reading fails
         */
        @Override
        public String read(final DataInputStream dataInput) throws IOException {
            final String value = dataInput.readUTF();
            this.readCount++;
            return value;
        }
    }

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
     * Builds a backward trie whose repeated equal values remain on structurally
     * distinct compiled nodes.
     *
     * @return trie containing two separate but equal shared-value inputs
     */
    private static FrequencyTrie<String> sharedValueTrie() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();
        builder.put("ab", new String("shared"));
        builder.put("xab", "left");
        builder.put("cb", new String("shared"));
        builder.put("ycb", "right");
        return builder.build();
    }

    /**
     * Creates reduction settings with the internal uniform-subtree contraction
     * enabled.
     *
     * @return contraction-enabled settings
     */
    private static ReductionSettings uniformSubtreeContractionSettings() {
        return ReductionSettings.withUniformSubtreeContraction(ReductionSettings
                .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
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
     * Verifies that lookup-time key normalization follows persisted case processing
     * metadata.
     */
    @Test
    @DisplayName("Lookup applies lowercase normalization when metadata requires it")
    void lookupAppliesLowercaseNormalizationWhenMetadataRequiresIt() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                WordTraversalDirection.BACKWARD, CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
        builder.put("house", "noun");
        builder.put("house", "verb");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertEquals("noun", trie.get("HOUSE")),
                () -> assertArrayEquals(new String[] { "noun", "verb" }, trie.getAll("HoUsE")));
    }

    /**
     * Verifies that REMOVE mode strips diacritics both at build time and at lookup
     * time and composes independently with case normalization.
     */
    @Test
    @DisplayName("Diacritic REMOVE mode strips dictionary and lookup keys")
    void diacriticRemoveModeStripsDictionaryAndLookupKeys() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                WordTraversalDirection.BACKWARD, CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT,
                DiacriticProcessingMode.REMOVE);
        builder.put("Příliš", "cz");
        builder.put("žluťoučký", "cz2");
        builder.put("Smørrebrød", "da");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(
                () -> assertEquals("cz", trie.get("PRILIS")),
                () -> assertEquals("cz", trie.get("příliš")),
                () -> assertEquals("cz2", trie.get("zlutoucky")),
                () -> assertEquals("da", trie.get("SMORREBROD")),
                () -> assertArrayEquals(new String[] { "cz" }, trie.getAll("prilis")));
    }

    /**
     * Verifies that fallback diacritic mode is explicitly rejected for now.
     */
    @Test
    @DisplayName("AS_IS_AND_STRIPPED_FALLBACK mode is not supported yet")
    void fallbackDiacriticModeIsNotSupportedYet() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                WordTraversalDirection.BACKWARD, CaseProcessingMode.AS_IS,
                DiacriticProcessingMode.AS_IS_AND_STRIPPED_FALLBACK);

        final UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> builder.put("kůň", "horse"));
        assertTrue(exception.getMessage().contains("not supported yet"));
    }

    /**
     * Verifies that lookup preserves casing when metadata uses AS_IS mode.
     */
    @Test
    @DisplayName("Lookup keeps case-sensitive behavior when metadata is AS_IS")
    void lookupKeepsCaseSensitiveBehaviorWhenMetadataIsAsIs() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                WordTraversalDirection.BACKWARD, CaseProcessingMode.AS_IS);
        builder.put("House", "noun");

        final FrequencyTrie<String> trie = builder.build();

        assertAll(() -> assertEquals("noun", trie.get("House")), () -> assertNull(trie.get("house")),
                () -> assertArrayEquals(new String[] { "noun" }, trie.getAll("House")),
                () -> assertArrayEquals(new String[0], trie.getAll("HOUSE")));
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
     * Verifies that {@link FrequencyTrie#getEntries(String)} short-circuits to a one-item immutable list.
     */
    @Test
    @DisplayName("getEntries returns a one-item list for single stored values")
    void getEntriesReturnsSingleItemListForSingleStoredValue() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("gamma", "only");

        final FrequencyTrie<String> trie = builder.build();

        final List<ValueCount<String>> entries = trie.getEntries("gamma");

        assertAll(() -> assertEquals(List.of(new ValueCount<String>("only", 1)), entries),
                () -> assertThrows(UnsupportedOperationException.class, () -> entries.add(new ValueCount<String>("z", 1))));
    }

    /**
     * Verifies that visitor lookup returns the same deterministic order and counts
     * as the allocating APIs.
     */
    @Test
    @DisplayName("Visitor lookup matches getAll order and getEntries counts")
    void visitorLookupMatchesGetAllOrderAndGetEntriesCounts() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();
        builder.put("house", "noun", 3);
        builder.put("house", "verb", 2);
        builder.put("house", "adjective", 1);
        final FrequencyTrie<String> trie = builder.build();
        final List<String> values = new ArrayList<>();
        final List<Integer> counts = new ArrayList<>();
        final List<Integer> ranks = new ArrayList<>();

        final int visited = trie.getAllNormalized("house", (value, count, rank) -> {
            values.add(value);
            counts.add(count);
            ranks.add(rank);
            return true;
        }, 10);

        assertAll(() -> assertEquals(3, visited),
                () -> assertEquals(List.of("noun", "verb", "adjective"), values),
                () -> assertEquals(List.of(3, 2, 1), counts),
                () -> assertEquals(List.of(0, 1, 2), ranks));
    }

    /**
     * Verifies visitor maximum result and early-stop behavior.
     */
    @Test
    @DisplayName("Visitor lookup honors maxResults and sink early stop")
    void visitorLookupHonorsMaxResultsAndSinkEarlyStop() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();
        builder.put("house", "noun", 3);
        builder.put("house", "verb", 2);
        builder.put("house", "adjective", 1);
        final FrequencyTrie<String> trie = builder.build();
        final List<String> limited = new ArrayList<>();
        final List<String> stopped = new ArrayList<>();

        final int limitedCount = trie.getAllNormalized("house", (value, count, rank) -> {
            limited.add(value);
            return true;
        }, 2);
        final int stoppedCount = trie.getAllNormalized("house", (value, count, rank) -> {
            stopped.add(value);
            return false;
        }, 10);

        assertAll(() -> assertEquals(2, limitedCount),
                () -> assertEquals(List.of("noun", "verb"), limited),
                () -> assertEquals(1, stoppedCount),
                () -> assertEquals(List.of("noun"), stopped));
    }

    /**
     * Verifies visitor zero, negative, missing, and first-result behavior.
     */
    @Test
    @DisplayName("Visitor lookup handles zero, negative, missing, and first-result cases")
    void visitorLookupHandlesBoundaryCases() {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();
        builder.put("house", "noun");
        final FrequencyTrie<String> trie = builder.build();
        final int[] calls = new int[1];

        assertAll(() -> assertEquals(0, trie.getAllNormalized("house", (value, count, rank) -> {
            calls[0]++;
            return true;
        }, 0)),
                () -> assertEquals(0, calls[0]),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> trie.getAllNormalized("house", (value, count, rank) -> true, -1)),
                () -> assertEquals(0, trie.getAllNormalized("missing", (value, count, rank) -> true, 10)),
                () -> assertFalse(trie.getFirstNormalized("missing", (value, count, rank) -> true)),
                () -> assertTrue(trie.getFirstNormalized("house", (value, count, rank) -> {
                    assertEquals("noun", value);
                    assertEquals(1, count);
                    assertEquals(0, rank);
                    return true;
                })));
    }

    /**
     * Verifies visitor API argument validation.
     */
    @Test
    @DisplayName("Visitor lookup rejects null and invalid range arguments")
    void visitorLookupRejectsNullAndInvalidRangeArguments() {
        final FrequencyTrie<String> trie = rankedBuilder().build();
        final char[] key = "house".toCharArray();
        final FrequencyTrie.EntrySink<String> sink = (value, count, rank) -> true;

        assertAll(() -> assertThrows(NullPointerException.class,
                () -> trie.getAllNormalized((char[]) null, 0, 0, sink, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> trie.getAllNormalized(key, 0, key.length, null, 1)),
                () -> assertThrows(IndexOutOfBoundsException.class,
                        () -> trie.getAllNormalized(key, -1, key.length, sink, 1)),
                () -> assertThrows(IndexOutOfBoundsException.class,
                        () -> trie.getAllNormalized(key, 1, key.length, sink, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> trie.getAllNormalized((CharSequence) null, sink, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> trie.getAllNormalized("house", null, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> trie.getNormalized(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> trie.getNormalizedString(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> trie.getAll((CharSequence) null, sink, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> trie.getAll("house", null, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> trie.getAll("house", sink, -1)));
    }

    /**
     * Verifies normalized char-array slices and metadata-aware CharSequence visitor
     * lookup.
     */
    @Test
    @DisplayName("Visitor lookup supports normalized char slices and metadata-aware CharSequence keys")
    void visitorLookupSupportsCharSlicesAndMetadataAwareKeys() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                WordTraversalDirection.BACKWARD, CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
        builder.put("house", "noun");
        final FrequencyTrie<String> trie = builder.build();
        final char[] padded = "__house__".toCharArray();

        assertAll(() -> assertEquals(1,
                trie.getAllNormalized(padded, 2, 5, (value, count, rank) -> {
                    assertEquals("noun", value);
                    return true;
                }, 10)),
                () -> assertFalse(trie.getFirstNormalized("HOUSE", (value, count, rank) -> true),
                        "Normalized lookup must bypass metadata lowercasing."),
                () -> assertNull(trie.getNormalized("HOUSE"),
                        "Normalized preferred lookup must bypass metadata lowercasing."),
                () -> assertNull(trie.getNormalizedString("HOUSE"),
                        "String-specialized normalized lookup must bypass metadata lowercasing."),
                () -> assertEquals("noun", trie.getNormalized("house")),
                () -> assertEquals("noun", trie.getNormalizedString("house")),
                () -> assertTrue(trie.getFirst("HOUSE", (value, count, rank) -> {
                    assertEquals("noun", value);
                    return true;
                })));
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
                () -> assertEquals(original.getFingerprint(), restored.getFingerprint()),
                () -> assertEquals(original.metadata(), restored.metadata()),
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
     * Verifies that the public current-format query reports stream version 7.
     */
    @Test
    @Tag("persistence")
    @DisplayName("Current compiled trie format version is 7")
    void currentCompiledTrieFormatVersionIsSeven() {
        assertEquals(7, FrequencyTrie.currentFormatVersion());
    }

    /**
     * Verifies the raw version 7 header, metadata, and deterministic value-table
     * placement without relying on deserialization.
     *
     * @throws IOException if test I/O fails unexpectedly
     */
    @Test
    @Tag("persistence")
    @DisplayName("Version 7 stream writes header metadata and value table in order")
    void versionSevenStreamWritesHeaderMetadataAndValueTableInOrder() throws IOException {
        final FrequencyTrie<String> currentTrie = sharedValueTrie();
        final TrieMetadata currentMetadata = currentTrie.metadata();
        final TrieMetadata historicalMetadata = new TrieMetadata(6, currentMetadata.traversalDirection(),
                currentMetadata.reductionSettings(), currentMetadata.diacriticProcessingMode(),
                currentMetadata.caseProcessingMode());
        final FrequencyTrie<String> trie = FrequencyTrie.fromCompiled(String[]::new, currentTrie.root(),
                historicalMetadata);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        trie.writeTo(outputStream, STRING_CODEC);

        final DataInputStream dataInput = new DataInputStream(new ByteArrayInputStream(outputStream.toByteArray()));
        final int magic = dataInput.readInt();
        final int version = dataInput.readInt();
        final int nodeCount = dataInput.readInt();
        final int rootNodeId = dataInput.readInt();
        final String metadataText = dataInput.readUTF();
        final int distinctValueCount = dataInput.readInt();
        final String firstValue = dataInput.readUTF();
        final String secondValue = dataInput.readUTF();
        final String thirdValue = dataInput.readUTF();

        assertAll(() -> assertEquals(0x45475452, magic),
                () -> assertEquals(7, version),
                () -> assertTrue(nodeCount > 0),
                () -> assertTrue(rootNodeId >= 0 && rootNodeId < nodeCount),
                () -> assertTrue(metadataText.contains("\nformatVersion=7\n")),
                () -> assertEquals(6, trie.metadata().formatVersion()),
                () -> assertEquals(3, distinctValueCount),
                () -> assertArrayEquals(new String[] { "shared", "left", "right" },
                        new String[] { firstValue, secondValue, thirdValue }));
    }

    /**
     * Verifies that version 7 serialization deduplicates equal values represented
     * by distinct Java objects.
     *
     * @throws IOException if test I/O fails unexpectedly
     */
    @Test
    @Tag("persistence")
    @DisplayName("Version 7 writer encodes each equality-distinct value once")
    void versionSevenWriterEncodesEachEqualityDistinctValueOnce() throws IOException {
        final FrequencyTrie<String> trie = sharedValueTrie();
        final CountingStringCodec countingCodec = new CountingStringCodec();

        trie.writeTo(new ByteArrayOutputStream(), countingCodec);

        assertEquals(3, countingCodec.writeCount);
    }

    /**
     * Verifies that version 7 deserialization invokes the value codec once per
     * table entry rather than once per node-local slot.
     *
     * @throws IOException if test I/O fails unexpectedly
     */
    @Test
    @Tag("persistence")
    @DisplayName("Version 7 reader decodes each table value once")
    void versionSevenReaderDecodesEachTableValueOnce() throws IOException {
        final FrequencyTrie<String> original = sharedValueTrie();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        original.writeTo(outputStream, STRING_CODEC);
        final CountingStringCodec countingCodec = new CountingStringCodec();

        final FrequencyTrie<String> restored = FrequencyTrie.readFrom(
                new ByteArrayInputStream(outputStream.toByteArray()), String[]::new, countingCodec);

        assertAll(() -> assertEquals(3, countingCodec.readCount),
                () -> assertEquals("shared", restored.get("ab")),
                () -> assertEquals("shared", restored.get("cb")),
                () -> assertEquals("left", restored.get("xab")),
                () -> assertEquals("right", restored.get("ycb")),
                () -> assertNull(restored.get("missing")));
    }

    /**
     * Verifies that repeated version 7 table references become direct shared value
     * references in the final compiled node arrays.
     *
     * @throws IOException if test I/O fails unexpectedly
     */
    @Test
    @Tag("persistence")
    @DisplayName("Version 7 materializes shared values directly in compiled nodes")
    void versionSevenMaterializesSharedValuesDirectlyInCompiledNodes() throws IOException {
        final FrequencyTrie<String> original = sharedValueTrie();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        original.writeTo(outputStream, STRING_CODEC);

        final FrequencyTrie<String> restored = FrequencyTrie.readFrom(
                new ByteArrayInputStream(outputStream.toByteArray()), String[]::new, STRING_CODEC);
        final CompiledNode<String> suffixBNode = restored.root().findChild('b');
        final CompiledNode<String> abNode = suffixBNode.findChild('a');
        final CompiledNode<String> cbNode = suffixBNode.findChild('c');
        final String abValue = abNode.orderedValues()[0];
        final String cbValue = cbNode.orderedValues()[0];

        assertAll(() -> assertSame(restored.get("ab"), restored.get("cb")),
                () -> assertSame(abValue, cbValue),
                () -> assertSame(restored.get("ab"), abValue),
                () -> assertEquals(String[].class, abNode.orderedValues().getClass()),
                () -> assertEquals(String[].class, cbNode.orderedValues().getClass()),
                () -> assertEquals("left", restored.get("xab")),
                () -> assertEquals("right", restored.get("ycb")),
                () -> assertNull(restored.get("missing")));
    }

    /**
     * Verifies that metadata-aware version 7 reads receive parsed metadata before
     * decoding and materialize shared final values directly in compiled nodes.
     *
     * @throws IOException if test I/O fails unexpectedly
     */
    @Test
    @Tag("persistence")
    @DisplayName("Metadata-aware version 7 reader materializes shared final values")
    void metadataAwareVersionSevenReaderMaterializesSharedFinalValues() throws IOException {
        final FrequencyTrie<String> original = sharedValueTrie();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        original.writeTo(outputStream, STRING_CODEC);
        final AtomicInteger readCount = new AtomicInteger();
        final AtomicInteger observedMetadataVersion = new AtomicInteger();

        final FrequencyTrie<StringBuilder> restored = FrequencyTrie.readFromWithMetadata(
                new ByteArrayInputStream(outputStream.toByteArray()), StringBuilder[]::new,
                (dataInput, metadata) -> {
                    observedMetadataVersion.set(metadata.formatVersion());
                    readCount.incrementAndGet();
                    return new StringBuilder(dataInput.readUTF());
                }, -1);
        final CompiledNode<StringBuilder> suffixBNode = restored.root().findChild('b');
        final CompiledNode<StringBuilder> abNode = suffixBNode.findChild('a');
        final CompiledNode<StringBuilder> cbNode = suffixBNode.findChild('c');

        assertAll(() -> assertEquals(7, observedMetadataVersion.get()),
                () -> assertEquals(3, readCount.get()),
                () -> assertSame(restored.get("ab"), restored.get("cb")),
                () -> assertSame(abNode.orderedValues()[0], cbNode.orderedValues()[0]),
                () -> assertEquals(StringBuilder[].class, abNode.orderedValues().getClass()),
                () -> assertEquals("left", restored.get("xab").toString()),
                () -> assertEquals("right", restored.get("ycb").toString()));
    }

    /**
     * Verifies that metadata-aware reading preserves the inline value layout used
     * by every historical stream version from 1 through 6.
     *
     * @throws IOException if test I/O fails unexpectedly
     */
    @Test
    @Tag("persistence")
    @DisplayName("Metadata-aware reader supports inline values in versions 1 through 6")
    void metadataAwareReaderSupportsInlineValuesInVersionsOneThroughSix() throws IOException {
        for (int version = 1; version <= 6; version++) {
            final int historicalVersion = version;
            final byte[] bytes = createSerializedStream(0x45475452, historicalVersion, 1, 0,
                    dataOutput -> writeMetadataForHistoricalVersion(dataOutput, historicalVersion),
                    new NodeWriter[] { dataOutput -> {
                        if (historicalVersion >= 6) {
                            dataOutput.writeBoolean(false);
                        }
                        dataOutput.writeInt(0);
                        dataOutput.writeInt(1);
                        dataOutput.writeUTF("inline-" + historicalVersion);
                        dataOutput.writeInt(1);
                    } });
            final AtomicInteger readCount = new AtomicInteger();
            final AtomicInteger observedMetadataVersion = new AtomicInteger();

            final FrequencyTrie<StringBuilder> trie = FrequencyTrie.readFromWithMetadata(
                    new ByteArrayInputStream(bytes), StringBuilder[]::new,
                    (dataInput, metadata) -> {
                        observedMetadataVersion.set(metadata.formatVersion());
                        readCount.incrementAndGet();
                        return new StringBuilder(dataInput.readUTF());
                    }, -1);

            assertAll(() -> assertEquals(historicalVersion, observedMetadataVersion.get()),
                    () -> assertEquals(1, readCount.get()),
                    () -> assertEquals("inline-" + historicalVersion, trie.get("").toString()));
        }
    }

    /**
     * Verifies fingerprint stability and sensitivity to metadata and trie content.
     */
    @Test
    @DisplayName("Fingerprint reflects metadata and compiled trie content")
    void fingerprintReflectsMetadataAndCompiledTrieContent() {
        final FrequencyTrie.Builder<String> baseBuilderA = rankedBuilder();
        baseBuilderA.put("run", "verb", 3);
        baseBuilderA.put("run", "noun", 1);
        baseBuilderA.put("runner", "noun", 2);
        final FrequencyTrie<String> trieA = baseBuilderA.build();

        final FrequencyTrie.Builder<String> baseBuilderB = rankedBuilder();
        baseBuilderB.put("run", "verb", 3);
        baseBuilderB.put("run", "noun", 1);
        baseBuilderB.put("runner", "noun", 2);
        final FrequencyTrie<String> trieB = baseBuilderB.build();

        final FrequencyTrie.Builder<String> reorderedBuilder = rankedBuilder();
        reorderedBuilder.put("runner", "noun", 2);
        reorderedBuilder.put("run", "noun", 1);
        reorderedBuilder.put("run", "verb", 3);
        final FrequencyTrie<String> reorderedTrie = reorderedBuilder.build();

        final FrequencyTrie.Builder<String> differentContentBuilder = rankedBuilder();
        differentContentBuilder.put("run", "verb", 3);
        differentContentBuilder.put("run", "noun", 2);
        differentContentBuilder.put("runner", "noun", 2);
        final FrequencyTrie<String> differentContentTrie = differentContentBuilder.build();

        final FrequencyTrie.Builder<String> differentMetadataBuilder = new FrequencyTrie.Builder<>(String[]::new,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                WordTraversalDirection.FORWARD, CaseProcessingMode.AS_IS);
        differentMetadataBuilder.put("run", "verb", 3);
        differentMetadataBuilder.put("run", "noun", 1);
        differentMetadataBuilder.put("runner", "noun", 2);
        final FrequencyTrie<String> differentMetadataTrie = differentMetadataBuilder.build();

        final String fingerprintA = trieA.getFingerprint();
        final String fingerprintB = trieB.getFingerprint();
        final String reorderedFingerprint = reorderedTrie.getFingerprint();
        final String differentContentFingerprint = differentContentTrie.getFingerprint();
        final String differentMetadataFingerprint = differentMetadataTrie.getFingerprint();
        final byte[] fingerprintBytes = trieA.copyFingerprintBytes();
        final byte[] secondFingerprintBytes = trieA.copyFingerprintBytes();
        fingerprintBytes[0] = (byte) (fingerprintBytes[0] ^ 0x7F);

        assertAll(() -> assertEquals(fingerprintA, fingerprintB),
                () -> assertEquals(fingerprintA, reorderedFingerprint),
                () -> assertEquals(fingerprintA, trieA.getFingerprint()),
                () -> assertFalse(fingerprintA.isBlank()),
                () -> assertLowercaseSha256Hex(fingerprintA),
                () -> assertEquals(fingerprintA, toLowerHex(secondFingerprintBytes)),
                () -> assertArrayEquals(secondFingerprintBytes, trieA.copyFingerprintBytes()),
                () -> assertFalse(fingerprintA.equals(differentContentFingerprint)),
                () -> assertFalse(fingerprintA.equals(differentMetadataFingerprint)));
    }

    /**
     * Verifies that trie construction does not calculate the canonical fingerprint.
     */
    @Test
    @Tag("fingerprint")
    @DisplayName("Construction does not calculate fingerprint")
    void constructionDoesNotCalculateFingerprint() {
        final AtomicInteger toStringCalls = new AtomicInteger();
        final ObservableValue value = new ObservableValue("observed", toStringCalls);

        directCompiledObservableTrie(value);

        assertEquals(0, toStringCalls.get());
    }

    /**
     * Verifies that ordinary read-only trie operations do not calculate the
     * canonical fingerprint.
     */
    @Test
    @Tag("fingerprint")
    @DisplayName("Ordinary operations do not calculate fingerprint")
    void ordinaryOperationsDoNotCalculateFingerprint() {
        final AtomicInteger toStringCalls = new AtomicInteger();
        final ObservableValue value = new ObservableValue("observed", toStringCalls);
        final FrequencyTrie<ObservableValue> trie = observableTrie(value);
        toStringCalls.set(0);

        final ObservableValue preferred = trie.get("alpha");
        final ObservableValue[] allValues = trie.getAll("alpha");
        final List<ValueCount<ObservableValue>> entries = trie.getEntries("alpha");
        final int trieSize = trie.size();
        final TrieMetadata metadata = trie.metadata();
        final WordTraversalDirection traversalDirection = trie.traversalDirection();

        assertEquals(0, toStringCalls.get());
        assertAll(() -> assertSame(value, preferred),
                () -> assertEquals(1, allValues.length),
                () -> assertSame(value, allValues[0]),
                () -> assertEquals(1, entries.size()),
                () -> assertSame(value, entries.get(0).value()),
                () -> assertEquals(1, entries.get(0).count()),
                () -> assertTrue(trieSize > 0),
                () -> assertEquals(metadata.traversalDirection(), traversalDirection));
    }

    /**
     * Verifies that the first string fingerprint request computes and caches the
     * canonical digest.
     */
    @Test
    @Tag("fingerprint")
    @DisplayName("First getFingerprint calculates and caches fingerprint")
    void firstGetFingerprintCalculatesAndCachesFingerprint() {
        final AtomicInteger toStringCalls = new AtomicInteger();
        final ObservableValue value = new ObservableValue("observed", toStringCalls);
        final FrequencyTrie<ObservableValue> trie = observableTrie(value);
        toStringCalls.set(0);

        final String firstFingerprint = trie.getFingerprint();
        final int callsAfterFirstFingerprint = toStringCalls.get();
        final String secondFingerprint = trie.getFingerprint();
        final byte[] fingerprintBytes = trie.copyFingerprintBytes();

        assertAll(() -> assertTrue(callsAfterFirstFingerprint > 0),
                () -> assertEquals(callsAfterFirstFingerprint, toStringCalls.get()),
                () -> assertEquals(firstFingerprint, secondFingerprint),
                () -> assertEquals(firstFingerprint, toLowerHex(fingerprintBytes)));
    }

    /**
     * Verifies that the raw-byte accessor can initialize the canonical digest cache
     * and still returns defensive copies.
     */
    @Test
    @Tag("fingerprint")
    @DisplayName("copyFingerprintBytes can initialize fingerprint cache")
    void copyFingerprintBytesCanInitializeFingerprintCache() {
        final AtomicInteger toStringCalls = new AtomicInteger();
        final ObservableValue value = new ObservableValue("observed", toStringCalls);
        final FrequencyTrie<ObservableValue> trie = observableTrie(value);
        toStringCalls.set(0);

        final byte[] firstFingerprintBytes = trie.copyFingerprintBytes();
        final int callsAfterFirstCopy = toStringCalls.get();
        final String expectedFingerprint = toLowerHex(firstFingerprintBytes);
        firstFingerprintBytes[0] = (byte) (firstFingerprintBytes[0] ^ 0x7F);

        final byte[] secondFingerprintBytes = trie.copyFingerprintBytes();
        final String fingerprint = trie.getFingerprint();

        assertAll(() -> assertTrue(callsAfterFirstCopy > 0),
                () -> assertEquals(32, firstFingerprintBytes.length),
                () -> assertEquals(callsAfterFirstCopy, toStringCalls.get()),
                () -> assertEquals(expectedFingerprint, toLowerHex(secondFingerprintBytes)),
                () -> assertEquals(expectedFingerprint, fingerprint),
                () -> assertFalse(Arrays.equals(firstFingerprintBytes, secondFingerprintBytes)));
    }

    /**
     * Verifies that concurrent first fingerprint access performs one digest
     * calculation and publishes the same result to all readers.
     *
     * @throws Exception if the worker coordination fails unexpectedly
     */
    @Test
    @Tag("fingerprint")
    @DisplayName("Concurrent first access calculates fingerprint once")
    void concurrentFirstAccessCalculatesFingerprintOnce() throws Exception {
        final AtomicInteger toStringCalls = new AtomicInteger();
        final ObservableValue value = new ObservableValue("observed", toStringCalls);
        final FrequencyTrie<ObservableValue> trie = observableTrie(value);
        final CountDownLatch startWorkers = new CountDownLatch(1);
        final CountDownLatch firstToStringEntered = new CountDownLatch(1);
        final CountDownLatch releaseFirstToString = new CountDownLatch(1);
        final int workerCount = 8;
        final ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        final List<Future<String>> futures = new ArrayList<>(workerCount);
        toStringCalls.set(0);
        value.blockFirstToStringInvocation(firstToStringEntered, releaseFirstToString);

        try {
            for (int workerIndex = 0; workerIndex < workerCount; workerIndex++) {
                final int fingerprintAccessIndex = workerIndex;
                futures.add(executorService.submit(() -> {
                    startWorkers.await();
                    if (fingerprintAccessIndex % 2 == 0) {
                        return trie.getFingerprint();
                    }
                    return toLowerHex(trie.copyFingerprintBytes());
                }));
            }

            startWorkers.countDown();
            assertTrue(firstToStringEntered.await(5, TimeUnit.SECONDS),
                    "Timed out waiting for the first fingerprint calculation to start.");
            releaseFirstToString.countDown();

            final Set<String> observedFingerprints = new HashSet<>();
            observedFingerprints.add(futures.get(0).get(5, TimeUnit.SECONDS));
            final int callsAfterFirstCompletedAccess = toStringCalls.get();
            for (int index = 1; index < futures.size(); index++) {
                observedFingerprints.add(futures.get(index).get(5, TimeUnit.SECONDS));
            }

            final String fingerprint = trie.getFingerprint();
            final byte[] fingerprintBytes = trie.copyFingerprintBytes();

            assertAll(() -> assertEquals(1, observedFingerprints.size()),
                    () -> assertTrue(callsAfterFirstCompletedAccess > 0),
                    () -> assertEquals(callsAfterFirstCompletedAccess, toStringCalls.get()),
                    () -> assertTrue(observedFingerprints.contains(fingerprint)),
                    () -> assertEquals(fingerprint, toLowerHex(fingerprintBytes)));
        } finally {
            releaseFirstToString.countDown();
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS),
                    "Timed out waiting for fingerprint worker shutdown.");
        }
    }

    private static void assertLowercaseSha256Hex(final String fingerprint) {
        assertEquals(64, fingerprint.length());
        for (int index = 0; index < fingerprint.length(); index++) {
            final char character = fingerprint.charAt(index);
            final boolean digit = character >= '0' && character <= '9';
            final boolean lowercaseHex = character >= 'a' && character <= 'f';
            assertTrue(digit || lowercaseHex, "Invalid fingerprint character at index " + index + '.');
        }
    }

    private static String toLowerHex(final byte[] bytes) {
        final StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            builder.append(Character.forDigit((item >>> 4) & 0x0F, 16));
            builder.append(Character.forDigit(item & 0x0F, 16));
        }
        return builder.toString();
    }

    /**
     * Builds a small trie containing one observable value.
     *
     * @param value observable value to store
     * @return compiled trie containing {@code value}
     */
    private static FrequencyTrie<ObservableValue> observableTrie(final ObservableValue value) {
        final FrequencyTrie.Builder<ObservableValue> builder = new FrequencyTrie.Builder<>(ObservableValue[]::new,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        builder.put("alpha", value);
        return builder.build();
    }

    /**
     * Builds a one-node compiled trie directly so constructor-time fingerprinting is
     * observable without build-time value ordering.
     *
     * @param value observable value to store at the root
     * @return compiled trie containing {@code value}
     */
    @SuppressWarnings("unchecked")
    private static FrequencyTrie<ObservableValue> directCompiledObservableTrie(final ObservableValue value) {
        final CompiledNode<ObservableValue>[] children = new CompiledNode[0];
        final CompiledNode<ObservableValue> root = new CompiledNode<>(new char[0], children,
                new ObservableValue[] { value }, new int[] { 1 });
        final TrieMetadata metadata = TrieMetadata.forCompilation(WordTraversalDirection.BACKWARD,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                DiacriticProcessingMode.AS_IS, CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
        return FrequencyTrie.fromCompiled(ObservableValue[]::new, root, metadata);
    }

    /**
     * Test value whose textual representation records every invocation.
     */
    private static final class ObservableValue {

        /**
         * Deterministic text returned by {@link #toString()}.
         */
        private final String text;

        /**
         * Invocation counter owned by the current test.
         */
        private final AtomicInteger toStringCalls;

        /**
         * Optional first-invocation blocker used by the concurrency test.
         */
        private volatile FirstToStringBlocker firstToStringBlocker;

        /**
         * Creates an observable value.
         *
         * @param text          deterministic textual representation
         * @param toStringCalls invocation counter
         */
        ObservableValue(final String text, final AtomicInteger toStringCalls) {
            this.text = text;
            this.toStringCalls = toStringCalls;
        }

        /**
         * Blocks the next {@link #toString()} invocation until the supplied release
         * latch opens.
         *
         * @param entered latch counted down when the invocation reaches the blocker
         * @param release latch that releases the blocked invocation
         */
        void blockFirstToStringInvocation(final CountDownLatch entered, final CountDownLatch release) {
            this.firstToStringBlocker = new FirstToStringBlocker(entered, release);
        }

        /**
         * Returns the deterministic value text while recording the invocation.
         *
         * @return deterministic value text
         */
        @Override
        public String toString() {
            this.toStringCalls.incrementAndGet();
            final FirstToStringBlocker blocker = this.firstToStringBlocker;
            if (blocker != null) {
                blocker.blockFirstInvocation();
            }
            return this.text;
        }

        /**
         * Compares observable values by deterministic text only.
         *
         * @param other other object
         * @return {@code true} when both values have the same deterministic text
         */
        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ObservableValue)) {
                return false;
            }
            final ObservableValue that = (ObservableValue) other;
            return this.text.equals(that.text);
        }

        /**
         * Returns a stable hash code based on deterministic text.
         *
         * @return stable hash code
         */
        @Override
        public int hashCode() {
            return this.text.hashCode();
        }
    }

    /**
     * One-shot latch pair used to hold the first observable {@code toString()}
     * invocation inside fingerprint calculation.
     */
    private static final class FirstToStringBlocker {

        /**
         * Latch signaled when the first invocation reaches the blocker.
         */
        private final CountDownLatch entered;

        /**
         * Latch that releases the blocked invocation.
         */
        private final CountDownLatch release;

        /**
         * Ensures only one invocation blocks.
         */
        private final AtomicInteger blockClaims = new AtomicInteger();

        /**
         * Creates a one-shot blocker.
         *
         * @param entered latch signaled when blocking starts
         * @param release latch that releases the blocked invocation
         */
        FirstToStringBlocker(final CountDownLatch entered, final CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        /**
         * Blocks only the first caller until the release latch opens.
         */
        void blockFirstInvocation() {
            if (this.blockClaims.compareAndSet(0, 1)) {
                this.entered.countDown();
                try {
                    this.release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while coordinating observable toString().",
                            exception);
                }
            }
        }
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
     * Verifies that reading a compiled trie with a negative max-expanded override
     * smaller than -1 is rejected.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom rejects invalid maxExpandedIndex override")
    void readFromRejectsInvalidMaxExpandedIndexOverride() {
        final byte[] bytes = createSerializedStream(0x45475452, 1, 1, 0, new NodeWriter[] { dataOutput -> {
            dataOutput.writeInt(0);
            dataOutput.writeInt(0);
        } });

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC, -2));

        assertEquals("maxExpandedIndex must be >= -1.", exception.getMessage());
    }

    /**
     * Verifies that the max-expanded override controls dense lookup materialization
     * while preserving lookup semantics.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom respects dense lookup max-expanded index override")
    void readFromRespectsDenseLookupMaxExpandedIndexOverride() throws IOException {
        final FrequencyTrie.Builder<String> builder = rankedBuilder();

        builder.put("a", "a");
        builder.put("b", "b");
        builder.put("c", "c");
        builder.put("d", "d");

        final FrequencyTrie<String> original = builder.build();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        original.writeTo(outputStream, STRING_CODEC);
        final byte[] serializedTrie = outputStream.toByteArray();

        final FrequencyTrie<String> defaultDense = FrequencyTrie.readFrom(new ByteArrayInputStream(serializedTrie), String[]::new,
                STRING_CODEC);
        final FrequencyTrie<String> defaultDenseByNegative = FrequencyTrie.readFrom(new ByteArrayInputStream(serializedTrie),
                String[]::new, STRING_CODEC, -1);
        final FrequencyTrie<String> disabledDense = FrequencyTrie.readFrom(new ByteArrayInputStream(serializedTrie), String[]::new,
                STRING_CODEC, 0);

        assertAll(
                () -> assertTrue(defaultDense.root().hasDenseLookup(),
                        "Default read should enable dense lookup for compact first-level edges."),
                () -> assertTrue(defaultDenseByNegative.root().hasDenseLookup(),
                        "Negative override should use the default dense lookup span."),
                () -> assertFalse(disabledDense.root().hasDenseLookup(),
                        "Zero override should disable dense lookup tables."),
                () -> assertEquals(original.get("a"), disabledDense.get("a")),
                () -> assertEquals(original.get("b"), disabledDense.get("b")),
                () -> assertEquals(original.get("c"), disabledDense.get("c")),
                () -> assertEquals(original.get("d"), disabledDense.get("d")),
                () -> assertEquals(original.get("z"), disabledDense.get("z")));
    }

    /**
     * Verifies that uniform subtree contraction is not part of the default generic
     * trie semantics.
     */
    @Test
    @Tag("reduction")
    @DisplayName("Default reduction keeps exact lookup semantics for uniform subtrees")
    void shouldKeepExactLookupWhenUniformSubtreeContractionIsDisabled() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                WordTraversalDirection.FORWARD);
        builder.put("aa", "x");
        builder.put("ab", "x");

        final FrequencyTrie<String> trie = builder.build();

        assertAll("exact lookup",
                () -> assertEquals("x", trie.get("aa")),
                () -> assertEquals("x", trie.get("ab")),
                () -> assertNull(trie.get("az")),
                () -> assertFalse(trie.root().findChild('a').acceptsRemainingInput()));
    }

    /**
     * Verifies that the internal uniform-subtree contraction replaces a uniform
     * non-leaf subtree with an accepting leaf.
     */
    @Test
    @Tag("reduction")
    @DisplayName("Uniform subtree contraction replaces uniform internal subtree with accepting leaf")
    void shouldContractUniformInternalSubtreeIntoAcceptingLeaf() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new,
                uniformSubtreeContractionSettings(), WordTraversalDirection.FORWARD);
        builder.put("aa", "x");
        builder.put("ab", "x");
        builder.put("ba", "y");

        final FrequencyTrie<String> trie = builder.build();

        assertAll("contracted lookup",
                () -> assertEquals(3, trie.size()),
                () -> assertTrue(trie.root().findChild('a').acceptsRemainingInput()),
                () -> assertEquals("x", trie.get("a")),
                () -> assertEquals("x", trie.get("aa")),
                () -> assertEquals("x", trie.get("ab")),
                () -> assertEquals("x", trie.get("az")),
                () -> assertEquals("y", trie.get("bz")),
                () -> assertNull(trie.get("c")));
    }

    /**
     * Verifies that binary persistence preserves accepting leaf semantics.
     */
    @Test
    @Tag("persistence")
    @DisplayName("Binary round trip preserves uniform subtree accepting leaf")
    void shouldPreserveUniformSubtreeContractionAcrossBinaryRoundTrip() throws IOException {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new,
                uniformSubtreeContractionSettings(), WordTraversalDirection.FORWARD);
        builder.put("aa", "x");
        builder.put("ab", "x");
        builder.put("ba", "y");
        final FrequencyTrie<String> original = builder.build();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        original.writeTo(outputStream, STRING_CODEC);

        final FrequencyTrie<String> restored = FrequencyTrie
                .readFrom(new ByteArrayInputStream(outputStream.toByteArray()), String[]::new, STRING_CODEC);

        assertAll("restored contraction",
                () -> assertTrue(restored.root().findChild('a').acceptsRemainingInput()),
                () -> assertEquals("x", restored.get("az")),
                () -> assertTrue(restored.metadata().reductionSettings().contractUniformSubtrees()));
    }

    /**
     * Verifies that value mapping keeps accepting leaf semantics.
     */
    @Test
    @Tag("reduction")
    @DisplayName("Value mapping preserves uniform subtree accepting leaf")
    void shouldPreserveUniformSubtreeContractionWhenMappingValues() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new,
                uniformSubtreeContractionSettings(), WordTraversalDirection.FORWARD);
        builder.put("aa", "x");
        builder.put("ab", "x");
        builder.put("ba", "y");
        final FrequencyTrie<String> source = builder.build();

        final FrequencyTrie<Integer> mapped = FrequencyTrieBuilders.mapValues(source, Integer[]::new,
                source.metadata().reductionSettings(), String::length);

        assertAll("mapped contraction",
                () -> assertTrue(mapped.root().findChild('a').acceptsRemainingInput()),
                () -> assertEquals(1, mapped.get("az")));
    }

    /**
     * Verifies that cyclic serialized node references are rejected as invalid
     * serialization.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom rejects cyclic serialized node references")
    void readFromRejectsCyclicSerializedNodeReferences() {
        final byte[] bytes = createSerializedStream(0x45475452, 1, 2, 0, new NodeWriter[] {
                dataOutput -> {
                    dataOutput.writeInt(1);
                    dataOutput.writeChar('b');
                    dataOutput.writeInt(1);
                    dataOutput.writeInt(0);
                },
                dataOutput -> {
                    dataOutput.writeInt(1);
                    dataOutput.writeChar('a');
                    dataOutput.writeInt(0);
                    dataOutput.writeInt(0);
                } });

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertTrue(exception.getMessage().contains("cyclic reference detected"));
    }

    /**
     * Verifies that child node references outside the valid serialized range are
     * rejected.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom rejects invalid child node identifiers")
    void readFromRejectsInvalidChildNodeId() {
        final byte[] bytes = createSerializedStream(0x45475452, 1, 1, 0, new NodeWriter[] { dataOutput -> {
            dataOutput.writeInt(1);
            dataOutput.writeChar('a');
            dataOutput.writeInt(3);
            dataOutput.writeInt(0);
        } });

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertTrue(exception.getMessage().contains("Invalid child node id"));
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
     * Verifies that the latest stream version validates textual metadata blocks.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom rejects invalid textual metadata block")
    void readFromRejectsInvalidTextualMetadataBlock() {
        final int version = FrequencyTrie.currentFormatVersion();
        final byte[] bytes = createSerializedStream(0x45475452, version, 1, 0, dataOutput -> {
            dataOutput.writeUTF("not valid metadata");
        }, new NodeWriter[] { dataOutput -> {
            dataOutput.writeInt(0);
            dataOutput.writeInt(0);
        } });

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertTrue(exception.getMessage().contains("Invalid metadata block"));
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
     * Verifies that version 7 deserialization rejects a negative distinct-value
     * count.
     */
    @Test
    @Tag("persistence")
    @DisplayName("Version 7 reader rejects negative value table size")
    void versionSevenReaderRejectsNegativeValueTableSize() {
        final byte[] bytes = createVersionSevenSerializedStream(dataOutput -> dataOutput.writeInt(-1),
                dataOutput -> {
                    // The invalid table size is rejected before node decoding.
                });

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertEquals("Negative distinct value count: -1", exception.getMessage());
    }

    /**
     * Verifies that version 7 deserialization rejects a negative value-table index
     * with complete node-local context.
     */
    @Test
    @Tag("persistence")
    @DisplayName("Version 7 reader rejects negative value table index")
    void versionSevenReaderRejectsNegativeValueTableIndex() {
        final byte[] bytes = createVersionSevenSerializedStream(FrequencyTrieTest::writeSingleValueTable,
                dataOutput -> writeVersionSevenValueNode(dataOutput, -1, 1));

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertEquals("Invalid value table index at node 0, local value 0: -1; table size is 1.",
                exception.getMessage());
    }

    /**
     * Verifies that version 7 deserialization rejects an index equal to the
     * value-table size.
     */
    @Test
    @Tag("persistence")
    @DisplayName("Version 7 reader rejects value table index equal to size")
    void versionSevenReaderRejectsValueTableIndexEqualToSize() {
        final byte[] bytes = createVersionSevenSerializedStream(FrequencyTrieTest::writeSingleValueTable,
                dataOutput -> writeVersionSevenValueNode(dataOutput, 1, 1));

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertEquals("Invalid value table index at node 0, local value 0: 1; table size is 1.",
                exception.getMessage());
    }

    /**
     * Verifies that version 7 deserialization rejects an index greater than the
     * value-table size.
     */
    @Test
    @Tag("persistence")
    @DisplayName("Version 7 reader rejects value table index greater than size")
    void versionSevenReaderRejectsValueTableIndexGreaterThanSize() {
        final byte[] bytes = createVersionSevenSerializedStream(FrequencyTrieTest::writeSingleValueTable,
                dataOutput -> writeVersionSevenValueNode(dataOutput, 2, 1));

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertEquals("Invalid value table index at node 0, local value 0: 2; table size is 1.",
                exception.getMessage());
    }

    /**
     * Verifies that version 7 deserialization retains positive-count validation
     * after resolving a valid table reference.
     */
    @Test
    @Tag("persistence")
    @DisplayName("Version 7 reader rejects zero occurrence count")
    void versionSevenReaderRejectsZeroOccurrenceCount() {
        final byte[] bytes = createVersionSevenSerializedStream(FrequencyTrieTest::writeSingleValueTable,
                dataOutput -> writeVersionSevenValueNode(dataOutput, 0, 0));

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertEquals("Non-positive stored count at node 0, value index 0: 0", exception.getMessage());
    }

    /**
     * Verifies that a truncated version 7 value-table payload remains an
     * {@link EOFException}.
     */
    @Test
    @Tag("persistence")
    @DisplayName("Version 7 reader rejects truncated value table")
    void versionSevenReaderRejectsTruncatedValueTable() {
        final byte[] bytes = createVersionSevenSerializedStream(dataOutput -> {
            dataOutput.writeInt(1);
            dataOutput.writeByte(0);
        }, dataOutput -> {
            // Value decoding fails before node decoding.
        });

        assertThrows(EOFException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));
    }

    /**
     * Verifies that a truncated version 7 node-local table index remains an
     * {@link EOFException}.
     */
    @Test
    @Tag("persistence")
    @DisplayName("Version 7 reader rejects truncated node-local value index")
    void versionSevenReaderRejectsTruncatedNodeLocalValueIndex() {
        final byte[] bytes = createVersionSevenSerializedStream(FrequencyTrieTest::writeSingleValueTable,
                dataOutput -> {
                    dataOutput.writeBoolean(false);
                    dataOutput.writeInt(0);
                    dataOutput.writeInt(1);
                    dataOutput.writeShort(0);
                });

        assertThrows(EOFException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));
    }

    /**
     * Verifies that a truncated version 7 occurrence count remains an
     * {@link EOFException}.
     */
    @Test
    @Tag("persistence")
    @DisplayName("Version 7 reader rejects truncated occurrence count")
    void versionSevenReaderRejectsTruncatedOccurrenceCount() {
        final byte[] bytes = createVersionSevenSerializedStream(FrequencyTrieTest::writeSingleValueTable,
                dataOutput -> {
                    dataOutput.writeBoolean(false);
                    dataOutput.writeInt(0);
                    dataOutput.writeInt(1);
                    dataOutput.writeInt(0);
                    dataOutput.writeShort(1);
                });

        assertThrows(EOFException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));
    }

    /**
     * Verifies that legacy version 1 metadata uses compatibility defaults.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom supports legacy version 1 metadata")
    void readFromSupportsLegacyVersionOneMetadata() throws IOException {
        final byte[] bytes = createSerializedStream(0x45475452, 1, 1, 0, new NodeWriter[] { dataOutput -> {
            dataOutput.writeInt(0);
            dataOutput.writeInt(0);
        } });

        final FrequencyTrie<String> trie = FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC);

        assertEquals(TrieMetadata.legacy(1, WordTraversalDirection.BACKWARD), trie.metadata());
    }

    /**
     * Verifies that legacy version 2 metadata stores traversal direction and uses
     * compatibility defaults for other values.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom supports legacy version 2 metadata")
    void readFromSupportsLegacyVersionTwoMetadata() throws IOException {
        final byte[] bytes = createSerializedStream(0x45475452, 2, 1, 0,
                dataOutput -> dataOutput.writeInt(WordTraversalDirection.FORWARD.ordinal()), new NodeWriter[] { dataOutput -> {
                    dataOutput.writeInt(0);
                    dataOutput.writeInt(0);
                } });

        final FrequencyTrie<String> trie = FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC);

        assertEquals(TrieMetadata.legacy(2, WordTraversalDirection.FORWARD), trie.metadata());
    }

    /**
     * Verifies that version 3 metadata includes reduction and diacritic
     * processing settings.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom parses version 3 metadata")
    void readFromParsesVersionThreeMetadata() throws IOException {
        final ReductionSettings reductionSettings = new ReductionSettings(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS, 81, 4);

        final byte[] bytes = createSerializedStream(0x45475452, 3, 1, 0,
                dataOutput -> {
                    dataOutput.writeInt(WordTraversalDirection.BACKWARD.ordinal());
                    dataOutput.writeInt(reductionSettings.reductionMode().ordinal());
                    dataOutput.writeInt(reductionSettings.dominantWinnerMinPercent());
                    dataOutput.writeInt(reductionSettings.dominantWinnerOverSecondRatio());
                    dataOutput.writeInt(DiacriticProcessingMode.REMOVE.ordinal());
                },
                new NodeWriter[] { dataOutput -> {
                    dataOutput.writeInt(0);
                    dataOutput.writeInt(0);
                } });

        final FrequencyTrie<String> trie = FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC);
        final TrieMetadata metadata = trie.metadata();

        assertAll(() -> assertEquals(3, metadata.formatVersion()),
                () -> assertEquals(WordTraversalDirection.BACKWARD, metadata.traversalDirection()),
                () -> assertEquals(reductionSettings, metadata.reductionSettings()),
                () -> assertEquals(DiacriticProcessingMode.REMOVE, metadata.diacriticProcessingMode()),
                () -> assertEquals(CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT, metadata.caseProcessingMode()));
    }

    /**
     * Verifies that version 4 metadata additionally stores case-processing mode.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom parses version 4 case processing metadata")
    void readFromParsesVersionFourCaseMetadata() throws IOException {
        final ReductionSettings reductionSettings = new ReductionSettings(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS, 75, 3);

        final byte[] bytes = createSerializedStream(0x45475452, 4, 1, 0,
                dataOutput -> {
                    dataOutput.writeInt(WordTraversalDirection.FORWARD.ordinal());
                    dataOutput.writeInt(reductionSettings.reductionMode().ordinal());
                    dataOutput.writeInt(reductionSettings.dominantWinnerMinPercent());
                    dataOutput.writeInt(reductionSettings.dominantWinnerOverSecondRatio());
                    dataOutput.writeInt(DiacriticProcessingMode.AS_IS.ordinal());
                    dataOutput.writeInt(CaseProcessingMode.AS_IS.ordinal());
                },
                new NodeWriter[] { dataOutput -> {
                    dataOutput.writeInt(0);
                    dataOutput.writeInt(0);
                } });

        final FrequencyTrie<String> trie = FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC);
        final TrieMetadata metadata = trie.metadata();

        assertAll(() -> assertEquals(4, metadata.formatVersion()),
                () -> assertEquals(WordTraversalDirection.FORWARD, metadata.traversalDirection()),
                () -> assertEquals(reductionSettings, metadata.reductionSettings()),
                () -> assertEquals(DiacriticProcessingMode.AS_IS, metadata.diacriticProcessingMode()),
                () -> assertEquals(CaseProcessingMode.AS_IS, metadata.caseProcessingMode()));
    }

    /**
     * Verifies that historical text-metadata versions 5 and 6 retain inline value
     * decoding without a value table.
     *
     * @throws IOException if test I/O fails unexpectedly
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom supports inline values in stream versions 5 and 6")
    void readFromSupportsInlineValuesInStreamVersionsFiveAndSix() throws IOException {
        for (int version = 5; version <= 6; version++) {
            final int historicalVersion = version;
            final TrieMetadata historicalMetadata = new TrieMetadata(historicalVersion,
                    WordTraversalDirection.BACKWARD,
                    ReductionSettings.withDefaults(
                            ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                    DiacriticProcessingMode.AS_IS, CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
            final byte[] bytes = createSerializedStream(0x45475452, historicalVersion, 1, 0,
                    dataOutput -> dataOutput.writeUTF(historicalMetadata.toTextBlock()),
                    new NodeWriter[] { dataOutput -> {
                        if (historicalVersion >= 6) {
                            dataOutput.writeBoolean(false);
                        }
                        dataOutput.writeInt(0);
                        dataOutput.writeInt(1);
                        dataOutput.writeUTF("inline");
                        dataOutput.writeInt(2);
                    } });

            final CountingStringCodec countingCodec = new CountingStringCodec();
            final FrequencyTrie<String> trie = FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new,
                    countingCodec);

            assertAll(() -> assertEquals(historicalVersion, trie.metadata().formatVersion()),
                    () -> assertEquals("inline", trie.get("")),
                    () -> assertEquals(List.of(new ValueCount<>("inline", 2)), trie.getEntries("")),
                    () -> assertEquals(1, countingCodec.readCount));
        }
    }

    /**
     * Verifies that invalid legacy metadata ordinals are rejected by validation.
     */
    @Test
    @Tag("persistence")
    @DisplayName("readFrom rejects invalid metadata ordinal in legacy stream")
    void readFromRejectsInvalidLegacyMetadataOrdinal() {
        final byte[] bytes = createSerializedStream(0x45475452, 2, 1, 0,
                dataOutput -> dataOutput.writeInt(999), new NodeWriter[] { dataOutput -> {
                    dataOutput.writeInt(0);
                    dataOutput.writeInt(0);
                } });

        final IOException exception = assertThrows(IOException.class,
                () -> FrequencyTrie.readFrom(new ByteArrayInputStream(bytes), String[]::new, STRING_CODEC));

        assertTrue(exception.getMessage().contains("Invalid traversal direction ordinal"));
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
        return createSerializedStream(magic, version, nodeCount, rootNodeId, dataOutput -> {
            // legacy and text-based versions write their metadata differently.
        }, nodes);
    }

    /**
     * Writes a synthetic serialized trie stream with a metadata writer hook.
     *
     * @param magic      stream magic
     * @param version    stream version
     * @param nodeCount  declared node count
     * @param rootNodeId declared root node identifier
     * @param metadata   version-specific metadata writer
     * @param nodes      node body writers
     * @return serialized bytes
     */
    private static byte[] createSerializedStream(final int magic, final int version, final int nodeCount,
            final int rootNodeId, final MetadataWriter metadata, final NodeWriter[] nodes) {
        return createSerializedStream(magic, version, nodeCount, rootNodeId, metadata, dataOutput -> {
            if (version >= 7) {
                dataOutput.writeInt(0);
            }
        }, nodes);
    }

    /**
     * Creates a synthetic serialized trie stream with metadata and value-table
     * writer hooks.
     *
     * @param magic      stream magic
     * @param version    stream version
     * @param nodeCount  declared node count
     * @param rootNodeId declared root node identifier
     * @param metadata   version-specific metadata writer
     * @param valueTable version-specific value-table writer
     * @param nodes      node body writers
     * @return serialized bytes
     */
    private static byte[] createSerializedStream(final int magic, final int version, final int nodeCount,
            final int rootNodeId, final MetadataWriter metadata, final ValueTableWriter valueTable,
            final NodeWriter[] nodes) {
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

            dataOutputStream.writeInt(magic);
            dataOutputStream.writeInt(version);
            dataOutputStream.writeInt(nodeCount);
            dataOutputStream.writeInt(rootNodeId);
            metadata.write(dataOutputStream);
            valueTable.write(dataOutputStream);

            for (NodeWriter node : nodes) {
                node.write(dataOutputStream);
            }

            dataOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected I/O while building synthetic trie stream.", exception);
        }
    }

    /**
     * Creates one synthetic version 7 stream containing a single declared node.
     *
     * @param valueTable value-table writer
     * @param node       node-body writer
     * @return serialized bytes
     */
    private static byte[] createVersionSevenSerializedStream(final ValueTableWriter valueTable,
            final NodeWriter node) {
        final TrieMetadata metadata = new TrieMetadata(7, WordTraversalDirection.BACKWARD,
                ReductionSettings.withDefaults(
                        ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                DiacriticProcessingMode.AS_IS, CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
        return createSerializedStream(0x45475452, 7, 1, 0,
                dataOutput -> dataOutput.writeUTF(metadata.toTextBlock()), valueTable, new NodeWriter[] { node });
    }

    /**
     * Writes one version 7 value table containing the string {@code value}.
     *
     * @param dataOutput output stream
     * @throws IOException if writing fails
     */
    private static void writeSingleValueTable(final DataOutputStream dataOutput) throws IOException {
        dataOutput.writeInt(1);
        dataOutput.writeUTF("value");
    }

    /**
     * Writes one leaf node with a single version 7 value-table reference.
     *
     * @param dataOutput     output stream
     * @param valueTableIndex referenced table index
     * @param occurrenceCount stored local occurrence count
     * @throws IOException if writing fails
     */
    private static void writeVersionSevenValueNode(final DataOutputStream dataOutput, final int valueTableIndex,
            final int occurrenceCount) throws IOException {
        dataOutput.writeBoolean(false);
        dataOutput.writeInt(0);
        dataOutput.writeInt(1);
        dataOutput.writeInt(valueTableIndex);
        dataOutput.writeInt(occurrenceCount);
    }

    /**
     * Writes the metadata layout used by one historical stream version.
     *
     * @param dataOutput output stream
     * @param version    historical stream version from 1 through 6
     * @throws IOException if writing fails
     */
    private static void writeMetadataForHistoricalVersion(final DataOutputStream dataOutput, final int version)
            throws IOException {
        if (version == 1) {
            return;
        }
        if (version == 2) {
            dataOutput.writeInt(WordTraversalDirection.BACKWARD.ordinal());
            return;
        }

        final ReductionSettings reductionSettings = ReductionSettings
                .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        if (version <= 4) {
            dataOutput.writeInt(WordTraversalDirection.BACKWARD.ordinal());
            dataOutput.writeInt(reductionSettings.reductionMode().ordinal());
            dataOutput.writeInt(reductionSettings.dominantWinnerMinPercent());
            dataOutput.writeInt(reductionSettings.dominantWinnerOverSecondRatio());
            dataOutput.writeInt(DiacriticProcessingMode.AS_IS.ordinal());
            if (version == 4) {
                dataOutput.writeInt(CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT.ordinal());
            }
            return;
        }

        final TrieMetadata metadata = new TrieMetadata(version, WordTraversalDirection.BACKWARD, reductionSettings,
                DiacriticProcessingMode.AS_IS, CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
        dataOutput.writeUTF(metadata.toTextBlock());
    }

    /**
     * Writes one synthetic metadata block.
     */
    @FunctionalInterface
    private interface MetadataWriter {

        /**
         * Writes metadata bytes for one stream version.
         *
         * @param dataOutput output stream
         * @throws IOException if writing fails
         */
        void write(DataOutputStream dataOutput) throws IOException;
    }

    /**
     * Writes the value-table section of a synthetic serialized trie stream.
     */
    @FunctionalInterface
    private interface ValueTableWriter {

        /**
         * Writes one stream's version-specific value-table bytes.
         *
         * @param dataOutput output stream
         * @throws IOException if writing fails
         */
        void write(DataOutputStream dataOutput) throws IOException;
    }
}
