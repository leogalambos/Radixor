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
 * Mutable build-time node.
 *
 * <p>
 * The maps exposed by the accessors are the internal mutable backing state of
 * the node. They are returned directly for efficiency and are intended only for
 * closely related trie-building infrastructure.
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
     * Creates an empty node.
     */
    public MutableNode() {
        this.children = new LinkedHashMap<>();
        this.valueCounts = new LinkedHashMap<>();
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
