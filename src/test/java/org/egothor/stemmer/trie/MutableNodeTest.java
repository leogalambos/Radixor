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
package org.egothor.stemmer.trie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MutableNode}.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("MutableNode")
class MutableNodeTest {

    @Test
    @DisplayName("must create empty maps on construction")
    void shouldCreateEmptyMapsOnConstruction() {
        final MutableNode<String> node = new MutableNode<>();

        assertTrue(node.children().isEmpty());
        assertTrue(node.valueCounts().isEmpty());
    }

    @Test
    @DisplayName("children must expose mutable backing map")
    void shouldExposeMutableBackingChildrenMap() {
        final MutableNode<String> node = new MutableNode<>();
        final MutableNode<String> child = new MutableNode<>();

        final Map<Character, MutableNode<String>> children = node.children();
        children.put('x', child);

        assertSame(children, node.children());
        assertSame(child, node.children().get('x'));
    }

    @Test
    @DisplayName("valueCounts must expose mutable backing map")
    void shouldExposeMutableBackingValueCountsMap() {
        final MutableNode<String> node = new MutableNode<>();

        final Map<String, Integer> valueCounts = node.valueCounts();
        valueCounts.put("stem", 3);

        assertSame(valueCounts, node.valueCounts());
        assertEquals(Integer.valueOf(3), node.valueCounts().get("stem"));
    }
}
