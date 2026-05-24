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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
public final class FrequencyTrie<V> {

    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(FrequencyTrie.class.getName());

    /**
     * Domain separator used by the trie fingerprint canonical input.
     */
    private static final String FINGERPRINT_DOMAIN = "RADIXOR-FREQUENCY-TRIE-FINGERPRINT";

    /**
     * Version of the canonical fingerprint input format.
     */
    private static final int FINGERPRINT_FORMAT_VERSION = 1;

    /**
     * Root node of the compiled read-only trie.
     */
    private final CompiledNode<V> root;

    /**
     * Metadata persisted together with this trie.
     */
    private final TrieMetadata metadata;

    /**
     * Canonical SHA-256 fingerprint bytes. The internal array is never exposed
     * directly to callers.
     */
    private final byte[] fingerprintBytes;

    /**
     * Cached traversal direction used for key lookup.
     */
    private final WordTraversalDirection lookupTraversalDirection;

    /**
     * Whether lookups require lowercase normalization.
     */
    private final boolean lowercasesLookupKeys;

    /**
     * Whether lookups require diacritic stripping.
     */
    private final boolean removeDiacritics;

    /**
     * Shared empty array instance for empty lookup results from
     * {@link #getAll(String)}.
     */
    private final V[] emptyValues;

    /**
     * Binary format magic header.
     */
    private static final int STREAM_MAGIC = 0x45475452;

    /**
     * Minimum supported stream version constant retained for explicit range checks.
     */
    private static final int MIN_STREAM_VERSION = 1;

    /**
     * Number of stored values for which {@link #getEntries(String)} can return an
     * empty result.
     */
    private static final int NO_VALUE_COUNT = 0;

    /**
     * Number of stored values for which {@link #getEntries(String)} can use a
     * one-item immutable list special case.
     */
    private static final int SINGLE_VALUE_COUNT = 1;

    /**
     * Binary format version.
     */
    private static final int STREAM_VERSION = 5;

    /**
     * Version where traversal-direction ordinal is persisted.
     */
    private static final int TRAVERSAL_VERSION = 2;

    /**
     * Version where compact reduction metadata is persisted.
     */
    private static final int REDUCTION_VERSION = 3;

    /**
     * Version where case-processing mode ordinal is persisted.
     */
    private static final int CASE_VERSION = 4;

    /**
     * Argument name for lookup keys.
     */
    private static final String ARG_KEY = "key";

    /**
     * Default dense child lookup span in code points used when materializing
     * compiled nodes without an explicit override.
     * <p>
     * Increasing this value increases the chance of direct array indexing for child
     * lookup at runtime at the cost of per-node dense table memory for compact
     * character spans.
     * </p>
     */
    public static final int DEFAULT_MAX_EXPANDED_INDEX = 512;

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
     * Receives trie values during visitor-style lookup.
     *
     * <p>
     * Implementations are caller-owned and are not retained by the trie. Returning
     * {@code false} stops iteration after the current callback.
     * </p>
     *
     * @param <V> value type
     */
    @FunctionalInterface
    public interface EntrySink<V> {

        /**
         * Accepts one ordered local value.
         *
         * @param value stored value
         * @param count stored local occurrence count
         * @param rank  zero-based rank in deterministic local ordering
         * @return {@code true} to continue iteration, {@code false} to stop
         */
        boolean accept(V value, int count, int rank);
    }

