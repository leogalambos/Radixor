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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.egothor.stemmer.ReductionSettings;

/**
 * Immutable reduction signature of a full subtree.
 *
 * @param <V> value type
 */
public final class ReductionSignature<V> {

    /**
     * Local semantic descriptor.
     */
    private final Object localDescriptor;

    /**
     * Child edge descriptors in sorted edge order.
     */
    private final List<ChildDescriptor<V>> childDescriptors;

    /**
     * Creates a signature.
     *
     * @param localDescriptor  local descriptor
     * @param childDescriptors child descriptors
     */
    private ReductionSignature(final Object localDescriptor, final List<ChildDescriptor<V>> childDescriptors) {
        this.localDescriptor = localDescriptor;
        this.childDescriptors = childDescriptors;
    }

    /**
     * Creates a subtree signature according to the selected reduction mode.
     *
     * @param localSummary local value summary
     * @param children     reduced children
     * @param settings     reduction settings
     * @param <V>          value type
     * @return subtree signature
     */
    public static <V> ReductionSignature<V> create(final LocalValueSummary<V> localSummary,
            final Map<Character, ReducedNode<V>> children, final ReductionSettings settings) {
        final Object localDescriptor = switch (settings.reductionMode()) {
            case MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS ->
                RankedLocalDescriptor.of(localSummary.orderedValues());
            case MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS ->
                UnorderedLocalDescriptor.of(localSummary.orderedValues());
            case MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS -> {
                if (localSummary.hasQualifiedDominantWinner(settings)) {
                    yield new DominantLocalDescriptor<>(localSummary.dominantValue);
                } else {
                    yield RankedLocalDescriptor.of(localSummary.orderedValues());
                }
            }
        };

        final List<Map.Entry<Character, ReducedNode<V>>> entries = new ArrayList<>(children.entrySet());
        entries.sort(Map.Entry.comparingByKey());

        final List<ChildDescriptor<V>> childDescriptors = new ArrayList<>(entries.size());

        for (Map.Entry<Character, ReducedNode<V>> entry : entries) {
            childDescriptors.add(new ChildDescriptor<>(entry.getKey(), entry.getValue().signature()));
        }

        return new ReductionSignature<>(localDescriptor, Collections.unmodifiableList(childDescriptors));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.localDescriptor, this.childDescriptors);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReductionSignature<?>)) {
            return false;
        }
        final ReductionSignature<?> that = (ReductionSignature<?>) other;
        return Objects.equals(this.localDescriptor, that.localDescriptor)
                && Objects.equals(this.childDescriptors, that.childDescriptors);
    }
}
