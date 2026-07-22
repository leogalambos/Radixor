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

import org.egothor.stemmer.StemmerPatchTrieLoader;

/**
 * Builds a deterministic English token corpus for side-by-side stemming
 * benchmarks from the bundled Radixor English dictionary resource.
 *
 * <p>
 * The dictionary resource stores the expected stem as the first tab-separated
 * field on each line and its surface variants on the same line. This helper
 * uses only token/root pairs where the token differs from the expected root for
 * timing. Resources smaller than the shared timing minimum are repeated
 * deterministically by {@link LanguageBenchmarkCorpus}.
 * </p>
 */
final class EnglishComparisonCorpus {

    /**
     * Utility class.
     */
    private EnglishComparisonCorpus() {
        throw new AssertionError("No instances.");
    }

    /**
     * Creates a deterministic token corpus for English stemming comparison.
     *
     * @return token array in stable order
     * @throws IOException if the bundled English resource cannot be read
     */
    static String[] createTokens() throws IOException {
        return createCorpus().tokens();
    }

    /**
     * Creates a deterministic changed-token corpus and expected root array for
     * English stemming comparison.
     *
     * @return token corpus with expected roots
     * @throws IOException if the bundled English resource cannot be read
     */
    static LanguageBenchmarkCorpus.Corpus createCorpus() throws IOException {
        return LanguageBenchmarkCorpus.createChangedCorpus(StemmerPatchTrieLoader.Language.US_UK);
    }
}
