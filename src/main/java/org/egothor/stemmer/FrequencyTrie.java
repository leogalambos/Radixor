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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.egothor.stemmer.trie.CompiledNode;
import org.egothor.stemmer.trie.LocalValueSummary;
import org.egothor.stemmer.trie.MutableNode;
import org.egothor.stemmer.trie.NodeData;
import org.egothor.stemmer.trie.ReducedNode;
import org.egothor.stemmer.trie.ReductionContext;
import org.egothor.stemmer.trie.ReductionSignature;

/**
 * Read-only trie mapping {@link String} keys to one or more values with
 * frequency tracking.
 *
 * <p>
 * A key may be associated with multiple values. Each value keeps the number of
 * times it was inserted during the build phase. The method {@link #get(String)}
 * returns the locally most frequent value stored at the terminal node of the
 * supplied key, while {@link #getAll(String)} returns all locally stored values
 * ordered by descending frequency.
 *
 * <p>
 * If multiple values have the same local frequency, their ordering is
 * deterministic. The preferred value is selected by the following tie-breaking
 * rules, in order:
 * <ol>
 * <li>shorter {@link String} representation wins, based on
 * {@code value.toString()}</li>
 * <li>if the lengths are equal, lexicographically lower {@link String}
 * representation wins</li>
 * <li>if the textual representations are still equal, first-seen insertion
 * order remains stable</li>
 * </ol>
 *
 * <p>
 * Values may be stored at any trie node, including internal nodes and leaf
 * nodes. Therefore, reduction and canonicalization always operate on both the
 * node-local terminal values and the structure of all descendant edges.
 *
 * @param <V> value type
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class FrequencyTrie<V> {

    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(FrequencyTrie.class.getName());

    /**
     * Factory used to create correctly typed arrays for {@link #getAll(String)}.
     */
    private final IntFunction<V[]> arrayFactory;

    /**
     * Root node of the compiled read-only trie.
     */
    private final CompiledNode<V> root;

    /**
     * Metadata persisted together with this trie.
     */
    private final TrieMetadata metadata;

    /**
     * Binary format magic header.
     */
    private static final int STREAM_MAGIC = 0x45475452;

    /**
     * Binary format version.
     */
    private static final int STREAM_VERSION = 5;

    /**
     * Returns the current persisted binary stream format version.
     *
     * <p>
     * This method exists so other components can construct {@link TrieMetadata}
     * instances aligned with the currently written binary format without
     * duplicating constants.
     * </p>
     *
     * @return current trie stream format version
     */
    public static int currentFormatVersion() {
        return STREAM_VERSION;
    }

    /**
     * Creates a new compiled trie instance.
     *
     * @param arrayFactory       array factory
     * @param root               compiled root node
     * @param traversalDirection logical key traversal direction
     * @throws NullPointerException if any argument is {@code null}
     */
    private FrequencyTrie(final IntFunction<V[]> arrayFactory, final CompiledNode<V> root,
            final TrieMetadata metadata) {
        this.arrayFactory = Objects.requireNonNull(arrayFactory, "arrayFactory");
        this.root = Objects.requireNonNull(root, "root");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    /**
     * Returns the most frequent value stored at the node addressed by the supplied
     * key.
     *
     * <p>
     * If multiple values have the same local frequency, the returned value is
     * selected deterministically by shorter {@code toString()} value first, then by
     * lexicographically lower {@code toString()}, and finally by stable first-seen
     * order.
     *
     * <p>
     * The supplied key is normalized according to persisted
     * {@link TrieMetadata#caseProcessingMode()} before traversal.
     * 
     * @param key key to resolve
     * @return most frequent value, or {@code null} if the key does not exist or no
     *         value is stored at the addressed node
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public V get(final String key) {
        Objects.requireNonNull(key, "key");
        final CompiledNode<V> node = findNode(normalizeLookupKey(key));
        if (node == null || node.orderedValues().length == 0) {
            return null;
        }
        return node.orderedValues()[0];
    }

    /**
     * Returns all values stored at the node addressed by the supplied key, ordered
     * by descending frequency.
     *
     * <p>
     * If multiple values have the same local frequency, the ordering is
     * deterministic by shorter {@code toString()} value first, then by
     * lexicographically lower {@code toString()}, and finally by stable first-seen
     * order.
     *
     * <p>
     * The returned array is a defensive copy.
     *
     * <p>
     * The supplied key is normalized according to persisted
     * {@link TrieMetadata#caseProcessingMode()} before traversal.
     *
     * @param key key to resolve
     * @return all values stored at the addressed node, ordered by descending
     *         frequency; returns an empty array if the key does not exist or no
     *         value is stored at the addressed node
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public V[] getAll(final String key) {
        Objects.requireNonNull(key, "key");
        final CompiledNode<V> node = findNode(normalizeLookupKey(key));
        if (node == null || node.orderedValues().length == 0) {
            return this.arrayFactory.apply(0);
        }
        return Arrays.copyOf(node.orderedValues(), node.orderedValues().length);
    }

    /**
     * Returns all values stored at the node addressed by the supplied key together
     * with their occurrence counts, ordered by the same rules as
     * {@link #getAll(String)}.
     *
     * <p>
     * The returned list is aligned with the arrays returned by
     * {@link #getAll(String)} and the internal compiled count representation.
     *
     * <p>
     * The returned list is immutable.
     *
     * <p>
     * In reduction modes that merge semantically equivalent subtrees, the returned
     * counts may be aggregated across multiple original build-time nodes that were
     * reduced into the same canonical compiled node.
     *
     * @param key key to resolve
     * @return immutable ordered list of value-count entries; returns an empty list
     *         if the key does not exist or no value is stored at the addressed node
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public List<ValueCount<V>> getEntries(final String key) {
        Objects.requireNonNull(key, "key");
        final CompiledNode<V> node = findNode(normalizeLookupKey(key));
        if (node == null || node.orderedValues().length == 0) {
            return List.of();
        }

        final List<ValueCount<V>> entries = new ArrayList<>(node.orderedValues().length);
        for (int index = 0; index < node.orderedValues().length; index++) {
            entries.add(new ValueCount<>(node.orderedValues()[index], node.orderedCounts()[index]));
        }
        return Collections.unmodifiableList(entries);
    }

    /**
     * Returns the logical key traversal direction used by this trie.
     *
     * <p>
     * The same direction must be used when reconstructing mutable builders or when
     * applying patch commands that were generated against keys stored in this trie.
     * </p>
     *
     * @return logical key traversal direction
     */
    public WordTraversalDirection traversalDirection() {
        return this.metadata.traversalDirection();
    }

    /**
     * Returns immutable persisted metadata associated with this trie.
     *
     * @return trie metadata
     */
    public TrieMetadata metadata() {
        return this.metadata;
    }

    /**
     * Returns the root node mainly for diagnostics and tests within the package.
     *
     * @return compiled root node
     */
    /* default */ CompiledNode<V> root() {
        return this.root;
    }

    /**
     * Writes this compiled trie to the supplied output stream.
     *
     * <p>
     * The binary format is versioned and preserves canonical shared compiled nodes,
     * therefore the serialized representation remains compact even for tries
     * reduced by subtree merging.
     *
     * <p>
     * The supplied codec is responsible for persisting individual values of type
     * {@code V}.
     *
     * @param outputStream target output stream
     * @param valueCodec   codec used to write values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if writing fails
     */
    public void writeTo(final OutputStream outputStream, final ValueStreamCodec<V> valueCodec) throws IOException {
        Objects.requireNonNull(outputStream, "outputStream");
        Objects.requireNonNull(valueCodec, "valueCodec");

        final DataOutputStream dataOutput; // NOPMD
        if (outputStream instanceof DataOutputStream) {
            dataOutput = (DataOutputStream) outputStream;
        } else {
            dataOutput = new DataOutputStream(outputStream);
        }

        final Map<CompiledNode<V>, Integer> nodeIds = new IdentityHashMap<>();
        final List<CompiledNode<V>> orderedNodes = new ArrayList<>();
        assignNodeIds(this.root, nodeIds, orderedNodes);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Writing compiled trie with {0} canonical nodes.", orderedNodes.size());
        }

        dataOutput.writeInt(STREAM_MAGIC);
        dataOutput.writeInt(STREAM_VERSION);
        dataOutput.writeInt(orderedNodes.size());
        dataOutput.writeInt(nodeIds.get(this.root));
        writeMetadata(dataOutput, this.metadata);

        for (CompiledNode<V> node : orderedNodes) {
            writeNode(dataOutput, valueCodec, node, nodeIds);
        }

        dataOutput.flush();
    }

    /**
     * Reads a compiled trie from the supplied input stream.
     *
     * <p>
     * The caller must provide the same value codec semantics that were used during
     * persistence as well as the array factory required for typed result arrays.
     *
     * @param inputStream  source input stream
     * @param arrayFactory factory used to create typed arrays
     * @param valueCodec   codec used to read values
     * @param <V>          value type
     * @return deserialized compiled trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if reading fails or the binary format is invalid
     */
    public static <V> FrequencyTrie<V> readFrom(final InputStream inputStream, final IntFunction<V[]> arrayFactory,
            final ValueStreamCodec<V> valueCodec) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        Objects.requireNonNull(arrayFactory, "arrayFactory");
        Objects.requireNonNull(valueCodec, "valueCodec");

        final DataInputStream dataInput; // NOPMD
        if (inputStream instanceof DataInputStream) {
            dataInput = (DataInputStream) inputStream;
        } else {
            dataInput = new DataInputStream(inputStream);
        }

        final int magic = dataInput.readInt();
        if (magic != STREAM_MAGIC) {
            throw new IOException("Unsupported trie stream header: " + Integer.toHexString(magic));
        }

        final int version = dataInput.readInt();
        if (version != 1 && version != 3 && version != 4 && version != STREAM_VERSION) {
            throw new IOException("Unsupported trie stream version: " + version);
        }

        final int nodeCount = dataInput.readInt();
        if (nodeCount < 0) {
            throw new IOException("Negative node count: " + nodeCount);
        }

        final int rootNodeId = dataInput.readInt();
        if (rootNodeId < 0 || rootNodeId >= nodeCount) {
            throw new IOException("Invalid root node id: " + rootNodeId);
        }

        final TrieMetadata metadata = readMetadata(dataInput, version);

        final CompiledNode<V>[] nodes = readNodes(dataInput, arrayFactory, valueCodec, nodeCount);
        final CompiledNode<V> rootNode = nodes[rootNodeId];

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Read compiled trie with {0} canonical nodes.", nodeCount);
        }

        return new FrequencyTrie<>(arrayFactory, rootNode, metadata);
    }

    /**
     * Writes persisted trie metadata.
     *
     * @param dataOutput output stream
     * @param metadata   metadata to serialize
     * @throws IOException if writing fails
     */
    private static void writeMetadata(final DataOutputStream dataOutput, final TrieMetadata metadata)
            throws IOException {
        dataOutput.writeUTF(metadata.toTextBlock());
    }

    /**
     * Reads persisted trie metadata while remaining backward compatible with
     * earlier stream versions.
     *
     * @param dataInput input stream
     * @param version   persisted stream version
     * @return deserialized metadata
     * @throws IOException if the metadata section is invalid
     */
    private static TrieMetadata readMetadata(final DataInputStream dataInput, final int version) throws IOException {
        if (version >= 5) { // NOPMD
            try {
                return TrieMetadata.fromTextBlock(version, dataInput.readUTF());
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid metadata block.", exception);
            }
        }

        final WordTraversalDirection traversalDirection;
        if (version >= 2) { // NOPMD
            final int traversalDirectionOrdinal = dataInput.readInt();
            final WordTraversalDirection[] traversalDirections = WordTraversalDirection.values();
            if (traversalDirectionOrdinal < 0 || traversalDirectionOrdinal >= traversalDirections.length) {
                throw new IOException("Invalid traversal direction ordinal: " + traversalDirectionOrdinal);
            }
            traversalDirection = traversalDirections[traversalDirectionOrdinal];
        } else {
            traversalDirection = WordTraversalDirection.BACKWARD;
        }

        if (version < 3) { // NOPMD
            return TrieMetadata.legacy(version, traversalDirection);
        }

        final ReductionMode[] reductionModes = ReductionMode.values();
        final int reductionModeOrdinal = dataInput.readInt();
        if (reductionModeOrdinal < 0 || reductionModeOrdinal >= reductionModes.length) {
            throw new IOException("Invalid reduction mode ordinal: " + reductionModeOrdinal);
        }

        final int dominantWinnerMinPercent = dataInput.readInt();
        final int dominantWinnerOverSecondRatio = dataInput.readInt(); // NOPMD

        final DiacriticProcessingMode[] diacriticProcessingModes = DiacriticProcessingMode.values();
        final int diacriticProcessingModeOrdinal = dataInput.readInt(); // NOPMD
        if (diacriticProcessingModeOrdinal < 0 || diacriticProcessingModeOrdinal >= diacriticProcessingModes.length) {
            throw new IOException("Invalid diacritic processing mode ordinal: " + diacriticProcessingModeOrdinal);
        }

        final CaseProcessingMode caseProcessingMode;
        if (version >= 4) { // NOPMD
            final CaseProcessingMode[] caseProcessingModes = CaseProcessingMode.values();
            final int caseProcessingModeOrdinal = dataInput.readInt();
            if (caseProcessingModeOrdinal < 0 || caseProcessingModeOrdinal >= caseProcessingModes.length) {
                throw new IOException("Invalid case processing mode ordinal: " + caseProcessingModeOrdinal);
            }
            caseProcessingMode = caseProcessingModes[caseProcessingModeOrdinal];
        } else {
            caseProcessingMode = CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT;
        }

        return new TrieMetadata(version, traversalDirection,
                new ReductionSettings(reductionModes[reductionModeOrdinal], dominantWinnerMinPercent,
                        dominantWinnerOverSecondRatio),
                diacriticProcessingModes[diacriticProcessingModeOrdinal], caseProcessingMode);
    }

    /**
     * Returns the number of canonical compiled nodes reachable from the root.
     *
     * <p>
     * The returned value reflects the size of the final reduced immutable trie, not
     * the number of mutable build-time nodes inserted before reduction. Shared
     * canonical subtrees are counted only once.
     *
     * @return number of canonical compiled nodes in this trie
     */
    public int size() {
        final Map<CompiledNode<V>, Integer> nodeIds = new IdentityHashMap<>();
        final List<CompiledNode<V>> orderedNodes = new ArrayList<>();
        assignNodeIds(this.root, nodeIds, orderedNodes);
        return orderedNodes.size();
    }

    /**
     * Assigns deterministic identifiers to all canonical compiled nodes reachable
     * from the supplied root.
     *
     * @param node         current node
     * @param nodeIds      assigned node identifiers
     * @param orderedNodes ordered nodes in identifier order
     */
    private static <V> void assignNodeIds(final CompiledNode<V> node, final Map<CompiledNode<V>, Integer> nodeIds,
            final List<CompiledNode<V>> orderedNodes) {
        if (nodeIds.containsKey(node)) {
            return;
        }

        final int nodeId = orderedNodes.size();
        nodeIds.put(node, nodeId);
        orderedNodes.add(node);

        for (CompiledNode<V> child : node.children()) {
            assignNodeIds(child, nodeIds, orderedNodes);
        }
    }

    /**
     * Writes one compiled node.
     *
     * @param dataOutput output
     * @param valueCodec value codec
     * @param node       node to write
     * @param nodeIds    node identifiers
     * @throws IOException if writing fails
     */
    private static <V> void writeNode(final DataOutputStream dataOutput, final ValueStreamCodec<V> valueCodec,
            final CompiledNode<V> node, final Map<CompiledNode<V>, Integer> nodeIds) throws IOException {
        dataOutput.writeInt(node.edgeLabels().length);
        for (int index = 0; index < node.edgeLabels().length; index++) {
            dataOutput.writeChar(node.edgeLabels()[index]);
            final Integer childNodeId = nodeIds.get(node.children()[index]);
            if (childNodeId == null) {
                throw new IOException("Missing child node identifier during serialization.");
            }
            dataOutput.writeInt(childNodeId);
        }

        dataOutput.writeInt(node.orderedValues().length);
        for (int index = 0; index < node.orderedValues().length; index++) {
            valueCodec.write(dataOutput, node.orderedValues()[index]);
            dataOutput.writeInt(node.orderedCounts()[index]);
        }
    }

    /**
     * Reads all compiled nodes and resolves child references.
     *
     * @param dataInput    input
     * @param arrayFactory array factory
     * @param valueCodec   value codec
     * @param nodeCount    number of nodes
     * @param <V>          value type
     * @return array of nodes indexed by serialized node identifier
     * @throws IOException if reading fails or the stream is invalid
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static <V> CompiledNode<V>[] readNodes(final DataInputStream dataInput, final IntFunction<V[]> arrayFactory,
            final ValueStreamCodec<V> valueCodec, final int nodeCount) throws IOException {
        final List<NodeData<V>> nodeDataList = new ArrayList<>(nodeCount);

        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            final int edgeCount = dataInput.readInt();
            if (edgeCount < 0) {
                throw new IOException("Negative edge count at node " + nodeIndex + ": " + edgeCount);
            }

            final char[] edgeLabels = new char[edgeCount];
            final int[] childNodeIds = new int[edgeCount];

            for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                edgeLabels[edgeIndex] = dataInput.readChar();
                childNodeIds[edgeIndex] = dataInput.readInt();
            }

            validateSerializedEdges(nodeIndex, edgeLabels);

            final int valueCount = dataInput.readInt();
            if (valueCount < 0) {
                throw new IOException("Negative value count at node " + nodeIndex + ": " + valueCount);
            }

            final V[] orderedValues = arrayFactory.apply(valueCount);
            final int[] orderedCounts = new int[valueCount];

            for (int valueIndex = 0; valueIndex < valueCount; valueIndex++) {
                orderedValues[valueIndex] = valueCodec.read(dataInput);
                orderedCounts[valueIndex] = dataInput.readInt();
                if (orderedCounts[valueIndex] <= 0) {
                    throw new IOException("Non-positive stored count at node " + nodeIndex + ", value index "
                            + valueIndex + ": " + orderedCounts[valueIndex]);
                }
            }

            nodeDataList.add(new NodeData<>(edgeLabels, childNodeIds, orderedValues, orderedCounts));
        }

        @SuppressWarnings("unchecked")
        final CompiledNode<V>[] nodes = new CompiledNode[nodeCount];

        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            final NodeData<V> nodeData = nodeDataList.get(nodeIndex);
            @SuppressWarnings("unchecked")
            final CompiledNode<V>[] children = new CompiledNode[nodeData.childNodeIds().length];
            nodes[nodeIndex] = new CompiledNode<>(nodeData.edgeLabels(), children, nodeData.orderedValues(),
                    nodeData.orderedCounts());
        }

        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            final NodeData<V> nodeData = nodeDataList.get(nodeIndex);
            final CompiledNode<V> node = nodes[nodeIndex];

            for (int edgeIndex = 0; edgeIndex < nodeData.childNodeIds().length; edgeIndex++) {
                final int childNodeId = nodeData.childNodeIds()[edgeIndex];
                if (childNodeId < 0 || childNodeId >= nodeCount) {
                    throw new IOException("Invalid child node id at node " + nodeIndex + ", edge index " + edgeIndex
                            + ": " + childNodeId);
                }
                node.children()[edgeIndex] = nodes[childNodeId];
            }
        }

        return nodes;
    }

    /**
     * Validates the serialized edge-label sequence for one node.
     *
     * <p>
     * Compiled nodes rely on binary search for child lookup and therefore require
     * edge labels to be stored in strict ascending order without duplicates.
     * Rejecting malformed streams here keeps lookup semantics deterministic and
     * avoids silently constructing a trie whose search behavior would be undefined.
     *
     * @param nodeIndex  serialized node identifier
     * @param edgeLabels serialized edge labels
     * @throws IOException if the edge labels are not strictly ascending
     */
    private static void validateSerializedEdges(final int nodeIndex, final char... edgeLabels) throws IOException {
        for (int edgeIndex = 1; edgeIndex < edgeLabels.length; edgeIndex++) {
            if (edgeLabels[edgeIndex - 1] >= edgeLabels[edgeIndex]) {
                throw new IOException("Edge labels must be strictly ascending at node " + nodeIndex + ", edge index "
                        + edgeIndex + ": '" + edgeLabels[edgeIndex - 1] + "' then '" + edgeLabels[edgeIndex] + "'.");
            }
        }
    }

    /**
     * Locates the compiled node for the supplied key.
     *
     * @param key already-normalized key to resolve
     * @return compiled node, or {@code null} if the path does not exist
     */
    private CompiledNode<V> findNode(final String key) {
        CompiledNode<V> current = this.root;
        for (int traversalOffset = 0; traversalOffset < key.length(); traversalOffset++) {
            current = current.findChild(
                    key.charAt(this.metadata.traversalDirection().logicalIndex(key.length(), traversalOffset)));
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * Applies lookup-time case normalization according to persisted metadata.
     *
     * @param key lookup key
     * @return normalized key for trie traversal
     */
    private String normalizeLookupKey(final String key) {
        String normalized = key;

        if (this.metadata.caseProcessingMode() == CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }

        if (this.metadata.diacriticProcessingMode() == DiacriticProcessingMode.REMOVE) {
            normalized = DiacriticStripper.strip(normalized);
        } else if (this.metadata.diacriticProcessingMode() == DiacriticProcessingMode.AS_IS_AND_STRIPPED_FALLBACK) {
            throw new UnsupportedOperationException(
                    "Diacritic processing mode AS_IS_AND_STRIPPED_FALLBACK is not supported yet.");
        }

        return normalized;
    }

    /**
     * Builder of {@link FrequencyTrie}.
     *
     * <p>
     * The builder is intentionally mutable and optimized for repeated
     * {@link #put(String, Object)} calls. The final trie is created by
     * {@link #build()}, which performs bottom-up subtree reduction and converts the
     * structure to a compact immutable representation optimized for read
     * operations.
     *
     * @param <V> value type
     */
    public static final class Builder<V> {

        /**
         * Logger of this class.
         */
        private static final Logger LOGGER = Logger.getLogger(Builder.class.getName());

        /**
         * Factory used to create typed arrays.
         */
        private final IntFunction<V[]> arrayFactory;

        /**
         * Reduction configuration.
         */
        private final ReductionSettings reductionSettings;

        /**
         * Logical key traversal direction used by this builder.
         */
        private final WordTraversalDirection traversalDirection;

        /**
         * Dictionary case processing mode associated with this builder.
         */
        private final CaseProcessingMode caseProcessingMode;

        /**
         * Dictionary diacritic processing mode associated with this builder.
         */
        private final DiacriticProcessingMode diacriticProcessingMode;

        /**
         * Mutable root node.
         */
        private final MutableNode<V> root;

        /**
         * Creates a new builder with the provided settings.
         *
         * <p>
         * This constructor preserves the historical Egothor behavior and therefore
         * traverses logical keys from their end toward their beginning.
         * </p>
         *
         * @param arrayFactory      array factory
         * @param reductionSettings reduction configuration
         * @throws NullPointerException if any argument is {@code null}
         */
        public Builder(final IntFunction<V[]> arrayFactory, final ReductionSettings reductionSettings) {
            this(arrayFactory, reductionSettings, WordTraversalDirection.BACKWARD);
        }

        /**
         * Creates a new builder with the provided settings and explicit traversal
         * direction.
         *
         * @param arrayFactory       array factory
         * @param reductionSettings  reduction configuration
         * @param traversalDirection logical key traversal direction
         * @throws NullPointerException if any argument is {@code null}
         */
        public Builder(final IntFunction<V[]> arrayFactory, final ReductionSettings reductionSettings,
                final WordTraversalDirection traversalDirection) {
            this(arrayFactory, reductionSettings, traversalDirection, CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
        }

        /**
         * Creates a new builder with the provided settings, explicit traversal
         * direction, and explicit case processing mode.
         *
         * @param arrayFactory       array factory
         * @param reductionSettings  reduction configuration
         * @param traversalDirection logical key traversal direction
         * @param caseProcessingMode dictionary case processing mode
         * @throws NullPointerException if any argument is {@code null}
         */
        public Builder(final IntFunction<V[]> arrayFactory, final ReductionSettings reductionSettings,
                final WordTraversalDirection traversalDirection, final CaseProcessingMode caseProcessingMode) {
            this(arrayFactory, reductionSettings, traversalDirection, caseProcessingMode,
                    DiacriticProcessingMode.AS_IS);
        }

        /**
         * Creates a new builder with the provided settings, explicit traversal
         * direction, explicit case processing mode, and explicit diacritic processing
         * mode.
         *
         * @param arrayFactory            array factory
         * @param reductionSettings       reduction configuration
         * @param traversalDirection      logical key traversal direction
         * @param caseProcessingMode      dictionary case processing mode
         * @param diacriticProcessingMode dictionary diacritic processing mode
         * @throws NullPointerException if any argument is {@code null}
         */
        public Builder(final IntFunction<V[]> arrayFactory, final ReductionSettings reductionSettings,
                final WordTraversalDirection traversalDirection, final CaseProcessingMode caseProcessingMode,
                final DiacriticProcessingMode diacriticProcessingMode) {
            this.arrayFactory = Objects.requireNonNull(arrayFactory, "arrayFactory");
            this.reductionSettings = Objects.requireNonNull(reductionSettings, "reductionSettings");
            this.traversalDirection = Objects.requireNonNull(traversalDirection, "traversalDirection");
            this.caseProcessingMode = Objects.requireNonNull(caseProcessingMode, "caseProcessingMode");
            this.diacriticProcessingMode = Objects.requireNonNull(diacriticProcessingMode, "diacriticProcessingMode");
            this.root = new MutableNode<>();
        }

        /**
         * Creates a new builder using default thresholds for the supplied reduction
         * mode.
         *
         * <p>
         * This constructor preserves the historical Egothor behavior and therefore
         * traverses logical keys from their end toward their beginning.
         * </p>
         *
         * @param arrayFactory  array factory
         * @param reductionMode reduction mode
         * @throws NullPointerException if any argument is {@code null}
         */
        public Builder(final IntFunction<V[]> arrayFactory, final ReductionMode reductionMode) {
            this(arrayFactory, ReductionSettings.withDefaults(reductionMode), WordTraversalDirection.BACKWARD);
        }

        /**
         * Creates a new builder using default thresholds for the supplied reduction
         * mode and explicit traversal direction.
         *
         * @param arrayFactory       array factory
         * @param reductionMode      reduction mode
         * @param traversalDirection logical key traversal direction
         * @throws NullPointerException if any argument is {@code null}
         */
        public Builder(final IntFunction<V[]> arrayFactory, final ReductionMode reductionMode,
                final WordTraversalDirection traversalDirection) {
            this(arrayFactory, ReductionSettings.withDefaults(reductionMode), traversalDirection);
        }

        /**
         * Stores a value for the supplied key and increments its local frequency.
         *
         * <p>
         * Values are stored at the node addressed by the full key. Since trie values
         * may also appear on internal nodes, an empty key is valid and stores a value
         * directly at the root.
         *
         * @param key   key
         * @param value value
         * @return this builder
         * @throws NullPointerException if {@code key} or {@code value} is {@code null}
         */
        public Builder<V> put(final String key, final V value) {
            return put(key, value, 1);
        }

        /**
         * Builds a compiled read-only trie.
         *
         * @return compiled trie
         */
        public FrequencyTrie<V> build() {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Starting trie compilation with reduction mode {0}.",
                        this.reductionSettings.reductionMode());
            }

            final ReductionContext<V> reductionContext = new ReductionContext<>(this.reductionSettings);
            final ReducedNode<V> reducedRoot = reduce(this.root, reductionContext);
            final CompiledNode<V> compiledRoot = freeze(reducedRoot, new IdentityHashMap<>());

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Trie compilation finished. Canonical node count: {0}.",
                        reductionContext.canonicalNodeCount());
            }

            final TrieMetadata metadata = TrieMetadata.forCompilation(this.traversalDirection, this.reductionSettings,
                    this.diacriticProcessingMode, this.caseProcessingMode);
            return new FrequencyTrie<>(this.arrayFactory, compiledRoot, metadata);
        }

        /**
         * Stores a value for the supplied key and increments its local frequency by the
         * specified positive count.
         *
         * <p>
         * Values are stored at the node addressed by the full key. Since trie values
         * may also appear on internal nodes, an empty key is valid and stores a value
         * directly at the root.
         *
         * <p>
         * This method is functionally equivalent to calling
         * {@link #put(String, Object)} repeatedly {@code count} times, but it avoids
         * unnecessary repeated map updates and is therefore preferable for bulk
         * reconstruction from compiled tries or other aggregated sources.
         *
         * @param key   key
         * @param value value
         * @param count positive frequency increment
         * @return this builder
         * @throws NullPointerException     if {@code key} or {@code value} is
         *                                  {@code null}
         * @throws IllegalArgumentException if {@code count} is less than {@code 1}
         */
        public Builder<V> put(final String key, final V value, final int count) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");

            if (count < 1) { // NOPMD
                throw new IllegalArgumentException("count must be at least 1.");
            }

            final String normalizedKey = normalizeDictionaryKey(key);

            MutableNode<V> current = this.root;
            for (int traversalOffset = 0; traversalOffset < normalizedKey.length(); traversalOffset++) {
                final Character edge = normalizedKey
                        .charAt(this.traversalDirection.logicalIndex(normalizedKey.length(), traversalOffset));
                MutableNode<V> child = current.children().get(edge);
                if (child == null) {
                    child = new MutableNode<>(); // NOPMD
                    current.children().put(edge, child);
                }
                current = child;
            }

            final Integer previous = current.valueCounts().get(value);
            if (previous == null) {
                current.valueCounts().put(value, count);
            } else {
                current.valueCounts().put(value, previous + count);
            }
            return this;
        }

        private String normalizeDictionaryKey(final String key) {
            String normalized = key;

            if (this.caseProcessingMode == CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT) {
                normalized = normalized.toLowerCase(Locale.ROOT);
            }

            if (this.diacriticProcessingMode == DiacriticProcessingMode.REMOVE) {
                normalized = DiacriticStripper.strip(normalized);
            } else if (this.diacriticProcessingMode == DiacriticProcessingMode.AS_IS_AND_STRIPPED_FALLBACK) {
                throw new UnsupportedOperationException(
                        "Diacritic processing mode AS_IS_AND_STRIPPED_FALLBACK is not supported yet.");
            }

            return normalized;
        }

        /**
         * Returns the number of mutable build-time nodes currently reachable from the
         * builder root.
         *
         * <p>
         * This metric is intended mainly for diagnostics and tests that compare the
         * unreduced build-time structure with the final reduced compiled trie.
         *
         * @return number of mutable build-time nodes
         */
        /* default */ int buildTimeSize() {
            return countMutableNodes(this.root);
        }

        /**
         * Returns the logical key traversal direction used by this builder.
         *
         * @return logical key traversal direction
         */
        /* default */ WordTraversalDirection traversalDirection() {
            return this.traversalDirection;
        }

        /**
         * Counts mutable nodes recursively.
         *
         * @param node current node
         * @return reachable mutable node count
         */
        private int countMutableNodes(final MutableNode<V> node) {
            int count = 1;
            for (MutableNode<V> child : node.children().values()) {
                count += countMutableNodes(child);
            }
            return count;
        }

        /**
         * Reduces a mutable node to a canonical reduced node.
         *
         * @param source  source mutable node
         * @param context reduction context
         * @return canonical reduced node
         */
        private ReducedNode<V> reduce(final MutableNode<V> source, final ReductionContext<V> context) {
            final Map<Character, ReducedNode<V>> reducedChildren = new LinkedHashMap<>();

            for (Map.Entry<Character, MutableNode<V>> childEntry : source.children().entrySet()) {
                final ReducedNode<V> reducedChild = reduce(childEntry.getValue(), context);
                reducedChildren.put(childEntry.getKey(), reducedChild);
            }

            final Map<V, Integer> localCounts = copyCounts(source.valueCounts());
            final LocalValueSummary<V> localSummary = LocalValueSummary.of(localCounts, this.arrayFactory);
            final ReductionSignature<V> signature = ReductionSignature.create(localSummary, reducedChildren,
                    context.settings());

            ReducedNode<V> canonical = context.lookup(signature);
            if (canonical == null) {
                canonical = new ReducedNode<>(signature, localCounts, reducedChildren);
                context.register(signature, canonical);
                return canonical;
            }

            canonical.mergeLocalCounts(localCounts);
            canonical.mergeChildren(reducedChildren);

            return canonical;
        }

        /**
         * Freezes a reduced node into an immutable compiled node.
         *
         * @param reducedNode reduced node
         * @param cache       already frozen nodes
         * @return immutable compiled node
         */
        private CompiledNode<V> freeze(final ReducedNode<V> reducedNode,
                final Map<ReducedNode<V>, CompiledNode<V>> cache) {
            final CompiledNode<V> existing = cache.get(reducedNode);
            if (existing != null) {
                return existing;
            }

            final LocalValueSummary<V> localSummary = LocalValueSummary.of(reducedNode.localCounts(),
                    this.arrayFactory);

            final List<Map.Entry<Character, ReducedNode<V>>> childEntries = new ArrayList<>(
                    reducedNode.children().entrySet());
            childEntries.sort(Map.Entry.comparingByKey());

            final char[] edges = new char[childEntries.size()];
            @SuppressWarnings("unchecked")
            final CompiledNode<V>[] childNodes = new CompiledNode[childEntries.size()];

            for (int index = 0; index < childEntries.size(); index++) {
                final Map.Entry<Character, ReducedNode<V>> entry = childEntries.get(index);
                edges[index] = entry.getKey();
                childNodes[index] = freeze(entry.getValue(), cache);
            }

            final CompiledNode<V> frozen = new CompiledNode<>(edges, childNodes, localSummary.orderedValues(),
                    localSummary.orderedCounts());
            cache.put(reducedNode, frozen);
            return frozen;
        }

        /**
         * Creates a shallow frequency copy preserving deterministic insertion order of
         * first occurrence.
         *
         * @param source source counts
         * @return copied counts
         */
        private Map<V, Integer> copyCounts(final Map<V, Integer> source) {
            return new LinkedHashMap<>(source);
        }
    }

    /**
     * Codec used to persist values stored in the trie.
     *
     * @param <V> value type
     */
    public interface ValueStreamCodec<V> {

        /**
         * Writes one value to the supplied data output.
         *
         * @param dataOutput target data output
         * @param value      value to write
         * @throws IOException if writing fails
         */
        void write(DataOutputStream dataOutput, V value) throws IOException;

        /**
         * Reads one value from the supplied data input.
         *
         * @param dataInput source data input
         * @return read value
         * @throws IOException if reading fails
         */
        V read(DataInputStream dataInput) throws IOException;
    }

}
