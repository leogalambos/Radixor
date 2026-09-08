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
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.IntFunction;

import org.egothor.stemmer.trie.CompiledNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FrequencyTrieBuilders}.
 *
 * <p>
 * The tested helper reconstructs a writable {@link FrequencyTrie.Builder} from
 * a compiled read-only {@link FrequencyTrie}. These tests verify that the
 * reconstructed builder preserves the observable compiled semantics of the
 * source trie, including local value counts, deterministic ordering, root-local
 * values, traversal across sibling branches, and the ability to continue
 * mutating the reconstructed builder before recompilation.
 */
@DisplayName("FrequencyTrieBuilders")
@Tag("unit")
@Tag("construction")
@Tag("frequency-trie")
class FrequencyTrieBuildersTest {

    /**
     * Shared array factory used by all tries in this test class.
     */
    private static final IntFunction<String[]> ARRAY_FACTORY = String[]::new;

    /**
     * Ranked reduction settings preserving deterministic {@code getAll()}
     * semantics.
     */
    private static final ReductionSettings RANKED_SETTINGS = ReductionSettings
            .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

    /**
     * Verifies that the utility class constructor is intentionally inaccessible and
     * rejects instantiation attempts.
     *
     * @throws Exception if reflection unexpectedly fails
     */
    @Test
    @DisplayName("should reject instantiation of utility class")
    void shouldRejectInstantiationOfUtilityClass() throws Exception {
        final Constructor<FrequencyTrieBuilders> constructor = FrequencyTrieBuilders.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> constructor.newInstance());

