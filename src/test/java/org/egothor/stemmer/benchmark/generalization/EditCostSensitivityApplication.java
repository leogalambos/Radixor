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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
 * For every bundled language and every combination of
 * (delete, insert, replace, match) costs drawn from the configured value sets,
 * the application:
 * <ol>
 * <li>Counts the number of distinct patch commands generated from the full
 *     dictionary using the candidate cost configuration.</li>
 * <li>Compares the count against the baseline (1, 1, 1, 0) count to determine
 *     viability: configurations with &gt;10&times; baseline patch commands are
 *     {@code NOT_VIABLE}; configurations with &gt;5&times; are {@code MARGINAL}
 *     and currently not evaluated.</li>
 * <li>For viable configurations, evaluates generalization quality at each
 *     training-knowledge level (10 %, 20 %, …, 100 %) using the dictionary
 *     itself as the gold standard.</li>
 * <li>Computes trie structural statistics and pairwise stemming-quality metrics
 *     for each (cost configuration, knowledge level) combination.</li>
 * </ol>
 *
 * <p>
 * Output is a single UTF-8 CSV file whose rows cover all selected languages,
 * cost configurations, and knowledge levels. The application discovers model
 * descriptors through the context class loader, reads model resources from the
 * runtime class path, retains the report in memory while it runs, creates the
 * output parent directory when necessary, and replaces an existing output
 * file.</p>
 *
 * <p>The implementation has no mutable global state. Separate invocations may
 * run concurrently provided that they write to different output paths.</p>
 */
public final class EditCostSensitivityApplication {

    /** Cost values used for the delete, insert, and replace operations. */
    static final List<Integer> EDIT_COST_VALUES = List.of(1, 2, 3, 5, 10);

    /** Cost values used for the match (skip) operation. */
    static final List<Integer> MATCH_COST_VALUES = List.of(0, 1);

    /** Ratio threshold above which a configuration is flagged as {@code MARGINAL}. */
    static final double MARGINAL_THRESHOLD = 5.0d;

    /** Ratio threshold above which a configuration is flagged as {@code NOT_VIABLE}. */
    static final double NOT_VIABLE_THRESHOLD = 10.0d;

    /** Default deterministic seed. */
    static final long DEFAULT_SEED = DictionaryGeneralizationApplication.SEEDS[0];

    /** Traversal direction used by the production stemmer. */
    private static final WordTraversalDirection DIRECTION = WordTraversalDirection.BACKWARD;

    /** Reduction settings matching the production stemmer compilation. */
    private static final ReductionSettings REDUCTION_SETTINGS = new ReductionSettings(
            ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS,
            ReductionSettings.DEFAULT_DOMINANT_WINNER_MIN_PERCENT,
            ReductionSettings.DEFAULT_DOMINANT_WINNER_OVER_SECOND_RATIO, true);

    /** Baseline cost configuration. */
    static final CostConfig BASELINE = new CostConfig(1, 1, 1, 0);

    private static final String PROTOCOL_VERSION = "radixor-cost-sensitivity-v1";

    static final String HEADER = String.join(",",
            "protocol_version", "language", "model_id", "model_version",
            "delete_cost", "insert_cost", "replace_cost", "match_cost", "cost_label",
            "patch_command_count", "baseline_patch_command_count", "patch_count_ratio", "viability",
            "training_percent", "selected_rows", "total_rows", "withheld_rows", "excluded_overlap_occurrences",
            "whole_correct", "whole_total", "whole_changed_correct", "whole_changed_total",
            "whole_root_correct", "whole_root_total",
            "withheld_correct", "withheld_total", "withheld_changed_correct", "withheld_changed_total",
            "withheld_root_correct", "withheld_root_total",
            "unseen_correct", "unseen_total", "unseen_changed_correct", "unseen_changed_total",
            "unseen_root_correct", "unseen_root_total",
            "trie_internal_nodes", "trie_leaves", "trie_longest_path", "trie_avg_path_length",
            "tp", "fp", "fn", "tn",
            "over_error_pairs", "over_possible_pairs", "over_percent",
            "under_error_pairs", "under_possible_pairs", "under_percent",
            "precision", "recall", "specificity", "balanced_accuracy",
            "f05", "f1", "f2", "jaccard", "fowlkes_mallows", "mcc", "error_rate");

    /** Number of columns emitted by {@link #appendCommonPrefix}. */
    private static final int COMMON_PREFIX_FIELD_COUNT = 13;

