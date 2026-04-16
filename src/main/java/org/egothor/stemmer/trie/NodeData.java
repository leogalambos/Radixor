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

import java.util.Objects;

/**
 * Intermediate node data used during deserialization before child references
 * are resolved.
 *
 * <p>
 * The arrays exposed by the accessors are the internal backing storage of this
 * holder. They are returned directly for efficiency because the deserialization
 * pipeline copies references into immutable compiled nodes immediately after
 * the record is created. Callers must therefore treat every returned array as
 * read-only.
 *
 * @param <V>           value type
 * @param edgeLabels    edge labels
 * @param childNodeIds  child node identifiers
 * @param orderedValues ordered values
 * @param orderedCounts ordered counts
 */
@SuppressWarnings("PMD.DataClass")
public record NodeData<V>(char[] edgeLabels, int[] childNodeIds, V[] orderedValues, int... orderedCounts) {
    /**
     * Creates one validated node-data holder.
     *
     * @throws NullPointerException     if any array argument is {@code null}
     * @throws IllegalArgumentException if the edge-related arrays or value-related
     *                                  arrays do not have matching lengths
     */
    public NodeData {
        Objects.requireNonNull(edgeLabels, "edgeLabels");
        Objects.requireNonNull(childNodeIds, "childNodeIds");
        Objects.requireNonNull(orderedValues, "orderedValues");
        Objects.requireNonNull(orderedCounts, "orderedCounts");

        if (edgeLabels.length != childNodeIds.length) {
            throw new IllegalArgumentException("edgeLabels and childNodeIds must have the same length.");
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
     * Returns the internal child-node identifier array.
     *
     * <p>
     * The returned array is not copied for performance reasons and must be treated
     * as read-only.
     *
     * @return internal child-node identifier array
     */
    @Override
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    public int[] childNodeIds() {
        return this.childNodeIds;
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

}
