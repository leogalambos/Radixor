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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Tests the exact prohibited-model/official-Snowball intersection. */
final class ProhibitedSnowballLanguageCaseTest {

    @Test
    void containsOnlyTheOfficialBasqueExactLanguageCase() {
        final List<String> modelIds = List.of(ProhibitedSnowballLanguageCase.values()).stream()
                .map(ProhibitedSnowballLanguageCase::modelId)
                .toList();

        assertEquals(List.of("eus-default"), modelIds);
        assertEquals("EUS", ProhibitedSnowballLanguageCase.BASQUE.language());
        assertEquals("Basque — UniMorph", ProhibitedSnowballLanguageCase.BASQUE.modelDisplayName());
        assertEquals("Basque", ProhibitedSnowballLanguageCase.BASQUE.displayLanguage());
        assertEquals("SNOWBALL_BASQUE_DIRECT", ProhibitedSnowballLanguageCase.BASQUE.candidate());
        assertTrue(ProhibitedSnowballLanguageCase.find("eus-default").isPresent());
        assertTrue(ProhibitedSnowballLanguageCase.find("cy-gb-default").isEmpty());
        assertTrue(ProhibitedSnowballLanguageCase.find("sl-si-default").isEmpty());
    }
}
