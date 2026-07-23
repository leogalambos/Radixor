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
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ar.ArabicNormalizationFilter;
import org.apache.lucene.analysis.core.DecimalDigitFilter;
import org.apache.lucene.analysis.cz.CzechStemFilter;
import org.apache.lucene.analysis.de.GermanLightStemFilter;
import org.apache.lucene.analysis.de.GermanMinimalStemFilter;
import org.apache.lucene.analysis.de.GermanNormalizationFilter;
import org.apache.lucene.analysis.de.GermanStemFilter;
import org.apache.lucene.analysis.es.SpanishLightStemFilter;
import org.apache.lucene.analysis.es.SpanishMinimalStemFilter;
import org.apache.lucene.analysis.es.SpanishPluralStemFilter;
import org.apache.lucene.analysis.fa.PersianNormalizationFilter;
import org.apache.lucene.analysis.fa.PersianStemFilter;
import org.apache.lucene.analysis.fi.FinnishLightStemFilter;
import org.apache.lucene.analysis.fr.FrenchLightStemFilter;
import org.apache.lucene.analysis.fr.FrenchMinimalStemFilter;
import org.apache.lucene.analysis.hu.HungarianLightStemFilter;
import org.apache.lucene.analysis.it.ItalianLightStemFilter;
import org.apache.lucene.analysis.morfologik.MorfologikFilter;
import org.apache.lucene.analysis.no.NorwegianLightStemFilter;
import org.apache.lucene.analysis.no.NorwegianMinimalStemFilter;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseLightStemFilter;
import org.apache.lucene.analysis.pt.PortugueseMinimalStemFilter;
import org.apache.lucene.analysis.pt.PortugueseStemFilter;
import org.apache.lucene.analysis.ru.RussianLightStemFilter;
import org.apache.lucene.analysis.stempel.StempelFilter;
import org.apache.lucene.analysis.stempel.StempelStemmer;
import org.apache.lucene.analysis.sv.SwedishLightStemFilter;
import org.apache.lucene.analysis.sv.SwedishMinimalStemFilter;
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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import morfologik.stemming.Dictionary;
import morfologik.stemming.DictionaryLookup;
import morfologik.stemming.WordData;

