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
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
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

    /* default */ static final String FILENAME_REQUIRED = "fileName required";

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
     * <p>
     * This overload applies the following implicit compilation defaults in addition
     * to the supplied {@code reductionSettings}:
     * </p>
     * <ul>
     * <li>traversal direction is derived from {@link Language#isRightToLeft()}
     * ({@link WordTraversalDirection#FORWARD} for right-to-left languages,
     * {@link WordTraversalDirection#BACKWARD} otherwise)</li>
     * <li>case processing mode is
     * {@link CaseProcessingMode#LOWERCASE_WITH_LOCALE_ROOT}</li>
     * <li>diacritic processing mode is {@link DiacriticProcessingMode#AS_IS}</li>
     * </ul>
     *
     * <p>
     * The resolved settings are persisted into {@link TrieMetadata} of the
     * resulting trie.
     * </p>
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
        final TrieMetadata metadata = metadataForCompilation(traversalDirectionOf(language), reductionSettings,
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT, DiacriticProcessingMode.AS_IS);
        return load(language, storeOriginal, metadata);
    }

    /**
     * Loads a bundled dictionary using explicit trie compilation metadata.
     *
     * <p>
     * All semantic compilation settings (reduction mode and thresholds, traversal
     * direction, case processing mode, and diacritic processing mode) are taken
     * from the supplied metadata object and are persisted unchanged in the
     * resulting trie.
     * </p>
     *
     * @param language      bundled language dictionary
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param metadata      trie metadata describing the compilation configuration
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the dictionary cannot be found or read
     */
    public static FrequencyTrie<String> load(final Language language, final boolean storeOriginal,
            final TrieMetadata metadata) throws IOException {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(metadata, "metadata");

        final String resourcePath = language.resourcePath();

        try (InputStream inputStream = openBundledResource(resourcePath);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return load(reader, resourcePath, storeOriginal, metadata);
        }
    }

    /**
     * Loads a bundled dictionary using default settings for the supplied reduction
     * mode.
     *
     * <p>
     * This overload is equivalent to calling
     * {@link #load(Language, boolean, ReductionSettings)} with
     * {@link ReductionSettings#withDefaults(ReductionMode)} and therefore uses the
     * same implicit defaults for traversal direction, case processing mode, and
     * diacritic processing mode.
     * </p>
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
     * <p>
     * This overload applies historical Egothor-compatible implicit defaults:
     * {@link WordTraversalDirection#BACKWARD},
     * {@link CaseProcessingMode#LOWERCASE_WITH_LOCALE_ROOT}, and
     * {@link DiacriticProcessingMode#AS_IS}. These settings are persisted in
     * resulting trie metadata.
     * </p>
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
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT, DiacriticProcessingMode.AS_IS);
    }

    /**
     * Loads a dictionary from a filesystem path using explicit reduction settings
     * and explicit traversal direction.
     *
     * <p>
     * Implicit defaults still apply for unspecified dimensions:
     * {@link CaseProcessingMode#LOWERCASE_WITH_LOCALE_ROOT} and
     * {@link DiacriticProcessingMode#AS_IS}.
     * </p>
     *
     * @param path               path to the dictionary file
     * @param storeOriginal      whether the stem itself should be inserted using
     *                           the canonical no-op patch command
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
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT, DiacriticProcessingMode.AS_IS);
    }

    /**
     * Loads a dictionary from a filesystem path using explicit reduction settings,
     * explicit traversal direction, and explicit case processing mode.
     *
     * <p>
     * This overload still defaults diacritic processing to
     * {@link DiacriticProcessingMode#AS_IS}.
     * </p>
     *
     * @param path               path to the dictionary file
     * @param storeOriginal      whether the stem itself should be inserted using
     *                           the canonical no-op patch command
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
        return load(path, storeOriginal, reductionSettings, traversalDirection, caseProcessingMode,
                DiacriticProcessingMode.AS_IS);
    }

    /**
     * Loads a dictionary from a filesystem path using explicit reduction settings,
     * traversal direction, case processing mode, and diacritic processing mode.
     *
     * @param path                    path to the dictionary file
     * @param storeOriginal           whether the stem itself should be inserted
     *                                using the canonical no-op patch command
     * @param reductionSettings       reduction settings
     * @param traversalDirection      traversal direction used for both trie keys
     *                                and patch commands
     * @param caseProcessingMode      case processing mode used during dictionary
     *                                parsing
     * @param diacriticProcessingMode diacritic processing mode used during
     *                                dictionary parsing
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<String> load(final Path path, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final WordTraversalDirection traversalDirection,
            final CaseProcessingMode caseProcessingMode, final DiacriticProcessingMode diacriticProcessingMode)
            throws IOException {
        Objects.requireNonNull(path, "path");
        final TrieMetadata metadata = metadataForCompilation(traversalDirection, reductionSettings, caseProcessingMode,
                diacriticProcessingMode);
        return load(path, storeOriginal, metadata);
    }

    /**
     * Loads a dictionary from a filesystem path using explicit trie compilation
     * metadata.
     *
     * <p>
     * The supplied metadata is the authoritative source of trie compilation
     * semantics. Callers should ensure metadata matches how they expect to query
     * the trie (for example, with or without lowercasing or diacritic stripping).
     * </p>
     *
     * @param path          path to the dictionary file
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param metadata      trie metadata describing the compilation configuration
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<String> load(final Path path, final boolean storeOriginal, final TrieMetadata metadata)
            throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(metadata, "metadata");

        try (InputStream inputStream = openDictionaryInputStream(path);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return load(reader, path.toAbsolutePath().toString(), storeOriginal, metadata);
        }
    }

    /**
     * Loads a dictionary from a filesystem path using default settings for the
     * supplied reduction mode.
     *
     * <p>
     * This overload is equivalent to calling
     * {@link #load(Path, boolean, ReductionSettings)} with
     * {@link ReductionSettings#withDefaults(ReductionMode)} and therefore uses
     * implicit defaults ({@link WordTraversalDirection#BACKWARD},
     * {@link CaseProcessingMode#LOWERCASE_WITH_LOCALE_ROOT},
     * {@link DiacriticProcessingMode#AS_IS}).
     * </p>
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
     * <p>
     * Same semantics as {@link #load(Path, boolean, ReductionSettings)} including
     * implicit defaults ({@link WordTraversalDirection#BACKWARD},
     * {@link CaseProcessingMode#LOWERCASE_WITH_LOCALE_ROOT},
     * {@link DiacriticProcessingMode#AS_IS}).
     * </p>
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
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return load(Path.of(fileName), storeOriginal, reductionSettings);
    }

    /**
     * Loads a dictionary from a filesystem path string using explicit reduction
     * settings and explicit traversal direction.
     *
     * <p>
     * Same semantics as
     * {@link #load(Path, boolean, ReductionSettings, WordTraversalDirection)}.
     * Implicit defaults remain
     * {@link CaseProcessingMode#LOWERCASE_WITH_LOCALE_ROOT} and
     * {@link DiacriticProcessingMode#AS_IS}.
     * </p>
     *
     * @param fileName           file name or path string
     * @param storeOriginal      whether the stem itself should be inserted using
     *                           the canonical no-op patch command
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
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return load(Path.of(fileName), storeOriginal, reductionSettings, traversalDirection,
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
    }

    /**
     * Loads a dictionary from a filesystem path string using explicit reduction
     * settings, explicit traversal direction, and explicit case processing mode.
     *
     * <p>
     * Same semantics as
     * {@link #load(Path, boolean, ReductionSettings, WordTraversalDirection, CaseProcessingMode)}.
     * Implicit default remains {@link DiacriticProcessingMode#AS_IS}.
     * </p>
     *
     * @param fileName           file name or path string
     * @param storeOriginal      whether the stem itself should be inserted using
     *                           the canonical no-op patch command
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
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return load(Path.of(fileName), storeOriginal, reductionSettings, traversalDirection, caseProcessingMode,
                DiacriticProcessingMode.AS_IS);
    }

    /**
     * Loads a dictionary from a filesystem path string using explicit reduction
     * settings, explicit traversal direction, explicit case processing mode, and
     * explicit diacritic processing mode.
     *
     * @param fileName                file name or path string
     * @param storeOriginal           whether the stem itself should be inserted
     *                                using the canonical no-op patch command
     * @param reductionSettings       reduction settings
     * @param traversalDirection      traversal direction used for both trie keys
     *                                and patch commands
     * @param caseProcessingMode      case processing mode used during dictionary
     *                                parsing
     * @param diacriticProcessingMode diacritic processing mode used during
     *                                dictionary parsing
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<String> load(final String fileName, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final WordTraversalDirection traversalDirection,
            final CaseProcessingMode caseProcessingMode, final DiacriticProcessingMode diacriticProcessingMode)
            throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return load(Path.of(fileName), storeOriginal, reductionSettings, traversalDirection, caseProcessingMode,
                diacriticProcessingMode);
    }

    /**
     * Loads a dictionary from a filesystem path string using explicit trie
     * compilation metadata.
     *
     * <p>
     * Same semantics as {@link #load(Path, boolean, TrieMetadata)}.
     * </p>
     *
     * @param fileName      file name or path string
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param metadata      trie metadata describing the compilation configuration
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<String> load(final String fileName, final boolean storeOriginal,
            final TrieMetadata metadata) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return load(Path.of(fileName), storeOriginal, metadata);
    }

    /**
     * Loads a dictionary from a filesystem path string using default settings for
     * the supplied reduction mode.
     *
     * <p>
     * Equivalent to {@link #load(Path, boolean, ReductionMode)} and therefore uses
     * implicit defaults ({@link WordTraversalDirection#BACKWARD},
     * {@link CaseProcessingMode#LOWERCASE_WITH_LOCALE_ROOT},
     * {@link DiacriticProcessingMode#AS_IS}).
     * </p>
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
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return load(Path.of(fileName), storeOriginal, reductionMode);
    }

    /**
     * Parses one dictionary and builds the compiled trie.
     *
     * @param reader            dictionary reader
     * @param sourceDescription logical source description used for diagnostics
     * @param storeOriginal     whether the stem itself should be inserted using the
     *                          canonical no-op patch command
     * @param metadata          trie metadata used to drive all compilation settings
     * @return compiled patch-command trie
     * @throws IOException if parsing fails
     */
    private static FrequencyTrie<String> load(final BufferedReader reader, final String sourceDescription,
            final boolean storeOriginal, final TrieMetadata metadata) throws IOException {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new,
                metadata.reductionSettings(), metadata.traversalDirection(), metadata.caseProcessingMode(),
                metadata.diacriticProcessingMode());
        final PatchCommandEncoder patchCommandEncoder = PatchCommandEncoder.builder()
                .traversalDirection(metadata.traversalDirection()).build();
        final int[] insertedMappings = new int[1];

        final StemmerDictionaryParser.ParseStatistics statistics = StemmerDictionaryParser.parse(reader,
                sourceDescription, metadata.caseProcessingMode(), (stem, variants, lineNumber) -> {
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
                    "Loaded stemmer dictionary from {0}; insertedMappings={1}, lines={2}, entries={3}, ignoredLines={4}, metadata={5}.",
                    new Object[] { sourceDescription, insertedMappings[0], statistics.lineCount(),
                            statistics.entryCount(), statistics.ignoredLineCount(), metadata.toTextBlock() });
        }

        return builder.build();
    }

    private static TrieMetadata metadataForCompilation(final WordTraversalDirection traversalDirection,
            final ReductionSettings reductionSettings, final CaseProcessingMode caseProcessingMode,
            final DiacriticProcessingMode diacriticProcessingMode) {
        Objects.requireNonNull(traversalDirection, "traversalDirection");
        Objects.requireNonNull(reductionSettings, "reductionSettings");
        Objects.requireNonNull(caseProcessingMode, "caseProcessingMode");
        Objects.requireNonNull(diacriticProcessingMode, "diacriticProcessingMode");
        return TrieMetadata.forCompilation(traversalDirection, reductionSettings, diacriticProcessingMode,
                caseProcessingMode);
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
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
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
     * Loads only persisted metadata from a GZip-compressed binary patch-command
     * trie file.
     *
     * @param path path to the compressed binary trie file
     * @return persisted trie metadata
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if the file cannot be opened, decompressed, or
     *                              read
     */
    public static TrieMetadata loadBinaryMetadata(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return StemmerPatchTrieBinaryIO.readMetadata(path);
    }

    /**
     * Loads only persisted metadata from a GZip-compressed binary patch-command
     * trie file.
     *
     * @param fileName file name or path string
     * @return persisted trie metadata
     * @throws NullPointerException if {@code fileName} is {@code null}
     * @throws IOException          if the file cannot be opened, decompressed, or
     *                              read
     */
    public static TrieMetadata loadBinaryMetadata(final String fileName) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return StemmerPatchTrieBinaryIO.readMetadata(fileName);
    }

    /**
     * Loads only persisted metadata from a GZip-compressed binary patch-command
     * trie stream.
     *
     * @param inputStream source input stream
     * @return persisted trie metadata
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException          if the stream cannot be decompressed or read
     */
    public static TrieMetadata loadBinaryMetadata(final InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        return StemmerPatchTrieBinaryIO.readMetadata(inputStream);
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
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
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
