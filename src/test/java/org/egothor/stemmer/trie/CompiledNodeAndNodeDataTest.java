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
 * 3. All advertising materials mentioning features or use of this software must
 *    display the following acknowledgement:
 *    This product includes software developed by the Egothor project.
 *
 * 4. Neither the name of the copyright holder nor the names of its contributors
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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CompiledNode} and {@link NodeData} validation and
 * documented backing-array exposure.
 */
@Tag("unit")
@Tag("fast")
@Tag("trie")
@DisplayName("CompiledNode and NodeData")
class CompiledNodeAndNodeDataTest {

    /**
     * Verifies that {@link NodeData} rejects mismatched edge-related array lengths.
     */
    @Test
    @DisplayName("NodeData rejects mismatched edge arrays")
    void nodeDataShouldRejectMismatchedEdgeArrays() {
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new NodeData<String>(new char[] { 'a' }, new int[0], new String[0], new int[0]));

        assertEquals("edgeLabels and childNodeIds must have the same length.", exception.getMessage());
    }

    /**
     * Verifies that {@link NodeData} rejects mismatched value-related array
     * lengths.
     */
    @Test
    @DisplayName("NodeData rejects mismatched value arrays")
    void nodeDataShouldRejectMismatchedValueArrays() {
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new NodeData<String>(new char[0], new int[0], new String[] { "stem" }, new int[0]));

        assertEquals("orderedValues and orderedCounts must have the same length.", exception.getMessage());
    }

    /**
     * Verifies that {@link NodeData} continues to expose the documented backing
     * arrays directly.
     */
    @Test
    @DisplayName("NodeData accessors expose documented backing arrays")
    void nodeDataAccessorsShouldExposeDocumentedBackingArrays() {
        final char[] edgeLabels = new char[] { 'a' };
        final int[] childNodeIds = new int[] { 7 };
        final String[] orderedValues = new String[] { "stem" };
        final int[] orderedCounts = new int[] { 3 };
        final NodeData<String> nodeData = new NodeData<>(edgeLabels, childNodeIds, orderedValues, orderedCounts);

        assertSame(edgeLabels, nodeData.edgeLabels());
        assertSame(childNodeIds, nodeData.childNodeIds());
        assertSame(orderedValues, nodeData.orderedValues());
        assertSame(orderedCounts, nodeData.orderedCounts());
    }

    /**
     * Verifies that {@link CompiledNode} rejects mismatched edge and child arrays.
     */
    @Test
    @DisplayName("CompiledNode rejects mismatched edge and child arrays")
    void compiledNodeShouldRejectMismatchedEdgeAndChildArrays() {
        @SuppressWarnings("unchecked")
        final CompiledNode<String>[] children = new CompiledNode[0];

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CompiledNode<String>(new char[] { 'a' }, children, new String[0], new int[0]));

        assertEquals("edgeLabels and children must have the same length.", exception.getMessage());
    }

    /**
     * Verifies that {@link CompiledNode} rejects mismatched value arrays.
     */
    @Test
    @DisplayName("CompiledNode rejects mismatched value arrays")
    void compiledNodeShouldRejectMismatchedValueArrays() {
        @SuppressWarnings("unchecked")
        final CompiledNode<String>[] children = new CompiledNode[0];

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CompiledNode<String>(new char[0], children, new String[] { "stem" }, new int[0]));

        assertEquals("orderedValues and orderedCounts must have the same length.", exception.getMessage());
    }

    /**
     * Verifies that {@link CompiledNode} continues to expose the documented backing
     * arrays directly.
     */
    @Test
    @DisplayName("CompiledNode accessors expose documented backing arrays")
    void compiledNodeAccessorsShouldExposeDocumentedBackingArrays() {
        final char[] edgeLabels = new char[] { 'a' };
        @SuppressWarnings("unchecked")
        final CompiledNode<String>[] children = new CompiledNode[1];
        final String[] orderedValues = new String[] { "stem" };
        final int[] orderedCounts = new int[] { 5 };
        final CompiledNode<String> node = new CompiledNode<>(edgeLabels, children, orderedValues, orderedCounts);

        assertSame(edgeLabels, node.edgeLabels());
        assertSame(children, node.children());
        assertSame(orderedValues, node.orderedValues());
        assertSame(orderedCounts, node.orderedCounts());
    }
}
