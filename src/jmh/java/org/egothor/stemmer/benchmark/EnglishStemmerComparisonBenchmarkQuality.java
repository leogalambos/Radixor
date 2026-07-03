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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.egothor.stemmer.benchmark.snowball.ext.porterStemmer;
import org.openjdk.jmh.annotations.AuxCounters;
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
 * Emits exact-root agreement metrics for the canonical English badge pair.
 *
 * <p>
 * This class is deliberately named so the existing focused include pattern for
 * English stemmer comparison benchmarks includes it. The benchmark methods are
 * separate from throughput methods so equality checks do not contaminate timing
 * scores.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.MILLISECONDS)
@Fork(0)
public class EnglishStemmerComparisonBenchmarkQuality {

    /**
     * Shared English quality corpus and stemmer state.
     */
    @State(Scope.Benchmark)
    public static class SharedState {

        /**
         * Complete English resource-derived corpus.
         */
        private LanguageBenchmarkCorpus.Corpus corpus;

        /**
         * Compiled Radixor English trie.
         */
        private RadixorBenchmarkStemmer radixorStemmer;

        /**
         * Reusable Snowball Porter adapter.
         */
        private SnowballStemmerAdapter porterStemmer;

        /**
         * Initializes quality resources.
         *
         * @throws IOException if corpus or trie loading fails
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            this.corpus = LanguageBenchmarkCorpus.createFullCorpus(StemmerPatchTrieLoader.Language.US_UK);
            this.radixorStemmer = new RadixorBenchmarkStemmer(StemmerPatchTrieLoader.loadCompiled(
                    StemmerPatchTrieLoader.Language.US_UK, true,
                    ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
            this.porterStemmer = new SnowballStemmerAdapter(porterStemmer::new);
        }
    }

    /**
     * JMH auxiliary counters for exact-root agreement.
     */
    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    public static class AccuracyCounters {

        /**
         * Number of exact-root matches.
         */
        public long correctMatches;

        /**
         * Number of evaluated tokens.
         */
        public long evaluatedTokens;

        /**
         * Number of exact-root matches where the input token differs from the
         * expected root.
         */
        public long changedCorrectMatches;

        /**
         * Number of evaluated tokens where the input token differs from the expected
         * root.
         */
        public long changedEvaluatedTokens;

        /**
         * Number of exact-root matches where the input token is already the expected
         * root.
         */
        public long rootPreservedMatches;

        /**
         * Number of evaluated tokens where the input token is already the expected
         * root.
         */
        public long rootEvaluatedTokens;

        /**
         * Resets counters before each measured iteration.
         */
        @Setup(Level.Iteration)
        public void reset() {
            this.correctMatches = 0L;
            this.evaluatedTokens = 0L;
            this.changedCorrectMatches = 0L;
            this.changedEvaluatedTokens = 0L;
            this.rootPreservedMatches = 0L;
            this.rootEvaluatedTokens = 0L;
        }
    }

    /**
     * Evaluates exact-root agreement for the canonical Radixor badge method.
     *
     * @param sharedState shared English quality state
     * @param counters JMH auxiliary counters
     * @param blackhole result sink
     * @return exact-root match count
     */
    @Benchmark
    public int radixorUsUkProfiPreferredStemAccuracy(final SharedState sharedState,
            final AccuracyCounters counters, final Blackhole blackhole) {
        return evaluate(sharedState.corpus, sharedState.radixorStemmer::stem, counters, blackhole);
    }

    /**
     * Evaluates exact-root agreement for the canonical Snowball Porter badge
     * method.
     *
     * @param sharedState shared English quality state
     * @param counters JMH auxiliary counters
     * @param blackhole result sink
     * @return exact-root match count
     */
    @Benchmark
    public int snowballOriginalPorterAccuracy(final SharedState sharedState,
            final AccuracyCounters counters, final Blackhole blackhole) {
        return evaluate(sharedState.corpus, sharedState.porterStemmer::stem, counters, blackhole);
    }

    /**
     * Evaluates one stemmer against the expected roots.
     *
     * @param corpus token/root corpus
     * @param stemmer stemmer under evaluation
     * @param counters JMH auxiliary counters
     * @param blackhole result sink
     * @return exact-root match count
     */
    private static int evaluate(final LanguageBenchmarkCorpus.Corpus corpus, final Stemmer stemmer,
            final AccuracyCounters counters, final Blackhole blackhole) {
        Objects.requireNonNull(corpus, "corpus");
        Objects.requireNonNull(stemmer, "stemmer");

        int correct = 0;
        int changedCorrect = 0;
        int changedEvaluated = 0;
        int rootPreserved = 0;
        int rootEvaluated = 0;
        final String[] tokens = corpus.tokens();
        final String[] expectedRoots = corpus.expectedRoots();
        for (int index = 0; index < tokens.length; index++) {
            final String token = tokens[index];
            final String expectedRoot = expectedRoots[index];
            final String actual = stemmer.stem(token);
            blackhole.consume(actual);
            final boolean exact = Objects.equals(expectedRoot, actual);
            if (exact) {
                correct++;
            }
            if (Objects.equals(token, expectedRoot)) {
                rootEvaluated++;
                if (exact) {
                    rootPreserved++;
                }
            } else {
                changedEvaluated++;
                if (exact) {
                    changedCorrect++;
                }
            }
        }

        counters.correctMatches += correct;
        counters.evaluatedTokens += tokens.length;
        counters.changedCorrectMatches += changedCorrect;
        counters.changedEvaluatedTokens += changedEvaluated;
        counters.rootPreservedMatches += rootPreserved;
        counters.rootEvaluatedTokens += rootEvaluated;
        return correct;
    }

    /**
     * Direct stemmer function.
     */
    @FunctionalInterface
    private interface Stemmer {

        /**
         * Produces one stem.
         *
         * @param token input token
         * @return produced stem
         */
        String stem(String token);
    }
}
