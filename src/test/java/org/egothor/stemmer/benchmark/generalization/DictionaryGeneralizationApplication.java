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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import org.egothor.stemmer.WordTraversalDirection;

/**
 * Produces the deterministic all-language dictionary-family generalization
 * report used by the documentation.
 *
 * <p>Each split ranks complete dictionary rows with a frozen seeded hash. The
 * first requested percentage is used for training, making the percentages
 * exact-size and nested within a seed. Evaluation reports both the complete
 * dictionary and withheld rows. Its primary unseen-surface scope additionally
 * excludes a withheld occurrence when the same normalized surface form also
 * occurs in training.</p>
 */
public final class DictionaryGeneralizationApplication {
    /** Version of the frozen split and report protocol. */
    static final String PROTOCOL_VERSION = "radixor-generalization-v1";
    /** Fixed seeds declared before evaluating the result. */
    static final long[] SEEDS = {
        0x000000009e3779b1L, 0x9e3779b97f4a7c15L,
        0x61c8864680b583ebL, 0x243f6a8885a308d3L,
        0xd1b54a32d192ed03L
    };
    private static final int REQUIRED_ARGUMENT_COUNT = 5;
    private static final String EXACT_MODEL_ARGUMENT = "--exact-model";
    private static final String GUARDED_MODEL_ARGUMENT = "--guarded-model";
    private static final int GUARDED_ARGUMENT_COUNT = 11;
    private static final int IMMUTABLE_STANDARD_DEFAULT_COUNT = 20;
    private static final String HEADER = String.join(",",
            "protocol_version", "radixor_java_version", "source_revision", "source_state",
            "generator_sha256", "language", "model_id", "model_version", "model_sha256",
            "seed", "requested_percent", "selected_rows", "total_rows",
            "withheld_rows", "whole_correct", "whole_total", "whole_changed_correct",
            "whole_changed_total", "whole_root_correct", "whole_root_total", "withheld_correct",
            "withheld_total", "withheld_changed_correct", "withheld_changed_total",
            "withheld_root_correct", "withheld_root_total", "unseen_correct", "unseen_total",
            "unseen_changed_correct", "unseen_changed_total", "unseen_root_correct",
            "unseen_root_total", "excluded_overlap_occurrences");

    private DictionaryGeneralizationApplication() {
        throw new AssertionError("No instances.");
    }

    /**
     * Generates the complete report.
     *
     * @param arguments output CSV path, Radixor Java version, source revision,
     *                  source state, generator source path, and optionally a
     *                  UTF-8 file containing one selected default model ID per line,
     *                  or {@code --exact-model} followed by one default model ID.
     *                  The closed prohibited-model runner may instead append
     *                  {@code --guarded-model}, model ID, language, version,
     *                  SHA-256, and dictionary path.
     * @throws IOException when a model dictionary cannot be read or the report
     *                     cannot be written
     */
    public static void main(final String[] arguments) throws IOException {
        final boolean guarded = arguments.length == GUARDED_ARGUMENT_COUNT
                && GUARDED_MODEL_ARGUMENT.equals(arguments[5]);
        if (!guarded && (arguments.length < REQUIRED_ARGUMENT_COUNT
                || arguments.length > REQUIRED_ARGUMENT_COUNT + 2)) {
            throw new IllegalArgumentException("Expected output CSV path, Radixor Java version, source revision, source state, generator source path, and optionally a selected-default model file or --exact-model plus one default model ID.");
        }
        final Path output = Path.of(arguments[0]);
        final String javaVersion = requireText(arguments[1], "Radixor Java version");
        final String sourceRevision = requireText(arguments[2], "source revision");
        final String sourceState = requireText(arguments[3], "source state");
        final String generatorSha256 = sha256(Files.readAllBytes(Path.of(arguments[4])));
        final List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        if (guarded) {
            final GuardedModel model = new GuardedModel(arguments[6], arguments[7], arguments[8],
                    arguments[9], Path.of(arguments[10]));
            final List<DictionaryRow> dictionaryRows = readRows(model.dictionary(), model.sha256());
            verifyProductionEquivalence(model.dictionary(), model.language(), dictionaryRows);
            appendResults(lines, model.language(), model.id(), model.version(), model.sha256(),
                    dictionaryRows, javaVersion, sourceRevision, sourceState, generatorSha256);
            writeReport(output, lines);
            return;
        }
        final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
        final List<StemmerModelDescriptor> descriptors;
        if (arguments.length == REQUIRED_ARGUMENT_COUNT) {
            descriptors = allDefaultDescriptors(registry);
        } else if (arguments.length == REQUIRED_ARGUMENT_COUNT + 1) {
            descriptors = selectedStandaloneDescriptors(registry, Path.of(arguments[5]));
        } else if (EXACT_MODEL_ARGUMENT.equals(arguments[5])) {
            descriptors = List.of(exactDefaultDescriptor(registry, arguments[6]));
        } else {
            throw new IllegalArgumentException("A seven-argument invocation must use --exact-model.");
        }
        for (StemmerModelDescriptor descriptor : descriptors) {
            final StemmerPatchTrieLoader.Language language = descriptor.language();
            final List<DictionaryRow> dictionaryRows = readRows(descriptor);
            verifyProductionEquivalence(language, dictionaryRows);
            appendResults(lines, language.name(), descriptor.id(), descriptor.version(), descriptor.sha256(),
                    dictionaryRows, javaVersion, sourceRevision, sourceState, generatorSha256);
            System.out.printf(Locale.ROOT, "Evaluated %s (%s): %,d rows.%n",
                    language, descriptor.id(), dictionaryRows.size());
        }
        writeReport(output, lines);
    }

