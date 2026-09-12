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

import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.egothor.stemmer.benchmark.snowball.ext.arabicStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.armenianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.catalanStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.czechStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.danishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.dutchStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.englishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.estonianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.finnishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.frenchStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.germanStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.greekStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.hungarianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.indonesianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.irishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.italianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.lithuanianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.norwegianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.persianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.polishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.portugueseStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.russianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.romanianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.sesothoStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.spanishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.swedishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.turkishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.yiddishStemmer;

/**
 * Maps Radixor dictionary languages to matching official Snowball algorithms.
 */
enum SnowballLanguageCase {

    /** Arabic Snowball stemming over the Radixor Arabic dictionary. */
    ARABIC("Arabic", StemmerPatchTrieLoader.Language.AR, arabicStemmer::new, "Arabic",
            "snowballDirect[ARABIC]", "SNOWBALL_ARABIC_DIRECT"),

    /** Armenian Snowball stemming over the Radixor Armenian dictionary. */
    ARMENIAN("Armenian", StemmerPatchTrieLoader.Language.HY_AM, armenianStemmer::new, "Armenian",
            "snowballDirect[ARMENIAN]", "SNOWBALL_ARMENIAN_DIRECT"),

    /** Catalan Snowball stemming over the Radixor Catalan dictionary. */
    CATALAN("Catalan", StemmerPatchTrieLoader.Language.CA_ES, catalanStemmer::new, "Catalan",
            "snowballDirect[CATALAN]", "SNOWBALL_CATALAN_DIRECT"),

    /**
     * Czech Snowball stemming over the Radixor Czech dictionary.
     */
    CZECH("Czech", StemmerPatchTrieLoader.Language.CS_CZ, czechStemmer::new, null,
            "snowballDirect[CZECH]", "SNOWBALL_CZECH_DIRECT"),

    /**
     * Danish Snowball stemming over the Radixor Danish dictionary.
     */
    DANISH("Danish", StemmerPatchTrieLoader.Language.DA_DK, danishStemmer::new, "Danish",
            "snowballDirect[DANISH]", "SNOWBALL_DANISH_DIRECT"),

    /**
     * Dutch Snowball stemming over the Radixor Dutch dictionary.
     */
    DUTCH("Dutch", StemmerPatchTrieLoader.Language.NL_NL, dutchStemmer::new, "Dutch",
            "snowballDirect[DUTCH]", "SNOWBALL_DUTCH_DIRECT"),

    /** Estonian Snowball stemming over the Radixor Estonian dictionary. */
    ESTONIAN("Estonian", StemmerPatchTrieLoader.Language.ET_EE, estonianStemmer::new, "Estonian",
            "snowballDirect[ESTONIAN]", "SNOWBALL_ESTONIAN_DIRECT"),

    /** English Porter2 Snowball stemming over the Radixor English dictionary. */
    ENGLISH("English", StemmerPatchTrieLoader.Language.US_UK, englishStemmer::new, "English",
            "snowballEnglishPorter2", "ENGLISH_SNOWBALL_PORTER2"),

    /**
     * Finnish Snowball stemming over the Radixor Finnish dictionary.
     */
    FINNISH("Finnish", StemmerPatchTrieLoader.Language.FI_FI, finnishStemmer::new, "Finnish",
            "snowballDirect[FINNISH]", "SNOWBALL_FINNISH_DIRECT"),

    /**
     * French Snowball stemming over the Radixor French dictionary.
     */
    FRENCH("French", StemmerPatchTrieLoader.Language.FR_FR, frenchStemmer::new, "French",
            "snowballDirect[FRENCH]", "SNOWBALL_FRENCH_DIRECT"),

    /**
     * German Snowball stemming over the Radixor German dictionary.
     */
    GERMAN("German", StemmerPatchTrieLoader.Language.DE_DE, germanStemmer::new, "German",
            "snowballDirect[GERMAN]", "SNOWBALL_GERMAN_DIRECT"),

    /** Greek Snowball stemming over the Radixor Greek dictionary. */
    GREEK("Greek", StemmerPatchTrieLoader.Language.EL_GR, greekStemmer::new, "Greek",
            "snowballDirect[GREEK]", "SNOWBALL_GREEK_DIRECT"),

    /**
     * Hungarian Snowball stemming over the Radixor Hungarian dictionary.
     */
    HUNGARIAN("Hungarian", StemmerPatchTrieLoader.Language.HU_HU, hungarianStemmer::new, "Hungarian",
            "snowballDirect[HUNGARIAN]", "SNOWBALL_HUNGARIAN_DIRECT"),

