package org.egothor.stemmer.benchmark.quality;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Publishes validated stemming-quality CSV results into marked sections of the
 * existing language benchmark pages. This test-source utility never modifies
 * performance benchmark content outside its markers.
 */
public final class StemmingQualityDocumentationPublisher {
    private static final String START = "<!-- STEMMING-QUALITY:START -->";
    private static final String END = "<!-- STEMMING-QUALITY:END -->";
    private static final String OVERVIEW_START = "<!-- STEMMING-QUALITY-OVERVIEW:START -->";
    private static final String OVERVIEW_END = "<!-- STEMMING-QUALITY-OVERVIEW:END -->";
    private static final List<String> MODES = List.of("ALL_WORDS", "LOWERCASE_GROUPS_ONLY");
    private static final Map<String, Integer> POLICY_ORDER = Map.of("PRIMARY_OUTPUT", 0, "ANY_CANDIDATE", 1, "ALL_CANDIDATES", 2);
    private static final Pattern PAGE_ROW = Pattern.compile("^\\|[^|]+\\| `([^`]+)` \\| \\[([^]]+)]\\(([^)]+\\.md)\\) \\|$");
    private static final Pattern BUILT_IN_LANGUAGE_ROW = Pattern.compile("^\\|[^|]+\\| `([^`]+)` \\|.*$");

    /** Prevents construction of this command-line utility. */
    private StemmingQualityDocumentationPublisher() { }

    /**
     * Updates or verifies the documentation from one complete source CSV.
     *
     * @param arguments source CSV, documentation root, and either {@code update} or {@code verify}
     * @throws IOException when source or documentation access fails
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length != 3) {
            throw new IllegalArgumentException("Expected arguments: source CSV, documentation root, and update or verify mode.");
        }
        final Path source = Path.of(arguments[0]);
        final Path documentationRoot = Path.of(arguments[1]);
        final boolean update = switch (arguments[2]) {
            case "update" -> true;
            case "verify" -> false;
            default -> throw new IllegalArgumentException("Documentation mode must be update or verify.");
        };
        publish(source, documentationRoot, update);
    }

    /**
     * Validates the complete result set and updates or verifies every mapped page.
     *
     * @param source authoritative complete CSV
     * @param documentationRoot repository documentation directory
     * @param update whether files may be replaced
     * @throws IOException when files cannot be read or written
     */
    static void publish(final Path source, final Path documentationRoot, final boolean update) throws IOException {
        if (!Files.isRegularFile(source) || source.getFileName().toString().contains("filtered")) {
            throw new IllegalArgumentException("The documentation source must be an existing complete, unfiltered CSV report: " + source);
        }
        final List<ResultRow> rows = readRows(source);
        final Map<String, Page> pages = readPages(documentationRoot.resolve("benchmarks/languages/index.md"));
        final Set<String> languageUniverse = readLanguageUniverse(documentationRoot.resolve("built-in-languages.md"));
        validate(rows, pages.keySet(), languageUniverse);
        final String checksum = sha256(source);
        if (!update) {
            final Path checksumFile = documentationRoot.resolve("benchmarks/data/stemming-quality.sha256");
            final String recorded = Files.readString(checksumFile, StandardCharsets.UTF_8).strip();
            if (!recorded.equals(checksum + "  stemming-quality.csv")) {
                throw new IllegalStateException("The published stemming-quality checksum does not match the authoritative CSV.");
            }
        }
        for (Page page : pages.values()) {
            final List<ResultRow> languageRows = rows.stream().filter(row -> row.language().equals(page.language())).toList();
            final String section = render(page, languageRows, checksum);
            final Path path = documentationRoot.resolve("benchmarks/languages").resolve(page.file());
            final String original = Files.readString(path, StandardCharsets.UTF_8);
            final String expected = replaceSection(original, section);
            if (update) {
                Files.writeString(path, expected, StandardCharsets.UTF_8);
            } else if (!original.equals(expected)) {
                throw new IllegalStateException("Stemming-quality documentation is stale or manually altered: " + path);
            }
        }
        final Path overviewPath = documentationRoot.resolve("benchmarks/index.md");
        final String overview = Files.readString(overviewPath, StandardCharsets.UTF_8);
        final String expectedOverview = replaceMarkedSection(overview, renderOverview(pages, rows, checksum),
                OVERVIEW_START, OVERVIEW_END);
        if (update) {
            Files.writeString(overviewPath, expectedOverview, StandardCharsets.UTF_8);
        } else if (!overview.equals(expectedOverview)) {
            throw new IllegalStateException("The generated benchmark quality overview is stale or manually altered: " + overviewPath);
        }
        if (update) {
            final Path publishedSource = documentationRoot.resolve("benchmarks/data/stemming-quality.csv");
            Files.createDirectories(publishedSource.getParent());
            Files.copy(source, publishedSource, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(documentationRoot.resolve("benchmarks/data/stemming-quality.sha256"), checksum + "  stemming-quality.csv\n", StandardCharsets.UTF_8);
        }
        System.out.printf(Locale.ROOT, "%s stemming-quality documentation for %d languages from %d validated rows.%n",
                update ? "Updated" : "Verified", pages.size(), rows.stream().filter(row -> pages.containsKey(row.language())).count());
    }

    /** Reads the authoritative built-in language identifiers from the existing registry table. */
    private static Set<String> readLanguageUniverse(final Path builtInLanguages) throws IOException {
        final Set<String> languages = new HashSet<>();
        for (String line : Files.readAllLines(builtInLanguages, StandardCharsets.UTF_8)) {
            final Matcher matcher = BUILT_IN_LANGUAGE_ROW.matcher(line);
            if (matcher.matches()) {
                languages.add(matcher.group(1));
            }
        }
        if (languages.isEmpty()) {
            throw new IllegalStateException("No authoritative built-in languages were discovered in " + builtInLanguages);
        }
        return Set.copyOf(languages);
    }

    /** Reads the language-code-to-page mapping from the existing documentation index. */
    private static Map<String, Page> readPages(final Path index) throws IOException {
        final Map<String, Page> pages = new LinkedHashMap<>();
        for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
            final Matcher matcher = PAGE_ROW.matcher(line);
            if (matcher.matches()) {
                final Page previous = pages.put(matcher.group(1), new Page(matcher.group(1), matcher.group(2), matcher.group(3)));
                if (previous != null) {
                    throw new IllegalStateException("Duplicate language mapping in benchmark index: " + matcher.group(1));
                }
            }
        }
        if (pages.isEmpty()) {
            throw new IllegalStateException("No language benchmark pages were discovered in " + index);
        }
        return pages;
    }