    /**
     * Creates a new compiled trie instance.
     *
     * @param arrayFactory array factory
     * @param root         compiled root node
     * @param metadata     trie metadata describing lookup and persistence semantics
     * @throws NullPointerException if any argument is {@code null}
     */
    private FrequencyTrie(final IntFunction<V[]> arrayFactory, final CompiledNode<V> root,
            final TrieMetadata metadata) {
        this.root = Objects.requireNonNull(root, "root");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.fingerprintBytes = computeFingerprintBytes(root, metadata);
        this.lookupTraversalDirection = metadata.traversalDirection();
        this.lowercasesLookupKeys = metadata.caseProcessingMode() == CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT;
        this.removeDiacritics = metadata.diacriticProcessingMode() == DiacriticProcessingMode.REMOVE;
        this.emptyValues = arrayFactory.apply(0);
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
        Objects.requireNonNull(key, ARG_KEY);
        final CompiledNode<V> node = findNode(normalizeLookupKey(key));
        if (node == null) {
            return null;
        }
        final V[] orderedValues = node.orderedValues();
        if (orderedValues.length == 0) {
            return null;
        }
        return orderedValues[0];
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
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    public V[] getAll(final String key) {
        Objects.requireNonNull(key, ARG_KEY);
        final CompiledNode<V> node = findNode(normalizeLookupKey(key));
        if (node == null) {
            return this.emptyValues;
        }
        final V[] orderedValues = node.orderedValues();
        if (orderedValues.length == 0) {
            return this.emptyValues;
        }
        return Arrays.copyOf(orderedValues, orderedValues.length);
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
        Objects.requireNonNull(key, ARG_KEY);
        final CompiledNode<V> node = findNode(normalizeLookupKey(key));
        if (node == null) {
            return List.of();
        }

        final V[] orderedValues = node.orderedValues();
        final int valueCount = orderedValues.length;
        if (valueCount == NO_VALUE_COUNT) {
            return List.of();
        }

        if (valueCount == SINGLE_VALUE_COUNT) {
            return List.of(new ValueCount<>(orderedValues[0], node.orderedCounts()[0]));
        }

        final int[] orderedCounts = node.orderedCounts();
        final List<ValueCount<V>> entries = new ArrayList<>(valueCount);
        for (int index = 0; index < valueCount; index++) {
            entries.add(new ValueCount<>(orderedValues[index], orderedCounts[index]));
        }
        return Collections.unmodifiableList(entries);
    }

    /**
     * Visits all values stored at the node addressed by an already-normalized
     * {@code char[]} key slice.
     *
     * <p>
     * This method bypasses {@link TrieMetadata#caseProcessingMode()} and
     * {@link TrieMetadata#diacriticProcessingMode()}. The caller must provide input
     * normalized exactly as required by this trie's metadata. The trie is immutable
     * and thread-safe for concurrent reads; the supplied sink is caller-owned and
     * is not retained.
     * </p>
     *
     * @param key        normalized key storage
     * @param offset     first character offset
     * @param length     number of characters to read
     * @param sink       value sink
     * @param maxResults maximum number of results to visit
     * @return number of visited values
     * @throws NullPointerException      if {@code key} or {@code sink} is
     *                                   {@code null}
     * @throws IndexOutOfBoundsException if the key slice is invalid
     * @throws IllegalArgumentException  if {@code maxResults} is negative
     */
    public int getAllNormalized(final char[] key, final int offset, final int length, final EntrySink<? super V> sink,
            final int maxResults) {
        Objects.requireNonNull(key, ARG_KEY);
        Objects.requireNonNull(sink, "sink");
        Objects.checkFromIndexSize(offset, length, key.length);
        validateMaxResults(maxResults);
        if (maxResults == 0) {
            return 0;
        }
        return visitNode(findNode(key, offset, length), sink, maxResults);
    }

    /**
     * Visits all values stored at the node addressed by an already-normalized
     * character sequence.
     *
     * @param key        normalized key
     * @param sink       value sink
     * @param maxResults maximum number of results to visit
     * @return number of visited values
     * @throws NullPointerException     if {@code key} or {@code sink} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code maxResults} is negative
     * @see #getAllNormalized(char[], int, int, EntrySink, int)
     */
    public int getAllNormalized(final CharSequence key, final EntrySink<? super V> sink, final int maxResults) {
        Objects.requireNonNull(key, ARG_KEY);
        Objects.requireNonNull(sink, "sink");
        validateMaxResults(maxResults);
        if (maxResults == 0) {
            return 0;
        }
        return visitNode(findNode(key), sink, maxResults);
    }

    /**
     * Visits the first value stored at the node addressed by an already-normalized
     * {@code char[]} key slice.
     *
     * @param key    normalized key storage
     * @param offset first character offset
     * @param length number of characters to read
     * @param sink   value sink
     * @return {@code true} when a value was visited, otherwise {@code false}
     * @see #getAllNormalized(char[], int, int, EntrySink, int)
     */
    public boolean getFirstNormalized(final char[] key, final int offset, final int length,
            final EntrySink<? super V> sink) {
        return getAllNormalized(key, offset, length, sink, 1) == 1;
    }

    /**
     * Visits the first value stored at the node addressed by an already-normalized
     * character sequence.
     *
     * @param key  normalized key
     * @param sink value sink
     * @return {@code true} when a value was visited, otherwise {@code false}
     * @see #getAllNormalized(CharSequence, EntrySink, int)
     */
    public boolean getFirstNormalized(final CharSequence key, final EntrySink<? super V> sink) {
        return getAllNormalized(key, sink, 1) == 1;
    }

    /**
     * Visits all values stored at the node addressed by the supplied key, applying
     * metadata-driven lookup normalization when required.
     *
     * <p>
     * This method preserves the same lookup normalization semantics as
     * {@link #getAll(String)}. It may allocate when metadata requires lowercase or
     * diacritic normalization.
     * </p>
     *
     * @param key        key to resolve
     * @param sink       value sink
     * @param maxResults maximum number of results to visit
     * @return number of visited values
     */
    public int getAll(final CharSequence key, final EntrySink<? super V> sink, final int maxResults) {
        Objects.requireNonNull(key, ARG_KEY);
        Objects.requireNonNull(sink, "sink");
        validateMaxResults(maxResults);
        if (maxResults == 0) {
            return 0;
        }
        final CharSequence normalized = normalizeLookupKey(key);
        return visitNode(findNode(normalized), sink, maxResults);
    }

    /**
     * Visits the first value stored at the node addressed by the supplied key,
     * applying metadata-driven lookup normalization when required.
     *
     * @param key  key to resolve
     * @param sink value sink
     * @return {@code true} when a value was visited, otherwise {@code false}
     * @see #getAll(CharSequence, EntrySink, int)
     */
    public boolean getFirst(final CharSequence key, final EntrySink<? super V> sink) {
        return getAll(key, sink, 1) == 1;
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
     * Returns the deterministic SHA-256 fingerprint of this trie.
     *
     * <p>
     * The fingerprint is a canonical model identity, not a Java object identity.
     * It includes a fingerprint-domain marker, the fingerprint input format
     * version, persisted metadata, and the complete compiled-node structure
     * reachable from the root, including edges, child references, local values,
     * and local counts.
     * </p>
     *
     * <p>
     * The returned value is stable across JVM runs for equivalent trie content and
     * metadata. It does not include object identity, memory layout, runtime cache
     * state, absolute file paths, timestamps, or other process-local state.
     * </p>
     *
     * @return 64-character lowercase hexadecimal SHA-256 fingerprint
     */
    public String getFingerprint() {
        return toLowerHex(this.fingerprintBytes);
    }

    /**
     * Returns a defensive copy of the raw SHA-256 fingerprint bytes.
     *
     * <p>
     * The returned array has length {@code 32}. Mutating it does not affect this
     * trie.
     * </p>
     *
     * @return defensive copy of the 32-byte SHA-256 fingerprint
     */
    public byte[] copyFingerprintBytes() {
        return Arrays.copyOf(this.fingerprintBytes, this.fingerprintBytes.length);
    }

    private static <V> byte[] computeFingerprintBytes(final CompiledNode<V> root, final TrieMetadata metadata) {
        final MessageDigest messageDigest = newSha256Digest();
        updateUtf8(messageDigest, FINGERPRINT_DOMAIN);
        updateInt(messageDigest, FINGERPRINT_FORMAT_VERSION);
        updateUtf8(messageDigest, metadata.toTextBlock());

        final Map<CompiledNode<V>, Integer> nodeIds = new IdentityHashMap<>();
        final List<CompiledNode<V>> orderedNodes = new ArrayList<>();
        assignNodeIds(root, nodeIds, orderedNodes);

        updateInt(messageDigest, nodeIds.get(root));
        updateInt(messageDigest, orderedNodes.size());
        for (CompiledNode<V> node : orderedNodes) {
            updateNodeFingerprint(messageDigest, node, nodeIds);
        }
        return messageDigest.digest();
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
        return readFrom(inputStream, arrayFactory, valueCodec, -1);
    }

    /**
     * Reads a compiled trie from the supplied input stream, optionally overriding
     * dense child-index span configuration.
     * <p>
     * This setting is applied only while materializing the in-memory compiled
     * representation during load. It is not serialized in {@link TrieMetadata}, so
     * each load can independently choose its own runtime lookup trade-off.
     * </p>
     *
     * @param inputStream      source input stream
     * @param arrayFactory     array factory used to create typed arrays
     * @param valueCodec       codec used to read values
     * @param maxExpandedIndex dense lookup span override; zero disables dense
     *                         lookup, negative values use
     *                         {@link #DEFAULT_MAX_EXPANDED_INDEX}
     * @param <V>              value type
     * @return deserialized compiled trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if reading fails or the binary format is invalid
     */
    public static <V> FrequencyTrie<V> readFrom(final InputStream inputStream, final IntFunction<V[]> arrayFactory,
            final ValueStreamCodec<V> valueCodec, final int maxExpandedIndex) throws IOException {
        return CompiledTrieReader.read(inputStream, arrayFactory, valueCodec, maxExpandedIndex);
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

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available.", exception);
        }
    }

    private static <V> void updateNodeFingerprint(final MessageDigest messageDigest, final CompiledNode<V> node,
            final Map<CompiledNode<V>, Integer> nodeIds) {
        final char[] edgeLabels = node.edgeLabels();
        final CompiledNode<V>[] children = node.children();
        final V[] values = node.orderedValues();
        final int[] counts = node.orderedCounts();

        updateInt(messageDigest, edgeLabels.length);
        for (char edgeLabel : edgeLabels) {
            updateInt(messageDigest, edgeLabel);
        }
        for (CompiledNode<V> child : children) {
            final Integer childNodeId = nodeIds.get(child);
            if (childNodeId == null) {
                throw new IllegalStateException("Missing child node identifier during trie fingerprinting.");
            }
            updateInt(messageDigest, childNodeId.intValue());
        }

        updateInt(messageDigest, values.length);
        for (V value : values) {
            updateUtf8(messageDigest, String.valueOf(value));
        }
        for (int count : counts) {
            updateInt(messageDigest, count);
        }
    }

    private static void updateUtf8(final MessageDigest messageDigest, final String value) {
        final byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        updateInt(messageDigest, encoded.length);
        messageDigest.update(encoded);
    }

    private static void updateInt(final MessageDigest messageDigest, final int value) {
        messageDigest.update((byte) (value >>> 24));
        messageDigest.update((byte) (value >>> 16));
        messageDigest.update((byte) (value >>> 8));
        messageDigest.update((byte) value);
    }

    private static String toLowerHex(final byte[] digest) {
        final StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            builder.append(Character.forDigit((item >>> 4) & 0x0F, 16));
            builder.append(Character.forDigit(item & 0x0F, 16));
        }
        return builder.toString();
    }

