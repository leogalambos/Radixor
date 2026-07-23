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

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.morfologik.MorfologikFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Compares the two Polish production stemmer paths over the PoliMorf-backed
 * Radixor dictionary workload.
 *
 * <p>
 * Each benchmark operation processes the same changed-token corpus derived from
 * {@code pl-pl-polimorf}. The Radixor method uses the explicit
 * {@code pl-pl-polimorf} runtime model, while the Lucene method uses the public
 * {@link MorfologikFilter} path.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = { "-Xmx6g" })
public class PolishPolimorfStemmerComparisonBenchmark {

    /**
     * Explicit Polish PoliMorf model identifier.
     */
    private static final String POLIMORF_MODEL_ID = "pl-pl-polimorf";

    /**
     * Shared PoliMorf corpus and Radixor trie state.
     */
    @State(Scope.Benchmark)
    public static class SharedState {

        /**
         * Shared deterministic changed-token dictionary corpus.
         */
        private String[] tokens;

        /**
         * Radixor benchmark adapter over the PoliMorf model.
         */
        private RadixorBenchmarkStemmer radixorStemmer;

        /**
         * Initializes the PoliMorf corpus and Radixor trie before measurement.
         *
         * @throws IOException if the corpus or trie cannot be loaded
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            this.tokens = LanguageBenchmarkCorpus.createTokens(POLIMORF_MODEL_ID);
            final FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadCompiled(POLIMORF_MODEL_ID,
                    true, ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
            this.radixorStemmer = new RadixorBenchmarkStemmer(trie);
        }
    }

    /**
     * Per-thread Lucene Morfologik filter state.
     */
    @State(Scope.Thread)
    public static class LuceneFilterState {

        /**
         * Reusable Morfologik token-filter pipeline.
         */
        private final FilterPipeline polishMorfologik = new FilterPipeline(MorfologikFilter::new);
    }

    /**
     * Runs Radixor with the {@code pl-pl-polimorf} model over the PoliMorf corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole result sink
     */
    @Benchmark
    public void polishPolimorfRadixor(final SharedState sharedState, final Blackhole blackhole) {
        final String[] tokens = sharedState.tokens;
        final RadixorBenchmarkStemmer stemmer = sharedState.radixorStemmer;

        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }

    /**
     * Runs Lucene MorfologikFilter over the PoliMorf-derived corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void polishLuceneMorfologikFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.polishMorfologik.run(sharedState.tokens, blackhole);
    }

    /**
     * Factory for a Lucene filter under test.
     */
    private interface FilterFactory {

        /**
         * Creates a token stream wrapping the supplied benchmark input stream.
         *
         * @param input input token stream
         * @return filter stream
         */
        TokenStream create(TokenStream input);
    }

    /**
     * Reusable input stream, filter stream, and term attribute for one Lucene
     * benchmark method.
     */
    private static final class FilterPipeline {

        /**
         * Reusable benchmark input stream.
         */
        private final BenchmarkTokenStream input;

        /**
         * Lucene filter output stream.
         */
        private final TokenStream output;

        /**
         * Term attribute consumed by the benchmark.
         */
        private final CharTermAttribute termAttribute;

        /**
         * Creates one reusable filter pipeline.
         *
         * @param factory filter factory
         */
        private FilterPipeline(final FilterFactory factory) {
            this.input = new BenchmarkTokenStream(new String[0]);
            this.output = factory.create(this.input);
            this.termAttribute = this.output.addAttribute(CharTermAttribute.class);
        }

        /**
         * Runs the filter over one token corpus and consumes all emitted terms.
         *
         * @param tokens token corpus
         * @param blackhole result sink
         * @throws IOException if Lucene token streaming fails
         */
        private void run(final String[] tokens, final Blackhole blackhole) throws IOException {
            this.input.setTokens(tokens);
            this.output.reset();
            while (this.output.incrementToken()) {
                blackhole.consume(this.termAttribute.toString());
            }
            this.output.end();
        }
    }
}
