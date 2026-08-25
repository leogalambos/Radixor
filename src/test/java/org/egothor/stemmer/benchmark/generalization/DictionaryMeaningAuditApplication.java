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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.egothor.stemmer.CaseProcessingMode;
import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.FrequencyTrieBuilders;
import org.egothor.stemmer.PatchCommandEncoder;
import org.egothor.stemmer.StemmerDictionaryParser;
import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.egothor.stemmer.TrieStatistics;
import org.egothor.stemmer.WordTraversalDirection;
import org.egothor.stemmer.benchmark.quality.PairwiseMetrics;
import org.egothor.stemmer.benchmark.quality.ProcessingMode;
import org.egothor.stemmer.benchmark.quality.QualityEvaluator;
import org.egothor.stemmer.benchmark.quality.QualityResult;

/**
 * Audits anomalous dictionaries using only evidence already present in each
 * Radixor dictionary and writes non-destructive filtered candidate inputs.
 *
 * <p>The application never changes a registered model input. A mapping is
 * removed only when its baseline patch command is rare and the same surface
 * form is assigned elsewhere in the same dictionary to a substantially closer
 * stem. This identifies internally dominated assignments such as a declined
 * noun being attached to an unrelated derivational stem while preserving
 * uncorroborated irregular and suppletive forms. Rare, highly divergent
 * mappings without an internal alternative are reported for review but remain
 * in the generated variant.</p>
 *
 * <p>Each input specification has the form
 * {@code LANGUAGE|MODEL_ID|EXPECTED_BASELINE_COMMANDS|DICTIONARY_PATH|DESTINATION_OR_-}.
 * The first argument is the report output directory. A dash destination audits
 * a source without materializing an unchanged candidate. Generated
 * dictionaries retain the source comment header.</p>
 *
 * <p>This offline application is single-threaded. It processes models one at a
 * time and bounds retained audit state to the current dictionary. Its memory
 * use is linear in the number of rows, variants, distinct commands, and
 * distinct surface forms in that dictionary.</p>
 */
public final class DictionaryMeaningAuditApplication {

    /** Minimum baseline vocabulary that places a model in this audit campaign. */
    static final long MINIMUM_ANOMALOUS_COMMANDS = 500L;

    /** Largest command frequency eligible for automatic removal. */
    static final long MAXIMUM_RARE_COMMAND_FREQUENCY = 2L;

    /** Minimum edit distance eligible for automatic removal or review. */
    static final int MINIMUM_DIVERGENT_DISTANCE = 4;

    /** Minimum absolute distance improvement required from another stem. */
    static final int MINIMUM_DISTANCE_IMPROVEMENT = 2;

    /** Minimum coherent alternative-stem group eligible for automatic removal. */
    static final int MINIMUM_DOMINATED_GROUP_SIZE = 3;

    /** Non-lexical metadata accidentally embedded in one Czech source row. */
    private static final Set<String> CZECH_METADATA_ARTIFACTS = Set.of(
            "because", "is", "it", "no", "perfective", "present");

    /** Utility class. */
    private DictionaryMeaningAuditApplication() {
        throw new AssertionError("No instances.");
    }

