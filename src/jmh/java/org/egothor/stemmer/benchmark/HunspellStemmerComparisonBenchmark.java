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
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.HunspellStemFilter;
import org.apache.lucene.analysis.hunspell.SortingStrategy;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.egothor.stemmer.CompiledPatchCommand;
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
 * Compares Radixor with Lucene's Hunspell integration over selected
 * benchmark-only Hunspell dictionaries.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class HunspellStemmerComparisonBenchmark {

    /**
     * Parameterized benchmark case.
     */
    @State(Scope.Benchmark)
    public static class SharedState {

        /**
         * Selected language case.
         */
        @Param({ "ENGLISH", "CZECH", "GERMAN", "SPANISH", "FRENCH", "DUTCH", "POLISH", "UKRAINIAN" })
        public String languageCaseName;

        /**
         * Selected language case descriptor.
         */
        private HunspellLanguageCase languageCase;

        /**
         * Shared deterministic changed-token corpus.
         */
        private String[] tokens;

        /**
         * Radixor benchmark adapter.
         */
        private RadixorBenchmarkStemmer radixorStemmer;

        /**
         * Initializes the selected language corpus and Radixor stemmer.
         *
         * @throws IOException if the Radixor corpus or trie cannot be loaded
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            this.languageCase = HunspellLanguageCase.valueOf(this.languageCaseName);
            this.tokens = LanguageBenchmarkCorpus.createTokens(this.languageCase.radixorLanguage());
            final FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadCompiled(
                    this.languageCase.radixorLanguage(), true,
                    ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
            this.radixorStemmer = new RadixorBenchmarkStemmer(trie);
        }
    }

    /**
     * Reusable Hunspell filter state.
     */
    @State(Scope.Thread)
    public static class HunspellState {

        /**
         * Reusable benchmark input stream.
         */
        private BenchmarkTokenStream input;

        /**
         * Reusable Hunspell filter output stream.
         */
        private TokenStream output;

        /**
         * Output term attribute.
         */
        private CharTermAttribute termAttribute;

        /**
         * Initializes the Hunspell dictionary and filter for the selected language.
         *
         * @param sharedState selected language state
         * @throws IOException    if dictionary resources cannot be read
         * @throws ParseException if the Hunspell dictionary cannot be parsed
         */
        @Setup(Level.Trial)
        public void setUp(final SharedState sharedState) throws IOException, ParseException {
            this.input = new BenchmarkTokenStream(new String[0]);
            final Dictionary dictionary = loadDictionary(sharedState.languageCase);
            this.output = new HunspellStemFilter(new LowerCaseFilter(this.input), dictionary, true);
            this.termAttribute = this.output.addAttribute(CharTermAttribute.class);
        }

        /**
         * Runs Hunspell over one token corpus.
         *
         * @param tokens    token corpus
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

    /**
     * Runs Radixor direct lookup and patch application.
     *
     * @param sharedState selected language state
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
     * Runs Lucene HunspellStemFilter over the selected language corpus.
     *
     * @param sharedState selected language state
     * @param hunspellState reusable Hunspell state
     * @param blackhole result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void luceneHunspellStemFilter(final SharedState sharedState, final HunspellState hunspellState,
            final Blackhole blackhole) throws IOException {
        hunspellState.run(sharedState.tokens, blackhole);
    }

    /**
     * Loads a benchmark-only Hunspell dictionary from generated JMH resources.
     *
     * @param languageCase selected language case
     * @return parsed Hunspell dictionary
     * @throws IOException    if dictionary resources cannot be read
     * @throws ParseException if the Hunspell dictionary cannot be parsed
     */
    private static Dictionary loadDictionary(final HunspellLanguageCase languageCase) throws IOException,
            ParseException {
        final ClassLoader classLoader = HunspellStemmerComparisonBenchmark.class.getClassLoader();
        final String basePath = "hunspell/" + languageCase.hunspellResourceCode() + "/index.";
        try (InputStream affixStream = openRequiredResource(classLoader, basePath + "aff");
                InputStream dictionaryStream = openRequiredResource(classLoader, basePath + "dic")) {
            return new Dictionary(affixStream, List.of(dictionaryStream), true, SortingStrategy.inMemory());
        }
    }

    /**
     * Opens a classpath resource or fails with a descriptive exception.
     *
     * @param classLoader class loader
     * @param path resource path
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
         * wooorm/dictionaries resource code.
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
