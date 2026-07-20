package org.egothor.stemmer.benchmark.quality;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;

import org.egothor.stemmer.benchmark.QualityStemmerMatrix.Candidate;

/** Writes deterministic UTF-8 Markdown and CSV quality reports. */
public final class QualityReportWriter {
    private static final String TABLE_DELIMITER = " | ";
    /** Utility class. */
    private QualityReportWriter() { throw new AssertionError("No instances."); }

    /** Writes a report without external coverage metadata for focused formatting tests. */
    public static void writeMarkdown(final Path path, final Iterable<QualityResult> input,
            final boolean filtered) throws IOException {
        writeMarkdown(path, input, filtered, new LanguageUniverse(java.util.Map.of(), List.of(), List.of()),
                List.of(), sorted(input).size(), "PAIRWISE_F05");
    }

    /** Writes the human-readable report with methodology and required table columns. */
    public static void writeMarkdown(final Path path, final Iterable<QualityResult> input, final boolean filtered,
            final LanguageUniverse universe, final List<Candidate> candidates, final int expectedRows,
            final String rankMetric) throws IOException {
        final List<QualityResult> rows = sorted(input);
        final StringBuilder text = new StringBuilder(4096);
        text.append("# Stemming quality\n\n");
        if (filtered) {
            text.append("> This is a filtered analytical report and is not the complete JMH candidate matrix.\n\n");
        }
        text.append("## Methodology\n\nEach parsed multilingual dictionary row is a gold-standard equivalence class. Exact duplicates are removed only within that row. `PRIMARY_OUTPUT` is the deterministic JMH partition. `ANY_CANDIDATE` is an optimistic oracle-assisted pairwise upper bound: within-row sets must intersect, while a cross-row error occurs only for two equal singleton sets. `ALL_CANDIDATES` activates the complete overlap relation: within-row disjoint sets are false negatives and cross-row intersections are false positives. A shared pair is counted once. Candidate policies need not define partitions.\n\nTP is a related within-row pair, FN is an unrelated within-row pair, FP is a related cross-row pair, and TN is an unrelated cross-row pair. Under-stemming is FN/(TP+FN); over-stemming is FP/(TN+FP), so their denominators differ. F0.5 emphasizes precision, F1 balances precision and recall, and F2 emphasizes recall. Undefined values are `n/a`. Percentages and scores use `Locale.ROOT`.\n\n| Stemmer | Language | Dictionary mode | Output policy | Applied dictionary rows | Processed word forms | Distinct output stems | Over-stemming | Under-stemming | Pairwise F0.5 | Pairwise F1 | Pairwise F2 |\n|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (QualityResult row : rows) {
            text.append("| ").append(escapeMarkdown(row.stemmer())).append(TABLE_DELIMITER)
                    .append(escapeMarkdown(row.language())).append(TABLE_DELIMITER).append(row.processingMode()).append(TABLE_DELIMITER)
                    .append(row.outputPolicy()).append(TABLE_DELIMITER).append(row.appliedDictionaryRows()).append(TABLE_DELIMITER)
                    .append(row.processedWordForms()).append(TABLE_DELIMITER).append(row.distinctOutputStems()).append(TABLE_DELIMITER)
                    .append(humanMetric(row.overErrorPairs(), row.overPossiblePairs(), row.overPercentage())).append(TABLE_DELIMITER)
                    .append(humanMetric(row.underErrorPairs(), row.underPossiblePairs(), row.underPercentage())).append(TABLE_DELIMITER)
                    .append(score(row.pairwiseMetrics().f05())).append(TABLE_DELIMITER)
                    .append(score(row.pairwiseMetrics().f1())).append(TABLE_DELIMITER)
                    .append(score(row.pairwiseMetrics().f2())).append(" |\n");
        }
        appendComparisons(text, rows);
        appendCoverage(text, universe, candidates, expectedRows, rows.size());
        appendRankings(text, rows, rankMetric);
        appendSummaries(text, rows);
        text.append("\n## Reproducibility environment\n\n- JDK: `").append(System.getProperty("java.version"))
                .append("`\n- Operating system: `").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append("`\n");
        write(path, text.toString());
    }

