package org.egothor.stemmer.benchmark.quality;

import java.util.OptionalDouble;

/**
 * Derives scientifically labelled pairwise confusion metrics from unrounded raw counts.
 * Undefined ratios are represented by empty optionals; no method returns NaN or infinity.
 */
record PairwiseMetrics(long truePositivePairs, long falsePositivePairs, long falseNegativePairs,
        long trueNegativePairs) {
    /** Creates checked confusion counts from one quality result. */
    static PairwiseMetrics from(final QualityResult result) {
        return new PairwiseMetrics(Math.subtractExact(result.underPossiblePairs(), result.underErrorPairs()),
                result.overErrorPairs(), result.underErrorPairs(),
                Math.subtractExact(result.overPossiblePairs(), result.overErrorPairs()));
    }

    /** @return pairwise precision */ OptionalDouble precision() { return ratio(truePositivePairs, Math.addExact(truePositivePairs, falsePositivePairs)); }
    /** @return pairwise recall */ OptionalDouble recall() { return ratio(truePositivePairs, Math.addExact(truePositivePairs, falseNegativePairs)); }
    /** @return pairwise specificity */ OptionalDouble specificity() { return ratio(trueNegativePairs, Math.addExact(trueNegativePairs, falsePositivePairs)); }
    /** @return pairwise accuracy, potentially dominated by true negatives */
    OptionalDouble accuracy() { return ratio(Math.addExact(truePositivePairs, trueNegativePairs), total()); }
    /** @return arithmetic mean of recall and specificity */
    OptionalDouble balancedAccuracy() { return mean(recall(), specificity()); }
    /** @return pairwise F0.5 */ OptionalDouble f05() { return fBeta(0.25); }
    /** @return pairwise F1 */ OptionalDouble f1() { return fBeta(1.0); }
    /** @return pairwise F2 */ OptionalDouble f2() { return fBeta(4.0); }
    /** @return Jaccard index */
    OptionalDouble jaccard() { return ratio(truePositivePairs, Math.addExact(Math.addExact(truePositivePairs, falsePositivePairs), falseNegativePairs)); }
    /** @return Fowlkes-Mallows index */
    OptionalDouble fowlkesMallows() {
        final OptionalDouble precisionValue = precision(); final OptionalDouble recallValue = recall();
        return precisionValue.isEmpty() || recallValue.isEmpty() ? OptionalDouble.empty()
                : OptionalDouble.of(Math.sqrt(precisionValue.getAsDouble() * recallValue.getAsDouble()));
    }
    /** @return Matthews correlation coefficient using scaled double arithmetic */
    OptionalDouble matthewsCorrelationCoefficient() {
        final double a = (double) truePositivePairs + falsePositivePairs;
        final double b = (double) truePositivePairs + falseNegativePairs;
        final double c = (double) trueNegativePairs + falsePositivePairs;
        final double d = (double) trueNegativePairs + falseNegativePairs;
        final double denominator = Math.sqrt(a * b * c * d);
        if (denominator == 0.0) { return OptionalDouble.empty(); }
        final double numerator = (double) truePositivePairs * trueNegativePairs
                - (double) falsePositivePairs * falseNegativePairs;
        return OptionalDouble.of(numerator / denominator);
    }
    /** @return pairwise error rate */
    OptionalDouble errorRate() { return ratio(Math.addExact(falsePositivePairs, falseNegativePairs), total()); }

    /** Calculates F-beta directly from raw counts. */
    private OptionalDouble fBeta(final double betaSquared) {
        final double numerator = (1.0 + betaSquared) * truePositivePairs;
        final double denominator = numerator + betaSquared * falseNegativePairs + falsePositivePairs;
        return denominator == 0.0 ? OptionalDouble.empty() : OptionalDouble.of(numerator / denominator);
    }
    /** Returns the checked total pair population. */
    private long total() { return Math.addExact(Math.addExact(truePositivePairs, falsePositivePairs), Math.addExact(falseNegativePairs, trueNegativePairs)); }
    /** Calculates one ratio with explicit zero-denominator handling. */
    private static OptionalDouble ratio(final long numerator, final long denominator) {
        return denominator == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) numerator / denominator);
    }
    /** Averages two defined ratios. */
    private static OptionalDouble mean(final OptionalDouble left, final OptionalDouble right) {
        return left.isEmpty() || right.isEmpty() ? OptionalDouble.empty()
                : OptionalDouble.of((left.getAsDouble() + right.getAsDouble()) / 2.0);
    }
}