    /** Reads and schema-validates the quoted UTF-8 CSV. */
    private static List<ResultRow> readRows(final Path source) throws IOException {
        final List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IllegalStateException("The stemming-quality CSV is empty.");
        }
        final List<String> header = parseCsv(lines.getFirst());
        final List<String> required = List.of("Stemmer", "Language", "Dictionary mode", "Output policy", "Applied dictionary rows",
                "Processed word forms", "Forms with multiple candidates", "Maximum candidates for one form", "Total candidate assignments",
                "True-positive pairs", "False-positive pairs", "False-negative pairs", "True-negative pairs",
                "Over-stemming error pairs", "Over-stemming possible pairs", "Over-stemming percentage", "Under-stemming error pairs",
                "Under-stemming possible pairs", "Under-stemming percentage", "Pairwise precision", "Pairwise recall", "Pairwise specificity",
                "Pairwise accuracy", "Balanced accuracy", "Pairwise F0.5", "Pairwise F1", "Pairwise F2", "Jaccard index",
                "Fowlkes-Mallows index", "Matthews correlation coefficient", "Pairwise error rate", "Adjusted Rand Index", "Homogeneity",
                "Completeness", "V-measure", "Normalized mutual information");
        if (!header.containsAll(required)) {
            throw new IllegalStateException("The stemming-quality CSV does not contain the required publication schema.");
        }
        final Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < header.size(); index++) {
            indexes.put(header.get(index), index);
        }
        final List<ResultRow> rows = new ArrayList<>();
        for (int line = 1; line < lines.size(); line++) {
            final List<String> values = parseCsv(lines.get(line));
            if (values.size() != header.size()) {
                throw new IllegalStateException("CSV column count differs from the header at logical row " + (line + 1));
            }
            rows.add(new ResultRow(values, indexes));
        }
        return List.copyOf(rows);
    }

    /** Parses one RFC-4180-compatible line emitted by the quality report writer. */
    private static List<String> parseCsv(final String line) {
        final List<String> values = new ArrayList<>();
        final StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            final char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }
        if (quoted) {
            throw new IllegalStateException("Unterminated quoted CSV value.");
        }
        values.add(value.toString());
        return values;
    }

    /** Validates uniqueness, coverage, raw arithmetic, metrics, and policy invariants. */
    private static void validate(final List<ResultRow> rows, final Set<String> documentedLanguages,
            final Set<String> languageUniverse) {
        final Set<String> keys = new HashSet<>();
        for (ResultRow row : rows) {
            if (!keys.add(row.key())) {
                throw new IllegalStateException("Duplicate stemming-quality result key: " + row.key());
            }
            row.validate();
        }
        final Set<String> resultLanguages = new HashSet<>();
        rows.forEach(row -> resultLanguages.add(row.language()));
        if (!resultLanguages.equals(languageUniverse)) {
            throw new IllegalStateException("Complete-report language coverage differs from the authoritative built-in universe. Results: "
                    + resultLanguages + "; authoritative languages: " + languageUniverse);
        }
        for (String language : languageUniverse) {
            for (String mode : MODES) {
                for (String policy : POLICY_ORDER.keySet()) {
                    final boolean present = rows.stream().anyMatch(row -> row.language().equals(language) && row.mode().equals(mode)
                            && row.policy().equals(policy) && row.stemmer().endsWith("_RADIXOR"));
                    if (!present) {
                        throw new IllegalStateException("The complete report omits Radixor result " + language + "/" + mode + "/" + policy);
                    }
                }
            }
        }
        for (String language : documentedLanguages) {
            final List<ResultRow> languageRows = rows.stream().filter(row -> row.language().equals(language)).toList();
            if (languageRows.isEmpty()) {
                throw new IllegalStateException("No stemming-quality results exist for documented language " + language);
            }
            for (String mode : MODES) {
                if (languageRows.stream().noneMatch(row -> row.mode().equals(mode))) {
                    throw new IllegalStateException("Missing dictionary mode " + mode + " for documented language " + language);
                }
            }
            validatePolicies(languageRows);
        }
        if (!documentedLanguages.contains("DA_DK") || !documentedLanguages.contains("YI")) {
            throw new IllegalStateException("The documentation mapping must contain DA_DK and YI.");
        }
    }

    /** Validates policy monotonicity for each multi-output scenario. */
    private static void validatePolicies(final List<ResultRow> rows) {
        final Map<String, Map<String, ResultRow>> scenarios = new HashMap<>();
        for (ResultRow row : rows) {
            scenarios.computeIfAbsent(row.stemmer() + "\u0000" + row.mode(), ignored -> new HashMap<>()).put(row.policy(), row);
        }
        for (Map<String, ResultRow> policies : scenarios.values()) {
            final ResultRow primary = policies.get("PRIMARY_OUTPUT");
            if (primary == null) {
                throw new IllegalStateException("Every documented stemmer scenario must contain PRIMARY_OUTPUT.");
            }
            if (policies.containsKey("ANY_CANDIDATE") || policies.containsKey("ALL_CANDIDATES")) {
                final ResultRow any = policies.get("ANY_CANDIDATE");
                final ResultRow all = policies.get("ALL_CANDIDATES");
                if (any == null || all == null || any.fn() > primary.fn() || all.fn() != any.fn()
                        || any.fp() > primary.fp() || all.fp() < primary.fp()) {
                    throw new IllegalStateException("Output-policy invariants fail for " + primary.key());
                }
            }
        }
    }

    /** Renders one complete generated section for a language page. */
    private static String render(final Page page, final List<ResultRow> rows, final String checksum) {
        final StringBuilder output = new StringBuilder(32768);
        output.append(START).append("\n\n## Stemming Quality\n\n")
                .append("Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `")
                .append(page.language()).append("` using the complete validated stemming-quality result matrix. Every usable dictionary row is one gold-standard group of forms expected to share a morphological family or lemma. Exact equality with a predetermined lemma is not required. Same-row pairs are positive pairs; pairs from different rows are negative pairs.\n\n")
                .append("`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/stemming-quality.csv).\n\n")
                .append("### Evaluation Scope and Key Findings\n\n")
                .append("The dictionary resource is `src/main/resources/").append(page.language().toLowerCase(Locale.ROOT)).append("/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.\n\n");
        for (String mode : MODES) {
            appendFinding(output, rows, mode);
        }
        for (String mode : MODES) {
            final List<ResultRow> selected = rows.stream().filter(row -> row.mode().equals(mode)).sorted(resultOrder()).toList();
            final long stemmers = selected.stream().map(ResultRow::stemmer).distinct().count();
            final long policies = selected.stream().map(ResultRow::policy).distinct().count();
            output.append("### `").append(mode).append("`\n\n")
                    .append("This mode contains **").append(selected.size()).append(" result rows**, **").append(stemmers)
                    .append(" evaluated stemmers**, and **").append(policies).append(" output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. Rankings are separated by output policy and ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. Balanced accuracy is a navigation metric, not a universally authoritative quality score.\n\n");
            for (String policy : List.of("PRIMARY_OUTPUT", "ANY_CANDIDATE", "ALL_CANDIDATES")) {
                final List<ResultRow> policyRows = selected.stream().filter(row -> row.policy().equals(policy)).toList();
                if (!policyRows.isEmpty()) {
                    output.append("#### `").append(policy).append("` ranking\n\n");
                    renderPrimaryTable(output, policyRows);
                    renderDetailedTables(output, policyRows);
                }
            }
            renderCandidateAnalysis(output, selected);
        }
        appendMethodology(output);
        output.append("### Provenance\n\n")
                .append("- Authoritative source: `docs/benchmarks/data/stemming-quality.csv`\n")
                .append("- Source SHA-256: `").append(checksum).append("`\n")
                .append("- Evaluation command: `./gradlew stemmingQuality`\n")
                .append("- Dictionary language: `").append(page.language()).append("`\n")
                .append("- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`\n")
                .append("- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`\n")
                .append("- Radixor version, Git revision, generation date, JDK version, operating system, and dictionary revision: not recorded in the authoritative CSV\n\n")
                .append(END).append('\n');
        return output.toString();
    }

    /** Appends one deterministic primary-output winner and runner-up statement. */
    private static void appendFinding(final StringBuilder output, final List<ResultRow> rows, final String mode) {
        final List<ResultRow> primary = rows.stream().filter(row -> row.mode().equals(mode) && row.policy().equals("PRIMARY_OUTPUT"))
                .sorted(resultOrder()).toList();
        final ResultRow winner = primary.getFirst();
        final ResultRow runnerUp = primary.size() > 1 ? primary.get(1) : null;
        output.append("- **").append(mode).append(":** `").append(displayStemmer(winner.stemmer())).append("` ranks first by balanced accuracy at **")
                .append(metric(winner, "Balanced accuracy")).append("** among ").append(primary.size()).append(" deterministic stemmers");
        if (runnerUp == null) {
            output.append("; no same-language competitor was available");
        } else {
            final double difference = winner.number("Balanced accuracy") - runnerUp.number("Balanced accuracy");
            output.append(". The runner-up is `").append(displayStemmer(runnerUp.stemmer())).append("` at ")
                    .append(metric(runnerUp, "Balanced accuracy")).append(", a difference of ")
                    .append(String.format(Locale.ROOT, "%.6f", difference));
            if (difference == 0.0) {
                output.append(" (an exact tie before formatting)");
            }
        }
        output.append(". This rank does not imply leadership in throughput or every secondary metric.\n");
    }

    /** Renders the compact primary ranking table in an accessible scroll region. */
    private static void renderPrimaryTable(final StringBuilder output, final List<ResultRow> rows) {
        output.append("<div class=\"quality-table quality-table--compact\" role=\"region\" aria-label=\"Compact stemming-quality ranking; scroll horizontally for additional columns\" tabindex=\"0\" markdown=\"1\">\n\n")
                .append("| Rank | Stemmer | Output policy | Balanced accuracy | Over-stemming | Under-stemming | F0.5 | F1 | MCC |\n")
                .append("|---:|---|---|---:|---:|---:|---:|---:|---:|\n");
        for (int index = 0; index < rows.size(); index++) {
            final ResultRow row = rows.get(index);
            output.append('|').append(index + 1).append('|').append(displayStemmer(row.stemmer())).append('|').append(row.policy()).append('|')
                    .append(metric(row, "Balanced accuracy")).append('|')
                    .append(pair(row, "Over-stemming error pairs", "Over-stemming possible pairs", "Over-stemming percentage")).append('|')
                    .append(pair(row, "Under-stemming error pairs", "Under-stemming possible pairs", "Under-stemming percentage")).append('|')
                    .append(metric(row, "Pairwise F0.5")).append('|').append(metric(row, "Pairwise F1")).append('|')
                    .append(metric(row, "Matthews correlation coefficient")).append("|\n");
        }
        output.append("\n</div>\n\n");
    }

    /** Renders classification, relation, partition, and raw-count tables with repeated identities. */
    private static void renderDetailedTables(final StringBuilder output, final List<ResultRow> rows) {
        output.append("<details class=\"quality-details\" markdown=\"1\"><summary>Classification metrics</summary>\n\n")
                .append("| Rank | Stemmer | Output policy | Precision | Recall | Specificity | Balanced accuracy | Pairwise accuracy | Error rate |\n")
                .append("|---:|---|---|---:|---:|---:|---:|---:|---:|\n");
        for (int index = 0; index < rows.size(); index++) {
            final ResultRow row = rows.get(index);
            output.append(identity(index, row)).append(metric(row, "Pairwise precision")).append('|').append(metric(row, "Pairwise recall")).append('|')
                    .append(metric(row, "Pairwise specificity")).append('|').append(metric(row, "Balanced accuracy")).append('|')
                    .append(metric(row, "Pairwise accuracy")).append('|').append(metric(row, "Pairwise error rate")).append("|\n");
        }
        output.append("\n</details>\n\n<details class=\"quality-details\" markdown=\"1\"><summary>Pair-relation metrics</summary>\n\n")
                .append("| Rank | Stemmer | Output policy | F0.5 | F1 | F2 | Jaccard | Fowlkes–Mallows | MCC |\n")
                .append("|---:|---|---|---:|---:|---:|---:|---:|---:|\n");
        for (int index = 0; index < rows.size(); index++) {
            final ResultRow row = rows.get(index);
            output.append(identity(index, row)).append(metric(row, "Pairwise F0.5")).append('|').append(metric(row, "Pairwise F1")).append('|')
                    .append(metric(row, "Pairwise F2")).append('|').append(metric(row, "Jaccard index")).append('|')
                    .append(metric(row, "Fowlkes-Mallows index")).append('|').append(metric(row, "Matthews correlation coefficient")).append("|\n");
        }
        output.append("\n</details>\n\n<details class=\"quality-details\" markdown=\"1\"><summary>Partition metrics (PRIMARY_OUTPUT only)</summary>\n\n")
                .append("| Rank | Stemmer | Output policy | Adjusted Rand Index | Homogeneity | Completeness | V-measure | Normalized mutual information |\n")
                .append("|---:|---|---|---:|---:|---:|---:|---:|\n");
        for (int index = 0; index < rows.size(); index++) {
            final ResultRow row = rows.get(index);
            output.append(identity(index, row)).append(metric(row, "Adjusted Rand Index")).append('|').append(metric(row, "Homogeneity")).append('|')
                    .append(metric(row, "Completeness")).append('|').append(metric(row, "V-measure")).append('|')
                    .append(metric(row, "Normalized mutual information")).append("|\n");
        }
        output.append("\n</details>\n\n<details class=\"quality-details\" markdown=\"1\"><summary>Raw pair counts</summary>\n\n")
                .append("| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |\n")
                .append("|---:|---|---|---:|---:|---:|---:|---:|---:|\n");
        for (int index = 0; index < rows.size(); index++) {
            final ResultRow row = rows.get(index);
            output.append(identity(index, row)).append(row.value("True-positive pairs")).append('|').append(row.value("False-positive pairs"))
                    .append('|').append(row.value("False-negative pairs")).append('|').append(row.value("True-negative pairs")).append('|')
                    .append(row.value("Over-stemming error pairs")).append(" / ").append(row.value("Over-stemming possible pairs")).append('|')
                    .append(row.value("Under-stemming error pairs")).append(" / ").append(row.value("Under-stemming possible pairs")).append("|\n");
        }
        output.append("\n</details>\n\n");
    }

    /** Renders the candidate-policy trade-off for every genuinely multi-output adapter. */
    private static void renderCandidateAnalysis(final StringBuilder output, final List<ResultRow> rows) {
        final Map<String, Map<String, ResultRow>> byStemmer = new LinkedHashMap<>();
        rows.forEach(row -> byStemmer.computeIfAbsent(row.stemmer(), ignored -> new HashMap<>()).put(row.policy(), row));
        final List<Map.Entry<String, Map<String, ResultRow>>> multi = byStemmer.entrySet().stream()
                .filter(entry -> entry.getValue().containsKey("ANY_CANDIDATE")).sorted(Map.Entry.comparingByKey()).toList();
        if (multi.isEmpty()) {
            return;
        }
        output.append("#### Multi-output analysis\n\nAlternative candidates are capability analyses, not replacements for the deterministic comparison.\n\n")
                .append("| Stemmer | Under pairs repaired | Best-case over pairs avoided | All-candidate collisions added | Multi-candidate forms | Multi-candidate share | Maximum candidates | Total candidate assignments |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Map.Entry<String, Map<String, ResultRow>> entry : multi) {
            final ResultRow primary = entry.getValue().get("PRIMARY_OUTPUT");
            final ResultRow any = entry.getValue().get("ANY_CANDIDATE");
            final ResultRow all = entry.getValue().get("ALL_CANDIDATES");
            final long forms = any.longValue("Processed word forms");
            final long multiple = any.longValue("Forms with multiple candidates");
            output.append('|').append(displayStemmer(entry.getKey())).append('|').append(primary.fn() - any.fn()).append('|')
                    .append(primary.fp() - any.fp()).append('|').append(all.fp() - primary.fp()).append('|').append(multiple).append('|')
                    .append(String.format(Locale.ROOT, "%.6f%%", 100.0 * multiple / forms)).append('|')
                    .append(any.value("Maximum candidates for one form")).append('|').append(any.value("Total candidate assignments")).append("|\n");
        }
        output.append('\n');
    }

    /** Returns the repeated rank, stemmer, and policy prefix for a detailed table row. */
    private static String identity(final int index, final ResultRow row) {
        return "|" + (index + 1) + "|" + displayStemmer(row.stemmer()) + "|" + row.policy() + "|";
    }

    /** Converts authoritative adapter identifiers into a stable readable label without merging competitors. */
    private static String displayStemmer(final String identifier) {
        return identifier.endsWith("_RADIXOR") ? "Radixor" : identifier.replace('_', ' ');
    }

    /** Appends the self-contained policy, confusion-matrix, and metric definitions. */
    private static void appendMethodology(final StringBuilder output) {
        output.append("### Output Policies and Metric Definitions\n\n")
                .append("`PRIMARY_OUTPUT` uses one deterministic stem per form and therefore defines a strict partition. `ANY_CANDIDATE` is an optimistic oracle-assisted pairwise upper bound: a same-group pair succeeds when candidates intersect, while a different-group pair succeeds when a non-colliding selection exists. Candidate choices may differ between pairs, so this is not deterministic runtime behaviour and need not represent one globally consistent assignment. `ALL_CANDIDATES` activates every returned candidate; forms are related when candidate sets intersect. Alternatives can reduce under-stemming but can introduce cross-group collisions, and the resulting relation can overlap and need not be a partition.\n\n")
                .append("For each row, `TP = underPossiblePairs - underErrorPairs`, `FN = underErrorPairs`, `FP = overErrorPairs`, and `TN = overPossiblePairs - overErrorPairs`. TP and FN concern same-group pairs; FP and TN concern different-group pairs. Consequently, under-stemming and over-stemming use different denominators. Undefined values are rendered as `n/a`.\n\n")
                .append("- Under-stemming rate: `FN / (TP + FN)`, the false-negative rate over same-group pairs.\n")
                .append("- Over-stemming rate: `FP / (TN + FP)`, the false-positive rate over different-group pairs.\n")
                .append("- Pairwise precision: `TP / (TP + FP)`, the fraction of predicted conflations that are gold-standard positive pairs.\n")
                .append("- Pairwise recall: `TP / (TP + FN)`, the fraction of gold-standard positive pairs successfully connected.\n")
                .append("- Pairwise specificity: `TN / (TN + FP)`, the fraction of different-group pairs correctly separated.\n")
                .append("- Balanced accuracy: `(recall + specificity) / 2`. It gives equal weight to positive and negative pair classes and is less dominated by the large true-negative class than ordinary accuracy. It does not replace the raw errors or other metrics.\n")
                .append("- Pairwise F-beta: `((1 + betaSquared) * TP) / (((1 + betaSquared) * TP) + (betaSquared * FN) + FP)`. F0.5 emphasizes precision and penalizes over-stemming more; F1 weights precision and recall equally; F2 emphasizes recall and penalizes under-stemming more.\n")
                .append("- MCC: `(TP * TN - FP * FN) / sqrt((TP + FP) * (TP + FN) * (TN + FP) * (TN + FN))`. It uses all confusion counts and remains useful under class imbalance, except when its denominator is degenerate.\n")
                .append("- Jaccard index: `TP / (TP + FP + FN)`.\n")
                .append("- Fowlkes–Mallows index: `sqrt(precision * recall)`.\n")
                .append("- Pairwise accuracy: `(TP + TN) / (TP + TN + FP + FN)`. It can be dominated by true-negative cross-group pairs.\n")
                .append("- Pairwise error rate: `(FP + FN) / (TP + TN + FP + FN)`.\n\n")
                .append("Adjusted Rand Index uses the gold/predicted contingency table and chance correction. Homogeneity is `1 - H(gold | predicted) / H(gold)`; completeness is `1 - H(predicted | gold) / H(predicted)`; V-measure is their harmonic mean; normalized mutual information uses the arithmetic-mean entropy normalization `MI / ((H(gold) + H(predicted)) / 2)`. These partition-only metrics apply to `PRIMARY_OUTPUT`; candidate-relation rows show `n/a`.\n\n");
    }

    /** Renders the generated executive findings, winner matrix, and Radixor aggregates. */
    private static String renderOverview(final Map<String, Page> pages, final List<ResultRow> rows, final String checksum) {
        final StringBuilder output = new StringBuilder(16384);
        output.append(OVERVIEW_START).append("\n\n## Pairwise Quality Findings\n\n")
                .append("The validated snapshot is a broad multilingual comparison covering the complete 20-language Radixor dictionary universe; 19 languages have existing benchmark pages. The direct ranking below uses only deterministic `PRIMARY_OUTPUT` rows over identical per-language inputs. Candidate-aware rows are intentionally excluded from this claim.\n\n");
        int radixorWins = 0;
        int comparisons = 0;
        for (String mode : MODES) {
            for (String language : pages.keySet()) {
                final List<ResultRow> ranked = primaryRows(rows, language, mode);
                comparisons++;
                if (ranked.getFirst().stemmer().endsWith("_RADIXOR")) {
                    radixorWins++;
                }
            }
        }
        if (radixorWins == comparisons) {
            output.append("!!! success \"Evidence-based primary-output result\"\n    Radixor achieved the highest balanced accuracy among the evaluated deterministic stemmers for every documented language in both `ALL_WORDS` and `LOWERCASE_GROUPS_ONLY`: **")
                    .append(radixorWins).append(" wins in ").append(comparisons).append(" language-mode comparisons, with no exact first-place ties**. This statement is limited to the evaluated implementations, versions, dictionaries, adapters, and balanced-accuracy metric; it is not a universal claim about every stemming use case.\n\n");
        } else {
            output.append("Radixor ranks first in **").append(radixorWins).append(" of ").append(comparisons)
                    .append("** documented primary-output language-mode comparisons.\n\n");
        }
        output.append("### Per-language winner matrix\n\n| Language | Dictionary mode | Winner | Balanced accuracy | Runner-up | Difference | Exact tie | Deterministic stemmers |\n")
                .append("|---|---|---|---:|---|---:|---|---:|\n");
        for (Page page : pages.values()) {
            for (String mode : MODES) {
                final List<ResultRow> ranked = primaryRows(rows, page.language(), mode);
                final ResultRow winner = ranked.getFirst();
                final ResultRow runner = ranked.size() > 1 ? ranked.get(1) : null;
                final double difference = runner == null ? Double.NaN : winner.number("Balanced accuracy") - runner.number("Balanced accuracy");
                output.append('|').append(page.displayName()).append(" (`").append(page.language()).append("`)|").append(mode).append('|')
                        .append(displayStemmer(winner.stemmer())).append('|').append(metric(winner, "Balanced accuracy")).append('|')
                        .append(runner == null ? "n/a" : displayStemmer(runner.stemmer())).append('|')
                        .append(runner == null ? "n/a" : String.format(Locale.ROOT, "%.9f", difference)).append('|')
                        .append(runner != null && difference == 0.0 ? "yes" : "no").append('|').append(ranked.size()).append("|\n");
            }
        }
        renderSecondaryLeaders(output, pages, rows);
        output.append("\n### Win, tie, and placement summary\n\nCounts use `PRIMARY_OUTPUT` only and retain each adapter configuration as a separate stemmer except that language-specific Radixor identifiers are combined as Radixor. Coverage is displayed explicitly; unsupported languages are absent, not losses.\n\n");
        for (String mode : MODES) {
            renderPlacementSummary(output, pages, rows, mode);
        }
        output.append("\n### Radixor full-coverage aggregates\n\nThese aggregates cover all 19 documented languages. Macro balanced accuracy gives each language equal weight. Micro metrics first sum raw pair counts across languages. Unsupported third-party languages are never inserted as zero results, so this full-coverage table is not presented as a cross-stemmer common-language ranking.\n\n")
                .append("| Dictionary mode | Languages | Macro balanced accuracy | Micro balanced accuracy | Micro precision | Micro recall | Micro F1 |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (String mode : MODES) {
            final List<ResultRow> radixor = rows.stream().filter(row -> pages.containsKey(row.language()) && row.mode().equals(mode)
                    && row.policy().equals("PRIMARY_OUTPUT") && row.stemmer().endsWith("_RADIXOR")).toList();
            final double macroBalanced = radixor.stream().mapToDouble(row -> row.number("Balanced accuracy")).average().orElseThrow();
            long tp = 0;
            long fp = 0;
            long fn = 0;
            long tn = 0;
            for (ResultRow row : radixor) {
                tp = Math.addExact(tp, row.longValue("True-positive pairs"));
                fp = Math.addExact(fp, row.fp());
                fn = Math.addExact(fn, row.fn());
                tn = Math.addExact(tn, row.longValue("True-negative pairs"));
            }
            final double precision = (double) tp / Math.addExact(tp, fp);
            final double recall = (double) tp / Math.addExact(tp, fn);
            final double specificity = (double) tn / Math.addExact(tn, fp);
            final double f1 = 2.0 * tp / (2.0 * tp + fp + fn);
            output.append('|').append(mode).append('|').append(radixor.size()).append('|').append(format(macroBalanced)).append('|')
                    .append(format((recall + specificity) / 2.0)).append('|').append(format(precision)).append('|')
                    .append(format(recall)).append('|').append(format(f1)).append("|\n");
        }
        output.append("\n### Reproducible data\n\n- [Machine-readable quality snapshot](data/stemming-quality.csv)\n")
                .append("- SHA-256: `").append(checksum).append("`\n")
                .append("- [Linguistic quality methodology](reference/linguistic-quality.md)\n")
                .append("- [Tested stemmer inventory](reference/tested-stemmers.md)\n")
                .append("- [Reproducibility and raw data](reference/reproducibility.md)\n")
                .append("- Pearson and Spearman correlation files are generated under `build/reports/stemming-quality/`; they are separated by dictionary mode and output policy. Correlation does not establish metric equivalence.\n\n")
                .append(OVERVIEW_END).append('\n');
        return output.toString();
    }

    /** Publishes every deterministic secondary-metric case led by a non-Radixor adapter. */
    private static void renderSecondaryLeaders(final StringBuilder output, final Map<String, Page> pages,
            final List<ResultRow> rows) {
        final Map<String, Boolean> metrics = new LinkedHashMap<>();
        metrics.put("Pairwise precision", true);
        metrics.put("Pairwise recall", true);
        metrics.put("Pairwise F0.5", true);
        metrics.put("Pairwise F1", true);
        metrics.put("Pairwise F2", true);
        metrics.put("Matthews correlation coefficient", true);
        metrics.put("Over-stemming percentage", false);
        metrics.put("Under-stemming percentage", false);
        final StringBuilder cases = new StringBuilder();
        int count = 0;
        for (Page page : pages.values()) {
            for (String mode : MODES) {
                final List<ResultRow> primary = primaryRows(rows, page.language(), mode);
                for (Map.Entry<String, Boolean> metric : metrics.entrySet()) {
                    final Comparator<ResultRow> comparator = Comparator.comparingDouble(row -> row.number(metric.getKey()));
                    final ResultRow leader = metric.getValue() ? primary.stream().max(comparator).orElseThrow()
                            : primary.stream().min(comparator).orElseThrow();
                    if (!leader.stemmer().endsWith("_RADIXOR")) {
                        count++;
                        cases.append('|').append(page.displayName()).append('|').append(mode).append('|').append(metric.getKey()).append('|')
                                .append(displayStemmer(leader.stemmer())).append('|').append(metric(leader, metric.getKey())).append("|\n");
                    }
                }
            }
        }
        output.append("\n### Secondary-metric trade-offs\n\nBalanced-accuracy leadership does not imply leadership on every error trade-off. The table below lists all **")
                .append(count).append("** deterministic primary-output language-mode-metric cases where a non-Radixor adapter has the best displayed value. Equal values are resolved by the authoritative row ordering and should be read as ties when the unrounded values are equal. Throughput leadership remains in the separate performance tables.\n\n")
                .append("<details class=\"quality-details\" markdown=\"1\"><summary>Non-Radixor secondary-metric leaders</summary>\n\n")
                .append("| Language | Dictionary mode | Metric | Leader | Value |\n|---|---|---|---|---:|\n")
                .append(cases).append("\n</details>\n");
    }

    /** Renders coverage-aware placement statistics for one dictionary mode. */
    private static void renderPlacementSummary(final StringBuilder output, final Map<String, Page> pages,
            final List<ResultRow> rows, final String mode) {
        final Map<String, List<Integer>> ranks = new HashMap<>();
        final Map<String, Integer> wins = new HashMap<>();
        final Map<String, Integer> ties = new HashMap<>();
        final Map<String, Integer> topThree = new HashMap<>();
        for (String language : pages.keySet()) {
            final List<ResultRow> ranked = primaryRows(rows, language, mode);
            final double leading = ranked.getFirst().number("Balanced accuracy");
            final long leaders = ranked.stream().filter(row -> row.number("Balanced accuracy") == leading).count();
            for (int index = 0; index < ranked.size(); index++) {
                final ResultRow row = ranked.get(index);
                final String name = displayStemmer(row.stemmer());
                ranks.computeIfAbsent(name, ignored -> new ArrayList<>()).add(index + 1);
                if (row.number("Balanced accuracy") == leading) {
                    wins.merge(name, 1, Integer::sum);
                    if (leaders > 1) {
                        ties.merge(name, 1, Integer::sum);
                    }
                }
                if (index < 3) {
                    topThree.merge(name, 1, Integer::sum);
                }
            }
        }
        output.append("<details class=\"quality-details\" markdown=\"1\"><summary>").append(mode).append(" placements</summary>\n\n")
                .append("| Stemmer | Evaluated languages | Wins | Exact first-place ties | Top-three placements | Average rank | Median rank |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|\n");
        final List<String> names = ranks.keySet().stream().sorted(Comparator
                .comparingInt((String name) -> wins.getOrDefault(name, 0)).reversed()
                .thenComparing(Comparator.comparingInt((String name) -> ranks.get(name).size()).reversed())
                .thenComparing(name -> name)).toList();
        for (String name : names) {
            final List<Integer> placements = ranks.get(name).stream().sorted().toList();
            final double average = placements.stream().mapToInt(Integer::intValue).average().orElseThrow();
            final int middle = placements.size() / 2;
            final double median = placements.size() % 2 == 0
                    ? (placements.get(middle - 1) + placements.get(middle)) / 2.0 : placements.get(middle);
            output.append('|').append(name).append('|').append(placements.size()).append('|').append(wins.getOrDefault(name, 0)).append('|')
                    .append(ties.getOrDefault(name, 0)).append('|').append(topThree.getOrDefault(name, 0)).append('|')
                    .append(String.format(Locale.ROOT, "%.3f", average)).append('|').append(String.format(Locale.ROOT, "%.3f", median)).append("|\n");
        }
        output.append("\n</details>\n\n");
    }

    /** Returns deterministically ranked primary-output rows for one language and mode. */
    private static List<ResultRow> primaryRows(final List<ResultRow> rows, final String language, final String mode) {
        return rows.stream().filter(row -> row.language().equals(language) && row.mode().equals(mode)
                && row.policy().equals("PRIMARY_OUTPUT")).sorted(resultOrder()).toList();
    }

    /** Formats an aggregate metric at the publication precision. */
    private static String format(final double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    /** Returns the deterministic publication order based on unrounded source values. */
    private static Comparator<ResultRow> resultOrder() {
        return Comparator.comparingDouble((ResultRow row) -> row.number("Balanced accuracy")).reversed()
                .thenComparing(Comparator.comparingDouble((ResultRow row) -> row.number("Matthews correlation coefficient")).reversed())
                .thenComparing(Comparator.comparingDouble((ResultRow row) -> row.number("Pairwise F1")).reversed())
                .thenComparingDouble(row -> row.number("Over-stemming percentage"))
                .thenComparingLong(row -> row.longValue("Over-stemming error pairs"))
                .thenComparingDouble(row -> row.number("Under-stemming percentage"))
                .thenComparing(ResultRow::stemmer).thenComparingInt(row -> POLICY_ORDER.get(row.policy()));
    }

    /** Formats a score to the publication-wide six-decimal precision. */
    private static String metric(final ResultRow row, final String name) {
        final String value = row.value(name);
        return value.isEmpty() ? "n/a" : String.format(Locale.ROOT, "%.6f", Double.parseDouble(value));
    }

    /** Formats one raw error numerator, denominator, and percentage. */
    private static String pair(final ResultRow row, final String error, final String possible, final String percentage) {
        final String rate = row.value(percentage);
        return row.value(error) + " / " + row.value(possible) + " (" + (rate.isEmpty() ? "n/a" : String.format(Locale.ROOT, "%.6f%%", Double.parseDouble(rate))) + ")";
    }

    /** Replaces an existing marked section or appends the first generated section. */
    private static String replaceSection(final String original, final String section) {
        return replaceMarkedSection(original, section, START, END);
    }

    /** Replaces or appends a section delimited by the supplied deterministic markers. */
    private static String replaceMarkedSection(final String original, final String section, final String startMarker,
            final String endMarker) {
        final int start = original.indexOf(startMarker);
        final int end = original.indexOf(endMarker);
        if ((start < 0) != (end < 0) || (start >= 0 && end < start)) {
            throw new IllegalStateException("Malformed stemming-quality generated-section markers.");
        }
        if (start < 0) {
            return original.stripTrailing() + "\n\n" + section;
        }
        return original.substring(0, start) + section + original.substring(end + endMarker.length()).stripLeading();
    }

    /** Calculates a lowercase hexadecimal SHA-256 checksum. */
    private static String sha256(final Path source) throws IOException {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source));
            final StringBuilder text = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                text.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return text.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The required SHA-256 algorithm is unavailable.", exception);
        }
    }

    /** Immutable mapping from a language identifier to its existing page. */
    private record Page(String language, String displayName, String file) { }

    /** Immutable view of one authoritative CSV row. */
    private record ResultRow(List<String> values, Map<String, Integer> indexes) {
        /** Creates and validates an immutable row view. */
        private ResultRow {
            values = List.copyOf(values);
            indexes = Map.copyOf(indexes);
        }

        /** Returns a field by its exact English header. */
        private String value(final String name) { return this.values.get(this.indexes.get(name)); }
        /** Returns the stemmer identifier. */
        private String stemmer() { return value("Stemmer"); }
        /** Returns the language identifier. */
        private String language() { return value("Language"); }
        /** Returns the dictionary-processing mode. */
        private String mode() { return value("Dictionary mode"); }
        /** Returns the output policy. */
        private String policy() { return value("Output policy"); }
        /** Returns a unique scenario key. */
        private String key() { return stemmer() + "/" + language() + "/" + mode() + "/" + policy(); }
        /** Parses a required long field. */
        private long longValue(final String name) { return Long.parseLong(value(name)); }
        /** Parses a numeric field, placing undefined values last during sorting. */
        private double number(final String name) { return value(name).isEmpty() ? Double.NEGATIVE_INFINITY : Double.parseDouble(value(name)); }
        /** Returns false-negative pairs. */
        private long fn() { return longValue("False-negative pairs"); }
        /** Returns false-positive pairs. */
        private long fp() { return longValue("False-positive pairs"); }

        /** Validates raw confusion counts and the published balanced accuracy. */
        private void validate() {
            final long tp = longValue("True-positive pairs");
            final long fp = fp();
            final long fn = fn();
            final long tn = longValue("True-negative pairs");
            if (fn != longValue("Under-stemming error pairs") || fp != longValue("Over-stemming error pairs")
                    || Math.addExact(tp, fn) != longValue("Under-stemming possible pairs")
                    || Math.addExact(tn, fp) != longValue("Over-stemming possible pairs")) {
                throw new IllegalStateException("Raw pair-count invariants fail for " + key());
            }
            final double recall = ratio(tp, Math.addExact(tp, fn));
            final double specificity = ratio(tn, Math.addExact(tn, fp));
            final double expected = (recall + specificity) / 2.0;
            if (Math.abs(expected - number("Balanced accuracy")) > 0.0000000000015) {
                throw new IllegalStateException("Balanced accuracy is inconsistent with raw counts for " + key());
            }
            if (!policy().equals("PRIMARY_OUTPUT") && !value("Adjusted Rand Index").isEmpty()) {
                throw new IllegalStateException("Partition-only metrics are present for a candidate relation: " + key());
            }
        }

        /** Divides raw counts with explicit zero-denominator handling. */
        private static double ratio(final long numerator, final long denominator) {
            if (denominator == 0) {
                throw new IllegalStateException("A balanced-accuracy component is undefined in a published result row.");
            }
            return (double) numerator / (double) denominator;
        }
    }
}
