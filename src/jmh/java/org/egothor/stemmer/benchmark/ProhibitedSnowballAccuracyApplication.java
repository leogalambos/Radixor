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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.egothor.stemmer.benchmark.snowball.SnowballStemmer;

/** Produces exact-root evidence for prohibited models with an official Snowball peer. */
public final class ProhibitedSnowballAccuracyApplication {
    private ProhibitedSnowballAccuracyApplication() {
        throw new AssertionError("No instances.");
    }

    /**
     * Writes exact raw numerators and denominators for the closed comparator set.
     *
     * @param arguments guarded model manifest and output CSV paths
     * @throws IOException if input cannot be read or output cannot be written
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected prohibited model manifest and output CSV paths.");
        }
        final List<ProhibitedModelBenchmarkManifest.Entry> entries =
                ProhibitedModelBenchmarkManifest.read(Path.of(arguments[0]));
        final StringBuilder output = new StringBuilder(512);
        output.append("Candidate,Language,Dictionary model ID,Dictionary model version,")
                .append("Dictionary model SHA-256,Total tokens,Already-root tokens,Changed tokens,")
                .append("All exact matches,Changed exact matches,Root preserved matches\n");
        for (ProhibitedSnowballLanguageCase languageCase : ProhibitedSnowballLanguageCase.values()) {
            final ProhibitedModelBenchmarkManifest.Entry entry = find(entries, languageCase);
            final LanguageBenchmarkCorpus.Corpus corpus =
                    LanguageBenchmarkCorpus.createFullCorpus(entry.dictionary());
            final SnowballStemmer stemmer = languageCase.createStemmer();
            long alreadyRoot = 0L;
            long allExact = 0L;
            long changedExact = 0L;
            long rootPreserved = 0L;
            final String[] tokens = corpus.tokens();
            final String[] roots = corpus.expectedRoots();
            for (int index = 0; index < tokens.length; index++) {
                final boolean changed = !Objects.equals(tokens[index], roots[index]);
                if (!changed) {
                    alreadyRoot++;
                }
                stemmer.setCurrent(tokens[index]);
                stemmer.stem();
                if (Objects.equals(stemmer.getCurrent(), roots[index])) {
                    allExact++;
                    if (changed) {
                        changedExact++;
                    } else {
                        rootPreserved++;
                    }
                }
            }
            output.append(languageCase.candidate()).append(',').append(entry.language()).append(',')
                    .append(entry.modelId()).append(',').append(entry.modelVersion()).append(',')
                    .append(entry.modelSha256()).append(',').append(tokens.length).append(',')
                    .append(alreadyRoot).append(',').append(tokens.length - alreadyRoot).append(',')
                    .append(allExact).append(',').append(changedExact).append(',')
                    .append(rootPreserved).append('\n');
        }
        final Path outputPath = Path.of(arguments[1]);
        final Path parent = outputPath.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, output, StandardCharsets.UTF_8);
    }

    private static ProhibitedModelBenchmarkManifest.Entry find(
            final List<ProhibitedModelBenchmarkManifest.Entry> entries,
            final ProhibitedSnowballLanguageCase languageCase) {
        for (ProhibitedModelBenchmarkManifest.Entry entry : entries) {
            if (entry.modelId().equals(languageCase.modelId())
                    && entry.language().equals(languageCase.language())) {
                return entry;
            }
        }
        throw new IllegalStateException(
                "Guarded prohibited manifest omits exact Snowball case " + languageCase.modelId() + ".");
    }
}
