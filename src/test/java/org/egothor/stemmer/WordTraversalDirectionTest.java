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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("WordTraversalDirection")
class WordTraversalDirectionTest {

    @Test
    @DisplayName("startIndex follows direction and validates negatives")
    void startIndexFollowsDirectionAndValidatesNegatives() {
        assertAll(() -> assertEquals(0, WordTraversalDirection.FORWARD.startIndex(3)),
                () -> assertEquals(2, WordTraversalDirection.BACKWARD.startIndex(3)),
                () -> assertEquals(-1, WordTraversalDirection.FORWARD.startIndex(0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> WordTraversalDirection.BACKWARD.startIndex(-1)));
    }

    @Test
    @DisplayName("logicalIndex maps offsets in both directions")
    void logicalIndexMapsOffsetsInBothDirections() {
        assertAll(() -> assertEquals(0, WordTraversalDirection.FORWARD.logicalIndex(4, 0)),
                () -> assertEquals(3, WordTraversalDirection.BACKWARD.logicalIndex(4, 0)),
                () -> assertEquals(1, WordTraversalDirection.FORWARD.logicalIndex(4, 1)),
                () -> assertEquals(2, WordTraversalDirection.BACKWARD.logicalIndex(4, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> WordTraversalDirection.FORWARD.logicalIndex(-1, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> WordTraversalDirection.BACKWARD.logicalIndex(3, 3)));
    }

    @Test
    @DisplayName("traversal character conversion preserves and reverses as expected")
    void traversalCharacterConversionPreservesAndReversesAsExpected() {
        assertAll(() -> assertArrayEquals(new char[] { 'a', 'b', 'c' },
                WordTraversalDirection.FORWARD.toTraversalCharacters("abc")),
                () -> assertArrayEquals(new char[] { 'c', 'b', 'a' },
                        WordTraversalDirection.BACKWARD.toTraversalCharacters("abc")),
                () -> assertEquals("abc", WordTraversalDirection.FORWARD.traversalPathToLogicalKey("abc")),
                () -> assertEquals("cba", WordTraversalDirection.BACKWARD.traversalPathToLogicalKey("abc")),
                () -> assertThrows(NullPointerException.class,
                        () -> WordTraversalDirection.FORWARD.toTraversalCharacters(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> WordTraversalDirection.BACKWARD.traversalPathToLogicalKey(null)));
    }
}
