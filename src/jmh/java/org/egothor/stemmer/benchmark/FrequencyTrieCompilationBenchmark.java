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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
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
 * Benchmarks end-to-end dictionary compilation for different reduction modes.
 *
 * <p>
 * This benchmark measures the offline path that matters for dictionary build
 * workflows: dictionary parsing, patch-command generation, mutable trie
 * population, subtree reduction, and freezing into the compiled read-only trie.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class FrequencyTrieCompilationBenchmark {

    /**
     * Shared benchmark state for compilation scenarios.
     */
    @State(Scope.Benchmark)
    public static class CompilationState {

        /**
         * Number of canonical stems to generate.
         */
        @Param({ "2000", "10000" })
        public int stemCount;

        /**
         * Reduction mode used during trie compilation.
         */
        @Param({
                "MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS",
                "MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS",
                "MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS"
        })
        public String reductionMode;

        /**
         * Whether to store the stem itself using the canonical no-op patch.
         */
        @Param({ "true", "false" })
        public boolean storeOriginalStem;

        /**
         * Full dictionary text used as the benchmark input.
         */
        private String dictionaryText;

        /**
         * Initializes the benchmark state.
         */
        @Setup(Level.Trial)
        public void setUp() {
            this.dictionaryText = BenchmarkCorpusSupport.createCorpus(this.stemCount).dictionaryText();
        }
    }

    /**
     * Measures end-to-end patch trie compilation latency.
     *
     * @param state prepared compilation state
     * @param blackhole sink preventing dead-code elimination
     * @throws IOException if dictionary parsing fails
     */
    @Benchmark
    public void compilePatchTrie(final CompilationState state, final Blackhole blackhole) throws IOException {
        final ReductionSettings settings =
                ReductionSettings.withDefaults(ReductionMode.valueOf(state.reductionMode));
        blackhole.consume(
                BenchmarkCorpusSupport.compilePatchTrie(
                        state.dictionaryText,
                        settings,
                        state.storeOriginalStem));
    }
}
