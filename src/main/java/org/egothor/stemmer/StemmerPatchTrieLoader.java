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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loader of patch-command tries from bundled stemmer dictionaries.
 *
 * <p>
 * Each dictionary is line-oriented. The first token on a line is interpreted as
 * the stem, and all following tokens are treated as known variants of that
 * stem.
 *
 * <p>
 * For each line, the loader inserts:
 * <ul>
 * <li>the stem itself mapped to the canonical no-op patch command
 * {@link PatchCommandEncoder#NOOP_PATCH}, when requested by the caller</li>
 * <li>every distinct variant mapped to the patch command transforming that
 * variant to the stem</li>
 * </ul>
 *
 * <p>
 * Parsing is delegated to {@link StemmerDictionaryParser}, which also supports
 * line remarks introduced by {@code #} or {@code //}.
 */
public final class StemmerPatchTrieLoader {

    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(StemmerPatchTrieLoader.class.getName());

    /**
     * Canonical no-op patch command used when the source and target are equal.
     */
    private static final String NOOP_PATCH_COMMAND = PatchCommandEncoder.NOOP_PATCH;

    /**
     * Utility class.
     */
    private StemmerPatchTrieLoader() {
        throw new AssertionError("No instances.");
    }

    /**
     * Supported bundled stemmer dictionaries.
     */
    public enum Language {

        /**
         * Danish.
         */
        DA_DK("da_dk"),

        /**
         * German.
         */
        DE_DE("de_de"),

        /**
         * Spanish.
         */
        ES_ES("es_es"),

        /**
         * French.
         */
        FR_FR("fr_fr"),

        /**
         * Italian.
         */
        IT_IT("it_it"),

        /**
         * Dutch.
         */
        NL_NL("nl_nl"),

        /**
         * Norwegian.
         */
        NO_NO("no_no"),

        /**
         * Portuguese.
         */
        PT_PT("pt_pt"),

        /**
         * Russian.
         */
        RU_RU("ru_ru"),

        /**
         * Swedish.
         */
        SV_SE("sv_se"),

        /**
         * English.
         */
        US_UK("us_uk"),

        /**
         * English professional dictionary.
         */
        US_UK_PROFI("us_uk.profi");

        /**
         * Resource directory name.
         */
        private final String resourceDirectory;

        /**
         * Creates a language constant.
         *
         * @param resourceDirectory resource directory name
         */
        Language(final String resourceDirectory) {
            this.resourceDirectory = resourceDirectory;
        }

        /**
         * Returns the classpath resource path of the stemmer dictionary.
         *
         * @return classpath resource path
         */
        public String resourcePath() {
            return this.resourceDirectory + "/stemmer";
        }

        /**
         * Returns the resource directory name.
         *
         * @return resource directory name
         */
        public String resourceDirectory() {
            return this.resourceDirectory;
        }
    }

    /**
     * Loads a bundled dictionary using explicit reduction settings.
     *
     * @param language          bundled language dictionary
     * @param storeOriginal     whether the stem itself should be inserted using the
     *                          canonical no-op patch command
     * @param reductionSettings reduction settings
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the dictionary cannot be found or read
     */
    public static FrequencyTrie<String> load(final Language language, final boolean storeOriginal,
            final ReductionSettings reductionSettings) throws IOException {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(reductionSettings, "reductionSettings");

        final String resourcePath = language.resourcePath();

        try (InputStream inputStream = openBundledResource(resourcePath);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return load(reader, resourcePath, storeOriginal, reductionSettings);
        }
    }

    /**
     * Loads a bundled dictionary using default settings for the supplied reduction
     * mode.
     *
     * @param language      bundled language dictionary
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param reductionMode reduction mode
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the dictionary cannot be found or read
     */
    public static FrequencyTrie<String> load(final Language language, final boolean storeOriginal,
            final ReductionMode reductionMode) throws IOException {
        Objects.requireNonNull(reductionMode, "reductionMode");
        return load(language, storeOriginal, ReductionSettings.withDefaults(reductionMode));
    }

    /**
     * Loads a dictionary from a filesystem path using explicit reduction settings.
     *
     * @param path              path to the dictionary file
     * @param storeOriginal     whether the stem itself should be inserted using the
     *                          canonical no-op patch command
     * @param reductionSettings reduction settings
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<String> load(final Path path, final boolean storeOriginal,
            final ReductionSettings reductionSettings) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(reductionSettings, "reductionSettings");

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return load(reader, path.toAbsolutePath().toString(), storeOriginal, reductionSettings);
        }
    }

    /**
     * Loads a dictionary from a filesystem path using default settings for the
     * supplied reduction mode.
     *
     * @param path          path to the dictionary file
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param reductionMode reduction mode
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<String> load(final Path path, final boolean storeOriginal,
            final ReductionMode reductionMode) throws IOException {
        Objects.requireNonNull(reductionMode, "reductionMode");
        return load(path, storeOriginal, ReductionSettings.withDefaults(reductionMode));
    }

    /**
     * Loads a dictionary from a filesystem path string using explicit reduction
     * settings.
     *
     * @param fileName          file name or path string
     * @param storeOriginal     whether the stem itself should be inserted using the
     *                          canonical no-op patch command
     * @param reductionSettings reduction settings
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<String> load(final String fileName, final boolean storeOriginal,
            final ReductionSettings reductionSettings) throws IOException {
        Objects.requireNonNull(fileName, "fileName");
        return load(Path.of(fileName), storeOriginal, reductionSettings);
    }

    /**
     * Loads a dictionary from a filesystem path string using default settings for
     * the supplied reduction mode.
     *
     * @param fileName      file name or path string
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param reductionMode reduction mode
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<String> load(final String fileName, final boolean storeOriginal,
            final ReductionMode reductionMode) throws IOException {
        Objects.requireNonNull(fileName, "fileName");
        return load(Path.of(fileName), storeOriginal, reductionMode);
    }

    /**
     * Parses one dictionary and builds the compiled trie.
     *
     * @param reader            dictionary reader
     * @param sourceDescription logical source description used for diagnostics
     * @param storeOriginal     whether the stem itself should be inserted using the
     *                          canonical no-op patch command
     * @param reductionSettings reduction settings
     * @return compiled patch-command trie
     * @throws IOException if parsing fails
     */
    private static FrequencyTrie<String> load(final BufferedReader reader, final String sourceDescription,
            final boolean storeOriginal, final ReductionSettings reductionSettings) throws IOException {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new, reductionSettings);
        final PatchCommandEncoder patchCommandEncoder = new PatchCommandEncoder();
        final int[] insertedMappings = new int[1];

        final StemmerDictionaryParser.ParseStatistics statistics = StemmerDictionaryParser.parse(reader,
                sourceDescription, (stem, variants, lineNumber) -> {
                    if (storeOriginal) {
                        builder.put(stem, NOOP_PATCH_COMMAND);
                        insertedMappings[0]++;
                    }

                    for (String variant : variants) {
                        if (!variant.equals(stem)) {
                            builder.put(variant, patchCommandEncoder.encode(variant, stem));
                            insertedMappings[0]++;
                        }
                    }
                });

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE,
                    "Loaded stemmer dictionary from {0}; insertedMappings={1}, lines={2}, entries={3}, ignoredLines={4}.",
                    new Object[] { sourceDescription, insertedMappings[0], statistics.lineCount(),
                            statistics.entryCount(), statistics.ignoredLineCount() });
        }

        return builder.build();
    }

    /**
     * Loads a GZip-compressed binary patch-command trie from a filesystem path.
     *
     * @param path path to the compressed binary trie file
     * @return compiled patch-command trie
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if the file cannot be opened, decompressed, or
     *                              read
     */
    public static FrequencyTrie<String> loadBinary(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return StemmerPatchTrieBinaryIO.read(path);
    }

    /**
     * Loads a GZip-compressed binary patch-command trie from a filesystem path
     * string.
     *
     * @param fileName file name or path string
     * @return compiled patch-command trie
     * @throws NullPointerException if {@code fileName} is {@code null}
     * @throws IOException          if the file cannot be opened, decompressed, or
     *                              read
     */
    public static FrequencyTrie<String> loadBinary(final String fileName) throws IOException {
        Objects.requireNonNull(fileName, "fileName");
        return StemmerPatchTrieBinaryIO.read(fileName);
    }

    /**
     * Loads a GZip-compressed binary patch-command trie from an input stream.
     *
     * @param inputStream source input stream
     * @return compiled patch-command trie
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException          if the stream cannot be decompressed or read
     */
    public static FrequencyTrie<String> loadBinary(final InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        return StemmerPatchTrieBinaryIO.read(inputStream);
    }

    /**
     * Saves a compiled patch-command trie as a GZip-compressed binary file.
     *
     * @param trie compiled trie
     * @param path target file
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if writing fails
     */
    public static void saveBinary(final FrequencyTrie<String> trie, final Path path) throws IOException {
        Objects.requireNonNull(trie, "trie");
        Objects.requireNonNull(path, "path");
        StemmerPatchTrieBinaryIO.write(trie, path);
    }

    /**
     * Saves a compiled patch-command trie as a GZip-compressed binary file.
     *
     * @param trie     compiled trie
     * @param fileName target file name or path string
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if writing fails
     */
    public static void saveBinary(final FrequencyTrie<String> trie, final String fileName) throws IOException {
        Objects.requireNonNull(trie, "trie");
        Objects.requireNonNull(fileName, "fileName");
        StemmerPatchTrieBinaryIO.write(trie, fileName);
    }

    /**
     * Opens a bundled resource from the classpath.
     *
     * @param resourcePath classpath resource path
     * @return opened input stream
     * @throws IOException if the resource cannot be found
     */
    private static InputStream openBundledResource(final String resourcePath) throws IOException {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final InputStream inputStream = classLoader.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Stemmer resource not found: " + resourcePath);
        }
        return inputStream;
    }
}
