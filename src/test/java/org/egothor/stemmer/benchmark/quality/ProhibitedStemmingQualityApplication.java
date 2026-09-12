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

import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.egothor.stemmer.benchmark.ProhibitedModelBenchmarkManifest;

/**
 * Produces Radixor-only pairwise quality evidence for prohibited documentation models.
 *
 * <p>The application uses explicit filesystem inputs and never registers these
 * models. Models are processed sequentially; memory is proportional to the
 * largest current dictionary and its pairwise cover.</p>
 */
public final class ProhibitedStemmingQualityApplication {
    private ProhibitedStemmingQualityApplication() {
        throw new AssertionError("No instances.");
    }

    /**
     * Writes one machine-readable quality CSV.
     *
     * @param arguments input manifest and output CSV paths
     * @throws IOException if an input cannot be read or the report cannot be written
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected prohibited benchmark manifest and output CSV paths.");
        }
        final List<QualityResult> results = new ArrayList<>();
        for (ProhibitedModelBenchmarkManifest.Entry entry
                : ProhibitedModelBenchmarkManifest.read(Path.of(arguments[0]))) {
            final List<GoldStandardGroup> groups = BundledGoldStandardLoader.loadPath(
                    entry.dictionary(), entry.modelId());
            final FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadCompiled(
                    entry.dictionary(), true,
                    ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
            for (ProcessingMode mode : ProcessingMode.values()) {
                final QualityResult result = QualityEvaluator.evaluate(
                        entry.language() + "_RADIXOR", entry.language(), mode, groups,
                        token -> stem(trie, token)).withModelProvenance(
                                entry.modelId(), entry.modelVersion(), entry.modelSha256());
                results.add(result);
            }
        }
        QualityReportWriter.writeCsv(Path.of(arguments[1]), results);
        System.out.println("Prohibited stemming-quality CSV: " + Path.of(arguments[1]).toAbsolutePath());
    }

    private static String stem(final FrequencyTrie<CompiledPatchCommand> trie, final String token) {
        final CompiledPatchCommand command = trie.getNormalizedString(token);
        return command == null || command.preservesAllSources() ? token : command.apply(token);
    }
}
