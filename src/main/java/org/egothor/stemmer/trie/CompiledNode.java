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
 * @param <V>           value type
 * @param edgeLabels    internal edge label array
 * @param children      internal child array
 * @param orderedValues internal ordered values array
 * @param orderedCounts internal ordered counts array
 */
@SuppressWarnings("PMD.DataClass")
public record CompiledNode<V>(char[] edgeLabels, CompiledNode<V>[] children, V[] orderedValues, int... orderedCounts) {

    /**
     * Creates one validated compiled node.
     *
     * @throws NullPointerException     if any array argument is {@code null}
     * @throws IllegalArgumentException if the edge-related arrays or value-related
     *                                  arrays do not have matching lengths
     */
    public CompiledNode {
        Objects.requireNonNull(edgeLabels, "edgeLabels");
        Objects.requireNonNull(children, "children");
        Objects.requireNonNull(orderedValues, "orderedValues");
        Objects.requireNonNull(orderedCounts, "orderedCounts");

        if (edgeLabels.length != children.length) {
            throw new IllegalArgumentException("edgeLabels and children must have the same length.");
        }
        if (orderedValues.length != orderedCounts.length) {
            throw new IllegalArgumentException("orderedValues and orderedCounts must have the same length.");
        }
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
    @Override
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
    @Override
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
    @Override
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
    @Override
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    public int[] orderedCounts() {
        return this.orderedCounts;
    }

    /**
     * Finds a child for the supplied edge character.
     *
     * @param edge edge character
     * @return child node, or {@code null} if absent
     */
    public CompiledNode<V> findChild(final char edge) {
        final int index = Arrays.binarySearch(this.edgeLabels, edge);
        if (index < 0) {
            return null;
        }
        return this.children[index];
    }
}
