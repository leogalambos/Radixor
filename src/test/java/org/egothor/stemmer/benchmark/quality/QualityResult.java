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

import java.util.Comparator;
import java.util.Objects;
import java.util.OptionalDouble;

/** Immutable pairwise stemming-quality result; all pair quantities are counts. */
public record QualityResult(String stemmer, String language, ProcessingMode processingMode,
        OutputPolicy outputPolicy,
        long appliedDictionaryRows, long processedWordForms, long singletonDictionaryRows,
        long dictionaryRowsContributingUnderPairs, long formsWithOneCandidate, long formsWithMultipleCandidates,
        long maximumCandidatesForOneWord, long totalCandidateAssignments, long distinctOutputStems,
        long overErrorPairs, long overPossiblePairs, long underErrorPairs, long underPossiblePairs,
        PartitionMetrics partitionMetrics) {
    /** Stable report ordering by stemmer, language, and processing mode. */
    public static final Comparator<QualityResult> ORDER = Comparator.comparing(QualityResult::stemmer)
            .thenComparing(QualityResult::language).thenComparing(QualityResult::processingMode)
            .thenComparing(QualityResult::outputPolicy);

    /** Validates non-null labels, non-negative counts, and bounded errors. */
    public QualityResult {
        Objects.requireNonNull(stemmer, "stemmer");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(processingMode, "processingMode");
        Objects.requireNonNull(outputPolicy, "outputPolicy");
        final long[] counts = {appliedDictionaryRows, processedWordForms, singletonDictionaryRows,
            dictionaryRowsContributingUnderPairs, formsWithOneCandidate, formsWithMultipleCandidates,
            maximumCandidatesForOneWord, totalCandidateAssignments, distinctOutputStems,
            overErrorPairs, overPossiblePairs, underErrorPairs, underPossiblePairs};
        for (long count : counts) {
            if (count < 0) {
                throw new IllegalArgumentException("Quality-result counts must not be negative.");
            }
        }
        if (overErrorPairs > overPossiblePairs || underErrorPairs > underPossiblePairs) {
            throw new IllegalArgumentException("Error-pair counts must not exceed possible-pair counts.");
        }
        if (outputPolicy != OutputPolicy.PRIMARY_OUTPUT && partitionMetrics != null) {
            throw new IllegalArgumentException("Partition metrics apply only to PRIMARY_OUTPUT.");
        }
    }

    /** @return over-stemming percentage, or empty when its denominator is zero */
    public OptionalDouble overPercentage() { return percentage(overErrorPairs, overPossiblePairs); }
    /** @return under-stemming percentage, or empty when its denominator is zero */
    public OptionalDouble underPercentage() { return percentage(underErrorPairs, underPossiblePairs); }
    /** @return aggregate pairwise metrics derived from raw confusion counts */
    public PairwiseMetrics pairwiseMetrics() { return PairwiseMetrics.from(this); }
    /** Calculates a percentage without manufacturing a value for a zero denominator. */
    private static OptionalDouble percentage(final long errors, final long possible) {
        return possible == 0 ? OptionalDouble.empty() : OptionalDouble.of(100.0 * errors / possible);
    }
}