    /**
     * Internal helper that materializes serialized trie data.
     *
     * <p>
     * Moving reader complexity into this helper keeps the public-facing class from
     * accumulating excessive class-level cyclomatic complexity while preserving the
     * same binary compatibility contract.
     * </p>
     */
    private static final class CompiledTrieReader {

        private static <V> FrequencyTrie<V> read(final InputStream inputStream, final IntFunction<V[]> arrayFactory,
                final ValueStreamCodec<V> valueCodec, final int maxExpandedIndex) throws IOException {
            Objects.requireNonNull(inputStream, "inputStream");
            Objects.requireNonNull(arrayFactory, "arrayFactory");
            Objects.requireNonNull(valueCodec, "valueCodec");
            if (maxExpandedIndex < -1) {
                throw new IllegalArgumentException("maxExpandedIndex must be >= -1.");
            }

            final DataInputStream dataInput = wrapInputStream(inputStream);
            final int magic = dataInput.readInt();
            if (magic != STREAM_MAGIC) {
                throw new IOException("Unsupported trie stream header: " + Integer.toHexString(magic));
            }

            final int version = dataInput.readInt();
            if (version < MIN_STREAM_VERSION || version > STREAM_VERSION) {
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

            final TrieMetadata sourceMetadata = readMetadata(dataInput, version);
            final int effectiveMaxExpandedIndex = maxExpandedIndex >= 0 ? maxExpandedIndex : DEFAULT_MAX_EXPANDED_INDEX;
            final CompiledNode<V>[] nodes = readNodes(dataInput, arrayFactory, valueCodec, nodeCount,
                    effectiveMaxExpandedIndex);
            final CompiledNode<V> rootNode = nodes[rootNodeId];

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Read compiled trie with {0} canonical nodes.", nodeCount);
            }

            return new FrequencyTrie<>(arrayFactory, rootNode, sourceMetadata);
        }

