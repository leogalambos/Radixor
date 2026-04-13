package org.egothor.stemmer.trie;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LocalValueSummary}.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("LocalValueSummary")
class LocalValueSummaryTest {

    @Test
    @DisplayName("of must create empty summary for empty counts")
    void shouldCreateEmptySummaryForEmptyCounts() {
        final LocalValueSummary<String> summary = LocalValueSummary.of(Map.of(), String[]::new);

        assertArrayEquals(new String[0], summary.orderedValues());
        assertArrayEquals(new int[0], summary.orderedCounts());
        assertNull(summary.dominantValue);
    }

    @Test
    @DisplayName("of must order by descending frequency then shorter textual form then lexicographically")
    void shouldOrderByFrequencyLengthAndLexicographicalValue() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("bbb", 4);
        counts.put("a", 4);
        counts.put("aa", 4);
        counts.put("ab", 4);
        counts.put("z", 2);

        final LocalValueSummary<String> summary = LocalValueSummary.of(counts, String[]::new);

        assertArrayEquals(new String[] { "a", "aa", "ab", "bbb", "z" }, summary.orderedValues());
        assertArrayEquals(new int[] { 4, 4, 4, 4, 2 }, summary.orderedCounts());
        assertEquals("a", summary.dominantValue);
    }

    @Test
    @DisplayName("of must use insertion order as the final tie breaker")
    void shouldUseInsertionOrderAsFinalTieBreaker() {
        final Map<Object, Integer> counts = new LinkedHashMap<>();
        final TextTwin first = new TextTwin("xy");
        final TextTwin second = new TextTwin("xy");

        counts.put(first, 5);
        counts.put(second, 5);

        final LocalValueSummary<Object> summary = LocalValueSummary.of(counts, Object[]::new);

        assertSame(first, summary.orderedValues()[0]);
        assertSame(second, summary.orderedValues()[1]);
        assertArrayEquals(new int[] { 5, 5 }, summary.orderedCounts());
    }

    @Test
    @DisplayName("orderedValues must expose the documented backing array")
    void shouldExposeDocumentedOrderedValuesBackingArray() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("alpha", 2);
        counts.put("beta", 1);

        final LocalValueSummary<String> summary = LocalValueSummary.of(counts, String[]::new);

        final String[] orderedValues = summary.orderedValues();
        orderedValues[0] = "mutated";

        assertEquals("mutated", summary.orderedValues()[0]);
    }

    @Test
    @DisplayName("orderedCounts must expose the documented backing array")
    void shouldExposeDocumentedOrderedCountsBackingArray() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("alpha", 2);
        counts.put("beta", 1);

        final LocalValueSummary<String> summary = LocalValueSummary.of(counts, String[]::new);

        final int[] orderedCounts = summary.orderedCounts();
        orderedCounts[0] = 99;

        assertEquals(99, summary.orderedCounts()[0]);
    }

    @Test
    @DisplayName("hasQualifiedDominantWinner must return true when percentage and ratio thresholds are satisfied")
    void shouldAcceptQualifiedDominantWinner() {
        final LocalValueSummary<String> summary = new LocalValueSummary<>(new String[] { "a", "b" }, new int[] { 8, 2 },
                10, "a", 8, 2);

        final ReductionSettings settings = new ReductionSettings(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS, 70, 3);

        assertTrue(summary.hasQualifiedDominantWinner(settings));
    }

    @Test
    @DisplayName("hasQualifiedDominantWinner must reject winner when percentage threshold is not satisfied")
    void shouldRejectWinnerWhenPercentageThresholdIsNotSatisfied() {
        final LocalValueSummary<String> summary = new LocalValueSummary<>(new String[] { "a", "b" }, new int[] { 6, 4 },
                10, "a", 6, 4);

        final ReductionSettings settings = new ReductionSettings(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS, 70, 1);

        assertFalse(summary.hasQualifiedDominantWinner(settings));
    }

    @Test
    @DisplayName("hasQualifiedDominantWinner must reject winner when over-second ratio is not satisfied")
    void shouldRejectWinnerWhenOverSecondRatioIsNotSatisfied() {
        final LocalValueSummary<String> summary = new LocalValueSummary<>(new String[] { "a", "b" }, new int[] { 6, 4 },
                10, "a", 6, 4);

        final ReductionSettings settings = new ReductionSettings(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS, 50, 2);

        assertFalse(summary.hasQualifiedDominantWinner(settings));
    }

    @Test
    @DisplayName("hasQualifiedDominantWinner must accept single winner when second count is absent")
    void shouldAcceptSingleWinnerWhenSecondCountIsAbsent() {
        final LocalValueSummary<String> summary = new LocalValueSummary<>(new String[] { "a" }, new int[] { 3 }, 3, "a",
                3, 0);

        final ReductionSettings settings = new ReductionSettings(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS, 100, 10);

        assertTrue(summary.hasQualifiedDominantWinner(settings));
    }

    @Test
    @DisplayName("hasQualifiedDominantWinner must return false when no dominant value exists")
    void shouldReturnFalseWhenNoDominantValueExists() {
        final LocalValueSummary<String> summary = new LocalValueSummary<>(new String[0], new int[0], 0, null, 0, 0);

        final ReductionSettings settings = ReductionSettings
                .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS);

        assertFalse(summary.hasQualifiedDominantWinner(settings));
    }

    /**
     * Test helper with identical textual form but distinct identity.
     */
    private static final class TextTwin {

        /**
         * Textual form.
         */
        private final String text;

        /**
         * Creates a helper value.
         *
         * @param text textual form
         */
        private TextTwin(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return this.text;
        }
    }
}