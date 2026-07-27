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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Binary persistence helper for patch-command stemmer tries.
 *
 * <p>
 * This class persists {@link FrequencyTrie} instances whose values are compact
 * patch commands represented as {@link String}. The serialized trie payload is
 * the native binary format of {@link FrequencyTrie}, wrapped in GZip
 * compression.
 * Binary reads can either preserve those serialized strings or materialize
 * {@link CompiledPatchCommand} values directly in the final trie nodes.
 *
 * <p>
 * The helper centralizes the codec and compression details so that higher-level
 * loader APIs can remain focused on source selection rather than stream
 * mechanics.
 */
public final class StemmerPatchTrieBinaryIO {

    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(StemmerPatchTrieBinaryIO.class.getName());

    /**
     * Value codec for persisted patch-command strings.
     */
    private static final FrequencyTrie.ValueStreamCodec<String> STRING_CODEC = new StringValueStreamCodec();

    /**
     * Maximum serialized patch-command length included in validation diagnostics.
     */
    private static final int MAX_DIAGNOSTIC_PATCH_LENGTH = 128;

    /**
     * Null-check parameter name for filesystem paths.
     */
    private static final String PATH_PARAMETER = "path";

    /**
     * Null-check parameter name for filesystem path strings.
     */
    private static final String FILE_NAME_PARAMETER = "fileName";

    /**
     * Utility class.
     */
    private StemmerPatchTrieBinaryIO() {
        throw new AssertionError("No instances.");
    }

    /**
     * Reads a GZip-compressed binary patch-command trie from a filesystem path.
     *
     * @param path source file
     * @return deserialized trie
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if reading or decompression fails
     */
    public static FrequencyTrie<String> read(final Path path) throws IOException {
        Objects.requireNonNull(path, PATH_PARAMETER);

        try (InputStream fileInputStream = Files.newInputStream(path)) {
            return read(fileInputStream);
        }
    }

    /**
     * Reads a GZip-compressed binary patch-command trie from a filesystem path with
     * an optional dense child lookup span override.
     * <p>
     * This is a runtime-only tuning parameter. The dense-span setting is not
     * persisted in the file and does not change the compiled metadata.
     * </p>
     *
     * @param path             source file
     * @param maxExpandedIndex dense lookup span override; negative values use
     *                         {@link FrequencyTrie#DEFAULT_MAX_EXPANDED_INDEX}
     * @return deserialized trie
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if reading or decompression fails
     */
    public static FrequencyTrie<String> read(final Path path, final int maxExpandedIndex) throws IOException {
        Objects.requireNonNull(path, PATH_PARAMETER);

        try (InputStream fileInputStream = Files.newInputStream(path)) {
            return read(fileInputStream, maxExpandedIndex);
        }
    }

    /**
     * Reads a GZip-compressed binary patch-command trie from a filesystem path
     * string.
     *
     * @param fileName source file name or path string
     * @return deserialized trie
     * @throws NullPointerException if {@code fileName} is {@code null}
     * @throws IOException          if reading or decompression fails
     */
    public static FrequencyTrie<String> read(final String fileName) throws IOException {
        Objects.requireNonNull(fileName, FILE_NAME_PARAMETER);
        return read(Path.of(fileName));
    }

    /**
     * Reads a GZip-compressed binary patch-command trie from a filesystem path
     * string with an optional dense child lookup span override.
     * <p>
     * This is a runtime-only tuning parameter. The dense-span setting is not
     * persisted in the file and does not change the compiled metadata.
     * </p>
     *
     * @param fileName         source file name or path string
     * @param maxExpandedIndex dense lookup span override; negative values use
     *                         {@link FrequencyTrie#DEFAULT_MAX_EXPANDED_INDEX}
     * @return deserialized trie
     * @throws NullPointerException if {@code fileName} is {@code null}
     * @throws IOException          if reading or decompression fails
     */
    public static FrequencyTrie<String> read(final String fileName, final int maxExpandedIndex) throws IOException {
        Objects.requireNonNull(fileName, FILE_NAME_PARAMETER);
        return read(Path.of(fileName), maxExpandedIndex);
    }

