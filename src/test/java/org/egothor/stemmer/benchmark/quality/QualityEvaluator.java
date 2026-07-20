package org.egothor.stemmer.benchmark.quality;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;

import org.egothor.stemmer.benchmark.QualityStemmerMatrix.BatchStemmer;

/** Evaluates pairwise partition agreement using aggregated frequencies, never explicit pairs. */
public final class QualityEvaluator {
    /** Utility class. */
    private QualityEvaluator() { throw new AssertionError("No instances."); }

    /**
     * Evaluates one scenario in time proportional to forms and group-to-stem associations.
     * All combinatorial arithmetic is checked and overflow is reported.
     *
     * @param stemmerName stable stemmer name
     * @param language stable language identifier
     * @param mode processing mode
     * @param groups parsed gold-standard groups
     * @param stemmer stemmer implementation
     * @return immutable metric result
     */
    public static QualityResult evaluate(final String stemmerName, final String language, final ProcessingMode mode,
            final Iterable<GoldStandardGroup> groups, final StemmerFunction stemmer) {
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(stemmer, "stemmer");
        final Map<String, Long> global = new HashMap<>();
        long rows = 0;
        long words = 0;
        long singletonRows = 0;
        long pairRows = 0;
        long underPossible = 0;
        long withinSameStem = 0;
        final Set<String> stems = new HashSet<>();
        final Map<String, Long> local = new HashMap<>();
        final List<Map<String, Long>> contingency = new ArrayList<>();
        final List<Long> groupSizes = new ArrayList<>();
        for (GoldStandardGroup group : groups) {
            final List<String> forms = group.forms();
            if (!mode.includes(forms)) {
                continue;
            }
            rows = add(rows, 1, "applied dictionary rows");
            words = add(words, forms.size(), "processed word forms");
            if (forms.size() == 1) {
                singletonRows = add(singletonRows, 1, "singleton dictionary rows");
            } else {
                pairRows = add(pairRows, 1, "dictionary rows contributing under-stemming pairs");
            }
            underPossible = add(underPossible, chooseTwo(forms.size()), "under-stemming possible pairs");
            local.clear();
            for (String form : forms) {
                final String output;
                try {
                    output = stemmer.stem(form);
                } catch (IOException exception) {
                    throw failure(stemmerName, language, mode, group.rowNumber(), form,
                            "the stemmer threw an exception", exception);
                }
                if (output == null) {
                    throw failure(stemmerName, language, mode, group.rowNumber(), form,
                            "the stemmer returned null", null);
                }
                local.merge(output, 1L, (left, right) -> add(left, right, "group-to-stem frequency"));
                global.merge(output, 1L, (left, right) -> add(left, right, "global stem frequency"));
                stems.add(output);
            }
            for (long frequency : local.values()) {
                withinSameStem = add(withinSameStem, chooseTwo(frequency), "within-group merged pairs");
            }
            contingency.add(Map.copyOf(local));
            groupSizes.add((long) forms.size());
        }
        long allPairs = chooseTwo(words);
        long overPossible = subtract(allPairs, underPossible, "over-stemming possible pairs");
        long allSameStem = 0;
        for (long frequency : global.values()) {
            allSameStem = add(allSameStem, chooseTwo(frequency), "same-stem pairs");
        }
        final long underError = subtract(underPossible, withinSameStem, "under-stemming error pairs");
        final long overError = subtract(allSameStem, withinSameStem, "over-stemming error pairs");
        final PartitionMetrics partition = partitionMetrics(words, underPossible, allSameStem,
                withinSameStem, groupSizes, global, contingency);
        return new QualityResult(stemmerName, language, mode, OutputPolicy.PRIMARY_OUTPUT,
                rows, words, singletonRows, pairRows, words, 0, words == 0 ? 0 : 1, words, stems.size(), overError, overPossible,
                underError, underPossible, partition);
    }

