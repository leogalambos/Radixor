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
 *******************************************************************************/
package org.egothor.stemmer.benchmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import org.egothor.stemmer.CaseProcessingMode;
import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerDictionaryParser;
import org.egothor.stemmer.StemmerPatchTrieLoader;

/**
 * Writes corpus and exact-root evidence for the closed prohibited-model cohort.
 *
 * <p>Models are read from verified filesystem paths and never registered with
 * the production model registry. Processing is sequential and retains at most
 * the unique forms and corpus of the current model.</p>
 */
public final class ProhibitedBenchmarkCorpusReportApplication {
    private ProhibitedBenchmarkCorpusReportApplication() {
        throw new AssertionError("No instances.");
    }

    /**
     * Writes the prohibited corpus CSV.
     *
     * @param arguments input manifest and output CSV paths
     * @throws IOException if an input cannot be read or the report cannot be written
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected prohibited benchmark manifest and output CSV paths.");
        }
        final Path output = Path.of(arguments[1]);
        final Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        final StringBuilder csv = new StringBuilder(16_384);
        csv.append("Language,Model ID,Model version,Model SHA-256,Dictionary rows,Distinct usable forms,Total tokens,Already-root tokens,Changed tokens,")
                .append("Speed timing workload,Speed timing tokens,All exact matches,Changed exact matches,Root preserved matches,")
                .append("Command class,Command count\n");
        for (ProhibitedModelBenchmarkManifest.Entry entry
                : ProhibitedModelBenchmarkManifest.read(Path.of(arguments[0]))) {
            appendModel(csv, entry);
        }
        Files.writeString(output, csv, StandardCharsets.UTF_8);
        System.out.println("Prohibited benchmark corpus report: " + output.toAbsolutePath());
    }

    private static void appendModel(final StringBuilder csv,
            final ProhibitedModelBenchmarkManifest.Entry entry) throws IOException {
        final CorpusStatistics statistics = countDictionary(entry.dictionary());
        final LanguageBenchmarkCorpus.Corpus corpus = LanguageBenchmarkCorpus.createFullCorpus(entry.dictionary());
        final LanguageBenchmarkCorpus.TimingCorpus timing = LanguageBenchmarkCorpus.createTimingCorpus(entry.dictionary());
        final String[] tokens = corpus.tokens();
        final String[] roots = corpus.expectedRoots();
        final FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadCompiled(entry.dictionary(), true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        final RadixorBenchmarkStemmer stemmer = new RadixorBenchmarkStemmer(trie);
        final Map<String, Long> commandCounts = new TreeMap<>();
        long rootTokens = 0L;
        long allExact = 0L;
        long changedExact = 0L;
        long rootExact = 0L;
        for (int index = 0; index < tokens.length; index++) {
            final boolean changed = !Objects.equals(tokens[index], roots[index]);
            if (!changed) {
                rootTokens++;
            }
            final CompiledPatchCommand command = trie.getNormalizedString(tokens[index]);
            commandCounts.merge(command == null ? "NoCommand" : command.getClass().getSimpleName(), 1L, Math::addExact);
            if (Objects.equals(stemmer.stem(tokens[index]), roots[index])) {
                allExact++;
                if (changed) {
                    changedExact++;
                } else {
                    rootExact++;
                }
            }
        }
        for (Map.Entry<String, Long> commandCount : commandCounts.entrySet()) {
            csv.append(entry.language()).append(',').append(entry.modelId()).append(',')
                    .append(entry.modelVersion()).append(',').append(entry.modelSha256()).append(',')
                    .append(statistics.rows()).append(',').append(statistics.distinctForms()).append(',')
                    .append(tokens.length).append(',').append(rootTokens).append(',')
                    .append(tokens.length - rootTokens).append(',').append(timing.basis().reportValue()).append(',')
                    .append(timing.corpus().tokens().length).append(',').append(allExact).append(',')
                    .append(changedExact).append(',').append(rootExact).append(',')
                    .append(commandCount.getKey()).append(',').append(commandCount.getValue()).append('\n');
        }
    }

    private static CorpusStatistics countDictionary(final Path dictionary) throws IOException {
        final int[] rows = {0};
        final Set<String> forms = new HashSet<>();
        try (InputStream input = Files.newInputStream(dictionary);
                GZIPInputStream gzip = new GZIPInputStream(input);
                BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
            StemmerDictionaryParser.parse(reader, dictionary.toString(), CaseProcessingMode.AS_IS,
                    (stem, variants, lineNumber) -> {
                        rows[0] = Math.addExact(rows[0], 1);
                        forms.add(stem);
                        for (String variant : variants) {
                            forms.add(variant);
                        }
                    });
        }
        return new CorpusStatistics(rows[0], forms.size());
    }

    private record CorpusStatistics(int rows, int distinctForms) {
    }
}
