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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.de.GermanLightStemFilter;
import org.apache.lucene.analysis.de.GermanMinimalStemFilter;
import org.apache.lucene.analysis.de.GermanNormalizationFilter;
import org.apache.lucene.analysis.de.GermanStemFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
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

import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;

/**
 * German-only stemmer comparison on CISTEM gold standards.
 *
 * <p>
 * Each benchmark operation is fed by one cluster file. The same candidate set is
 * evaluated twice, once per file, to produce one precision/recall/f-measure
 * table for each gold standard.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(java.util.concurrent.TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
@Fork(1)
public class GermanGoldstandardStemmerComparisonBenchmark {

    /**
     * Shared German benchmark state for one dataset and one candidate.
     */
    @State(Scope.Benchmark)
    public static class SharedState {

        /**
         * Gold standard dataset.
         */
        @Param({"goldstandard1.txt", "goldstandard2.txt"})
        public String goldStandardFileName;

        /**
         * Candidate stemmer.
         */
        @Param({
                "GERMAN_RADIXOR",
                "GERMAN_LUCENE_GERMAN_STEM_FILTER",
                "GERMAN_LUCENE_GERMAN_LIGHT_STEM_FILTER",
                "GERMAN_LUCENE_GERMAN_MINIMAL_STEM_FILTER",
                "GERMAN_CISTEM",
                "SNOWBALL_GERMAN_DIRECT",
                "SNOWBALL_GERMAN_LUCENE_FILTER"
        })
        public String candidateName;

        /**
         * Parsed gold standard corpus.
         */
        private GermanGoldstandardCorpus corpus;

        /**
         * Gold standard words flattened by cluster order.
         */
        private String[] allTokens;

        /**
         * Candidate evaluator.
         */
        private GoldstandardStemmer stemmer;

        /**
         * Initializes one candidate on one gold standard corpus.
         *
         * @throws IOException when the corpus cannot be loaded
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            this.corpus = loadCorpus(this.goldStandardFileName);
            this.allTokens = flattenCorpusTokens(this.corpus);
            this.stemmer = GermanCandidate.valueOf(this.candidateName).createEvaluator();
        }
    }

    /**
     * JMH auxiliary counters for CISTEM-style cluster accounting.
     */
    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    public static class GoldstandardQualityCounters {

        /**
         * True positives across clusters.
         */
        public long truePositives;

        /**
         * False positives across clusters.
         */
        public long falsePositives;

        /**
         * False negatives across clusters.
         */
        public long falseNegatives;

        /**
         * Evaluated clusters.
         */
        public long evaluatedClusters;

        /**
         * Evaluated tokens.
         */
        public long evaluatedTokens;

        /**
         * Resets counters before each measured iteration.
         */
        @Setup(Level.Iteration)
        public void reset() {
            this.truePositives = 0L;
            this.falsePositives = 0L;
            this.falseNegatives = 0L;
            this.evaluatedClusters = 0L;
            this.evaluatedTokens = 0L;
        }
    }

    /**
     * Evaluates CISTEM-style precision, recall, and F1-relevant counts.
     *
     * @param state shared benchmark state
     * @param counters quality counters
     * @param blackhole result sink
     * @return evaluated token count for this operation
     * @throws IOException if token filtering cannot run
     */
    @Benchmark
    @Warmup(iterations = 0)
    @Measurement(iterations = 1, time = 1, timeUnit = java.util.concurrent.TimeUnit.MILLISECONDS)
    @Fork(0)
    public long cistemStyleQuality(final SharedState state, final GoldstandardQualityCounters counters,
            final Blackhole blackhole) throws IOException {
        final GoldstandardResult result = evaluateCistemStyle(state.corpus, state.allTokens, state.stemmer, blackhole);
        counters.truePositives += result.truePositives();
        counters.falsePositives += result.falsePositives();
        counters.falseNegatives += result.falseNegatives();
        counters.evaluatedClusters += result.evaluatedClusters();
        counters.evaluatedTokens += result.evaluatedTokens();
        return result.evaluatedTokens();
    }

    /**
     * Benchmarks candidate throughput over the selected gold standard.
     *
     * @param state shared benchmark state
     * @param blackhole result sink
     * @throws IOException if token filtering cannot run
     */
    @Benchmark
    public void cistemStyleSpeed(final SharedState state, final Blackhole blackhole) throws IOException {
        state.stemmer.stem(state.allTokens, blackhole);
    }

    /**
     * Named German candidates used for the CISTEM gold-standard comparison.
     */
    private enum GermanCandidate {
        GERMAN_RADIXOR,
        GERMAN_LUCENE_GERMAN_STEM_FILTER,
        GERMAN_LUCENE_GERMAN_LIGHT_STEM_FILTER,
        GERMAN_LUCENE_GERMAN_MINIMAL_STEM_FILTER,
        GERMAN_CISTEM,
        SNOWBALL_GERMAN_DIRECT,
        SNOWBALL_GERMAN_LUCENE_FILTER;

        /**
         * Creates a candidate evaluator.
         *
         * @return stemmer evaluator
         * @throws IOException if trie resources cannot be loaded
         */
        GoldstandardStemmer createEvaluator() throws IOException {
            return switch (this) {
                case GERMAN_RADIXOR -> direct(createGermanRadixorStemmer());
                case GERMAN_LUCENE_GERMAN_STEM_FILTER ->
                    tokenFilter(input -> new GermanStemFilter(lowercase(input)));
                case GERMAN_LUCENE_GERMAN_LIGHT_STEM_FILTER ->
                    tokenFilter(input -> new GermanLightStemFilter(germanNormalize(input)));
                case GERMAN_LUCENE_GERMAN_MINIMAL_STEM_FILTER ->
                    tokenFilter(input -> new GermanMinimalStemFilter(germanNormalize(input)));
                case GERMAN_CISTEM -> direct(Cistem::stem);
                case SNOWBALL_GERMAN_DIRECT -> direct(SnowballLanguageCase.GERMAN.createDirectStemmer()::stem);
                case SNOWBALL_GERMAN_LUCENE_FILTER ->
                    tokenFilter(input -> new SnowballFilter(new LowerCaseFilter(input),
                            SnowballLanguageCase.GERMAN.luceneSnowballName()));
            };
        }
    }

    /**
     * Evaluates one full corpus through CISTEM-style cluster scoring.
     *
     * <p>
     * For each cluster, the most frequent predicted stem is considered the
     * cluster main stem. TP are cluster words mapped to this stem, FN are
     * words mapped elsewhere inside the same cluster, and FP are words from
     * other clusters mapped to the same main stem.
     * </p>
     *
     * @param corpus parsed gold standard corpus
     * @param allTokens flattened token sequence
     * @param stemmer candidate stemmer
     * @param blackhole result sink
     * @return aggregated TP/FP/FN counters and token metrics
     * @throws IOException when token filtering cannot run
     */
    private static GoldstandardResult evaluateCistemStyle(final GermanGoldstandardCorpus corpus,
            final String[] allTokens, final GoldstandardStemmer stemmer, final Blackhole blackhole) throws IOException {
        final String[] predicted = stemmer.stem(allTokens, blackhole);
        final Map<String, Integer> globalPredictions = new LinkedHashMap<>();
        for (int index = 0; index < allTokens.length; index++) {
            final String prediction = normalizePrediction(predicted[index], allTokens[index]);
            globalPredictions.put(prediction, globalPredictions.getOrDefault(prediction, 0) + 1);
        }

        long truePositives = 0L;
        long falsePositives = 0L;
        long falseNegatives = 0L;
        int tokenOffset = 0;
        for (final String[] cluster : corpus.clusters()) {
            if (cluster.length == 0) {
                continue;
            }

            final Map<String, Integer> localPredictions = new LinkedHashMap<>();
            for (int index = 0; index < cluster.length; index++) {
                final int tokenIndex = tokenOffset + index;
                final String word = allTokens[tokenIndex];
                final String prediction = normalizePrediction(predicted[tokenIndex], word);
                localPredictions.put(prediction, localPredictions.getOrDefault(prediction, 0) + 1);
            }

            final String mainStem = mostFrequent(localPredictions);
            final int predictedAsMain = localPredictions.get(mainStem);
            final int clusterSize = cluster.length;
            final int falseNegative = clusterSize - predictedAsMain;
            final int falsePositive = globalPredictions.get(mainStem) - predictedAsMain;

            truePositives += predictedAsMain;
            falseNegatives += falseNegative;
            falsePositives += falsePositive;
            tokenOffset += clusterSize;
        }

        return new GoldstandardResult(truePositives, falsePositives, falseNegatives, corpus.clusters().length,
                allTokens.length);
    }

    /**
     * Creates a direct evaluator.
     *
     * @param stemmer direct word stemmer
     * @return evaluator
     */
    private static GoldstandardStemmer direct(final Stemmer stemmer) {
        Objects.requireNonNull(stemmer, "stemmer");
        return (tokens, blackhole) -> {
            final String[] outputs = new String[tokens.length];
            for (int index = 0; index < tokens.length; index++) {
                final String output = stemmer.stem(tokens[index]);
                outputs[index] = output;
                blackhole.consume(output);
            }
            return outputs;
        };
    }

    /**
     * Creates a TokenFilter evaluator.
     *
     * @param factory filter stream factory
     * @return evaluator
     */
    private static GoldstandardStemmer tokenFilter(final Function<TokenStream, TokenStream> factory) {
        Objects.requireNonNull(factory, "factory");
        return (tokens, blackhole) -> firstTokenFilterOutputs(tokens, factory, blackhole);
    }

    /**
     * Loads and parses one gold standard file from generated JMH resources.
     *
     * @param resourceName gold standard file name
     * @return parsed corpus
     * @throws IOException if reading fails
     */
    private static GermanGoldstandardCorpus loadCorpus(final String resourceName) throws IOException {
        final ClassLoader classLoader = GermanGoldstandardStemmerComparisonBenchmark.class.getClassLoader();
        final InputStream resourceStream = classLoader.getResourceAsStream(resourceName);
        if (resourceStream == null) {
            throw new IllegalStateException("Missing generated CISTEM gold standard resource: " + resourceName
                    + ". Run the Gradle JMH resource preparation task to download benchmark-only inputs.");
        }
        try (InputStream input = resourceStream) {
            return parseCorpus(new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)));
        }
    }

    /**
     * Parses CISTEM gold standard format into clustered candidates.
     *
     * @param reader UTF-8 reader
     * @return parsed corpus
     * @throws IOException if input cannot be read
     */
    private static GermanGoldstandardCorpus parseCorpus(final BufferedReader reader) throws IOException {
        final List<String[]> clusters = new ArrayList<>();
        String line = reader.readLine();
        while (line != null) {
            final String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                final String[] words = trimmed.split("\\s+");
                if (words.length > 0) {
                    clusters.add(words);
                }
            }
            line = reader.readLine();
        }
        return new GermanGoldstandardCorpus(clusters.toArray(String[][]::new));
    }

    /**
     * Flattens the corpus in deterministic cluster order.
     *
     * @param corpus corpus to flatten
     * @return flattened token array
     */
    private static String[] flattenCorpusTokens(final GermanGoldstandardCorpus corpus) {
        int total = 0;
        for (final String[] cluster : corpus.clusters()) {
            total += cluster.length;
        }
        final String[] tokens = new String[total];
        int index = 0;
        for (final String[] cluster : corpus.clusters()) {
            System.arraycopy(cluster, 0, tokens, index, cluster.length);
            index += cluster.length;
        }
        return tokens;
    }

    /**
     * Returns the most frequent key; insertion order is preserved on ties.
     *
     * @param frequencies predicted stem frequencies
     * @return most frequent stem
     */
    private static String mostFrequent(final Map<String, Integer> frequencies) {
        String best = null;
        int bestCount = -1;
        for (final Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    /**
     * Normalizes a null/empty prediction using the input token as fallback.
     *
     * @param prediction stemmed token
     * @param fallback fallback token
     * @return safe prediction
     */
    private static String normalizePrediction(final String prediction, final String fallback) {
        if (prediction == null || prediction.isEmpty()) {
            return fallback;
        }
        return prediction;
    }

    /**
     * Applies one TokenFilter to all input tokens and returns the first emitted term
     * for each input token.
     *
     * @param tokens input token corpus
     * @param factory TokenFilter factory
     * @param blackhole result sink
     * @return first emitted term per input token
     * @throws IOException if token streaming fails
     */
    private static String[] firstTokenFilterOutputs(final String[] tokens, final Function<TokenStream, TokenStream> factory,
            final Blackhole blackhole) throws IOException {
        final String[] outputs = new String[tokens.length];
        final BenchmarkTokenStream input = new BenchmarkTokenStream(tokens);
        final TokenStream output = factory.apply(input);
        final CharTermAttribute termAttribute = output.addAttribute(CharTermAttribute.class);
        final PositionIncrementAttribute positionAttribute = output.addAttribute(PositionIncrementAttribute.class);

        int inputIndex = -1;
        boolean recordedForPosition = false;
        output.reset();
        while (output.incrementToken()) {
            final int positionIncrement = positionAttribute.getPositionIncrement();
            if (positionIncrement > 0) {
                inputIndex += positionIncrement;
                recordedForPosition = false;
            }
            if (inputIndex >= 0 && inputIndex < outputs.length && !recordedForPosition) {
                outputs[inputIndex] = termAttribute.toString();
                blackhole.consume(termAttribute);
                recordedForPosition = true;
            }
        }
        output.end();
        output.close();

        for (int index = 0; index < outputs.length; index++) {
            if (outputs[index] == null) {
                outputs[index] = tokens[index];
            }
        }
        return outputs;
    }

    /**
     * Creates a direct Radixor evaluator using the contracted dictionary trie.
     *
     * @return direct Radixor stemmer
     * @throws IOException if the trie cannot be loaded
     */
    private static Stemmer createGermanRadixorStemmer() throws IOException {
        return new RadixorBenchmarkStemmer(StemmerPatchTrieLoader.loadCompiled(
                StemmerPatchTrieLoader.Language.DE_DE, true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS))::stem;
    }

    /**
     * Adds Lucene lower-case normalization.
     *
     * @param input token stream
     * @return normalized token stream
     */
    private static TokenStream lowercase(final TokenStream input) {
        return new LowerCaseFilter(input);
    }

    /**
     * Adds Lucene German normalization for light and minimal filters.
     *
     * @param input token stream
     * @return normalized token stream
     */
    private static TokenStream germanNormalize(final TokenStream input) {
        return new GermanNormalizationFilter(lowercase(input));
    }

    /**
     * Direct or filter stemmer adapter used by this benchmark.
     */
    @FunctionalInterface
    private interface GoldstandardStemmer {

        /**
         * Runs one complete token list.
         *
         * @param tokens input tokens
         * @param blackhole result sink
         * @return per-token outputs
         * @throws IOException if filter processing fails
         */
        String[] stem(String[] tokens, Blackhole blackhole) throws IOException;
    }

    /**
     * Deterministic direct word stem function.
     */
    @FunctionalInterface
    private interface Stemmer {

        /**
         * Stems one token.
         *
         * @param token input token
         * @return stemmed token
         */
        String stem(String token);
    }

    /**
     * Immutable parsed CISTEM gold standard corpus.
     */
    private static final class GermanGoldstandardCorpus {

        private final String[][] clusters;

        GermanGoldstandardCorpus(final String[][] clusters) {
            this.clusters = clusters;
        }

        String[][] clusters() {
            return this.clusters;
        }
    }

    /**
     * Aggregated quality result for one benchmark operation.
     *
     * @param truePositives true positives
     * @param falsePositives false positives
     * @param falseNegatives false negatives
     * @param evaluatedClusters evaluated clusters
     * @param evaluatedTokens evaluated tokens
     */
    private record GoldstandardResult(long truePositives, long falsePositives, long falseNegatives,
            long evaluatedClusters, long evaluatedTokens) {
    }
}