        private static DataInputStream wrapInputStream(final InputStream inputStream) {
            return inputStream instanceof DataInputStream ? (DataInputStream) inputStream
                    : new DataInputStream(inputStream);
        }

        private static TrieMetadata readMetadata(final DataInputStream dataInput, final int version)
                throws IOException {
            if (version == STREAM_VERSION) {
                return readTextMetadata(dataInput);
            }

            final WordTraversalDirection traversalDirection = readTraversalDirection(dataInput, version);
            if (version < REDUCTION_VERSION) {
                return TrieMetadata.legacy(version, traversalDirection);
            }

            final ReductionSettings reductionSettings = readReductionSettings(dataInput);
            final DiacriticProcessingMode diacriticProcessingMode = readEnumByOrdinal(dataInput,
                    DiacriticProcessingMode.values(), "diacritic processing mode");
            final CaseProcessingMode caseProcessingMode = version >= CASE_VERSION ? readCaseProcessingMode(dataInput)
                    : CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT;
            return new TrieMetadata(version, traversalDirection, reductionSettings, diacriticProcessingMode,
                    caseProcessingMode);
        }

        private static TrieMetadata readTextMetadata(final DataInputStream dataInput) throws IOException {
            try {
                return TrieMetadata.fromTextBlock(STREAM_VERSION, dataInput.readUTF());
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid metadata block.", exception);
            }
        }

