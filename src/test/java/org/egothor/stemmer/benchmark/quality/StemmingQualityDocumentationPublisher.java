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

import org.egothor.stemmer.StemmerPatchTrieLoader.Language;

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
    private static final Set<String> LEGACY_LANGUAGES = Set.of(
            "CS_CZ", "DA_DK", "DE_DE", "ES_ES", "FA_IR", "FI_FI", "FR_FR", "HE_IL", "HU_HU", "IT_IT",
            "NB_NO", "NL_NL", "NN_NO", "PL_PL", "PT_PT", "RU_RU", "SV_SE", "UK_UA", "US_UK", "YI");
    private static final String OPTIONAL_POLIMORF = "pl-pl-polimorf";
    private static final String ACTIVE_MANIFEST = "active-snapshots.properties";
    private static final String LEGACY_SOURCE_SHA256 =
            "85763189eab4d0fbb047c2d5d3554c66abf9732182bd0d8fd758d7aef680e66f";
    private static final Map<String, Integer> POLICY_ORDER = Map.of("PRIMARY_OUTPUT", 0, "ANY_CANDIDATE", 1, "ALL_CANDIDATES", 2);
    private static final Pattern PAGE_ROW = Pattern.compile(
            "^\\| ([^|]+) \\| `([^`]+)` \\|.*\\| \\[([^]]+)]\\(([^)]+\\.md)\\) \\|$");

    /** Prevents construction of this command-line utility. */
    private StemmingQualityDocumentationPublisher() { }

    /**
     * Updates or verifies the documentation from one complete source CSV.
     *
     * @param arguments source CSV, documentation root, and either {@code update} or {@code verify}
     * @throws IOException when source or documentation access fails
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length != 4) {
            throw new IllegalArgumentException("Expected arguments: source CSV, documentation root, update or verify mode, and active snapshot file name.");
        }
        final Path source = Path.of(arguments[0]);
        final Path documentationRoot = Path.of(arguments[1]);
        final boolean update = switch (arguments[2]) {
            case "update" -> true;
            case "verify" -> false;
            default -> throw new IllegalArgumentException("Documentation mode must be update or verify.");
        };
        publish(source, documentationRoot, update, arguments[3]);
    }

    /**
     * Validates the complete result set and updates or verifies every mapped page.
     *
     * @param source authoritative complete CSV
     * @param documentationRoot repository documentation directory
     * @param update whether files may be replaced
     * @throws IOException when files cannot be read or written
     */
    static void publish(final Path source, final Path documentationRoot, final boolean update,
            final String activeSnapshotFileName) throws IOException {
        if (!activeSnapshotFileName.matches("stemming-quality-[0-9]{4}-[0-9]{2}-[0-9]{2}\\.csv")) {
            throw new IllegalArgumentException("The active stemming-quality snapshot must use a dated file name.");
        }
        if (!Files.isRegularFile(source) || source.getFileName().toString().contains("filtered")) {
            throw new IllegalArgumentException("The documentation source must be an existing complete, unfiltered CSV report: " + source);
        }
        final List<ResultRow> sourceRows = readRows(source);
        final Set<String> resultLanguages = new HashSet<>();
        sourceRows.forEach(row -> resultLanguages.add(row.language()));
        final String checksum = sha256(source);
        final Set<String> languageUniverse = publicationLanguageUniverse(
                resultLanguages, update, source, documentationRoot, checksum);
        final List<ResultRow> rows = sourceRows.stream()
                .filter(row -> languageUniverse.contains(row.language())).toList();
        final Map<String, Page> pages = readPages(
                documentationRoot.resolve("benchmarks/languages/index.md"), languageUniverse);
        validate(rows, pages.keySet(), languageUniverse);
        if (!update) {
            final Path checksumFile = documentationRoot.resolve("benchmarks/data/")
                    .resolve(activeSnapshotFileName.replace(".csv", ".sha256"));
            final String recorded = Files.readString(checksumFile, StandardCharsets.UTF_8).strip();
            if (!recorded.equals(checksum + "  " + activeSnapshotFileName)) {
                throw new IllegalStateException("The published stemming-quality checksum does not match the authoritative CSV.");
            }
            verifyActiveManifest(documentationRoot, activeSnapshotFileName);
        }
        for (Page page : pages.values()) {
            final List<ResultRow> languageRows = rows.stream().filter(row -> row.language().equals(page.language())).toList();
            final String section = render(page, languageRows, checksum, activeSnapshotFileName);
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
        final String expectedOverview = replaceMarkedSection(overview,
                renderOverview(pages, rows, checksum, activeSnapshotFileName),
                OVERVIEW_START, OVERVIEW_END);
        if (update) {
            Files.writeString(overviewPath, expectedOverview, StandardCharsets.UTF_8);
        } else if (!overview.equals(expectedOverview)) {
            throw new IllegalStateException("The generated benchmark quality overview is stale or manually altered: " + overviewPath);
        }
        if (update) {
            final Path publishedSource = documentationRoot.resolve("benchmarks/data").resolve(activeSnapshotFileName);
            Files.createDirectories(publishedSource.getParent());
            Files.copy(source, publishedSource, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(publishedSource.resolveSibling(activeSnapshotFileName.replace(".csv", ".sha256")),
                    checksum + "  " + activeSnapshotFileName + "\n", StandardCharsets.UTF_8);
            updateActiveManifest(documentationRoot, activeSnapshotFileName);
        }
        System.out.printf(Locale.ROOT, "%s stemming-quality documentation for %d languages from %d validated rows.%n",
                update ? "Updated" : "Verified", pages.size(), rows.stream().filter(row -> pages.containsKey(row.language())).count());
    }

    /** Verifies that the shared lifecycle manifest selects the supplied quality snapshot. */
    private static void verifyActiveManifest(final Path documentationRoot, final String activeSnapshotFileName)
            throws IOException {
        final Path manifest = documentationRoot.resolve("benchmarks/data").resolve(ACTIVE_MANIFEST);
        final String expected = "quality=" + activeSnapshotFileName;
        if (Files.readAllLines(manifest, StandardCharsets.UTF_8).stream().noneMatch(expected::equals)) {
            throw new IllegalStateException("The active snapshot manifest does not select " + activeSnapshotFileName + ".");
        }
    }

    /** Updates only the quality pointer while preserving the other lifecycle entries. */
    private static void updateActiveManifest(final Path documentationRoot, final String activeSnapshotFileName)
            throws IOException {
        final Path manifest = documentationRoot.resolve("benchmarks/data").resolve(ACTIVE_MANIFEST);
        final List<String> lines = Files.exists(manifest)
                ? new ArrayList<>(Files.readAllLines(manifest, StandardCharsets.UTF_8))
                : new ArrayList<>(List.of("# Radixor benchmark active snapshots v1"));
        final String entry = "quality=" + activeSnapshotFileName;
        boolean replaced = false;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith("quality=")) {
                lines.set(index, entry);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            lines.add(entry);
        }
        Files.writeString(manifest, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    /**
     * Selects either the frozen legacy snapshot or the complete current default
     * universe. Partial current-universe reports are never publishable.
     *
     * @param reportedLanguages language labels present in the source report
     * @param update whether documentation would be updated
     * @param source source CSV path
     * @param documentationRoot documentation root
     * @param checksum source CSV SHA-256
     * @return authoritative default-language universe for publication
     */
    static Set<String> publicationLanguageUniverse(final Set<String> reportedLanguages,
            final boolean update, final Path source, final Path documentationRoot, final String checksum) {
        final Set<String> authoritative = new HashSet<>();
        for (Language language : Language.values()) {
            authoritative.add(language.name());
        }
        final Set<String> unexpected = new HashSet<>(reportedLanguages);
        unexpected.removeAll(authoritative);
        unexpected.remove(OPTIONAL_POLIMORF);
        if (!unexpected.isEmpty()) {
            throw new IllegalStateException("Unknown stemming-quality language labels: " + unexpected);
        }
        final Set<String> reportedDefaults = new HashSet<>(reportedLanguages);
        reportedDefaults.retainAll(authoritative);
        if (reportedDefaults.equals(LEGACY_LANGUAGES)) {
            final Path expectedSource = documentationRoot.resolve("benchmarks/data/stemming-quality.csv")
                    .toAbsolutePath().normalize();
            final Path actualSource = source.toAbsolutePath().normalize();
            if (update || !actualSource.equals(expectedSource) || !LEGACY_SOURCE_SHA256.equals(checksum)) {
                throw new IllegalStateException("The frozen 20-language snapshot is accepted only in verify mode "
                        + "from docs/benchmarks/data/stemming-quality.csv with its historical SHA-256.");
            }
            return LEGACY_LANGUAGES;
        }
        if (reportedDefaults.equals(authoritative)) {
            if (reportedLanguages.contains(OPTIONAL_POLIMORF)) {
                throw new IllegalStateException("The active default-language stemming-quality snapshot must not "
                        + "contain optional PoliMorf rows; publish that model under a separate protocol.");
            }
            return Set.copyOf(authoritative);
        }
        final Set<String> missing = new HashSet<>(authoritative);
        missing.removeAll(reportedDefaults);
        throw new IllegalStateException("A non-legacy stemming-quality report must cover all "
                + authoritative.size() + " language defaults; missing: " + missing + ".");
    }

    /** Reads the language-code-to-page mapping from the existing documentation index. */
    private static Map<String, Page> readPages(final Path index, final Set<String> includedLanguages)
            throws IOException {
        final Map<String, Page> pages = new LinkedHashMap<>();
        for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
            final Matcher matcher = PAGE_ROW.matcher(line);
            if (matcher.matches() && includedLanguages.contains(matcher.group(2))) {
                final String language = matcher.group(2);
                final Page previous = pages.put(language,
                        new Page(language, matcher.group(1), matcher.group(4)));
                if (previous != null) {
                    throw new IllegalStateException("Duplicate language mapping in benchmark index: " + language);
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
        final List<String> required = List.of("Stemmer", "Language", "Dictionary model ID",
                "Dictionary model version", "Dictionary model SHA-256",
                "Dictionary mode", "Output policy", "Applied dictionary rows",
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
            final String expectedModelId = Language.valueOf(row.language()).defaultModelId();
            if (!row.modelId().equals(expectedModelId)) {
                throw new IllegalStateException("Stemming-quality row " + row.key()
                        + " uses model " + row.modelId() + " instead of default model " + expectedModelId + ".");
            }
            if (row.modelVersion().isBlank() || !row.modelSha256().matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("Incomplete dictionary-model provenance for " + row.key() + ".");
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
        if (!documentedLanguages.contains("DA_DK") || !documentedLanguages.contains("HE_IL")
                || !documentedLanguages.contains("YI")) {
            throw new IllegalStateException("The documentation mapping must contain DA_DK, HE_IL, and YI.");
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
    private static String render(final Page page, final List<ResultRow> rows, final String checksum,
            final String activeSnapshotFileName) {
        final String modelId = Language.valueOf(page.language()).defaultModelId();
        final StringBuilder output = new StringBuilder(32768);
        output.append(START).append("\n\n## Stemming Quality\n\n")
                .append("Runtime performance and linguistic grouping quality are independent dimensions. This section evaluates language `")
                .append(page.language()).append("` using the complete validated stemming-quality result matrix. Every distinct surface form is one evaluated item and can belong to several dictionary groups. Two forms are a positive pair when their group-membership sets intersect and a negative pair when those sets are disjoint. A pair shared through several groups is counted once. Exact equality with a predetermined lemma is not required.\n\n")
                .append("`ALL_WORDS` includes every valid group and its original forms. `LOWERCASE_GROUPS_ONLY` excludes an entire group when any Unicode code point is uppercase or titlecase; retained words are not lowercased or otherwise rewritten. This isolates case-handling effects without changing retained inputs. [Download the complete machine-readable result snapshot](../data/")
                .append(activeSnapshotFileName).append(").\n\n")
                .append("### Evaluation Scope and Key Findings\n\n")
                .append("The default model is `").append(modelId).append("`, loaded from classpath resource `org/egothor/stemmer/models/")
                .append(modelId).append("/stemmer.gz`. The following findings compare only deterministic `PRIMARY_OUTPUT` rows over identical included groups; candidate policies are reported separately as capability analyses.\n\n");
        for (String mode : MODES) {
            appendFinding(output, rows, mode);
        }
        for (String mode : MODES) {
            final List<ResultRow> selected = rows.stream().filter(row -> row.mode().equals(mode)).sorted(resultOrder()).toList();
            final long stemmers = selected.stream().map(ResultRow::stemmer).distinct().count();
            final long policies = selected.stream().map(ResultRow::policy).distinct().count();
            output.append("### `").append(mode).append("`\n\n")
                    .append("This mode contains **").append(selected.size()).append(" result rows**, **").append(stemmers)
                    .append(" evaluated stemmers**, and **").append(policies).append(" output policies**. Applied-row and form counts are shown per row because adapters share the language corpus but policy rows remain independently auditable. `PRIMARY_OUTPUT` and `ALL_CANDIDATES` rankings are ordered by unrounded balanced accuracy, followed by MCC, F1, over-stemming rate, over-stemming count, under-stemming rate, and stemmer. `ANY_CANDIDATE` has no single rank metric and is listed alphabetically. Balanced accuracy is a navigation metric, not a universally authoritative quality score.\n\n");
            for (String policy : List.of("PRIMARY_OUTPUT", "ANY_CANDIDATE", "ALL_CANDIDATES")) {
                final List<ResultRow> policyRows = selected.stream().filter(row -> row.policy().equals(policy)).toList();
                if (!policyRows.isEmpty()) {
                    if (policy.equals("ANY_CANDIDATE")) {
                        renderAnyCandidatePolicy(output, policyRows);
                    } else {
                        final boolean rankable = policyRows.stream()
                                .anyMatch(row -> row.isDefined("Balanced accuracy"));
                        output.append("#### `").append(policy)
                                .append(rankable ? "` ranking\n\n" : "` results (balanced accuracy `n/a`)\n\n");
                        renderPrimaryTable(output, policyRows);
                        renderDetailedTables(output, policyRows);
                    }
                }
            }
            renderCandidateAnalysis(output, selected);
        }
        appendMethodology(output);
        output.append("### Provenance\n\n")
                .append("- Authoritative source: `docs/benchmarks/data/").append(activeSnapshotFileName).append("`\n")
                .append("- Source SHA-256: `").append(checksum).append("`\n")
                .append("- Evaluation command: `./gradlew stemmingQuality --no-daemon`\n")
                .append("- Dictionary language: `").append(page.language()).append("`\n")
                .append("- Processing modes: `ALL_WORDS`, `LOWERCASE_GROUPS_ONLY`\n")
                .append("- Stemmer versions and transitive artifacts: resolved by the repository's JMH Gradle configuration and `gradle.lockfile`\n")
                .append("- Model ID, version, and SHA-256: recorded in every CSV row\n")
                .append("- Run date, core source state, JDK, operating system, and hardware: recorded on the [benchmark environment page](../reference/environment.md)\n\n")
                .append(END).append('\n');
        return output.toString();
    }

    /** Appends one deterministic primary-output winner and runner-up statement. */
    private static void appendFinding(final StringBuilder output, final List<ResultRow> rows, final String mode) {
        final List<ResultRow> primary = rows.stream().filter(row -> row.mode().equals(mode) && row.policy().equals("PRIMARY_OUTPUT"))
                .filter(row -> row.isDefined("Balanced accuracy")).sorted(resultOrder()).toList();
        if (primary.isEmpty()) {
            output.append("- **").append(mode)
                    .append(":** balanced-accuracy ranking is **n/a** because this corpus has no pairs in one required class. OI/UI and raw numerators and denominators remain authoritative.\n");
            return;
        }
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

    /** Renders the compact primary ranking without duplicating metrics available in the details. */
    private static void renderPrimaryTable(final StringBuilder output, final List<ResultRow> rows) {
        output.append("<div class=\"quality-summary\" markdown=\"1\">\n\n")
                .append("| Rank | Stemmer | Balanced accuracy | Over-stemming (OI) | Under-stemming (UI) |\n")
                .append("|---:|---|---:|---:|---:|\n");
        for (int index = 0; index < rows.size(); index++) {
            final ResultRow row = rows.get(index);
            output.append('|').append(rank(row, index)).append('|').append(displayStemmer(row.stemmer())).append('|')
                    .append(metric(row, "Balanced accuracy")).append('|')
                    .append(rate(row, "Over-stemming error pairs", "Over-stemming percentage")).append('|')
                    .append(rate(row, "Under-stemming error pairs", "Under-stemming percentage")).append("|\n");
        }
        output.append("\n</div>\n\n");
    }

    /** Renders classification, relation, and raw-count tables with repeated identities. */
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
        output.append("\n</details>\n\n<details class=\"quality-details\" markdown=\"1\"><summary>Raw pair counts</summary>\n\n")
                .append("| Rank | Stemmer | Output policy | TP | FP | FN | TN | Over error / possible | Under error / possible |\n")
                .append("|---:|---|---|---:|---:|---:|---:|---:|---:|\n");
        for (int index = 0; index < rows.size(); index++) {
            final ResultRow row = rows.get(index);
            output.append(identity(index, row)).append(rawCount(row, "True-positive pairs")).append('|')
                    .append(rawCount(row, "False-positive pairs")).append('|')
                    .append(rawCount(row, "False-negative pairs")).append('|')
                    .append(rawCount(row, "True-negative pairs")).append('|')
                    .append(row.value("Over-stemming error pairs")).append(" / ").append(row.value("Over-stemming possible pairs")).append('|')
                    .append(row.value("Under-stemming error pairs")).append(" / ").append(row.value("Under-stemming possible pairs")).append("|\n");
        }
        output.append("\n</details>\n\n");
    }

    /** Renders the two defined per-pair oracle bounds without implying one confusion matrix. */
    private static void renderAnyCandidatePolicy(final StringBuilder output, final List<ResultRow> rows) {
        final List<ResultRow> alphabetical = rows.stream().sorted(Comparator.comparing(ResultRow::stemmer)).toList();
        output.append("#### `ANY_CANDIDATE` oracle bounds\n\n")
                .append("These results are measured, not missing. `ANY_CANDIDATE` answers two separate optimistic questions for each pair: a gold-related pair avoids under-stemming when the candidate sets intersect, while a gold-negative pair avoids over-stemming when some non-colliding candidate selection exists. The oracle may choose a different candidate for the same word in different pairs. Consequently, these decisions do not form one globally realizable predicted relation or one TP/FP/FN/TN confusion matrix. Balanced accuracy, F-scores, Jaccard, Fowlkes–Mallows, and MCC are therefore mathematically **not applicable**, rather than unknown.\n\n")
                .append("<div class=\"quality-summary quality-summary--oracle\" markdown=\"1\">\n\n")
                .append("| Stemmer | Optimistic over-stemming (OI) | Optimistic under-stemming (UI) |\n")
                .append("|---|---:|---:|\n");
        for (ResultRow row : alphabetical) {
            output.append('|').append(displayStemmer(row.stemmer())).append('|')
                    .append(rate(row, "Over-stemming error pairs", "Over-stemming percentage")).append('|')
                    .append(rate(row, "Under-stemming error pairs", "Under-stemming percentage")).append("|\n");
        }
        output.append("\n</div>\n\n")
                .append("<details class=\"quality-details\" markdown=\"1\"><summary>Oracle-bound pair counts</summary>\n\n")
                .append("| Stemmer | Unavoidable over errors / gold-negative pairs | Unrepairable under errors / gold-related pairs |\n")
                .append("|---|---:|---:|\n");
        for (ResultRow row : alphabetical) {
            output.append('|').append(displayStemmer(row.stemmer())).append('|')
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
        return "|" + rank(row, index) + "|" + displayStemmer(row.stemmer()) + "|" + row.policy() + "|";
    }

    /** Returns a rank only when the navigation metric is mathematically defined. */
    private static String rank(final ResultRow row, final int index) {
        return row.isDefined("Balanced accuracy") ? Integer.toString(index + 1) : "n/a";
    }

    /** Converts authoritative adapter identifiers into a stable readable label without merging competitors. */
    private static String displayStemmer(final String identifier) {
        return identifier.endsWith("_RADIXOR") ? "Radixor" : identifier.replace('_', ' ');
    }

    /** Appends the self-contained policy, confusion-matrix, and metric definitions. */
    private static void appendMethodology(final StringBuilder output) {
        output.append("### Output Policies and Metric Definitions\n\n")
                .append("Each distinct surface form is one item and may belong to several gold groups. Two forms are gold-related when their membership sets intersect; a relation shared by several groups is counted once. `PRIMARY_OUTPUT` uses one deterministic stem per form. `ANY_CANDIDATE` is an optimistic oracle-assisted pairwise upper bound: a gold-related pair succeeds when candidates intersect, while a gold-negative pair succeeds when a non-colliding selection exists. Candidate choices may differ between pairs, so this is not deterministic runtime behaviour and does not define one confusion matrix. `ALL_CANDIDATES` activates every returned candidate; forms are related when candidate sets intersect.\n\n")
                .append("For `PRIMARY_OUTPUT` and `ALL_CANDIDATES`, `TP = underPossiblePairs - underErrorPairs`, `FN = underErrorPairs`, `FP = overErrorPairs`, and `TN = overPossiblePairs - overErrorPairs`. `ANY_CANDIDATE` publishes only its separate oracle-assisted under/over bounds; confusion-derived metrics are mathematically inapplicable and are not presented in its language-page section. Their machine-readable CSV fields remain empty. Undefined metric denominators in otherwise applicable policies are rendered as `n/a`.\n\n")
                .append("- Under-stemming rate (Paice UI): `FN / (TP + FN)`, the false-negative rate over gold-related pairs.\n")
                .append("- Over-stemming rate (Paice OI): `FP / (TN + FP)`, the false-positive rate over gold-negative pairs.\n")
                .append("- Pairwise precision: `TP / (TP + FP)`, the fraction of predicted conflations that are gold-standard positive pairs.\n")
                .append("- Pairwise recall: `TP / (TP + FN)`, the fraction of gold-standard positive pairs successfully connected.\n")
                .append("- Pairwise specificity: `TN / (TN + FP)`, the fraction of gold-negative pairs correctly separated.\n")
                .append("- Balanced accuracy: `(recall + specificity) / 2`. It gives equal weight to positive and negative pair classes and is less dominated by the large true-negative class than ordinary accuracy. It does not replace the raw errors or other metrics.\n")
                .append("- Pairwise F-beta: `((1 + betaSquared) * TP) / (((1 + betaSquared) * TP) + (betaSquared * FN) + FP)`. F0.5 emphasizes precision and penalizes over-stemming more; F1 weights precision and recall equally; F2 emphasizes recall and penalizes under-stemming more.\n")
                .append("- MCC: `(TP * TN - FP * FN) / sqrt((TP + FP) * (TP + FN) * (TN + FP) * (TN + FN))`. It uses all confusion counts and remains useful under class imbalance, except when its denominator is degenerate.\n")
                .append("- Jaccard index: `TP / (TP + FP + FN)`.\n")
                .append("- Fowlkes–Mallows index: `sqrt(precision * recall)`.\n")
                .append("- Pairwise accuracy: `(TP + TN) / (TP + TN + FP + FN)`. It can be dominated by true-negative cross-group pairs.\n")
                .append("- Pairwise error rate: `(FP + FN) / (TP + TN + FP + FN)`.\n\n")
                .append("Standard ARI, homogeneity, completeness, V-measure, and NMI are not calculated: their usual contingency-table definitions require an exclusive gold partition, while this gold standard is an overlapping cover.\n\n");
    }

    /** Renders the generated executive findings, winner matrix, and Radixor aggregates. */
    private static String renderOverview(final Map<String, Page> pages, final List<ResultRow> rows, final String checksum,
            final String activeSnapshotFileName) {
        final StringBuilder output = new StringBuilder(16384);
        output.append(OVERVIEW_START).append("\n\n## Pairwise Quality Findings\n\n")
                .append("The validated snapshot is a broad multilingual comparison covering the complete ")
                .append(pages.size()).append("-language Radixor default-model universe, with one benchmark page per language. The direct ranking below uses only deterministic `PRIMARY_OUTPUT` rows over identical per-language inputs. Candidate-aware rows are intentionally excluded from this claim.\n\n");
        int radixorWins = 0;
        int comparisons = 0;
        int directComparisons = 0;
        for (String mode : MODES) {
            for (String language : pages.keySet()) {
                final List<ResultRow> ranked = definedPrimaryRows(rows, language, mode);
                if (ranked.isEmpty()) {
                    continue;
                }
                comparisons++;
                if (ranked.size() > 1) {
                    directComparisons++;
                }
                if (ranked.getFirst().stemmer().endsWith("_RADIXOR")) {
                    radixorWins++;
                }
            }
        }
        if (radixorWins == comparisons) {
            output.append("!!! success \"Evidence-based primary-output result\"\n    Radixor achieved the highest balanced accuracy among the evaluated deterministic stemmers for every documented language in both `ALL_WORDS` and `LOWERCASE_GROUPS_ONLY`: **")
                    .append("first place in all ").append(comparisons).append(" evaluated language-mode matrices, with no exact first-place ties**. ")
                    .append(directComparisons).append(" matrices include at least one direct comparator; the two Hebrew modes report Radixor independently because no same-language adapter is configured. This statement is limited to the evaluated implementations, versions, dictionaries, adapters, and balanced-accuracy metric; it is not a universal claim about every stemming use case.\n\n");
        } else {
            output.append("Radixor ranks first in **").append(radixorWins).append(" of ").append(comparisons)
                    .append("** documented primary-output language-mode comparisons.\n\n");
        }
        output.append("### Per-language winner matrix\n\n| Language | Dictionary mode | Winner | Balanced accuracy | Runner-up | Difference | Exact tie | Deterministic stemmers |\n")
                .append("|---|---|---|---:|---|---:|---|---:|\n");
        for (Page page : pages.values()) {
            for (String mode : MODES) {
                final List<ResultRow> ranked = definedPrimaryRows(rows, page.language(), mode);
                if (ranked.isEmpty()) {
                    output.append('|').append(page.displayName()).append(" (`").append(page.language()).append("`)|")
                            .append(mode).append("|n/a|n/a|n/a|n/a|n/a|0|\n");
                    continue;
                }
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
        output.append("\n### Radixor full-coverage aggregates\n\nThese aggregates cover all ")
                .append(pages.size()).append(" documented languages. Macro balanced accuracy gives each language equal weight. Micro metrics first sum raw pair counts across languages. Unsupported third-party languages are never inserted as zero results, so this full-coverage table is not presented as a cross-stemmer common-language ranking.\n\n")
                .append("| Dictionary mode | Languages | Macro balanced accuracy | Micro balanced accuracy | Micro precision | Micro recall | Micro F1 |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (String mode : MODES) {
            final List<ResultRow> radixor = rows.stream().filter(row -> pages.containsKey(row.language()) && row.mode().equals(mode)
                    && row.policy().equals("PRIMARY_OUTPUT") && row.stemmer().endsWith("_RADIXOR")).toList();
            final List<ResultRow> definedRadixor = radixor.stream()
                    .filter(row -> row.isDefined("Balanced accuracy")).toList();
            final double macroBalanced = definedRadixor.stream()
                    .mapToDouble(row -> row.number("Balanced accuracy")).average().orElseThrow();
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
            output.append('|').append(mode).append('|').append(definedRadixor.size()).append(" / ")
                    .append(radixor.size()).append('|').append(format(macroBalanced)).append('|')
                    .append(format((recall + specificity) / 2.0)).append('|').append(format(precision)).append('|')
                    .append(format(recall)).append('|').append(format(f1)).append("|\n");
        }
        output.append("\n### Reproducible data\n\n- [Machine-readable quality snapshot](data/")
                .append(activeSnapshotFileName).append(")\n")
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
                final List<ResultRow> primary = definedPrimaryRows(rows, page.language(), mode);
                if (primary.isEmpty()) {
                    continue;
                }
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
            final List<ResultRow> ranked = definedPrimaryRows(rows, language, mode);
            if (ranked.isEmpty()) {
                continue;
            }
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

    /** Returns primary-output rows whose balanced-accuracy ranking metric is defined. */
    private static List<ResultRow> definedPrimaryRows(final List<ResultRow> rows, final String language,
            final String mode) {
        return primaryRows(rows, language, mode).stream()
                .filter(row -> row.isDefined("Balanced accuracy")).toList();
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

    /** Formats an over- or under-stemming rate as a percentage. */
    private static String rate(final ResultRow row, final String errorName, final String percentageName) {
        final String value = row.value(percentageName);
        if (value.isEmpty()) {
            return "n/a";
        }
        final double rate = Double.parseDouble(value);
        return rate < 0.000001 && row.longValue(errorName) > 0 ? "&lt;0.000001%" : String.format(Locale.ROOT, "%.6f%%", rate);
    }

    /** Formats one raw error numerator, denominator, and percentage. */
    private static String pair(final ResultRow row, final String error, final String possible, final String percentage) {
        final String rate = row.value(percentage);
        return row.value(error) + " / " + row.value(possible) + " (" + (rate.isEmpty() ? "n/a" : String.format(Locale.ROOT, "%.6f%%", Double.parseDouble(rate))) + ")";
    }

    /** Formats an inapplicable confusion count explicitly. */
    private static String rawCount(final ResultRow row, final String name) {
        final String value = row.value(name);
        return value.isEmpty() ? "n/a" : value;
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
        final String trailingContent = original.substring(end + endMarker.length()).stripLeading();
        if (trailingContent.isEmpty()) {
            return original.substring(0, start) + section;
        }
        return original.substring(0, start) + section + "\n" + trailingContent;
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
        /** Returns the dictionary model identifier. */
        private String modelId() { return value("Dictionary model ID"); }
        /** Returns the dictionary model version. */
        private String modelVersion() { return value("Dictionary model version"); }
        /** Returns the dictionary model SHA-256. */
        private String modelSha256() { return value("Dictionary model SHA-256"); }
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
        /** Reports whether a metric has a mathematically defined source value. */
        private boolean isDefined(final String name) { return !value(name).isEmpty(); }
        /** Returns false-negative pairs. */
        private long fn() { return longValue("Under-stemming error pairs"); }
        /** Returns false-positive pairs. */
        private long fp() { return longValue("Over-stemming error pairs"); }

        /** Validates raw confusion counts and the published balanced accuracy. */
        private void validate() {
            final long fp = fp();
            final long fn = fn();
            final long underPossible = longValue("Under-stemming possible pairs");
            final long overPossible = longValue("Over-stemming possible pairs");
            if (fn < 0 || fp < 0 || fn > underPossible || fp > overPossible) {
                throw new IllegalStateException("Raw pair-count invariants fail for " + key());
            }
            if (policy().equals("ANY_CANDIDATE")) {
                if (!value("True-positive pairs").isEmpty() || !value("False-positive pairs").isEmpty()
                        || !value("False-negative pairs").isEmpty() || !value("True-negative pairs").isEmpty()
                        || !value("Balanced accuracy").isEmpty() || !value("Pairwise F1").isEmpty()
                        || !value("Matthews correlation coefficient").isEmpty()) {
                    throw new IllegalStateException("Oracle-assisted ANY_CANDIDATE row contains incoherent classification metrics: "
                            + key());
                }
                return;
            }
            final long tp = longValue("True-positive pairs");
            final long tn = longValue("True-negative pairs");
            if (Math.addExact(tp, fn) != underPossible || Math.addExact(tn, fp) != overPossible) {
                throw new IllegalStateException("Raw confusion-count invariants fail for " + key());
            }
            final long recallDenominator = Math.addExact(tp, fn);
            final long specificityDenominator = Math.addExact(tn, fp);
            if (recallDenominator == 0 || specificityDenominator == 0) {
                if (!value("Balanced accuracy").isEmpty()) {
                    throw new IllegalStateException("Balanced accuracy must be empty when either class is absent for "
                            + key());
                }
                return;
            }
            final double recall = ratio(tp, recallDenominator);
            final double specificity = ratio(tn, specificityDenominator);
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