    /** Total number of columns declared by {@link #HEADER}. */
    private static final int HEADER_FIELD_COUNT = HEADER.split(",", -1).length;

    /** Utility class. */
    private EditCostSensitivityApplication() {
        throw new AssertionError("No instances.");
    }

    /**
     * Generates and writes the complete cost-sensitivity CSV report.
     *
     * @param arguments output CSV path, optionally a long seed, and optionally
     *                  an exact {@link StemmerPatchTrieLoader.Language} name
     * @throws IOException when a model dictionary cannot be read or the CSV cannot
     *                     be written
     * @throws IllegalArgumentException if the argument count, seed, or language
     *                                  name is invalid
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length < 1 || arguments.length > 3) {
            throw new IllegalArgumentException("Expected: output-csv [seed [language-name]]");
        }
        final Path output = Path.of(arguments[0]);
        final long seed = arguments.length >= 2 ? Long.parseLong(arguments[1]) : DEFAULT_SEED;
        final String languageFilter = arguments.length == 3 ? arguments[2].toUpperCase(Locale.ROOT) : null;

        final List<StemmerPatchTrieLoader.Language> languages = new ArrayList<>();
        for (final StemmerPatchTrieLoader.Language candidate : StemmerPatchTrieLoader.Language.values()) {
            if (languageFilter == null || candidate.name().equals(languageFilter)) {
                languages.add(candidate);
            }
        }
        if (languages.isEmpty()) {
            throw new IllegalArgumentException("No language matches filter: " + languageFilter);
        }

        final List<String> lines = new ArrayList<>();
        lines.add(HEADER);

        final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
        final List<CostConfig> grid = generateGrid();

        for (final StemmerPatchTrieLoader.Language language : languages) {
            final StemmerModelDescriptor descriptor = registry.requireDefault(language);
            final List<DictionaryRow> rows = readRows(descriptor);

            final long baselineCount = countDistinctPatchCommands(rows, BASELINE);

            System.out.printf(Locale.ROOT, "Language %s (%s): %,d rows, baseline patch commands: %,d%n",
                    language, descriptor.id(), rows.size(), baselineCount);

            final List<DictionaryRow> ranked = rankRows(rows, descriptor.id(), seed);
            final List<GoldStandardGroup> goldGroups = toGoldGroups(rows);

            int configIndex = 0;
            for (final CostConfig config : grid) {
                configIndex++;
                final long patchCount = countDistinctPatchCommands(rows, config);
                final double ratio = baselineCount == 0L ? 1.0d : (double) patchCount / (double) baselineCount;
                final Viability viability = classifyViability(ratio);

                if (viability == Viability.NOT_VIABLE || viability == Viability.MARGINAL) {
                    lines.add(buildSummaryRow(language, descriptor, config, patchCount,
                            baselineCount, ratio, viability));
                } else {
                    for (int percent = 10; percent <= 100; percent += 10) {
                        final String row = evaluateScenario(language, descriptor, ranked, goldGroups,
                                config, patchCount, baselineCount, ratio, viability, percent);
                        lines.add(row);
                    }
                }

                if (configIndex % 50 == 0) {
                    System.out.printf(Locale.ROOT, "  %s: %d/%d configs processed%n",
                            language, configIndex, grid.size());
                }
            }

            System.out.printf(Locale.ROOT, "  Completed %s: %d configs%n", language, grid.size());
        }

        final Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(output, lines, StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT, "Cost-sensitivity CSV: %s (%d data rows)%n",
                output.toAbsolutePath(), lines.size() - 1);
    }

    /**
     * Generates all cost-configuration combinations for the grid search.
     *
     * @return immutable list of all combinations from {@link #EDIT_COST_VALUES}
     *         and {@link #MATCH_COST_VALUES}
     */
    static List<CostConfig> generateGrid() {
        final List<CostConfig> grid = new ArrayList<>(
                EDIT_COST_VALUES.size() * EDIT_COST_VALUES.size()
                        * EDIT_COST_VALUES.size() * MATCH_COST_VALUES.size());
        for (final int deleteCost : EDIT_COST_VALUES) {
            for (final int insertCost : EDIT_COST_VALUES) {
                for (final int replaceCost : EDIT_COST_VALUES) {
                    for (final int matchCost : MATCH_COST_VALUES) {
                        grid.add(new CostConfig(deleteCost, insertCost, replaceCost, matchCost));
                    }
                }
            }
        }
        return List.copyOf(grid);
    }