    /** Indonesian Snowball stemming over the Radixor Indonesian dictionary. */
    INDONESIAN("Indonesian", StemmerPatchTrieLoader.Language.ID_ID, indonesianStemmer::new, "Indonesian",
            "snowballDirect[INDONESIAN]", "SNOWBALL_INDONESIAN_DIRECT"),

    /** Irish Snowball stemming over the Radixor Irish dictionary. */
    IRISH("Irish", StemmerPatchTrieLoader.Language.GA_IE, irishStemmer::new, "Irish",
            "snowballDirect[IRISH]", "SNOWBALL_IRISH_DIRECT"),

    /**
     * Italian Snowball stemming over the Radixor Italian dictionary.
     */
    ITALIAN("Italian", StemmerPatchTrieLoader.Language.IT_IT, italianStemmer::new, "Italian",
            "snowballDirect[ITALIAN]", "SNOWBALL_ITALIAN_DIRECT"),

    /** Lithuanian Snowball stemming over the Radixor Lithuanian dictionary. */
    LITHUANIAN("Lithuanian", StemmerPatchTrieLoader.Language.LT_LT, lithuanianStemmer::new, "Lithuanian",
            "snowballDirect[LITHUANIAN]", "SNOWBALL_LITHUANIAN_DIRECT"),

    /**
     * Norwegian Snowball stemming over the Radixor Bokmal dictionary.
     */
    NORWEGIAN_BOKMAL("Norwegian Bokmal", StemmerPatchTrieLoader.Language.NB_NO, norwegianStemmer::new,
            "Norwegian", "snowballDirect[NORWEGIAN_BOKMAL]", "SNOWBALL_NORWEGIAN_BOKMAL_DIRECT"),

    /**
     * Norwegian Snowball stemming over the Radixor Nynorsk dictionary.
     */
    NORWEGIAN_NYNORSK("Norwegian Nynorsk", StemmerPatchTrieLoader.Language.NN_NO, norwegianStemmer::new,
            "Norwegian", "snowballDirect[NORWEGIAN_NYNORSK]", "SNOWBALL_NORWEGIAN_NYNORSK_DIRECT"),

    /**
     * Persian Snowball stemming over the Radixor Persian dictionary.
     */
    PERSIAN("Persian", StemmerPatchTrieLoader.Language.FA_IR, persianStemmer::new, null,
            "snowballDirect[PERSIAN]", "SNOWBALL_PERSIAN_DIRECT"),

    /**
     * Polish Snowball stemming over the Radixor Polish dictionary.
     */
    POLISH("Polish", StemmerPatchTrieLoader.Language.PL_PL, polishStemmer::new, null,
            "snowballDirect[POLISH]", "SNOWBALL_POLISH_DIRECT"),

    /**
     * Portuguese Snowball stemming over the Radixor Portuguese dictionary.
     */
    PORTUGUESE("Portuguese", StemmerPatchTrieLoader.Language.PT_PT, portugueseStemmer::new, "Portuguese",
            "snowballDirect[PORTUGUESE]", "SNOWBALL_PORTUGUESE_DIRECT"),

    /**
     * Russian Snowball stemming over the Radixor Russian dictionary.
     */
    RUSSIAN("Russian", StemmerPatchTrieLoader.Language.RU_RU, russianStemmer::new, "Russian",
            "snowballDirect[RUSSIAN]", "SNOWBALL_RUSSIAN_DIRECT"),

    /** Romanian Snowball stemming over the Radixor Romanian dictionary. */
    ROMANIAN("Romanian", StemmerPatchTrieLoader.Language.RO_RO, romanianStemmer::new, "Romanian",
            "snowballDirect[ROMANIAN]", "SNOWBALL_ROMANIAN_DIRECT"),

    /** Southern Sotho Snowball stemming over the matching Radixor dictionary. */
    SESOTHO("Southern Sotho", StemmerPatchTrieLoader.Language.ST_ZA, sesothoStemmer::new, null,
            "snowballDirect[SESOTHO]", "SNOWBALL_SESOTHO_DIRECT"),

    /**
     * Spanish Snowball stemming over the Radixor Spanish dictionary.
     */
    SPANISH("Spanish", StemmerPatchTrieLoader.Language.ES_ES, spanishStemmer::new, "Spanish",
            "snowballDirect[SPANISH]", "SNOWBALL_SPANISH_DIRECT"),

