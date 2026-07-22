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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;

/** Writes deterministic Pearson and tied-rank Spearman correlations within compatible cohorts. */
final class MetricCorrelationWriter {
    /** Stable metric extractors used for correlation analysis. */
    private static final Map<String, Function<QualityResult, OptionalDouble>> METRICS = metrics();
    /** Utility class. */
    private MetricCorrelationWriter() { throw new AssertionError("No instances."); }

    /** Writes both correlation reports from unrounded per-language scenario values. */
    static void write(final Path pearson, final Path spearman, final List<QualityResult> results) throws IOException {
        writeOne(pearson, results, false); writeOne(spearman, results, true);
    }

    /** Writes one correlation method with explicit missing-value reasons. */
    private static void writeOne(final Path path, final List<QualityResult> results, final boolean ranks) throws IOException {
        final StringBuilder output = new StringBuilder("Aggregation,Dictionary mode,Output policy,Metric A,Metric B,Observation count,Correlation,Missing-value reason\n");
        for (ProcessingMode mode : ProcessingMode.values()) {
            for (OutputPolicy policy : OutputPolicy.values()) {
                final List<QualityResult> cohort = results.stream().filter(row -> row.processingMode() == mode
                        && row.outputPolicy() == policy).toList();
                final List<String> names = new ArrayList<>(METRICS.keySet());
                if (policy != OutputPolicy.PRIMARY_OUTPUT) { names.remove("Adjusted Rand Index"); }
                for (int left = 0; left < names.size(); left++) {
                    for (int right = left; right < names.size(); right++) {
                        append(output, mode, policy, names.get(left), names.get(right), cohort, ranks);
                    }
                }
            }
        }
        final Path parent = path.toAbsolutePath().getParent(); if (parent != null) { Files.createDirectories(parent); }
        Files.writeString(path, output.toString(), StandardCharsets.UTF_8);
    }

    /** Appends one coefficient after pairwise removal of undefined observations. */
    private static void append(final StringBuilder output, final ProcessingMode mode, final OutputPolicy policy,
            final String leftName, final String rightName, final List<QualityResult> cohort, final boolean ranks) {
        final List<Double> left = new ArrayList<>(); final List<Double> right = new ArrayList<>();
        for (QualityResult row : cohort) {
            final OptionalDouble a = METRICS.get(leftName).apply(row); final OptionalDouble b = METRICS.get(rightName).apply(row);
            if (a.isPresent() && b.isPresent()) { left.add(a.getAsDouble()); right.add(b.getAsDouble()); }
        }
        String value = ""; String reason = "";
        if (left.size() < 3) { reason = "Fewer than three defined observations."; }
        else {
            final double[] a = ranks ? ranks(left) : values(left); final double[] b = ranks ? ranks(right) : values(right);
            final OptionalDouble correlation = pearson(a, b);
            if (correlation.isEmpty()) { reason = "At least one metric has zero variance."; }
            else { value = String.format(java.util.Locale.ROOT, "%.12f", correlation.getAsDouble()); }
        }
        output.append("Per-language scenario,").append(mode).append(',').append(policy).append(',')
                .append(csv(leftName)).append(',').append(csv(rightName)).append(',').append(left.size()).append(',')
                .append(value).append(',').append(csv(reason)).append('\n');
    }

    /** Calculates Pearson correlation with an empty result for zero variance. */
    private static OptionalDouble pearson(final double[] left, final double[] right) {
        double leftMean = 0.0; double rightMean = 0.0;
        for (int index = 0; index < left.length; index++) { leftMean += left[index]; rightMean += right[index]; }
        leftMean /= left.length; rightMean /= right.length;
        double covariance = 0.0; double leftVariance = 0.0; double rightVariance = 0.0;
        for (int index = 0; index < left.length; index++) {
            final double a = left[index] - leftMean; final double b = right[index] - rightMean;
            covariance += a * b; leftVariance += a * a; rightVariance += b * b;
        }
        return leftVariance == 0.0 || rightVariance == 0.0 ? OptionalDouble.empty()
                : OptionalDouble.of(covariance / Math.sqrt(leftVariance * rightVariance));
    }

    /** Assigns deterministic average ranks to tied values. */
    private static double[] ranks(final List<Double> input) {
        final List<Integer> order = new ArrayList<>(); for (int index = 0; index < input.size(); index++) { order.add(index); }
        order.sort(Comparator.comparingDouble(input::get)); final double[] ranks = new double[input.size()];
        int start = 0; while (start < order.size()) {
            int end = start + 1; while (end < order.size() && input.get(order.get(start)).equals(input.get(order.get(end)))) { end++; }
            final double rank = (start + 1 + end) / 2.0; for (int index = start; index < end; index++) { ranks[order.get(index)] = rank; }
            start = end;
        }
        return ranks;
    }
    /** Copies boxed values into a primitive array. */
    private static double[] values(final List<Double> values) { final double[] result = new double[values.size()]; for (int index = 0; index < result.length; index++) { result[index] = values.get(index); } return result; }
    /** Defines stable metric names and unrounded extractors. */
    private static Map<String, Function<QualityResult, OptionalDouble>> metrics() {
        final Map<String, Function<QualityResult, OptionalDouble>> values = new LinkedHashMap<>();
        values.put("Pairwise F0.5", row -> row.pairwiseMetrics().f05()); values.put("Pairwise F1", row -> row.pairwiseMetrics().f1());
        values.put("Pairwise F2", row -> row.pairwiseMetrics().f2()); values.put("Jaccard", row -> row.pairwiseMetrics().jaccard());
        values.put("Fowlkes-Mallows", row -> row.pairwiseMetrics().fowlkesMallows());
        values.put("Matthews correlation coefficient", row -> row.pairwiseMetrics().matthewsCorrelationCoefficient());
        values.put("Balanced accuracy", row -> row.pairwiseMetrics().balancedAccuracy());
        values.put("Adjusted Rand Index", row -> row.partitionMetrics() == null ? OptionalDouble.empty() : OptionalDouble.of(row.partitionMetrics().adjustedRandIndex()));
        return java.util.Collections.unmodifiableMap(values);
    }
    /** Quotes one CSV field. */
    private static String csv(final String value) { return '"' + value.replace("\"", "\"\"") + '"'; }
}
