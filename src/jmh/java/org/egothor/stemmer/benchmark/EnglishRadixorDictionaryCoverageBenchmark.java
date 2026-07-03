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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.FrequencyTrieBuilders;
import org.egothor.stemmer.PatchCommandEncoder;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.egothor.stemmer.StemmerDictionaryParser;
import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.egothor.stemmer.WordTraversalDirection;
import org.openjdk.jmh.annotations.AuxCounters;
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
 * Measures Radixor English stemming quality and changed-token speed when the
 * runtime trie is trained from a deterministic percentage of dictionary rows.
 *
 * <p>
 * The measured stemmer always uses {@link CompiledPatchCommand} values. Quality
 * is evaluated against the complete English dictionary corpus, while speed is
 * measured over the complete changed-token English corpus used by the comparison
 * benchmarks.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class EnglishRadixorDictionaryCoverageBenchmark {

    /**
     * Shared benchmark state for one dictionary-row coverage percentage.
     */
    @State(Scope.Benchmark)
    public static class CoverageState {

        /**
         * Percentage of parsed English dictionary rows used to build the Radixor trie.
         */
        @Param({ "100", "90", "80", "70", "60", "50", "40", "30", "20", "10" })
        public int coveragePercent;

        /**
         * Full English corpus used for exact-root accounting.
         */
        private LanguageBenchmarkCorpus.Corpus fullCorpus;

        /**
         * Complete changed-token English corpus used for speed measurement.
         */
        private LanguageBenchmarkCorpus.Corpus changedCorpus;

        /**
         * Radixor stemmer backed by a trie built from selected dictionary rows.
         */
        private RadixorBenchmarkStemmer stemmer;

        /**
         * Parsed dictionary row count before deterministic coverage selection.
         */
        private int totalRowCount;

        /**
         * Selected dictionary row count for the configured coverage percentage.
         */
        private int selectedRowCount;

        /**
         * Builds the reduced dictionary trie and shared corpora before measurement.
         *
         * @throws IOException if the English dictionary resource cannot be read
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            final List<DictionaryRow> rows = readEnglishRows();
            this.totalRowCount = rows.size();
            final List<DictionaryRow> selectedRows = selectRows(rows, this.coveragePercent);
            this.selectedRowCount = selectedRows.size();
            this.fullCorpus = LanguageBenchmarkCorpus.createFullCorpus(StemmerPatchTrieLoader.Language.US_UK);
            this.changedCorpus = LanguageBenchmarkCorpus.createChangedCorpus(StemmerPatchTrieLoader.Language.US_UK);
            this.stemmer = new RadixorBenchmarkStemmer(buildCompiledTrie(selectedRows));
        }
    }

    /**
     * JMH auxiliary counters for dictionary-row coverage and exact-root agreement.
     */
    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    public static class CoverageCounters {

        /**
         * Number of exact output/root matches over the full dictionary corpus.
         */
        public long correctMatches;

        /**
         * Number of evaluated tokens over the full dictionary corpus.
         */
        public long evaluatedTokens;

        /**
         * Number of exact output/root matches where token and root differ.
         */
        public long changedCorrectMatches;

        /**
         * Number of evaluated tokens where token and root differ.
         */
        public long changedEvaluatedTokens;

        /**
         * Number of exact output/root matches where token already equals root.
         */
        public long rootPreservedMatches;

        /**
         * Number of evaluated tokens where token already equals root.
         */
        public long rootEvaluatedTokens;

        /**
         * Number of parsed dictionary rows used for trie construction.
         */
        public long selectedRows;

        /**
         * Total number of parsed dictionary rows available.
         */
        public long totalRows;

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
            this.selectedRows = 0L;
            this.totalRows = 0L;
        }
    }

    /**
     * Measures direct Radixor stemming over the complete English changed-token
     * corpus.
     *
     * @param state     shared coverage state
     * @param blackhole result sink
     */
    @Benchmark
    public void changedTokenStemmingSpeed(final CoverageState state, final Blackhole blackhole) {
        final String[] tokens = state.changedCorpus.tokens();
        final RadixorBenchmarkStemmer stemmer = state.stemmer;
        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }

    /**
     * Measures exact-root agreement over the complete English dictionary corpus.
     *
     * @param state     shared coverage state
     * @param counters  auxiliary exact-root counters
     * @param blackhole result sink
     * @return exact-root match count for one benchmark operation
     */
    @Benchmark
    public int exactRootAgreement(final CoverageState state, final CoverageCounters counters,
            final Blackhole blackhole) {
        final QualityCounts counts = evaluate(state.fullCorpus, state.stemmer, blackhole);
        counters.correctMatches += counts.correctMatches();
        counters.evaluatedTokens += counts.evaluatedTokens();
        counters.changedCorrectMatches += counts.changedCorrectMatches();
        counters.changedEvaluatedTokens += counts.changedEvaluatedTokens();
        counters.rootPreservedMatches += counts.rootPreservedMatches();
        counters.rootEvaluatedTokens += counts.rootEvaluatedTokens();
        counters.selectedRows += state.selectedRowCount;
        counters.totalRows += state.totalRowCount;
        return counts.correctMatches();
    }

    private static QualityCounts evaluate(final LanguageBenchmarkCorpus.Corpus corpus,
            final RadixorBenchmarkStemmer stemmer, final Blackhole blackhole) {
        final String[] tokens = corpus.tokens();
        final String[] roots = corpus.expectedRoots();
        int correct = 0;
        int changedCorrect = 0;
        int changedEvaluated = 0;
        int rootPreserved = 0;
        int rootEvaluated = 0;
        for (int index = 0; index < tokens.length; index++) {
            final String token = tokens[index];
            final String root = roots[index];
            final String actual = stemmer.stem(token);
            blackhole.consume(actual);
            final boolean exact = Objects.equals(root, actual);
            if (exact) {
                correct++;
            }
            if (Objects.equals(token, root)) {
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
        return new QualityCounts(correct, tokens.length, changedCorrect, changedEvaluated, rootPreserved,
                rootEvaluated);
    }

    private static FrequencyTrie<CompiledPatchCommand> buildCompiledTrie(final List<DictionaryRow> rows) {
        final ReductionSettings settings = new ReductionSettings(
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS,
                ReductionSettings.DEFAULT_DOMINANT_WINNER_MIN_PERCENT,
                ReductionSettings.DEFAULT_DOMINANT_WINNER_OVER_SECOND_RATIO,
                true);
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new, settings,
                WordTraversalDirection.BACKWARD);
        final PatchCommandEncoder encoder = PatchCommandEncoder.builder()
                .traversalDirection(WordTraversalDirection.BACKWARD)
                .build();

        for (DictionaryRow row : rows) {
            builder.put(row.stem(), encoder.encode(row.stem(), row.stem()));
            for (String variant : row.variants()) {
                if (!variant.equals(row.stem())) {
                    builder.put(variant, encoder.encode(variant, row.stem()));
                }
            }
        }

        final FrequencyTrie<String> trie = builder.build();
        final Map<String, CompiledPatchCommand> compiledCommands = new HashMap<String, CompiledPatchCommand>(4096);
        return FrequencyTrieBuilders.mapValues(trie, CompiledPatchCommand[]::new, trie.metadata().reductionSettings(),
                patch -> compiledCommands.computeIfAbsent(patch,
                        value -> CompiledPatchCommand.compile(value, trie.traversalDirection())));
    }

    private static List<DictionaryRow> selectRows(final List<DictionaryRow> rows, final int coveragePercent) {
        if (coveragePercent < 1 || coveragePercent > 100) {
            throw new IllegalArgumentException("coveragePercent must be between 1 and 100.");
        }
        if (coveragePercent == 100) {
            return List.copyOf(rows);
        }

        final int selectedCount = Math.max(1, Math.round(rows.size() * coveragePercent / 100.0F));
        final List<DictionaryRow> rankedRows = new ArrayList<DictionaryRow>(rows);
        rankedRows.sort(Comparator.comparingLong(DictionaryRow::rank).thenComparingInt(DictionaryRow::lineNumber));

        final Set<Integer> selectedLineNumbers = new HashSet<Integer>(selectedCount);
        for (int index = 0; index < selectedCount; index++) {
            selectedLineNumbers.add(rankedRows.get(index).lineNumber());
        }

        final List<DictionaryRow> selectedRows = new ArrayList<DictionaryRow>(selectedCount);
        for (DictionaryRow row : rows) {
            if (selectedLineNumbers.contains(row.lineNumber())) {
                selectedRows.add(row);
            }
        }
        return selectedRows;
    }

    private static List<DictionaryRow> readEnglishRows() throws IOException {
        final String resourcePath = StemmerPatchTrieLoader.Language.US_UK.resourcePath();
        final InputStream resource = StemmerPatchTrieLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Missing bundled English dictionary resource " + resourcePath + ".");
        }

        final List<DictionaryRow> rows = new ArrayList<DictionaryRow>(400_000);
        try (InputStream inputStream = resource;
                GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
                InputStreamReader inputStreamReader = new InputStreamReader(gzipInputStream, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(inputStreamReader)) {
            StemmerDictionaryParser.parse(reader, resourcePath, (stem, variants, lineNumber) -> {
                rows.add(new DictionaryRow(lineNumber, stem, variants, rank(lineNumber, stem, variants)));
            });
        }
        return rows;
    }

    private static long rank(final int lineNumber, final String stem, final String[] variants) {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, lineNumber);
        hash = mix(hash, stem);
        for (String variant : variants) {
            hash = mix(hash, variant);
        }
        return hash;
    }

    private static long mix(final long hash, final int value) {
        long result = hash;
        result ^= value & 0xFFL;
        result *= 0x100000001b3L;
        result ^= value >>> 8 & 0xFFL;
        result *= 0x100000001b3L;
        result ^= value >>> 16 & 0xFFL;
        result *= 0x100000001b3L;
        result ^= value >>> 24 & 0xFFL;
        result *= 0x100000001b3L;
        return result;
    }

    private static long mix(final long hash, final String value) {
        long result = hash;
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            result ^= character & 0xFFL;
            result *= 0x100000001b3L;
            result ^= character >>> 8;
            result *= 0x100000001b3L;
        }
        result ^= 0xFFL;
        result *= 0x100000001b3L;
        return result;
    }

    /**
     * One parsed dictionary row with deterministic selection rank.
     *
     * @param lineNumber source dictionary line number
     * @param stem       canonical stem from the first column
     * @param variants   normalized variants from following columns
     * @param rank       deterministic selection rank
     */
    private record DictionaryRow(int lineNumber, String stem, String[] variants, long rank) {

        /**
         * Creates one immutable dictionary row snapshot.
         *
         * @param lineNumber source dictionary line number
         * @param stem       canonical stem from the first column
         * @param variants   normalized variants from following columns
         * @param rank       deterministic selection rank
         */
        DictionaryRow {
            Objects.requireNonNull(stem, "stem");
            variants = variants.clone();
        }

        @Override
        public String[] variants() {
            return this.variants.clone();
        }
    }

    /**
     * Exact-root accounting result for one quality operation.
     *
     * @param correctMatches         exact-root matches for all tokens
     * @param evaluatedTokens        evaluated token count
     * @param changedCorrectMatches  exact-root matches for changed tokens
     * @param changedEvaluatedTokens evaluated changed-token count
     * @param rootPreservedMatches   exact-root matches for root-equal tokens
     * @param rootEvaluatedTokens    evaluated root-equal token count
     */
    private record QualityCounts(int correctMatches, int evaluatedTokens, int changedCorrectMatches,
            int changedEvaluatedTokens, int rootPreservedMatches, int rootEvaluatedTokens) {
    }
}
