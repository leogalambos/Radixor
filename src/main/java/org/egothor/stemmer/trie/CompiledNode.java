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

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable compiled trie node optimized for read access.
 *
 * <p>
 * The returned arrays are the internal backing storage of the compiled node.
 * They are exposed for efficient access by closely related trie infrastructure
 * and therefore must never be modified by callers. The node itself is still
 * immutable from the public API perspective because construction wires these
 * arrays once and all lookup operations thereafter treat them as read-only.
 *
 * @param <V> value type
 */
public final class CompiledNode<V> {

    /**
     * Default dense child lookup span in characters used when an explicit override
     * is not provided.
     */
    public static final int DEFAULT_MAX_EXPANDED_INDEX = 512;

    /**
     * Number of child edges where linear scan is cheaper than binary search.
     */
    private static final int LINEAR_CHILD_COUNT_THRESHOLD = 4;

    /**
     * Edge labels in sorted ascending order.
     */
    private final char[] edgeLabels;

    /**
     * Sparse child array aligned with {@link #edgeLabels}.
     */
    private final CompiledNode<V>[] children;

    /**
     * Dense child lookup table used when labels fit into a compact char interval.
     * <p>
     * The table enables direct O(1) indexing for child lookup and is allocated only
     * when the character span of this node's edges is within the configured
     * threshold.
     * </p>
     */
    private final CompiledNode<V>[] denseChildren;

    /**
     * Normalized minimum edge value for the dense lookup table.
     */
    private final int denseEdgeMin;

    /**
     * Values stored at this node in local order.
     */
    private final V[] orderedValues;

    /**
     * Occurrence counts aligned with {@link #orderedValues}.
     */
    private final int[] orderedCounts;

    /**
     * Creates one validated compiled node using {@link #DEFAULT_MAX_EXPANDED_INDEX}
     * for dense lookup sizing.
     *
     * @throws NullPointerException     if any array argument is {@code null}
     * @throws IllegalArgumentException if the edge-related arrays or value-related
     *                                  arrays do not have matching lengths
     */
    public CompiledNode(final char[] edgeLabels, final CompiledNode<V>[] children, final V[] orderedValues,
            final int... orderedCounts) {
        this(edgeLabels, children, orderedValues, DEFAULT_MAX_EXPANDED_INDEX, orderedCounts);
    }

    /**
     * Creates one validated compiled node.
     *
     * @param maxExpandedIndex upper bound for the dense lookup interval size; zero
     *                         disables dense lookup. Larger values improve
     *                         direct-index likelihood while increasing dense table
     *                         memory in compact-label nodes.
     * @throws NullPointerException     if any array argument is {@code null}
     * @throws IllegalArgumentException if the edge-related arrays or value-related
     *                                  arrays do not have matching lengths or the
     *                                  dense interval size is negative
     */
    public CompiledNode(final char[] edgeLabels, final CompiledNode<V>[] children, final V[] orderedValues,
            final int maxExpandedIndex, final int... orderedCounts) {
        Objects.requireNonNull(edgeLabels, "edgeLabels");
        Objects.requireNonNull(children, "children");
        Objects.requireNonNull(orderedValues, "orderedValues");
        Objects.requireNonNull(orderedCounts, "orderedCounts");

        if (maxExpandedIndex < 0) {
            throw new IllegalArgumentException("maxExpandedIndex must be non-negative.");
        }

        if (edgeLabels.length != children.length) {
            throw new IllegalArgumentException("edgeLabels and children must have the same length.");
        }
        if (orderedValues.length != orderedCounts.length) {
            throw new IllegalArgumentException("orderedValues and orderedCounts must have the same length.");
        }

        this.edgeLabels = edgeLabels;
        this.children = children;
        this.orderedValues = orderedValues;
        this.orderedCounts = orderedCounts;

        if (edgeLabels.length == 0 || maxExpandedIndex == 0) {
            this.denseChildren = null;
            this.denseEdgeMin = 0;
            return;
        }

        final int minEdge = edgeLabels[0];
        final int maxEdge = edgeLabels[edgeLabels.length - 1];
        final int span = maxEdge - minEdge;

        if (span < 0 || span > maxExpandedIndex) {
            this.denseChildren = null;
            this.denseEdgeMin = 0;
            return;
        }

        @SuppressWarnings("unchecked")
        final CompiledNode<V>[] dense = new CompiledNode[span + 1];
        for (int edgeIndex = 0; edgeIndex < edgeLabels.length; edgeIndex++) {
            dense[edgeLabels[edgeIndex] - minEdge] = children[edgeIndex];
        }

        this.denseChildren = dense;
        this.denseEdgeMin = minEdge;
    }

    /**
     * Returns the internal edge-label array.
     *
     * <p>
     * The returned array is not copied for performance reasons and must be treated
     * as read-only.
     *
     * @return internal edge-label array
     */
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    public char[] edgeLabels() {
        return this.edgeLabels;
    }

    /**
     * Returns the internal child-node array.
     *
     * <p>
     * The returned array is not copied for performance reasons and must be treated
     * as read-only by external callers.
     *
     * @return internal child-node array
     */
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    public CompiledNode<V>[] children() {
        return this.children;
    }