    /**
     * Audits every supplied dictionary and writes variants and evidence reports.
     *
     * @param arguments output directory followed by one or more input
     *                  specifications
     * @throws IOException if an input cannot be read or an output cannot be
     *                     written
     * @throws IllegalArgumentException if arguments or an expected command count
     *                                  are invalid
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length < 2) {
            throw new IllegalArgumentException(
                    "Expected: output-directory "
                            + "LANGUAGE|MODEL_ID|EXPECTED_COMMANDS|DICTIONARY_PATH|DESTINATION_OR_- [...]");
        }
        final Path outputDirectory = Path.of(arguments[0]);
        Files.createDirectories(outputDirectory);
        final Path summary = outputDirectory.resolve("audit-summary.csv");
        final Path evidence = outputDirectory.resolve("audit-evidence.tsv");
        final Path sanitation = outputDirectory.resolve("audit-sanitation.tsv");
        try (BufferedWriter summaryWriter = Files.newBufferedWriter(summary, StandardCharsets.UTF_8);
                BufferedWriter evidenceWriter = Files.newBufferedWriter(evidence, StandardCharsets.UTF_8);
                BufferedWriter sanitationWriter = Files.newBufferedWriter(sanitation, StandardCharsets.UTF_8)) {
            writeSummaryHeader(summaryWriter);
            writeEvidenceHeader(evidenceWriter);
            writeSanitationHeader(sanitationWriter);
            for (int index = 1; index < arguments.length; index++) {
                final InputSpec spec = InputSpec.parse(arguments[index]);
                final AuditResult result = audit(spec);
                writeSummary(summaryWriter, result);
                for (final Evidence item : result.evidence()) {
                    writeEvidence(evidenceWriter, spec, item);
                }
                for (final SanitationEvidence item : result.sanitationEvidence()) {
                    writeSanitation(sanitationWriter, spec, item);
                }
                System.out.printf(Locale.ROOT,
                        "%s: %,d -> %,d commands; removed %,d artifact and %,d dominated mappings; "
                                + "normalized %,d tokens; parser ignored %,d whitespace rows and %,d variants; "
                                + "%,d review candidates.%n",
                        spec.language(), result.originalCommandCount(), result.filteredCommandCount(),
                        result.removedArtifactMappings(), result.removedMappings(),
                        result.normalizedTokens(), result.ignoredWhitespaceRows(),
                        result.ignoredWhitespaceVariants(), result.reviewCandidates());
            }
        }
    }

    /** Audits and materializes one dictionary variant. */
    private static AuditResult audit(final InputSpec spec) throws IOException {
        if (!Files.isRegularFile(spec.dictionary())) {
            throw new IllegalArgumentException("Dictionary does not exist: " + spec.dictionary());
        }
        final List<EditCostSensitivityApplication.DictionaryRow> sourceRows = readRows(
                spec.dictionary(), CaseProcessingMode.AS_IS);
        final WhitespaceResult whitespace = inspectWhitespace(spec.dictionary());
        final List<EditCostSensitivityApplication.DictionaryRow> originalRows = lowercaseRows(sourceRows);
        final SanitationResult sanitation = sanitize(sourceRows, spec.language());
        final List<EditCostSensitivityApplication.DictionaryRow> sanitizedRows = lowercaseRows(sanitation.rows());
        final int minimumDominatedGroupSize = spec.language() == StemmerPatchTrieLoader.Language.DE_DE
                ? 1 : MINIMUM_DOMINATED_GROUP_SIZE;
        final Analysis analysis = analyze(sanitizedRows, minimumDominatedGroupSize);
        final List<EditCostSensitivityApplication.DictionaryRow> filteredRows = filterRows(
                sanitizedRows, analysis.removals(), false);
        final List<EditCostSensitivityApplication.DictionaryRow> filteredSourceRows = filterRows(
                sanitation.rows(), analysis.removals(), true);
        final long originalCommands = EditCostSensitivityApplication.countDistinctPatchCommands(
                originalRows, EditCostSensitivityApplication.BASELINE);
        if (originalCommands != spec.expectedBaselineCommands()) {
            throw new IllegalArgumentException("Expected " + spec.expectedBaselineCommands()
                    + " baseline commands for " + spec.modelId() + " but measured " + originalCommands + '.');
        }
        final long filteredCommands = EditCostSensitivityApplication.countDistinctPatchCommands(
                filteredRows, EditCostSensitivityApplication.BASELINE);
        final FrequencyTrie<CompiledPatchCommand> trie = EditCostSensitivityApplication.buildCompiledTrie(
                filteredRows, EditCostSensitivityApplication.BASELINE);
        final TrieStatistics statistics = FrequencyTrieBuilders.computeStatistics(trie);
        final ExactResult exact = exactResult(sanitizedRows, trie);
        final QualityResult quality = QualityEvaluator.evaluate(
                "MEANING_FILTERED", spec.language().name(), ProcessingMode.ALL_WORDS,
                EditCostSensitivityApplication.toGoldGroups(sanitizedRows),
                form -> EditCostSensitivityApplication.stem(trie, form));
        if (spec.destination().isPresent()) {
            writeVariant(spec.dictionary(), spec.destination().orElseThrow(), filteredSourceRows);
        }
        return new AuditResult(spec, originalRows.size(), countVariants(originalRows),
                countVariants(sanitizedRows),
                countVariants(filteredRows), originalCommands, filteredCommands,
                sanitation.removedRows(), sanitation.removedMappings(), sanitation.normalizedTokens(),
                sanitation.legacyEncodingReviews(), whitespace.ignoredRows(),
                whitespace.ignoredVariants(), analysis.removals().size(),
                analysis.reviewCandidates(), statistics,
                exact, quality, analysis.evidence(),
                mergeSanitationEvidence(sanitation.evidence(), whitespace.evidence()));
    }

    /** Reads one compressed dictionary through the production parser. */
    private static List<EditCostSensitivityApplication.DictionaryRow> readRows(
            final Path dictionary, final CaseProcessingMode caseMode) throws IOException {
        final List<EditCostSensitivityApplication.DictionaryRow> rows = new ArrayList<>();
        try (InputStream file = Files.newInputStream(dictionary);
                GZIPInputStream gzip = new GZIPInputStream(file);
                InputStreamReader streamReader = new InputStreamReader(gzip, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader)) {
            StemmerDictionaryParser.parse(reader, dictionary.toString(), caseMode,
                    (stem, variants, lineNumber) -> rows.add(
                            new EditCostSensitivityApplication.DictionaryRow(lineNumber, stem, variants)));
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Dictionary contains no parsed rows: " + dictionary);
        }
        return List.copyOf(rows);
    }

