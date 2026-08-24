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

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.egothor.stemmer.trie.CompiledNode;

/**
 * Factory utilities related to {@link FrequencyTrie.Builder}.
 *
 * <p>
 * This helper reconstructs writable builders from compiled read-only tries. The
 * reconstruction preserves the semantics and local counts of the compiled trie
 * as currently stored, which makes it suitable for subsequent modifications
 * followed by recompilation.
 *
 * <p>
 * Reconstruction operates on the compiled form. Therefore, if the compiled trie
 * was produced using a reduction mode that merged semantically equivalent
 * subtrees, the recreated builder reflects that reduced compiled state rather
 * than the exact original unreduced insertion history.
 */
public final class FrequencyTrieBuilders {

    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(FrequencyTrieBuilders.class.getName());

    /**
     * Utility class.
     */
    private FrequencyTrieBuilders() {
        throw new AssertionError("No instances.");
    }

    /**
     * Reconstructs a new writable builder from a compiled read-only trie.
     *
     * <p>
     * The returned builder contains the same key-local value counts as the supplied
     * compiled trie. Callers may continue modifying the returned builder and then
     * compile a new {@link FrequencyTrie} instance.
     *
     * @param source            source compiled trie
     * @param arrayFactory      array factory for the reconstructed builder
     * @param reductionSettings reduction settings to associate with the new builder
     * @param <V>               value type
     * @return reconstructed writable builder
     * @throws NullPointerException if any argument is {@code null}
     */
    public static <V> FrequencyTrie.Builder<V> copyOf(final FrequencyTrie<V> source,
            final IntFunction<V[]> arrayFactory, final ReductionSettings reductionSettings) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(arrayFactory, "arrayFactory");
        Objects.requireNonNull(reductionSettings, "reductionSettings");

        final FrequencyTrie.Builder<V> builder = new FrequencyTrie.Builder<>(arrayFactory, reductionSettings,
                source.traversalDirection(), source.metadata().caseProcessingMode(),
                source.metadata().diacriticProcessingMode());
        final StringBuilder keyBuilder = new StringBuilder(64);

        copyNode(source.root(), keyBuilder, builder, source.traversalDirection());

