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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CompiledNode} and {@link NodeData} validation and
 * documented backing-array exposure.
 */
@Tag("unit")
@Tag("trie")
@Tag("lookup")
@DisplayName("CompiledNode and NodeData")
class CompiledNodeAndNodeDataTest {

    /**
     * Creates a typed child array for compiled-node tests.
     *
     * @param length requested array length
     * @return typed child array
     */
    @SuppressWarnings("unchecked")
    private static CompiledNode<String>[] children(final int length) {
        return new CompiledNode[length];
    }

    /**
     * Creates an empty child array for leaf compiled-node tests.
     *
     * @return empty typed child array
     */
    private static CompiledNode<String>[] noChildren() {
        return children(0);
    }

    /**
     * Creates a leaf node used as a child in lookup tests.
     *
     * @return leaf node
     */
    private static CompiledNode<String> leaf() {
        return new CompiledNode<>(new char[0], noChildren(), new String[0], new int[0]);
    }

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
        final CompiledNode<String>[] children = noChildren();

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
        final CompiledNode<String>[] children = noChildren();

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
        final CompiledNode<String>[] children = children(1);
        final String[] orderedValues = new String[] { "stem" };
        final int[] orderedCounts = new int[] { 5 };
        final CompiledNode<String> node = new CompiledNode<>(edgeLabels, children, orderedValues, orderedCounts);

        assertSame(edgeLabels, node.edgeLabels());
        assertSame(children, node.children());
        assertSame(orderedValues, node.orderedValues());
        assertSame(orderedCounts, node.orderedCounts());
    }

    /**
     * Verifies that dense lookup is used when the interval is compact.
     */
    @Test
    @DisplayName("CompiledNode can resolve child via dense lookup table")
    void compiledNodeUsesDenseLookupForCompactIntervals() {
        final CompiledNode<String>[] children = children(4);
        children[0] = leaf();
        children[1] = leaf();
        children[2] = leaf();
        children[3] = leaf();

        final CompiledNode<String> node = new CompiledNode<>(new char[] { 'a', 'b', 'c', 'd' }, children,
                new String[] { "1", "2", "3", "4" }, new int[] { 1, 1, 1, 1 });

        assertTrue(node.hasDenseLookup());

        assertSame(children[0], node.findChild('a'));
        assertSame(children[3], node.findChild('d'));
        assertSame(null, node.findChild('z'));
    }

    /**
     * Verifies that fallback linear scan is used for small node degree.
     */
    @Test
    @DisplayName("CompiledNode resolves child by linear scan for small degree")
    void compiledNodeUsesLinearScanForSmallDegree() {
        final CompiledNode<String>[] children = children(4);
        final CompiledNode<String> childA = leaf();
        final CompiledNode<String> childB = leaf();
        final CompiledNode<String> childC = leaf();
        final CompiledNode<String> childD = leaf();
        children[0] = childA;
        children[1] = childB;
        children[2] = childC;
        children[3] = childD;

        final CompiledNode<String> node = new CompiledNode<>(new char[] { 'a', 'z', '中', '你' }, children,
                new String[] { "1", "2", "3", "4" }, 0, new int[] { 1, 1, 1, 1 });

        assertFalse(node.hasDenseLookup());

        assertSame(childA, node.findChild('a'));
        assertSame(childD, node.findChild('你'));
        assertSame(null, node.findChild('b'));
    }

    /**
     * Verifies that fallback binary search is used for larger node degree without
     * dense lookup.
     */
    @Test
    @DisplayName("CompiledNode resolves child by binary search for large degree")
    void compiledNodeUsesBinarySearchForLargeDegree() {
        final CompiledNode<String>[] children = children(5);
        final CompiledNode<String> childA = leaf();
        final CompiledNode<String> childB = leaf();
        final CompiledNode<String> childC = leaf();
        final CompiledNode<String> childD = leaf();
        final CompiledNode<String> childE = leaf();
        children[0] = childA;
        children[1] = childB;
        children[2] = childC;
        children[3] = childD;
        children[4] = childE;

        final CompiledNode<String> node = new CompiledNode<>(new char[] { 'a', 'c', 'k', 't', 'z' }, children,
                new String[] { "1", "2", "3", "4", "5" }, 0, new int[] { 1, 1, 1, 1, 1 });

        assertFalse(node.hasDenseLookup());

        assertSame(childC, node.findChild('k'));
        assertSame(childE, node.findChild('z'));
        assertSame(null, node.findChild('x'));
    }

    /**
     * Verifies the basic node-state helpers that are used by diagnostics and
     * behavioral checks.
     */
    @Test
    @DisplayName("CompiledNode reports leaf, value and edge presence state")
    void compiledNodeReportsNodeStateHelpers() {
        final CompiledNode<String>[] childless = noChildren();
        final CompiledNode<String> leaf = new CompiledNode<>(new char[0], childless, new String[0], new int[0]);

        assertTrue(leaf.isLeaf());
        assertFalse(leaf.hasChildren());
        assertFalse(leaf.hasValues());
        assertFalse(leaf.hasEdge('a'));

        final CompiledNode<String>[] child = children(1);
        final String[] orderedValues = new String[] { "leaf" };
        final int[] orderedCounts = new int[] { 1 };
        child[0] = new CompiledNode<>(new char[0], noChildren(), orderedValues, orderedCounts);
        final CompiledNode<String> node = new CompiledNode<>(new char[] { 'a' }, child, orderedValues, orderedCounts);

        assertFalse(node.isLeaf());
        assertTrue(node.hasChildren());
        assertTrue(node.hasValues());
        assertTrue(node.valueCount() > 0);
        assertTrue(node.hasEdge('a'));
        assertFalse(node.hasEdge('b'));
    }

    /**
     * Verifies structural equality and hash-code behavior for compiled nodes.
     */
    @Test
    @DisplayName("CompiledNode equals and hashCode align for identical structure")
    void compiledNodeEqualsAndHashCodeAlignForIdenticalStructure() {
        final CompiledNode<String>[] child = children(1);
        final CompiledNode<String> leaf = new CompiledNode<>(new char[0], noChildren(), new String[] { "v" },
                new int[] { 1 });
        child[0] = leaf;

        final CompiledNode<String> first = new CompiledNode<>(new char[] { 'a' }, child, new String[] { "x" },
                new int[] { 2 });
        final CompiledNode<String> second = new CompiledNode<>(new char[] { 'a' }, child, new String[] { "x" },
                new int[] { 2 });

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
