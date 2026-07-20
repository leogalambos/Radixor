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