    private static void appendResults(final List<String> lines, final String language, final String modelId,
            final String modelVersion, final String modelSha256, final List<DictionaryRow> dictionaryRows,
            final String javaVersion, final String sourceRevision, final String sourceState,
            final String generatorSha256) {
        for (long seed : SEEDS) {
            final List<DictionaryRow> ranked = rankRows(dictionaryRows, modelId, seed);
            for (int percent = 100; percent >= 10; percent -= 10) {
                final Result result = evaluate(language, modelId, modelVersion, modelSha256, ranked, percent);
                lines.add(result.toCsv(javaVersion, sourceRevision, sourceState, generatorSha256, seed));
            }
        }
    }

    private static void writeReport(final Path output, final List<String> lines) throws IOException {
        final Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(output, lines, StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT, "Generalization CSV: %s (%d scenarios)%n",
                output.toAbsolutePath(), lines.size() - 1);
    }

    /** Returns every authoritative default in stable language-enum order. */
    static List<StemmerModelDescriptor> allDefaultDescriptors(final StemmerModelRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        final List<StemmerModelDescriptor> descriptors = new ArrayList<>(StemmerPatchTrieLoader.Language.values().length);
        for (StemmerPatchTrieLoader.Language language : StemmerPatchTrieLoader.Language.values()) {
            descriptors.add(registry.requireDefault(language));
        }
        return List.copyOf(descriptors);
    }

