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
 *******************************************************************************/
package org.egothor.stemmer.benchmark;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.egothor.stemmer.benchmark.snowball.SnowballStemmer;
import org.egothor.stemmer.benchmark.snowball.ext.basqueStemmer;

/**
 * Exact official Snowball mappings for documentation-only prohibited models.
 *
 * <p>This closed benchmark authority is deliberately separate from the active
 * {@link SnowballLanguageCase} catalog. It does not register or distribute a
 * prohibited dictionary. The current prohibited intersection contains Basque;
 * Snowball 3.1.0 has no exact Welsh or Slovenian algorithm.</p>
 */
public enum ProhibitedSnowballLanguageCase {

    /** Official Basque Snowball over the privately retained Basque dictionary. */
    BASQUE("eus-default", "EUS", "Basque — UniMorph", "Basque",
            "SNOWBALL_BASQUE_DIRECT", basqueStemmer::new);

    private final String modelId;
    private final String language;
    private final String modelDisplayName;
    private final String displayLanguage;
    private final String candidate;
    private final Supplier<SnowballStemmer> factory;

    ProhibitedSnowballLanguageCase(final String modelId, final String language,
            final String modelDisplayName, final String displayLanguage, final String candidate,
            final Supplier<SnowballStemmer> factory) {
        this.modelId = modelId;
        this.language = language;
        this.modelDisplayName = modelDisplayName;
        this.displayLanguage = displayLanguage;
        this.candidate = candidate;
        this.factory = factory;
    }

    /** @return exact prohibited model identifier */
    public String modelId() {
        return this.modelId;
    }

    /** @return stable report language identifier */
    public String language() {
        return this.language;
    }

    /** @return exact private model display name from its guarded build metadata */
    public String modelDisplayName() {
        return this.modelDisplayName;
    }

    /** @return human-readable language name */
    public String displayLanguage() {
        return this.displayLanguage;
    }

    /** @return stable direct-Snowball candidate identifier */
    public String candidate() {
        return this.candidate;
    }

    /** @return a fresh mutable Snowball stemmer */
    public SnowballStemmer createStemmer() {
        return this.factory.get();
    }

    /**
     * Finds the exact comparator for one prohibited model.
     *
     * @param modelId prohibited model identifier
     * @return matching comparator, or empty when Snowball has no exact algorithm
     */
    public static Optional<ProhibitedSnowballLanguageCase> find(final String modelId) {
        Objects.requireNonNull(modelId, "modelId");
        for (ProhibitedSnowballLanguageCase languageCase : values()) {
            if (languageCase.modelId.equals(modelId)) {
                return Optional.of(languageCase);
            }
        }
        return Optional.empty();
    }
}
