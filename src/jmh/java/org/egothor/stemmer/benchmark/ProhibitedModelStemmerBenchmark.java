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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.egothor.stemmer.benchmark.snowball.SnowballStemmer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures dictionaries retained solely for a closed documentation benchmark.
 *
 * <p>This benchmark never discovers models from the production registry. The
 * guarded runner supplies both an exact identifier and a staged filesystem root;
 * trial setup rejects path traversal and missing inputs before compiling the trie.
 * Instances are confined to one JMH trial and are not thread-safe.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class ProhibitedModelStemmerBenchmark {

    /** Trial-confined state for one exact prohibited model. */
    @State(Scope.Benchmark)
    public static class ModelState {
        /** Exact model ID supplied by the closed runner. */
        @Param("__guarded_runner_must_supply_model_id__")
        public String modelId;

        private String[] tokens;
        private RadixorBenchmarkStemmer stemmer;
        private SnowballStemmer snowballStemmer;

        /**
         * Loads one staged dictionary without consulting the production registry.
         *
         * @throws IOException if the guarded input is missing or unreadable
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            if (this.modelId == null || !this.modelId.matches("[a-z]{2,3}(?:-[a-z]{2})?-default")) {
                throw new IllegalArgumentException("The prohibited benchmark requires one exact default model ID.");
            }
            final String rootProperty = System.getProperty("radixor.prohibited.modelRoot");
            if (rootProperty == null || rootProperty.isBlank()) {
                throw new IllegalStateException("The guarded prohibited-model root property is missing.");
            }
            final String manifestProperty = System.getProperty("radixor.prohibited.manifest");
            if (manifestProperty == null || manifestProperty.isBlank()) {
                throw new IllegalStateException("The guarded prohibited-model manifest property is missing.");
            }
            final Path root = Path.of(rootProperty).toAbsolutePath().normalize();
            final ProhibitedModelBenchmarkManifest.Entry entry = ProhibitedModelBenchmarkManifest.readEntry(
                    Path.of(manifestProperty).toAbsolutePath().normalize(), this.modelId);
            final Path dictionary = entry.dictionary();
            final Path expected = root.resolve(this.modelId).resolve("src/modelInput/stemmer.gz").normalize();
            if (!dictionary.equals(expected) || !dictionary.startsWith(root) || !Files.isRegularFile(dictionary)) {
                throw new IOException("The guarded prohibited dictionary is missing for " + this.modelId + ".");
            }
            this.tokens = LanguageBenchmarkCorpus.createTimingCorpus(dictionary).corpus().tokens();
            this.stemmer = new RadixorBenchmarkStemmer(StemmerPatchTrieLoader.loadCompiled(
                    dictionary, true, ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
            this.snowballStemmer = ProhibitedSnowballLanguageCase.find(this.modelId)
                    .map(ProhibitedSnowballLanguageCase::createStemmer)
                    .orElse(null);
        }
    }

    /**
     * Stems one model's canonical timing corpus.
     *
     * @param state exact model trial state
     * @param blackhole result sink
     */
    @Benchmark
    public void radixor(final ModelState state, final Blackhole blackhole) {
        final String[] tokens = state.tokens;
        final RadixorBenchmarkStemmer stemmer = state.stemmer;
        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }

    /**
     * Stems the canonical timing corpus with the exact official Snowball peer.
     *
     * <p>The guarded runner selects only model identifiers emitted by
     * {@link ProhibitedSnowballLanguageCatalogApplication}; other identifiers
     * fail before a measurement can be emitted.</p>
     *
     * @param state exact prohibited-model trial state
     * @param blackhole result sink
     */
    @Benchmark
    public void snowballDirect(final ModelState state, final Blackhole blackhole) {
        final SnowballStemmer snowball = state.snowballStemmer;
        if (snowball == null) {
            throw new IllegalStateException("No exact prohibited Snowball case exists for " + state.modelId + ".");
        }
        for (String token : state.tokens) {
            snowball.setCurrent(token);
            snowball.stem();
            blackhole.consume(snowball.getCurrent());
        }
    }
}
