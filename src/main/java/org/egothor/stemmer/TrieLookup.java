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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.egothor.stemmer.trie.CompiledNode;

/**
 * Command-selection traversals for {@link LookupMode#LAST} and
 * {@link LookupMode#ALL}.
 *
 * <p>
 * The {@link LookupMode#FIRST} walk lives on the read hot path inside
 * {@link FrequencyTrie}. This collaborator hosts the less common most-specific
 * ({@code LAST}) and collect-all ({@code ALL}) traversals so the trie class
 * stays cohesive, together with the small value-assembly helpers those modes
 * need. All methods are stateless and operate on the shared, immutable compiled
 * node graph.
 * </p>
 */
final class TrieLookup {

    /**
     * Prevents instantiation of this stateless utility class.
     *
     * @throws AssertionError unconditionally, including reflective construction
     */
    private TrieLookup() {
        throw new AssertionError("No instances.");
    }

    /**
     * Locates the deepest (most specific) node for {@code key}, using the deepest
     * accepting ancestor as a fallback when descent dead-ends or the exact
     * terminal stores no value.
     *
     * @param root     compiled root node
     * @param backward whether traversal consumes the key from its end
     * @param key      already-normalized key
     * @param <V>      value type
     * @return resolved node, or {@code null} if no applicable node exists
     */
    /* default */ static <V> CompiledNode<V> findLast(final CompiledNode<V> root, final boolean backward,
            final CharSequence key) {
        CompiledNode<V> current = root;
        CompiledNode<V> fallback = current.acceptsRemainingInput() ? current : null;
        final int length = key.length();
        for (int step = 0; step < length; step++) {
            final int index = backward ? length - 1 - step : step;
            final CompiledNode<V> next = current.findChild(key.charAt(index));
            if (next == null) {
                return fallback;
            }
            current = next;
            if (current.acceptsRemainingInput()) {
                fallback = current;
            }
        }
        if (current.orderedValues().length > 0) {
            return current;
        }
        return fallback;
    }

    /**
     * Array-slice specialization of
     * {@link #findLast(CompiledNode, boolean, CharSequence)} that avoids a
     * temporary wrapper allocation on normalized visitor hot paths.
     *
     * @param root      compiled root node
     * @param backward  whether traversal consumes the slice from its end
     * @param key       normalized key storage
     * @param offset    first character in the slice
     * @param length    number of characters in the slice
     * @param <V>       value type
     * @return resolved node, or {@code null} if no applicable node exists
     */
    /* default */ static <V> CompiledNode<V> findLast(final CompiledNode<V> root, final boolean backward,
            final char[] key, final int offset, final int length) {
        CompiledNode<V> current = root;
        CompiledNode<V> fallback = current.acceptsRemainingInput() ? current : null;
        for (int step = 0; step < length; step++) {
            final int index = backward ? offset + length - 1 - step : offset + step;
            final CompiledNode<V> next = current.findChild(key[index]);
            if (next == null) {
                return fallback;
            }
            current = next;
            if (current.acceptsRemainingInput()) {
                fallback = current;
            }
        }
        return current.orderedValues().length > 0 ? current : fallback;
    }

    /**
     * Collects every node whose values apply to {@code key}, most specific first:
     * the exact terminal (when the key is fully consumed) followed by each
     * accepting ancestor from deepest to shallowest.
     *
     * @param root     compiled root node
     * @param backward whether traversal consumes the key from its end
     * @param key      already-normalized key
     * @param <V>      value type
     * @return ordered applicable nodes; empty when none apply
     */
    /* default */ static <V> List<CompiledNode<V>> collectPath(final CompiledNode<V> root,
            final boolean backward, final CharSequence key) {
        final List<CompiledNode<V>> accepting = new ArrayList<>();
        CompiledNode<V> current = root;
        if (current.acceptsRemainingInput()) {
            accepting.add(current);
        }
        boolean fullyConsumed = true;
        final int length = key.length();
        for (int step = 0; step < length; step++) {
            final int index = backward ? length - 1 - step : step;
            final CompiledNode<V> next = current.findChild(key.charAt(index));
            if (next == null) {
                fullyConsumed = false;
                break;
            }
            current = next;
            if (current.acceptsRemainingInput()) {
                accepting.add(current);
            }
        }

        final List<CompiledNode<V>> ordered = new ArrayList<>(accepting.size() + 1);
        // A fully-consumed terminal that itself accepts is already the deepest
        // entry in `accepting`, so add the terminal only when it does not accept.
        if (fullyConsumed && !current.acceptsRemainingInput() && current.orderedValues().length > 0) {
            ordered.add(current);
        }
        for (int index = accepting.size() - 1; index >= 0; index--) {
            ordered.add(accepting.get(index));
        }
        return ordered;
    }

    /**
     * Collects all applicable values across {@code nodes}, de-duplicated by
     * {@link Object#equals(Object)}, in list order (most specific first).
     *
     * @param nodes ordered applicable nodes
     * @param empty shared empty array carrying the target component type
     * @param <V>   value type
     * @return de-duplicated values, or {@code empty} when none apply
     */
    @SuppressWarnings("PMD.UseVarargs")
    /* default */ static <V> V[] collectAllValues(final List<CompiledNode<V>> nodes, final V[] empty) {
        if (nodes.isEmpty()) {
            return empty;
        }
        final List<V> collected = new ArrayList<>();
        final Set<V> seen = new HashSet<>();
        for (final CompiledNode<V> node : nodes) {
            for (final V value : node.orderedValues()) {
                if (seen.add(value)) {
                    collected.add(value);
                }
            }
        }
        return collected.isEmpty() ? empty : collected.toArray(empty);
    }

    /**
     * Collects all applicable value-count entries across {@code nodes},
     * de-duplicated by value, in list order (most specific first).
     *
     * @param nodes ordered applicable nodes
     * @param <V>   value type
     * @return immutable de-duplicated entries
     */
    /* default */ static <V> List<ValueCount<V>> collectAllEntries(final List<CompiledNode<V>> nodes) {
        final List<ValueCount<V>> entries = new ArrayList<>();
        final Set<V> seen = new HashSet<>();
        for (final CompiledNode<V> node : nodes) {
            final V[] values = node.orderedValues();
            final int[] counts = node.orderedCounts();
            for (int index = 0; index < values.length; index++) {
                if (seen.add(values[index])) {
                    entries.add(new ValueCount<>(values[index], counts[index]));
                }
            }
        }
        return entries.isEmpty() ? List.of() : Collections.unmodifiableList(entries);
    }

    /**
     * Visits applicable values across {@code nodes}, de-duplicated by value, most
     * specific first, up to {@code maxResults}. Mirrors the
     * {@link FrequencyTrie.EntrySink} contract with the visit index as the rank.
     *
     * @param nodes      ordered applicable nodes
     * @param sink       value sink
     * @param maxResults maximum values to visit
     * @param <V>        value type
     * @return number of visited values
     */
    /* default */ static <V> int visitNodes(final List<CompiledNode<V>> nodes,
            final FrequencyTrie.EntrySink<? super V> sink, final int maxResults) {
        int visited = 0;
        final Set<V> seen = new HashSet<>();
        for (final CompiledNode<V> node : nodes) {
            final V[] values = node.orderedValues();
            final int[] counts = node.orderedCounts();
            for (int index = 0; index < values.length; index++) {
                if (visited >= maxResults) {
                    return visited;
                }
                if (!seen.add(values[index])) {
                    continue;
                }
                visited++;
                if (!sink.accept(values[index], counts[index], visited - 1)) {
                    return visited;
                }
            }
        }
        return visited;
    }
}
