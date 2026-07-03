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

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;
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
 * Compares Radixor with official Snowball algorithms for every Radixor language
 * that has a matching Snowball Java stemmer.
 *
 * <p>
 * Each benchmark operation processes the same changed-token Radixor
 * dictionary-derived language corpus, repeated only when the changed-token
 * resource contains fewer than 5,000 token fields. The direct Snowball method
 * measures the isolated benchmark-only Snowball source. The Lucene
 * SnowballFilter method measures Lucene's TokenStream integration path,
 * including lower-case normalization and token attribute overhead.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class SnowballLanguageStemmerComparisonBenchmark {

    /**
     * Shared language corpus and Radixor trie state.
     */
    @State(Scope.Benchmark)
    public static class SharedState {

        /**
         * Language/algorithm case under comparison.
         */
        @Param({ "DANISH", "DUTCH", "FINNISH", "FRENCH", "GERMAN", "HUNGARIAN", "ITALIAN",
                "NORWEGIAN_BOKMAL", "NORWEGIAN_NYNORSK", "PORTUGUESE", "RUSSIAN", "SPANISH", "SWEDISH",
                "YIDDISH" })
        public String languageCaseName;

        /**
         * Resolved language/algorithm case.
         */
        private SnowballLanguageCase languageCase;

        /**
         * Shared deterministic changed-token dictionary corpus.
         */
        private String[] tokens;

        /**
         * Compiled Radixor trie for the selected language.
         */
        private RadixorBenchmarkStemmer radixorStemmer;

        /**
         * Initializes shared language resources before measurement.
         *
         * @throws IOException if the corpus or trie cannot be loaded
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            this.languageCase = SnowballLanguageCase.valueOf(this.languageCaseName);
            this.tokens = LanguageBenchmarkCorpus.createTokens(this.languageCase.radixorLanguage());
            this.radixorStemmer = new RadixorBenchmarkStemmer(StemmerPatchTrieLoader.loadCompiled(
                    this.languageCase.radixorLanguage(), true,
                    ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
        }
    }

    /**
     * Per-thread direct Snowball state.
     */
    @State(Scope.Thread)
    public static class DirectState {

        /**
         * Reusable direct Snowball adapter.
         */
        private SnowballStemmerAdapter snowballStemmer;

        /**
         * Initializes direct Snowball state for the selected language.
         *
         * @param sharedState selected language state
         */
        @Setup(Level.Trial)
        public void setUp(final SharedState sharedState) {
            this.snowballStemmer = sharedState.languageCase.createDirectStemmer();
        }
    }

    /**
     * Per-thread Lucene SnowballFilter state.
     */
    @State(Scope.Thread)
    public static class LuceneSnowballState {

        /**
         * Reusable benchmark input stream.
         */
        private BenchmarkTokenStream input;

        /**
         * Reusable Lucene SnowballFilter output stream.
         */
        private TokenStream output;

        /**
         * Reusable term attribute.
         */
        private CharTermAttribute termAttribute;

        /**
         * Initializes Lucene SnowballFilter state for the selected language.
         *
         * @param sharedState selected language state
         */
        @Setup(Level.Trial)
        public void setUp(final SharedState sharedState) {
            this.input = new BenchmarkTokenStream(new String[0]);
            final TokenStream normalizedInput = new LowerCaseFilter(this.input);
            this.output = new SnowballFilter(normalizedInput, sharedState.languageCase.luceneSnowballName());
            this.termAttribute = this.output.addAttribute(CharTermAttribute.class);
        }

        /**
         * Runs the reusable Lucene SnowballFilter over one corpus.
         *
         * <p>
         * The benchmark intentionally rebinds the {@code String[]} corpus on every
         * measured operation so the adaptation cost from the canonical string input to
         * Lucene's mutable character attributes is included.
         * </p>
         *
         * @param tokens    token corpus
         * @param blackhole result sink
         * @throws IOException if Lucene token streaming fails
         */
        void run(final String[] tokens, final Blackhole blackhole) throws IOException {
            this.input.setTokens(tokens);
            this.output.reset();
            while (this.output.incrementToken()) {
                blackhole.consume(this.termAttribute.toString());
            }
            this.output.end();
        }
    }

    /**
     * Runs Radixor over the selected Snowball-language corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void radixor(final SharedState sharedState, final Blackhole blackhole) {
        final String[] tokens = sharedState.tokens;
        final RadixorBenchmarkStemmer stemmer = sharedState.radixorStemmer;

        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }

    /**
     * Runs the official Snowball direct Java implementation over the selected
     * language corpus.
     *
     * @param sharedState shared benchmark state
     * @param directState reusable direct Snowball state
     * @param blackhole   result sink
     */
    @Benchmark
    public void snowballDirect(final SharedState sharedState, final DirectState directState,
            final Blackhole blackhole) {
        final String[] tokens = sharedState.tokens;
        final SnowballStemmerAdapter stemmer = directState.snowballStemmer;

        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }

    /**
     * Runs Lucene SnowballFilter over the selected language corpus.
     *
     * @param sharedState shared benchmark state
     * @param luceneState reusable Lucene Snowball state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void luceneSnowballFilter(final SharedState sharedState, final LuceneSnowballState luceneState,
            final Blackhole blackhole) throws IOException {
        luceneState.run(sharedState.tokens, blackhole);
    }
}