    /** Returns one exact authoritative default for a targeted reproducibility run. */
    static StemmerModelDescriptor exactDefaultDescriptor(final StemmerModelRegistry registry,
            final String modelId) {
        Objects.requireNonNull(registry, "registry");
        final String requested = Objects.requireNonNull(modelId, "modelId").strip();
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("The exact generalization model ID must not be blank.");
        }
        final StemmerModelDescriptor descriptor = registry.require(requested);
        final StemmerModelDescriptor authoritative = registry.requireDefault(descriptor.language());
        if (!authoritative.id().equals(requested)) {
            throw new IllegalArgumentException("The exact generalization model must be an authoritative default: "
                    + requested + ".");
        }
        return descriptor;
    }

    /**
     * Reads and validates the exact standalone-default selection produced from
     * the build topology. Blank lines and duplicate or non-default IDs are
     * rejected so a partial measurement cannot be mistaken for the approved
     * topology-selected standalone continuation.
     */
    static List<StemmerModelDescriptor> selectedStandaloneDescriptors(final StemmerModelRegistry registry,
            final Path selectionFile) throws IOException {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(selectionFile, "selectionFile");
        final List<StemmerModelDescriptor> defaults = allDefaultDescriptors(registry);
        final int expectedCount = defaults.size() - IMMUTABLE_STANDARD_DEFAULT_COUNT;
        final List<String> modelIds = Files.readAllLines(selectionFile, StandardCharsets.UTF_8);
        if (modelIds.size() != expectedCount || modelIds.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("The standalone selector must contain exactly "
                    + expectedCount + " non-blank model IDs.");
        }
        final Set<String> selected = new HashSet<>(modelIds);
        if (selected.size() != expectedCount) {
            throw new IllegalArgumentException("The standalone selector contains duplicate model IDs.");
        }
        final Map<String, StemmerModelDescriptor> byId = new HashMap<>(defaults.size() * 2);
        for (StemmerModelDescriptor descriptor : defaults) {
            byId.put(descriptor.id(), descriptor);
        }
        final Set<String> unknown = new HashSet<>(selected);
        unknown.removeAll(byId.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("The standalone selector contains non-default model IDs: " + unknown);
        }
        final List<StemmerModelDescriptor> result = new ArrayList<>(expectedCount);
        for (StemmerModelDescriptor descriptor : defaults) {
            if (selected.contains(descriptor.id())) {
                result.add(descriptor);
            }
        }
        return List.copyOf(result);
    }

    private static Result evaluate(final StemmerPatchTrieLoader.Language language,
            final StemmerModelDescriptor descriptor, final List<DictionaryRow> rankedRows, final int percent) {
        return evaluate(language.name(), descriptor.id(), descriptor.version(), descriptor.sha256(),
                rankedRows, percent);
    }

    private static Result evaluate(final String language, final String modelId, final String modelVersion,
            final String modelSha256, final List<DictionaryRow> rankedRows, final int percent) {
        final int selectedCount = percent == 100 ? rankedRows.size()
                : Math.max(1, (rankedRows.size() * percent + 50) / 100);
        final List<DictionaryRow> selectedRows = rankedRows.subList(0, selectedCount);
        final Set<Integer> selectedLineNumbers = new HashSet<>(selectedCount * 2);
        final Set<String> trainingForms = new HashSet<>();
        for (DictionaryRow row : selectedRows) {
            selectedLineNumbers.add(row.lineNumber());
            trainingForms.add(row.stem());
            for (String variant : row.variants()) {
                trainingForms.add(variant);
            }
        }

        final FrequencyTrie<CompiledPatchCommand> trie = buildCompiledTrie(selectedRows);
        Counts whole = Counts.empty();
        Counts withheld = Counts.empty();
        Counts unseen = Counts.empty();
        long excluded = 0L;
        for (DictionaryRow row : rankedRows) {
            final boolean selected = selectedLineNumbers.contains(row.lineNumber());
            final List<String> forms = row.forms();
            for (String form : forms) {
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
        return new Result(language, modelId, modelVersion, modelSha256, percent,
                selectedCount, rankedRows.size(), rankedRows.size() - selectedCount,
                whole, withheld, unseen, excluded);
    }

    private static FrequencyTrie<CompiledPatchCommand> buildCompiledTrie(final List<DictionaryRow> rows) {
        final WordTraversalDirection direction = WordTraversalDirection.BACKWARD;
        final ReductionSettings settings = new ReductionSettings(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS,
                ReductionSettings.DEFAULT_DOMINANT_WINNER_MIN_PERCENT,
                ReductionSettings.DEFAULT_DOMINANT_WINNER_OVER_SECOND_RATIO, true);
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new, settings, direction);
        final PatchCommandEncoder encoder = PatchCommandEncoder.builder().traversalDirection(direction).build();
        for (DictionaryRow row : rows) {
            builder.put(row.stem(), encoder.encode(row.stem(), row.stem()));
            for (String variant : row.variants()) {
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

    private static void verifyProductionEquivalence(final StemmerPatchTrieLoader.Language language,
            final List<DictionaryRow> rows) throws IOException {
        final FrequencyTrie<CompiledPatchCommand> experiment = buildCompiledTrie(rows);
        final FrequencyTrie<CompiledPatchCommand> production = StemmerPatchTrieLoader.loadCompiled(language, true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        for (DictionaryRow row : rows) {
            if (!Objects.equals(stem(experiment, row.stem()), stem(production, row.stem()))) {
                throw new IllegalStateException("Full-coverage experiment differs from production for " + language
                        + " root " + row.stem() + '.');
            }
            for (String variant : row.variants()) {
                if (!Objects.equals(stem(experiment, variant), stem(production, variant))) {
                    throw new IllegalStateException("Full-coverage experiment differs from production for "
                            + language + " form " + variant + '.');
                }
            }
        }
    }

    private static void verifyProductionEquivalence(final Path dictionary, final String language,
            final List<DictionaryRow> rows) throws IOException {
        final FrequencyTrie<CompiledPatchCommand> experiment = buildCompiledTrie(rows);
        final FrequencyTrie<CompiledPatchCommand> production = StemmerPatchTrieLoader.loadCompiled(dictionary, true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        verifyEquivalent(language, rows, experiment, production);
    }

    private static void verifyEquivalent(final String language, final List<DictionaryRow> rows,
            final FrequencyTrie<CompiledPatchCommand> experiment,
            final FrequencyTrie<CompiledPatchCommand> production) {
        for (DictionaryRow row : rows) {
            if (!Objects.equals(stem(experiment, row.stem()), stem(production, row.stem()))) {
                throw new IllegalStateException("Full-coverage experiment differs from production for " + language
                        + " root " + row.stem() + '.');
            }
            for (String variant : row.variants()) {
                if (!Objects.equals(stem(experiment, variant), stem(production, variant))) {
                    throw new IllegalStateException("Full-coverage experiment differs from production for "
                            + language + " form " + variant + '.');
                }
            }
        }
    }

    private static String stem(final FrequencyTrie<CompiledPatchCommand> trie, final String token) {
        final CompiledPatchCommand patch = trie.getNormalizedString(token);
        return patch == null || patch.preservesAllSources() ? token : patch.apply(token);
    }

    private static List<DictionaryRow> readRows(final StemmerModelDescriptor descriptor) throws IOException {
        final InputStream resource = StemmerPatchTrieLoader.class.getClassLoader().getResourceAsStream(
                descriptor.resource());
        if (resource == null) {
            throw new IllegalStateException("Missing bundled dictionary resource " + descriptor.resource() + '.');
        }
        final byte[] compressed;
        try (InputStream input = resource) {
            compressed = input.readAllBytes();
        }
        verifySha256(compressed, descriptor.sha256(), descriptor.resource());
        final List<DictionaryRow> rows = new ArrayList<>();
        try (ByteArrayInputStream input = new ByteArrayInputStream(compressed);
                GZIPInputStream gzip = new GZIPInputStream(input);
                InputStreamReader streamReader = new InputStreamReader(gzip, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader)) {
            StemmerDictionaryParser.parse(reader, descriptor.resource(), (stem, variants, lineNumber) ->
                    rows.add(new DictionaryRow(lineNumber, stem, variants)));
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("No dictionary rows were parsed from " + descriptor.resource() + '.');
        }
        return List.copyOf(rows);
    }

    private static List<DictionaryRow> readRows(final Path dictionary, final String expectedSha256)
            throws IOException {
        verifySha256(dictionary, expectedSha256);
        final List<DictionaryRow> rows = new ArrayList<>();
        try (InputStream input = Files.newInputStream(dictionary);
                GZIPInputStream gzip = new GZIPInputStream(input);
                InputStreamReader streamReader = new InputStreamReader(gzip, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader)) {
            StemmerDictionaryParser.parse(reader, dictionary.toString(), (stem, variants, lineNumber) ->
                    rows.add(new DictionaryRow(lineNumber, stem, variants)));
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("No dictionary rows were parsed from " + dictionary + '.');
        }
        return List.copyOf(rows);
    }

    private static void verifySha256(final Path path, final String expected) throws IOException {
        final String required = requireText(expected, "expected SHA-256");
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
        try (DigestInputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        final String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(required)) {
            throw new IllegalStateException("SHA-256 mismatch for " + path + ": expected " + required
                    + " but read " + actual + '.');
        }
    }

    static void verifySha256(final byte[] content, final String expected, final String label) {
        Objects.requireNonNull(content, "content");
        final String required = requireText(expected, "expected SHA-256");
        final String actual = sha256(content);
        if (!actual.equals(required)) {
            throw new IllegalStateException("SHA-256 mismatch for " + label + ": expected " + required
                    + " but read " + actual + '.');
        }
    }

    private static String sha256(final byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static List<DictionaryRow> rankRows(final List<DictionaryRow> rows, final String modelId,
            final long seed) {
        final List<DictionaryRow> ranked = new ArrayList<>(rows);
        ranked.sort(Comparator.comparingLong((DictionaryRow row) -> rank(row, modelId, seed))
                .thenComparingInt(DictionaryRow::lineNumber));
        return ranked;
    }

    private static long rank(final DictionaryRow row, final String modelId, final long seed) {
        long hash = 0xcbf29ce484222325L ^ seed;
        hash = mix(hash, PROTOCOL_VERSION);
        hash = mix(hash, modelId);
        hash = mix(hash, row.stem());
        for (String variant : row.variants()) {
            hash = mix(hash, variant);
        }
        return hash;
    }

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

    private static String requireText(final String value, final String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        return value.strip();
    }

    private record DictionaryRow(int lineNumber, String stem, String[] variants) {
        DictionaryRow {
            Objects.requireNonNull(stem, "stem");
            variants = variants.clone();
        }

        @Override
        public String[] variants() {
            return this.variants.clone();
        }

        List<String> forms() {
            final List<String> forms = new ArrayList<>(this.variants.length + 1);
            forms.add(this.stem);
            for (String variant : this.variants) {
                forms.add(variant);
            }
            return forms;
        }
    }

    private record GuardedModel(String id, String language, String version, String sha256, Path dictionary) {
        GuardedModel {
            id = requireText(id, "guarded model ID");
            language = requireText(language, "guarded language");
            version = requireText(version, "guarded model version");
            sha256 = requireText(sha256, "guarded model SHA-256");
            Objects.requireNonNull(dictionary, "dictionary");
            dictionary = dictionary.toAbsolutePath().normalize();
            if (!id.matches("[a-z]{2,3}(?:-[a-z]{2})?-default")
                    || !language.matches("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*")
                    || !sha256.matches("[0-9a-f]{64}") || !Files.isRegularFile(dictionary)) {
                throw new IllegalArgumentException("Invalid guarded generalization model identity for " + id + '.');
            }
        }
    }

    private record Counts(long correct, long total, long changedCorrect, long changedTotal,
            long rootCorrect, long rootTotal) {
        static Counts empty() {
            return new Counts(0L, 0L, 0L, 0L, 0L, 0L);
        }

        Counts add(final String token, final String root, final boolean exact) {
            final boolean changed = !Objects.equals(token, root);
            return new Counts(this.correct + (exact ? 1L : 0L), this.total + 1L,
                    this.changedCorrect + (changed && exact ? 1L : 0L),
                    this.changedTotal + (changed ? 1L : 0L),
                    this.rootCorrect + (!changed && exact ? 1L : 0L),
                    this.rootTotal + (!changed ? 1L : 0L));
        }

        String csv() {
            return this.correct + "," + this.total + "," + this.changedCorrect + "," + this.changedTotal
                    + "," + this.rootCorrect + "," + this.rootTotal;
        }
    }

    private record Result(String language, String modelId, String modelVersion, String modelSha256,
            int requestedPercent, int selectedRows, int totalRows, int withheldRows, Counts whole,
            Counts withheld, Counts unseen, long excludedOverlapOccurrences) {
        String toCsv(final String javaVersion, final String sourceRevision, final String sourceState,
                final String generatorSha256, final long seed) {
            return String.join(",", PROTOCOL_VERSION, javaVersion, sourceRevision, sourceState,
                    generatorSha256, this.language, this.modelId, this.modelVersion, this.modelSha256,
                    Long.toUnsignedString(seed),
                    Integer.toString(this.requestedPercent), Integer.toString(this.selectedRows),
                    Integer.toString(this.totalRows), Integer.toString(this.withheldRows),
                    this.whole.csv(), this.withheld.csv(), this.unseen.csv(),
                    Long.toString(this.excludedOverlapOccurrences));
        }
    }
}