    /**
     * Reads a GZip-compressed binary patch-command trie from an input stream.
     *
     * <p>
     * The supplied stream is consumed but not interpreted as plain trie bytes; it
     * is first decompressed using {@link GZIPInputStream}.
     *
     * @param inputStream source stream
     * @return deserialized trie
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException          if reading or decompression fails
     */
    public static FrequencyTrie<String> read(final InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");

        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new BufferedInputStream(inputStream));
                DataInputStream dataInputStream = new DataInputStream(gzipInputStream)) {
            final FrequencyTrie<String> trie = FrequencyTrie.readFrom(dataInputStream, String[]::new, STRING_CODEC);

            LOGGER.log(Level.FINE, "Read compressed binary stemmer trie.");
            return trie;
        }
    }

    /**
     * Reads a GZip-compressed binary patch-command trie from an input stream with
     * an optional dense child lookup span override.
     * <p>
     * This is a runtime-only tuning parameter. The dense-span setting is not
     * persisted in the file and does not change the compiled metadata.
     * </p>
     *
     * @param inputStream      source stream
     * @param maxExpandedIndex dense lookup span override; negative values use
     *                         {@link FrequencyTrie#DEFAULT_MAX_EXPANDED_INDEX}
     * @return deserialized trie
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException          if reading or decompression fails
     */
    public static FrequencyTrie<String> read(final InputStream inputStream, final int maxExpandedIndex)
            throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");

        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new BufferedInputStream(inputStream));
                DataInputStream dataInputStream = new DataInputStream(gzipInputStream)) {
            final FrequencyTrie<String> trie = FrequencyTrie.readFrom(dataInputStream, String[]::new, STRING_CODEC,
                    maxExpandedIndex);

            LOGGER.log(Level.FINE, "Read compressed binary stemmer trie.");
            return trie;
        }
    }

    /**
     * Reads a compressed binary patch-command trie directly as compiled values from
     * a filesystem path.
     *
     * @param path source file
     * @return directly materialized compiled patch-command trie
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if reading, decompression, or command compilation
     *                              fails
     */
    /* default */ static FrequencyTrie<CompiledPatchCommand> readCompiled(final Path path) throws IOException {
        Objects.requireNonNull(path, PATH_PARAMETER);

        try (InputStream fileInputStream = Files.newInputStream(path)) {
            return readCompiled(fileInputStream);
        }
    }

    /**
     * Reads a compressed binary patch-command trie directly as compiled values from
     * a filesystem path with a dense child lookup span override.
     *
     * @param path             source file
     * @param maxExpandedIndex dense lookup span override; negative values use
     *                         {@link FrequencyTrie#DEFAULT_MAX_EXPANDED_INDEX}
     * @return directly materialized compiled patch-command trie
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if reading, decompression, or command compilation
     *                              fails
     */
    /* default */ static FrequencyTrie<CompiledPatchCommand> readCompiled(final Path path,
            final int maxExpandedIndex)
            throws IOException {
        Objects.requireNonNull(path, PATH_PARAMETER);

        try (InputStream fileInputStream = Files.newInputStream(path)) {
            return readCompiled(fileInputStream, maxExpandedIndex);
        }
    }

    /**
     * Reads a compressed binary patch-command trie directly as compiled values from
     * a filesystem path string.
     *
     * @param fileName source file name or path string
     * @return directly materialized compiled patch-command trie
     * @throws NullPointerException if {@code fileName} is {@code null}
     * @throws IOException          if reading, decompression, or command compilation
     *                              fails
     */
    /* default */ static FrequencyTrie<CompiledPatchCommand> readCompiled(final String fileName) throws IOException {
        Objects.requireNonNull(fileName, FILE_NAME_PARAMETER);
        return readCompiled(Path.of(fileName));
    }

    /**
     * Reads a compressed binary patch-command trie directly as compiled values from
     * a filesystem path string with a dense child lookup span override.
     *
     * @param fileName         source file name or path string
     * @param maxExpandedIndex dense lookup span override; negative values use
     *                         {@link FrequencyTrie#DEFAULT_MAX_EXPANDED_INDEX}
     * @return directly materialized compiled patch-command trie
     * @throws NullPointerException if {@code fileName} is {@code null}
     * @throws IOException          if reading, decompression, or command compilation
     *                              fails
     */
    /* default */ static FrequencyTrie<CompiledPatchCommand> readCompiled(final String fileName,
            final int maxExpandedIndex)
            throws IOException {
        Objects.requireNonNull(fileName, FILE_NAME_PARAMETER);
        return readCompiled(Path.of(fileName), maxExpandedIndex);
    }

    /**
     * Reads a compressed binary patch-command trie directly as compiled values from
     * an input stream.
     *
     * @param inputStream source stream
     * @return directly materialized compiled patch-command trie
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException          if reading, decompression, or command compilation
     *                              fails
     */
    /* default */ static FrequencyTrie<CompiledPatchCommand> readCompiled(final InputStream inputStream)
            throws IOException {
        return readCompiled(inputStream, -1);
    }

    /**
     * Reads a compressed binary patch-command trie directly as compiled values from
     * an input stream with a dense child lookup span override.
     *
     * @param inputStream      source stream
     * @param maxExpandedIndex dense lookup span override; negative values use
     *                         {@link FrequencyTrie#DEFAULT_MAX_EXPANDED_INDEX}
     * @return directly materialized compiled patch-command trie
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException          if reading, decompression, or command compilation
     *                              fails
     */
    /* default */ static FrequencyTrie<CompiledPatchCommand> readCompiled(final InputStream inputStream,
            final int maxExpandedIndex) throws IOException {
        return readCompiled(inputStream, maxExpandedIndex, CompiledPatchCommand::compile);
    }

    /**
     * Reads a compressed binary patch-command trie using a caller-supplied command
     * compiler.
     *
     * <p>
     * This package-private seam permits deterministic compilation-count testing
     * without global counters. Production callers use
     * {@link CompiledPatchCommand#compile(String, WordTraversalDirection)}.
     * </p>
     *
     * @param inputStream      source stream
     * @param maxExpandedIndex dense lookup span override
     * @param commandCompiler  compiler for one serialized command and traversal
     *                         direction
     * @return directly materialized compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if reading, decompression, or command compilation
     *                              fails
     */
    /* default */ static FrequencyTrie<CompiledPatchCommand> readCompiled(final InputStream inputStream,
            final int maxExpandedIndex,
            final BiFunction<String, WordTraversalDirection, CompiledPatchCommand> commandCompiler)
            throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        Objects.requireNonNull(commandCompiler, "commandCompiler");
        final CompiledPatchValueReader valueReader = new CompiledPatchValueReader(commandCompiler);

        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new BufferedInputStream(inputStream));
                DataInputStream dataInputStream = new DataInputStream(gzipInputStream)) {
            final FrequencyTrie<CompiledPatchCommand> trie = FrequencyTrie.readFromWithMetadata(dataInputStream,
                    CompiledPatchCommand[]::new, valueReader, maxExpandedIndex);

            LOGGER.log(Level.FINE, "Read compressed binary stemmer trie directly as compiled patch commands.");
            return trie;
        }
    }

    /**
     * Reads only metadata from a GZip-compressed binary patch-command trie stored
     * at a filesystem path.
     *
     * @param path source file
     * @return deserialized trie metadata
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if reading or decompression fails
     */
    public static TrieMetadata readMetadata(final Path path) throws IOException {
        Objects.requireNonNull(path, PATH_PARAMETER);
        return read(path).metadata();
    }

    /**
     * Reads only metadata from a GZip-compressed binary patch-command trie stored
     * at a filesystem path string.
     *
     * @param fileName source file name or path string
     * @return deserialized trie metadata
     * @throws NullPointerException if {@code fileName} is {@code null}
     * @throws IOException          if reading or decompression fails
     */
    public static TrieMetadata readMetadata(final String fileName) throws IOException {
        Objects.requireNonNull(fileName, FILE_NAME_PARAMETER);
        return readMetadata(Path.of(fileName));
    }

    /**
     * Reads only metadata from a GZip-compressed binary patch-command trie from an
     * input stream.
     *
     * @param inputStream source stream
     * @return deserialized trie metadata
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException          if reading or decompression fails
     */
    public static TrieMetadata readMetadata(final InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        return read(inputStream).metadata();
    }

    /**
     * Writes a GZip-compressed binary patch-command trie to a filesystem path.
     *
     * @param trie trie to persist
     * @param path target file
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if writing fails
     */
    public static void write(final FrequencyTrie<String> trie, final Path path) throws IOException {
        Objects.requireNonNull(trie, "trie");
        Objects.requireNonNull(path, PATH_PARAMETER);

        final Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (OutputStream fileOutputStream = Files.newOutputStream(path)) {
            write(trie, fileOutputStream);
        }
    }

    /**
     * Writes a GZip-compressed binary patch-command trie to a filesystem path
     * string.
     *
     * @param trie     trie to persist
     * @param fileName target file name or path string
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if writing fails
     */
    public static void write(final FrequencyTrie<String> trie, final String fileName) throws IOException {
        Objects.requireNonNull(fileName, FILE_NAME_PARAMETER);
        write(trie, Path.of(fileName));
    }

    /**
     * Writes a GZip-compressed binary patch-command trie to an output stream.
     *
     * @param trie         trie to persist
     * @param outputStream target stream
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if writing fails
     */
    public static void write(final FrequencyTrie<String> trie, final OutputStream outputStream) throws IOException {
        Objects.requireNonNull(trie, "trie");
        Objects.requireNonNull(outputStream, "outputStream");

        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(new BufferedOutputStream(outputStream));
                DataOutputStream dataOutputStream = new DataOutputStream(gzipOutputStream)) {
            trie.writeTo(dataOutputStream, STRING_CODEC);
        }

        LOGGER.log(Level.FINE, "Wrote compressed binary stemmer trie.");
    }

    /**
     * Binary stream codec for persisted patch-command strings.
     */
    private static final class StringValueStreamCodec implements FrequencyTrie.ValueStreamCodec<String> {

        /**
         * Creates a codec instance.
         */
        private StringValueStreamCodec() {
        }

        @Override
        public void write(final DataOutputStream dataOutput, final String value) throws IOException {
            dataOutput.writeUTF(value);
        }

        @Override
        public String read(final DataInputStream dataInput) throws IOException {
            return dataInput.readUTF();
        }
    }

    /**
     * Metadata-aware reader that compiles serialized patch commands into final trie
     * values.
     *
     * <p>
     * Version 7 value tables already contain distinct serialized values, so each
     * entry is compiled directly. Historical inline formats use the reader-local
     * equality cache to compile repeated serialized commands once. Neither the cache
     * nor this reader is retained after trie loading.
     * </p>
     */
    private static final class CompiledPatchValueReader
            implements FrequencyTrie.MetadataValueStreamReader<CompiledPatchCommand> {

        /**
         * Compiler used to materialize one final command.
         */
        private final BiFunction<String, WordTraversalDirection, CompiledPatchCommand> commandCompiler;

        /**
         * Compatibility cache used only by historical inline-value streams.
         */
        private final Map<String, CompiledPatchCommand> legacyCompiledCommands = new HashMap<>();

        /**
         * Creates one reader with a caller-supplied compiler.
         *
         * @param commandCompiler compiler for serialized patch commands
         */
        private CompiledPatchValueReader(
                final BiFunction<String, WordTraversalDirection, CompiledPatchCommand> commandCompiler) {
            this.commandCompiler = commandCompiler;
        }

        /**
         * Reads and compiles one serialized patch command.
         *
         * @param dataInput source data input
         * @param metadata  parsed trie metadata
         * @return final compiled patch command
         * @throws IOException if reading or command compilation fails
         */
        @Override
        public CompiledPatchCommand read(final DataInputStream dataInput, final TrieMetadata metadata)
                throws IOException {
            final String serializedPatch = dataInput.readUTF();
            if (FrequencyTrie.usesValueTableFormat(metadata)) {
                return compile(serializedPatch, metadata.traversalDirection());
            }

            final CompiledPatchCommand cachedCommand = this.legacyCompiledCommands.get(serializedPatch);
            if (cachedCommand != null) {
                return cachedCommand;
            }
            final CompiledPatchCommand compiledCommand = compile(serializedPatch, metadata.traversalDirection());
            this.legacyCompiledCommands.put(serializedPatch, compiledCommand);
            return compiledCommand;
        }

        /**
         * Compiles one serialized command and converts validation failures to
         * trust-boundary {@link IOException} instances.
         *
         * @param serializedPatch   serialized patch command
         * @param traversalDirection traversal direction from persisted metadata
         * @return compiled patch command
         * @throws IOException if the serialized command is invalid
         */
        private CompiledPatchCommand compile(final String serializedPatch,
                final WordTraversalDirection traversalDirection) throws IOException {
            try {
                return this.commandCompiler.apply(serializedPatch, traversalDirection);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid persisted patch command '" + boundedPatch(serializedPatch)
                        + "' for traversal direction " + traversalDirection + '.', exception);
            }
        }
    }

    /**
     * Returns a safely bounded patch-command representation for diagnostics.
     *
     * @param serializedPatch serialized patch command
     * @return complete or length-bounded diagnostic representation
     */
    private static String boundedPatch(final String serializedPatch) {
        if (serializedPatch.length() <= MAX_DIAGNOSTIC_PATCH_LENGTH) {
            return serializedPatch;
        }
        return serializedPatch.substring(0, MAX_DIAGNOSTIC_PATCH_LENGTH) + "... (length "
                + serializedPatch.length() + ')';
    }
}
