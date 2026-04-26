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
package org.egothor.stemmer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Evaluates how stemming quality degrades when the compiled trie is built from
 * only a deterministic subset of the available dictionary knowledge.
 *
 * <p>
 * The experiment operates on whole dictionary entries. For a chosen knowledge
 * percentage, each parsed dictionary line is deterministically included or
 * excluded from the training subset using a seeded {@link SplittableRandom}.
 * The resulting subset is compiled into a {@link FrequencyTrie}, while the
 * evaluation is performed against all word forms from the original dictionary.
 * </p>
 *
 * <p>
 * Two lookup APIs are evaluated:
 * </p>
 * <ul>
 * <li>{@link FrequencyTrie#get(String)} through top-1 accuracy</li>
 * <li>{@link FrequencyTrie#getAll(String)} through global precision, recall,
 * and F1</li>
 * </ul>
 */
public final class StemmerKnowledgeExperiment {

    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(StemmerKnowledgeExperiment.class.getName());

    /**
     * Minimum supported knowledge percentage.
     */
    public static final int MINIMUM_KNOWLEDGE_PERCENT = 10;

    /**
     * Maximum supported knowledge percentage.
     */
    public static final int MAXIMUM_KNOWLEDGE_PERCENT = 100;

    /**
     * Step between adjacent evaluated knowledge percentages.
     */
    public static final int KNOWLEDGE_PERCENT_STEP = 10;

    /**
     * Canonical no-op patch command.
     */
    private static final String NOOP_PATCH_COMMAND = PatchCommandEncoder.NOOP_PATCH;

    /**
     * Shared patch encoder reused for subset compilation.
     */
    private final PatchCommandEncoder patchCommandEncoder;

    /**
     * Creates a new experiment harness.
     */
    public StemmerKnowledgeExperiment() {
        this.patchCommandEncoder = PatchCommandEncoder.builder().build();
    }

    /**
     * Evaluates all supported bundled dictionaries using the supplied seed.
     *
     * @param seed deterministic sampling seed
     * @return immutable ordered list of experiment rows
     * @throws IOException if reading a bundled dictionary fails
     */
    public List<ResultRow> evaluateAllBundledLanguages(final long seed) throws IOException {
        final List<ResultRow> rows = new ArrayList<>();
        for (StemmerPatchTrieLoader.Language language : StemmerPatchTrieLoader.Language.values()) {
            rows.addAll(evaluateBundledLanguage(language, seed));
        }
        return List.copyOf(rows);
    }

    /**
     * Evaluates one bundled dictionary across all supported experiment
     * configurations.
     *
     * @param language bundled language dictionary
     * @param seed     deterministic sampling seed
     * @return immutable ordered list of experiment rows
     * @throws NullPointerException if {@code language} is {@code null}
     * @throws IOException          if reading the bundled dictionary fails
     */
    public List<ResultRow> evaluateBundledLanguage(final StemmerPatchTrieLoader.Language language, final long seed)
            throws IOException {
        Objects.requireNonNull(language, "language");
        final String resourcePath = language.resourcePath();
        try (InputStream inputStream = StemmerPatchTrieLoader.openBundledResource(resourcePath)) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return evaluate(reader, resourcePath, language.name(), seed);
            }
        }
    }

    /**
     * Evaluates one filesystem dictionary across all supported experiment
     * configurations.
     *
     * @param dictionaryPath path to a dictionary file
     * @param seed           deterministic sampling seed
     * @return immutable ordered list of experiment rows
     * @throws NullPointerException if {@code dictionaryPath} is {@code null}
     * @throws IOException          if reading fails
     */
    public List<ResultRow> evaluatePath(final Path dictionaryPath, final long seed) throws IOException {
        Objects.requireNonNull(dictionaryPath, "dictionaryPath");
        try (BufferedReader reader = Files.newBufferedReader(dictionaryPath, StandardCharsets.UTF_8)) {
            return evaluate(reader, dictionaryPath.toAbsolutePath().toString(), dictionaryPath.getFileName().toString(),
                    seed);
        }
    }

    /**
     * Evaluates a dictionary provided through an arbitrary reader.
     *
     * @param reader            source reader
     * @param sourceDescription logical source description
     * @param languageLabel     label stored in the result rows
     * @param seed              deterministic sampling seed
     * @return immutable ordered list of experiment rows
     * @throws NullPointerException if any argument except {@code seed} is
     *                              {@code null}
     * @throws IOException          if parsing fails
     */
    public List<ResultRow> evaluate(final Reader reader, final String sourceDescription, final String languageLabel,
            final long seed) throws IOException {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(sourceDescription, "sourceDescription");
        Objects.requireNonNull(languageLabel, "languageLabel");

        final DictionaryData dictionaryData = readDictionary(reader, sourceDescription);
        final List<ResultRow> rows = new ArrayList<>();

        for (ReductionMode reductionMode : ReductionMode.values()) {
            final ReductionSettings reductionSettings = ReductionSettings.withDefaults(reductionMode);
            for (boolean storeOriginal : new boolean[] { false, true }) { // NOPMD
                for (boolean includeStemInEvaluation : new boolean[] { false, true }) { // NOPMD
                    for (int knowledgePercent = MINIMUM_KNOWLEDGE_PERCENT; knowledgePercent <= MAXIMUM_KNOWLEDGE_PERCENT; knowledgePercent += KNOWLEDGE_PERCENT_STEP) {
                        final ResultRow row = evaluateScenario(dictionaryData, languageLabel, seed, reductionSettings,
                                storeOriginal, includeStemInEvaluation, knowledgePercent);
                        rows.add(row);
                    }
                }
            }
        }

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "Knowledge experiment finished for source {0}: entries={1}, rows={2}, seed={3}.",
                    new Object[] { sourceDescription, dictionaryData.entryCount(), rows.size(), seed });
        }

        return List.copyOf(rows);
    }

    /**
     * Writes result rows as UTF-8 CSV with a stable fixed header.
     *
     * @param outputPath target file path
     * @param rows       rows to write
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if writing fails
     */
    public static void writeCsv(final Path outputPath, final List<ResultRow> rows) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath");
        Objects.requireNonNull(rows, "rows");

        final Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add(ResultRow.csvHeader());
        for (ResultRow row : rows) {
            lines.add(row.toCsvRow());
        }
        Files.write(outputPath, lines, StandardCharsets.UTF_8);
    }

    /**
     * Parses the full dictionary into an in-memory representation suitable for
     * repeated deterministic subset compilation.
     *
     * @param reader            source reader
     * @param sourceDescription logical source description
     * @return parsed dictionary data
     * @throws IOException if parsing fails
     */
    private static DictionaryData readDictionary(final Reader reader, final String sourceDescription)
            throws IOException {
        final List<DictionaryEntry> entries = new ArrayList<>();
        final StemmerDictionaryParser.ParseStatistics parseStatistics = StemmerDictionaryParser.parse(reader,
                sourceDescription,
                (stem, variants, lineNumber) -> entries.add(new DictionaryEntry(stem, variants, lineNumber)));
        return new DictionaryData(sourceDescription, parseStatistics, entries);
    }

    /**
     * Evaluates one concrete experiment scenario.
     *
     * @param dictionaryData          parsed dictionary data
     * @param languageLabel           logical language label
     * @param seed                    deterministic sampling seed
     * @param reductionSettings       reduction settings
     * @param storeOriginal           whether canonical stems are inserted with a
     *                                no-op patch
     * @param includeStemInEvaluation whether the canonical stem itself is evaluated
     * @param knowledgePercent        retained percentage of dictionary entries
     * @return result row
     */
    private ResultRow evaluateScenario(final DictionaryData dictionaryData, final String languageLabel, final long seed,
            final ReductionSettings reductionSettings, final boolean storeOriginal,
            final boolean includeStemInEvaluation, final int knowledgePercent) {
        final FrequencyTrie<String> trie = compileSubset(dictionaryData, reductionSettings, storeOriginal,
                knowledgePercent, seed);

        long evaluatedInputCount = 0L;
        long getCorrectCount = 0L;
        long getAllTruePositiveCount = 0L;
        long getAllFalsePositiveCount = 0L;
        long getAllCoveredInputCount = 0L;
        long uniqueCandidateCount = 0L;

        for (DictionaryEntry entry : dictionaryData.entries()) {
            if (includeStemInEvaluation) {
                final EvaluationCounts stemCounts = evaluateInput(entry.stem(), entry.stem(), trie);
                evaluatedInputCount++;
                getCorrectCount += stemCounts.getCorrect();
                getAllTruePositiveCount += stemCounts.getAllTruePositives();
                getAllFalsePositiveCount += stemCounts.getAllFalsePositives();
                getAllCoveredInputCount += stemCounts.getAllCoveredInputs();
                uniqueCandidateCount += stemCounts.getUniqueCandidateCount();
            }
            for (String variant : entry.variants()) {
                final EvaluationCounts variantCounts = evaluateInput(variant, entry.stem(), trie);
                evaluatedInputCount++;
                getCorrectCount += variantCounts.getCorrect();
                getAllTruePositiveCount += variantCounts.getAllTruePositives();
                getAllFalsePositiveCount += variantCounts.getAllFalsePositives();
                getAllCoveredInputCount += variantCounts.getAllCoveredInputs();
                uniqueCandidateCount += variantCounts.getUniqueCandidateCount();
            }
        }

        final long trainingEntryCount = countSelectedEntries(dictionaryData.entryCount(), seed, knowledgePercent);
        final double getAccuracy = ratio(getCorrectCount, evaluatedInputCount);
        final double getAllPrecision = ratio(getAllTruePositiveCount,
                getAllTruePositiveCount + getAllFalsePositiveCount);
        final double getAllRecall = ratio(getAllCoveredInputCount, evaluatedInputCount);
        final double getAllF1 = f1(getAllPrecision, getAllRecall);
        final double averageUniqueCandidateCount = ratio(uniqueCandidateCount, evaluatedInputCount);

        return new ResultRow(languageLabel, reductionSettings.reductionMode().name(), storeOriginal,
                includeStemInEvaluation, knowledgePercent, seed, dictionaryData.entryCount(), trainingEntryCount,
                evaluatedInputCount, getCorrectCount, getAccuracy, getAllTruePositiveCount, getAllFalsePositiveCount,
                getAllCoveredInputCount, getAllPrecision, getAllRecall, getAllF1, averageUniqueCandidateCount);
    }

    /**
     * Compiles a trie from the deterministically selected subset of dictionary
     * entries.
     *
     * @param dictionaryData    parsed dictionary data
     * @param reductionSettings reduction settings
     * @param storeOriginal     whether stems themselves should be stored
     * @param knowledgePercent  retained percentage of dictionary entries
     * @param seed              deterministic sampling seed
     * @return compiled trie for the selected subset
     */
    private FrequencyTrie<String> compileSubset(final DictionaryData dictionaryData,
            final ReductionSettings reductionSettings, final boolean storeOriginal, final int knowledgePercent,
            final long seed) {
        validateKnowledgePercent(knowledgePercent);

        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new, reductionSettings);
        final SplittableRandom random = new SplittableRandom(seed);

        for (DictionaryEntry entry : dictionaryData.entries()) {
            if (!isSelected(random, knowledgePercent)) {
                continue;
            }
            if (storeOriginal) {
                builder.put(entry.stem(), NOOP_PATCH_COMMAND);
            }
            for (String variant : entry.variants()) {
                final String patch = this.patchCommandEncoder.encode(variant, entry.stem());
                builder.put(variant, patch);
            }
        }
        return builder.build();
    }

    /**
     * Evaluates one input word form against both lookup APIs.
     *
     * @param input        input form to transform
     * @param expectedStem expected stem
     * @param trie         compiled trie under test
     * @return immutable counts for this single input
     */
    private static EvaluationCounts evaluateInput(final String input, final String expectedStem,
            final FrequencyTrie<String> trie) {
        long getCorrect = 0L;
        final String preferredPatch = trie.get(input);
        if (preferredPatch != null) {
            final String preferredStem = PatchCommandEncoder.apply(input, preferredPatch);
            if (expectedStem.equals(preferredStem)) {
                getCorrect = 1L;
            }
        } else {
            if (expectedStem.equals(input)) {
                getCorrect = 1L;
            }
        }

        final String[] patches = trie.getAll(input);

        long truePositives = 0L;
        long falsePositives = 0L;
        long coveredInputs = 0L;
        for (String patch : patches) {
            final String candidateStem = PatchCommandEncoder.apply(input, patch);
            if (expectedStem.equals(candidateStem)) {
                truePositives++;
                coveredInputs = 1L;
            } else {
                falsePositives++;
            }
        }
        return new EvaluationCounts(getCorrect, truePositives, falsePositives, coveredInputs, patches.length);
    }

    /**
     * Counts how many entries would be selected for one scenario without
     * recompiling the trie.
     *
     * @param entryCount       total entry count
     * @param seed             deterministic sampling seed
     * @param knowledgePercent retained percentage of dictionary entries
     * @return selected entry count
     */
    private static long countSelectedEntries(final int entryCount, final long seed, final int knowledgePercent) {
        validateKnowledgePercent(knowledgePercent);
        final SplittableRandom random = new SplittableRandom(seed);
        long count = 0L;
        for (int index = 0; index < entryCount; index++) {
            if (isSelected(random, knowledgePercent)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns whether one entry is selected for the supplied knowledge level.
     *
     * @param random           deterministic random source
     * @param knowledgePercent retained percentage of entries
     * @return {@code true} when the entry should be kept
     */
    private static boolean isSelected(final SplittableRandom random, final int knowledgePercent) {
        return random.nextInt(100) < knowledgePercent;
    }

    /**
     * Validates one knowledge percentage value.
     *
     * @param knowledgePercent value to validate
     */
    private static void validateKnowledgePercent(final int knowledgePercent) {
        if (knowledgePercent < MINIMUM_KNOWLEDGE_PERCENT || knowledgePercent > MAXIMUM_KNOWLEDGE_PERCENT
                || knowledgePercent % KNOWLEDGE_PERCENT_STEP != 0) {
            throw new IllegalArgumentException(
                    "knowledgePercent must be one of 10, 20, ..., 100 but was " + knowledgePercent + '.');
        }
    }

    /**
     * Computes a safe ratio.
     *
     * @param numerator   numerator
     * @param denominator denominator
     * @return ratio, or {@code 0.0} when the denominator is zero
     */
    private static double ratio(final long numerator, final long denominator) {
        if (denominator == 0L) { // NOPMD
            return 0.0d;
        }
        return (double) numerator / (double) denominator; // NOPMD
    }

    /**
     * Computes the harmonic mean of precision and recall.
     *
     * @param precision global precision
     * @param recall    global recall
     * @return F1 score, or {@code 0.0} when both inputs are zero
     */
    private static double f1(final double precision, final double recall) {
        if (precision == 0.0d && recall == 0.0d) {
            return 0.0d;
        }
        return 2.0d * precision * recall / (precision + recall);
    }

    /**
     * One parsed dictionary line.
     *
     * @param stem       canonical stem
     * @param variants   known variants of the stem
     * @param lineNumber physical line number in the source dictionary
     */
    private record DictionaryEntry(String stem, String[] variants, int lineNumber) {

        /**
         * Creates a parsed dictionary entry.
         *
         * @param stem       canonical stem
         * @param variants   known variants of the stem
         * @param lineNumber physical line number in the source dictionary
         */
        private DictionaryEntry {
            Objects.requireNonNull(stem, "stem");
            Objects.requireNonNull(variants, "variants");
            if (lineNumber < 1) { // NOPMD
                throw new IllegalArgumentException("lineNumber must be positive.");
            }
        }
    }

    /**
     * Parsed dictionary state reused across all scenarios.
     *
     * @param sourceDescription logical source description
     * @param parseStatistics   parser statistics
     * @param entries           immutable ordered entries
     */
    private record DictionaryData(String sourceDescription, StemmerDictionaryParser.ParseStatistics parseStatistics,
            List<DictionaryEntry> entries) {

        /**
         * Creates parsed dictionary data.
         *
         * @param sourceDescription logical source description
         * @param parseStatistics   parser statistics
         * @param entries           immutable ordered entries
         */
        private DictionaryData {
            Objects.requireNonNull(sourceDescription, "sourceDescription");
            Objects.requireNonNull(parseStatistics, "parseStatistics");
            Objects.requireNonNull(entries, "entries");
            entries = List.copyOf(entries);
        }

        /**
         * Returns the number of logical dictionary entries.
         *
         * @return entry count
         */
        private int entryCount() {
            return this.entries.size();
        }
    }

    /**
     * Per-input evaluation counts.
     */
    private static final class EvaluationCounts {

        /**
         * Preferred lookup correctness.
         */
        private final long getCorrect;

        /**
         * Number of correct candidates returned by {@code getAll()}.
         */
        private final long getAllTruePositives;

        /**
         * Number of incorrect candidates returned by {@code getAll()}.
         */
        private final long getAllFalsePositives;

        /**
         * Whether the correct stem was covered by {@code getAll()}.
         */
        private final long getAllCoveredInputs;

        /**
         * Number of candidate commands returned by {@code getAll()}.
         */
        private final long uniqueCandidateCount;

        /**
         * Creates a new immutable counter object.
         *
         * @param getCorrect           preferred lookup correctness
         * @param getAllTruePositives  correct candidates
         * @param getAllFalsePositives incorrect candidates
         * @param getAllCoveredInputs  coverage marker
         * @param uniqueCandidateCount candidate command count
         */
        private EvaluationCounts(final long getCorrect, final long getAllTruePositives, final long getAllFalsePositives,
                final long getAllCoveredInputs, final long uniqueCandidateCount) {
            this.getCorrect = getCorrect;
            this.getAllTruePositives = getAllTruePositives;
            this.getAllFalsePositives = getAllFalsePositives;
            this.getAllCoveredInputs = getAllCoveredInputs;
            this.uniqueCandidateCount = uniqueCandidateCount;
        }

        /**
         * Returns preferred lookup correctness.
         *
         * @return preferred lookup correctness
         */
        private long getCorrect() {
            return this.getCorrect;
        }

        /**
         * Returns the number of correct candidates.
         *
         * @return correct candidates
         */
        private long getAllTruePositives() {
            return this.getAllTruePositives;
        }

        /**
         * Returns the number of incorrect candidates.
         *
         * @return incorrect candidates
         */
        private long getAllFalsePositives() {
            return this.getAllFalsePositives;
        }

        /**
         * Returns the per-input coverage marker.
         *
         * @return coverage marker
         */
        private long getAllCoveredInputs() {
            return this.getAllCoveredInputs;
        }

        /**
         * Returns the number of candidate commands.
         *
         * @return candidate command count
         */
        private long getUniqueCandidateCount() {
            return this.uniqueCandidateCount;
        }
    }

    /**
     * One immutable result row of the knowledge experiment.
     *
     * @param language                    language label
     * @param reductionMode               reduction mode name
     * @param storeOriginal               whether no-op patches were stored for
     *                                    canonical stems
     * @param includeStemInEvaluation     whether canonical stems were part of the
     *                                    evaluated inputs
     * @param knowledgePercent            retained knowledge percentage
     * @param seed                        deterministic sampling seed
     * @param dictionaryEntryCount        total parsed dictionary entry count
     * @param trainingEntryCount          selected dictionary entry count used for
     *                                    build
     * @param evaluatedInputCount         total evaluated input count
     * @param getCorrectCount             number of correct preferred
     *                                    transformations
     * @param getAccuracy                 preferred lookup accuracy
     * @param getAllTruePositiveCount     number of unique correct candidates from
     *                                    {@code getAll()}
     * @param getAllFalsePositiveCount    number of unique incorrect candidates from
     *                                    {@code getAll()}
     * @param getAllCoveredInputCount     number of inputs for which the correct
     *                                    stem appeared in {@code getAll()}
     * @param getAllPrecision             global candidate precision for
     *                                    {@code getAll()}
     * @param getAllRecall                global input recall for {@code getAll()}
     * @param getAllF1                    F1 score derived from {@code getAll()}
     *                                    precision and recall
     * @param averageUniqueCandidateCount average number of unique candidate stems
     *                                    per input
     */
    public record ResultRow(String language, String reductionMode, boolean storeOriginal,
            boolean includeStemInEvaluation, int knowledgePercent, long seed, int dictionaryEntryCount,
            long trainingEntryCount, long evaluatedInputCount, long getCorrectCount, double getAccuracy,
            long getAllTruePositiveCount, long getAllFalsePositiveCount, long getAllCoveredInputCount,
            double getAllPrecision, double getAllRecall, double getAllF1, double averageUniqueCandidateCount) {

        /**
         * Creates one immutable result row.
         *
         * @param language                    language label
         * @param reductionMode               reduction mode name
         * @param storeOriginal               whether no-op patches were stored for
         *                                    canonical stems
         * @param includeStemInEvaluation     whether canonical stems were evaluated
         * @param knowledgePercent            retained knowledge percentage
         * @param seed                        deterministic sampling seed
         * @param dictionaryEntryCount        total dictionary entry count
         * @param trainingEntryCount          selected training entry count
         * @param evaluatedInputCount         total evaluated input count
         * @param getCorrectCount             number of correct preferred
         *                                    transformations
         * @param getAccuracy                 preferred lookup accuracy
         * @param getAllTruePositiveCount     number of unique correct candidates
         * @param getAllFalsePositiveCount    number of unique incorrect candidates
         * @param getAllCoveredInputCount     coverage count for {@code getAll()}
         * @param getAllPrecision             global candidate precision for
         *                                    {@code getAll()}
         * @param getAllRecall                global input recall for {@code getAll()}
         * @param getAllF1                    harmonic mean of precision and recall
         * @param averageUniqueCandidateCount average unique candidate count per input
         */
        @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
        public ResultRow {
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(reductionMode, "reductionMode");
            validateKnowledgePercent(knowledgePercent);
            if (dictionaryEntryCount < 0) {
                throw new IllegalArgumentException("dictionaryEntryCount must not be negative.");
            }
            if (trainingEntryCount < 0L) {
                throw new IllegalArgumentException("trainingEntryCount must not be negative.");
            }
            if (evaluatedInputCount < 0L) {
                throw new IllegalArgumentException("evaluatedInputCount must not be negative.");
            }
            if (getCorrectCount < 0L) {
                throw new IllegalArgumentException("getCorrectCount must not be negative.");
            }
            if (getAllTruePositiveCount < 0L) {
                throw new IllegalArgumentException("getAllTruePositiveCount must not be negative.");
            }
            if (getAllFalsePositiveCount < 0L) {
                throw new IllegalArgumentException("getAllFalsePositiveCount must not be negative.");
            }
            if (getAllCoveredInputCount < 0L) {
                throw new IllegalArgumentException("getAllCoveredInputCount must not be negative.");
            }
        }

        /**
         * Returns the stable CSV header of this result format.
         *
         * @return CSV header line
         */
        public static String csvHeader() {
            return String.join(",",
                    List.of("language", "reductionMode", "storeOriginal", "includeStemInEvaluation", "knowledgePercent",
                            "seed", "dictionaryEntryCount", "trainingEntryCount", "evaluatedInputCount",
                            "getCorrectCount", "getAccuracy", "getAllTruePositiveCount", "getAllFalsePositiveCount",
                            "getAllCoveredInputCount", "getAllPrecision", "getAllRecall", "getAllF1",
                            "averageUniqueCandidateCount"));
        }

        /**
         * Serializes this row as one CSV record.
         *
         * @return CSV record
         */
        public String toCsvRow() {
            return String.join(",",
                    List.of(escapeCsv(this.language), escapeCsv(this.reductionMode), String.valueOf(this.storeOriginal),
                            String.valueOf(this.includeStemInEvaluation), String.valueOf(this.knowledgePercent),
                            String.valueOf(this.seed), String.valueOf(this.dictionaryEntryCount),
                            String.valueOf(this.trainingEntryCount), String.valueOf(this.evaluatedInputCount),
                            String.valueOf(this.getCorrectCount), formatDouble(this.getAccuracy),
                            String.valueOf(this.getAllTruePositiveCount), String.valueOf(this.getAllFalsePositiveCount),
                            String.valueOf(this.getAllCoveredInputCount), formatDouble(this.getAllPrecision),
                            formatDouble(this.getAllRecall), formatDouble(this.getAllF1),
                            formatDouble(this.averageUniqueCandidateCount)));
        }

        /**
         * Escapes a string for CSV output.
         *
         * @param value value to escape
         * @return escaped CSV cell
         */
        private static String escapeCsv(final String value) {
            if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0
                    && value.indexOf('\r') < 0) {
                return value;
            }
            return '"' + value.replace("\"", "\"\"") + '"';
        }

        /**
         * Formats one floating-point value using a locale-independent decimal
         * representation.
         *
         * @param value value to format
         * @return formatted value
         */
        private static String formatDouble(final double value) {
            return String.format(Locale.ROOT, "%.10f", value);
        }
    }
}
