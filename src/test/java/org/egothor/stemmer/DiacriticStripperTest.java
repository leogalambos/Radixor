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
package org.egothor.stemmer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DiacriticStripper}.
 */
@Tag("unit")
@Tag("diacritics")
@DisplayName("DiacriticStripper")
class DiacriticStripperTest {

    /**
     * Verifies that pure ASCII input is returned unchanged and without allocating a
     * new string instance.
     */
    @Test
    @DisplayName("ASCII input is returned as-is")
    void asciiInputIsReturnedAsIs() {
        final String input = "plain-ascii-123";

        final String stripped = DiacriticStripper.strip(input);

        assertSame(input, stripped);
    }

    /**
     * Verifies direct-table replacements for Czech and other common diacritics.
     */
    @Test
    @DisplayName("Direct replacement table strips common diacritics")
    void directReplacementTableStripsCommonDiacritics() {
        assertEquals("prilis zlutoucky kun", DiacriticStripper.strip("příliš žluťoučký kůň"));
    }

    /**
     * Verifies explicit multi-character replacements for ligatures and sharp s.
     */
    @Test
    @DisplayName("Special replacements support multi-character ASCII output")
    void specialReplacementsSupportMultiCharacterAsciiOutput() {
        assertEquals("strasse AEsir and OEuvre", DiacriticStripper.strip("straße Æsir and Œuvre"));
        assertEquals("aether oeuvre", DiacriticStripper.strip("æther œuvre"));
    }

    /**
     * Verifies Unicode decomposition fallback for characters not in the direct
     * replacement table.
     */
    @Test
    @DisplayName("Unicode decomposition fallback strips combining marks")
    void unicodeDecompositionFallbackStripsCombiningMarks() {
        assertEquals("I", DiacriticStripper.strip("İ"));
    }

    /**
     * Verifies behavior for non-Latin letters that cannot be mapped to ASCII.
     */
    @Test
    @DisplayName("Unmappable non-Latin characters remain unchanged")
    void unmappableNonLatinCharactersRemainUnchanged() {
        assertEquals("abcЖxyz", DiacriticStripper.strip("abcЖxyz"));
    }

    /**
     * Verifies mixed input where normalization starts mid-string and subsequent
     * unchanged characters are preserved.
     */
    @Test
    @DisplayName("Mixed input preserves untouched characters after normalization starts")
    void mixedInputPreservesUntouchedCharactersAfterNormalizationStarts() {
        assertEquals("Cafe-123", DiacriticStripper.strip("Café-123"));
    }
}
