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

import org.egothor.stemmer.PatchCommandEncoder;
import org.egothor.stemmer.StemmerModelDescriptor;
import org.egothor.stemmer.StemmerModelRegistry;
import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.egothor.stemmer.WordTraversalDirection;

/**
 * Writes the patch commands generated from one bundled dictionary to a UTF-8
 * TSV report.
 *
 * <p>The report includes the baseline-cost command for every stem identity pair
 * and every variant-to-stem pair. Each command row has the following shape:</p>
 *
 * <pre>
 * command TAB count TAB example_source TAB example_target
 * </pre>
 *
 * <p>Rows are ordered by descending occurrence count and then by command. The
 * application creates the output parent directory when necessary and replaces
 * an existing output file. It has no mutable global state, so invocations that
 * target different files may run concurrently.</p>
 *
 * <p>Usage: {@code PatchCommandDumpApplication output-tsv language-name}</p>
 */
public final class PatchCommandDumpApplication {

    /** Utility class. */
    private PatchCommandDumpApplication() {
        throw new AssertionError("No instances.");
    }

    /**
     * Generates a patch-command frequency report for one bundled language.
     *
     * @param arguments output TSV path followed by an exact
     *                  {@link StemmerPatchTrieLoader.Language} name
     * @throws IOException if the dictionary or output file cannot be read or
     *                     written
     * @throws IllegalArgumentException if the argument count or language name is
     *                                  invalid
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected: output-tsv language-name");
        }
        final Path output = Path.of(arguments[0]);
        final String languageFilter = arguments[1].toUpperCase(Locale.ROOT);

        StemmerPatchTrieLoader.Language language = null;
        for (final StemmerPatchTrieLoader.Language candidate : StemmerPatchTrieLoader.Language.values()) {
            if (candidate.name().equals(languageFilter)) {
                language = candidate;
                break;
            }
        }
        if (language == null) {
            throw new IllegalArgumentException("No language matches: " + languageFilter);
        }

        final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
        final StemmerModelDescriptor descriptor = registry.requireDefault(language);
        final List<EditCostSensitivityApplication.DictionaryRow> rows =
                EditCostSensitivityApplication.readRows(descriptor);

        final PatchCommandEncoder encoder = PatchCommandEncoder.builder()
                .traversalDirection(WordTraversalDirection.BACKWARD)
                .deleteCost(1)
                .insertCost(1)
                .replaceCost(1)
                .matchCost(0)
                .build();

        final Map<String, Long> counts = new LinkedHashMap<>();
        final Map<String, String[]> examples = new LinkedHashMap<>();

        long totalPairs = 0L;

        for (final EditCostSensitivityApplication.DictionaryRow row : rows) {
            final String stemCommand = encoder.encode(row.stem(), row.stem());
            counts.merge(stemCommand, 1L, Long::sum);
            examples.putIfAbsent(stemCommand, new String[] { row.stem(), row.stem() });

            for (final String variant : row.variants()) {
                final String command = encoder.encode(variant, row.stem());
                counts.merge(command, 1L, Long::sum);
                examples.putIfAbsent(command, new String[] { variant, row.stem() });
                totalPairs++;
            }
        }

        final List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));

        final Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final List<String> lines = new ArrayList<>(sorted.size() + 2);
        lines.add("# language=" + language + " model=" + descriptor.id()
                + " total_variant_pairs=" + totalPairs
                + " distinct_commands=" + sorted.size());
        lines.add("command\tcount\texample_source\texample_target");
        for (final Map.Entry<String, Long> entry : sorted) {
            final String[] example = examples.get(entry.getKey());
            lines.add(entry.getKey() + "\t" + entry.getValue()
                    + "\t" + example[0] + "\t" + example[1]);
        }

        Files.write(output, lines, StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT,
                "Dumped %d distinct patch commands (from %,d variant pairs) to %s%n",
                sorted.size(), totalPairs, output.toAbsolutePath());
    }
}
