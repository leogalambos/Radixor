package org.egothor.stemmer.benchmark;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.PatchCommandEncoder;
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
import org.tartarus.snowball.ext.englishStemmer;
import org.tartarus.snowball.ext.porterStemmer;

/**
 * Compares English stemming throughput across Radixor and Snowball stemmers.
 *
 * <p>
 * The benchmark processes the same deterministic token array with:
 * </p>
 * <ul>
 * <li>Radixor using bundled
 * {@link StemmerPatchTrieLoader.Language#US_UK_PROFI}</li>
 * <li>Snowball original Porter stemmer</li>
 * <li>Snowball English stemmer, commonly referred to as Porter2</li>
 * </ul>
 *
 * <p>
 * This benchmark compares throughput on a shared workload. It does not imply
 * that the algorithms are linguistically equivalent.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class EnglishStemmerComparisonBenchmark {

    /**
     * Shared benchmark data.
     */
    @State(Scope.Benchmark)
    public static class SharedState {

        /**
         * Number of generated lexical families.
         */
        @Param({ "1000", "5000" })
        public int familyCount;

        /**
         * Token workload processed by all compared stemmers.
         */
        private String[] tokens;

        /**
         * Radixor trie loaded from the bundled professional English dictionary.
         */
        private FrequencyTrie<String> radixorTrie;

        /**
         * Initializes the shared benchmark state.
         *
         * @throws IOException if the bundled Radixor dictionary cannot be loaded
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            this.tokens = EnglishComparisonCorpus.createTokens(this.familyCount);
            this.radixorTrie = StemmerPatchTrieLoader.load(StemmerPatchTrieLoader.Language.US_UK_PROFI, true,
                    ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        }
    }

    /**
     * Per-thread reusable Snowball stemmers.
     */
    @State(Scope.Thread)
    public static class SnowballState {

        /**
         * Adapter for the original Porter stemmer.
         */
        private SnowballStemmerAdapter porterStemmer;

        /**
         * Adapter for the Snowball English stemmer.
         */
        private SnowballStemmerAdapter englishStemmer;

        /**
         * Initializes reusable Snowball stemmers for the executing thread.
         */
        @Setup(Level.Trial)
        public void setUp() {
            this.porterStemmer = new SnowballStemmerAdapter(porterStemmer::new);
            this.englishStemmer = new SnowballStemmerAdapter(englishStemmer::new);
        }
    }

    /**
     * Measures Radixor preferred-result stemming throughput.
     *
     * @param sharedState shared benchmark data
     * @param blackhole   sink preventing dead-code elimination
     */
    @Benchmark
    public void radixorUsUkProfiPreferredStem(final SharedState sharedState, final Blackhole blackhole) {
        final String[] tokens = sharedState.tokens;
        final FrequencyTrie<String> trie = sharedState.radixorTrie;

        for (String token : tokens) {
            final String patch = trie.get(token);
            final String stem = patch == null ? token : PatchCommandEncoder.apply(token, patch);
            blackhole.consume(stem);
        }
    }

    /**
     * Measures Snowball original Porter stemming throughput.
     *
     * @param sharedState   shared benchmark data
     * @param snowballState reusable Snowball stemmers
     * @param blackhole     sink preventing dead-code elimination
     */
    @Benchmark
    public void snowballOriginalPorter(final SharedState sharedState, final SnowballState snowballState,
            final Blackhole blackhole) {
        final String[] tokens = sharedState.tokens;
        final SnowballStemmerAdapter stemmer = snowballState.porterStemmer;

        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }

    /**
     * Measures Snowball English stemming throughput.
     *
     * <p>
     * Snowball English is the newer English stemmer commonly referred to as
     * Porter2.
     * </p>
     *
     * @param sharedState   shared benchmark data
     * @param snowballState reusable Snowball stemmers
     * @param blackhole     sink preventing dead-code elimination
     */
    @Benchmark
    public void snowballEnglishPorter2(final SharedState sharedState, final SnowballState snowballState,
            final Blackhole blackhole) {
        final String[] tokens = sharedState.tokens;
        final SnowballStemmerAdapter stemmer = snowballState.englishStemmer;

        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }
}