    /** Calculates strict-partition metrics from the exact contingency table. */
    private static PartitionMetrics partitionMetrics(final long words, final long rowPairs, final long columnPairs,
            final long indexPairs, final List<Long> groupSizes, final Map<String, Long> global,
            final List<Map<String, Long>> contingency) {
        if (words == 0) { return new PartitionMetrics(0.0, 0.0, 0.0, 0.0, 0.0); }
        final double totalPairs = chooseTwo(words);
        final double expected = totalPairs == 0.0 ? 0.0 : (double) rowPairs * columnPairs / totalPairs;
        final double maximum = (rowPairs + (double) columnPairs) / 2.0;
        final double adjustedRand = maximum == expected ? 1.0 : (indexPairs - expected) / (maximum - expected);
        final double goldEntropy = entropy(words, groupSizes);
        final double predictedEntropy = entropy(words, global.values());
        double mutualInformation = 0.0;
        for (int group = 0; group < contingency.size(); group++) {
            final long groupSize = groupSizes.get(group);
            for (Map.Entry<String, Long> cell : contingency.get(group).entrySet()) {
                final double frequency = cell.getValue();
                mutualInformation += frequency / words * Math.log(frequency * words
                        / (groupSize * (double) global.get(cell.getKey())));
            }
        }
        final double homogeneity = goldEntropy == 0.0 ? 1.0 : mutualInformation / goldEntropy;
        final double completeness = predictedEntropy == 0.0 ? 1.0 : mutualInformation / predictedEntropy;
        final double vMeasure = homogeneity + completeness == 0.0 ? 0.0
                : 2.0 * homogeneity * completeness / (homogeneity + completeness);
        final double nmiDenominator = (goldEntropy + predictedEntropy) / 2.0;
        final double nmi = nmiDenominator == 0.0 ? 1.0 : mutualInformation / nmiDenominator;
        return new PartitionMetrics(adjustedRand, homogeneity, completeness, vMeasure, nmi);
    }

    /** Calculates natural-log entropy from category frequencies. */
    private static double entropy(final long total, final Iterable<Long> frequencies) {
        double weightedLogs = 0.0;
        for (long frequency : frequencies) { weightedLogs += frequency * Math.log(frequency); }
        return Math.log(total) - weightedLogs / total;
    }

    /**
     * Evaluates one scenario through an authoritative JMH batch adapter.
     * The temporary input and output arrays are required to preserve TokenStream
     * lifecycle and preprocessing semantics used by the JMH comparison.
     *
     * @param stemmerName stable JMH candidate name
     * @param language registered dictionary language
     * @param mode processing mode
     * @param groups parsed gold-standard groups
     * @param stemmer scenario-confined batch adapter
     * @return immutable pairwise result
     * @throws IOException when the benchmark adapter fails
     */
    public static QualityResult evaluateBatch(final String stemmerName, final String language,
            final ProcessingMode mode, final List<GoldStandardGroup> groups, final BatchStemmer stemmer)
            throws IOException {
        final List<String> included = new ArrayList<>();
        for (GoldStandardGroup group : groups) {
            if (mode.includes(group.forms())) {
                included.addAll(group.forms());
            }
        }
        final String[] outputs = stemmer.stem(included.toArray(String[]::new));
        if (outputs == null || outputs.length != included.size()) {
            throw new IOException("JMH stemmer " + stemmerName + " returned an invalid output batch for language "
                    + language + " and processing mode " + mode + ".");
        }
        final int[] index = {0};
        return evaluate(stemmerName, language, mode, groups, word -> {
            final String output = outputs[index[0]++];
            if (output == null) {
                throw new IOException("JMH stemmer " + stemmerName + " returned null for language " + language
                        + ", processing mode " + mode + ", and word form '" + word + "'.");
            }
            return output;
        });
    }

    /** Calculates C2(n) with checked arithmetic. */
    /* default */ static long chooseTwo(final long value) {
        if (value < 0) { throw new IllegalArgumentException("Pair population must not be negative."); }
        try {
            return value % 2 == 0 ? Math.multiplyExact(value / 2, value - 1)
                    : Math.multiplyExact(value, (value - 1) / 2);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Arithmetic overflow while calculating unordered word-form pairs.", exception);
        }
    }
    /** Checked addition with metric context. */
    private static long add(final long left, final long right, final String context) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException exception) { throw new IllegalStateException("Arithmetic overflow in " + context + ".", exception); }
    }
    /** Checked subtraction with metric context. */
    private static long subtract(final long left, final long right, final String context) {
        try { return Math.subtractExact(left, right); }
        catch (ArithmeticException exception) { throw new IllegalStateException("Arithmetic overflow in " + context + ".", exception); }
    }
    /** Builds a contextual failure without producing a partial result. */
    private static IllegalStateException failure(final String stemmer, final String language,
            final ProcessingMode mode, final int row, final String form, final String reason, final Exception cause) {
        final String message = "Quality evaluation failed for stemmer " + stemmer + ", language " + language
                + ", processing mode " + mode + ", dictionary row " + row + ", word form '" + form + "': " + reason + ".";
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }
}