    /** Writes machine-readable counts and separate percentage fields. */
    public static void writeCsv(final Path path, final Iterable<QualityResult> input) throws IOException {
        final StringBuilder text = new StringBuilder(4096);
        text.append("Stemmer,Language,Dictionary mode,Output policy,Applied dictionary rows,Processed word forms,Singleton dictionary rows,Forms with one candidate,Forms with multiple candidates,Maximum candidates for one form,Total candidate assignments,Distinct output stems,True-positive pairs,False-positive pairs,False-negative pairs,True-negative pairs,Over-stemming error pairs,Over-stemming possible pairs,Over-stemming percentage,Under-stemming error pairs,Under-stemming possible pairs,Under-stemming percentage,Pairwise precision,Pairwise recall,Pairwise specificity,Pairwise accuracy,Balanced accuracy,Pairwise F0.5,Pairwise F1,Pairwise F2,Jaccard index,Fowlkes-Mallows index,Matthews correlation coefficient,Pairwise error rate,Adjusted Rand Index,Homogeneity,Completeness,V-measure,Normalized mutual information\n");
        for (QualityResult row : sorted(input)) {
            final PairwiseMetrics metrics = row.pairwiseMetrics();
            appendCsv(text, row.stemmer()); appendCsv(text, row.language()); appendCsv(text, row.processingMode().name());
            appendCsv(text, row.outputPolicy().name());
            appendCsv(text, Long.toString(row.appliedDictionaryRows())); appendCsv(text, Long.toString(row.processedWordForms()));
            appendCsv(text, Long.toString(row.singletonDictionaryRows()));
            appendCsv(text, Long.toString(row.formsWithOneCandidate()));
            appendCsv(text, Long.toString(row.formsWithMultipleCandidates()));
            appendCsv(text, Long.toString(row.maximumCandidatesForOneWord()));
            appendCsv(text, Long.toString(row.totalCandidateAssignments()));
            appendCsv(text, Long.toString(row.distinctOutputStems()));
            appendCsv(text, Long.toString(metrics.truePositivePairs())); appendCsv(text, Long.toString(metrics.falsePositivePairs()));
            appendCsv(text, Long.toString(metrics.falseNegativePairs())); appendCsv(text, Long.toString(metrics.trueNegativePairs()));
            appendCsv(text, Long.toString(row.overErrorPairs()));
            appendCsv(text, Long.toString(row.overPossiblePairs())); appendCsv(text, machinePercent(row.overPercentage()));
            appendCsv(text, Long.toString(row.underErrorPairs())); appendCsv(text, Long.toString(row.underPossiblePairs()));
            appendCsv(text, machinePercent(row.underPercentage()));
            appendCsv(text, machineScore(metrics.precision())); appendCsv(text, machineScore(metrics.recall()));
            appendCsv(text, machineScore(metrics.specificity())); appendCsv(text, machineScore(metrics.accuracy()));
            appendCsv(text, machineScore(metrics.balancedAccuracy())); appendCsv(text, machineScore(metrics.f05()));
            appendCsv(text, machineScore(metrics.f1())); appendCsv(text, machineScore(metrics.f2()));
            appendCsv(text, machineScore(metrics.jaccard())); appendCsv(text, machineScore(metrics.fowlkesMallows()));
            appendCsv(text, machineScore(metrics.matthewsCorrelationCoefficient())); appendCsv(text, machineScore(metrics.errorRate()));
            final PartitionMetrics partition = row.partitionMetrics();
            appendCsv(text, partition == null ? "" : format(partition.adjustedRandIndex()));
            appendCsv(text, partition == null ? "" : format(partition.homogeneity()));
            appendCsv(text, partition == null ? "" : format(partition.completeness()));
            appendCsv(text, partition == null ? "" : format(partition.vMeasure()));
            appendCsv(text, partition == null ? "" : format(partition.normalizedMutualInformation()));
            text.setLength(text.length() - 1); text.append('\n');
        }
        write(path, text.toString());
    }

