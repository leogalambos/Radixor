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
import java.io.InputStream;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.HunspellStemFilter;
import org.apache.lucene.analysis.hunspell.SortingStrategy;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.egothor.stemmer.StemmerPatchTrieLoader;
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

/**
 * Emits exact-root agreement metrics for the benchmark-only Hunspell comparisons.
 *
 * <p>
 * This class mirrors the existing Hunspell throughput setup but adds
 * quality-style accuracy counters for every Hunspell language dictionary used
 * in benchmark-only throughput comparisons.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.MILLISECONDS)
@Fork(0)
public class HunspellStemmerComparisonBenchmarkQuality {

    /**
     * Shared quality corpus and Hunspell dictionary for a selected language.
     */
    @State(Scope.Benchmark)
    public static class SharedState {

        /**
         * Selected language case.
         */
        @Param({ "ENGLISH", "CZECH", "GERMAN", "SPANISH", "FRENCH", "DUTCH", "POLISH", "UKRAINIAN" })
        public String languageCaseName;

        /**
         * Selected language descriptor.
         */
        private HunspellLanguageCase languageCase;

        /**
         * Complete language dictionary corpus and expected roots.
         */
        private LanguageBenchmarkCorpus.Corpus corpus;

        /**
         * Parsed benchmark-only Hunspell dictionary.
         */
        private Dictionary dictionary;

        /**
         * Initializes quality resources.
         *
         * @throws IOException    if corpus or dictionary loading fails
         * @throws ParseException if the Hunspell dictionary cannot be parsed
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException, ParseException {
            this.languageCase = HunspellLanguageCase.valueOf(this.languageCaseName);
            this.corpus = LanguageBenchmarkCorpus.createFullCorpus(this.languageCase.radixorLanguage());
            this.dictionary = loadDictionary(this.languageCase);
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
     * Evaluates exact-root agreement for the selected Hunspell dictionary.
     *
     * @param sharedState shared quality state
     * @param counters    JMH auxiliary counters
     * @param blackhole   result sink
     * @return exact-root match count
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public int luceneHunspellStemFilterAccuracy(final SharedState sharedState, final AccuracyCounters counters,
            final Blackhole blackhole) throws IOException {
        final String[] actualStems = firstHunspellOutputs(sharedState.corpus.tokens(), sharedState.dictionary,
                blackhole);
        final String[] tokens = sharedState.corpus.tokens();
        final String[] expectedRoots = sharedState.corpus.expectedRoots();

        int correct = 0;
        int changedCorrect = 0;
        int changedEvaluated = 0;
        int rootPreserved = 0;
        int rootEvaluated = 0;
        for (int index = 0; index < actualStems.length; index++) {
            final String token = tokens[index];
            final String expectedRoot = expectedRoots[index];
            final boolean exact = Objects.equals(expectedRoot, actualStems[index]);
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
        counters.evaluatedTokens += actualStems.length;
        counters.changedCorrectMatches += changedCorrect;
        counters.changedEvaluatedTokens += changedEvaluated;
        counters.rootPreservedMatches += rootPreserved;
        counters.rootEvaluatedTokens += rootEvaluated;
        return correct;
    }

    /**
     * Extracts the first emitted Hunspell stem for each input token.
     *
     * @param tokens     token corpus
     * @param dictionary Hunspell dictionary
     * @param blackhole  result sink
     * @return first emitted term per input token
     * @throws IOException if Lucene streaming fails
     */
    private static String[] firstHunspellOutputs(final String[] tokens, final Dictionary dictionary,
            final Blackhole blackhole) throws IOException {
        final String[] outputs = new String[tokens.length];
        final BenchmarkTokenStream input = new BenchmarkTokenStream(tokens);
        final TokenStream output = new HunspellStemFilter(new LowerCaseFilter(input), dictionary, true);
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
                recordedForPosition = true;
            }
            blackhole.consume(termAttribute);
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
     * Loads a benchmark-only Hunspell dictionary from generated resources.
     *
     * @param languageCase selected language case
     * @return parsed dictionary
     * @throws IOException    if dictionary resources cannot be read
     * @throws ParseException if dictionary parsing fails
     */
    private static Dictionary loadDictionary(final HunspellLanguageCase languageCase) throws IOException,
            ParseException {
        final ClassLoader classLoader = HunspellStemmerComparisonBenchmarkQuality.class.getClassLoader();
        final String basePath = "hunspell/" + languageCase.hunspellResourceCode() + "/index.";
        try (InputStream affixStream = openRequiredResource(classLoader, basePath + "aff");
                InputStream dictionaryStream = openRequiredResource(classLoader, basePath + "dic")) {
            return new Dictionary(affixStream, List.of(dictionaryStream), true, SortingStrategy.inMemory());
        }
    }

    /**
     * Opens a required classpath resource.
     *
     * @param classLoader class loader
     * @param path        resource path
     * @return resource stream
     */
    private static InputStream openRequiredResource(final ClassLoader classLoader, final String path) {
        final InputStream stream = classLoader.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Missing benchmark-only Hunspell resource: " + path);
        }
        return stream;
    }

    /**
     * Benchmark language mapping.
     */
    private enum HunspellLanguageCase {

        /**
         * English Hunspell dictionary over the Radixor English corpus.
         */
        ENGLISH("en", StemmerPatchTrieLoader.Language.US_UK),

        /**
         * Czech Hunspell dictionary over the Radixor Czech corpus.
         */
        CZECH("cs", StemmerPatchTrieLoader.Language.CS_CZ),

        /**
         * German Hunspell dictionary over the Radixor German corpus.
         */
        GERMAN("de", StemmerPatchTrieLoader.Language.DE_DE),

        /**
         * Spanish Hunspell dictionary over the Radixor Spanish corpus.
         */
        SPANISH("es", StemmerPatchTrieLoader.Language.ES_ES),

        /**
         * French Hunspell dictionary over the Radixor French corpus.
         */
        FRENCH("fr", StemmerPatchTrieLoader.Language.FR_FR),

        /**
         * Dutch Hunspell dictionary over the Radixor Dutch corpus.
         */
        DUTCH("nl", StemmerPatchTrieLoader.Language.NL_NL),

        /**
         * Polish Hunspell dictionary over the Radixor Polish corpus.
         */
        POLISH("pl", StemmerPatchTrieLoader.Language.PL_PL),

        /**
         * Ukrainian Hunspell dictionary over the Radixor Ukrainian corpus.
         */
        UKRAINIAN("uk", StemmerPatchTrieLoader.Language.UK_UA);

        /**
         * Wooorm/dictionaries resource code.
         */
        private final String hunspellResourceCode;

        /**
         * Matching Radixor language.
         */
        private final StemmerPatchTrieLoader.Language radixorLanguage;

        /**
         * Creates a language mapping.
         *
         * @param hunspellResourceCode Hunspell resource code
         * @param radixorLanguage      Radixor language
         */
        HunspellLanguageCase(final String hunspellResourceCode, final StemmerPatchTrieLoader.Language radixorLanguage) {
            this.hunspellResourceCode = hunspellResourceCode.toLowerCase(Locale.ROOT);
            this.radixorLanguage = radixorLanguage;
        }

        /**
         * Returns the Hunspell resource code.
         *
         * @return resource code
         */
        String hunspellResourceCode() {
            return this.hunspellResourceCode;
        }

        /**
         * Returns the matching Radixor language.
         *
         * @return Radixor language
         */
        StemmerPatchTrieLoader.Language radixorLanguage() {
            return this.radixorLanguage;
        }
    }
}
