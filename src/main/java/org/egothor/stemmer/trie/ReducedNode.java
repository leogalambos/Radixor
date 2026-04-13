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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical reduced node used during subtree merging.
 *
 * <p>
 * The maps exposed by the accessors are the internal backing state of the
 * canonical reduced node. They are returned directly for efficiency and are
 * intended only for closely related trie-reduction infrastructure.
 *
 * @param <V> value type
 */
public final class ReducedNode<V> {

    /**
     * Reduction signature.
     */
    private final ReductionSignature<V> signature;

    /**
     * Aggregated local value counts.
     */
    private final Map<V, Integer> localCounts;

    /**
     * Canonical children by edge.
     */
    private final Map<Character, ReducedNode<V>> children;

    /**
     * Creates a new reduced node.
     *
     * @param signature   reduction signature
     * @param localCounts local counts
     * @param children    children
     */
    public ReducedNode(final ReductionSignature<V> signature, final Map<V, Integer> localCounts,
            final Map<Character, ReducedNode<V>> children) {
        this.signature = signature;
        this.localCounts = new LinkedHashMap<>(localCounts);
        this.children = new LinkedHashMap<>(children);
    }

    /**
     * Returns the reduction signature of this canonical node.
     *
     * @return reduction signature
     */
    public ReductionSignature<V> signature() {
        return this.signature;
    }

    /**
     * Returns the internal aggregated local value-count map.
     *
     * <p>
     * The returned map is the internal backing state of this canonical reduced node
     * and is exposed only for efficient cooperation with trie-reduction
     * infrastructure.
     *
     * @return internal aggregated local value-count map
     */
    public Map<V, Integer> localCounts() {
        return this.localCounts;
    }

    /**
     * Returns the internal canonical child map indexed by transition character.
     *
     * <p>
     * The returned map is the internal backing state of this canonical reduced node
     * and is exposed only for efficient cooperation with trie-reduction
     * infrastructure.
     *
     * @return internal canonical child map
     */
    public Map<Character, ReducedNode<V>> children() {
        return this.children;
    }

    /**
     * Merges additional local counts into this node.
     *
     * @param additionalCounts additional local counts
     */
    public void mergeLocalCounts(final Map<V, Integer> additionalCounts) {
        for (Map.Entry<V, Integer> entry : additionalCounts.entrySet()) {
            final Integer previous = this.localCounts.get(entry.getKey());
            if (previous == null) {
                this.localCounts.put(entry.getKey(), entry.getValue());
            } else {
                this.localCounts.put(entry.getKey(), previous + entry.getValue());
            }
        }
    }

    /**
     * Merges child references into this node.
     *
     * <p>
     * For nodes with the same reduction signature, child edge sets and child
     * signatures must be compatible. This method therefore only needs to verify
     * consistency and store the canonical child instance.
     *
     * @param additionalChildren additional children
     */
    public void mergeChildren(final Map<Character, ReducedNode<V>> additionalChildren) {
        for (Map.Entry<Character, ReducedNode<V>> entry : additionalChildren.entrySet()) {
            final ReducedNode<V> existing = this.children.get(entry.getKey());
            if (existing == null) {
                this.children.put(entry.getKey(), entry.getValue());
            } else if (existing != entry.getValue()) { // NOPMD - we have canonical instances
                throw new IllegalStateException("Incompatible canonical child encountered during reduction.");
            }
        }
    }
}