/**
 * Compares Radixor with public Lucene stemmer integration paths for languages
 * where both systems have a matching bundled resource.
 *
 * <p>
 * Each benchmark operation processes the same changed-token dictionary corpus
 * for one language, repeated only when the changed-token resource contains
 * fewer than 5,000 token fields. The token corpus is built during trial setup
 * from Radixor's registered default-model dictionary for that same language. Lucene TokenFilter
 * methods include TokenStream and attribute overhead; direct Stempel measures
 * the public table-driven stemmer API without TokenFilter overhead.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@SuppressWarnings("deprecation")
public class MultiLanguageStemmerComparisonBenchmark {

    /**
     * Shared language corpus and Radixor trie state.
     */
    @State(Scope.Benchmark)
    public static class SharedState {

        /**
         * Czech benchmark state.
         */
        private LanguageState czech;

        /**
         * German benchmark state.
         */
        private LanguageState german;

        /**
         * Spanish benchmark state.
         */
        private LanguageState spanish;

        /**
         * Persian benchmark state.
         */
        private LanguageState persian;

        /**
         * Finnish benchmark state.
         */
        private LanguageState finnish;

        /**
         * French benchmark state.
         */
        private LanguageState french;

        /**
         * Hebrew benchmark state.
         */
        private LanguageState hebrew;

        /**
         * Hungarian benchmark state.
         */
        private LanguageState hungarian;

        /**
         * Italian benchmark state.
         */
        private LanguageState italian;

        /**
         * Norwegian Bokmal benchmark state.
         */
        private LanguageState norwegianBokmal;

        /**
         * Polish benchmark state.
         */
        private LanguageState polish;

        /**
         * Portuguese benchmark state.
         */
        private LanguageState portuguese;

        /**
         * Russian benchmark state.
         */
        private LanguageState russian;

        /**
         * Swedish benchmark state.
         */
        private LanguageState swedish;

        /**
         * Ukrainian benchmark state.
         */
        private LanguageState ukrainian;

        /**
         * Initializes all language resources before measurement.
         *
         * @throws IOException if a bundled language resource cannot be loaded
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            this.czech = load(StemmerPatchTrieLoader.Language.CS_CZ);
            this.german = load(StemmerPatchTrieLoader.Language.DE_DE);
            this.spanish = load(StemmerPatchTrieLoader.Language.ES_ES);
            this.persian = load(StemmerPatchTrieLoader.Language.FA_IR);
            this.finnish = load(StemmerPatchTrieLoader.Language.FI_FI);
            this.french = load(StemmerPatchTrieLoader.Language.FR_FR);
            this.hebrew = load(StemmerPatchTrieLoader.Language.HE_IL);
            this.hungarian = load(StemmerPatchTrieLoader.Language.HU_HU);
            this.italian = load(StemmerPatchTrieLoader.Language.IT_IT);
            this.norwegianBokmal = load(StemmerPatchTrieLoader.Language.NB_NO);
            this.polish = load(StemmerPatchTrieLoader.Language.PL_PL);
            this.portuguese = load(StemmerPatchTrieLoader.Language.PT_PT);
            this.russian = load(StemmerPatchTrieLoader.Language.RU_RU);
            this.swedish = load(StemmerPatchTrieLoader.Language.SV_SE);
            this.ukrainian = load(StemmerPatchTrieLoader.Language.UK_UA);
        }
    }

    /**
     * Per-thread Lucene filter state.
     */
    @State(Scope.Thread)
    public static class LuceneFilterState {

        /**
         * Czech stem filter.
         */
        private final FilterPipeline czechStem = new FilterPipeline(
                input -> new CzechStemFilter(lowercase(input)));

        /**
         * German classic stem filter.
         */
        private final FilterPipeline germanStem = new FilterPipeline(
                input -> new GermanStemFilter(lowercase(input)));

        /**
         * German light stem filter.
         */
        private final FilterPipeline germanLightStem = new FilterPipeline(
                input -> new GermanLightStemFilter(germanNormalize(input)));

        /**
         * German minimal stem filter.
         */
        private final FilterPipeline germanMinimalStem = new FilterPipeline(
                input -> new GermanMinimalStemFilter(germanNormalize(input)));

        /**
         * Spanish light stem filter.
         */
        private final FilterPipeline spanishLightStem = new FilterPipeline(
                input -> new SpanishLightStemFilter(lowercase(input)));

        /**
         * Spanish minimal stem filter.
         */
        private final FilterPipeline spanishMinimalStem = new FilterPipeline(
                input -> new SpanishMinimalStemFilter(lowercase(input)));

        /**
         * Spanish plural stem filter.
         */
        private final FilterPipeline spanishPluralStem = new FilterPipeline(
                input -> new SpanishPluralStemFilter(lowercase(input)));

        /**
         * Persian stem filter.
         */
        private final FilterPipeline persianStem = new FilterPipeline(
                input -> new PersianStemFilter(persianNormalize(input)));

        /**
         * Finnish light stem filter.
         */
        private final FilterPipeline finnishLightStem = new FilterPipeline(
                input -> new FinnishLightStemFilter(lowercase(input)));

        /**
         * French light stem filter.
         */
        private final FilterPipeline frenchLightStem = new FilterPipeline(
                input -> new FrenchLightStemFilter(lowercase(input)));

        /**
         * French minimal stem filter.
         */
        private final FilterPipeline frenchMinimalStem = new FilterPipeline(
                input -> new FrenchMinimalStemFilter(lowercase(input)));

        /**
         * Hungarian light stem filter.
         */
        private final FilterPipeline hungarianLightStem = new FilterPipeline(
                input -> new HungarianLightStemFilter(lowercase(input)));

        /**
         * Italian light stem filter.
         */
        private final FilterPipeline italianLightStem = new FilterPipeline(
                input -> new ItalianLightStemFilter(lowercase(input)));

        /**
         * Norwegian light stem filter.
         */
        private final FilterPipeline norwegianLightStem = new FilterPipeline(
                input -> new NorwegianLightStemFilter(lowercase(input)));

        /**
         * Norwegian minimal stem filter.
         */
        private final FilterPipeline norwegianMinimalStem = new FilterPipeline(
                input -> new NorwegianMinimalStemFilter(lowercase(input)));

        /**
         * Polish Stempel token filter.
         */
        private final FilterPipeline polishStempelStem = new FilterPipeline(
                input -> new StempelFilter(input, new StempelStemmer(PolishAnalyzer.getDefaultTable())));

        /**
         * Polish Morfologik token filter.
         */
        private final FilterPipeline polishMorfologik = new FilterPipeline(MorfologikFilter::new);

        /**
         * Portuguese full stem filter.
         */
        private final FilterPipeline portugueseStem = new FilterPipeline(
                input -> new PortugueseStemFilter(lowercase(input)));

        /**
         * Portuguese light stem filter.
         */
        private final FilterPipeline portugueseLightStem = new FilterPipeline(
                input -> new PortugueseLightStemFilter(lowercase(input)));

        /**
         * Portuguese minimal stem filter.
         */
        private final FilterPipeline portugueseMinimalStem = new FilterPipeline(
                input -> new PortugueseMinimalStemFilter(lowercase(input)));

        /**
         * Russian light stem filter.
         */
        private final FilterPipeline russianLightStem = new FilterPipeline(
                input -> new RussianLightStemFilter(lowercase(input)));

        /**
         * Swedish light stem filter.
         */
        private final FilterPipeline swedishLightStem = new FilterPipeline(
                input -> new SwedishLightStemFilter(lowercase(input)));

        /**
         * Swedish minimal stem filter.
         */
        private final FilterPipeline swedishMinimalStem = new FilterPipeline(
                input -> new SwedishMinimalStemFilter(lowercase(input)));

        /**
         * Ukrainian Morfologik token filter.
         */
        private FilterPipeline ukrainianMorfologik;

        /**
         * Initializes filter state that needs benchmark-only dictionary resources.
         *
         * @throws IOException if a benchmark-only dictionary cannot be loaded
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            final Dictionary dictionary = loadUkrainianMorfologikDictionary();
            this.ukrainianMorfologik = new FilterPipeline(input -> new MorfologikFilter(input, dictionary));
        }
    }

    /**
     * Per-thread direct non-TokenFilter stemmer state.
     */
    @State(Scope.Thread)
    public static class DirectState {

        /**
         * Direct Stempel stemmer using Lucene's default Polish table.
         */
        private StempelStemmer polishStempelStemmer;

        /**
         * Direct Ukrainian Morfologik dictionary lookup.
         */
        private DictionaryLookup ukrainianMorfologikLookup;

        /**
         * Initializes direct stemmer instances before measurement.
         *
         * @throws IOException if a benchmark-only dictionary cannot be loaded
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            this.polishStempelStemmer = new StempelStemmer(PolishAnalyzer.getDefaultTable());
            this.ukrainianMorfologikLookup = new DictionaryLookup(loadUkrainianMorfologikDictionary());
        }
    }

    /**
     * Runs Radixor over the Czech corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void czechRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.czech, blackhole);
    }

    /**
     * Runs Lucene CzechStemFilter over the Czech corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void czechLuceneCzechStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.czechStem.run(sharedState.czech.tokens, blackhole);
    }

    /**
     * Runs Radixor over the German corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void germanRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.german, blackhole);
    }

    /**
     * Runs Lucene GermanStemFilter over the German corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void germanLuceneGermanStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.germanStem.run(sharedState.german.tokens, blackhole);
    }

    /**
     * Runs Lucene GermanLightStemFilter over the German corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void germanLuceneGermanLightStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.germanLightStem.run(sharedState.german.tokens, blackhole);
    }

    /**
     * Runs Lucene GermanMinimalStemFilter over the German corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void germanLuceneGermanMinimalStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.germanMinimalStem.run(sharedState.german.tokens, blackhole);
    }

    /**
     * Runs CISTEM directly over the German corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void germanCistem(final SharedState sharedState, final Blackhole blackhole) {
        for (final String token : sharedState.german.tokens) {
            blackhole.consume(Cistem.stem(token));
        }
    }

    /**
     * Runs Radixor over the Spanish corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void spanishRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.spanish, blackhole);
    }

    /**
     * Runs Lucene SpanishLightStemFilter over the Spanish corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void spanishLuceneSpanishLightStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.spanishLightStem.run(sharedState.spanish.tokens, blackhole);
    }

    /**
     * Runs Lucene SpanishMinimalStemFilter over the Spanish corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void spanishLuceneSpanishMinimalStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.spanishMinimalStem.run(sharedState.spanish.tokens, blackhole);
    }

    /**
     * Runs Lucene SpanishPluralStemFilter over the Spanish corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void spanishLuceneSpanishPluralStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.spanishPluralStem.run(sharedState.spanish.tokens, blackhole);
    }

    /**
     * Runs Radixor over the Persian corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void persianRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.persian, blackhole);
    }

    /**
     * Runs Lucene PersianStemFilter over the Persian corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void persianLucenePersianStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.persianStem.run(sharedState.persian.tokens, blackhole);
    }

    /**
     * Runs Radixor over the Finnish corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void finnishRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.finnish, blackhole);
    }

    /**
     * Runs Lucene FinnishLightStemFilter over the Finnish corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void finnishLuceneFinnishLightStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.finnishLightStem.run(sharedState.finnish.tokens, blackhole);
    }

    /**
     * Runs Radixor over the French corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void frenchRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.french, blackhole);
    }

    /**
     * Runs Radixor over the Hebrew corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void hebrewRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.hebrew, blackhole);
    }

    /**
     * Runs Lucene FrenchLightStemFilter over the French corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void frenchLuceneFrenchLightStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.frenchLightStem.run(sharedState.french.tokens, blackhole);
    }

    /**
     * Runs Lucene FrenchMinimalStemFilter over the French corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void frenchLuceneFrenchMinimalStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.frenchMinimalStem.run(sharedState.french.tokens, blackhole);
    }

    /**
     * Runs Radixor over the Hungarian corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void hungarianRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.hungarian, blackhole);
    }

    /**
     * Runs Lucene HungarianLightStemFilter over the Hungarian corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void hungarianLuceneHungarianLightStemFilter(final SharedState sharedState,
            final LuceneFilterState filterState, final Blackhole blackhole) throws IOException {
        filterState.hungarianLightStem.run(sharedState.hungarian.tokens, blackhole);
    }

    /**
     * Runs Radixor over the Italian corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void italianRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.italian, blackhole);
    }

    /**
     * Runs Lucene ItalianLightStemFilter over the Italian corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void italianLuceneItalianLightStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.italianLightStem.run(sharedState.italian.tokens, blackhole);
    }

    /**
     * Runs Radixor over the Norwegian Bokmal corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void norwegianBokmalRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.norwegianBokmal, blackhole);
    }

    /**
     * Runs Lucene NorwegianLightStemFilter over the Norwegian Bokmal corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void norwegianBokmalLuceneNorwegianLightStemFilter(final SharedState sharedState,
            final LuceneFilterState filterState, final Blackhole blackhole) throws IOException {
        filterState.norwegianLightStem.run(sharedState.norwegianBokmal.tokens, blackhole);
    }

    /**
     * Runs Lucene NorwegianMinimalStemFilter over the Norwegian Bokmal corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void norwegianBokmalLuceneNorwegianMinimalStemFilter(final SharedState sharedState,
            final LuceneFilterState filterState, final Blackhole blackhole) throws IOException {
        filterState.norwegianMinimalStem.run(sharedState.norwegianBokmal.tokens, blackhole);
    }

    /**
     * Runs Radixor over the Polish corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void polishRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.polish, blackhole);
    }

    /**
     * Runs Lucene Stempel direct API over the Polish corpus.
     *
     * @param sharedState shared benchmark state
     * @param directState reusable direct stemmer state
     * @param blackhole   result sink
     */
    @Benchmark
    public void polishLuceneStempelStemmerDirect(final SharedState sharedState, final DirectState directState,
            final Blackhole blackhole) {
        final String[] tokens = sharedState.polish.tokens;
        final StempelStemmer stemmer = directState.polishStempelStemmer;
        for (String token : tokens) {
            final StringBuilder stem = stemmer.stem(token);
            blackhole.consume(stem == null ? token : stem.toString());
        }
    }

    /**
     * Runs Lucene StempelFilter over the Polish corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void polishLuceneStempelFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.polishStempelStem.run(sharedState.polish.tokens, blackhole);
    }

    /**
     * Runs Lucene MorfologikFilter over the Polish corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void polishLuceneMorfologikFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.polishMorfologik.run(sharedState.polish.tokens, blackhole);
    }

    /**
     * Runs Radixor over the Portuguese corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void portugueseRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.portuguese, blackhole);
    }

    /**
     * Runs Lucene PortugueseStemFilter over the Portuguese corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void portugueseLucenePortugueseStemFilter(final SharedState sharedState,
            final LuceneFilterState filterState, final Blackhole blackhole) throws IOException {
        filterState.portugueseStem.run(sharedState.portuguese.tokens, blackhole);
    }

    /**
     * Runs Lucene PortugueseLightStemFilter over the Portuguese corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void portugueseLucenePortugueseLightStemFilter(final SharedState sharedState,
            final LuceneFilterState filterState, final Blackhole blackhole) throws IOException {
        filterState.portugueseLightStem.run(sharedState.portuguese.tokens, blackhole);
    }

    /**
     * Runs Lucene PortugueseMinimalStemFilter over the Portuguese corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void portugueseLucenePortugueseMinimalStemFilter(final SharedState sharedState,
            final LuceneFilterState filterState, final Blackhole blackhole) throws IOException {
        filterState.portugueseMinimalStem.run(sharedState.portuguese.tokens, blackhole);
    }

    /**
     * Runs Radixor over the Russian corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void russianRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.russian, blackhole);
    }

    /**
     * Runs Lucene RussianLightStemFilter over the Russian corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void russianLuceneRussianLightStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.russianLightStem.run(sharedState.russian.tokens, blackhole);
    }

    /**
     * Runs Radixor over the Swedish corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void swedishRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.swedish, blackhole);
    }

    /**
     * Runs Lucene SwedishLightStemFilter over the Swedish corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void swedishLuceneSwedishLightStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.swedishLightStem.run(sharedState.swedish.tokens, blackhole);
    }

    /**
     * Runs Lucene SwedishMinimalStemFilter over the Swedish corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void swedishLuceneSwedishMinimalStemFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.swedishMinimalStem.run(sharedState.swedish.tokens, blackhole);
    }

    /**
     * Runs Radixor over the Ukrainian corpus.
     *
     * @param sharedState shared benchmark state
     * @param blackhole   result sink
     */
    @Benchmark
    public void ukrainianRadixor(final SharedState sharedState, final Blackhole blackhole) {
        runRadixor(sharedState.ukrainian, blackhole);
    }

    /**
     * Runs direct Morfologik Ukrainian dictionary lookup over the Ukrainian corpus.
     *
     * @param sharedState shared benchmark state
     * @param directState reusable direct stemmer state
     * @param blackhole   result sink
     */
    @Benchmark
    public void ukrainianMorfologikDirect(final SharedState sharedState, final DirectState directState,
            final Blackhole blackhole) {
        final String[] tokens = sharedState.ukrainian.tokens;
        final DictionaryLookup lookup = directState.ukrainianMorfologikLookup;
        for (String token : tokens) {
            blackhole.consume(firstMorfologikStem(token, lookup));
        }
    }

    /**
     * Runs Lucene MorfologikFilter with the Ukrainian dictionary over the Ukrainian
     * corpus.
     *
     * @param sharedState shared benchmark state
     * @param filterState reusable filter state
     * @param blackhole   result sink
     * @throws IOException if Lucene token streaming fails
     */
    @Benchmark
    public void ukrainianLuceneMorfologikFilter(final SharedState sharedState, final LuceneFilterState filterState,
            final Blackhole blackhole) throws IOException {
        filterState.ukrainianMorfologik.run(sharedState.ukrainian.tokens, blackhole);
    }

    /**
     * Loads one language benchmark state.
     *
     * @param language bundled Radixor language
     * @return initialized language state
     * @throws IOException if the corpus or trie cannot be loaded
     */
    private static LanguageState load(final StemmerPatchTrieLoader.Language language) throws IOException {
        final String[] tokens = LanguageBenchmarkCorpus.createTokens(language);
        final FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadCompiled(language, true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        return new LanguageState(tokens, new RadixorBenchmarkStemmer(trie));
    }

    /**
     * Runs Radixor direct lookup and patch application.
     *
     * @param languageState language state
     * @param blackhole     result sink
     */
    private static void runRadixor(final LanguageState languageState, final Blackhole blackhole) {
        final String[] tokens = languageState.tokens;
        final RadixorBenchmarkStemmer stemmer = languageState.radixorStemmer;

        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }

    /**
     * Adds Lucene lower-case normalization to a measured filter pipeline.
     *
     * @param input input token stream
     * @return normalized stream
     */
    private static TokenStream lowercase(final TokenStream input) {
        return new LowerCaseFilter(input);
    }

    /**
     * Adds the German normalization path used by Lucene's German analyzer before
     * German light/minimal stemming.
     *
     * @param input input token stream
     * @return normalized stream
     */
    private static TokenStream germanNormalize(final TokenStream input) {
        return new GermanNormalizationFilter(lowercase(input));
    }

    /**
     * Adds the Persian normalization path used by Lucene's Persian analyzer before
     * Persian stemming.
     *
     * @param input input token stream
     * @return normalized stream
     */
    private static TokenStream persianNormalize(final TokenStream input) {
        TokenStream result = lowercase(input);
        result = new DecimalDigitFilter(result);
        result = new ArabicNormalizationFilter(result);
        result = new PersianNormalizationFilter(result);
        return result;
    }

    /**
     * Loads the benchmark-only Ukrainian Morfologik dictionary.
     *
     * @return Ukrainian Morfologik dictionary
     * @throws IOException if the dictionary cannot be loaded
     */
    private static Dictionary loadUkrainianMorfologikDictionary() throws IOException {
        final URL dictionaryUrl = MultiLanguageStemmerComparisonBenchmark.class.getClassLoader()
                .getResource("ua/net/nlp/ukrainian.dict");
        if (dictionaryUrl == null) {
            throw new IllegalStateException("Missing Ukrainian Morfologik dictionary resource.");
        }
        return Dictionary.read(dictionaryUrl);
    }

    /**
     * Returns the first Morfologik stem for one token.
     *
     * @param token  input token
     * @param lookup dictionary lookup
     * @return first Morfologik stem, or the input token when no analysis exists
     */
    private static String firstMorfologikStem(final String token, final DictionaryLookup lookup) {
        final List<WordData> analyses = lookup.lookup(token);
        if (analyses.isEmpty()) {
            return token;
        }
        return analyses.get(0).getStem().toString();
    }

    /**
     * Immutable language-specific state.
     */
    private static final class LanguageState {

        /**
         * Shared deterministic changed-token dictionary corpus.
         */
        private final String[] tokens;

        /**
         * Radixor benchmark adapter.
         */
        private final RadixorBenchmarkStemmer radixorStemmer;

        /**
         * Creates language state.
         *
         * @param tokens token corpus
         * @param radixorStemmer Radixor benchmark adapter
         */
        private LanguageState(final String[] tokens, final RadixorBenchmarkStemmer radixorStemmer) {
            this.tokens = tokens;
            this.radixorStemmer = radixorStemmer;
        }
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