    /** Appends deterministic primary-versus-candidate trade-off rows for multi-output scenarios. */
    private static void appendComparisons(final StringBuilder text, final List<QualityResult> rows) {
        text.append("\n## Primary-versus-candidate comparison\n\n")
                .append("| Stemmer | Language | Dictionary mode | Primary under | Any under | All under | Repaired under | Primary over | Any over | Best-case avoided over | All over | Additional all-candidate over | Multi-candidate forms | Multi-candidate percent | Maximum candidates | Candidate assignments |\n")
                .append("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (QualityResult candidate : rows) {
            if (candidate.outputPolicy() != OutputPolicy.ANY_CANDIDATE) { continue; }
            final QualityResult primary = rows.stream().filter(row -> row.stemmer().equals(candidate.stemmer())
                    && row.language().equals(candidate.language()) && row.processingMode() == candidate.processingMode()
                    && row.outputPolicy() == OutputPolicy.PRIMARY_OUTPUT).findFirst().orElse(null);
            final QualityResult all = rows.stream().filter(row -> row.stemmer().equals(candidate.stemmer())
                    && row.language().equals(candidate.language()) && row.processingMode() == candidate.processingMode()
                    && row.outputPolicy() == OutputPolicy.ALL_CANDIDATES).findFirst().orElse(null);
            if (primary == null || all == null) { continue; }
            text.append("| ").append(escapeMarkdown(candidate.stemmer())).append(TABLE_DELIMITER)
                    .append(escapeMarkdown(candidate.language())).append(TABLE_DELIMITER).append(candidate.processingMode()).append(TABLE_DELIMITER)
                    .append(primary.underErrorPairs()).append(TABLE_DELIMITER).append(candidate.underErrorPairs()).append(TABLE_DELIMITER)
                    .append(all.underErrorPairs()).append(TABLE_DELIMITER)
                    .append(primary.underErrorPairs() - candidate.underErrorPairs()).append(TABLE_DELIMITER)
                    .append(primary.overErrorPairs()).append(TABLE_DELIMITER).append(candidate.overErrorPairs()).append(TABLE_DELIMITER)
                    .append(primary.overErrorPairs() - candidate.overErrorPairs()).append(TABLE_DELIMITER)
                    .append(all.overErrorPairs()).append(TABLE_DELIMITER)
                    .append(all.overErrorPairs() - primary.overErrorPairs()).append(TABLE_DELIMITER)
                    .append(candidate.formsWithMultipleCandidates()).append(TABLE_DELIMITER)
                    .append(String.format(Locale.ROOT, "%.6f%%", 100.0 * candidate.formsWithMultipleCandidates()
                            / candidate.processedWordForms())).append(TABLE_DELIMITER)
                    .append(candidate.maximumCandidatesForOneWord()).append(TABLE_DELIMITER)
                    .append(candidate.totalCandidateAssignments()).append(" |\n");
        }
    }

    /** Appends validated language, adapter, policy, and row-count coverage. */
    private static void appendCoverage(final StringBuilder text, final LanguageUniverse universe,
            final List<Candidate> candidates, final int expectedRows, final int actualRows) {
        text.append("\n## Matrix coverage\n\n- Discovered dictionary languages: ").append(universe.resourceDirectories()).append("\n")
                .append("- Discovered `StemmerPatchTrieLoader.Language` values: ").append(universe.enumerationValues()).append("\n")
                .append("- Reconciled mappings: ").append(universe.dictionaries().entrySet().stream()
                        .sorted(java.util.Map.Entry.comparingByKey()).map(entry -> entry.getKey() + " -> " + entry.getValue().getFileName()).toList()).append("\n")
                .append("- Discovered adapter-language mappings: ").append(candidates.size()).append("\n")
                .append("- Expected result rows: ").append(expectedRows).append("\n")
                .append("- Actual result rows: ").append(actualRows).append("\n\n")
                .append("Unsupported third-party combinations are excluded because their authoritative JMH adapter metadata declares no mapping for that language. They are not emitted as zero-valued rows. Radixor is independently registered for every reconciled dictionary language.\n");
        final java.util.Map<String, Set<String>> support = new java.util.TreeMap<>();
        for (Candidate candidate : candidates) { support.computeIfAbsent(candidate.name(), ignored -> new TreeSet<>()).add(candidate.language().name()); }
        text.append("\n| Adapter | Supported language count | Supported languages |\n|---|---:|---|\n");
        support.forEach((name, languages) -> text.append("| ").append(escapeMarkdown(name)).append(TABLE_DELIMITER)
                .append(languages.size()).append(TABLE_DELIMITER).append(languages).append(" |\n"));
    }