        private static WordTraversalDirection readTraversalDirection(final DataInputStream dataInput, final int version)
                throws IOException {
            if (version < TRAVERSAL_VERSION) {
                return WordTraversalDirection.BACKWARD;
            }
            return readEnumByOrdinal(dataInput, WordTraversalDirection.values(), "traversal direction");
        }

        private static ReductionSettings readReductionSettings(final DataInputStream dataInput) throws IOException {
            final ReductionMode reductionMode = readEnumByOrdinal(dataInput, ReductionMode.values(), "reduction mode");
            final int dominantWinnerMinPercent = dataInput.readInt();
            final int dominantWinnerOverSecondRatio = dataInput.readInt(); // NOPMD
            return new ReductionSettings(reductionMode, dominantWinnerMinPercent, dominantWinnerOverSecondRatio);
        }

        private static CaseProcessingMode readCaseProcessingMode(final DataInputStream dataInput) throws IOException {
            return readEnumByOrdinal(dataInput, CaseProcessingMode.values(), "case processing mode");
        }

        private static <E extends Enum<E>> E readEnumByOrdinal(final DataInputStream dataInput, final E[] values,
                final String name) throws IOException {
            final int ordinal = dataInput.readInt();
            if (ordinal < 0 || ordinal >= values.length) {
                throw new IOException("Invalid " + name + " ordinal: " + ordinal);
            }
            return values[ordinal];
        }

