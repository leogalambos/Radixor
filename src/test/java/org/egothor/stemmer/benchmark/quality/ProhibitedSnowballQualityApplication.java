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
package org.egothor.stemmer.benchmark.quality;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.egothor.stemmer.benchmark.ProhibitedModelBenchmarkManifest;
import org.egothor.stemmer.benchmark.ProhibitedSnowballLanguageCase;
import org.egothor.stemmer.benchmark.snowball.SnowballStemmer;

/** Produces pairwise quality evidence for exact prohibited Snowball peers. */
public final class ProhibitedSnowballQualityApplication {
    private ProhibitedSnowballQualityApplication() {
        throw new AssertionError("No instances.");
    }

    /**
     * Writes two processing-policy rows for every authoritative comparator.
     *
     * @param arguments guarded model manifest and output CSV paths
     * @throws IOException if an input cannot be read or output cannot be written
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected prohibited model manifest and output CSV paths.");
        }
        final List<ProhibitedModelBenchmarkManifest.Entry> entries =
                ProhibitedModelBenchmarkManifest.read(Path.of(arguments[0]));
        final List<QualityResult> results = new ArrayList<>();
        for (ProhibitedSnowballLanguageCase languageCase : ProhibitedSnowballLanguageCase.values()) {
            final ProhibitedModelBenchmarkManifest.Entry entry = find(entries, languageCase);
            final List<GoldStandardGroup> groups = BundledGoldStandardLoader.loadPath(
                    entry.dictionary(), entry.modelId());
            for (ProcessingMode mode : ProcessingMode.values()) {
                final SnowballStemmer stemmer = languageCase.createStemmer();
                results.add(QualityEvaluator.evaluate(
                        languageCase.candidate(), entry.language(), mode, groups,
                        token -> stem(stemmer, token)).withModelProvenance(
                                entry.modelId(), entry.modelVersion(), entry.modelSha256()));
            }
        }
        QualityReportWriter.writeCsv(Path.of(arguments[1]), results);
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

    private static String stem(final SnowballStemmer stemmer, final String token) {
        stemmer.setCurrent(token);
        stemmer.stem();
        return stemmer.getCurrent();
    }
}
