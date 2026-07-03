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
import java.util.logging.Logger;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishMinimalStemFilter;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.KStemFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.egothor.stemmer.benchmark.snowball.ext.englishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.porterStemmer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.StemmerDictionaryParser;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;

/**
 * Compares English stemming throughput across Radixor and selected Java
 * algorithm paths with a shared deterministic corpus.
 *
 * <p>
 * The comparison uses one shared changed-token dictionary array for all methods:
 * </p>
 * <ul>
 * <li>Radixor direct dictionary lookup</li>
 * <li>Snowball Porter</li>
 * <li>Snowball English (Porter2)</li>
 * <li>Lucene direct Porter API (generated copy)</li>
 * <li>Lucene Porter, KStem, and EnglishMinimal token-filter paths</li>
 * <li>Benchmark-only Paice/Husk Lancaster baseline</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class EnglishStemmerComparisonBenchmark {

    /**
     * Shared, parameterized benchmark corpus state.
     */
    @State(Scope.Benchmark)
    public static class SharedState {

        /**
         * Shared deterministic token corpus.
         */
        private String[] tokens;

        /**
         * Radixor benchmark adapter for the US/UK benchmark corpus.
         */
        private RadixorBenchmarkStemmer radixorStemmer;

        /**
         * Initializes shared corpus and trie state once per trial.
         */
        @Setup(Level.Trial)
        public void setUp() throws java.io.IOException {
            Logger.getLogger(StemmerDictionaryParser.class.getName())
                    .setLevel(java.util.logging.Level.OFF);
            Logger.getLogger(StemmerDictionaryParser.class.getName()).setUseParentHandlers(false);
            this.tokens = EnglishComparisonCorpus.createTokens();
            this.radixorStemmer = new RadixorBenchmarkStemmer(StemmerPatchTrieLoader.loadCompiled(
                    StemmerPatchTrieLoader.Language.US_UK, true,
                    ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
        }
    }

    /**
     * Reusable direct stemmer instances.
     */
    @State(Scope.Thread)
    public static class DirectStemmerState {

        /**
         * Snowball classic Porter.
         */
        private SnowballStemmerAdapter porterStemmer;

        /**
         * Snowball English (Porter2) for legacy and dictionary comparison.
         */
        private SnowballStemmerAdapter englishPorterStemmer;

        /**
         * Generated Lucene direct Porter implementation copy.
         */
        private LucenePorterStemmerCopied lucenePorter;

        /**
         * Benchmark-only Paice/Husk Lancaster implementation.
         */
        private PaiceHuskLancasterStemmer paiceHuskLancaster;

        /**
         * Apache OpenNLP Porter stemmer.
         */
        private opennlp.tools.stemmer.PorterStemmer openNlpPorterStemmer;

        /**
         * Initializes mutable stemmer instances reused by all benchmark calls.
         */
        @Setup(Level.Trial)
        public void setUp() {
            this.porterStemmer = new SnowballStemmerAdapter(porterStemmer::new);
            this.englishPorterStemmer = new SnowballStemmerAdapter(englishStemmer::new);
            this.lucenePorter = new LucenePorterStemmerCopied();
            this.paiceHuskLancaster = new PaiceHuskLancasterStemmer();
            this.openNlpPorterStemmer = new opennlp.tools.stemmer.PorterStemmer();
        }
    }

    /**
     * Reusable Lucene token streams and filters.
     */
    @State(Scope.Thread)
    public static class LuceneFilterState {

        /**
         * Reused Porter filter stream input.
         */
        private final EnglishStemmerComparisonTokenStream porterStemFilterInput;

        /**
         * Porter token filter for public API integration-path comparison.
         */
        private final PorterStemFilter porterStemFilter;

        /**
         * Porter filter attributes.
         */
        private final CharTermAttribute porterStemFilterTerm;

        /**
         * Reused KStem filter stream input.
         */
        private final EnglishStemmerComparisonTokenStream kStemFilterInput;

        /**
         * KStem token filter for a second Lucene English baseline.
         */
        private final KStemFilter kStemFilter;

        /**
         * KStem filter attributes.
         */
        private final CharTermAttribute kStemTerm;

        /**
         * Reused minimal stem filter stream input.
         */
        private final EnglishStemmerComparisonTokenStream englishMinimalStemFilterInput;

        /**
         * EnglishMinimal token filter.
         */
        private final EnglishMinimalStemFilter englishMinimalStemFilter;

        /**
         * EnglishMinimal filter attributes.
         */
        private final CharTermAttribute englishMinimalTerm;

        /**
         * Reused English possessive filter stream input.
         */
        private final EnglishStemmerComparisonTokenStream englishPossessiveFilterInput;

        /**
         * English possessive filter.
         */
        private final EnglishPossessiveFilter englishPossessiveFilter;

        /**
         * English possessive filter attributes.
         */
        private final CharTermAttribute englishPossessiveTerm;

        /**
         * Creates benchmark stream/filter state and attaches token attributes.
         */
        public LuceneFilterState() {
            this.porterStemFilterInput = new EnglishStemmerComparisonTokenStream(new String[0]);
            this.porterStemFilter = new PorterStemFilter(this.porterStemFilterInput);
            this.porterStemFilterTerm = this.porterStemFilter.getAttribute(CharTermAttribute.class);

            this.kStemFilterInput = new EnglishStemmerComparisonTokenStream(new String[0]);
            this.kStemFilter = new KStemFilter(this.kStemFilterInput);
            this.kStemTerm = this.kStemFilter.getAttribute(CharTermAttribute.class);

            this.englishMinimalStemFilterInput = new EnglishStemmerComparisonTokenStream(new String[0]);
            this.englishMinimalStemFilter = new EnglishMinimalStemFilter(this.englishMinimalStemFilterInput);
            this.englishMinimalTerm = this.englishMinimalStemFilter.getAttribute(CharTermAttribute.class);

            this.englishPossessiveFilterInput = new EnglishStemmerComparisonTokenStream(new String[0]);
            this.englishPossessiveFilter = new EnglishPossessiveFilter(this.englishPossessiveFilterInput);
            this.englishPossessiveTerm = this.englishPossessiveFilter.getAttribute(CharTermAttribute.class);
        }

        /**
         * Rebinds the shared corpus and resets all streams for another measured
         * operation.
         *
         * <p>
         * The {@code String[]} to Lucene character-buffer conversion is deliberately
         * performed every time so TokenFilter benchmarks include the cost of adapting
         * the benchmark's canonical string corpus to Lucene's mutable token
         * attributes.
         * </p>
         *
         * @param tokens benchmark token corpus
         */
        void configure(final String[] tokens) throws IOException {
            this.porterStemFilterInput.setTokens(tokens);
            this.kStemFilterInput.setTokens(tokens);
            this.englishMinimalStemFilterInput.setTokens(tokens);
            this.englishPossessiveFilterInput.setTokens(tokens);

            this.porterStemFilter.reset();
            this.kStemFilter.reset();
            this.englishMinimalStemFilter.reset();
            this.englishPossessiveFilter.reset();
        }

        /**
         * Reuses one mutable filter stream and returns all emitted tokens to blackhole.
         *
         * @param stream     benchmark token stream with configured filter
         * @param term       token text attribute
         * @param blackhole  sink
         * @throws IOException on token stream failure
         */
        private static void consume(final TokenStream stream, final CharTermAttribute term, final Blackhole blackhole)
                throws IOException {
            while (stream.incrementToken()) {
                blackhole.consume(term.toString());
            }
            stream.end();
        }

        /**
         * Executes Porter filter over the shared corpus.
         *
         * @param blackhole sink
         * @throws IOException if tokenization fails
         */
        void runPorterStemFilter(final Blackhole blackhole) throws IOException {
            consume(this.porterStemFilter, this.porterStemFilterTerm, blackhole);
        }

        /**
         * Executes KStem filter over the shared corpus.
         *
         * @param blackhole sink
         * @throws IOException if tokenization fails
         */
        void runKStemFilter(final Blackhole blackhole) throws IOException {
            consume(this.kStemFilter, this.kStemTerm, blackhole);
        }

        /**
         * Executes English minimal filter over the shared corpus.
         *
         * @param blackhole sink
         * @throws IOException if tokenization fails
         */
        void runEnglishMinimalStemFilter(final Blackhole blackhole) throws IOException {
            consume(this.englishMinimalStemFilter, this.englishMinimalTerm, blackhole);
        }

        /**
         * Executes English possessive filter over the shared corpus.
         *
         * @param blackhole sink
         * @throws IOException if tokenization fails
         */
        void runEnglishPossessiveFilter(final Blackhole blackhole) throws IOException {
            consume(this.englishPossessiveFilter, this.englishPossessiveTerm, blackhole);
        }
    }

    /**
     * Measures Radixor preferred-result stemming throughput.
     *
     * <p>
     * This path uses a single shared dictionary lookup and patch application.
     * </p>
     *
     * @param sharedState shared corpus and trie
     * @param blackhole   result sink
     */
    @Benchmark
    public void radixorUsUkProfiPreferredStem(final SharedState sharedState, final Blackhole blackhole) {
        final String[] tokens = sharedState.tokens;
        final RadixorBenchmarkStemmer stemmer = sharedState.radixorStemmer;

        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }

    /**
     * Measures the canonical Snowball Porter stemming throughput used by the
     * performance badge.
     *
     * <p>
     * This uses Snowball classic Porter as a direct stemmer API call and includes
     * no Lucene token stream integration overhead.
     * </p>
     *
     * @param sharedState  shared corpus
     * @param stemmerState reusable Snowball adapter state
     * @param blackhole    result sink
     */
    @Benchmark
    public void snowballOriginalPorter(final SharedState sharedState, final DirectStemmerState stemmerState,
            final Blackhole blackhole) {
        final String[] tokens = sharedState.tokens;
        final SnowballStemmerAdapter stemmer = stemmerState.porterStemmer;

        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }

    /**
     * Measures Snowball English (Porter2) direct API throughput.
     *
     * @param sharedState  shared corpus
     * @param stemmerState reusable Snowball adapter state
     * @param blackhole    result sink
     */
    @Benchmark
    public void snowballEnglishPorter2(final SharedState sharedState, final DirectStemmerState stemmerState,
            final Blackhole blackhole) {
        final String[] tokens = sharedState.tokens;
        final SnowballStemmerAdapter stemmer = stemmerState.englishPorterStemmer;

        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }

    /**
     * Measures Lucene generated Porter stemmer API throughput.
     *
     * <p>
     * This path is a generated copy of Lucene&apos;s package-private PorterStemmer
     * class, compiled into the JMH source set only.
     * </p>
     *
     * @param sharedState  shared corpus
     * @param stemmerState reusable Lucene copied API state
     * @param blackhole    result sink
     */
    @Benchmark
    public void lucenePorterStemmerCopied(final SharedState sharedState, final DirectStemmerState stemmerState,
            final Blackhole blackhole) {
        final String[] tokens = sharedState.tokens;
        final LucenePorterStemmerCopied stemmer = stemmerState.lucenePorter;

        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }

    /**
     * Measures Lucene Porter token-filter integration throughput.
     *
     * <p>
     * This includes stream, reusable token attributes, and filter overhead and is
     * not equivalent to a direct API stemmer call.
     * </p>
     *
     * @param sharedState shared corpus
     * @param filterState reusable filter state
     * @param blackhole    sink
     * @throws IOException if token stream fails
     */
    @Benchmark
    public void lucenePorterStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.configure(sharedState.tokens);
        filterState.runPorterStemFilter(blackhole);
    }

    /**
     * Measures Lucene KStem integration-path throughput.
     *
     * @param sharedState shared corpus
     * @param filterState reusable filter state
     * @param blackhole    sink
     * @throws IOException if token stream fails
     */
    @Benchmark
    public void luceneKStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.configure(sharedState.tokens);
        filterState.runKStemFilter(blackhole);
    }

    /**
     * Measures Lucene EnglishMinimal integration-path throughput.
     *
     * @param sharedState shared corpus
     * @param filterState reusable filter state
     * @param blackhole    sink
     * @throws IOException if token stream fails
     */
    @Benchmark
    public void luceneEnglishMinimalStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.configure(sharedState.tokens);
        filterState.runEnglishMinimalStemFilter(blackhole);
    }

    /**
     * Measures benchmark-only Paice/Husk Lancaster throughput.
     *
     * @param sharedState  shared corpus
     * @param stemmerState reusable Paice/Husk instance
     * @param blackhole    sink
     */
    @Benchmark
    public void paiceHuskLancaster(final SharedState sharedState, final DirectStemmerState stemmerState,
            final Blackhole blackhole) {
        final String[] tokens = sharedState.tokens;
        final PaiceHuskLancasterStemmer stemmer = stemmerState.paiceHuskLancaster;

        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }

    /**
     * Measures Apache OpenNLP Porter stemming throughput.
     *
     * @param sharedState  shared corpus
     * @param stemmerState reusable OpenNLP Porter instance
     * @param blackhole    sink
     */
    @Benchmark
    public void opennlpPorterStemmer(final SharedState sharedState, final DirectStemmerState stemmerState,
            final Blackhole blackhole) {
        final String[] tokens = sharedState.tokens;
        final opennlp.tools.stemmer.PorterStemmer stemmer = stemmerState.openNlpPorterStemmer;

        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token).toString());
        }
    }

    /**
     * Measures Lucene EnglishPossessiveFilter as a narrow possessive-removal
     * baseline.
     *
     * @param sharedState shared corpus
     * @param filterState reusable filter state
     * @param blackhole   sink
     * @throws IOException if token stream fails
     */
    @Benchmark
    public void luceneEnglishPossessiveFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.configure(sharedState.tokens);
        filterState.runEnglishPossessiveFilter(blackhole);
    }
}