    /** Reports rows and variants that the production parser rejects for whitespace. */
    static WhitespaceResult inspectWhitespace(final Path dictionary) throws IOException {
        final List<SanitationEvidence> evidence = new ArrayList<>();
        long ignoredRows = 0L;
        long ignoredVariants = 0L;
        try (InputStream file = Files.newInputStream(dictionary);
                GZIPInputStream gzip = new GZIPInputStream(file);
                InputStreamReader streamReader = new InputStreamReader(gzip, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader)) {
            int lineNumber = 0;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                lineNumber++;
                final String logicalLine = stripRemark(line).trim();
                if (logicalLine.isEmpty()) {
                    continue;
                }
                final String[] columns = logicalLine.split("\t", -1);
                final String stem = columns[0].strip();
                if (stem.isEmpty()) {
                    continue;
                }
                if (containsWhitespace(stem)) {
                    ignoredRows++;
                    evidence.add(new SanitationEvidence(SanitationDecision.PARSER_REJECT_WHITESPACE_ROW,
                            lineNumber, stem, ""));
                    continue;
                }
                for (int index = 1; index < columns.length; index++) {
                    final String variant = columns[index].strip();
                    if (!variant.isEmpty() && containsWhitespace(variant)) {
                        ignoredVariants++;
                        evidence.add(new SanitationEvidence(
                                SanitationDecision.PARSER_REJECT_WHITESPACE_VARIANT,
                                lineNumber, variant, ""));
                    }
                }
            }
        }
        return new WhitespaceResult(ignoredRows, ignoredVariants, List.copyOf(evidence));
    }

    /** Returns the part of one line preceding the first supported remark marker. */
    private static String stripRemark(final String line) {
        final int hash = line.indexOf('#');
        final int slash = line.indexOf("//");
        if (hash < 0) {
            return slash < 0 ? line : line.substring(0, slash);
        }
        if (slash < 0) {
            return line.substring(0, hash);
        }
        return line.substring(0, Math.min(hash, slash));
    }

    /** Returns whether a dictionary field contains a Unicode whitespace character. */
    private static boolean containsWhitespace(final String field) {
        for (int index = 0; index < field.length(); index++) {
            if (Character.isWhitespace(field.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    /** Concatenates objective sanitation and parser-rejection evidence. */
    private static List<SanitationEvidence> mergeSanitationEvidence(
            final List<SanitationEvidence> sanitation,
            final List<SanitationEvidence> whitespace) {
        final List<SanitationEvidence> result = new ArrayList<>(sanitation.size() + whitespace.size());
        result.addAll(sanitation);
        result.addAll(whitespace);
        result.sort(Comparator.comparing(SanitationEvidence::decision)
                .thenComparingInt(SanitationEvidence::lineNumber)
                .thenComparing(SanitationEvidence::source));
        return List.copyOf(result);
    }

    /** Returns rows normalized with the same root-locale case policy as runtime loading. */
    private static List<EditCostSensitivityApplication.DictionaryRow> lowercaseRows(
            final List<EditCostSensitivityApplication.DictionaryRow> rows) {
        final List<EditCostSensitivityApplication.DictionaryRow> result = new ArrayList<>(rows.size());
        for (final EditCostSensitivityApplication.DictionaryRow row : rows) {
            final String[] variants = new String[row.variants().length];
            for (int index = 0; index < variants.length; index++) {
                variants[index] = row.variants()[index].toLowerCase(Locale.ROOT);
            }
            result.add(new EditCostSensitivityApplication.DictionaryRow(row.lineNumber(),
                    row.stem().toLowerCase(Locale.ROOT), variants));
        }
        return List.copyOf(result);
    }

    /** Removes objective source artifacts and reports retained legacy encodings. */
    static SanitationResult sanitize(final List<EditCostSensitivityApplication.DictionaryRow> rows,
            final StemmerPatchTrieLoader.Language language) {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(language, "language");
        final List<EditCostSensitivityApplication.DictionaryRow> sanitized = new ArrayList<>(rows.size());
        final List<SanitationEvidence> evidence = new ArrayList<>();
        long removedRows = 0L;
        long removedMappings = 0L;
        long normalizedTokens = 0L;
        long legacyEncodingReviews = 0L;
        for (final EditCostSensitivityApplication.DictionaryRow row : rows) {
            if (language == StemmerPatchTrieLoader.Language.DE_DE && containsLegacyGermanUmlaut(row.stem())) {
                legacyEncodingReviews++;
                evidence.add(new SanitationEvidence(SanitationDecision.REVIEW_LEGACY_ENCODING,
                        row.lineNumber(), row.stem(), ""));
            }
            final String stem = sanitizeToken(row.stem());
            if (!stem.equals(row.stem())) {
                normalizedTokens++;
                evidence.add(new SanitationEvidence(SanitationDecision.NORMALIZE_TOKEN,
                        row.lineNumber(), row.stem(), stem));
            }
            if (isInvalidStem(stem)) {
                removedRows++;
                evidence.add(new SanitationEvidence(SanitationDecision.REMOVE_INVALID_ROW,
                        row.lineNumber(), row.stem(), ""));
                continue;
            }

            int identityIndex = -1;
            for (int index = 0; index < row.variants().length; index++) {
                final String candidate = sanitizeToken(row.variants()[index]);
                if (candidate.equalsIgnoreCase(stem)) {
                    identityIndex = index;
                    break;
                }
            }
            final List<String> variants = new ArrayList<>(row.variants().length);
            for (int index = 0; index < row.variants().length; index++) {
                final String sourceVariant = row.variants()[index];
                if (language == StemmerPatchTrieLoader.Language.DE_DE
                        && containsLegacyGermanUmlaut(sourceVariant)) {
                    legacyEncodingReviews++;
                    evidence.add(new SanitationEvidence(SanitationDecision.REVIEW_LEGACY_ENCODING,
                            row.lineNumber(), sourceVariant, ""));
                }
                final String variant = sanitizeToken(sourceVariant);
                if (isInvalidVariant(language, stem, variant, index, identityIndex)) {
                    removedMappings++;
                    evidence.add(new SanitationEvidence(SanitationDecision.REMOVE_INVALID_VARIANT,
                            row.lineNumber(), sourceVariant, ""));
                    continue;
                }
                if (!variant.equals(sourceVariant)) {
                    normalizedTokens++;
                    evidence.add(new SanitationEvidence(SanitationDecision.NORMALIZE_TOKEN,
                            row.lineNumber(), sourceVariant, variant));
                }
                variants.add(variant);
            }
            sanitized.add(new EditCostSensitivityApplication.DictionaryRow(
                    row.lineNumber(), stem, variants.toArray(String[]::new)));
        }
        return new SanitationResult(List.copyOf(sanitized), List.copyOf(evidence),
                removedRows, removedMappings, normalizedTokens, legacyEncodingReviews);
    }

    /** Returns a token without non-semantic Unicode format marks. */
    static String sanitizeToken(final String token) {
        Objects.requireNonNull(token, "token");
        final StringBuilder result = new StringBuilder(token.length());
        for (int index = 0; index < token.length();) {
            final int codePoint = token.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.getType(codePoint) == Character.FORMAT) {
                continue;
            }
            result.appendCodePoint(codePoint);
        }
        return result.toString();
    }

    /** Returns whether a German token contains the legacy quote-plus-vowel notation. */
    private static boolean containsLegacyGermanUmlaut(final String token) {
        for (int index = 0; index + 1 < token.length(); index++) {
            if (token.charAt(index) == '"' && "aouAOU".indexOf(token.charAt(index + 1)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Returns whether a stem is a known non-lexical source label. */
    private static boolean isInvalidStem(final String stem) {
        return stem.toLowerCase(Locale.ROOT).startsWith("citations:");
    }

    /** Returns whether a variant is an objective source artifact. */
    private static boolean isInvalidVariant(final StemmerPatchTrieLoader.Language language,
            final String stem, final String variant, final int index, final int identityIndex) {
        if (variant.equals("-") || variant.equals("–") || variant.indexOf('!') >= 0) {
            return true;
        }
        final String normalized = variant.toLowerCase(Locale.ROOT);
        if (language == StemmerPatchTrieLoader.Language.CS_CZ
                && CZECH_METADATA_ARTIFACTS.contains(normalized)) {
            return true;
        }
        return identityIndex >= 0 && index < identityIndex
                && !stem.equalsIgnoreCase(variant)
                && (normalized.equals("audio") || normalized.equals("file"));
    }

    /** Builds command frequencies and classifies dominated and review mappings. */
    static Analysis analyze(final List<EditCostSensitivityApplication.DictionaryRow> rows) {
        return analyze(rows, MINIMUM_DOMINATED_GROUP_SIZE);
    }

    /** Builds command frequencies and applies the supplied coherent-paradigm threshold. */
    private static Analysis analyze(final List<EditCostSensitivityApplication.DictionaryRow> rows,
            final int minimumDominatedGroupSize) {
        Objects.requireNonNull(rows, "rows");
        if (minimumDominatedGroupSize < 1) {
            throw new IllegalArgumentException("minimumDominatedGroupSize must be positive.");
        }
        final PatchCommandEncoder encoder = baselineEncoder();
        final Map<String, Long> commandCounts = new HashMap<>();
        for (final EditCostSensitivityApplication.DictionaryRow row : rows) {
            commandCounts.merge(encoder.encode(row.stem(), row.stem()), 1L, Long::sum);
            for (final String variant : row.variants()) {
                commandCounts.merge(encoder.encode(variant, row.stem()), 1L, Long::sum);
            }
        }

        final Map<String, BestAssignment> bestByForm = new HashMap<>();
        for (final EditCostSensitivityApplication.DictionaryRow row : rows) {
            updateBest(bestByForm, new Assignment(row.stem(), row.stem(),
                    encoder.encode(row.stem(), row.stem()), 0, true), commandCounts);
            for (final String variant : row.variants()) {
                updateBest(bestByForm, new Assignment(row.stem(), variant,
                        encoder.encode(variant, row.stem()), editDistance(variant, row.stem()), false),
                        commandCounts);
            }
        }

        final Map<CandidateGroupKey, List<Evidence>> dominatedCandidates = new HashMap<>();
        final List<Evidence> divergentCandidates = new ArrayList<>();
        long reviewCandidates = 0L;
        for (final EditCostSensitivityApplication.DictionaryRow row : rows) {
            for (final String variant : row.variants()) {
                if (variant.equals(row.stem())) {
                    continue;
                }
                final String command = encoder.encode(variant, row.stem());
                final long commandCount = commandCounts.get(command);
                final int distance = editDistance(variant, row.stem());
                final BestAssignment best = bestByForm.get(variant);
                if (isDominated(row.stem(), distance, commandCount, best)) {
                    final Evidence item = new Evidence(Decision.REVIEW_DOMINATED_MAPPING,
                            row.lineNumber(), variant, row.stem(), command, commandCount, distance,
                            best.stem(), best.command(), best.commandCount(), best.distance());
                    dominatedCandidates.computeIfAbsent(
                            new CandidateGroupKey(row.lineNumber(), best.stem()), ignored -> new ArrayList<>())
                            .add(item);
                } else if (isRareDivergent(variant, row.stem(), distance, commandCount)) {
                    reviewCandidates++;
                    divergentCandidates.add(new Evidence(Decision.REVIEW_RARE_DIVERGENT,
                            row.lineNumber(), variant,
                            row.stem(), command, commandCount, distance,
                            best.stem(), best.command(), best.commandCount(), best.distance()));
                }
            }
        }
        final Set<MappingKey> removals = new HashSet<>();
        final List<Evidence> evidence = new ArrayList<>(divergentCandidates);
        for (final List<Evidence> group : dominatedCandidates.values()) {
            final boolean coherentParadigm = group.size() >= minimumDominatedGroupSize;
            for (final Evidence candidate : group) {
                if (coherentParadigm) {
                    removals.add(new MappingKey(candidate.lineNumber(), candidate.variant()));
                    evidence.add(candidate.withDecision(Decision.REMOVE_DOMINATED_ASSIGNMENT));
                } else {
                    reviewCandidates++;
                    evidence.add(candidate);
                }
            }
        }
        evidence.sort(Comparator.comparing(Evidence::decision)
                .thenComparing(Comparator.comparingInt(Evidence::distance).reversed())
                .thenComparing(Evidence::stem)
                .thenComparing(Evidence::variant));
        return new Analysis(Set.copyOf(removals), List.copyOf(evidence), reviewCandidates);
    }

    /** Updates the structurally closest internally observed assignment. */
    private static void updateBest(final Map<String, BestAssignment> bestByForm,
            final Assignment candidate, final Map<String, Long> commandCounts) {
        final long commandCount = commandCounts.get(candidate.command());
        final BestAssignment replacement = new BestAssignment(candidate.stem(), candidate.command(),
                commandCount, candidate.distance(), candidate.syntheticIdentity());
        bestByForm.merge(candidate.variant(), replacement, DictionaryMeaningAuditApplication::betterAssignment);
    }

    /** Selects the stronger of two internal assignments for one surface form. */
    private static BestAssignment betterAssignment(final BestAssignment left, final BestAssignment right) {
        if (left.distance() != right.distance()) {
            return left.distance() < right.distance() ? left : right;
        }
        if (left.syntheticIdentity() != right.syntheticIdentity()) {
            return left.syntheticIdentity() ? left : right;
        }
        if (left.commandCount() != right.commandCount()) {
            return left.commandCount() > right.commandCount() ? left : right;
        }
        return left.stem().compareTo(right.stem()) <= 0 ? left : right;
    }

    /** Returns whether an assignment is strongly dominated by another internal stem. */
    static boolean isDominated(final String stem, final int distance, final long commandCount,
            final BestAssignment best) {
        Objects.requireNonNull(stem, "stem");
        Objects.requireNonNull(best, "best");
        return !stem.equals(best.stem())
                && commandCount <= MAXIMUM_RARE_COMMAND_FREQUENCY
                && distance >= MINIMUM_DIVERGENT_DISTANCE
                && distance - best.distance() >= MINIMUM_DISTANCE_IMPROVEMENT
                && best.distance() * 2 <= distance
                && best.commandCount() >= commandCount;
    }

    /** Returns whether a retained mapping warrants human semantic review. */
    static boolean isRareDivergent(final String variant, final String stem,
            final int distance, final long commandCount) {
        final int longest = Math.max(variant.length(), stem.length());
        return commandCount <= MAXIMUM_RARE_COMMAND_FREQUENCY
                && distance >= MINIMUM_DIVERGENT_DISTANCE
                && distance * 2 >= longest;
    }

    /** Computes allocation-bounded UTF-16 Levenshtein distance. */
    static int editDistance(final String left, final String right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.equals(right)) {
            return 0;
        }
        final String rows = left.length() >= right.length() ? left : right;
        final String columns = left.length() >= right.length() ? right : left;
        int[] previous = new int[columns.length() + 1];
        int[] current = new int[columns.length() + 1];
        for (int column = 0; column <= columns.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= rows.length(); row++) {
            current[0] = row;
            final char rowCharacter = rows.charAt(row - 1);
            for (int column = 1; column <= columns.length(); column++) {
                final int replacement = previous[column - 1]
                        + (rowCharacter == columns.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(Math.min(previous[column] + 1, current[column - 1] + 1), replacement);
            }
            final int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[columns.length()];
    }

    /** Creates the production-baseline backward patch encoder. */
    private static PatchCommandEncoder baselineEncoder() {
        return PatchCommandEncoder.builder()
                .traversalDirection(WordTraversalDirection.BACKWARD)
                .deleteCost(1)
                .insertCost(1)
                .replaceCost(1)
                .matchCost(0)
                .build();
    }

    /** Returns filtered immutable rows while preserving source row numbers. */
    private static List<EditCostSensitivityApplication.DictionaryRow> filterRows(
            final List<EditCostSensitivityApplication.DictionaryRow> rows,
            final Set<MappingKey> removals, final boolean normalizeRemovalKey) {
        final List<EditCostSensitivityApplication.DictionaryRow> filtered = new ArrayList<>(rows.size());
        for (final EditCostSensitivityApplication.DictionaryRow row : rows) {
            final List<String> retained = new ArrayList<>(row.variants().length);
            for (final String variant : row.variants()) {
                final String removalKey = normalizeRemovalKey
                        ? sanitizeToken(variant).toLowerCase(Locale.ROOT) : variant;
                if (!removals.contains(new MappingKey(row.lineNumber(), removalKey))) {
                    retained.add(variant);
                }
            }
            filtered.add(new EditCostSensitivityApplication.DictionaryRow(
                    row.lineNumber(), row.stem(), retained.toArray(String[]::new)));
        }
        return List.copyOf(filtered);
    }

    /** Writes one GZip variant while retaining source case and provenance comments. */
    private static void writeVariant(final Path source, final Path destination,
            final List<EditCostSensitivityApplication.DictionaryRow> rows) throws IOException {
        final Path temporary = destination.resolveSibling(destination.getFileName() + ".partial");
        Files.createDirectories(destination.getParent());
        try (OutputStream file = Files.newOutputStream(temporary);
                GZIPOutputStream gzip = new GZIPOutputStream(file);
                OutputStreamWriter streamWriter = new OutputStreamWriter(gzip, StandardCharsets.UTF_8);
                BufferedWriter writer = new BufferedWriter(streamWriter)) {
            for (final String headerLine : leadingHeader(source)) {
                writer.write(headerLine);
                writer.newLine();
            }
            writer.write("#");
            writer.newLine();
            writer.write("# Radixor filtered candidate dictionary; the registered source model is unchanged.");
            writer.newLine();
            writer.write("# Constructed only from checked-in source evidence: objective artifacts are sanitized");
            writer.newLine();
            writer.write("# and strongly internally dominated stem assignments are removed.");
            writer.newLine();
            for (final EditCostSensitivityApplication.DictionaryRow row : rows) {
                writer.write(row.stem());
                for (final String variant : row.variants()) {
                    writer.write('\t');
                    writer.write(variant);
                }
                writer.newLine();
            }
        }
        publish(temporary, destination);
    }

    /** Reads the contiguous source comment header. */
    private static List<String> leadingHeader(final Path source) throws IOException {
        final List<String> header = new ArrayList<>();
        try (InputStream file = Files.newInputStream(source);
                GZIPInputStream gzip = new GZIPInputStream(file);
                InputStreamReader streamReader = new InputStreamReader(gzip, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader)) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank() && !line.stripLeading().startsWith("#")
                        && !line.stripLeading().startsWith("//")) {
                    break;
                }
                header.add(line);
            }
        }
        return List.copyOf(header);
    }

    /** Atomically publishes an output when supported by the filesystem. */
    private static void publish(final Path temporary, final Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Computes exact-to-source-stem agreement over the sanitized dictionary. */
    private static ExactResult exactResult(final List<EditCostSensitivityApplication.DictionaryRow> rows,
            final FrequencyTrie<CompiledPatchCommand> trie) {
        long correct = 0L;
        long total = 0L;
        long changedCorrect = 0L;
        long changedTotal = 0L;
        for (final EditCostSensitivityApplication.DictionaryRow row : rows) {
            for (final String form : row.forms()) {
                final boolean exact = row.stem().equals(EditCostSensitivityApplication.stem(trie, form));
                final boolean changed = !row.stem().equals(form);
                total++;
                if (exact) {
                    correct++;
                }
                if (changed) {
                    changedTotal++;
                    if (exact) {
                        changedCorrect++;
                    }
                }
            }
        }
        return new ExactResult(correct, total, changedCorrect, changedTotal);
    }

    /** Counts explicit variant columns over parsed rows. */
    private static long countVariants(final List<EditCostSensitivityApplication.DictionaryRow> rows) {
        long result = 0L;
        for (final EditCostSensitivityApplication.DictionaryRow row : rows) {
            result = Math.addExact(result, row.variants().length);
        }
        return result;
    }

    /** Writes the stable summary header. */
    private static void writeSummaryHeader(final BufferedWriter writer) throws IOException {
        writer.write("language,model_id,source_rows,source_variants,sanitized_variants,filtered_variants,"
                + "original_commands,filtered_commands,command_change_percent,removed_rows,"
                + "removed_artifact_mappings,normalized_tokens,legacy_encoding_reviews,"
                + "ignored_whitespace_rows,ignored_whitespace_variants,"
                + "removed_semantic_mappings,review_candidates,"
                + "trie_nodes,trie_edges,trie_value_references,trie_logical_leaf_paths,"
                + "exact_percent,changed_exact_percent,pairwise_f05,over_percent,under_percent");
        writer.newLine();
    }

    /** Writes one numeric summary row. */
    private static void writeSummary(final BufferedWriter writer, final AuditResult result) throws IOException {
        final PairwiseMetrics pairwise = result.quality().pairwiseMetrics();
        final TrieStatistics stats = result.statistics();
        writer.write(String.join(",",
                result.spec().language().name(), result.spec().modelId(),
                Integer.toString(result.sourceRows()), Long.toString(result.sourceVariants()),
                Long.toString(result.sanitizedVariants()), Long.toString(result.filteredVariants()),
                Long.toString(result.originalCommandCount()),
                Long.toString(result.filteredCommandCount()),
                Double.toString(100.0d * (result.filteredCommandCount() - result.originalCommandCount())
                        / result.originalCommandCount()),
                Long.toString(result.removedRows()), Long.toString(result.removedArtifactMappings()),
                Long.toString(result.normalizedTokens()), Long.toString(result.legacyEncodingReviews()),
                Long.toString(result.ignoredWhitespaceRows()),
                Long.toString(result.ignoredWhitespaceVariants()),
                Long.toString(result.removedMappings()),
                Long.toString(result.reviewCandidates()),
                Long.toString(stats.internalNodeCount() + stats.leafNodeCount()),
                Long.toString(stats.edgeCount()), Long.toString(stats.valueReferenceCount()),
                Long.toString(stats.logicalLeafPathCount()),
                percentage(result.exact().correct(), result.exact().total()),
                percentage(result.exact().changedCorrect(), result.exact().changedTotal()),
                optional(pairwise.f05()), optional(result.quality().overPercentage()),
                optional(result.quality().underPercentage())));
        writer.newLine();
    }

    /** Writes the stable evidence header. */
    private static void writeEvidenceHeader(final BufferedWriter writer) throws IOException {
        writer.write("language\tmodel_id\tdecision\tline\tvariant\tstem\tcommand\tcommand_count\tdistance"
                + "\tbest_stem\tbest_command\tbest_command_count\tbest_distance");
        writer.newLine();
    }

    /** Writes the stable source-sanitation header. */
    private static void writeSanitationHeader(final BufferedWriter writer) throws IOException {
        writer.write("language\tmodel_id\tdecision\tline\tsource\treplacement");
        writer.newLine();
    }

    /** Writes one evidence row. */
    private static void writeEvidence(final BufferedWriter writer, final InputSpec spec,
            final Evidence evidence) throws IOException {
        writer.write(spec.language().name());
        writer.write('\t');
        writer.write(spec.modelId());
        writer.write('\t');
        writer.write(evidence.decision().name());
        writer.write('\t');
        writer.write(Integer.toString(evidence.lineNumber()));
        for (final String field : List.of(evidence.variant(), evidence.stem(), evidence.command(),
                Long.toString(evidence.commandCount()), Integer.toString(evidence.distance()),
                evidence.bestStem(), evidence.bestCommand(), Long.toString(evidence.bestCommandCount()),
                Integer.toString(evidence.bestDistance()))) {
            writer.write('\t');
            writer.write(field);
        }
        writer.newLine();
    }

    /** Writes one source-sanitation record. */
    private static void writeSanitation(final BufferedWriter writer, final InputSpec spec,
            final SanitationEvidence evidence) throws IOException {
        writer.write(spec.language().name());
        writer.write('\t');
        writer.write(spec.modelId());
        writer.write('\t');
        writer.write(evidence.decision().name());
        writer.write('\t');
        writer.write(Integer.toString(evidence.lineNumber()));
        writer.write('\t');
        writer.write(evidence.source());
        writer.write('\t');
        writer.write(evidence.replacement());
        writer.newLine();
    }

    /** Formats one percentage while preserving an undefined denominator. */
    private static String percentage(final long numerator, final long denominator) {
        return denominator == 0L ? "" : Double.toString(100.0d * numerator / denominator);
    }

    /** Formats an optional finite metric. */
    private static String optional(final OptionalDouble value) {
        return value.isPresent() ? Double.toString(value.getAsDouble()) : "";
    }

    /** Input identity for one source dictionary. */
    private record InputSpec(StemmerPatchTrieLoader.Language language, String modelId,
            long expectedBaselineCommands, Path dictionary, Optional<Path> destination) {

        InputSpec {
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(modelId, "modelId");
            Objects.requireNonNull(dictionary, "dictionary");
            Objects.requireNonNull(destination, "destination");
            if (modelId.isBlank()) {
                throw new IllegalArgumentException("modelId must not be blank.");
            }
            if (expectedBaselineCommands <= MINIMUM_ANOMALOUS_COMMANDS) {
                throw new IllegalArgumentException("Expected baseline command count must exceed "
                        + MINIMUM_ANOMALOUS_COMMANDS + ".");
            }
        }

        static InputSpec parse(final String value) {
            final String[] fields = value.split("\\|", 5);
            if (fields.length != 5) {
                throw new IllegalArgumentException("Malformed dictionary audit specification: " + value);
            }
            return new InputSpec(StemmerPatchTrieLoader.Language.valueOf(fields[0]), fields[1],
                    Long.parseLong(fields[2]), Path.of(fields[3]),
                    "-".equals(fields[4]) ? Optional.empty() : Optional.of(Path.of(fields[4])));
        }
    }

    /** Mapping identity within one physical dictionary row. */
    record MappingKey(int lineNumber, String variant) {
        MappingKey {
            Objects.requireNonNull(variant, "variant");
        }
    }

    /** One candidate assignment during internal evidence construction. */
    private record Assignment(String stem, String variant, String command,
            int distance, boolean syntheticIdentity) {
    }

    /** Group identity for multiple forms assigned to the same alternative stem. */
    private record CandidateGroupKey(int lineNumber, String bestStem) {
    }

    /** Best internally observed assignment for one surface form. */
    record BestAssignment(String stem, String command, long commandCount,
            int distance, boolean syntheticIdentity) {
        BestAssignment {
            Objects.requireNonNull(stem, "stem");
            Objects.requireNonNull(command, "command");
        }
    }

    /** Audit action attached to an evidence row. */
    enum Decision {
        /** Excluded because another internal stem strongly dominates the assignment. */
        REMOVE_DOMINATED_ASSIGNMENT,
        /** Retained because one dominated mapping alone may represent valid ambiguity. */
        REVIEW_DOMINATED_MAPPING,
        /** Retained, but rare and divergent enough to warrant semantic review. */
        REVIEW_RARE_DIVERGENT
    }

    /** Human-review evidence for one mapping. */
    record Evidence(Decision decision, int lineNumber, String variant, String stem,
            String command, long commandCount, int distance, String bestStem,
            String bestCommand, long bestCommandCount, int bestDistance) {

        /** Returns this evidence with the supplied final audit decision. */
        Evidence withDecision(final Decision replacement) {
            return new Evidence(replacement, this.lineNumber, this.variant, this.stem,
                    this.command, this.commandCount, this.distance, this.bestStem,
                    this.bestCommand, this.bestCommandCount, this.bestDistance);
        }
    }

    /** Complete classifier output for one dictionary. */
    record Analysis(Set<MappingKey> removals, List<Evidence> evidence, long reviewCandidates) {
    }

    /** Objective source-sanitation action. */
    enum SanitationDecision {
        /** Reported a row rejected by the production parser because its stem contains whitespace. */
        PARSER_REJECT_WHITESPACE_ROW,
        /** Reported a variant rejected by the production parser because it contains whitespace. */
        PARSER_REJECT_WHITESPACE_VARIANT,
        /** Removed a row whose stem is an embedded non-lexical label. */
        REMOVE_INVALID_ROW,
        /** Removed an objectively non-lexical variant or misplaced export marker. */
        REMOVE_INVALID_VARIANT,
        /** Removed non-semantic Unicode format controls. */
        NORMALIZE_TOKEN,
        /** Retained a German legacy quote-plus-vowel spelling for separate review. */
        REVIEW_LEGACY_ENCODING
    }

    /** One reproducible source-sanitation action. */
    record SanitationEvidence(SanitationDecision decision, int lineNumber,
            String source, String replacement) {
    }

    /** Sanitized source rows and their aggregate change counters. */
    record SanitationResult(List<EditCostSensitivityApplication.DictionaryRow> rows,
            List<SanitationEvidence> evidence, long removedRows,
            long removedMappings, long normalizedTokens, long legacyEncodingReviews) {
    }

    /** Whitespace fields rejected by the production parser before dictionary compilation. */
    record WhitespaceResult(long ignoredRows, long ignoredVariants,
            List<SanitationEvidence> evidence) {
    }

    /** Exact output agreement counters. */
    private record ExactResult(long correct, long total, long changedCorrect, long changedTotal) {
    }

    /** Complete materialized result for one model. */
    private record AuditResult(InputSpec spec, int sourceRows, long sourceVariants,
            long sanitizedVariants, long filteredVariants,
            long originalCommandCount, long filteredCommandCount,
            long removedRows, long removedArtifactMappings, long normalizedTokens,
            long legacyEncodingReviews, long ignoredWhitespaceRows,
            long ignoredWhitespaceVariants, long removedMappings, long reviewCandidates,
            TrieStatistics statistics,
            ExactResult exact, QualityResult quality, List<Evidence> evidence,
            List<SanitationEvidence> sanitationEvidence) {
    }
}
