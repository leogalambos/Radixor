package org.egothor.stemmer.benchmark;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.PatchCommandEncoder;
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
 * Benchmarks lookup-oriented operations on compiled Radixor tries.
 *
 * <p>
 * The benchmark uses a deterministic morphology-shaped corpus and measures the
 * latency of the hot-path lookup operations that are relevant at runtime:
 * retrieving the preferred patch command, retrieving all candidate patch
 * commands, and reconstructing stems from the returned patch values.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class FrequencyTrieLookupBenchmark {

    /**
     * Shared benchmark state for lookup scenarios.
     */
    @State(Scope.Benchmark)
    public static class LookupState {

        /**
         * Number of canonical stems to generate.
         */
        @Param({ "2000", "10000" })
        public int stemCount;

        /**
         * Reduction mode used to compile the lookup trie.
         */
        @Param({
                "MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS",
                "MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS",
                "MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS"
        })
        public String reductionMode;

        /**
         * Compiled trie under test.
         */
        private FrequencyTrie<String> trie;

        /**
         * Deterministic lookup keys.
         */
        private String[] lookupKeys;

        /**
         * Keys that are known to return multiple patch candidates from
         * {@code getAll()}.
         */
        private String[] ambiguousLookupKeys;

        /**
         * Initializes the benchmark state.
         *
         * @throws IOException if corpus compilation fails
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            final BenchmarkCorpusSupport.BenchmarkCorpus corpus = BenchmarkCorpusSupport.createCorpus(this.stemCount);
            final ReductionSettings settings =
                    ReductionSettings.withDefaults(ReductionMode.valueOf(this.reductionMode));
            this.trie = BenchmarkCorpusSupport.compilePatchTrie(corpus.dictionaryText(), settings, true);
            this.lookupKeys = corpus.lookupKeys();
            this.ambiguousLookupKeys = corpus.ambiguousLookupKeys();
        }
    }

    /**
     * Measures preferred patch lookup latency.
     *
     * @param state prepared lookup state
     * @param blackhole sink preventing dead-code elimination
     */
    @Benchmark
    public void lookupPreferredPatch(final LookupState state, final Blackhole blackhole) {
        final String[] keys = state.lookupKeys;
        for (String key : keys) {
            final String patch = state.trie.get(key);
            if (patch == null) {
                throw new IllegalStateException("Missing preferred patch for key " + key + '.');
            }
            blackhole.consume(patch);
        }
    }

    /**
     * Measures retrieval of all patch candidates on ambiguous forms.
     *
     * @param state prepared lookup state
     * @param blackhole sink preventing dead-code elimination
     */
    @Benchmark
    public void lookupAllPatches(final LookupState state, final Blackhole blackhole) {
        final String[] keys = state.ambiguousLookupKeys;
        for (String key : keys) {
            final String[] patches = state.trie.getAll(key);
            if (patches.length < 2) {
                throw new IllegalStateException("Expected multiple patches for key " + key + '.');
            }
            blackhole.consume(patches);
        }
    }

    /**
     * Measures end-to-end preferred stemming from lookup plus patch application.
     *
     * @param state prepared lookup state
     * @param blackhole sink preventing dead-code elimination
     */
    @Benchmark
    public void stemPreferredVariant(final LookupState state, final Blackhole blackhole) {
        final String[] keys = state.lookupKeys;
        for (String key : keys) {
            final String patch = state.trie.get(key);
            blackhole.consume(PatchCommandEncoder.apply(key, patch));
        }
    }

    /**
     * Measures end-to-end full candidate stemming from {@code getAll()} plus
     * patch application.
     *
     * @param state prepared lookup state
     * @param blackhole sink preventing dead-code elimination
     */
    @Benchmark
    public void stemAllVariants(final LookupState state, final Blackhole blackhole) {
        final String[] keys = state.ambiguousLookupKeys;
        for (String key : keys) {
            final String[] patches = state.trie.getAll(key);
            for (String patch : patches) {
                blackhole.consume(PatchCommandEncoder.apply(key, patch));
            }
        }
    }
}
