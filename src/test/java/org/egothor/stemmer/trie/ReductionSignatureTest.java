package org.egothor.stemmer.trie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReductionSignature}.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("ReductionSignature")
class ReductionSignatureTest {

    @Test
    @DisplayName("create must preserve ranked getAll semantics in ranked mode")
    void shouldPreserveRankedGetAllSemanticsInRankedMode() {
        final ReductionSettings settings = ReductionSettings
                .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        final ReductionSignature<String> left = ReductionSignature.create(createTwoValueSummary("a", 5, "b", 2),
                Map.of(), settings);

        final ReductionSignature<String> sameRankingDifferentCounts = ReductionSignature
                .create(createTwoValueSummary("a", 9, "b", 1), Map.of(), settings);

        final ReductionSignature<String> differentOrder = ReductionSignature.create(
                new LocalValueSummary<>(new String[] { "b", "a" }, new int[] { 5, 2 }, 7, "b", 5, 2), Map.of(),
                settings);

        assertEquals(left, sameRankingDifferentCounts);
        assertNotEquals(left, differentOrder);
    }

    @Test
    @DisplayName("create must ignore local ordering in unordered mode")
    void shouldIgnoreLocalOrderingInUnorderedMode() {
        final ReductionSettings settings = ReductionSettings
                .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS);

        final ReductionSignature<String> left = ReductionSignature.create(
                new LocalValueSummary<>(new String[] { "a", "b" }, new int[] { 5, 2 }, 7, "a", 5, 2), Map.of(),
                settings);

        final ReductionSignature<String> right = ReductionSignature.create(
                new LocalValueSummary<>(new String[] { "b", "a" }, new int[] { 5, 2 }, 7, "b", 5, 2), Map.of(),
                settings);

        assertEquals(left, right);
    }

    @Test
    @DisplayName("create must use dominant descriptor in dominant mode when dominant winner qualifies")
    void shouldUseDominantDescriptorWhenDominantWinnerQualifies() {
        final ReductionSettings settings = new ReductionSettings(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS, 70, 3);

        final ReductionSignature<String> left = ReductionSignature.create(
                new LocalValueSummary<>(new String[] { "a", "b" }, new int[] { 8, 2 }, 10, "a", 8, 2), Map.of(),
                settings);

        final ReductionSignature<String> right = ReductionSignature.create(
                new LocalValueSummary<>(new String[] { "a", "x", "y" }, new int[] { 8, 1, 1 }, 10, "a", 8, 1), Map.of(),
                settings);

        assertEquals(left, right);
    }

    @Test
    @DisplayName("create must fall back to ranked descriptor in dominant mode when dominant winner does not qualify")
    void shouldFallBackToRankedDescriptorWhenDominantWinnerDoesNotQualify() {
        final ReductionSettings settings = new ReductionSettings(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS, 80, 2);

        final ReductionSignature<String> left = ReductionSignature.create(
                new LocalValueSummary<>(new String[] { "a", "b" }, new int[] { 6, 4 }, 10, "a", 6, 4), Map.of(),
                settings);

        final ReductionSignature<String> right = ReductionSignature.create(
                new LocalValueSummary<>(new String[] { "a", "c" }, new int[] { 6, 4 }, 10, "a", 6, 4), Map.of(),
                settings);

        assertNotEquals(left, right);
    }

    @Test
    @DisplayName("create must sort child descriptors by edge regardless of map insertion order")
    void shouldSortChildDescriptorsByEdgeRegardlessOfMapInsertionOrder() {
        final ReductionSettings settings = ReductionSettings
                .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        final ReducedNode<String> childA = createReducedLeaf("child-a");
        final ReducedNode<String> childB = createReducedLeaf("child-b");

        final Map<Character, ReducedNode<String>> leftChildren = new LinkedHashMap<>();
        leftChildren.put('b', childB);
        leftChildren.put('a', childA);

        final Map<Character, ReducedNode<String>> rightChildren = new LinkedHashMap<>();
        rightChildren.put('a', childA);
        rightChildren.put('b', childB);

        final LocalValueSummary<String> summary = new LocalValueSummary<>(new String[] { "root" }, new int[] { 1 }, 1,
                "root", 1, 0);

        final ReductionSignature<String> left = ReductionSignature.create(summary, leftChildren, settings);
        final ReductionSignature<String> right = ReductionSignature.create(summary, rightChildren, settings);

        assertEquals(left, right);
    }

    @Test
    @DisplayName("create must include child signatures in equality")
    void shouldIncludeChildSignaturesInEquality() {
        final ReductionSettings settings = ReductionSettings
                .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        final LocalValueSummary<String> summary = new LocalValueSummary<>(new String[] { "root" }, new int[] { 1 }, 1,
                "root", 1, 0);

        final ReductionSignature<String> left = ReductionSignature.create(summary, Map.of('a', createReducedLeaf("x")),
                settings);
        final ReductionSignature<String> right = ReductionSignature.create(summary, Map.of('a', createReducedLeaf("y")),
                settings);

        assertNotEquals(left, right);
    }

    private static LocalValueSummary<String> createTwoValueSummary(final String dominantValue, final int dominantCount,
            final String secondValue, final int secondCount) {
        return new LocalValueSummary<>(new String[] { dominantValue, secondValue },
                new int[] { dominantCount, secondCount }, dominantCount + secondCount, dominantValue, dominantCount,
                secondCount);
    }

    private static ReductionSignature<String> createLeafSignature(final String value) {
        final LocalValueSummary<String> summary = new LocalValueSummary<>(new String[] { value }, new int[] { 1 }, 1,
                value, 1, 0);

        return ReductionSignature.create(summary, Map.of(),
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
    }

    private static ReducedNode<String> createReducedLeaf(final String value) {
        return new ReducedNode<>(createLeafSignature(value), Map.of(value, 1), Map.of());
    }
}