        private static <V> CompiledNode<V>[] readNodes(final DataInputStream dataInput,
                final IntFunction<V[]> arrayFactory, final ValueStreamCodec<V> valueCodec, final int nodeCount,
                final int maxExpandedIndex) throws IOException {
            final char[][] edgeLabelsByNode = new char[nodeCount][];
            final int[][] childNodeIdsByNode = new int[nodeCount][];
            @SuppressWarnings("unchecked")
            final V[][] orderedValuesByNode = (V[][]) new Object[nodeCount][];
            final int[][] orderedCountsByNode = new int[nodeCount][];

            for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
                final int edgeCount = dataInput.readInt();
                if (edgeCount < 0) {
                    throw new IOException("Negative edge count at node " + nodeIndex + ": " + edgeCount);
                }

                edgeLabelsByNode[nodeIndex] = new char[edgeCount];
                childNodeIdsByNode[nodeIndex] = new int[edgeCount];

                for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                    edgeLabelsByNode[nodeIndex][edgeIndex] = dataInput.readChar();
                    childNodeIdsByNode[nodeIndex][edgeIndex] = dataInput.readInt();
                }

                validateSerializedEdges(nodeIndex, edgeLabelsByNode[nodeIndex]);

                final int valueCount = dataInput.readInt();
                if (valueCount < 0) {
                    throw new IOException("Negative value count at node " + nodeIndex + ": " + valueCount);
                }

                orderedValuesByNode[nodeIndex] = arrayFactory.apply(valueCount);
                orderedCountsByNode[nodeIndex] = new int[valueCount];

                for (int valueIndex = 0; valueIndex < valueCount; valueIndex++) {
                    orderedValuesByNode[nodeIndex][valueIndex] = valueCodec.read(dataInput);
                    orderedCountsByNode[nodeIndex][valueIndex] = dataInput.readInt();
                    if (orderedCountsByNode[nodeIndex][valueIndex] <= 0) {
                        throw new IOException("Non-positive stored count at node " + nodeIndex + ", value index "
                                + valueIndex + ": " + orderedCountsByNode[nodeIndex][valueIndex]);
                    }
                }
            }

            @SuppressWarnings("unchecked")
            final CompiledNode<V>[] nodes = new CompiledNode[nodeCount];
            final boolean[] inProgress = new boolean[nodeCount];

