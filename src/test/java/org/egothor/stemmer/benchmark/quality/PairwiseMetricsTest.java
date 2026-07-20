package org.egothor.stemmer.benchmark.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Formula and degenerate-case tests for aggregate pairwise metrics. */
@Tag("unit")
@DisplayName("Pairwise aggregate metrics")
final class PairwiseMetricsTest {
    /** Verifies all formulas use the supplied raw confusion counts. */
    @Test @DisplayName("Aggregate metrics are calculated from raw confusion counts")
    void formulas() {
        final PairwiseMetrics metrics = new PairwiseMetrics(8, 2, 4, 16);
        assertEquals(0.8, metrics.precision().orElseThrow(), 1.0e-12);
        assertEquals(8.0 / 12.0, metrics.recall().orElseThrow(), 1.0e-12);
        assertEquals(16.0 / 18.0, metrics.specificity().orElseThrow(), 1.0e-12);
        assertEquals(24.0 / 30.0, metrics.accuracy().orElseThrow(), 1.0e-12);
        assertEquals(8.0 / 14.0, metrics.jaccard().orElseThrow(), 1.0e-12);
        assertEquals(6.0 / 30.0, metrics.errorRate().orElseThrow(), 1.0e-12);
        assertTrue(metrics.f05().orElseThrow() > metrics.f2().orElseThrow());
    }

    /** Verifies a perfect nondegenerate relation reaches every applicable maximum. */
    @Test @DisplayName("Perfect confusion counts produce maximum defined scores")
    void perfect() {
        final PairwiseMetrics metrics = new PairwiseMetrics(10, 0, 0, 20);
        assertEquals(1.0, metrics.f05().orElseThrow()); assertEquals(1.0, metrics.f1().orElseThrow());
        assertEquals(1.0, metrics.f2().orElseThrow()); assertEquals(1.0, metrics.matthewsCorrelationCoefficient().orElseThrow());
        assertEquals(1.0, metrics.balancedAccuracy().orElseThrow());
    }

    /** Verifies undefined denominators remain explicit missing values. */
    @Test @DisplayName("Degenerate zero denominators remain undefined")
    void undefined() {
        final PairwiseMetrics metrics = new PairwiseMetrics(0, 0, 0, 0);
        assertTrue(metrics.precision().isEmpty()); assertTrue(metrics.recall().isEmpty());
        assertTrue(metrics.matthewsCorrelationCoefficient().isEmpty());
    }
}
