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
import org.egothor.stemmer.benchmark.snowball.ext.czechStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.danishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.dutchStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.finnishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.frenchStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.germanStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.hungarianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.italianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.norwegianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.persianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.polishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.portugueseStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.russianStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.spanishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.swedishStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.yiddishStemmer;

/**
 * Maps Radixor dictionary languages to matching official Snowball algorithms.
 */
enum SnowballLanguageCase {

    /**
     * Czech Snowball stemming over the Radixor Czech dictionary.
     */
    CZECH("Czech", StemmerPatchTrieLoader.Language.CS_CZ, czechStemmer::new),

    /**
     * Danish Snowball stemming over the Radixor Danish dictionary.
     */
    DANISH("Danish", StemmerPatchTrieLoader.Language.DA_DK, danishStemmer::new, "Danish"),

    /**
     * Dutch Snowball stemming over the Radixor Dutch dictionary.
     */
    DUTCH("Dutch", StemmerPatchTrieLoader.Language.NL_NL, dutchStemmer::new, "Dutch"),

    /**
     * Finnish Snowball stemming over the Radixor Finnish dictionary.
     */
    FINNISH("Finnish", StemmerPatchTrieLoader.Language.FI_FI, finnishStemmer::new, "Finnish"),

    /**
     * French Snowball stemming over the Radixor French dictionary.
     */
    FRENCH("French", StemmerPatchTrieLoader.Language.FR_FR, frenchStemmer::new, "French"),

    /**
     * German Snowball stemming over the Radixor German dictionary.
     */
    GERMAN("German", StemmerPatchTrieLoader.Language.DE_DE, germanStemmer::new, "German"),

    /**
     * Hungarian Snowball stemming over the Radixor Hungarian dictionary.
     */
    HUNGARIAN("Hungarian", StemmerPatchTrieLoader.Language.HU_HU, hungarianStemmer::new, "Hungarian"),

    /**
     * Italian Snowball stemming over the Radixor Italian dictionary.
     */
    ITALIAN("Italian", StemmerPatchTrieLoader.Language.IT_IT, italianStemmer::new, "Italian"),

    /**
     * Norwegian Snowball stemming over the Radixor Bokmal dictionary.
     */
    NORWEGIAN_BOKMAL("Norwegian Bokmal", StemmerPatchTrieLoader.Language.NB_NO, norwegianStemmer::new,
            "Norwegian"),

    /**
     * Norwegian Snowball stemming over the Radixor Nynorsk dictionary.
     */
    NORWEGIAN_NYNORSK("Norwegian Nynorsk", StemmerPatchTrieLoader.Language.NN_NO, norwegianStemmer::new,
            "Norwegian"),

    /**
     * Persian Snowball stemming over the Radixor Persian dictionary.
     */
    PERSIAN("Persian", StemmerPatchTrieLoader.Language.FA_IR, persianStemmer::new),

    /**
     * Polish Snowball stemming over the Radixor Polish dictionary.
     */
    POLISH("Polish", StemmerPatchTrieLoader.Language.PL_PL, polishStemmer::new),

    /**
     * Portuguese Snowball stemming over the Radixor Portuguese dictionary.
     */
    PORTUGUESE("Portuguese", StemmerPatchTrieLoader.Language.PT_PT, portugueseStemmer::new, "Portuguese"),

    /**
     * Russian Snowball stemming over the Radixor Russian dictionary.
     */
    RUSSIAN("Russian", StemmerPatchTrieLoader.Language.RU_RU, russianStemmer::new, "Russian"),

    /**
     * Spanish Snowball stemming over the Radixor Spanish dictionary.
     */
    SPANISH("Spanish", StemmerPatchTrieLoader.Language.ES_ES, spanishStemmer::new, "Spanish"),

    /**
     * Swedish Snowball stemming over the Radixor Swedish dictionary.
     */
    SWEDISH("Swedish", StemmerPatchTrieLoader.Language.SV_SE, swedishStemmer::new, "Swedish"),

    /**
     * Yiddish Snowball stemming over the Radixor Yiddish dictionary.
     */
    YIDDISH("Yiddish", StemmerPatchTrieLoader.Language.YI, yiddishStemmer::new, "Yiddish");

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

    /**
     * Creates a direct-only language case not provided by the current Lucene
     * Snowball implementation.
     *
     * @param displayLanguage human-readable language name
     * @param radixorLanguage matching Radixor language resource
     * @param directFactory   direct Snowball stemmer factory
     */
    SnowballLanguageCase(final String displayLanguage, final StemmerPatchTrieLoader.Language radixorLanguage,
            final SnowballStemmerAdapter.Factory directFactory) {
        this(displayLanguage, radixorLanguage, directFactory, null);
    }

    /**
     * Creates a language case.
     *
     * @param displayLanguage   human-readable language name
     * @param radixorLanguage   matching Radixor language resource
     * @param directFactory     direct Snowball stemmer factory
     * @param luceneSnowballName Lucene SnowballFilter algorithm name
     */
    SnowballLanguageCase(final String displayLanguage, final StemmerPatchTrieLoader.Language radixorLanguage,
            final SnowballStemmerAdapter.Factory directFactory, final String luceneSnowballName) {
        this.displayLanguage = displayLanguage;
        this.radixorLanguage = radixorLanguage;
        this.directFactory = directFactory;
        this.luceneSnowballName = luceneSnowballName;
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
}
