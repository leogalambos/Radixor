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
    private static final int ARGUMENT_COUNT = 5;
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
     *                  source state, and generator source path
     * @throws IOException when a model dictionary cannot be read or the report
     *                     cannot be written
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length != ARGUMENT_COUNT) {
            throw new IllegalArgumentException("Expected output CSV path, Radixor Java version, source revision, source state, and generator source path.");
        }
        final Path output = Path.of(arguments[0]);
        final String javaVersion = requireText(arguments[1], "Radixor Java version");
        final String sourceRevision = requireText(arguments[2], "source revision");
        final String sourceState = requireText(arguments[3], "source state");
        final String generatorSha256 = sha256(Files.readAllBytes(Path.of(arguments[4])));
        final List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
        for (StemmerPatchTrieLoader.Language language : StemmerPatchTrieLoader.Language.values()) {
            final StemmerModelDescriptor descriptor = registry.requireDefault(language);
            final List<DictionaryRow> dictionaryRows = readRows(descriptor);
            verifyProductionEquivalence(language, dictionaryRows);
            for (long seed : SEEDS) {
                final List<DictionaryRow> ranked = rankRows(dictionaryRows, descriptor.id(), seed);
                for (int percent = 100; percent >= 10; percent -= 10) {
                    final Result result = evaluate(language, descriptor, ranked, percent);
                    lines.add(result.toCsv(javaVersion, sourceRevision, sourceState, generatorSha256, seed));
                }
            }
            System.out.printf(Locale.ROOT, "Evaluated %s (%s): %,d rows.%n",
                    language, descriptor.id(), dictionaryRows.size());
        }
        final Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(output, lines, StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT, "Generalization CSV: %s (%d scenarios)%n",
                output.toAbsolutePath(), lines.size() - 1);
    }

    private static Result evaluate(final StemmerPatchTrieLoader.Language language,
            final StemmerModelDescriptor descriptor, final List<DictionaryRow> rankedRows, final int percent) {
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
        return new Result(language.name(), descriptor.id(), descriptor.version(), descriptor.sha256(), percent,
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
