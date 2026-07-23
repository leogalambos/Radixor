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
package org.egothor.stemmer.benchmark.quality;

import java.util.OptionalDouble;

/**
 * Derives scientifically labelled pairwise confusion metrics from unrounded raw counts.
 * Undefined ratios are represented by empty optionals; no method returns NaN or infinity.
 */
record PairwiseMetrics(long truePositivePairs, long falsePositivePairs, long falseNegativePairs,
        long trueNegativePairs, boolean coherentConfusionMatrix) {
    /** Creates metrics for one coherent binary relation. */
    PairwiseMetrics(final long truePositivePairs, final long falsePositivePairs,
            final long falseNegativePairs, final long trueNegativePairs) {
        this(truePositivePairs, falsePositivePairs, falseNegativePairs, trueNegativePairs, true);
    }

    /** Creates checked confusion counts from one quality result. */
    static PairwiseMetrics from(final QualityResult result) {
        return new PairwiseMetrics(Math.subtractExact(result.underPossiblePairs(), result.underErrorPairs()),
                result.overErrorPairs(), result.underErrorPairs(),
                Math.subtractExact(result.overPossiblePairs(), result.overErrorPairs()),
                result.outputPolicy() != OutputPolicy.ANY_CANDIDATE);
    }

    /** @return pairwise precision */ OptionalDouble precision() { return coherentRatio(truePositivePairs, Math.addExact(truePositivePairs, falsePositivePairs)); }
    /** @return pairwise recall */ OptionalDouble recall() { return coherentRatio(truePositivePairs, Math.addExact(truePositivePairs, falseNegativePairs)); }
    /** @return pairwise specificity */ OptionalDouble specificity() { return coherentRatio(trueNegativePairs, Math.addExact(trueNegativePairs, falsePositivePairs)); }
    /** @return pairwise accuracy, potentially dominated by true negatives */
    OptionalDouble accuracy() { return coherentRatio(Math.addExact(truePositivePairs, trueNegativePairs), total()); }
    /** @return arithmetic mean of recall and specificity */
    OptionalDouble balancedAccuracy() { return mean(recall(), specificity()); }
    /** @return pairwise F0.5 */ OptionalDouble f05() { return fBeta(0.25); }
    /** @return pairwise F1 */ OptionalDouble f1() { return fBeta(1.0); }
    /** @return pairwise F2 */ OptionalDouble f2() { return fBeta(4.0); }
    /** @return Jaccard index */
    OptionalDouble jaccard() { return coherentRatio(truePositivePairs, Math.addExact(Math.addExact(truePositivePairs, falsePositivePairs), falseNegativePairs)); }
    /** @return Fowlkes-Mallows index */
    OptionalDouble fowlkesMallows() {
        final OptionalDouble precisionValue = precision(); final OptionalDouble recallValue = recall();
        return precisionValue.isEmpty() || recallValue.isEmpty() ? OptionalDouble.empty()
                : OptionalDouble.of(Math.sqrt(precisionValue.getAsDouble() * recallValue.getAsDouble()));
    }
    /** @return Matthews correlation coefficient using scaled double arithmetic */
    OptionalDouble matthewsCorrelationCoefficient() {
        if (!coherentConfusionMatrix) { return OptionalDouble.empty(); }
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
    OptionalDouble errorRate() { return coherentRatio(Math.addExact(falsePositivePairs, falseNegativePairs), total()); }

    /** Calculates F-beta directly from raw counts. */
    private OptionalDouble fBeta(final double betaSquared) {
        if (!coherentConfusionMatrix) { return OptionalDouble.empty(); }
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
    /** Calculates a ratio only when the counts describe one coherent relation. */
    private OptionalDouble coherentRatio(final long numerator, final long denominator) {
        return coherentConfusionMatrix ? ratio(numerator, denominator) : OptionalDouble.empty();
    }
    /** Averages two defined ratios. */
    private static OptionalDouble mean(final OptionalDouble left, final OptionalDouble right) {
        return left.isEmpty() || right.isEmpty() ? OptionalDouble.empty()
                : OptionalDouble.of((left.getAsDouble() + right.getAsDouble()) / 2.0);
    }
}
