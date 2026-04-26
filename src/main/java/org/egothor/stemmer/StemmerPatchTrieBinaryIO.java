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
import java.util.Objects;
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
        Objects.requireNonNull(path, "path");

        try (InputStream fileInputStream = Files.newInputStream(path)) {
            return read(fileInputStream);
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
        Objects.requireNonNull(fileName, "fileName");
        return read(Path.of(fileName));
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
     * Reads only metadata from a GZip-compressed binary patch-command trie stored
     * at a filesystem path.
     *
     * @param path source file
     * @return deserialized trie metadata
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if reading or decompression fails
     */
    public static TrieMetadata readMetadata(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
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
        Objects.requireNonNull(fileName, "fileName");
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
        Objects.requireNonNull(path, "path");

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
        Objects.requireNonNull(fileName, "fileName");
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
}
