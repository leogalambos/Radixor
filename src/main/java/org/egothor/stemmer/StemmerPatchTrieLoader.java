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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 * Loader of patch-command tries from bundled stemmer dictionaries.
 *
 * <p>
 * Each dictionary is line-oriented and uses a tab-separated values layout. The
 * first column on a line is interpreted as the stem, and all following
 * tab-separated columns are treated as known variants of that stem.
 *
 * <p>
 * For each line, the loader inserts:
 * <ul>
 * <li>the stem itself mapped to the canonical no-op patch command
 * {@link PatchCommandEncoder#NOOP_PATCH}, when requested by the caller</li>
 * <li>every distinct variant mapped to the patch command transforming that
 * variant to the stem using the traversal direction implied by the selected
 * language or loader overload</li>
 * </ul>
 *
 * <p>
 * Parsing is delegated to {@link StemmerDictionaryParser}, which also supports
 * line remarks introduced by {@code #} or {@code //} and ignores dictionary
 * items containing Unicode whitespace characters while reporting them through
 * aggregated warning log records.
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
     *
     * <p>
     * Each language constant defines:
     * </p>
     * <ul>
     * <li>the resource directory name used under the bundled resources tree</li>
     * <li>whether the language is written right-to-left</li>
     * </ul>
     *
     * <p>
     * The right-to-left flag is intended for consumers that need to decide whether
     * affix-oriented processing should conceptually traverse words from the visual
     * end or from the logical beginning of the stored form.
     * </p>
     */
    public enum Language {

        /**
         * Czech.
         */
        CS_CZ("cs_cz", false),

        /**
         * Danish.
         */
        DA_DK("da_dk", false),

        /**
         * German.
         */
        DE_DE("de_de", false),

        /**
         * Spanish.
         */
        ES_ES("es_es", false),

        /**
         * Persian.
         */
        FA_IR("fa_ir", true),

        /**
         * Finnish.
         */
        FI_FI("fi_fi", false),

        /**
         * French.
         */
        FR_FR("fr_fr", false),

        /**
         * Hebrew.
         */
        HE_IL("he_il", true),

        /**
         * Hungarian.
         */
        HU_HU("hu_hu", false),

        /**
         * Italian.
         */
        IT_IT("it_it", false),

        /**
         * Norwegian Bokmål.
         */
        NB_NO("nb_no", false),

        /**
         * Dutch.
         */
        NL_NL("nl_nl", false),

        /**
         * Norwegian Nynorsk.
         */
        NN_NO("nn_no", false),

        /**
         * Polish.
         */
        PL_PL("pl_pl", false),

        /**
         * Portuguese.
         */
        PT_PT("pt_pt", false),

        /**
         * Russian.
         */
        RU_RU("ru_ru", false),

        /**
         * Swedish.
         */
        SV_SE("sv_se", false),

        /**
         * Ukrainian.
         */
        UK_UA("uk_ua", false),

        /**
         * English.
         */
        US_UK("us_uk", false),

        /**
         * Yiddish.
         */
        YI("yi", true);

        /**
         * Resource directory name.
         */
        private final String resourceDirectory;

        /**
         * Whether the language is written right-to-left.
         */
        private final boolean rightToLeft;

        /**
         * Creates a language constant.
         *
         * @param resourceDirectory resource directory name
         * @param rightToLeft       whether the language is written right-to-left
         */
        Language(final String resourceDirectory, final boolean rightToLeft) {
            this.resourceDirectory = resourceDirectory;
            this.rightToLeft = rightToLeft;
        }

        /**
         * Returns the classpath resource path of the bundled stemmer dictionary.
         *
         * @return classpath resource path
         */
        public String resourcePath() {
            return this.resourceDirectory + "/stemmer.gz";
        }

        /**
         * Returns the resource directory name.
         *
         * @return resource directory name
         */
        public String resourceDirectory() {
            return this.resourceDirectory;
        }

        /**
         * Returns whether the language is written right-to-left.
         *
         * <p>
         * This flag can be used by trie-building and lookup logic to decide whether
         * suffix-oriented traversal should operate on the stored word form as-is rather
         * than by reversing the logical character sequence.
         * </p>
         *
         * @return {@code true} when the language is written right-to-left, otherwise
         *         {@code false}
         */
        public boolean isRightToLeft() {
            return this.rightToLeft;
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
            return load(reader, resourcePath, storeOriginal, reductionSettings, traversalDirectionOf(language),
                    CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
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
        return load(path, storeOriginal, reductionSettings, WordTraversalDirection.BACKWARD,
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
    }

    /**
     * Loads a dictionary from a filesystem path using explicit reduction settings
     * and explicit traversal direction.
     *
     * @param path               path to the dictionary file
     * @param storeOriginal      whether the stem itself should be inserted using the
     *                           canonical no-op patch command
     * @param reductionSettings  reduction settings
     * @param traversalDirection traversal direction used for both trie keys and
     *                           patch commands
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<String> load(final Path path, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final WordTraversalDirection traversalDirection)
            throws IOException {
        return load(path, storeOriginal, reductionSettings, traversalDirection,
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
    }

    /**
     * Loads a dictionary from a filesystem path using explicit reduction settings,
     * explicit traversal direction, and explicit case processing mode.
     *
     * @param path               path to the dictionary file
     * @param storeOriginal      whether the stem itself should be inserted using the
     *                           canonical no-op patch command
     * @param reductionSettings  reduction settings
     * @param traversalDirection traversal direction used for both trie keys and
     *                           patch commands
     * @param caseProcessingMode case processing mode used during dictionary parsing
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<String> load(final Path path, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final WordTraversalDirection traversalDirection,
            final CaseProcessingMode caseProcessingMode) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(reductionSettings, "reductionSettings");
        Objects.requireNonNull(traversalDirection, "traversalDirection");
        Objects.requireNonNull(caseProcessingMode, "caseProcessingMode");

        try (InputStream inputStream = openDictionaryInputStream(path);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return load(reader, path.toAbsolutePath().toString(), storeOriginal, reductionSettings,
                    traversalDirection, caseProcessingMode);
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
     * Loads a dictionary from a filesystem path string using explicit reduction
     * settings and explicit traversal direction.
     *
     * @param fileName           file name or path string
     * @param storeOriginal      whether the stem itself should be inserted using the
     *                           canonical no-op patch command
     * @param reductionSettings  reduction settings
     * @param traversalDirection traversal direction used for both trie keys and
     *                           patch commands
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<String> load(final String fileName, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final WordTraversalDirection traversalDirection)
            throws IOException {
        Objects.requireNonNull(fileName, "fileName");
        return load(Path.of(fileName), storeOriginal, reductionSettings, traversalDirection,
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
    }

    /**
     * Loads a dictionary from a filesystem path string using explicit reduction
     * settings, explicit traversal direction, and explicit case processing mode.
     *
     * @param fileName           file name or path string
     * @param storeOriginal      whether the stem itself should be inserted using the
     *                           canonical no-op patch command
     * @param reductionSettings  reduction settings
     * @param traversalDirection traversal direction used for both trie keys and
     *                           patch commands
     * @param caseProcessingMode case processing mode used during dictionary parsing
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<String> load(final String fileName, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final WordTraversalDirection traversalDirection,
            final CaseProcessingMode caseProcessingMode) throws IOException {
        Objects.requireNonNull(fileName, "fileName");
        return load(Path.of(fileName), storeOriginal, reductionSettings, traversalDirection, caseProcessingMode);
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
            final boolean storeOriginal, final ReductionSettings reductionSettings,
            final WordTraversalDirection traversalDirection, final CaseProcessingMode caseProcessingMode)
            throws IOException {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new, reductionSettings,
                traversalDirection, caseProcessingMode);
        final PatchCommandEncoder patchCommandEncoder = new PatchCommandEncoder(traversalDirection);
        final int[] insertedMappings = new int[1];

        final StemmerDictionaryParser.ParseStatistics statistics = StemmerDictionaryParser.parse(reader,
                sourceDescription, caseProcessingMode, (stem, variants, lineNumber) -> {
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
                    "Loaded stemmer dictionary from {0}; insertedMappings={1}, lines={2}, entries={3}, ignoredLines={4}, traversalDirection={5}.",
                    new Object[] { sourceDescription, insertedMappings[0], statistics.lineCount(),
                            statistics.entryCount(), statistics.ignoredLineCount(), traversalDirection });
        }

        return builder.build();
    }


    /**
     * Resolves the traversal direction implied by a bundled language definition.
     *
     * @param language bundled language
     * @return traversal direction to use for that language
     */
    private static WordTraversalDirection traversalDirectionOf(final Language language) {
        return language.isRightToLeft() ? WordTraversalDirection.FORWARD : WordTraversalDirection.BACKWARD;
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
     * Opens one filesystem dictionary input stream.
     *
     * <p>
     * Plain-text dictionaries are returned as-is. GZip-compressed dictionaries are
     * detected from the stream header rather than from the file extension so that
     * callers may provide arbitrary temporary file names without changing the
     * loading contract.
     * </p>
     *
     * @param path dictionary file path
     * @return opened dictionary stream, transparently decompressing GZip inputs
     * @throws IOException if the file cannot be opened
     */
    private static InputStream openDictionaryInputStream(final Path path) throws IOException {
        final PushbackInputStream pushbackInputStream = new PushbackInputStream(
                new BufferedInputStream(Files.newInputStream(path)), 2);
        final byte[] header = pushbackInputStream.readNBytes(2);

        if (header.length > 0) {
            pushbackInputStream.unread(header);
        }

        if (header.length == 2 && (header[0] & 0xFF) == 0x1F && (header[1] & 0xFF) == 0x8B) {
            return new GZIPInputStream(pushbackInputStream);
        }

        return pushbackInputStream;
    }

    /**
     * Opens a bundled resource from the classpath.
     *
     * @param resourcePath classpath resource path
     * @return opened input stream
     * @throws IOException if the resource cannot be found
     */
    /* default */ static InputStream openBundledResource(final String resourcePath) throws IOException {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final InputStream inputStream = classLoader.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Stemmer resource not found: " + resourcePath);
        }
        return new GZIPInputStream(inputStream);
    }
}
