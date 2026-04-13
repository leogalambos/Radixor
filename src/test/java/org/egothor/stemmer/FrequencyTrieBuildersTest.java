package org.egothor.stemmer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.IntFunction;

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
@Tag("builder")
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
                        () -> FrequencyTrieBuilders.copyOf(trie, ARRAY_FACTORY, (ReductionMode) null)));
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