    /** Appends policy-separated rankings for the requested navigation metric and all required alternatives. */
    private static void appendRankings(final StringBuilder text, final List<QualityResult> rows, final String selectedMetric) {
        text.append("\n## Rankings\n\nThe default or selected ranking metric (`").append(selectedMetric)
                .append("`) is a navigation choice, not a declaration of universal scientific superiority. Policies are ranked separately. Full-coverage and common-language comparisons must not be conflated.\n");
        final List<String> metricNames = List.of("Pairwise F0.5", "Pairwise F1", "Pairwise F2", "Jaccard index",
                "Fowlkes-Mallows index", "Matthews correlation coefficient", "Balanced accuracy", "Adjusted Rand Index");
        for (String metric : metricNames) {
            text.append("\n### ").append(metric).append("\n\n| Output policy | Stemmer | Language | Dictionary mode | Score |\n|---|---|---|---|---:|\n");
            rows.stream().filter(row -> !metric.equals("Adjusted Rand Index") || row.outputPolicy() == OutputPolicy.PRIMARY_OUTPUT)
                    .sorted(Comparator.comparingDouble((QualityResult row) -> rankingValue(row, metric)).reversed()
                            .thenComparingDouble(row -> row.overPercentage().orElse(Double.POSITIVE_INFINITY))
                            .thenComparingLong(QualityResult::overErrorPairs)
                            .thenComparingDouble(row -> row.underPercentage().orElse(Double.POSITIVE_INFINITY))
                            .thenComparing(QualityResult::stemmer).thenComparing(QualityResult::language))
                    .limit(25).forEach(row -> text.append("| ").append(row.outputPolicy()).append(TABLE_DELIMITER)
                            .append(escapeMarkdown(row.stemmer())).append(TABLE_DELIMITER).append(row.language()).append(TABLE_DELIMITER)
                            .append(row.processingMode()).append(TABLE_DELIMITER).append(score(metricValue(row, metric))).append(" |\n"));
        }
    }

    /** Returns one optional ranking metric. */
    private static OptionalDouble metricValue(final QualityResult row, final String metric) {
        return switch (metric) {
            case "Pairwise F0.5" -> row.pairwiseMetrics().f05(); case "Pairwise F1" -> row.pairwiseMetrics().f1();
            case "Pairwise F2" -> row.pairwiseMetrics().f2(); case "Jaccard index" -> row.pairwiseMetrics().jaccard();
            case "Fowlkes-Mallows index" -> row.pairwiseMetrics().fowlkesMallows();
            case "Matthews correlation coefficient" -> row.pairwiseMetrics().matthewsCorrelationCoefficient();
            case "Balanced accuracy" -> row.pairwiseMetrics().balancedAccuracy();
            case "Adjusted Rand Index" -> row.partitionMetrics() == null ? OptionalDouble.empty()
                    : OptionalDouble.of(row.partitionMetrics().adjustedRandIndex());
            default -> OptionalDouble.empty();
        };
    }

