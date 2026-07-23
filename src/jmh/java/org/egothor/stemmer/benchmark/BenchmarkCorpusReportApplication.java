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
package org.egothor.stemmer.benchmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import org.egothor.stemmer.CaseProcessingMode;
import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerDictionaryParser;
import org.egothor.stemmer.StemmerModelDescriptor;
import org.egothor.stemmer.StemmerModelRegistry;
import org.egothor.stemmer.StemmerPatchTrieLoader;

/**
 * Writes deterministic corpus and preferred patch-command counts for every
 * registered default model.
 *
 * <p>
 * This application performs setup-time analysis only; it does not publish or
 * interpret runtime performance. Optional model variants are excluded by
 * resolving every entry through
 * {@link StemmerModelRegistry#requireDefault(StemmerPatchTrieLoader.Language)}.
 * </p>
 */
public final class BenchmarkCorpusReportApplication {

    /**
     * Utility class.
     */
    private BenchmarkCorpusReportApplication() {
        throw new AssertionError("No instances.");
    }

    /**
     * Writes one UTF-8 CSV report.
     *
     * @param arguments one output-file path
     * @throws IOException if model discovery, dictionary parsing, trie loading, or
     *                     report writing fails
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one corpus-report output path.");
        }

        final Path output = Path.of(arguments[0]);
        final Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final StringBuilder csv = new StringBuilder(16_384);
        csv.append("Language,Model ID,Model version,Model SHA-256,Dictionary rows,Total tokens,Already-root tokens,Changed tokens,")
                .append("Speed timing tokens,All exact matches,Changed exact matches,Root preserved matches,")
                .append("Command class,Command count\n");
        final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
        for (StemmerPatchTrieLoader.Language language : StemmerPatchTrieLoader.Language.values()) {
            appendLanguage(csv, registry, language);
        }
        Files.writeString(output, csv, StandardCharsets.UTF_8);
        System.out.println("Benchmark corpus report: " + output.toAbsolutePath());
    }

    /**
     * Appends all command-class rows for one default model.
     *
     * @param csv      destination
     * @param registry discovered model registry
     * @param language language to analyze
     * @throws IOException if the model cannot be parsed or loaded
     */
    private static void appendLanguage(final StringBuilder csv, final StemmerModelRegistry registry,
            final StemmerPatchTrieLoader.Language language) throws IOException {
        final StemmerModelDescriptor descriptor = registry.requireDefault(language);
        final int dictionaryRows = countDictionaryRows(descriptor);
        final LanguageBenchmarkCorpus.Corpus corpus = LanguageBenchmarkCorpus.createFullCorpus(language);
        final String[] tokens = corpus.tokens();
        final String[] expectedRoots = corpus.expectedRoots();
        long alreadyRootTokens = 0;
        for (int index = 0; index < tokens.length; index++) {
            if (Objects.equals(tokens[index], expectedRoots[index])) {
                alreadyRootTokens++;
            }
        }
        final long changedTokens = tokens.length - alreadyRootTokens;
        final int timingTokens = LanguageBenchmarkCorpus.createChangedCorpus(language).tokens().length;

        final FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadCompiled(language, true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        final RadixorBenchmarkStemmer stemmer = new RadixorBenchmarkStemmer(trie);
        final Map<String, Long> commandCounts = new TreeMap<>();
        long allExactMatches = 0;
        long changedExactMatches = 0;
        long rootPreservedMatches = 0;
        for (int index = 0; index < tokens.length; index++) {
            final String token = tokens[index];
            final String expectedRoot = expectedRoots[index];
            final CompiledPatchCommand command = trie.getNormalizedString(token);
            final String commandClass = command == null ? "NoCommand" : command.getClass().getSimpleName();
            commandCounts.merge(commandClass, 1L, Math::addExact);
            final String actualRoot = stemmer.stem(token);
            if (Objects.equals(actualRoot, expectedRoot)) {
                allExactMatches++;
                if (Objects.equals(token, expectedRoot)) {
                    rootPreservedMatches++;
                } else {
                    changedExactMatches++;
                }
            }
        }
        for (Map.Entry<String, Long> commandCount : commandCounts.entrySet()) {
            csv.append(language).append(',')
                    .append(descriptor.id()).append(',')
                    .append(descriptor.version()).append(',')
                    .append(descriptor.sha256()).append(',')
                    .append(dictionaryRows).append(',')
                    .append(tokens.length).append(',')
                    .append(alreadyRootTokens).append(',')
                    .append(changedTokens).append(',')
                    .append(timingTokens).append(',')
                    .append(allExactMatches).append(',')
                    .append(changedExactMatches).append(',')
                    .append(rootPreservedMatches).append(',')
                    .append(commandCount.getKey()).append(',')
                    .append(commandCount.getValue()).append('\n');
        }
    }

    /**
     * Counts valid logical rows in one default dictionary.
     *
     * @param descriptor model descriptor
     * @return parsed dictionary-row count
     * @throws IOException if the dictionary cannot be opened or parsed
     */
    private static int countDictionaryRows(final StemmerModelDescriptor descriptor) throws IOException {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader classLoader = contextClassLoader == null
                ? BenchmarkCorpusReportApplication.class.getClassLoader()
                : contextClassLoader;
        final InputStream resource = classLoader.getResourceAsStream(descriptor.resource());
        if (resource == null) {
            throw new IOException("Dictionary resource is missing for model " + descriptor.id() + ": "
                    + descriptor.resource() + ".");
        }
        final int[] rows = {0};
        try (InputStream raw = resource;
                GZIPInputStream gzip = new GZIPInputStream(raw);
                BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
            StemmerDictionaryParser.parse(reader, descriptor.resource(), CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT,
                    (stem, variants, lineNumber) -> rows[0] = Math.addExact(rows[0], 1));
        }
        return rows[0];
    }
}
