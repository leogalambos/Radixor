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
package org.egothor.stemmer.benchmark.generalization;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.FrequencyTrieBuilders;
import org.egothor.stemmer.PatchCommandEncoder;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.egothor.stemmer.StemmerDictionaryParser;
import org.egothor.stemmer.StemmerModelDescriptor;
import org.egothor.stemmer.StemmerModelRegistry;
import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.egothor.stemmer.TrieStatistics;
import org.egothor.stemmer.WordTraversalDirection;
import org.egothor.stemmer.benchmark.quality.GoldStandardGroup;
import org.egothor.stemmer.benchmark.quality.PairwiseMetrics;
import org.egothor.stemmer.benchmark.quality.ProcessingMode;
import org.egothor.stemmer.benchmark.quality.QualityEvaluator;
import org.egothor.stemmer.benchmark.quality.QualityResult;

/**
 * Runs a deterministic grid-search experiment over edit-operation cost
 * configurations.
 *
 * <p>
 * For every bundled language, deterministic split seed, and non-redundant
 * combination of (delete, insert, replace, match) costs drawn from the
 * configured value sets, the application:
 * <ol>
 * <li>Builds a trie from the selected training rows at each knowledge level.</li>
 * <li>Counts both generated training commands and commands physically retained
 *     by that reduced trie.</li>
 * <li>Compares the retained command count against a baseline (1, 1, 1, 0) trie
 *     built from the identical training rows to determine
 *     viability: configurations with &gt;10&times; baseline patch commands are
 *     {@code NOT_VIABLE}; configurations with &gt;5&times; are {@code MARGINAL}
 *     classifications. Classification is reported but does not censor results.</li>
 * <li>Evaluates generalization quality at each
 *     training-knowledge level (10 %, 20 %, …, 100 %) using the dictionary
 *     itself as the gold standard.</li>
 * <li>Computes trie structural statistics and separate whole-dictionary,
 *     withheld-family, and unseen-surface pairwise quality metrics.</li>
 * </ol>
 *
 * <p>
 * Output is a single UTF-8 CSV file whose rows cover all selected languages,
 * cost configurations, and knowledge levels. The application discovers model
 * descriptors through the context class loader, reads model resources from the
 * runtime class path, streams rows through a temporary file, creates the output
 * parent directory when necessary, and atomically replaces an existing output
 * file when supported by the filesystem.</p>
 *
 * <p>The implementation has no mutable global state. Separate invocations may
 * run concurrently provided that they write to different output paths.</p>
 *
 * @apiNote The report contains raw exploratory observations and does not select
 *          an optimal configuration. Any subsequent selection must predeclare
 *          its quality-versus-size objective, use an independent validation
 *          scope, and reserve an untouched external test set for the final
 *          estimate.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class EditCostSensitivityApplication {

    /** Cost values used for the delete, insert, and replace operations. */
    /* default */ static final List<Integer> EDIT_COST_VALUES = List.of(1, 2, 3, 5, 10);

    /** Cost values used for the match (skip) operation. */
    /* default */ static final List<Integer> MATCH_COST_VALUES = List.of(0, 1);

    /** Ratio threshold above which a configuration is flagged as {@code MARGINAL}. */
    /* default */ static final double MARGINAL_THRESHOLD = 5.0d;

    /** Ratio threshold above which a configuration is flagged as {@code NOT_VIABLE}. */
    /* default */ static final double NOT_VIABLE_THRESHOLD = 10.0d;

    /** Default deterministic seed retained for explicitly requested single-seed runs. */
    /* default */ static final long DEFAULT_SEED = DictionaryGeneralizationApplication.SEEDS[0];

    /** Traversal direction used by the production stemmer. */
    private static final WordTraversalDirection DIRECTION = WordTraversalDirection.BACKWARD;

    /** Reduction settings matching the production stemmer compilation. */
    private static final ReductionSettings REDUCTION_SETTINGS = new ReductionSettings(
            ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS,
            ReductionSettings.DEFAULT_DOMINANT_WINNER_MIN_PERCENT,
            ReductionSettings.DEFAULT_DOMINANT_WINNER_OVER_SECOND_RATIO, true);

    /** Baseline cost configuration. */
    /* default */ static final CostConfig BASELINE = new CostConfig(1, 1, 1, 0);

    private static final String PROTOCOL_VERSION = "radixor-cost-sensitivity-v4";

    /** Required provenance arguments before optional seed and language filters. */
    private static final int REQUIRED_ARGUMENT_COUNT = 5;

    /** Largest accepted argument count. */
    private static final int MAXIMUM_ARGUMENT_COUNT = 8;

    /* default */ static final String HEADER = String.join(",",
            "protocol_version", "record_type", "radixor_java_version", "source_revision", "source_state",
            "generator_sha256",
            "language", "model_id", "model_version", "model_sha256", "seed",
            "delete_cost", "insert_cost", "replace_cost", "match_cost", "cost_label",
            "equivalent_cost_labels",
            "training_percent", "selected_rows", "total_rows", "withheld_rows", "excluded_overlap_occurrences",
            "training_generated_distinct_patch_commands", "baseline_training_generated_distinct_patch_commands",
            "trie_distinct_patch_commands", "baseline_trie_distinct_patch_commands",
            "trie_distinct_patch_command_ratio", "viability",
            "whole_correct", "whole_total", "whole_changed_correct", "whole_changed_total",
            "whole_root_correct", "whole_root_total",
            "withheld_correct", "withheld_total", "withheld_changed_correct", "withheld_changed_total",
            "withheld_root_correct", "withheld_root_total",
            "unseen_correct", "unseen_total", "unseen_changed_correct", "unseen_changed_total",
            "unseen_root_correct", "unseen_root_total",
            "trie_internal_nodes", "trie_leaves", "trie_edges", "trie_accepting_leaves",
            "trie_value_references", "trie_logical_leaf_paths", "trie_longest_path",
            "trie_avg_path_length", "trie_dense_lookup_nodes", "trie_dense_table_slots",
            "whole_tp", "whole_fp", "whole_fn", "whole_tn",
            "whole_over_error_pairs", "whole_over_possible_pairs", "whole_over_percent",
            "whole_under_error_pairs", "whole_under_possible_pairs", "whole_under_percent",
            "whole_precision", "whole_recall", "whole_specificity", "whole_balanced_accuracy",
            "whole_f05", "whole_f1", "whole_f2", "whole_jaccard", "whole_fowlkes_mallows", "whole_mcc",
            "whole_error_rate",
            "withheld_tp", "withheld_fp", "withheld_fn", "withheld_tn",
            "withheld_over_error_pairs", "withheld_over_possible_pairs", "withheld_over_percent",
            "withheld_under_error_pairs", "withheld_under_possible_pairs", "withheld_under_percent",
            "withheld_precision", "withheld_recall", "withheld_specificity", "withheld_balanced_accuracy",
            "withheld_f05", "withheld_f1", "withheld_f2", "withheld_jaccard", "withheld_fowlkes_mallows",
            "withheld_mcc", "withheld_error_rate",
            "unseen_tp", "unseen_fp", "unseen_fn", "unseen_tn",
            "unseen_over_error_pairs", "unseen_over_possible_pairs", "unseen_over_percent",
            "unseen_under_error_pairs", "unseen_under_possible_pairs", "unseen_under_percent",
            "unseen_precision", "unseen_recall", "unseen_specificity", "unseen_balanced_accuracy",
            "unseen_f05", "unseen_f1", "unseen_f2", "unseen_jaccard", "unseen_fowlkes_mallows", "unseen_mcc",
            "unseen_error_rate");

    /** Number of columns required in every emitted report row. */
    private static final int HEADER_FIELD_COUNT = HEADER.split(",", -1).length;

    /** Field delimiter used by the unquoted protocol CSV. */
    private static final char CSV_DELIMITER = ',';

    /** Utility class. */
    private EditCostSensitivityApplication() {
        throw new AssertionError("No instances.");
    }

    /**
     * Generates and writes the complete cost-sensitivity CSV report.
     *
     * @param arguments output CSV path, Radixor Java version, source revision,
     *                  source state, generator source path, optionally one long
     *                  seed (or {@code all}), and optionally an exact
     *                  {@link StemmerPatchTrieLoader.Language} name, and optionally
     *                  one exact cost label such as {@code D1I1R1M0}
     * @throws IOException when a model dictionary cannot be read or the CSV cannot
     *                     be written
     * @throws IllegalArgumentException if the argument count, seed, or language
     *                                  name is invalid
     */
    public static void main(final String[] arguments) throws IOException {
        final RunOptions options = parseArguments(arguments);
        if ("PREFLIGHT".equals(options.costFilter())) {
            writePreflight(options);
        } else {
            writeReport(options);
        }
    }

    /** Writes the inexpensive dictionary order and exact-equivalence class census. */
    private static void writePreflight(final RunOptions options) throws IOException {
        final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
        final List<LanguageWork> languages = orderLanguagesByBaselineCommandCount(
                selectLanguages(options.languageFilter()), registry);
        final List<CostConfig> grid = generateGrid();
        final List<String> lines = new ArrayList<>(languages.size() + 1);
        lines.add("language,model_id,model_version,model_sha256,dictionary_rows,baseline_patch_commands,"
                + "normalized_configurations,exact_equivalence_classes");
        for (final LanguageWork language : languages) {
            final List<DictionaryRow> rows = readRows(language.descriptor());
            final int classes = groupEquivalentConfigurations(rows, grid).size();
            lines.add(language.language() + "," + language.descriptor().id() + ","
                    + language.descriptor().version() + "," + language.descriptor().sha256() + ","
                    + language.dictionaryRows() + "," + language.baselineCommandCount() + ","
                    + grid.size() + "," + classes);
            System.out.printf(Locale.ROOT, "Preflight %s: %,d baseline commands, %d equivalence classes%n",
                    language.language(), language.baselineCommandCount(), classes);
        }
        final Path output = options.output().toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.write(output, lines, StandardCharsets.UTF_8);
        System.out.println("Cost-sensitivity preflight CSV: " + output);
    }

    /** Parses and validates command-line options without performing report I/O. */
    private static RunOptions parseArguments(final String... arguments) throws IOException {
        if (arguments.length < REQUIRED_ARGUMENT_COUNT || arguments.length > MAXIMUM_ARGUMENT_COUNT) {
            throw new IllegalArgumentException("Expected: output-csv Radixor-Java-version source-revision "
                    + "source-state generator-source [seed|all [language-name [cost-label]]]");
        }
        final Path output = Path.of(arguments[0]);
        final ExperimentProvenance provenance = new ExperimentProvenance(requireText(arguments[1],
                "Radixor Java version"), requireText(arguments[2], "source revision"),
                requireText(arguments[3], "source state"), sha256(Files.readAllBytes(Path.of(arguments[4]))));
        final long[] seeds = arguments.length >= 6 && !"all".equalsIgnoreCase(arguments[5])
                ? new long[] { Long.parseLong(arguments[5]) }
                : DictionaryGeneralizationApplication.SEEDS.clone();
        final String languageFilter = arguments.length >= 7
                ? arguments[6].toUpperCase(Locale.ROOT) : null;
        final String costFilter = arguments.length == MAXIMUM_ARGUMENT_COUNT
                ? arguments[7].toUpperCase(Locale.ROOT) : null;
        return new RunOptions(output, provenance, seeds, languageFilter, costFilter);
    }

    /** Writes the report through a temporary file and publishes it atomically when supported. */
    private static void writeReport(final RunOptions options) throws IOException {
        final Path output = options.output().toAbsolutePath();
        final Path outputDirectory = output.getParent();
        Files.createDirectories(outputDirectory);
        final Path temporaryOutput = output.resolveSibling(output.getFileName().toString() + ".partial");
        Files.deleteIfExists(temporaryOutput);
        boolean published = false;
        try {
            final long dataRows = writeReportContent(temporaryOutput, options);
            publishReport(temporaryOutput, output);
            published = true;
            System.out.printf(Locale.ROOT, "Cost-sensitivity CSV: %s (%,d data rows)%n", output, dataRows);
        } finally {
            if (!published) {
                System.err.println("Incomplete cost-sensitivity data retained at " + temporaryOutput);
            }
        }
    }

    /** Executes all requested experiment scenarios into an already resolved temporary path. */
    private static long writeReportContent(final Path temporaryOutput, final RunOptions options) throws IOException {
        final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
        final List<LanguageWork> languages = orderLanguagesByBaselineCommandCount(
                selectLanguages(options.languageFilter()), registry);
        final List<CostConfig> grid = selectConfigurations(options.costFilter());
        final long[] seeds = options.seeds();
        System.out.println("Dictionary execution order by full-dictionary baseline patch-command count:");
        for (final LanguageWork language : languages) {
            System.out.printf(Locale.ROOT, "  %s: %,d%n", language.language(), language.baselineCommandCount());
        }
        long dataRows = 0L;
        try (BufferedWriter writer = Files.newBufferedWriter(temporaryOutput, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (final LanguageWork languageWork : languages) {
                final StemmerPatchTrieLoader.Language language = languageWork.language();
                final StemmerModelDescriptor descriptor = languageWork.descriptor();
                final List<DictionaryRow> rows = readRows(descriptor);
                final List<GoldStandardGroup> goldGroups = toGoldGroups(rows);
                verifyProductionEquivalence(language, rows);
                final List<EquivalentCostClass> costClasses = groupEquivalentConfigurations(rows, grid);

                System.out.printf(Locale.ROOT, "Language %s (%s): %,d rows, %,d baseline patch commands, "
                        + "%d seeds, %d normalized configs in %d exact equivalence classes%n",
                        language, descriptor.id(), rows.size(), languageWork.baselineCommandCount(), seeds.length,
                        grid.size(), costClasses.size());

                for (final long seed : seeds) {
                    final List<DictionaryRow> ranked = rankRows(rows, descriptor.id(), seed);
                    final List<ScenarioContext> scenarios = createScenarioContexts(ranked, goldGroups);
                    int configIndex = 0;
                    for (final EquivalentCostClass costClass : costClasses) {
                        final CostConfig config = costClass.representative();
                        configIndex++;
                        for (final ScenarioContext scenario : scenarios) {
                            final String row = evaluateScenario(language, descriptor, ranked,
                                    config, costClass.labels(), seed, scenario, options.provenance());
                            writeCsvRow(writer, row);
                            dataRows++;
                        }
                        if (configIndex % 25 == 0) {
                            System.out.printf(Locale.ROOT, "  %s seed %s: %d/%d equivalence classes processed%n",
                                    language, Long.toUnsignedString(seed), configIndex, costClasses.size());
                        }
                    }
                }
                System.out.printf(Locale.ROOT, "  Completed %s: %d equivalence classes%n",
                        language, costClasses.size());
                writer.flush();
            }
        }
        return dataRows;
    }

    /** Resolves an optional exact language filter. */
    private static List<StemmerPatchTrieLoader.Language> selectLanguages(final String languageFilter) {
        final List<StemmerPatchTrieLoader.Language> languages = new ArrayList<>();
        for (final StemmerPatchTrieLoader.Language candidate : StemmerPatchTrieLoader.Language.values()) {
            if (languageFilter == null || "ALL".equals(languageFilter) || candidate.name().equals(languageFilter)) {
                languages.add(candidate);
            }
        }
        if (languages.isEmpty()) {
            throw new IllegalArgumentException("No language matches filter: " + languageFilter);
        }
        return List.copyOf(languages);
    }

    /**
     * Measures production-baseline command vocabularies and orders dictionaries
     * from least to most expensive before any grid scenario is evaluated.
     */
    /* default */ static List<LanguageWork> orderLanguagesByBaselineCommandCount(
            final List<StemmerPatchTrieLoader.Language> languages, final StemmerModelRegistry registry)
            throws IOException {
        final List<LanguageWork> ordered = new ArrayList<>(languages.size());
        for (final StemmerPatchTrieLoader.Language language : languages) {
            final StemmerModelDescriptor descriptor = registry.requireDefault(language);
            final List<DictionaryRow> rows = readRows(descriptor);
            ordered.add(new LanguageWork(language, descriptor, rows.size(),
                    countDistinctPatchCommands(rows, BASELINE)));
        }
        ordered.sort(Comparator.comparingLong(LanguageWork::baselineCommandCount)
                .thenComparing(work -> work.language().name()));
        return List.copyOf(ordered);
    }

    /** Writes one validated CSV data row. */
    private static void writeCsvRow(final BufferedWriter writer, final String row) throws IOException {
        final int actualFieldCount = fieldCount(row);
        if (actualFieldCount != HEADER_FIELD_COUNT) {
            throw new IllegalStateException("CSV row contains " + actualFieldCount + " fields; expected "
                    + HEADER_FIELD_COUNT + ".");
        }
        writer.write(row);
        writer.newLine();
    }

    /** Counts fields in an unquoted protocol CSV row. */
    private static int fieldCount(final String row) {
        int fields = 1;
        for (int index = 0; index < row.length(); index++) {
            if (row.charAt(index) == CSV_DELIMITER) {
                fields++;
            }
        }
        return fields;
    }

    /** Replaces the destination atomically when the filesystem supports it. */
    private static void publishReport(final Path temporaryOutput, final Path output) throws IOException {
        try {
            Files.move(temporaryOutput, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException exception) {
            Files.move(temporaryOutput, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Generates all cost-configuration combinations for the grid search.
     *
     * @return immutable list of all combinations from {@link #EDIT_COST_VALUES}
     *         and {@link #MATCH_COST_VALUES}
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    /* default */ static List<CostConfig> generateGrid() {
        final List<CostConfig> grid = new ArrayList<>(
                EDIT_COST_VALUES.size() * EDIT_COST_VALUES.size()
                        * EDIT_COST_VALUES.size() * MATCH_COST_VALUES.size());
        final Set<String> normalizedRatios = new HashSet<>();
        for (final int deleteCost : EDIT_COST_VALUES) {
            for (final int insertCost : EDIT_COST_VALUES) {
                for (final int replaceCost : EDIT_COST_VALUES) {
                    for (final int matchCost : MATCH_COST_VALUES) {
                        final CostConfig config = new CostConfig(deleteCost, insertCost, replaceCost, matchCost);
                        if (normalizedRatios.add(config.normalizedRatioKey())) {
                            grid.add(config);
                        }
                    }
                }
            }
        }
        return List.copyOf(grid);
    }

    /** Resolves an optional exact configuration label for pilot and shard runs. */
    /* default */ static List<CostConfig> selectConfigurations(final String costFilter) {
        final List<CostConfig> grid = generateGrid();
        if (costFilter == null) {
            return grid;
        }
        for (final CostConfig config : grid) {
            if (config.label().equals(costFilter)) {
                return List.of(config);
            }
        }
        throw new IllegalArgumentException("No normalized cost configuration matches filter: " + costFilter);
    }

    /**
     * Groups configurations that generate exactly the same command for every
     * full-dictionary input pair. Such configurations necessarily build the same
     * trie for every nested training subset used by this protocol.
     */
    /* default */ static List<EquivalentCostClass> groupEquivalentConfigurations(
            final List<DictionaryRow> rows, final List<CostConfig> configurations) {
        final Map<String, List<CostConfig>> byFingerprint = new LinkedHashMap<>();
        for (final CostConfig configuration : configurations) {
            byFingerprint.computeIfAbsent(commandFingerprint(rows, configuration), ignored -> new ArrayList<>())
                    .add(configuration);
        }
        final List<EquivalentCostClass> result = new ArrayList<>(byFingerprint.size());
        for (final List<CostConfig> fingerprintBucket : byFingerprint.values()) {
            final List<List<CostConfig>> exactGroups = new ArrayList<>();
            for (final CostConfig candidate : fingerprintBucket) {
                List<CostConfig> matchingGroup = null;
                for (final List<CostConfig> group : exactGroups) {
                    if (commandsEquivalent(rows, group.get(0), candidate)) {
                        matchingGroup = group;
                        break;
                    }
                }
                if (matchingGroup == null) {
                    matchingGroup = new ArrayList<>();
                    exactGroups.add(matchingGroup);
                }
                matchingGroup.add(candidate);
            }
            for (final List<CostConfig> members : exactGroups) {
                CostConfig representative = members.get(0);
                if (members.contains(BASELINE)) {
                    representative = BASELINE;
                }
                final List<String> labels = members.stream().map(CostConfig::label).sorted().toList();
                result.add(new EquivalentCostClass(representative, labels));
            }
        }
        return List.copyOf(result);
    }

    /** Verifies command-by-command equality after fingerprint bucketing. */
    private static boolean commandsEquivalent(final List<DictionaryRow> rows,
            final CostConfig leftConfiguration, final CostConfig rightConfiguration) {
        final PatchCommandEncoder left = leftConfiguration.buildEncoder();
        final PatchCommandEncoder right = rightConfiguration.buildEncoder();
        for (final DictionaryRow row : rows) {
            if (!left.encode(row.stem(), row.stem()).equals(right.encode(row.stem(), row.stem()))) {
                return false;
            }
            for (final String variant : row.variants()) {
                if (!variant.equals(row.stem())
                        && !left.encode(variant, row.stem()).equals(right.encode(variant, row.stem()))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Computes a length-delimited SHA-256 fingerprint of all generated commands. */
    private static String commandFingerprint(final List<DictionaryRow> rows, final CostConfig configuration) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
        final PatchCommandEncoder encoder = configuration.buildEncoder();
        for (final DictionaryRow row : rows) {
            updateCommandFingerprint(digest, encoder.encode(row.stem(), row.stem()));
            for (final String variant : row.variants()) {
                if (!variant.equals(row.stem())) {
                    updateCommandFingerprint(digest, encoder.encode(variant, row.stem()));
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Adds one unambiguously length-delimited UTF-8 command to a fingerprint. */
    private static void updateCommandFingerprint(final MessageDigest digest, final String command) {
        final byte[] bytes = command.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    /**
     * Counts distinct patch command strings generated for all word-stem pairs in
     * the supplied dictionary rows using the specified cost configuration.
     *
     * @param rows   training dictionary rows for one knowledge level
     * @param config cost configuration
     * @return number of distinct patch command strings
     */
    /* default */ static long countDistinctPatchCommands(final List<DictionaryRow> rows, final CostConfig config) {
        final PatchCommandEncoder encoder = config.buildEncoder();
        final Set<String> distinct = new HashSet<>();
        for (final DictionaryRow row : rows) {
            distinct.add(encoder.encode(row.stem(), row.stem()));
            for (final String variant : row.variants()) {
                distinct.add(encoder.encode(variant, row.stem()));
            }
        }
        return distinct.size();
    }

    /**
     * Prepares all nested knowledge-level scenarios for one deterministic split.
     * Baseline command counts are derived from the exact rows used to build each
     * partial trie.
     *
     * @param ranked     deterministically ranked dictionary rows
     * @param goldGroups gold groups for the complete dictionary
     * @return immutable scenarios ordered from 10 to 100 percent knowledge
     */
    /* default */ static List<ScenarioContext> createScenarioContexts(final List<DictionaryRow> ranked,
            final List<GoldStandardGroup> goldGroups) {
        final List<ScenarioContext> scenarios = new ArrayList<>(10);
        for (int percent = 10; percent <= 100; percent += 10) {
            scenarios.add(createScenarioContext(ranked, goldGroups, percent));
        }
        return List.copyOf(scenarios);
    }

    /** Creates one immutable knowledge-level scenario and its baseline measurements. */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static ScenarioContext createScenarioContext(final List<DictionaryRow> ranked,
            final List<GoldStandardGroup> goldGroups, final int percent) {
        final int selectedCount = percent == 100 ? ranked.size()
                : Math.max(1, (ranked.size() * percent + 50) / 100);
        final List<DictionaryRow> selectedRows = List.copyOf(ranked.subList(0, selectedCount));
        final Set<Integer> selectedLineNumbers = new HashSet<>(selectedCount * 2);
        final Set<String> trainingForms = new HashSet<>();
        for (final DictionaryRow row : selectedRows) {
            selectedLineNumbers.add(row.lineNumber());
            trainingForms.add(row.stem());
            for (final String variant : row.variants()) {
                trainingForms.add(variant);
            }
        }

        final List<GoldStandardGroup> withheldGroups = new ArrayList<>(ranked.size() - selectedCount);
        final List<GoldStandardGroup> unseenGroups = new ArrayList<>(ranked.size() - selectedCount);
        long excludedOverlapOccurrences = 0L;
        for (final DictionaryRow row : ranked) {
            if (selectedLineNumbers.contains(row.lineNumber())) {
                continue;
            }
            final List<String> forms = row.forms();
            withheldGroups.add(new GoldStandardGroup(row.lineNumber(), forms));
            final List<String> unseenForms = new ArrayList<>(forms.size());
            for (final String form : forms) {
                if (trainingForms.contains(form)) {
                    excludedOverlapOccurrences++;
                } else {
                    unseenForms.add(form);
                }
            }
            if (!unseenForms.isEmpty()) {
                unseenGroups.add(new GoldStandardGroup(row.lineNumber(), unseenForms));
            }
        }

        final long baselineGeneratedCount = countDistinctPatchCommands(selectedRows, BASELINE);
        final FrequencyTrie<CompiledPatchCommand> baselineTrie = buildCompiledTrie(selectedRows, BASELINE);
        final long baselineTrieCommandCount = FrequencyTrieBuilders.computeStatistics(baselineTrie)
                .distinctValueCount();
        return new ScenarioContext(percent, selectedRows, Set.copyOf(selectedLineNumbers), Set.copyOf(trainingForms),
                goldGroups, List.copyOf(withheldGroups), List.copyOf(unseenGroups), excludedOverlapOccurrences,
                baselineGeneratedCount, baselineTrieCommandCount);
    }

    /**
     * Evaluates one cost-configuration and training-knowledge scenario.
     *
     * @param language        bundled language
     * @param descriptor      model descriptor
     * @param ranked          deterministically ranked dictionary rows
     * @param config          cost configuration under evaluation
     * @param seed            deterministic split seed
     * @param scenario        prepared knowledge-level scenario
     * @param provenance      immutable build and source provenance
     * @return one CSV data row without a trailing line separator
     */
    private static String evaluateScenario(
            final StemmerPatchTrieLoader.Language language,
            final StemmerModelDescriptor descriptor,
            final List<DictionaryRow> ranked,
            final CostConfig config,
            final List<String> equivalentCostLabels,
            final long seed,
            final ScenarioContext scenario,
            final ExperimentProvenance provenance) {

        final FrequencyTrie<CompiledPatchCommand> trie = buildCompiledTrie(scenario.selectedRows(), config);
        final TrieStatistics trieStats = FrequencyTrieBuilders.computeStatistics(trie);
        final long generatedCommandCount = countDistinctPatchCommands(scenario.selectedRows(), config);
        final double ratio = scenario.baselineTrieCommandCount() == 0L ? 1.0d
                : trieStats.distinctValueCount() / (double) scenario.baselineTrieCommandCount();
        final Viability viability = classifyViability(ratio);

        Counts whole = Counts.empty();
        Counts withheld = Counts.empty();
        Counts unseen = Counts.empty();

        for (final DictionaryRow row : ranked) {
            final boolean selected = scenario.selectedLineNumbers().contains(row.lineNumber());
            final List<String> forms = row.forms();
            for (final String form : forms) {
                final boolean correct = Objects.equals(row.stem(), stem(trie, form));
                whole = whole.add(form, row.stem(), correct);
                if (!selected) {
                    withheld = withheld.add(form, row.stem(), correct);
                    if (!scenario.trainingForms().contains(form)) {
                        unseen = unseen.add(form, row.stem(), correct);
                    }
                }
            }
        }

        final QualityResult wholePairwise = computePairwiseMetrics(language, config, scenario.wholeGroups(), trie);
        final QualityResult withheldPairwise = computePairwiseMetrics(language, config, scenario.withheldGroups(), trie);
        final QualityResult unseenPairwise = computePairwiseMetrics(language, config, scenario.unseenGroups(), trie);

        final StringBuilder sb = new StringBuilder(1024);
        appendIdentity(sb, language, descriptor, config, equivalentCostLabels, seed, provenance);
        sb.append(',').append(scenario.percent())
                .append(',').append(scenario.selectedRows().size())
                .append(',').append(ranked.size())
                .append(',').append(ranked.size() - scenario.selectedRows().size())
                .append(',').append(scenario.excludedOverlapOccurrences())
                .append(',').append(generatedCommandCount)
                .append(',').append(scenario.baselineGeneratedCount())
                .append(',').append(trieStats.distinctValueCount())
                .append(',').append(scenario.baselineTrieCommandCount())
                .append(',').append(ratio)
                .append(',').append(viability.name())
                .append(',').append(whole.csv())
                .append(',').append(withheld.csv())
                .append(',').append(unseen.csv());
        appendTrieStats(sb, trieStats);
        appendPairwiseMetrics(sb, wholePairwise);
        appendPairwiseMetrics(sb, withheldPairwise);
        appendPairwiseMetrics(sb, unseenPairwise);
        return sb.toString();
    }

    /**
     * Appends experiment provenance, model identity, seed, and cost configuration.
     *
     * @param builder    destination CSV row builder
     * @param language   bundled language
     * @param descriptor descriptor of the evaluated model
     * @param config     evaluated cost configuration
     * @param equivalentCostLabels all normalized grid labels proven equivalent
     * @param seed       deterministic split seed
     * @param provenance source and generator provenance
     */
    private static void appendIdentity(final StringBuilder builder,
            final StemmerPatchTrieLoader.Language language,
            final StemmerModelDescriptor descriptor, final CostConfig config,
            final List<String> equivalentCostLabels,
            final long seed, final ExperimentProvenance provenance) {
        builder.append(PROTOCOL_VERSION)
                .append(",MEASUREMENT")
                .append(',').append(provenance.radixorJavaVersion())
                .append(',').append(provenance.sourceRevision())
                .append(',').append(provenance.sourceState())
                .append(',').append(provenance.generatorSha256())
                .append(',').append(language.name())
                .append(',').append(descriptor.id())
                .append(',').append(descriptor.version())
                .append(',').append(descriptor.sha256())
                .append(',').append(Long.toUnsignedString(seed))
                .append(',').append(config.deleteCost())
                .append(',').append(config.insertCost())
                .append(',').append(config.replaceCost())
                .append(',').append(config.matchCost())
                .append(',').append(config.label())
                .append(',').append(String.join(";", equivalentCostLabels));
    }

    /**
     * Appends trie statistics columns.
     *
     * @param builder destination CSV row builder
     * @param stats   structural statistics to append
     */
    private static void appendTrieStats(final StringBuilder builder, final TrieStatistics stats) {
        builder.append(',').append(stats.internalNodeCount())
                .append(',').append(stats.leafNodeCount())
                .append(',').append(stats.edgeCount())
                .append(',').append(stats.acceptingLeafNodeCount())
                .append(',').append(stats.valueReferenceCount())
                .append(',').append(stats.logicalLeafPathCount())
                .append(',').append(stats.longestPath())
                .append(',').append(stats.averageLeafDepth())
                .append(',').append(stats.denseLookupNodeCount())
                .append(',').append(stats.denseTableSlotCount());
    }

    /**
     * Appends pairwise confusion and quality metric columns.
     *
     * @param builder  destination CSV row builder
     * @param pairwise evaluated pairwise quality result
     */
    private static void appendPairwiseMetrics(final StringBuilder builder, final QualityResult pairwise) {
        final PairwiseMetrics pm = pairwise.pairwiseMetrics();
        builder.append(',').append(pm.truePositivePairs())
                .append(',').append(pm.falsePositivePairs())
                .append(',').append(pm.falseNegativePairs())
                .append(',').append(pm.trueNegativePairs())
                .append(',').append(pairwise.overErrorPairs())
                .append(',').append(pairwise.overPossiblePairs())
                .append(',').append(formatOptional(pairwise.overPercentage()))
                .append(',').append(pairwise.underErrorPairs())
                .append(',').append(pairwise.underPossiblePairs())
                .append(',').append(formatOptional(pairwise.underPercentage()))
                .append(',').append(formatOptional(pm.precision()))
                .append(',').append(formatOptional(pm.recall()))
                .append(',').append(formatOptional(pm.specificity()))
                .append(',').append(formatOptional(pm.balancedAccuracy()))
                .append(',').append(formatOptional(pm.f05()))
                .append(',').append(formatOptional(pm.f1()))
                .append(',').append(formatOptional(pm.f2()))
                .append(',').append(formatOptional(pm.jaccard()))
                .append(',').append(formatOptional(pm.fowlkesMallows()))
                .append(',').append(formatOptional(pm.matthewsCorrelationCoefficient()))
                .append(',').append(formatOptional(pm.errorRate()));
    }

    /**
     * Formats an optional metric value for CSV output.
     *
     * @param value optional value
     * @return unrounded decimal text, or an empty string when the metric is
     *         undefined
     */
    private static String formatOptional(final OptionalDouble value) {
        return value.isEmpty() ? "" : Double.toString(value.getAsDouble());
    }

    /**
     * Builds a compiled trie from the supplied rows using the given cost config.
     *
     * @param rows   selected training rows
     * @param config cost configuration
     * @return immutable compiled trie containing pre-compiled patch commands
     */
    /* default */ static FrequencyTrie<CompiledPatchCommand> buildCompiledTrie(final List<DictionaryRow> rows,
            final CostConfig config) {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new,
                REDUCTION_SETTINGS, DIRECTION);
        final PatchCommandEncoder encoder = config.buildEncoder();
        for (final DictionaryRow row : rows) {
            builder.put(row.stem(), encoder.encode(row.stem(), row.stem()));
            for (final String variant : row.variants()) {
                if (!variant.equals(row.stem())) {
                    builder.put(variant, encoder.encode(variant, row.stem()));
                }
            }
        }
        final FrequencyTrie<String> trie = builder.build();
        final Map<String, CompiledPatchCommand> compiled = new HashMap<>(4096);
        return FrequencyTrieBuilders.mapValues(trie, CompiledPatchCommand[]::new,
                trie.metadata().reductionSettings(), patch -> compiled.computeIfAbsent(patch,
                        value -> CompiledPatchCommand.compile(value, trie.traversalDirection())));
    }

    /**
     * Computes pairwise stemming-quality metrics for one explicitly supplied
     * evaluation scope.
     *
     * @param language   bundled language (used as label)
     * @param config     cost configuration (used as stemmer name)
     * @param goldGroups gold-standard groups in the requested evaluation scope
     * @param trie       compiled trie under evaluation
     * @return pairwise quality result
     */
    private static QualityResult computePairwiseMetrics(
            final StemmerPatchTrieLoader.Language language,
            final CostConfig config,
            final List<GoldStandardGroup> goldGroups,
            final FrequencyTrie<CompiledPatchCommand> trie) {
        return QualityEvaluator.evaluate(config.label(), language.name(), ProcessingMode.ALL_WORDS,
                goldGroups, form -> stem(trie, form));
    }

    /**
     * Applies the compiled trie to produce a stem for the supplied token.
     *
     * @param trie  compiled trie
     * @param token input word form
     * @return stemmed form, or the original token when no patch is stored
     */
    /* default */ static String stem(final FrequencyTrie<CompiledPatchCommand> trie, final String token) {
        final CompiledPatchCommand patch = trie.getNormalizedString(token);
        return patch == null || patch.preservesAllSources() ? token : patch.apply(token);
    }

    /**
     * Converts all dictionary rows to gold-standard groups for pairwise evaluation.
     *
     * @param rows all dictionary rows
     * @return immutable list of gold-standard groups in row order
     */
    /* default */ static List<GoldStandardGroup> toGoldGroups(final List<DictionaryRow> rows) {
        final List<GoldStandardGroup> groups = new ArrayList<>(rows.size());
        for (final DictionaryRow row : rows) {
            groups.add(new GoldStandardGroup(row.lineNumber(), row.forms()));
        }
        return List.copyOf(groups);
    }

    /**
     * Verifies that the baseline full-knowledge experiment reproduces the
     * corresponding production model for every dictionary form.
     *
     * @param language bundled language
     * @param rows     complete parsed dictionary
     * @throws IOException if the production model cannot be loaded
     * @throws IllegalStateException if any experiment output differs from production
     */
    private static void verifyProductionEquivalence(final StemmerPatchTrieLoader.Language language,
            final List<DictionaryRow> rows) throws IOException {
        final FrequencyTrie<CompiledPatchCommand> experiment = buildCompiledTrie(rows, BASELINE);
        final FrequencyTrie<CompiledPatchCommand> production = StemmerPatchTrieLoader.loadCompiled(language, true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        for (final DictionaryRow row : rows) {
            verifyEquivalentStem(language, experiment, production, row.stem());
            for (final String variant : row.variants()) {
                verifyEquivalentStem(language, experiment, production, variant);
            }
        }
    }

    /** Verifies one baseline experiment output against the production trie. */
    private static void verifyEquivalentStem(final StemmerPatchTrieLoader.Language language,
            final FrequencyTrie<CompiledPatchCommand> experiment,
            final FrequencyTrie<CompiledPatchCommand> production, final String form) {
        final String experimentStem = stem(experiment, form);
        final String productionStem = stem(production, form);
        if (!Objects.equals(experimentStem, productionStem)) {
            throw new IllegalStateException("Full-coverage baseline differs from production for " + language
                    + " form '" + form + "': experiment produced '" + experimentStem
                    + "' but production produced '" + productionStem + "'.");
        }
    }

    /**
     * Sorts dictionary rows into a deterministic order using a seeded hash.
     *
     * @param rows    all dictionary rows
     * @param modelId non-null model identifier used in the hash mix
     * @param seed    deterministic seed
     * @return mutable list containing the same row instances in deterministic
     *         rank order
     */
    /* default */ static List<DictionaryRow> rankRows(final List<DictionaryRow> rows, final String modelId,
            final long seed) {
        final List<DictionaryRow> ranked = new ArrayList<>(rows);
        ranked.sort(Comparator.comparingLong((DictionaryRow row) -> rank(row, modelId, seed))
                .thenComparingInt(DictionaryRow::lineNumber));
        return ranked;
    }

    /**
     * Calculates the deterministic rank key for one dictionary row.
     *
     * @param row     dictionary row to rank
     * @param modelId model identifier included in the hash
     * @param seed    experiment seed mixed into the initial state
     * @return signed 64-bit rank key
     */
    private static long rank(final DictionaryRow row, final String modelId, final long seed) {
        long hash = 0xcbf29ce484222325L ^ seed;
        hash = mix(hash, PROTOCOL_VERSION);
        hash = mix(hash, modelId);
        hash = mix(hash, row.stem());
        for (final String variant : row.variants()) {
            hash = mix(hash, variant);
        }
        return hash;
    }

    /**
     * Mixes one UTF-16 string into an FNV-1a-style hash state.
     *
     * @param hash  current hash state
     * @param value non-null value to mix
     * @return updated hash state, including a field separator
     */
    private static long mix(final long hash, final String value) {
        long result = hash;
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            result ^= character & 0xFFL;
            result *= 0x100000001b3L;
            result ^= character >>> 8;
            result *= 0x100000001b3L;
        }
        result ^= 0xFFL;
        result *= 0x100000001b3L;
        return result;
    }

    /** Calculates a lowercase SHA-256 digest for report provenance. */
    private static String sha256(final byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    /** Validates and normalizes one required provenance label. */
    private static String requireText(final String value, final String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        final String result = value.strip();
        if (result.indexOf(',') >= 0 || result.indexOf('\n') >= 0 || result.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(label + " must not contain CSV delimiters or line separators.");
        }
        return result;
    }

    /**
     * Classifies a patch-command-count ratio into a viability category.
     *
     * @param ratio ratio of candidate patch count to baseline patch count
     * @return viability classification
     */
    /* default */ static Viability classifyViability(final double ratio) {
        if (ratio > NOT_VIABLE_THRESHOLD) {
            return Viability.NOT_VIABLE;
        }
        if (ratio > MARGINAL_THRESHOLD) {
            return Viability.MARGINAL;
        }
        return Viability.VIABLE;
    }

    /**
     * Reads all dictionary rows for the supplied model descriptor.
     *
     * @param descriptor model descriptor
     * @return immutable list of dictionary rows in source order
     * @throws IOException when the bundled dictionary resource cannot be read
     * @throws IllegalStateException if the resource is absent, empty, or fails
     *                               checksum verification
     */
    /* default */ static List<DictionaryRow> readRows(final StemmerModelDescriptor descriptor) throws IOException {
        final byte[] compressed;
        try (InputStream resource = openDictionaryResource(descriptor)) {
            compressed = resource.readAllBytes();
        }
        DictionaryGeneralizationApplication.verifySha256(compressed, descriptor.sha256(), descriptor.resource());
        final List<DictionaryRow> rows = new ArrayList<>();
        try (ByteArrayInputStream input = new ByteArrayInputStream(compressed);
                GZIPInputStream gzip = new GZIPInputStream(input);
                InputStreamReader streamReader = new InputStreamReader(gzip, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader)) {
            StemmerDictionaryParser.parse(reader, descriptor.resource(),
                    (stem, variants, lineNumber) -> rows.add(new DictionaryRow(lineNumber, stem, variants)));
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("No dictionary rows were parsed from " + descriptor.resource() + '.');
        }
        return List.copyOf(rows);
    }

    /** Opens one dictionary resource through the experiment context class loader. */
    private static InputStream openDictionaryResource(final StemmerModelDescriptor descriptor) {
        final ClassLoader classLoader = Objects.requireNonNull(Thread.currentThread().getContextClassLoader(),
                "context class loader");
        final InputStream resource = classLoader.getResourceAsStream(descriptor.resource());
        if (resource == null) {
            throw new IllegalStateException("Missing bundled dictionary resource " + descriptor.resource() + '.');
        }
        return resource;
    }

    /**
     * Viability classification for one cost configuration.
     */
    /* default */ enum Viability {
        /** Patch command count is within acceptable range (&le;5&times; baseline). */
        VIABLE,
        /** Patch command count is between 5&times; and 10&times; baseline. */
        MARGINAL,
        /** Patch command count exceeds 10&times; baseline. */
        NOT_VIABLE
    }

    /**
     * Immutable provenance attached to every report row.
     *
     * @param radixorJavaVersion evaluated Radixor Java version
     * @param sourceRevision     exact source revision
     * @param sourceState        source state such as {@code clean} or {@code dirty}
     * @param generatorSha256    SHA-256 of this experiment application's source
     */
    private record ExperimentProvenance(String radixorJavaVersion, String sourceRevision,
            String sourceState, String generatorSha256) { }

    /**
     * Immutable validated command-line configuration.
     *
     * @param output destination CSV path
     * @param provenance source and generator provenance
     * @param seeds deterministic split seeds
     * @param languageFilter optional exact language name
     * @param costFilter optional exact normalized cost label
     */
    private record RunOptions(Path output, ExperimentProvenance provenance, long[] seeds,
            String languageFilter, String costFilter) {

        /** Validates and defensively snapshots command-line options. */
        RunOptions {
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(provenance, "provenance");
            seeds = Objects.requireNonNull(seeds, "seeds").clone();
            if (seeds.length == 0) {
                throw new IllegalArgumentException("At least one deterministic seed is required.");
            }
        }

        /** Returns a caller-owned copy of the configured seeds. */
        @Override
        public long[] seeds() {
            return this.seeds.clone();
        }
    }

    /**
     * Preflight identity and production-baseline command count for one dictionary.
     *
     * @param language bundled language
     * @param descriptor exact default-model descriptor
     * @param dictionaryRows number of source dictionary rows
     * @param baselineCommandCount distinct full-dictionary commands produced by
     *                             the production baseline edit costs
     */
    /* default */ record LanguageWork(StemmerPatchTrieLoader.Language language,
            StemmerModelDescriptor descriptor, int dictionaryRows, long baselineCommandCount) {

        /** Validates one preflight measurement. */
        LanguageWork {
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(descriptor, "descriptor");
            if (dictionaryRows <= 0 || baselineCommandCount <= 0L) {
                throw new IllegalArgumentException("Dictionary preflight counts must be positive.");
            }
        }
    }

    /**
     * One exact dictionary-specific equivalence class of normalized cost settings.
     *
     * @param representative configuration evaluated by the expensive stages
     * @param labels all normalized grid labels with the same generated-command sequence
     */
    /* default */ record EquivalentCostClass(CostConfig representative, List<String> labels) {

        /** Validates and snapshots one nonempty equivalence class. */
        EquivalentCostClass {
            Objects.requireNonNull(representative, "representative");
            labels = List.copyOf(labels);
            if (labels.isEmpty() || !labels.contains(representative.label())) {
                throw new IllegalArgumentException("An equivalence class must contain its representative.");
            }
        }
    }

    /**
     * Immutable data split and baseline measurements for one knowledge level.
     *
     * @param percent requested training knowledge percentage
     * @param selectedRows rows used to build the partial trie
     * @param selectedLineNumbers source line numbers represented in training
     * @param trainingForms normalized surface forms observed in training
     * @param wholeGroups complete dictionary gold-standard groups
     * @param withheldGroups gold-standard groups whose rows were withheld
     * @param unseenGroups withheld groups after removing training-surface overlaps
     * @param excludedOverlapOccurrences withheld occurrences excluded from unseen scope
     * @param baselineGeneratedCount distinct commands generated from the selected
     *                               rows by baseline costs
     * @param baselineTrieCommandCount distinct commands physically retained by
     *                                 the baseline partial trie
     */
    /* default */ record ScenarioContext(int percent, List<DictionaryRow> selectedRows,
            Set<Integer> selectedLineNumbers, Set<String> trainingForms,
            List<GoldStandardGroup> wholeGroups, List<GoldStandardGroup> withheldGroups,
            List<GoldStandardGroup> unseenGroups, long excludedOverlapOccurrences,
            long baselineGeneratedCount, long baselineTrieCommandCount) {

        /** Validates and defensively snapshots one prepared scenario. */
        ScenarioContext {
            if (percent < 10 || percent > 100 || percent % 10 != 0) {
                throw new IllegalArgumentException("percent must be a multiple of ten from 10 through 100.");
            }
            selectedRows = List.copyOf(selectedRows);
            selectedLineNumbers = Set.copyOf(selectedLineNumbers);
            trainingForms = Set.copyOf(trainingForms);
            wholeGroups = List.copyOf(wholeGroups);
            withheldGroups = List.copyOf(withheldGroups);
            unseenGroups = List.copyOf(unseenGroups);
            if (excludedOverlapOccurrences < 0L || baselineGeneratedCount < 0L
                    || baselineTrieCommandCount < 0L) {
                throw new IllegalArgumentException("Scenario counts must not be negative.");
            }
        }
    }

    /**
     * Immutable edit-cost configuration for the patch-command encoder.
     *
     * @param deleteCost  cost of one delete operation
     * @param insertCost  cost of one insert operation
     * @param replaceCost cost of one replace operation
     * @param matchCost   cost of one match (skip) operation
     */
    /* default */ record CostConfig(int deleteCost, int insertCost, int replaceCost, int matchCost) {

        /**
         * Creates a validated cost configuration.
         *
         * @param deleteCost  cost of one delete operation
         * @param insertCost  cost of one insert operation
         * @param replaceCost cost of one replace operation
         * @param matchCost   cost of one match operation
         * @throws IllegalArgumentException if any cost is negative
         */
        CostConfig {
            if (deleteCost < 0 || insertCost < 0 || replaceCost < 0 || matchCost < 0) {
                throw new IllegalArgumentException("Edit-operation costs must not be negative.");
            }
        }

        /**
         * Returns a short human-readable label.
         *
         * @return label string like {@code D1I1R1M0}
         */
        /* default */ String label() {
            return "D" + deleteCost + "I" + insertCost + "R" + replaceCost + "M" + matchCost;
        }

        /**
         * Returns a scale-normalized cost-ratio key. Multiplying all operation
         * costs by one positive integer produces the same key and cannot change
         * the encoder's minimum-cost decisions.
         *
         * @return colon-delimited normalized cost tuple
         */
        /* default */ String normalizedRatioKey() {
            final int divisor = Math.max(1, greatestCommonDivisor(
                    greatestCommonDivisor(deleteCost, insertCost),
                    greatestCommonDivisor(replaceCost, matchCost)));
            return deleteCost / divisor + ":" + insertCost / divisor + ":"
                    + replaceCost / divisor + ":" + matchCost / divisor;
        }

        /**
         * Builds a {@link PatchCommandEncoder} configured with these costs.
         *
         * @return configured encoder
         */
        /* default */ PatchCommandEncoder buildEncoder() {
            return PatchCommandEncoder.builder()
                    .traversalDirection(DIRECTION)
                    .deleteCost(deleteCost)
                    .insertCost(insertCost)
                    .replaceCost(replaceCost)
                    .matchCost(matchCost)
                    .build();
        }

        /** Calculates a non-negative greatest common divisor. */
        private static int greatestCommonDivisor(final int first, final int second) {
            int left = first;
            int right = second;
            while (right != 0) {
                final int remainder = left % right;
                left = right;
                right = remainder;
            }
            return left;
        }
    }

    /**
     * One parsed dictionary entry.
     *
     * @param lineNumber physical source line number
     * @param stem       canonical stem
     * @param variants   all surface forms that should map to this stem
     */
    /* default */ record DictionaryRow(int lineNumber, String stem, String[] variants) {

        DictionaryRow {
            Objects.requireNonNull(stem, "stem");
            variants = Objects.requireNonNull(variants, "variants").clone();
            for (int index = 0; index < variants.length; index++) {
                Objects.requireNonNull(variants[index], "variants[" + index + "]");
            }
        }

        /**
         * Returns a defensive copy of the surface forms.
         *
         * @return caller-owned array of non-null variants
         */
        @Override
        public String[] variants() {
            return this.variants.clone();
        }

        /**
         * Collects the stem followed by all surface forms.
         *
         * @return caller-owned list containing the stem and variants in source
         *         order
         */
        /* default */ List<String> forms() {
            final List<String> result = new ArrayList<>(this.variants.length + 1);
            result.add(this.stem);
            for (final String variant : this.variants) {
                result.add(variant);
            }
            return result;
        }
    }

    /**
     * Aggregated correctness counters for one evaluation scope.
     *
     * @param correct        exact-match correct count
     * @param total          total evaluated form count
     * @param changedCorrect exact-match correct count for forms that differ from
     *                       their stem
     * @param changedTotal   total count of forms that differ from their stem
     * @param rootCorrect    exact-match correct count for forms that equal their
     *                       stem (root/stem forms themselves)
     * @param rootTotal      total count of forms that equal their stem
     */
    /* default */ record Counts(long correct, long total, long changedCorrect, long changedTotal,
            long rootCorrect, long rootTotal) {

        /**
         * Creates zero-initialized counters.
         *
         * @return empty counter value
         */
        /* default */ static Counts empty() {
            return new Counts(0L, 0L, 0L, 0L, 0L, 0L);
        }

        /**
         * Returns counters including one additional evaluated token.
         *
         * @param token evaluated surface form
         * @param stem  expected stem
         * @param exact whether the produced stem exactly matched {@code stem}
         * @return new counters including the supplied observation
         */
        /* default */ Counts add(final String token, final String stem, final boolean exact) {
            final boolean rootForm = Objects.equals(token, stem);
            final boolean changed = !rootForm;
            return new Counts(
                    this.correct + (exact ? 1L : 0L),
                    this.total + 1L,
                    this.changedCorrect + (changed && exact ? 1L : 0L),
                    this.changedTotal + (changed ? 1L : 0L),
                    this.rootCorrect + (rootForm && exact ? 1L : 0L),
                    this.rootTotal + (rootForm ? 1L : 0L));
        }

        /**
         * Serializes the six counters in report-column order.
         *
         * @return comma-separated counter values without a trailing separator
         */
        /* default */ String csv() {
            return this.correct + "," + this.total + ","
                    + this.changedCorrect + "," + this.changedTotal + ","
                    + this.rootCorrect + "," + this.rootTotal;
        }
    }
}