    /**
     * Returns the internal ordered-values array.
     *
     * <p>
     * The returned array is not copied for performance reasons and must be treated
     * as read-only.
     *
     * @return internal ordered-values array
     */
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    public V[] orderedValues() {
        return this.orderedValues;
    }

    /**
     * Returns the internal ordered-counts array.
     *
     * <p>
     * The returned array is not copied for performance reasons and must be treated
     * as read-only.
     *
     * @return internal ordered-counts array
     */
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    public int[] orderedCounts() {
        return this.orderedCounts;
    }

    /**
     * Returns the number of child edges represented by this node.
     *
     * @return child edge count
     */
    public int edgeCount() {
        return this.edgeLabels.length;
    }

    /**
     * Returns the number of values stored in this node.
     *
     * @return value count
     */
    public int valueCount() {
        return this.orderedValues.length;
    }

    /**
     * Indicates whether this node stores any values.
     *
     * @return {@code true} when values are present at this node
     */
    public boolean hasValues() {
        return this.orderedValues.length > 0;
    }

    /**
     * Indicates whether this node has child edges.
     *
     * @return {@code true} when this node has at least one outgoing edge
     */
    public boolean hasChildren() {
        return this.edgeLabels.length > 0;
    }

    /**
     * Indicates whether this node has no child edges.
     *
     * @return {@code true} when this node is a terminal leaf node
     */
    public boolean isLeaf() {
        return !hasChildren();
    }

    /**
     * Tests whether an edge label is present at this node.
     *
     * @param edge edge label
     * @return {@code true} if this node contains the supplied edge label
     */
    public boolean hasEdge(final char edge) {
        return findChild(edge) != null;
    }

    /**
     * Indicates whether this node has a dense direct-index child lookup table.
     *
     * @return {@code true} when a direct-index child table is available
     */
    public boolean hasDenseLookup() {
        return this.denseChildren != null;
    }

    /**
     * Returns a small memory-related metric describing this node's dense table
     * size.
     *
     * @return number of dense table slots, or {@code 0} when dense lookup is not
     *         enabled
     */
    public int denseTableLength() {
        return this.denseChildren == null ? 0 : this.denseChildren.length;
    }

    /**
     * Returns a compact structural summary used by diagnostics and tests.
     *
     * @return summary hash for node structure and contents
     */
    @Override
    public int hashCode() {
        int hash = Arrays.hashCode(this.edgeLabels);
        hash = 31 * hash + Arrays.hashCode(this.children);
        hash = 31 * hash + Arrays.hashCode(this.orderedValues);
        hash = 31 * hash + Arrays.hashCode(this.orderedCounts);
        hash = 31 * hash + Objects.hash(this.denseEdgeMin);
        hash = 31 * hash + (hasDenseLookup() ? Arrays.hashCode(this.denseChildren) : 0);
        return hash;
    }

    /**
     * Compares structural node content, including dense table availability.
     *
     * @param object comparison object
     * @return {@code true} when nodes describe identical structure and payload
     */
    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CompiledNode<?> other)) {
            return false;
        }
        return Arrays.equals(this.edgeLabels, other.edgeLabels) && Arrays.equals(this.children, other.children)
                && Arrays.equals(this.orderedValues, other.orderedValues)
                && Arrays.equals(this.orderedCounts, other.orderedCounts) && this.denseEdgeMin == other.denseEdgeMin
                && Arrays.equals(this.denseChildren, other.denseChildren);
    }

    /**
     * Returns a short summary useful for debugging and diagnostics.
     *
     * @return textual node summary
     */
    @Override
    public String toString() {
        return "CompiledNode{" + "edgeCount=" + this.edgeLabels.length + ", orderedValueCount="
                + this.orderedValues.length + ", denseTableLength=" + denseTableLength() + '}';
    }

    /**
     * Finds a child for the supplied edge character.
     * 
     * Lookup order is:
     * <ol>
     * <li>dense array index (if the label interval is compact enough),</li>
     * <li>small-child linear scan when the fallback node has
     * {@value #LINEAR_CHILD_COUNT_THRESHOLD} or fewer edges,</li>
     * <li>binary search over sorted labels.</li>
     * </ol>
     *
     * @param edge edge character
     * @return child node, or {@code null} if absent
     */
    public CompiledNode<V> findChild(final char edge) {
        final int childCount = this.edgeLabels.length;
        if (childCount == 0) {
            return null;
        }

        if (this.denseChildren != null) {
            final int denseIndex = edge - this.denseEdgeMin;
            if (denseIndex < 0 || denseIndex >= this.denseChildren.length) {
                return null;
            }
            return this.denseChildren[denseIndex];
        }

        if (childCount <= LINEAR_CHILD_COUNT_THRESHOLD) {
            for (int index = 0; index < childCount; index++) {
                if (this.edgeLabels[index] == edge) {
                    return this.children[index];
                }
            }
            return null;
        }

        final int index = Arrays.binarySearch(this.edgeLabels, edge);
        if (index < 0) {
            return null;
        }
        return this.children[index];
    }
}