        LOGGER.log(Level.FINE, "Reconstructed writable builder from compiled trie.");
        return builder;
    }

    /**
     * Reconstructs a new writable builder from a compiled read-only trie using
     * default settings for the supplied reduction mode.
     *
     * @param source        source compiled trie
     * @param arrayFactory  array factory for the reconstructed builder
     * @param reductionMode reduction mode to associate with the new builder
     * @param <V>           value type
     * @return reconstructed writable builder
     * @throws NullPointerException if any argument is {@code null}
     */
    public static <V> FrequencyTrie.Builder<V> copyOf(final FrequencyTrie<V> source,
            final IntFunction<V[]> arrayFactory, final ReductionMode reductionMode) {
        Objects.requireNonNull(reductionMode, "reductionMode");
        return copyOf(source, arrayFactory, ReductionSettings.withDefaults(reductionMode));
    }

    /**
     * Reconstructs a compiled trie with every stored value transformed to another
     * value type.
     *
     * <p>
     * The method preserves logical keys, local value counts, trie metadata, and the
     * supplied reduction settings. It is intended for runtime specialization, such
     * as replacing serialized patch-command strings with precompiled patch command
     * objects without changing the persisted binary trie format.
     * </p>
     *
     * @param source            source compiled trie
     * @param arrayFactory      array factory for mapped values
     * @param reductionSettings reduction settings for the mapped trie
     * @param valueMapper       value mapping function
     * @param <S>               source value type
     * @param <T>               target value type
     * @return compiled trie containing mapped values
     * @throws NullPointerException if any argument is {@code null}
     */
    public static <S, T> FrequencyTrie<T> mapValues(final FrequencyTrie<S> source,
            final IntFunction<T[]> arrayFactory, final ReductionSettings reductionSettings,
            final Function<? super S, ? extends T> valueMapper) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(arrayFactory, "arrayFactory");
        Objects.requireNonNull(reductionSettings, "reductionSettings");
        Objects.requireNonNull(valueMapper, "valueMapper");

        final Map<CompiledNode<S>, CompiledNode<T>> cache = new IdentityHashMap<>();
        final CompiledNode<T> mappedRoot = mapCompiledNode(source.root(), arrayFactory, valueMapper, cache);
        final TrieMetadata metadata = TrieMetadata.forCompilation(source.traversalDirection(), reductionSettings,
                source.metadata().diacriticProcessingMode(), source.metadata().caseProcessingMode());

        LOGGER.log(Level.FINE, "Mapped compiled trie values to a specialized value type.");
        return FrequencyTrie.fromCompiled(arrayFactory, mappedRoot, metadata);
    }

    /**
     * Reconstructs a compiled trie with every stored value transformed to another
     * value type using default settings for the supplied reduction mode.
     *
     * @param source        source compiled trie
     * @param arrayFactory  array factory for mapped values
     * @param reductionMode reduction mode for the mapped trie
     * @param valueMapper   value mapping function
     * @param <S>           source value type
     * @param <T>           target value type
     * @return compiled trie containing mapped values
     * @throws NullPointerException if any argument is {@code null}
     */
    public static <S, T> FrequencyTrie<T> mapValues(final FrequencyTrie<S> source,
            final IntFunction<T[]> arrayFactory, final ReductionMode reductionMode,
            final Function<? super S, ? extends T> valueMapper) {
        Objects.requireNonNull(reductionMode, "reductionMode");
        return mapValues(source, arrayFactory, ReductionSettings.withDefaults(reductionMode), valueMapper);
    }

    /**
     * Computes structural statistics for one compiled trie.
     *
     * <p>
     * Each unique node instance is visited exactly once. Shared nodes — nodes
     * reachable via multiple paths after subtree merging — contribute at the depth
     * at which they are first encountered during depth-first traversal.
     * </p>
     *
     * @param trie source compiled trie
     * @return structural statistics
     * @throws NullPointerException if {@code trie} is {@code null}
     */
    public static TrieStatistics computeStatistics(final FrequencyTrie<?> trie) {
        Objects.requireNonNull(trie, "trie");
        final IdentityHashMap<CompiledNode<?>, Boolean> visited = new IdentityHashMap<>();
        final long[] counters = { 0L, 0L, 0L, 0L }; // internal, leaf, longestPath, leafDepthSum
        collectStats(trie.root(), 0, visited, counters);
        final double avgLeafDepth = counters[1] == 0L ? 0.0d : (double) counters[3] / (double) counters[1];
        return new TrieStatistics(counters[0], counters[1], counters[2], avgLeafDepth);
    }

    /**
     * Recursive depth-first helper that accumulates trie structure counters.
     *
     * @param node     current node
     * @param depth    current depth (number of edges from root)
     * @param visited  identity set of already-visited nodes
     * @param counters shared counter array: [internal, leaf, longestPath, leafDepthSum]
     */
    private static void collectStats(final CompiledNode<?> node, final int depth,
            final IdentityHashMap<CompiledNode<?>, Boolean> visited, final long[] counters) {
        if (visited.put(node, Boolean.TRUE) != null) {
            return;
        }
        if (node.edgeLabels().length == 0) {
            counters[1]++;
            if (depth > counters[2]) {
                counters[2] = depth;
            }
            counters[3] += depth;
        } else {
            counters[0]++;
            for (final CompiledNode<?> child : node.children()) {
                collectStats(child, depth + 1, visited, counters);
            }
        }
    }

    /**
     * Copies one compiled node and all reachable descendants into the target
     * builder.
     *
     * @param node               current compiled node
     * @param keyBuilder         current key builder
     * @param builder            target mutable builder
     * @param traversalDirection logical key traversal direction used by the source
     * @param <V>                value type
     */
    private static <V> void copyNode(final CompiledNode<V> node, final StringBuilder keyBuilder,
            final FrequencyTrie.Builder<V> builder, final WordTraversalDirection traversalDirection) {
        final String logicalKey = traversalDirection.traversalPathToLogicalKey(keyBuilder);
        for (int valueIndex = 0; valueIndex < node.orderedValues().length; valueIndex++) {
            builder.put(logicalKey, node.orderedValues()[valueIndex], node.orderedCounts()[valueIndex]);
        }

        for (int childIndex = 0; childIndex < node.edgeLabels().length; childIndex++) {
            keyBuilder.append(node.edgeLabels()[childIndex]);
            copyNode(node.children()[childIndex], keyBuilder, builder, traversalDirection);
            keyBuilder.setLength(keyBuilder.length() - 1);
        }
    }

    /**
     * Maps one compiled node graph while preserving canonical sharing and accepting
     * leaf semantics.
     *
     * @param node         source node
     * @param arrayFactory target value array factory
     * @param valueMapper  value mapper
     * @param cache        identity cache for shared compiled nodes
     * @param <S>          source value type
     * @param <T>          target value type
     * @return mapped compiled node
     */
    private static <S, T> CompiledNode<T> mapCompiledNode(final CompiledNode<S> node,
            final IntFunction<T[]> arrayFactory, final Function<? super S, ? extends T> valueMapper,
            final Map<CompiledNode<S>, CompiledNode<T>> cache) {
        final CompiledNode<T> existing = cache.get(node);
        if (existing != null) {
            return existing;
        }

        final CompiledNode<S>[] sourceChildren = node.children();
        @SuppressWarnings("unchecked")
        final CompiledNode<T>[] mappedChildren = new CompiledNode[sourceChildren.length];
        for (int childIndex = 0; childIndex < sourceChildren.length; childIndex++) {
            mappedChildren[childIndex] = mapCompiledNode(sourceChildren[childIndex], arrayFactory, valueMapper, cache);
        }

        final S[] sourceValues = node.orderedValues();
        final T[] mappedValues = arrayFactory.apply(sourceValues.length);
        for (int valueIndex = 0; valueIndex < sourceValues.length; valueIndex++) {
            mappedValues[valueIndex] = valueMapper.apply(sourceValues[valueIndex]);
        }

        final CompiledNode<T> mapped = new CompiledNode<>(node.edgeLabels().clone(), mappedChildren, mappedValues,
                node.acceptsRemainingInput(), CompiledNode.DEFAULT_MAX_EXPANDED_INDEX, node.orderedCounts().clone());
        cache.put(node, mapped);
        return mapped;
    }
}
