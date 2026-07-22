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
import java.util.Locale;
import java.util.Map;

import org.egothor.stemmer.benchmark.QualityStemmerMatrix.Candidate;

/** Produces deterministic scenario and group-contribution diagnostics for audit runs. */
final class QualityAudit {
    /** Utility class. */
    private QualityAudit() {
        throw new AssertionError("No instances.");
    }

    /**
     * Evaluates one scenario and retains its highest under-stemming contributors.
     *
     * @param candidate authoritative JMH candidate
     * @param mode processing mode
     * @param groups parsed dictionary groups
     * @param limit maximum listed contributors
     * @return immutable audited scenario
     * @throws IOException if the candidate adapter fails
     */
    static Scenario evaluate(final Candidate candidate, final ProcessingMode mode,
            final List<GoldStandardGroup> groups, final int limit) throws IOException {
        final List<GoldStandardGroup> includedGroups = groups.stream().filter(group -> mode.includes(group.forms())).toList();
        final List<String> forms = new ArrayList<>();
        for (GoldStandardGroup group : includedGroups) {
            forms.addAll(group.forms());
        }
        final String[] outputs = candidate.createStemmer().stem(forms.toArray(String[]::new));
        if (outputs.length != forms.size()) {
            throw new IOException("Invalid audit output count for stemmer " + candidate.name() + ", language "
                    + candidate.language() + ", and processing mode " + mode + ".");
        }
        final int[] outputIndex = {0};
        final QualityResult result = QualityEvaluator.evaluate(candidate.name(), candidate.language().name(), mode,
                groups, word -> outputs[outputIndex[0]++]);
        final List<Contributor> contributors = new ArrayList<>();
        long exactMatches = 0;
        int offset = 0;
        final List<Integer> sizes = new ArrayList<>();
        for (GoldStandardGroup group : includedGroups) {
            final Map<String, List<String>> formsByStem = new LinkedHashMap<>();
            final String expected = group.forms().get(0);
            long mergedPairs = 0;
            for (String form : group.forms()) {
                final String output = outputs[offset++];
                formsByStem.computeIfAbsent(output, ignored -> new ArrayList<>()).add(form);
                if (expected.equals(output)) {
                    exactMatches++;
                }
            }
            for (List<String> stemForms : formsByStem.values()) {
                mergedPairs = Math.addExact(mergedPairs, QualityEvaluator.chooseTwo(stemForms.size()));
            }
            final long possible = QualityEvaluator.chooseTwo(group.forms().size());
            final long errors = Math.subtractExact(possible, mergedPairs);
            sizes.add(group.forms().size());
            if (errors > 0) {
                contributors.add(new Contributor(group.rowNumber(), group.forms().size(), formsByStem, errors, possible));
            }
        }
        contributors.sort(Comparator.comparingLong(Contributor::errorPairs).reversed()
                .thenComparingInt(Contributor::rowNumber));
        final long contributionSum = contributors.stream().mapToLong(Contributor::errorPairs).reduce(0L, Math::addExact);
        if (contributionSum != result.underErrorPairs()) {
            throw new IOException("The summed group contributions do not equal the optimized under-stemming total for "
                    + candidate.name() + ", " + candidate.language() + ", and " + mode + ".");
        }
        sizes.sort(Integer::compareTo);
        final double mean = sizes.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        final double median = median(sizes);
        final String resource = org.egothor.stemmer.StemmerModelRegistry.fromContextClassLoader()
                .requireDefault(candidate.language()).resource();
        return new Scenario(result, resource, exactMatches, forms.size(),
                sizes.isEmpty() ? 0 : sizes.get(0), sizes.isEmpty() ? 0 : sizes.get(sizes.size() - 1), mean, median,
                List.copyOf(contributors.subList(0, Math.min(limit, contributors.size()))), contributionSum);
    }