    /**
     * Counts distinct patch command strings generated for all word-stem pairs in
     * the supplied dictionary rows using the specified cost configuration.
     *
     * @param rows   all dictionary rows
     * @param config cost configuration
     * @return number of distinct patch command strings
     */
    static long countDistinctPatchCommands(final List<DictionaryRow> rows, final CostConfig config) {
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
     * Evaluates one cost-configuration and training-knowledge scenario.
     *
     * @param language        bundled language
     * @param descriptor      model descriptor
     * @param ranked          deterministically ranked dictionary rows
     * @param goldGroups      gold groups derived from all dictionary rows
     * @param config          cost configuration under evaluation
     * @param patchCount      distinct patch command count for this configuration
     * @param baselineCount   baseline distinct patch command count
     * @param ratio           patch count ratio to baseline
     * @param viability       viability classification
     * @param percent         training knowledge percentage (10–100)
     * @return one CSV data row without a trailing line separator
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static String evaluateScenario(
            final StemmerPatchTrieLoader.Language language,
            final StemmerModelDescriptor descriptor,
            final List<DictionaryRow> ranked,
            final List<GoldStandardGroup> goldGroups,
            final CostConfig config,
            final long patchCount,
            final long baselineCount,
            final double ratio,
            final Viability viability,
            final int percent) {

        final int selectedCount = percent == 100 ? ranked.size()
                : Math.max(1, (ranked.size() * percent + 50) / 100);
        final List<DictionaryRow> selectedRows = ranked.subList(0, selectedCount);

        final Set<Integer> selectedLineNumbers = new HashSet<>(selectedCount * 2);
        final Set<String> trainingForms = new HashSet<>();
        for (final DictionaryRow row : selectedRows) {
            selectedLineNumbers.add(row.lineNumber());
            trainingForms.add(row.stem());
            for (final String variant : row.variants()) {
                trainingForms.add(variant);
            }
        }

        final FrequencyTrie<CompiledPatchCommand> trie = buildCompiledTrie(selectedRows, config);

        Counts whole = Counts.empty();
        Counts withheld = Counts.empty();
        Counts unseen = Counts.empty();
        long excluded = 0L;

        for (final DictionaryRow row : ranked) {
            final boolean selected = selectedLineNumbers.contains(row.lineNumber());
            final List<String> forms = row.forms();
            for (final String form : forms) {
                final boolean correct = Objects.equals(row.stem(), stem(trie, form));
                whole = whole.add(form, row.stem(), correct);
                if (!selected) {
                    withheld = withheld.add(form, row.stem(), correct);
                    if (trainingForms.contains(form)) {
                        excluded++;
                    } else {
                        unseen = unseen.add(form, row.stem(), correct);
                    }
                }
            }
        }

        final TrieStatistics trieStats = FrequencyTrieBuilders.computeStatistics(trie);

        final QualityResult pairwise = computePairwiseMetrics(language, config, goldGroups, trie);

        final StringBuilder sb = new StringBuilder(256);
        appendCommonPrefix(sb, language, descriptor, config, patchCount, baselineCount, ratio, viability);
        sb.append(',').append(percent);
        sb.append(',').append(selectedCount);
        sb.append(',').append(ranked.size());
        sb.append(',').append(ranked.size() - selectedCount);
        sb.append(',').append(excluded);
        sb.append(',').append(whole.csv());
        sb.append(',').append(withheld.csv());
        sb.append(',').append(unseen.csv());
        appendTrieStats(sb, trieStats);
        appendPairwiseMetrics(sb, pairwise);
        return sb.toString();
    }

    /**
     * Builds a summary-only row for a marginal or non-viable configuration.
     *
     * @param language      bundled language
     * @param descriptor    model descriptor
     * @param config        cost configuration
     * @param patchCount    distinct patch command count for this configuration
     * @param baselineCount baseline distinct patch command count
     * @param ratio         patch count ratio
     * @param viability     viability classification
     * @return CSV row string with empty evaluation fields
     */
    private static String buildSummaryRow(
            final StemmerPatchTrieLoader.Language language,
            final StemmerModelDescriptor descriptor,
            final CostConfig config,
            final long patchCount,
            final long baselineCount,
            final double ratio,
            final Viability viability) {
        final StringBuilder sb = new StringBuilder(128);
        appendCommonPrefix(sb, language, descriptor, config, patchCount, baselineCount, ratio, viability);
        // All per-scenario fields are empty for configurations that were not evaluated.
        final int emptyFields = HEADER_FIELD_COUNT - COMMON_PREFIX_FIELD_COUNT;
        for (int index = 0; index < emptyFields; index++) {
            sb.append(',');
        }
        return sb.toString();
    }

    /**
     * Appends the common prefix columns shared by all CSV rows.
     *
     * @param builder       destination CSV row builder
     * @param language      bundled language
     * @param descriptor    descriptor of the evaluated model
     * @param config        evaluated cost configuration
     * @param patchCount    distinct patch command count for the configuration
     * @param baselineCount distinct patch command count for the baseline
     * @param ratio         ratio of {@code patchCount} to {@code baselineCount}
     * @param viability     viability classification of the configuration
     */
    private static void appendCommonPrefix(final StringBuilder builder,
            final StemmerPatchTrieLoader.Language language,
            final StemmerModelDescriptor descriptor, final CostConfig config,
            final long patchCount, final long baselineCount, final double ratio, final Viability viability) {
        builder.append(PROTOCOL_VERSION);
        builder.append(',').append(language.name());
        builder.append(',').append(descriptor.id());
        builder.append(',').append(descriptor.version());
        builder.append(',').append(config.deleteCost());
        builder.append(',').append(config.insertCost());
        builder.append(',').append(config.replaceCost());
        builder.append(',').append(config.matchCost());
        builder.append(',').append(config.label());
        builder.append(',').append(patchCount);
        builder.append(',').append(baselineCount);
        builder.append(',').append(String.format(Locale.ROOT, "%.6f", ratio));
        builder.append(',').append(viability.name());
    }

    /**
     * Appends trie statistics columns.
     *
     * @param builder destination CSV row builder
     * @param stats   structural statistics to append
     */
    private static void appendTrieStats(final StringBuilder builder, final TrieStatistics stats) {
        builder.append(',').append(stats.internalNodeCount());
        builder.append(',').append(stats.leafNodeCount());
        builder.append(',').append(stats.longestPath());
        builder.append(',').append(String.format(Locale.ROOT, "%.6f", stats.averageLeafDepth()));
    }

    /**
     * Appends pairwise confusion and quality metric columns.
     *
     * @param builder  destination CSV row builder
     * @param pairwise evaluated pairwise quality result
     */
    @SuppressWarnings("PMD.LinguisticNaming")
    private static void appendPairwiseMetrics(final StringBuilder builder, final QualityResult pairwise) {
        final PairwiseMetrics pm = pairwise.pairwiseMetrics();
        builder.append(',').append(pm.truePositivePairs());
        builder.append(',').append(pm.falsePositivePairs());
        builder.append(',').append(pm.falseNegativePairs());
        builder.append(',').append(pm.trueNegativePairs());
        builder.append(',').append(pairwise.overErrorPairs());
        builder.append(',').append(pairwise.overPossiblePairs());
        builder.append(',').append(formatOptional(pairwise.overPercentage()));
        builder.append(',').append(pairwise.underErrorPairs());
        builder.append(',').append(pairwise.underPossiblePairs());
        builder.append(',').append(formatOptional(pairwise.underPercentage()));
        builder.append(',').append(formatOptional(pm.precision()));
        builder.append(',').append(formatOptional(pm.recall()));
        builder.append(',').append(formatOptional(pm.specificity()));
        builder.append(',').append(formatOptional(pm.balancedAccuracy()));
        builder.append(',').append(formatOptional(pm.f05()));
        builder.append(',').append(formatOptional(pm.f1()));
        builder.append(',').append(formatOptional(pm.f2()));
        builder.append(',').append(formatOptional(pm.jaccard()));
        builder.append(',').append(formatOptional(pm.fowlkesMallows()));
        builder.append(',').append(formatOptional(pm.matthewsCorrelationCoefficient()));
        builder.append(',').append(formatOptional(pm.errorRate()));
    }

    /**
     * Formats an optional metric value for CSV output.
     *
     * @param value optional value
     * @return decimal text with twelve fractional digits, or an empty string
     *         when the metric is undefined
     */
    private static String formatOptional(final OptionalDouble value) {
        return value.isEmpty() ? "" : String.format(Locale.ROOT, "%.12f", value.getAsDouble());
    }

    /**
     * Builds a compiled trie from the supplied rows using the given cost config.
     *
     * @param rows   selected training rows
     * @param config cost configuration
     * @return immutable compiled trie containing pre-compiled patch commands
     */
    static FrequencyTrie<CompiledPatchCommand> buildCompiledTrie(final List<DictionaryRow> rows,
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
     * Computes pairwise stemming-quality metrics using all gold groups and the
     * compiled trie as the stemmer under evaluation.
     *
     * @param language   bundled language (used as label)
     * @param config     cost configuration (used as stemmer name)
     * @param goldGroups gold-standard groups derived from the full dictionary
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
    static String stem(final FrequencyTrie<CompiledPatchCommand> trie, final String token) {
        final CompiledPatchCommand patch = trie.getNormalizedString(token);
        return patch == null || patch.preservesAllSources() ? token : patch.apply(token);
    }

    /**
     * Converts all dictionary rows to gold-standard groups for pairwise evaluation.
     *
     * @param rows all dictionary rows
     * @return immutable list of gold-standard groups in row order
     */
    static List<GoldStandardGroup> toGoldGroups(final List<DictionaryRow> rows) {
        final List<GoldStandardGroup> groups = new ArrayList<>(rows.size());
        for (final DictionaryRow row : rows) {
            groups.add(new GoldStandardGroup(row.lineNumber(), row.forms()));
        }
        return List.copyOf(groups);
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
    static List<DictionaryRow> rankRows(final List<DictionaryRow> rows, final String modelId, final long seed) {
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

    /**
     * Classifies a patch-command-count ratio into a viability category.
     *
     * @param ratio ratio of candidate patch count to baseline patch count
     * @return viability classification
     */
    static Viability classifyViability(final double ratio) {
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
    static List<DictionaryRow> readRows(final StemmerModelDescriptor descriptor) throws IOException {
        final InputStream resource = StemmerPatchTrieLoader.class.getClassLoader()
                .getResourceAsStream(descriptor.resource());
        if (resource == null) {
            throw new IllegalStateException("Missing bundled dictionary resource " + descriptor.resource() + '.');
        }
        final byte[] compressed;
        try (InputStream input = resource) {
            compressed = input.readAllBytes();
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

    /**
     * Viability classification for one cost configuration.
     */
    enum Viability {
        /** Patch command count is within acceptable range (&le;5&times; baseline). */
        VIABLE,
        /** Patch command count is between 5&times; and 10&times; baseline and is not evaluated. */
        MARGINAL,
        /** Patch command count exceeds 10&times; baseline — not viable. */
        NOT_VIABLE
    }

    /**
     * Immutable edit-cost configuration for the patch-command encoder.
     *
     * @param deleteCost  cost of one delete operation
     * @param insertCost  cost of one insert operation
     * @param replaceCost cost of one replace operation
     * @param matchCost   cost of one match (skip) operation
     */
    record CostConfig(int deleteCost, int insertCost, int replaceCost, int matchCost) {

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
        String label() {
            return "D" + deleteCost + "I" + insertCost + "R" + replaceCost + "M" + matchCost;
        }

        /**
         * Builds a {@link PatchCommandEncoder} configured with these costs.
         *
         * @return configured encoder
         */
        PatchCommandEncoder buildEncoder() {
            return PatchCommandEncoder.builder()
                    .traversalDirection(DIRECTION)
                    .deleteCost(deleteCost)
                    .insertCost(insertCost)
                    .replaceCost(replaceCost)
                    .matchCost(matchCost)
                    .build();
        }
    }

    /**
     * One parsed dictionary entry.
     *
     * @param lineNumber physical source line number
     * @param stem       canonical stem
     * @param variants   all surface forms that should map to this stem
     */
    record DictionaryRow(int lineNumber, String stem, String[] variants) {

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
        List<String> forms() {
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
    record Counts(long correct, long total, long changedCorrect, long changedTotal,
            long rootCorrect, long rootTotal) {

        /**
         * Creates zero-initialized counters.
         *
         * @return empty counter value
         */
        static Counts empty() {
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
        Counts add(final String token, final String stem, final boolean exact) {
            final boolean changed = !Objects.equals(token, stem);
            return new Counts(
                    this.correct + (exact ? 1L : 0L),
                    this.total + 1L,
                    this.changedCorrect + (changed && exact ? 1L : 0L),
                    this.changedTotal + (changed ? 1L : 0L),
                    this.rootCorrect + (!changed && exact ? 1L : 0L),
                    this.rootTotal + (!changed ? 1L : 0L));
        }

        /**
         * Serializes the six counters in report-column order.
         *
         * @return comma-separated counter values without a trailing separator
         */
        String csv() {
            return this.correct + "," + this.total + ","
                    + this.changedCorrect + "," + this.changedTotal + ","
                    + this.rootCorrect + "," + this.rootTotal;
        }
    }
}