            for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
                nodes[nodeIndex] = resolveNode(nodeIndex, edgeLabelsByNode, childNodeIdsByNode, orderedValuesByNode,
                        orderedCountsByNode, nodes, inProgress, maxExpandedIndex);
            }

            return nodes;
        }

        private static <V> CompiledNode<V> resolveNode(final int nodeIndex, final char[][] edgeLabelsByNode,
                final int[][] childNodeIdsByNode, final V[][] orderedValuesByNode, final int[][] orderedCountsByNode,
                final CompiledNode<V>[] nodes, final boolean[] inProgress, final int maxExpandedIndex)
                throws IOException {
            final CompiledNode<V> cachedNode = nodes[nodeIndex];
            if (cachedNode != null) {
                return cachedNode;
            }

            if (inProgress[nodeIndex]) {
                throw new IOException(
                        "Invalid serialized node graph: cyclic reference detected at node " + nodeIndex + '.');
            }
            inProgress[nodeIndex] = true;
            try {
                final char[] edgeLabels = edgeLabelsByNode[nodeIndex];
                final int[] childNodeIds = childNodeIdsByNode[nodeIndex];
                final int edgeCount = childNodeIds.length;
                @SuppressWarnings("unchecked")
                final CompiledNode<V>[] children = new CompiledNode[edgeCount];

                for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                    final int childNodeId = childNodeIds[edgeIndex];
                    if (childNodeId < 0 || childNodeId >= edgeLabelsByNode.length) {
                        throw new IOException("Invalid child node id at node " + nodeIndex + ", edge index " + edgeIndex
                                + ": " + childNodeId);
                    }
                    children[edgeIndex] = resolveNode(childNodeId, edgeLabelsByNode, childNodeIdsByNode,
                            orderedValuesByNode, orderedCountsByNode, nodes, inProgress, maxExpandedIndex);
                }

                final CompiledNode<V> node = new CompiledNode<>(edgeLabels, children, orderedValuesByNode[nodeIndex],
                        maxExpandedIndex, orderedCountsByNode[nodeIndex]);
                nodes[nodeIndex] = node;
                return node;
            } finally {
                inProgress[nodeIndex] = false;
            }
        }

        private static void validateSerializedEdges(final int nodeIndex, final char... edgeLabels) throws IOException {
            for (int edgeIndex = 1; edgeIndex < edgeLabels.length; edgeIndex++) {
                if (edgeLabels[edgeIndex - 1] >= edgeLabels[edgeIndex]) {
                    throw new IOException(
                            "Edge labels must be strictly ascending at node " + nodeIndex + ", edge index " + edgeIndex
                                    + ": '" + edgeLabels[edgeIndex - 1] + "' then '" + edgeLabels[edgeIndex] + "'.");
                }
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
        return findNode((CharSequence) key);
    }

    /**
     * Locates the compiled node for the supplied key.
     *
     * @param key already-normalized key to resolve
     * @return compiled node, or {@code null} if the path does not exist
     */
    private CompiledNode<V> findNode(final CharSequence key) {
        CompiledNode<V> current = this.root;
        if (this.lookupTraversalDirection == WordTraversalDirection.BACKWARD) {
            for (int traversalOffset = key.length() - 1; traversalOffset >= 0; traversalOffset--) {
                current = current.findChild(key.charAt(traversalOffset));
                if (current == null) {
                    return null;
                }
            }
            return current;
        }

        for (int traversalOffset = 0; traversalOffset < key.length(); traversalOffset++) {
            current = current.findChild(key.charAt(traversalOffset));
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * Locates the compiled node for the supplied key slice.
     *
     * @param key    already-normalized key storage
     * @param offset first character offset
     * @param length number of characters to read
     * @return compiled node, or {@code null} if the path does not exist
     */
    private CompiledNode<V> findNode(final char[] key, final int offset, final int length) {
        CompiledNode<V> current = this.root;
        if (this.lookupTraversalDirection == WordTraversalDirection.BACKWARD) {
            for (int traversalOffset = offset + length - 1; traversalOffset >= offset; traversalOffset--) {
                current = current.findChild(key[traversalOffset]);
                if (current == null) {
                    return null;
                }
            }
            return current;
        }

        final int endExclusive = offset + length;
        for (int traversalOffset = offset; traversalOffset < endExclusive; traversalOffset++) {
            current = current.findChild(key[traversalOffset]);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * Visits node-local values without allocating result containers.
     *
     * @param node       resolved node, or {@code null}
     * @param sink       value sink
     * @param maxResults maximum values to visit
     * @return number of visited values
     */
    private int visitNode(final CompiledNode<V> node, final EntrySink<? super V> sink, final int maxResults) {
        if (node == null) {
            return 0;
        }

        final V[] orderedValues = node.orderedValues();
        final int valueCount = Math.min(orderedValues.length, maxResults);
        if (valueCount == 0) {
            return 0;
        }

        final int[] orderedCounts = node.orderedCounts();
        int visited = 0;
        for (int rank = 0; rank < valueCount; rank++) {
            visited++;
            if (!sink.accept(orderedValues[rank], orderedCounts[rank], rank)) {
                break;
            }
        }
        return visited;
    }

    /**
     * Validates visitor maximum result count.
     *
     * @param maxResults maximum result count
     */
    private static void validateMaxResults(final int maxResults) {
        if (maxResults < 0) {
            throw new IllegalArgumentException("maxResults must be non-negative.");
        }
    }

    /**
     * Applies lookup-time case normalization according to persisted metadata.
     *
     * @param key lookup key
     * @return normalized key for trie traversal
     */
    private String normalizeLookupKey(final String key) {
        return normalizeLookupKey((CharSequence) key).toString();
    }

    /**
     * Applies lookup-time normalization according to persisted metadata.
     *
     * @param key lookup key
     * @return normalized key for trie traversal
     */
    private CharSequence normalizeLookupKey(final CharSequence key) {
        if (!this.lowercasesLookupKeys && !this.removeDiacritics) {
            return key;
        }

        String normalized = key.toString();
        if (this.lowercasesLookupKeys) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        if (this.removeDiacritics) {
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
         * Dense edge lookup span threshold.
         * <p>
         * This value controls a speed/memory trade-off during freezing: dense child
         * lookup tables are allocated only for nodes whose child labels fit in this
         * span.
         * </p>
         */
        private final int maxExpandedIndex;

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
            this(arrayFactory, reductionSettings, traversalDirection, caseProcessingMode, diacriticProcessingMode,
                    CompiledNode.DEFAULT_MAX_EXPANDED_INDEX);
        }

        /**
         * Creates a new builder with the provided settings, explicit traversal
         * direction, explicit case processing mode, explicit diacritic processing mode,
         * and an explicit dense child lookup threshold.
         *
         * @param arrayFactory            array factory
         * @param reductionSettings       reduction configuration
         * @param traversalDirection      logical key traversal direction
         * @param caseProcessingMode      dictionary case processing mode
         * @param diacriticProcessingMode dictionary diacritic processing mode
         * @param maxExpandedIndex        dense lookup span override; zero disables
         *                                dense lookup. Larger values increase direct
         *                                indexing opportunities while potentially
         *                                increasing materialization memory in nodes
         *                                whose edge label span is within the limit.
         * @throws NullPointerException if any argument is {@code null}
         */
        public Builder(final IntFunction<V[]> arrayFactory, final ReductionSettings reductionSettings,
                final WordTraversalDirection traversalDirection, final CaseProcessingMode caseProcessingMode,
                final DiacriticProcessingMode diacriticProcessingMode, final int maxExpandedIndex) {
            this.arrayFactory = Objects.requireNonNull(arrayFactory, "arrayFactory");
            this.reductionSettings = Objects.requireNonNull(reductionSettings, "reductionSettings");
            this.traversalDirection = Objects.requireNonNull(traversalDirection, "traversalDirection");
            this.caseProcessingMode = Objects.requireNonNull(caseProcessingMode, "caseProcessingMode");
            this.diacriticProcessingMode = Objects.requireNonNull(diacriticProcessingMode, "diacriticProcessingMode");
            if (maxExpandedIndex < 0) {
                throw new IllegalArgumentException("maxExpandedIndex must be non-negative.");
            }
            this.maxExpandedIndex = maxExpandedIndex;
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
            Objects.requireNonNull(key, ARG_KEY);
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

        /**
         * Applies build-time dictionary-key normalization according to the builder
         * configuration.
         *
         * @param key dictionary key
         * @return normalized key for trie insertion
         */
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
                    this.maxExpandedIndex, localSummary.orderedCounts());
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
