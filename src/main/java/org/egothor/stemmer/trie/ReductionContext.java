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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.egothor.stemmer.ReductionSettings;

/**
 * Mutable state confined to one bottom-up trie-reduction pass.
 *
 * <p>
 * The context owns the canonical-node table for the configured semantic
 * reduction mode. It also tracks provenance when a compiled DAG has been expanded
 * into mutable logical paths, ensuring that already-aggregated counts are not
 * multiplied during recompilation.
 * </p>
 *
 * <p>
 * Instances are not thread-safe and must not be reused across concurrent or
 * sequential builder compilations.
 * </p>
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
     * Source compiled-node identities already represented by each canonical node.
     */
    private final Map<ReducedNode<V>, Set<Object>> contributedCompiledSources;

    /**
     * Creates an empty reduction context for one compilation.
     *
     * @param settings immutable reduction settings governing canonical equality
     * @throws NullPointerException if {@code settings} is {@code null}
     */
    public ReductionContext(final ReductionSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.canonicalNodes = new LinkedHashMap<>();
        this.contributedCompiledSources = new IdentityHashMap<>();
    }

    /**
     * Looks up the canonical node previously registered for {@code signature}.
     *
     * @param signature semantic subtree signature
     * @return canonical node, or {@code null} if absent
     * @throws NullPointerException if {@code signature} is {@code null}
     */
    public ReducedNode<V> lookup(final ReductionSignature<V> signature) {
        return this.canonicalNodes.get(Objects.requireNonNull(signature, "signature"));
    }

    /**
     * Registers a canonical node for {@code signature}, replacing any previous
     * association. Normal bottom-up reduction registers each signature once; the
     * replacement behavior keeps this context usable by controlled reconstruction
     * and test infrastructure.
     *
     * @param signature semantic subtree signature
     * @param node      canonical reduced node
     * @throws NullPointerException if either argument is {@code null}
     */
    public void register(final ReductionSignature<V> signature, final ReducedNode<V> node) {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(node, "node");
        this.canonicalNodes.put(signature, node);
    }

    /**
     * Records that one canonical node now includes the counts of a source compiled
     * DAG node.
     *
     * <p>
     * Reconstruction expands a shared compiled node at every logical path. Its
     * counts are already aggregated, so only the first expanded occurrence merged
     * into a given canonical node may contribute them again. Both map levels use
     * identity semantics because compiled and reduced nodes are graph vertices,
     * not value objects.
     * </p>
     *
     * @param canonical     canonical node receiving the contribution
     * @param sourceIdentity identity of the source compiled node
     * @return {@code true} if this is the first contribution of that source to the
     *         canonical node; {@code false} if its counts are already represented
     * @throws NullPointerException if either argument is {@code null}
     */
    public boolean recordCompiledSourceContribution(final ReducedNode<V> canonical, final Object sourceIdentity) {
        Objects.requireNonNull(canonical, "canonical");
        Objects.requireNonNull(sourceIdentity, "sourceIdentity");
        final Set<Object> sources = this.contributedCompiledSources.computeIfAbsent(canonical,
                ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
        return sources.add(sourceIdentity);
    }

    /**
     * Returns the immutable settings governing this reduction pass.
     *
     * @return non-null reduction settings
     */
    public ReductionSettings settings() {
        return this.settings;
    }

    /**
     * Returns the number of distinct semantic subtree signatures registered so
     * far.
     *
     * @return canonical node count
     */
    public int canonicalNodeCount() {
        return this.canonicalNodes.size();
    }
}