    /** Appends full-coverage micro and macro summaries with explicit coverage. */
    private static void appendSummaries(final StringBuilder text, final List<QualityResult> rows) {
        text.append("\n## Aggregate summaries\n\nMicro values sum raw confusion counts before calculation. Macro values average defined per-language F1 values.\n\n")
                .append("| Stemmer | Dictionary mode | Output policy | Languages | Micro F0.5 | Micro F1 | Micro F2 | Macro F1 | Macro contributing languages |\n")
                .append("|---|---|---|---:|---:|---:|---:|---:|---:|\n");
        final java.util.Map<String, List<QualityResult>> groups = new java.util.TreeMap<>();
        for (QualityResult row : rows) {
            groups.computeIfAbsent(row.stemmer() + "\u0000" + row.processingMode() + "\u0000" + row.outputPolicy(),
                    ignored -> new ArrayList<>()).add(row);
        }
        for (List<QualityResult> group : groups.values()) {
            final QualityResult first = group.get(0); long tp = 0; long fp = 0; long fn = 0; long tn = 0;
            double macroF1 = 0.0; int macroCount = 0; final Set<String> languages = new TreeSet<>();
            for (QualityResult row : group) {
                final PairwiseMetrics metrics = row.pairwiseMetrics();
                tp = Math.addExact(tp, metrics.truePositivePairs()); fp = Math.addExact(fp, metrics.falsePositivePairs());
                fn = Math.addExact(fn, metrics.falseNegativePairs()); tn = Math.addExact(tn, metrics.trueNegativePairs());
                if (metrics.f1().isPresent()) { macroF1 += metrics.f1().getAsDouble(); macroCount++; }
                languages.add(row.language());
            }
            final PairwiseMetrics micro = new PairwiseMetrics(tp, fp, fn, tn);
            text.append("| ").append(escapeMarkdown(first.stemmer())).append(TABLE_DELIMITER).append(first.processingMode())
                    .append(TABLE_DELIMITER).append(first.outputPolicy()).append(TABLE_DELIMITER).append(languages.size())
                    .append(TABLE_DELIMITER).append(score(micro.f05())).append(TABLE_DELIMITER).append(score(micro.f1()))
                    .append(TABLE_DELIMITER).append(score(micro.f2())).append(TABLE_DELIMITER)
                    .append(macroCount == 0 ? "n/a" : format(macroF1 / macroCount)).append(TABLE_DELIMITER)
                    .append(macroCount).append(" |\n");
        }
        Set<String> common = null;
        final java.util.Map<String, Set<String>> byStemmer = new java.util.TreeMap<>();
        for (QualityResult row : rows) { byStemmer.computeIfAbsent(row.stemmer(), ignored -> new TreeSet<>()).add(row.language()); }
        for (Set<String> supported : byStemmer.values()) {
            if (common == null) { common = new TreeSet<>(supported); } else { common.retainAll(supported); }
        }
        text.append("\n### Common-language comparison\n\nCommon language intersection across displayed stemmers: ")
                .append(common == null ? Set.of() : common).append(". Unsupported languages are not assigned zero scores.\n");
    }

    /** Converts an undefined metric to negative infinity for descending navigation order. */
    private static double rankingValue(final QualityResult row, final String metric) { return metricValue(row, metric).orElse(Double.NEGATIVE_INFINITY); }

    /** Returns a sorted defensive list for deterministic output. */
    private static List<QualityResult> sorted(final Iterable<QualityResult> input) {
        final List<QualityResult> rows = new ArrayList<>();
        input.forEach(rows::add); rows.sort(QualityResult.ORDER); return rows;
    }
    /** Formats one human-readable ratio. */
    private static String humanMetric(final long errors, final long possible, final OptionalDouble percentage) {
        if (percentage.isEmpty()) { return errors + " / " + possible + " (n/a)"; }
        return String.format(Locale.ROOT, "%d / %d (%.6f%%)", errors, possible, percentage.getAsDouble());
    }
    /** Formats one optional machine-readable percentage. */
    private static String machinePercent(final OptionalDouble percentage) {
        return percentage.isEmpty() ? "" : String.format(Locale.ROOT, "%.6f", percentage.getAsDouble());
    }
    /** Formats one bounded or signed score for Markdown. */
    private static String score(final OptionalDouble value) { return value.isEmpty() ? "n/a" : format(value.getAsDouble()); }
    /** Formats one optional score for machine-readable output. */
    private static String machineScore(final OptionalDouble value) { return value.isEmpty() ? "" : format(value.getAsDouble()); }
    /** Formats an unrounded calculation deterministically with scientific precision. */
    private static String format(final double value) { return String.format(Locale.ROOT, "%.12f", value); }
    /** Appends one correctly quoted CSV field and delimiter. */
    private static void appendCsv(final StringBuilder output, final String value) {
        output.append('"').append(value.replace("\"", "\"\"")).append("\",");
    }
    /** Escapes Markdown table delimiters. */
    private static String escapeMarkdown(final String value) { return value.replace("|", "\\|"); }
    /** Creates the parent directory and atomically delegates UTF-8 file writing. */
    private static void write(final Path path, final String content) throws IOException {
        final Path parent = path.toAbsolutePath().getParent();
        if (parent != null) { Files.createDirectories(parent); }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
