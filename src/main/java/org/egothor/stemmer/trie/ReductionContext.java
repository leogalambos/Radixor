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

import org.egothor.stemmer.ReductionSettings;

/**
 * Reduction context used while canonicalizing mutable nodes.
 *
 * @param <V> value type
 */
public final class ReductionContext<V> {

    /**
     * Reduction settings.
     */
    private final ReductionSettings settings;

    /**
     * Canonical nodes by signature.
     */
    private final Map<ReductionSignature<V>, ReducedNode<V>> canonicalNodes;

    /**
     * Creates a new context.
     *
     * @param settings settings
     */
    public ReductionContext(final ReductionSettings settings) {
        this.settings = settings;
        this.canonicalNodes = new LinkedHashMap<>();
    }

    /**
     * Looks up a canonical node.
     *
     * @param signature signature
     * @return canonical node, or {@code null} if absent
     */
    public ReducedNode<V> lookup(final ReductionSignature<V> signature) {
        return this.canonicalNodes.get(signature);
    }

    /**
     * Registers a canonical node.
     *
     * @param signature signature
     * @param node      node
     */
    public void register(final ReductionSignature<V> signature, final ReducedNode<V> node) {
        this.canonicalNodes.put(signature, node);
    }

    /**
     * Returns the settings.
     *
     * @return settings
     */
    public ReductionSettings settings() {
        return this.settings;
    }

    /**
     * Returns the number of canonical nodes.
     *
     * @return canonical node count
     */
    public int canonicalNodeCount() {
        return this.canonicalNodes.size();
    }
}
