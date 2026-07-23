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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ar.ArabicNormalizationFilter;
import org.apache.lucene.analysis.core.DecimalDigitFilter;
import org.apache.lucene.analysis.cz.CzechStemFilter;
import org.apache.lucene.analysis.de.GermanLightStemFilter;
import org.apache.lucene.analysis.de.GermanMinimalStemFilter;
import org.apache.lucene.analysis.de.GermanNormalizationFilter;
import org.apache.lucene.analysis.de.GermanStemFilter;
import org.apache.lucene.analysis.en.EnglishMinimalStemFilter;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.KStemFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
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
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.stempel.StempelFilter;
import org.apache.lucene.analysis.stempel.StempelStemmer;
import org.apache.lucene.analysis.sv.SwedishLightStemFilter;
import org.apache.lucene.analysis.sv.SwedishMinimalStemFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.egothor.stemmer.benchmark.snowball.ext.englishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.porterStemmer;
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

import morfologik.stemming.Dictionary;
import morfologik.stemming.DictionaryLookup;
import morfologik.stemming.WordData;

/**
 * Emits exact-root agreement metrics through standard JMH result files.
 *
 * <p>
 * This benchmark is a quality pass, not a throughput competitor. Each operation
 * evaluates one stemmer against the complete Radixor dictionary resource for
 * the matching language. The useful outputs are the JMH auxiliary counters
 * {@code correctMatches}, {@code evaluatedTokens},
 * {@code changedCorrectMatches}, {@code changedEvaluatedTokens},
 * {@code rootPreservedMatches}, and {@code rootEvaluatedTokens}.
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.MILLISECONDS)
@Fork(0)
public class StemmerComparisonBenchmarkQuality {

    /**
     * Shared quality state for one candidate stemmer.
     */
    @State(Scope.Benchmark)
    public static class QualityState {

        /**
         * Candidate stemmer whose exact-root agreement is measured.
         */
        @Param({
                "ENGLISH_RADIXOR",
                "ENGLISH_SNOWBALL_ORIGINAL_PORTER",
                "ENGLISH_SNOWBALL_PORTER2",
                "ENGLISH_LUCENE_PORTER_COPIED",
                "ENGLISH_LUCENE_PORTER_FILTER",
                "ENGLISH_LUCENE_KSTEM_FILTER",
                "ENGLISH_LUCENE_MINIMAL_FILTER",
                "ENGLISH_LUCENE_POSSESSIVE_FILTER",
                "ENGLISH_PAICE_HUSK_LANCASTER",
                "ENGLISH_OPENNLP_PORTER",
                "CZECH_RADIXOR",
                "CZECH_LUCENE_CZECH_STEM_FILTER",
                "GERMAN_RADIXOR",
                "GERMAN_LUCENE_GERMAN_STEM_FILTER",
                "GERMAN_LUCENE_GERMAN_LIGHT_STEM_FILTER",
                "GERMAN_LUCENE_GERMAN_MINIMAL_STEM_FILTER",
                "GERMAN_CISTEM",
                "SPANISH_RADIXOR",
                "SPANISH_LUCENE_SPANISH_LIGHT_STEM_FILTER",
                "SPANISH_LUCENE_SPANISH_MINIMAL_STEM_FILTER",
                "SPANISH_LUCENE_SPANISH_PLURAL_STEM_FILTER",
                "PERSIAN_RADIXOR",
                "PERSIAN_LUCENE_PERSIAN_STEM_FILTER",
                "FINNISH_RADIXOR",
                "FINNISH_LUCENE_FINNISH_LIGHT_STEM_FILTER",
                "FRENCH_RADIXOR",
                "FRENCH_LUCENE_FRENCH_LIGHT_STEM_FILTER",
                "FRENCH_LUCENE_FRENCH_MINIMAL_STEM_FILTER",
                "HUNGARIAN_RADIXOR",
                "HUNGARIAN_LUCENE_HUNGARIAN_LIGHT_STEM_FILTER",
                "ITALIAN_RADIXOR",
                "ITALIAN_LUCENE_ITALIAN_LIGHT_STEM_FILTER",
                "NORWEGIAN_BOKMAL_RADIXOR",
                "NORWEGIAN_BOKMAL_LUCENE_NORWEGIAN_LIGHT_STEM_FILTER",
                "NORWEGIAN_BOKMAL_LUCENE_NORWEGIAN_MINIMAL_STEM_FILTER",
                "POLISH_RADIXOR",
                "POLISH_LUCENE_STEMPEL_DIRECT",
                "POLISH_LUCENE_STEMPEL_FILTER",
                "POLISH_LUCENE_MORFOLOGIK_FILTER",
                "PORTUGUESE_RADIXOR",
                "PORTUGUESE_LUCENE_PORTUGUESE_STEM_FILTER",
                "PORTUGUESE_LUCENE_PORTUGUESE_LIGHT_STEM_FILTER",
                "PORTUGUESE_LUCENE_PORTUGUESE_MINIMAL_STEM_FILTER",
                "RUSSIAN_RADIXOR",
                "RUSSIAN_LUCENE_RUSSIAN_LIGHT_STEM_FILTER",
                "SWEDISH_RADIXOR",
                "SWEDISH_LUCENE_SWEDISH_LIGHT_STEM_FILTER",
                "SWEDISH_LUCENE_SWEDISH_MINIMAL_STEM_FILTER",
                "UKRAINIAN_RADIXOR",
                "UKRAINIAN_MORFOLOGIK_DIRECT",
                "UKRAINIAN_LUCENE_MORFOLOGIK_FILTER",
                "SNOWBALL_DANISH_DIRECT",
                "SNOWBALL_DANISH_LUCENE_FILTER",
                "SNOWBALL_DUTCH_DIRECT",
                "SNOWBALL_DUTCH_LUCENE_FILTER",
                "SNOWBALL_FINNISH_DIRECT",
                "SNOWBALL_FINNISH_LUCENE_FILTER",
                "SNOWBALL_FRENCH_DIRECT",
                "SNOWBALL_FRENCH_LUCENE_FILTER",
                "SNOWBALL_GERMAN_DIRECT",
                "SNOWBALL_GERMAN_LUCENE_FILTER",
                "SNOWBALL_HUNGARIAN_DIRECT",
                "SNOWBALL_HUNGARIAN_LUCENE_FILTER",
                "SNOWBALL_ITALIAN_DIRECT",
                "SNOWBALL_ITALIAN_LUCENE_FILTER",
                "SNOWBALL_NORWEGIAN_BOKMAL_DIRECT",
                "SNOWBALL_NORWEGIAN_BOKMAL_LUCENE_FILTER",
                "SNOWBALL_NORWEGIAN_NYNORSK_DIRECT",
                "SNOWBALL_NORWEGIAN_NYNORSK_LUCENE_FILTER",
                "SNOWBALL_PORTUGUESE_DIRECT",
                "SNOWBALL_PORTUGUESE_LUCENE_FILTER",
                "SNOWBALL_RUSSIAN_DIRECT",
                "SNOWBALL_RUSSIAN_LUCENE_FILTER",
                "SNOWBALL_SPANISH_DIRECT",
                "SNOWBALL_SPANISH_LUCENE_FILTER",
                "SNOWBALL_SWEDISH_DIRECT",
                "SNOWBALL_SWEDISH_LUCENE_FILTER",
                "SNOWBALL_YIDDISH_DIRECT",
                "SNOWBALL_YIDDISH_LUCENE_FILTER"
        })
        public String candidateName;

        /**
         * Full dictionary corpus for the selected language.
         */
        private LanguageBenchmarkCorpus.Corpus corpus;

        /**
         * Candidate evaluator.
         */
        private QualityEvaluator evaluator;

        /**
         * Initializes corpus and evaluator before measurement.
         *
         * @throws IOException if dictionary or stemmer resources cannot be loaded
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            final QualityCandidate candidate = QualityCandidate.valueOf(this.candidateName);
            this.corpus = LanguageBenchmarkCorpus.createFullCorpus(candidate.radixorLanguage());
            this.evaluator = candidate.createEvaluator();
        }
    }

    /**
     * JMH auxiliary counters for exact-root agreement.
     */
    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    public static class QualityCounters {

        /**
         * Number of outputs equal to the dictionary root.
         */
        public long correctMatches;

        /**
         * Number of evaluated input tokens.
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
     * Runs exact-root agreement over the full dictionary corpus.
     *
     * @param state quality state
     * @param counters auxiliary JMH counters
     * @param blackhole result sink
     * @return exact-root match count for this operation
     * @throws IOException if Lucene streaming fails
     */
    @Benchmark
    public int exactRootAgreement(final QualityState state, final QualityCounters counters, final Blackhole blackhole)
            throws IOException {
        final QualityResult result = state.evaluator.evaluate(state.corpus, blackhole);
        counters.correctMatches += result.correctMatches();
        counters.evaluatedTokens += result.evaluatedTokens();
        counters.changedCorrectMatches += result.changedCorrectMatches();
        counters.changedEvaluatedTokens += result.changedEvaluatedTokens();
        counters.rootPreservedMatches += result.rootPreservedMatches();
        counters.rootEvaluatedTokens += result.rootEvaluatedTokens();
        return result.correctMatches();
    }

    /**
     * Candidate stemmers that can be evaluated against a Radixor resource.
     */
    enum QualityCandidate {
        ENGLISH_RADIXOR(StemmerPatchTrieLoader.Language.US_UK),
        ENGLISH_SNOWBALL_ORIGINAL_PORTER(StemmerPatchTrieLoader.Language.US_UK),
        ENGLISH_SNOWBALL_PORTER2(StemmerPatchTrieLoader.Language.US_UK),
        ENGLISH_LUCENE_PORTER_COPIED(StemmerPatchTrieLoader.Language.US_UK),
        ENGLISH_LUCENE_PORTER_FILTER(StemmerPatchTrieLoader.Language.US_UK),
        ENGLISH_LUCENE_KSTEM_FILTER(StemmerPatchTrieLoader.Language.US_UK),
        ENGLISH_LUCENE_MINIMAL_FILTER(StemmerPatchTrieLoader.Language.US_UK),
        ENGLISH_LUCENE_POSSESSIVE_FILTER(StemmerPatchTrieLoader.Language.US_UK),
        ENGLISH_PAICE_HUSK_LANCASTER(StemmerPatchTrieLoader.Language.US_UK),
        ENGLISH_OPENNLP_PORTER(StemmerPatchTrieLoader.Language.US_UK),
        CZECH_RADIXOR(StemmerPatchTrieLoader.Language.CS_CZ),
        CZECH_LUCENE_CZECH_STEM_FILTER(StemmerPatchTrieLoader.Language.CS_CZ),
        GERMAN_RADIXOR(StemmerPatchTrieLoader.Language.DE_DE),
        GERMAN_LUCENE_GERMAN_STEM_FILTER(StemmerPatchTrieLoader.Language.DE_DE),
        GERMAN_LUCENE_GERMAN_LIGHT_STEM_FILTER(StemmerPatchTrieLoader.Language.DE_DE),
        GERMAN_LUCENE_GERMAN_MINIMAL_STEM_FILTER(StemmerPatchTrieLoader.Language.DE_DE),
        GERMAN_CISTEM(StemmerPatchTrieLoader.Language.DE_DE),
        SPANISH_RADIXOR(StemmerPatchTrieLoader.Language.ES_ES),
        SPANISH_LUCENE_SPANISH_LIGHT_STEM_FILTER(StemmerPatchTrieLoader.Language.ES_ES),
        SPANISH_LUCENE_SPANISH_MINIMAL_STEM_FILTER(StemmerPatchTrieLoader.Language.ES_ES),
        SPANISH_LUCENE_SPANISH_PLURAL_STEM_FILTER(StemmerPatchTrieLoader.Language.ES_ES),
        PERSIAN_RADIXOR(StemmerPatchTrieLoader.Language.FA_IR),
        PERSIAN_LUCENE_PERSIAN_STEM_FILTER(StemmerPatchTrieLoader.Language.FA_IR),
        FINNISH_RADIXOR(StemmerPatchTrieLoader.Language.FI_FI),
        FINNISH_LUCENE_FINNISH_LIGHT_STEM_FILTER(StemmerPatchTrieLoader.Language.FI_FI),
        FRENCH_RADIXOR(StemmerPatchTrieLoader.Language.FR_FR),
        FRENCH_LUCENE_FRENCH_LIGHT_STEM_FILTER(StemmerPatchTrieLoader.Language.FR_FR),
        FRENCH_LUCENE_FRENCH_MINIMAL_STEM_FILTER(StemmerPatchTrieLoader.Language.FR_FR),
        HUNGARIAN_RADIXOR(StemmerPatchTrieLoader.Language.HU_HU),
        HUNGARIAN_LUCENE_HUNGARIAN_LIGHT_STEM_FILTER(StemmerPatchTrieLoader.Language.HU_HU),
        ITALIAN_RADIXOR(StemmerPatchTrieLoader.Language.IT_IT),
        ITALIAN_LUCENE_ITALIAN_LIGHT_STEM_FILTER(StemmerPatchTrieLoader.Language.IT_IT),
        NORWEGIAN_BOKMAL_RADIXOR(StemmerPatchTrieLoader.Language.NB_NO),
        NORWEGIAN_BOKMAL_LUCENE_NORWEGIAN_LIGHT_STEM_FILTER(StemmerPatchTrieLoader.Language.NB_NO),
        NORWEGIAN_BOKMAL_LUCENE_NORWEGIAN_MINIMAL_STEM_FILTER(StemmerPatchTrieLoader.Language.NB_NO),
        POLISH_RADIXOR(StemmerPatchTrieLoader.Language.PL_PL),
        POLISH_LUCENE_STEMPEL_DIRECT(StemmerPatchTrieLoader.Language.PL_PL),
        POLISH_LUCENE_STEMPEL_FILTER(StemmerPatchTrieLoader.Language.PL_PL),
        POLISH_LUCENE_MORFOLOGIK_FILTER(StemmerPatchTrieLoader.Language.PL_PL),
        PORTUGUESE_RADIXOR(StemmerPatchTrieLoader.Language.PT_PT),
        PORTUGUESE_LUCENE_PORTUGUESE_STEM_FILTER(StemmerPatchTrieLoader.Language.PT_PT),
        PORTUGUESE_LUCENE_PORTUGUESE_LIGHT_STEM_FILTER(StemmerPatchTrieLoader.Language.PT_PT),
        PORTUGUESE_LUCENE_PORTUGUESE_MINIMAL_STEM_FILTER(StemmerPatchTrieLoader.Language.PT_PT),
        RUSSIAN_RADIXOR(StemmerPatchTrieLoader.Language.RU_RU),
        RUSSIAN_LUCENE_RUSSIAN_LIGHT_STEM_FILTER(StemmerPatchTrieLoader.Language.RU_RU),
        SWEDISH_RADIXOR(StemmerPatchTrieLoader.Language.SV_SE),
        SWEDISH_LUCENE_SWEDISH_LIGHT_STEM_FILTER(StemmerPatchTrieLoader.Language.SV_SE),
        SWEDISH_LUCENE_SWEDISH_MINIMAL_STEM_FILTER(StemmerPatchTrieLoader.Language.SV_SE),
        UKRAINIAN_RADIXOR(StemmerPatchTrieLoader.Language.UK_UA),
        UKRAINIAN_MORFOLOGIK_DIRECT(StemmerPatchTrieLoader.Language.UK_UA),
        UKRAINIAN_LUCENE_MORFOLOGIK_FILTER(StemmerPatchTrieLoader.Language.UK_UA),
        SNOWBALL_DANISH_DIRECT(StemmerPatchTrieLoader.Language.DA_DK, SnowballLanguageCase.DANISH),
        SNOWBALL_DANISH_LUCENE_FILTER(StemmerPatchTrieLoader.Language.DA_DK, SnowballLanguageCase.DANISH),
        SNOWBALL_DUTCH_DIRECT(StemmerPatchTrieLoader.Language.NL_NL, SnowballLanguageCase.DUTCH),
        SNOWBALL_DUTCH_LUCENE_FILTER(StemmerPatchTrieLoader.Language.NL_NL, SnowballLanguageCase.DUTCH),
        SNOWBALL_FINNISH_DIRECT(StemmerPatchTrieLoader.Language.FI_FI, SnowballLanguageCase.FINNISH),
        SNOWBALL_FINNISH_LUCENE_FILTER(StemmerPatchTrieLoader.Language.FI_FI, SnowballLanguageCase.FINNISH),
        SNOWBALL_FRENCH_DIRECT(StemmerPatchTrieLoader.Language.FR_FR, SnowballLanguageCase.FRENCH),
        SNOWBALL_FRENCH_LUCENE_FILTER(StemmerPatchTrieLoader.Language.FR_FR, SnowballLanguageCase.FRENCH),
        SNOWBALL_GERMAN_DIRECT(StemmerPatchTrieLoader.Language.DE_DE, SnowballLanguageCase.GERMAN),
        SNOWBALL_GERMAN_LUCENE_FILTER(StemmerPatchTrieLoader.Language.DE_DE, SnowballLanguageCase.GERMAN),
        SNOWBALL_HUNGARIAN_DIRECT(StemmerPatchTrieLoader.Language.HU_HU, SnowballLanguageCase.HUNGARIAN),
        SNOWBALL_HUNGARIAN_LUCENE_FILTER(StemmerPatchTrieLoader.Language.HU_HU, SnowballLanguageCase.HUNGARIAN),
        SNOWBALL_ITALIAN_DIRECT(StemmerPatchTrieLoader.Language.IT_IT, SnowballLanguageCase.ITALIAN),
        SNOWBALL_ITALIAN_LUCENE_FILTER(StemmerPatchTrieLoader.Language.IT_IT, SnowballLanguageCase.ITALIAN),
        SNOWBALL_NORWEGIAN_BOKMAL_DIRECT(StemmerPatchTrieLoader.Language.NB_NO,
                SnowballLanguageCase.NORWEGIAN_BOKMAL),
        SNOWBALL_NORWEGIAN_BOKMAL_LUCENE_FILTER(StemmerPatchTrieLoader.Language.NB_NO,
                SnowballLanguageCase.NORWEGIAN_BOKMAL),
        SNOWBALL_NORWEGIAN_NYNORSK_DIRECT(StemmerPatchTrieLoader.Language.NN_NO,
                SnowballLanguageCase.NORWEGIAN_NYNORSK),
        SNOWBALL_NORWEGIAN_NYNORSK_LUCENE_FILTER(StemmerPatchTrieLoader.Language.NN_NO,
                SnowballLanguageCase.NORWEGIAN_NYNORSK),
        SNOWBALL_PORTUGUESE_DIRECT(StemmerPatchTrieLoader.Language.PT_PT, SnowballLanguageCase.PORTUGUESE),
        SNOWBALL_PORTUGUESE_LUCENE_FILTER(StemmerPatchTrieLoader.Language.PT_PT, SnowballLanguageCase.PORTUGUESE),
        SNOWBALL_RUSSIAN_DIRECT(StemmerPatchTrieLoader.Language.RU_RU, SnowballLanguageCase.RUSSIAN),
        SNOWBALL_RUSSIAN_LUCENE_FILTER(StemmerPatchTrieLoader.Language.RU_RU, SnowballLanguageCase.RUSSIAN),
        SNOWBALL_SPANISH_DIRECT(StemmerPatchTrieLoader.Language.ES_ES, SnowballLanguageCase.SPANISH),
        SNOWBALL_SPANISH_LUCENE_FILTER(StemmerPatchTrieLoader.Language.ES_ES, SnowballLanguageCase.SPANISH),
        SNOWBALL_SWEDISH_DIRECT(StemmerPatchTrieLoader.Language.SV_SE, SnowballLanguageCase.SWEDISH),
        SNOWBALL_SWEDISH_LUCENE_FILTER(StemmerPatchTrieLoader.Language.SV_SE, SnowballLanguageCase.SWEDISH),
        SNOWBALL_YIDDISH_DIRECT(StemmerPatchTrieLoader.Language.YI, SnowballLanguageCase.YIDDISH),
        SNOWBALL_YIDDISH_LUCENE_FILTER(StemmerPatchTrieLoader.Language.YI, SnowballLanguageCase.YIDDISH);

        /**
         * Radixor dictionary language used as truth.
         */
        private final StemmerPatchTrieLoader.Language radixorLanguage;

        /**
         * Optional Snowball language mapping.
         */
        private final SnowballLanguageCase snowballLanguageCase;

        /**
         * Creates a candidate.
         *
         * @param radixorLanguage Radixor dictionary language
         */
        QualityCandidate(final StemmerPatchTrieLoader.Language radixorLanguage) {
            this(radixorLanguage, null);
        }

        /**
         * Creates a candidate.
         *
         * @param radixorLanguage Radixor dictionary language
         * @param snowballLanguageCase matching Snowball case
         */
        QualityCandidate(final StemmerPatchTrieLoader.Language radixorLanguage,
                final SnowballLanguageCase snowballLanguageCase) {
            this.radixorLanguage = radixorLanguage;
            this.snowballLanguageCase = snowballLanguageCase;
        }

        /**
         * Returns the Radixor dictionary language.
         *
         * @return Radixor language
         */
        StemmerPatchTrieLoader.Language radixorLanguage() {
            return this.radixorLanguage;
        }

        /**
         * Creates the evaluator for this candidate.
         *
         * @return quality evaluator
         * @throws IOException if stemmer resources cannot be loaded
         */
        @SuppressWarnings("deprecation") // Lucene retains SpanishMinimalStemFilter only for compatibility benchmarking.
        CandidateStemmer createStemmer() throws IOException {
            if (name().endsWith("_RADIXOR")) {
                return radixor(createRadixorStemmer(this.radixorLanguage));
            }
            if (name().endsWith("_DIRECT") && this.snowballLanguageCase != null) {
                return direct(this.snowballLanguageCase.createDirectStemmer()::stem);
            }
            if (name().endsWith("_LUCENE_FILTER") && this.snowballLanguageCase != null) {
                return tokenFilter(input -> new SnowballFilter(new LowerCaseFilter(input),
                        this.snowballLanguageCase.luceneSnowballName()));
            }

            return switch (this) {
                case ENGLISH_SNOWBALL_ORIGINAL_PORTER -> direct(new SnowballStemmerAdapter(porterStemmer::new)::stem);
                case ENGLISH_SNOWBALL_PORTER2 -> direct(new SnowballStemmerAdapter(englishStemmer::new)::stem);
                case ENGLISH_LUCENE_PORTER_COPIED -> direct(new LucenePorterStemmerCopied()::stem);
                case ENGLISH_LUCENE_PORTER_FILTER -> tokenFilter(PorterStemFilter::new);
                case ENGLISH_LUCENE_KSTEM_FILTER -> tokenFilter(KStemFilter::new);
                case ENGLISH_LUCENE_MINIMAL_FILTER -> tokenFilter(EnglishMinimalStemFilter::new);
                case ENGLISH_LUCENE_POSSESSIVE_FILTER -> tokenFilter(EnglishPossessiveFilter::new);
                case ENGLISH_PAICE_HUSK_LANCASTER -> direct(new PaiceHuskLancasterStemmer()::stem);
                case ENGLISH_OPENNLP_PORTER -> {
                    final opennlp.tools.stemmer.PorterStemmer stemmer =
                            new opennlp.tools.stemmer.PorterStemmer();
                    yield direct(token -> stemmer.stem(token).toString());
                }
                case CZECH_LUCENE_CZECH_STEM_FILTER -> tokenFilter(input -> new CzechStemFilter(lowercase(input)));
                case GERMAN_LUCENE_GERMAN_STEM_FILTER -> tokenFilter(input -> new GermanStemFilter(lowercase(input)));
                case GERMAN_LUCENE_GERMAN_LIGHT_STEM_FILTER ->
                    tokenFilter(input -> new GermanLightStemFilter(germanNormalize(input)));
                case GERMAN_LUCENE_GERMAN_MINIMAL_STEM_FILTER ->
                    tokenFilter(input -> new GermanMinimalStemFilter(germanNormalize(input)));
                case GERMAN_CISTEM -> direct(createGermanCistemStemmer());
                case SPANISH_LUCENE_SPANISH_LIGHT_STEM_FILTER ->
                    tokenFilter(input -> new SpanishLightStemFilter(lowercase(input)));
                case SPANISH_LUCENE_SPANISH_MINIMAL_STEM_FILTER ->
                    tokenFilter(input -> new SpanishMinimalStemFilter(lowercase(input)));
                case SPANISH_LUCENE_SPANISH_PLURAL_STEM_FILTER ->
                    tokenFilter(input -> new SpanishPluralStemFilter(lowercase(input)));
                case PERSIAN_LUCENE_PERSIAN_STEM_FILTER ->
                    tokenFilter(input -> new PersianStemFilter(persianNormalize(input)));
                case FINNISH_LUCENE_FINNISH_LIGHT_STEM_FILTER ->
                    tokenFilter(input -> new FinnishLightStemFilter(lowercase(input)));
                case FRENCH_LUCENE_FRENCH_LIGHT_STEM_FILTER ->
                    tokenFilter(input -> new FrenchLightStemFilter(lowercase(input)));
                case FRENCH_LUCENE_FRENCH_MINIMAL_STEM_FILTER ->
                    tokenFilter(input -> new FrenchMinimalStemFilter(lowercase(input)));
                case HUNGARIAN_LUCENE_HUNGARIAN_LIGHT_STEM_FILTER ->
                    tokenFilter(input -> new HungarianLightStemFilter(lowercase(input)));
                case ITALIAN_LUCENE_ITALIAN_LIGHT_STEM_FILTER ->
                    tokenFilter(input -> new ItalianLightStemFilter(lowercase(input)));
                case NORWEGIAN_BOKMAL_LUCENE_NORWEGIAN_LIGHT_STEM_FILTER ->
                    tokenFilter(input -> new NorwegianLightStemFilter(lowercase(input)));
                case NORWEGIAN_BOKMAL_LUCENE_NORWEGIAN_MINIMAL_STEM_FILTER ->
                    tokenFilter(input -> new NorwegianMinimalStemFilter(lowercase(input)));
                case POLISH_LUCENE_STEMPEL_DIRECT -> {
                    final StempelStemmer stemmer = new StempelStemmer(PolishAnalyzer.getDefaultTable());
                    yield direct(token -> {
                        final StringBuilder stem = stemmer.stem(token);
                        return stem == null ? token : stem.toString();
                    });
                }
                case POLISH_LUCENE_STEMPEL_FILTER ->
                    tokenFilter(input -> new StempelFilter(input, new StempelStemmer(PolishAnalyzer.getDefaultTable())));
                case POLISH_LUCENE_MORFOLOGIK_FILTER -> tokenFilter(MorfologikFilter::new, true);
                case PORTUGUESE_LUCENE_PORTUGUESE_STEM_FILTER ->
                    tokenFilter(input -> new PortugueseStemFilter(lowercase(input)));
                case PORTUGUESE_LUCENE_PORTUGUESE_LIGHT_STEM_FILTER ->
                    tokenFilter(input -> new PortugueseLightStemFilter(lowercase(input)));
                case PORTUGUESE_LUCENE_PORTUGUESE_MINIMAL_STEM_FILTER ->
                    tokenFilter(input -> new PortugueseMinimalStemFilter(lowercase(input)));
                case RUSSIAN_LUCENE_RUSSIAN_LIGHT_STEM_FILTER ->
                    tokenFilter(input -> new RussianLightStemFilter(lowercase(input)));
                case SWEDISH_LUCENE_SWEDISH_LIGHT_STEM_FILTER ->
                    tokenFilter(input -> new SwedishLightStemFilter(lowercase(input)));
                case SWEDISH_LUCENE_SWEDISH_MINIMAL_STEM_FILTER ->
                    tokenFilter(input -> new SwedishMinimalStemFilter(lowercase(input)));
                case UKRAINIAN_MORFOLOGIK_DIRECT -> {
                    final DictionaryLookup lookup = new DictionaryLookup(loadUkrainianMorfologikDictionary());
                    yield morphologik(lookup);
                }
                case UKRAINIAN_LUCENE_MORFOLOGIK_FILTER -> {
                    final Dictionary dictionary = loadUkrainianMorfologikDictionary();
                    yield tokenFilter(input -> new MorfologikFilter(input, dictionary), true);
                }
                default -> throw new IllegalStateException("No evaluator for " + this + ".");
            };
        }

        /** Creates the exact-root evaluator used by the JMH quality benchmark. */
        QualityEvaluator createEvaluator() throws IOException {
            return exactRootEvaluator(createStemmer());
        }
    }

    /**
     * Creates a CISTEM stemmer adapter.
     *
     * @return German stem function
     */
    private static Stemmer createGermanCistemStemmer() {
        return Cistem::stem;
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

    /**
     * Quality evaluator for one candidate.
     */
    @FunctionalInterface
    private interface QualityEvaluator {

        /**
         * Evaluates exact-root agreement for one corpus.
         *
         * @param corpus token/root corpus
         * @param blackhole result sink
         * @return exact-root match count
         * @throws IOException if Lucene streaming fails
         */
        QualityResult evaluate(LanguageBenchmarkCorpus.Corpus corpus, Blackhole blackhole) throws IOException;
    }

    /** Stateful candidate adapter confined to one sequential evaluation scenario. */
    @FunctionalInterface
    interface CandidateStemmer {

        /**
         * Stems a deterministic batch through the authoritative JMH invocation path.
         *
         * @param tokens input tokens, never {@code null}
         * @return one non-null output for every input token
         * @throws IOException if a token-stream implementation fails
         */
        String[] stem(String[] tokens) throws IOException;

        /**
         * Returns complete distinct candidate sets, each containing its primary output.
         * Single-output adapters expose singleton lists.
         *
         * @param tokens input tokens
         * @return immutable candidate list for every token
         * @throws IOException if adapter processing fails
         */
        default List<List<String>> stemCandidates(final String[] tokens) throws IOException {
            final String[] primary = stem(tokens);
            return java.util.Arrays.stream(primary).map(List::of).toList();
        }

        /** @return whether the adapter exposes genuine alternative outputs */
        default boolean supportsMultipleOutputs() {
            return false;
        }
    }

    /** Creates the candidate-capable Radixor adapter backed by ranked {@code getAll}. */
    private static CandidateStemmer radixor(final RadixorBenchmarkStemmer stemmer) {
        return new CandidateStemmer() {
            /** {@inheritDoc} */
            @Override public String[] stem(final String[] tokens) {
                final String[] outputs = new String[tokens.length];
                for (int index = 0; index < tokens.length; index++) { outputs[index] = stemmer.stem(tokens[index]); }
                return outputs;
            }
            /** {@inheritDoc} */
            @Override public List<List<String>> stemCandidates(final String[] tokens) {
                return java.util.Arrays.stream(tokens).map(stemmer::stemAll).toList();
            }
            /** {@inheritDoc} */
            @Override public boolean supportsMultipleOutputs() { return true; }
        };
    }

    /**
     * Creates the authoritative multi-output Radixor adapter for a validated dictionary language.
     *
     * @param language bundled Radixor language
     * @return scenario-confined adapter using the JMH invocation path
     * @throws IOException if the compiled dictionary cannot be loaded
     */
    static CandidateStemmer createRadixorQualityStemmer(final StemmerPatchTrieLoader.Language language)
            throws IOException {
        return radixor(createRadixorStemmer(language));
    }

    /**
     * Creates the authoritative multi-output Radixor adapter for an explicitly
     * selected runtime model.
     *
     * @param modelId exact model identifier
     * @return scenario-confined adapter using the JMH invocation path
     * @throws IOException if the compiled dictionary cannot be loaded
     */
    static CandidateStemmer createRadixorQualityStemmer(final String modelId) throws IOException {
        return radixor(new RadixorBenchmarkStemmer(StemmerPatchTrieLoader.loadCompiled(
                modelId, true, ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS)));
    }

    /**
     * Exact-root agreement counters for one quality operation.
     *
     * @param correctMatches total exact-root matches
     * @param evaluatedTokens total evaluated tokens
     * @param changedCorrectMatches exact-root matches where token differs from root
     * @param changedEvaluatedTokens evaluated tokens where token differs from root
     * @param rootPreservedMatches exact-root matches where token already equals root
     * @param rootEvaluatedTokens evaluated tokens where token already equals root
     */
    private record QualityResult(int correctMatches, int evaluatedTokens, int changedCorrectMatches,
            int changedEvaluatedTokens, int rootPreservedMatches, int rootEvaluatedTokens) {
    }

    /**
     * Creates a direct candidate adapter.
     *
     * @param stemmer direct stemmer
     * @return sequential batch adapter
     */
    private static CandidateStemmer direct(final Stemmer stemmer) {
        Objects.requireNonNull(stemmer, "stemmer");
        return tokens -> {
            final String[] outputs = new String[tokens.length];
            for (int index = 0; index < tokens.length; index++) {
                outputs[index] = stemmer.stem(tokens[index]);
            }
            return outputs;
        };
    }

    /**
     * Creates a TokenFilter candidate adapter.
     *
     * @param factory token stream factory
     * @return sequential batch adapter
     */
    private static CandidateStemmer tokenFilter(final Function<TokenStream, TokenStream> factory) {
        Objects.requireNonNull(factory, "factory");
        return tokens -> firstTokenFilterOutputs(tokens, factory, null);
    }

    /** Creates a TokenFilter adapter that preserves all terms emitted per position. */
    private static CandidateStemmer tokenFilter(final Function<TokenStream, TokenStream> factory,
            final boolean multipleOutputs) {
        if (!multipleOutputs) { return tokenFilter(factory); }
        return new CandidateStemmer() {
            /** {@inheritDoc} */
            @Override public String[] stem(final String[] tokens) throws IOException {
                return firstTokenFilterOutputs(tokens, factory, null);
            }
            /** {@inheritDoc} */
            @Override public List<List<String>> stemCandidates(final String[] tokens) throws IOException {
                return allTokenFilterOutputs(tokens, factory);
            }
            /** {@inheritDoc} */
            @Override public boolean supportsMultipleOutputs() { return true; }
        };
    }

    /** Creates a multi-analysis Morphologik direct adapter. */
    private static CandidateStemmer morphologik(final DictionaryLookup lookup) {
        return new CandidateStemmer() {
            /** {@inheritDoc} */
            @Override public String[] stem(final String[] tokens) {
                final String[] outputs = new String[tokens.length];
                for (int index = 0; index < tokens.length; index++) { outputs[index] = firstMorfologikStem(tokens[index], lookup); }
                return outputs;
            }
            /** {@inheritDoc} */
            @Override public List<List<String>> stemCandidates(final String[] tokens) {
                return java.util.Arrays.stream(tokens).map(token -> allMorfologikStems(token, lookup)).toList();
            }
            /** {@inheritDoc} */
            @Override public boolean supportsMultipleOutputs() { return true; }
        };
    }

    /**
     * Creates exact-root accounting around an authoritative candidate adapter.
     *
     * @param stemmer candidate adapter
     * @return JMH exact-root evaluator
     */
    private static QualityEvaluator exactRootEvaluator(final CandidateStemmer stemmer) {
        Objects.requireNonNull(stemmer, "stemmer");
        return (corpus, blackhole) -> {
            final String[] actualStems = stemmer.stem(corpus.tokens());
            final String[] expectedRoots = corpus.expectedRoots();
            final String[] tokens = corpus.tokens();
            int correct = 0;
            int changedCorrect = 0;
            int changedEvaluated = 0;
            int rootPreserved = 0;
            int rootEvaluated = 0;
            for (int index = 0; index < actualStems.length; index++) {
                final String token = tokens[index];
                final String expectedRoot = expectedRoots[index];
                blackhole.consume(actualStems[index]);
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
            return new QualityResult(correct, actualStems.length, changedCorrect, changedEvaluated, rootPreserved,
                    rootEvaluated);
        };
    }

    /**
     * Creates a direct Radixor stemmer.
     *
     * @param language Radixor dictionary language
     * @return direct stemmer
     * @throws IOException if the trie cannot be loaded
     */
    private static RadixorBenchmarkStemmer createRadixorStemmer(final StemmerPatchTrieLoader.Language language) throws IOException {
        return new RadixorBenchmarkStemmer(StemmerPatchTrieLoader.loadCompiled(
                language, true, ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
    }

    /**
     * Loads the benchmark-only Ukrainian Morfologik dictionary.
     *
     * @return Ukrainian Morfologik dictionary
     * @throws IOException if the dictionary cannot be loaded
     */
    private static Dictionary loadUkrainianMorfologikDictionary() throws IOException {
        final URL dictionaryUrl = StemmerComparisonBenchmarkQuality.class.getClassLoader()
                .getResource("ua/net/nlp/ukrainian.dict");
        if (dictionaryUrl == null) {
            throw new IllegalStateException("Missing Ukrainian Morfologik dictionary resource.");
        }
        return Dictionary.read(dictionaryUrl);
    }

    /**
     * Returns the first Morfologik stem for one token.
     *
     * @param token input token
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

    /** Returns all distinct Morphologik lemma strings and always includes the primary output. */
    private static List<String> allMorfologikStems(final String token, final DictionaryLookup lookup) {
        final java.util.LinkedHashSet<String> stems = new java.util.LinkedHashSet<>();
        stems.add(firstMorfologikStem(token, lookup));
        for (WordData analysis : lookup.lookup(token)) { stems.add(analysis.getStem().toString()); }
        return List.copyOf(stems);
    }

    /**
     * Extracts the first emitted term for each input token from a TokenFilter
     * pipeline.
     *
     * @param tokens token corpus
     * @param factory token stream factory
     * @param blackhole result sink
     * @return first emitted term per input token
     * @throws IOException if Lucene streaming fails
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
                recordedForPosition = true;
            }
            if (blackhole != null) {
                blackhole.consume(termAttribute);
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
     * Extracts every distinct emitted term for each input position and includes the
     * deterministic primary output even when a filter omits it.
     */
    private static List<List<String>> allTokenFilterOutputs(final String[] tokens,
            final Function<TokenStream, TokenStream> factory) throws IOException {
        final List<java.util.LinkedHashSet<String>> candidates = new java.util.ArrayList<>(tokens.length);
        for (int index = 0; index < tokens.length; index++) { candidates.add(new java.util.LinkedHashSet<>()); }
        final BenchmarkTokenStream input = new BenchmarkTokenStream(tokens);
        final TokenStream output = factory.apply(input);
        final CharTermAttribute term = output.addAttribute(CharTermAttribute.class);
        final PositionIncrementAttribute position = output.addAttribute(PositionIncrementAttribute.class);
        int inputIndex = -1;
        output.reset();
        while (output.incrementToken()) {
            if (position.getPositionIncrement() > 0) { inputIndex += position.getPositionIncrement(); }
            if (inputIndex >= 0 && inputIndex < candidates.size()) { candidates.get(inputIndex).add(term.toString()); }
        }
        output.end();
        output.close();
        final String[] primary = firstTokenFilterOutputs(tokens, factory, null);
        final List<List<String>> result = new java.util.ArrayList<>(tokens.length);
        for (int index = 0; index < tokens.length; index++) {
            candidates.get(index).add(primary[index]);
            result.add(List.copyOf(candidates.get(index)));
        }
        return List.copyOf(result);
    }

    /**
     * Adds Lucene lower-case normalization.
     *
     * @param input input token stream
     * @return normalized stream
     */
    private static TokenStream lowercase(final TokenStream input) {
        return new LowerCaseFilter(input);
    }

    /**
     * Adds Lucene German normalization.
     *
     * @param input input token stream
     * @return normalized stream
     */
    private static TokenStream germanNormalize(final TokenStream input) {
        return new GermanNormalizationFilter(lowercase(input));
    }

    /**
     * Adds Lucene Persian normalization.
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
}