    /** Writes all audited scenarios to a fresh UTF-8 Markdown file. */
    static void write(final Path path, final List<Scenario> scenarios) throws IOException {
        final StringBuilder text = new StringBuilder(8192);
        text.append("# Stemming-quality audit\n\nThis report uses original dictionary forms and the exact JMH candidate adapters. Exact-output counts compare outputs with the first parsed field of each group; they are not interchangeable with the existing JMH exact-root counters when that corpus lowercases dictionary fields.\n\n");
        for (Scenario scenario : scenarios.stream().sorted(Comparator.comparing(item -> item.result(), QualityResult.ORDER)).toList()) {
            final QualityResult result = scenario.result();
            text.append("## ").append(result.stemmer()).append(" / ").append(result.language()).append(" / ")
                    .append(result.processingMode()).append("\n\n")
                    .append("- Dictionary source: `").append(scenario.dictionarySource()).append("`\n")
                    .append("- Processed dictionary rows: ").append(result.appliedDictionaryRows()).append("\n")
                    .append("- Processed unique word forms: ").append(result.processedWordForms()).append("\n")
                    .append("- Singleton dictionary rows: ").append(result.singletonDictionaryRows()).append("\n")
                    .append("- Dictionary rows contributing under-stemming pairs: ").append(result.dictionaryRowsContributingUnderPairs()).append("\n")
                    .append("- Group size minimum / maximum / mean / median: ").append(scenario.minimumGroupSize()).append(" / ")
                    .append(scenario.maximumGroupSize()).append(" / ").append(String.format(Locale.ROOT, "%.6f", scenario.meanGroupSize()))
                    .append(" / ").append(String.format(Locale.ROOT, "%.6f", scenario.medianGroupSize())).append("\n")
                    .append("- Exact first-field matches: ").append(scenario.exactMatches()).append(" / ").append(scenario.exactDenominator()).append("\n")
                    .append("- Under-stemming pairs: ").append(result.underErrorPairs()).append(" / ").append(result.underPossiblePairs()).append("\n")
                    .append("- Over-stemming pairs: ").append(result.overErrorPairs()).append(" / ").append(result.overPossiblePairs()).append("\n")
                    .append("- Independently summed under-stemming contributions: ").append(scenario.contributionSum()).append("\n\n")
                    .append("### Highest under-stemming contributors\n\n");
            for (Contributor contributor : scenario.contributors()) {
                text.append("#### Dictionary row ").append(contributor.rowNumber()).append("\n\n")
                        .append("Unique forms: ").append(contributor.groupSize()).append("; distinct predicted stems: ")
                        .append(contributor.formsByStem().size()).append("; contribution: ").append(contributor.errorPairs())
                        .append(" / ").append(contributor.possiblePairs()).append(" pairs.\n\n");
                for (Map.Entry<String, List<String>> entry : contributor.formsByStem().entrySet()) {
                    text.append("- Predicted stem `").append(escape(entry.getKey())).append("` (").append(entry.getValue().size())
                            .append("): ").append(entry.getValue().stream().map(QualityAudit::quoted).toList()).append("\n");
                }
                text.append('\n');
            }
        }
        final Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    /** Calculates the conventional median of a sorted integer list. */
    private static double median(final List<Integer> sorted) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        final int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0 : sorted.get(middle);
    }

    /** Escapes Markdown code-span delimiters. */
    private static String escape(final String value) {
        return value.replace("`", "\\`");
    }

    /** Quotes one original dictionary form for Markdown diagnostics. */
    private static String quoted(final String value) {
        return "`" + escape(value) + "`";
    }

    /** Immutable complete audit summary for one scenario. */
    record Scenario(QualityResult result, String dictionarySource, long exactMatches, long exactDenominator,
            int minimumGroupSize, int maximumGroupSize, double meanGroupSize, double medianGroupSize,
            List<Contributor> contributors, long contributionSum) {
    }

    /** Immutable contribution of one gold-standard group. */
    record Contributor(int rowNumber, int groupSize, Map<String, List<String>> formsByStem,
            long errorPairs, long possiblePairs) {
    }
}
