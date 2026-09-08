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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable build-time node.
 *
 * <p>
 * The maps exposed by the accessors are the internal mutable backing state of
 * the node. They are returned directly for efficiency and are intended only for
 * closely related trie-building infrastructure.
 * </p>
 *
 * <p>
 * Instances are mutable and not thread-safe. The owning builder is responsible
 * for confinement and for ensuring that the exposed maps are not retained after
 * compilation.
 * </p>
 *
 * @param <V> value type
 */
public final class MutableNode<V> {

    /**
     * Child nodes indexed by transition character.
     */
    private final Map<Character, MutableNode<V>> children;

    /**
     * Local terminal value counts stored exactly at this node.
     */
    private final Map<V, Integer> valueCounts;

    /**
     * Whether this node was a contracted accepting leaf in a source compiled
     * trie. Set only when a builder is reconstructed from a compiled trie (see
     * {@code FrequencyTrieBuilders.copyOf}); it is preserved through reduction so
     * the "accepts remaining input" generalization survives a round-trip even
     * when the original member paths were contracted away and cannot be replayed.
     */
    private boolean acceptsRemainingInput;

    /**
     * Creates an empty node.
     */
    public MutableNode() {
        this.children = new LinkedHashMap<>();
        this.valueCounts = new LinkedHashMap<>();
    }

    /**
     * Returns whether this node is marked as accepting remaining input.
     *
     * @return {@code true} when this node accepts any remaining lookup input
     */
    public boolean acceptsRemainingInput() {
        return this.acceptsRemainingInput;
    }

    /**
     * Marks this node as accepting remaining input.
     */
    public void markAcceptsRemainingInput() {
        this.acceptsRemainingInput = true;
    }

    /**
     * Clears the accepting-remaining-input marker.
     *
     * <p>
     * This transition is required when the last local value is removed: an
     * accepting node without a value cannot resolve a lookup and is rejected by
     * the compiled-node invariant.
     * </p>
     */
    public void clearAcceptsRemainingInput() {
        this.acceptsRemainingInput = false;
    }

    /**
     * Returns the internal child-node map indexed by transition character.
     *
     * <p>
     * The returned map is the internal mutable backing state of this node and is
     * exposed only for efficient cooperation with trie-building infrastructure.
     *
     * @return internal child-node map
     */
    public Map<Character, MutableNode<V>> children() {
        return this.children;
    }

    /**
     * Returns the internal local terminal value-count map.
     *
     * <p>
     * The returned map is the internal mutable backing state of this node and is
     * exposed only for efficient cooperation with trie-building infrastructure.
     *
     * @return internal local value-count map
     */
    public Map<V, Integer> valueCounts() {
        return this.valueCounts;
    }

}
