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

/**
 * Immutable structural statistics derived from one compiled
 * {@link FrequencyTrie}.
 *
 * <p>
 * Structural storage statistics are computed over unique node instances in the
 * compiled trie graph. Logical path statistics account for every distinct
 * root-to-leaf path, including paths that converge on a shared node after
 * subtree reduction.
 * </p>
 *
 * <p>This value type is immutable and thread-safe.</p>
 *
 * @param internalNodeCount number of nodes that have at least one outgoing
 *                          child edge
 * @param leafNodeCount     number of nodes with no outgoing child edges
 * @param edgeCount         number of outgoing edges stored by unique nodes
 * @param acceptingLeafNodeCount number of unique contracted leaves accepting
 *                               any remaining lookup input
 * @param valueReferenceCount number of value references stored by unique nodes
 * @param distinctValueCount number of distinct stored values according to
 *                           {@link Object#equals(Object)}
 * @param logicalLeafPathCount number of distinct root-to-leaf paths represented
 *                             by the compiled graph
 * @param longestPath       maximum root-to-leaf path length in edges
 * @param averageLeafDepth  arithmetic mean root-to-leaf path length, weighted
 *                          by distinct logical paths, or {@code 0.0} when there
 *                          are no leaves
 * @param denseLookupNodeCount number of unique nodes using a dense child table
 * @param denseTableSlotCount total number of dense child-table slots
 */
public record TrieStatistics(long internalNodeCount, long leafNodeCount, long edgeCount,
        long acceptingLeafNodeCount, long valueReferenceCount, long distinctValueCount,
        long logicalLeafPathCount, long longestPath, double averageLeafDepth,
        long denseLookupNodeCount, long denseTableSlotCount) {

    /** Smallest valid count or path length. */
    private static final long MINIMUM_COUNT = 0L;

    /**
     * Creates validated structural statistics.
     *
     * @param internalNodeCount number of nodes that have at least one outgoing
     *                          child edge
     * @param leafNodeCount     number of nodes with no outgoing child edges
     * @param edgeCount         number of stored outgoing edges
     * @param acceptingLeafNodeCount number of contracted accepting leaves
     * @param valueReferenceCount number of stored value references
     * @param distinctValueCount number of distinct stored values
     * @param logicalLeafPathCount number of represented root-to-leaf paths
     * @param longestPath       maximum root-to-leaf path length in edges
     * @param averageLeafDepth  arithmetic mean root-to-leaf path length
     * @param denseLookupNodeCount number of nodes using dense child lookup
     * @param denseTableSlotCount total number of dense child-table slots
     * @throws IllegalArgumentException if a count or path length is negative, or
     *                                  if {@code averageLeafDepth} is negative or
     *                                  not finite
     */
    public TrieStatistics {
        if (internalNodeCount < MINIMUM_COUNT) {
            throw new IllegalArgumentException("internalNodeCount must not be negative.");
        }
        if (leafNodeCount < MINIMUM_COUNT) {
            throw new IllegalArgumentException("leafNodeCount must not be negative.");
        }
        final long[] nonNegativeCounts = { edgeCount, acceptingLeafNodeCount, valueReferenceCount,
                distinctValueCount, logicalLeafPathCount, longestPath, denseLookupNodeCount, denseTableSlotCount };
        for (final long count : nonNegativeCounts) {
            if (count < MINIMUM_COUNT) {
                throw new IllegalArgumentException("Trie statistics must not contain negative counts.");
            }
        }
        if (acceptingLeafNodeCount > leafNodeCount) {
            throw new IllegalArgumentException("acceptingLeafNodeCount must not exceed leafNodeCount.");
        }
        if (distinctValueCount > valueReferenceCount) {
            throw new IllegalArgumentException("distinctValueCount must not exceed valueReferenceCount.");
        }
        if (!Double.isFinite(averageLeafDepth) || averageLeafDepth < 0.0d) {
            throw new IllegalArgumentException("averageLeafDepth must be finite and not negative.");
        }
    }
}
