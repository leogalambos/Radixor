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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("TrieMetadata")
class TrieMetadataTest {

    @Test
    @DisplayName("Text block roundtrip preserves all persisted fields")
    void textBlockRoundtripPreservesAllPersistedFields() {
        final TrieMetadata metadata = new TrieMetadata(5, WordTraversalDirection.FORWARD,
                new ReductionSettings(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS, 80, 4),
                DiacriticProcessingMode.AS_IS, CaseProcessingMode.AS_IS);

        final String textBlock = metadata.toTextBlock();
        final TrieMetadata parsed = TrieMetadata.fromTextBlock(5, textBlock);

        assertAll(() -> assertEquals(metadata.traversalDirection(), parsed.traversalDirection()),
                () -> assertEquals(metadata.reductionSettings(), parsed.reductionSettings()),
                () -> assertEquals(metadata.diacriticProcessingMode(), parsed.diacriticProcessingMode()),
                () -> assertEquals(metadata.caseProcessingMode(), parsed.caseProcessingMode()),
                () -> assertTrue(textBlock.contains("rightToLeft=true")));
    }

    @Test
    @DisplayName("Text block parser rejects malformed input")
    void textBlockParserRejectsMalformedInput() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TrieMetadata.fromTextBlock(5, "unknown-header\nx=y\n")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TrieMetadata.fromTextBlock(5, "radixor.metadata.v1\nmissingDelimiter\n")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TrieMetadata.fromTextBlock(5, "radixor.metadata.v1\ntraversalDirection=FORWARD\n")));
    }
}