        assertAll(() -> assertEquals(AssertionError.class, exception.getCause().getClass()),
                () -> assertEquals("No instances.", exception.getCause().getMessage()));
    }

    /**
     * Verifies that reconstruction of an empty compiled trie yields an empty
     * writable builder whose compiled form remains observably empty.
     */
    @Test
    @DisplayName("should reconstruct empty trie")
    void shouldReconstructEmptyTrie() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(ARRAY_FACTORY, RANKED_SETTINGS);
        final FrequencyTrie<String> original = builder.build();

        final FrequencyTrie.Builder<String> reconstructedBuilder = FrequencyTrieBuilders.copyOf(original, ARRAY_FACTORY,
                RANKED_SETTINGS);
        final FrequencyTrie<String> reconstructed = reconstructedBuilder.build();

        assertTrieStateEquals(original, reconstructed, "");
        assertTrieStateEquals(original, reconstructed, "a");
        assertTrieStateEquals(original, reconstructed, "missing");
    }

    /**
     * Verifies that reconstruction preserves the observable compiled semantics for
     * a representative trie containing root-local values, multiple values on the
     * same node, and several independent branches.
     */
    @Test
    @DisplayName("should preserve get, getAll and getEntries after reconstruction")
    void shouldPreserveCompiledSemanticsAfterReconstruction() {
        final FrequencyTrie<String> original = createRepresentativeTrie();

        final FrequencyTrie.Builder<String> reconstructedBuilder = FrequencyTrieBuilders.copyOf(original, ARRAY_FACTORY,
                RANKED_SETTINGS);
        final FrequencyTrie<String> reconstructed = reconstructedBuilder.build();

        assertTrieStateEquals(original, reconstructed, "");
        assertTrieStateEquals(original, reconstructed, "a");
        assertTrieStateEquals(original, reconstructed, "ab");
        assertTrieStateEquals(original, reconstructed, "abc");
        assertTrieStateEquals(original, reconstructed, "abd");
        assertTrieStateEquals(original, reconstructed, "x");
        assertTrieStateEquals(original, reconstructed, "xy");
        assertTrieStateEquals(original, reconstructed, "missing");
    }

    /**
     * Verifies that values stored directly on the root node are reconstructed
     * exactly, including their counts and ranking order.
     */
    @Test
    @DisplayName("should preserve root-local values")
    void shouldPreserveRootLocalValues() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(ARRAY_FACTORY, RANKED_SETTINGS);
        builder.put("", "root-dominant", 4);
        builder.put("", "root-secondary", 2);
        builder.put("a", "child", 1);

        final FrequencyTrie<String> compiled = builder.build();
        final FrequencyTrie.Builder<String> reconstructedBuilder = FrequencyTrieBuilders.copyOf(compiled, ARRAY_FACTORY,
                RANKED_SETTINGS);
        final FrequencyTrie<String> reconstructed = reconstructedBuilder.build();

        assertAll(() -> assertEquals("root-dominant", reconstructed.get("")),
                () -> assertArrayEquals(new String[] { "root-dominant", "root-secondary" }, reconstructed.getAll("")),
                () -> assertIterableEquals(List.of(new ValueCount<String>("root-dominant", 4),
                        new ValueCount<String>("root-secondary", 2)), reconstructed.getEntries("")));
    }

    /**
     * Verifies that local counts are reconstructed exactly and that deterministic
     * ordering remains preserved after reconstruction.
     *
     * <p>
     * This scenario is important because the helper copies raw ordered values and
     * ordered counts from compiled nodes.
     */
    @Test
    @DisplayName("should preserve local counts and deterministic local ordering")
    void shouldPreserveLocalCountsAndOrdering() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(ARRAY_FACTORY, RANKED_SETTINGS);
        builder.put("node", "bbb", 2);
        builder.put("node", "aa", 2);
        builder.put("node", "c", 2);
        builder.put("node", "winner", 5);

        final FrequencyTrie<String> compiled = builder.build();
        final FrequencyTrie.Builder<String> reconstructedBuilder = FrequencyTrieBuilders.copyOf(compiled, ARRAY_FACTORY,
                RANKED_SETTINGS);
        final FrequencyTrie<String> reconstructed = reconstructedBuilder.build();

        assertAll(() -> assertEquals("winner", reconstructed.get("node")),
                () -> assertArrayEquals(new String[] { "winner", "c", "aa", "bbb" }, reconstructed.getAll("node")),
                () -> assertIterableEquals(
                        List.of(new ValueCount<String>("winner", 5), new ValueCount<String>("c", 2),
                                new ValueCount<String>("aa", 2), new ValueCount<String>("bbb", 2)),
                        reconstructed.getEntries("node")));
    }

    /**
     * Verifies that recursive traversal correctly restores sibling branches sharing
     * a common prefix, which indirectly exercises the internal key-builder
     * backtracking logic used during node copying.
     */
    @Test
    @DisplayName("should preserve sibling branches under a shared prefix")
    void shouldPreserveSiblingBranchesUnderSharedPrefix() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(ARRAY_FACTORY, RANKED_SETTINGS);
        builder.put("car", "car", 4);
        builder.put("card", "card", 3);
        builder.put("care", "care", 2);
        builder.put("cat", "cat", 5);
        builder.put("dog", "dog", 1);

        final FrequencyTrie<String> compiled = builder.build();
        final FrequencyTrie.Builder<String> reconstructedBuilder = FrequencyTrieBuilders.copyOf(compiled, ARRAY_FACTORY,
                RANKED_SETTINGS);
        final FrequencyTrie<String> reconstructed = reconstructedBuilder.build();

        assertTrieStateEquals(compiled, reconstructed, "car");
        assertTrieStateEquals(compiled, reconstructed, "card");
        assertTrieStateEquals(compiled, reconstructed, "care");
        assertTrieStateEquals(compiled, reconstructed, "cat");
        assertTrieStateEquals(compiled, reconstructed, "dog");
        assertTrieStateEquals(compiled, reconstructed, "cab");
    }

    /**
     * Verifies that the reconstructed builder can be further modified and that such
     * modifications do not affect the already compiled source trie.
     */
    @Test
    @DisplayName("should allow further modifications without affecting source trie")
    void shouldAllowFurtherModificationsWithoutAffectingSourceTrie() {
        final FrequencyTrie.Builder<String> originalBuilder = new FrequencyTrie.Builder<String>(ARRAY_FACTORY,
                RANKED_SETTINGS);
        originalBuilder.put("walk", "Ra", 2);
        originalBuilder.put("walked", "Rb", 1);

        final FrequencyTrie<String> source = originalBuilder.build();
        final FrequencyTrie.Builder<String> reconstructedBuilder = FrequencyTrieBuilders.copyOf(source, ARRAY_FACTORY,
                RANKED_SETTINGS);

        reconstructedBuilder.put("walk", "Rc", 4);
        reconstructedBuilder.put("walker", "Rd", 3);

        final FrequencyTrie<String> modified = reconstructedBuilder.build();

        assertAll(
                () -> assertIterableEquals(List.of(new ValueCount<String>("Ra", 2)), source.getEntries("walk"),
                        "Source trie must remain unchanged."),
                () -> assertEquals(null, source.get("walker"), "Source trie must not gain newly inserted keys."),
                () -> assertEquals("Rc", modified.get("walk")),
                () -> assertIterableEquals(List.of(new ValueCount<String>("Rc", 4), new ValueCount<String>("Ra", 2)),
                        modified.getEntries("walk")),
                () -> assertEquals("Rd", modified.get("walker")),
                () -> assertIterableEquals(List.of(new ValueCount<String>("Rd", 3)), modified.getEntries("walker")),
                () -> assertIterableEquals(List.of(new ValueCount<String>("Rb", 1)), modified.getEntries("walked")));
    }

    /**
     * Contraction-enabled reduction settings that collapse uniform subtrees into
     * accepting leaves (the production-style generalization).
     */
    private static final ReductionSettings CONTRACTING_SETTINGS = ReductionSettings.withUniformSubtreeContraction(
            ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS));

    private static FrequencyTrie<String> contractedSuffixTrie() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(ARRAY_FACTORY,
                CONTRACTING_SETTINGS);
        // Two suffix families with distinct values keep the root non-uniform, so
        // contraction stops at the per-suffix nodes ("-s" -> accepting X, "-x" ->
        // accepting Y) instead of collapsing the whole trie into a root that
        // accepts everything.
        for (final String key : new String[] { "as", "bs", "cs", "ds", "es", "fs" }) {
            builder.put(key, "X", 3);
        }
        for (final String key : new String[] { "ax", "bx", "cx", "dx", "ex", "fx" }) {
            builder.put(key, "Y", 3);
        }
        return builder.build();
    }

    /**
     * Verifies that a contracted accepting generalization survives a copyOf
     * round-trip. The original member paths are collapsed away in the compiled
     * form, so reduction cannot re-derive the accepting flag; copyOf must
     * preserve it explicitly.
     */
    @Test
    @DisplayName("should preserve contracted accepting generalization across copyOf")
    void shouldPreserveAcceptingGeneralizationOnCopyOf() {
        final FrequencyTrie<String> base = contractedSuffixTrie();
        assertAll(
                () -> assertEquals("X", base.get("zs"), "precondition: -s generalizes to unseen words"),
                () -> assertEquals("Y", base.get("zx"), "precondition: -x generalizes to unseen words"),
                () -> assertEquals(null, base.get("zz"), "precondition: root is not accepting (non-degenerate)"));

        final FrequencyTrie<String> reconstructed = FrequencyTrieBuilders
                .copyOf(base, ARRAY_FACTORY, CONTRACTING_SETTINGS).build();

        assertAll(
                () -> assertEquals("X", reconstructed.get("zs"), "generalization survives the round-trip"),
                () -> assertEquals("X", reconstructed.get("as"), "an original member still matches"),
                () -> assertEquals("Y", reconstructed.get("zx"), "the second family survives too"),
                () -> assertEquals(null, reconstructed.get("zz"), "root remains non-accepting"));
    }

    /**
     * Verifies that a specific pair added through a contracted accepting node
     * yields an accepting node with child edges: LookupMode.FIRST keeps the
     * generalization while LookupMode.LAST honors the deeper override.
     */
    @Test
    @DisplayName("should support a specific override added through a contracted accepting node")
    void shouldSupportSpecificOverrideThroughAcceptingNode() {
        final FrequencyTrie.Builder<String> reconstructed = FrequencyTrieBuilders.copyOf(contractedSuffixTrie(),
                ARRAY_FACTORY, CONTRACTING_SETTINGS);
        // "kubernetes" ends in 's', so its BACKWARD path runs through the contracted
        // "-s" accepting node, giving that node a child branch.
        reconstructed.put("kubernetes", "SPECIFIC", 1);
        final FrequencyTrie<String> modified = reconstructed.build();

        assertAll(
                () -> assertEquals("X", modified.get("kubernetes"),
                        "FIRST: the shallow generalization short-circuits"),
                () -> assertEquals("SPECIFIC", modified.withLookupMode(LookupMode.LAST).get("kubernetes"),
                        "LAST: the deeper specific override wins"),
                () -> assertEquals("X", modified.withLookupMode(LookupMode.LAST).get("dogs"),
                        "LAST: other -s words still generalize via fallback"),
                () -> assertArrayEquals(new String[] { "SPECIFIC", "X" },
                        modified.withLookupMode(LookupMode.ALL).getAll("kubernetes"),
                        "ALL: most specific first, generalization last"));
    }

    /**
     * Verifies that deleting a contracted generalization also clears its accepting
     * marker. An accepting node without a local value is invalid and must never be
     * emitted by the rebuilt trie.
     */
    @Test
    @DisplayName("should remove a contracted accepting generalization")
    void shouldRemoveContractedAcceptingGeneralization() {
        final FrequencyTrie<String> modified = FrequencyTrieBuilders
                .copyOf(contractedSuffixTrie(), ARRAY_FACTORY, CONTRACTING_SETTINGS)
                .remove("s")
                .build();

        assertAll(
                () -> assertNull(modified.get("zs"), "the removed -s generalization no longer resolves"),
                () -> assertEquals("Y", modified.get("zx"), "the unrelated -x generalization remains intact"));
    }

    /**
     * Verifies that reconstruction also works when only the reduction mode is
     * supplied and the helper internally derives default reduction settings.
     */
    @Test
    @DisplayName("should reconstruct builder when only reduction mode is supplied")
    void shouldReconstructUsingReductionModeShortcut() {
        final FrequencyTrie<String> original = createRepresentativeTrie();

        final FrequencyTrie.Builder<String> reconstructedBuilder = FrequencyTrieBuilders.copyOf(original, ARRAY_FACTORY,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        final FrequencyTrie<String> reconstructed = reconstructedBuilder.build();

        assertTrieStateEquals(original, reconstructed, "");
        assertTrieStateEquals(original, reconstructed, "ab");
        assertTrieStateEquals(original, reconstructed, "xy");
    }

    /**
     * Verifies that compiled trie values can be mapped to another value type while
     * preserving lookup semantics and local counts.
     */
    @Test
    @DisplayName("should map values while preserving keys and counts")
    void shouldMapValuesWhilePreservingKeysAndCounts() {
        final FrequencyTrie<String> original = createRepresentativeTrie();

        final FrequencyTrie<String> mapped = FrequencyTrieBuilders.mapValues(original, ARRAY_FACTORY,
                RANKED_SETTINGS, value -> "mapped-" + value);

        assertAll(
                () -> assertEquals("mapped-root-main", mapped.get("")),
                () -> assertArrayEquals(new String[] { "mapped-A1", "mapped-A2" }, mapped.getAll("a")),
                () -> assertIterableEquals(List.of(new ValueCount<String>("mapped-AB1", 5),
                        new ValueCount<String>("mapped-AB2", 2)), mapped.getEntries("ab")));
    }

    /**
     * Verifies that path statistics account for two paths reaching the same leaf
     * at different depths instead of assigning the leaf its first discovery depth.
     */
    @Test
    @DisplayName("should measure every logical path through a shared compiled subtree")
    void shouldMeasureLogicalPathsThroughSharedSubtree() {
        final CompiledNode<String>[] noChildren = nodes();
        final CompiledNode<String> sharedLeaf = new CompiledNode<>(new char[0], noChildren,
                new String[] { "patch" }, false, CompiledNode.DEFAULT_MAX_EXPANDED_INDEX, 1);
        final CompiledNode<String>[] intermediateChildren = nodes(sharedLeaf);
        final CompiledNode<String> intermediate = new CompiledNode<>(new char[] { 'x' }, intermediateChildren,
                new String[0], false, CompiledNode.DEFAULT_MAX_EXPANDED_INDEX);
        final CompiledNode<String>[] rootChildren = nodes(sharedLeaf, intermediate);
        final CompiledNode<String> root = new CompiledNode<>(new char[] { 'a', 'b' }, rootChildren,
                new String[0], false, CompiledNode.DEFAULT_MAX_EXPANDED_INDEX);
        final TrieMetadata metadata = TrieMetadata.current(FrequencyTrie.currentFormatVersion(),
                WordTraversalDirection.FORWARD, RANKED_SETTINGS);
        final FrequencyTrie<String> trie = FrequencyTrie.fromCompiled(String[]::new, root, metadata);

        final TrieStatistics statistics = FrequencyTrieBuilders.computeStatistics(trie);

        assertAll(
                () -> assertEquals(2L, statistics.internalNodeCount(), "Two unique nodes have child edges."),
                () -> assertEquals(1L, statistics.leafNodeCount(), "The shared leaf must be stored once."),
                () -> assertEquals(3L, statistics.edgeCount(), "Every stored edge must be counted."),
                () -> assertEquals(1L, statistics.valueReferenceCount(), "One physical value reference is stored."),
                () -> assertEquals(1L, statistics.distinctValueCount(), "One distinct patch value is stored."),
                () -> assertEquals(2L, statistics.logicalLeafPathCount(), "Both logical paths must be counted."),
                () -> assertEquals(2L, statistics.longestPath(), "The deeper logical path has two edges."),
                () -> assertEquals(1.5d, statistics.averageLeafDepth(), "Path depths one and two average to 1.5."));
    }

    /**
     * Verifies that reconstruction treats a shared compiled node's local counts as
     * an already-aggregated contribution instead of replaying them once for every
     * incoming logical path.
     *
     * <p>
     * The second half also verifies copy-on-write behavior: modifying one expanded
     * path must separate that path from its unchanged peer even though ranked
     * reduction would otherwise consider both local value lists equivalent. A
     * rejected overflowing update must not create that boundary or otherwise alter
     * the builder.
     * </p>
     */
    @Test
    @DisplayName("should preserve aggregated counts of shared compiled nodes")
    void shouldPreserveAggregatedCountsOfSharedCompiledNodes() {
        final CompiledNode<String> sharedLeaf = new CompiledNode<>(new char[0], nodes(),
                new String[] { "patch" }, false, CompiledNode.DEFAULT_MAX_EXPANDED_INDEX, 5);
        final CompiledNode<String> root = new CompiledNode<>(new char[] { 'a', 'b' },
                nodes(sharedLeaf, sharedLeaf), new String[0], false, CompiledNode.DEFAULT_MAX_EXPANDED_INDEX);
        final TrieMetadata metadata = TrieMetadata.current(FrequencyTrie.currentFormatVersion(),
                WordTraversalDirection.FORWARD, RANKED_SETTINGS);
        final FrequencyTrie<String> source = FrequencyTrie.fromCompiled(String[]::new, root, metadata);

        final FrequencyTrie<String> reconstructed = FrequencyTrieBuilders
                .copyOf(source, ARRAY_FACTORY, RANKED_SETTINGS)
                .build();
        final FrequencyTrie<String> modified = FrequencyTrieBuilders
                .copyOf(source, ARRAY_FACTORY, RANKED_SETTINGS)
                .put("a", "patch")
                .build();
        final FrequencyTrie.Builder<String> failedUpdate = FrequencyTrieBuilders
                .copyOf(source, ARRAY_FACTORY, RANKED_SETTINGS);
        assertThrows(ArithmeticException.class, () -> failedUpdate.put("a", "patch", Integer.MAX_VALUE));
        final FrequencyTrie<String> afterFailedUpdate = failedUpdate.build();

        assertAll(
                () -> assertIterableEquals(List.of(new ValueCount<String>("patch", 5)),
                        reconstructed.getEntries("a"), "The first path must retain the compiled aggregate."),
                () -> assertIterableEquals(List.of(new ValueCount<String>("patch", 5)),
                        reconstructed.getEntries("b"), "A shared peer must not multiply the aggregate."),
                () -> assertIterableEquals(List.of(new ValueCount<String>("patch", 6)), modified.getEntries("a"),
                        "The changed path must receive its local increment."),
                () -> assertIterableEquals(List.of(new ValueCount<String>("patch", 5)), modified.getEntries("b"),
                        "The unchanged shared path must retain the source aggregate."),
                () -> assertEquals(1L, FrequencyTrieBuilders.computeStatistics(afterFailedUpdate).leafNodeCount(),
                        "A rejected update must not install a copy-on-write boundary."));
    }

    /**
     * Creates a generic compiled-node array for manually assembled DAG fixtures.
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
     * Verifies the documented null-argument contract for both public reconstruction
     * entry points.
     */
    @Test
    @DisplayName("should reject null arguments")
    void shouldRejectNullArguments() {
        final FrequencyTrie<String> trie = createRepresentativeTrie();

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrieBuilders.copyOf(null, ARRAY_FACTORY, RANKED_SETTINGS)),
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrieBuilders.copyOf(trie, null, RANKED_SETTINGS)),
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrieBuilders.copyOf(trie, ARRAY_FACTORY, (ReductionSettings) null)),
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrieBuilders.copyOf(null, ARRAY_FACTORY,
                                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS)),
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrieBuilders.copyOf(trie, null,
                                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS)),
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrieBuilders.copyOf(trie, ARRAY_FACTORY, (ReductionMode) null)),
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrieBuilders.mapValues(null, Integer[]::new, RANKED_SETTINGS, String::length)),
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrieBuilders.mapValues(trie, null, RANKED_SETTINGS, String::length)),
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrieBuilders.mapValues(trie, Integer[]::new, (ReductionSettings) null,
                                String::length)),
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrieBuilders.mapValues(trie, Integer[]::new, RANKED_SETTINGS, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> FrequencyTrieBuilders.mapValues(trie, Integer[]::new, (ReductionMode) null,
                                String::length)));
    }

    /**
     * Creates a representative compiled trie used across multiple tests.
     *
     * @return compiled trie with several branches and ranked values
     */
    private static FrequencyTrie<String> createRepresentativeTrie() {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(ARRAY_FACTORY, RANKED_SETTINGS);

        builder.put("", "root-main", 3);
        builder.put("", "root-alt", 1);

        builder.put("a", "A1", 2);
        builder.put("a", "A2", 1);

        builder.put("ab", "AB1", 5);
        builder.put("ab", "AB2", 2);

        builder.put("abc", "ABC", 4);
        builder.put("abd", "ABD", 3);

        builder.put("x", "X", 1);
        builder.put("xy", "XY1", 2);
        builder.put("xy", "XY2", 2);

        final FrequencyTrie<String> trie = builder.build();
        assertNotNull(trie);
        return trie;
    }

    /**
     * Asserts equality of the observable trie state for one key.
     *
     * @param expected expected trie
     * @param actual   actual trie
     * @param key      key to verify
     */
    private static void assertTrieStateEquals(final FrequencyTrie<String> expected, final FrequencyTrie<String> actual,
            final String key) {
        assertAll(
                () -> assertEquals(expected.get(key), actual.get(key),
                        "Unexpected get() result for key '" + key + "'."),
                () -> assertArrayEquals(expected.getAll(key), actual.getAll(key),
                        "Unexpected getAll() result for key '" + key + "'."),
                () -> assertIterableEquals(expected.getEntries(key), actual.getEntries(key),
                        "Unexpected getEntries() result for key '" + key + "'."));
    }
}