    /**
     * Swedish Snowball stemming over the Radixor Swedish dictionary.
     */
    SWEDISH("Swedish", StemmerPatchTrieLoader.Language.SV_SE, swedishStemmer::new, "Swedish",
            "snowballDirect[SWEDISH]", "SNOWBALL_SWEDISH_DIRECT"),

    /** Turkish Snowball stemming over the Radixor Turkish dictionary. */
    TURKISH("Turkish", StemmerPatchTrieLoader.Language.TR_TR, turkishStemmer::new, "Turkish",
            "snowballDirect[TURKISH]", "SNOWBALL_TURKISH_DIRECT"),

    /**
     * Yiddish Snowball stemming over the Radixor Yiddish dictionary.
     */
    YIDDISH("Yiddish", StemmerPatchTrieLoader.Language.YI, yiddishStemmer::new, "Yiddish",
            "snowballDirect[YIDDISH]", "SNOWBALL_YIDDISH_DIRECT");

    /**
     * Human-readable language name.
     */
    private final String displayLanguage;

    /**
     * Matching Radixor language resource.
     */
    private final StemmerPatchTrieLoader.Language radixorLanguage;

    /**
     * Factory for the isolated benchmark-only Snowball implementation.
     */
    private final SnowballStemmerAdapter.Factory directFactory;

    /**
     * Lucene SnowballFilter algorithm name.
     */
    private final String luceneSnowballName;

    /** Exact published JMH speed-method selector. */
    private final String speedMethod;

    /** Exact published quality candidate. */
    private final String qualityCandidate;

    /** Whether this direct case participates in the cross-language homepage aggregate. */
    private final boolean homepageAggregate;

    /**
     * Creates an exact-language comparison case with explicit publication identities.
     *
     * @param displayLanguage human-readable language name
     * @param radixorLanguage matching Radixor language resource
     * @param directFactory direct Snowball stemmer factory
     * @param luceneSnowballName Lucene SnowballFilter algorithm name, or {@code null}
     * @param speedMethod exact speed benchmark method selector
     * @param qualityCandidate exact quality benchmark candidate name
     */
    SnowballLanguageCase(final String displayLanguage, final StemmerPatchTrieLoader.Language radixorLanguage,
            final SnowballStemmerAdapter.Factory directFactory, final String luceneSnowballName,
            final String speedMethod, final String qualityCandidate) {
        this.displayLanguage = displayLanguage;
        this.radixorLanguage = radixorLanguage;
        this.directFactory = directFactory;
        this.luceneSnowballName = luceneSnowballName;
        this.speedMethod = speedMethod;
        this.qualityCandidate = qualityCandidate;
        this.homepageAggregate = true;
    }

    /**
     * Returns the exact published speed-method selector.
     *
     * @return speed-method selector including any JMH parameter identity
     */
    String speedMethod() {
        return this.speedMethod;
    }

    /**
     * Returns the exact published quality candidate.
     *
     * @return quality candidate name
     */
    String qualityCandidate() {
        return this.qualityCandidate;
    }

    /**
     * Returns the human-readable language name.
     *
     * @return display language
     */
    String displayLanguage() {
        return this.displayLanguage;
    }

    /**
     * Returns the matching Radixor dictionary language.
     *
     * @return Radixor language
     */
    StemmerPatchTrieLoader.Language radixorLanguage() {
        return this.radixorLanguage;
    }

    /**
     * Creates a direct Snowball stemmer adapter.
     *
     * @return direct Snowball adapter
     */
    SnowballStemmerAdapter createDirectStemmer() {
        return new SnowballStemmerAdapter(this.directFactory);
    }

    /**
     * Returns the Lucene SnowballFilter algorithm name.
     *
     * @return Lucene SnowballFilter algorithm name
     */
    String luceneSnowballName() {
        if (this.luceneSnowballName == null) {
            throw new IllegalStateException("Lucene Snowball does not provide " + this.displayLanguage);
        }
        return this.luceneSnowballName;
    }

    /**
     * Reports whether the current Lucene integration exposes this exact
     * Snowball algorithm.
     *
     * @return {@code true} when a Lucene SnowballFilter comparison is available
     */
    boolean hasLuceneSnowball() {
        return this.luceneSnowballName != null;
    }

    /**
     * Reports whether this official direct case is shown in the homepage chart.
     *
     * @return always {@code true}; every exact-language direct case is charted
     */
    boolean homepageChart() {
        return true;
    }

    /**
     * Reports whether this case contributes to the homepage geometric mean.
     *
     * @return always {@code true}; every charted case uses a changed-token corpus
     */
    boolean homepageAggregate() {
        return this.homepageAggregate;
    }
}
