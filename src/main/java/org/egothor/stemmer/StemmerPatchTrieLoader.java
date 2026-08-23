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
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
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
@SuppressWarnings({ "PMD.ExcessivePublicCount", "PMD.TooManyMethods" })
public final class StemmerPatchTrieLoader {

    /* default */ static final String FILENAME_REQUIRED = "fileName required";
    private static final String PARAMETER_PATH = "path";

    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(StemmerPatchTrieLoader.class.getName());

    /**
     * Canonical no-op patch command used when the source and target are equal.
     */
    private static final String NOOP_PATCH_COMMAND = PatchCommandEncoder.NOOP_PATCH;

    /** Default traversal for natural-language suffix models. */
    private static final WordTraversalDirection SUFFIX_TRAVERSAL_DIRECTION = WordTraversalDirection.BACKWARD;

    /**
     * Utility class.
     */
    private StemmerPatchTrieLoader() {
        throw new AssertionError("No instances.");
    }

    /**
     * Supported language identities and their stable default model mappings.
     *
     * <p>
     * Each language constant defines:
     * </p>
     * <ul>
     * <li>a deprecated legacy resource-directory name</li>
     * <li>the stable default model ID used by language-oriented loading</li>
     * <li>whether the language is written right-to-left</li>
     * </ul>
     *
     * <p>
     * The right-to-left flag is orthographic metadata for presentation and user
     * interface decisions. It does not select trie traversal: natural-language
     * suffixes occupy the end of the stored character sequence in every supported
     * script.
     * </p>
     */
    public enum Language {

        /**
         * Czech.
         */
        CS_CZ("cs_cz", "cs-cz-default", false),

        /**
         * Danish.
         */
        DA_DK("da_dk", "da-dk-default", false),

        /**
         * German.
         */
        DE_DE("de_de", "de-de-default", false),

        /**
         * Spanish.
         */
        ES_ES("es_es", "es-es-default", false),

        /**
         * Persian.
         */
        FA_IR("fa_ir", "fa-ir-default", true),

        /**
         * Finnish.
         */
        FI_FI("fi_fi", "fi-fi-default", false),

        /**
         * French.
         */
        FR_FR("fr_fr", "fr-fr-default", false),

        /**
         * Hebrew.
         */
        HE_IL("he_il", "he-il-default", true),

        /**
         * Hungarian.
         */
        HU_HU("hu_hu", "hu-hu-default", false),

        /**
         * Italian.
         */
        IT_IT("it_it", "it-it-default", false),

        /**
         * Norwegian Bokmål.
         */
        NB_NO("nb_no", "nb-no-default", false),

        /**
         * Dutch.
         */
        NL_NL("nl_nl", "nl-nl-default", false),

        /**
         * Norwegian Nynorsk.
         */
        NN_NO("nn_no", "nn-no-default", false),

        /**
         * Polish.
         */
        PL_PL("pl_pl", "pl-pl-unimorph", false),

        /**
         * Portuguese.
         */
        PT_PT("pt_pt", "pt-pt-default", false),

        /**
         * Russian.
         */
        RU_RU("ru_ru", "ru-ru-default", false),

        /**
         * Swedish.
         */
        SV_SE("sv_se", "sv-se-default", false),

        /**
         * Ukrainian.
         */
        UK_UA("uk_ua", "uk-ua-default", false),

        /**
         * English.
         */
        US_UK("us_uk", "us-uk-default", false),

        /**
         * Yiddish.
         */
        YI("yi", "yi-default", true);

        /**
         * Resource directory name.
         */
        private final String resourceDirectory;

        /** Stable identifier of the documented default model. */
        private final String defaultModelId;

        /**
         * Whether the language is written right-to-left.
         */
        private final boolean rightToLeft;

        /**
         * Creates a language constant.
         *
         * @param resourceDirectory deprecated legacy resource directory name
         * @param defaultModelId    stable default model identifier
         * @param rightToLeft       whether the language is written right-to-left
         */
        Language(final String resourceDirectory, final String defaultModelId, final boolean rightToLeft) {
            this.resourceDirectory = resourceDirectory;
            this.defaultModelId = defaultModelId;
            this.rightToLeft = rightToLeft;
        }

        /**
         * Returns the conventional resource path of this language's default model.
         *
         * <p>Production loading resolves descriptors through
         * {@link StemmerModelRegistry}; it does not use this method for discovery or
         * selection.</p>
         *
         * @return classpath resource path
         */
        @Deprecated(since = "4.0.0", forRemoval = false)
        public String resourcePath() {
            return "org/egothor/stemmer/models/" + this.defaultModelId + "/stemmer.gz";
        }

        /**
         * Returns the stable identifier selected by language-oriented loader methods.
         *
         * @return exact model ID, independent of classpath ordering
         */
        public String defaultModelId() {
            return this.defaultModelId;
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
         * This flag describes writing direction only. It does not imply a
         * {@link WordTraversalDirection}.
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
     * Loads the language's registered default model using explicit reduction settings.
     *
     * <p>
     * This overload applies the following implicit compilation defaults in addition
     * to the supplied {@code reductionSettings}:
     * </p>
     * <ul>
     * <li>traversal direction is {@link WordTraversalDirection#BACKWARD}, so
     * suffixes are processed from the end of the stored character sequence</li>
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
     * @param language          language whose stable default model is required
     * @param storeOriginal     whether the stem itself should be inserted using the
     *                          canonical no-op patch command
     * @param reductionSettings reduction settings
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the dictionary cannot be found or read
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(Language, boolean, ReductionSettings)} so
     *             patch commands are represented as {@link CompiledPatchCommand}
     *             values instead of reparsed {@link String} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final Language language, final boolean storeOriginal,
            final ReductionSettings reductionSettings) throws IOException {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(reductionSettings, "reductionSettings");
        final TrieMetadata metadata = metadataForCompilation(SUFFIX_TRAVERSAL_DIRECTION, reductionSettings,
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT, DiacriticProcessingMode.AS_IS);
        return load(language, storeOriginal, metadata);
    }

    /**
     * Loads the language's registered default model and returns a runtime-specialized trie whose
     * values are compiled patch commands.
     *
     * <p>
     * The text dictionary is still compiled through the canonical serialized
     * patch-command representation. The returned trie replaces each stored
     * serialized patch command with a {@link CompiledPatchCommand} so repeated
     * runtime stemming does not parse patch-command strings.
     * </p>
     *
     * @param language          language whose stable default model is required
     * @param storeOriginal     whether the stem itself should be inserted using the
     *                          canonical no-op patch command
     * @param reductionSettings reduction settings
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the dictionary cannot be found or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final Language language,
            final boolean storeOriginal, final ReductionSettings reductionSettings) throws IOException {
        return compilePatchTrie(load(language, storeOriginal, reductionSettings));
    }

    /**
     * Loads the language's registered default model using explicit trie compilation metadata.
     *
     * <p>
     * All semantic compilation settings (reduction mode and thresholds, traversal
     * direction, case processing mode, and diacritic processing mode) are taken
     * from the supplied metadata object and are persisted unchanged in the
     * resulting trie.
     * </p>
     *
     * @param language      language whose stable default model is required
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param metadata      trie metadata describing the compilation configuration
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the dictionary cannot be found or read
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(Language, boolean, TrieMetadata)} so patch
     *             commands are represented as {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final Language language, final boolean storeOriginal,
            final TrieMetadata metadata) throws IOException {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(metadata, "metadata");

        final StemmerModelDescriptor descriptor = StemmerModelRegistry.fromContextClassLoader().requireDefault(language);
        return load(descriptor, storeOriginal, metadata);
    }

    /**
     * Loads an explicitly selected descriptor using trie compilation metadata.
     *
     * <p>The method opens the descriptor's namespaced resource through its
     * discovering class loader, verifies SHA-256 over the compressed bytes,
     * decompresses the GZip UTF-8 dictionary, parses it, and builds a trie. Each
     * invocation performs this work and returns serialized patch-command values.</p>
     *
     * @param descriptor exact validated model descriptor
     * @param storeOriginal whether canonical stems receive no-op mappings
     * @param metadata trie compilation configuration
     * @return newly built trie containing serialized patch-command strings
     * @throws NullPointerException if {@code descriptor} or {@code metadata} is {@code null}
     * @throws IOException if the compressed dictionary cannot be read or decompressed
     * @throws StemmerModelIntegrityException if the resource is missing or its checksum differs
     */
    public static FrequencyTrie<String> load(final StemmerModelDescriptor descriptor, final boolean storeOriginal,
            final TrieMetadata metadata) throws IOException {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(metadata, "metadata");
        try (InputStream inputStream = openModelResource(descriptor);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return load(reader, descriptor.resource(), storeOriginal, metadata);
        }
    }

    /**
     * Loads one exact model descriptor and returns a runtime-specialized trie.
     *
     * <p>The descriptor's compressed dictionary is integrity-checked, fully
     * parsed, reduced with the supplied mode, and converted to immutable
     * {@link CompiledPatchCommand} values. The method does not consult a language
     * default and does not cache the constructed trie. Applications should retain
     * the result for their intended runtime scope. Constructing exceptionally
     * large models can require substantial temporary heap.</p>
     *
     * @param descriptor exact validated model descriptor
     * @param storeOriginal whether canonical stems receive no-op mappings
     * @param reductionMode reduction mode applied during trie construction
     * @return newly constructed trie containing compiled patch commands
     * @throws NullPointerException if {@code descriptor} or {@code reductionMode} is {@code null}
     * @throws IOException if the compressed dictionary cannot be read or decompressed
     * @throws StemmerModelIntegrityException if the resource is missing or its checksum differs
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final StemmerModelDescriptor descriptor,
            final boolean storeOriginal, final ReductionMode reductionMode) throws IOException {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(reductionMode, "reductionMode");
        final TrieMetadata metadata = metadataForCompilation(SUFFIX_TRAVERSAL_DIRECTION,
                ReductionSettings.withDefaults(reductionMode), CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT,
                DiacriticProcessingMode.AS_IS);
        return compilePatchTrie(load(descriptor, storeOriginal, metadata));
    }

    /**
     * Loads an exact model ID through a newly discovered context-class-loader registry.
     *
     * <p>This method is distinct from {@code load(String, ...)}, whose string is a
     * filesystem path. Selection is exact and never falls back to another model for
     * the same language.</p>
     *
     * @param modelId exact stable model identifier
     * @param storeOriginal whether canonical stems receive no-op mappings
     * @param metadata trie compilation configuration
     * @return newly built trie containing serialized patch-command strings
     * @throws NullPointerException if {@code modelId} or {@code metadata} is {@code null}
     * @throws IOException if discovery or resource reading fails
     * @throws StemmerModelNotFoundException if the model is not visible
     * @throws DuplicateStemmerModelException if discovery finds duplicate IDs
     * @throws UnsupportedStemmerModelFormatException if discovery finds an unsupported format
     * @throws StemmerModelIntegrityException if metadata or resource integrity is invalid
     */
    public static FrequencyTrie<String> loadModel(final String modelId, final boolean storeOriginal,
            final TrieMetadata metadata) throws IOException {
        return load(StemmerModelRegistry.fromContextClassLoader().require(modelId), storeOriginal, metadata);
    }

    /** Opens, integrity-checks, and decompresses a descriptor-backed dictionary. */
    @SuppressWarnings("PMD.CloseResource")
    private static InputStream openModelResource(final StemmerModelDescriptor descriptor) throws IOException {
        final InputStream unresolvedResource = descriptor.classLoader().getResourceAsStream(descriptor.resource());
        if (unresolvedResource == null) {
            throw new StemmerModelIntegrityException("Model resource is missing: " + descriptor.resource());
        }
        final byte[] bytes;
        try (InputStream resource = unresolvedResource) {
            bytes = resource.readAllBytes();
        }
        final String checksum;
        try {
            checksum = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new StemmerModelIntegrityException("The required SHA-256 algorithm is unavailable.", exception);
        }
        if (!checksum.equals(descriptor.sha256())) {
            throw new StemmerModelIntegrityException("Checksum mismatch for model '" + descriptor.id() + "' at "
                    + descriptor.resource() + ": expected " + descriptor.sha256() + " but found " + checksum + ".");
        }
        return new GZIPInputStream(new java.io.ByteArrayInputStream(bytes));
    }

    /**
     * Loads the language's registered default model using explicit trie compilation metadata and
     * returns a runtime-specialized trie whose values are compiled patch commands.
     *
     * @param language      language whose stable default model is required
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param metadata      trie metadata describing the compilation configuration
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the dictionary cannot be found or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final Language language,
            final boolean storeOriginal, final TrieMetadata metadata) throws IOException {
        return compilePatchTrie(load(language, storeOriginal, metadata));
    }

    /**
     * Loads the language's registered default model using settings for the supplied reduction
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
     * @param language      language whose stable default model is required
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param reductionMode reduction mode
     * @return compiled patch-command trie
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the dictionary cannot be found or read
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(Language, boolean, ReductionMode)} so patch
     *             commands are represented as {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final Language language, final boolean storeOriginal,
            final ReductionMode reductionMode) throws IOException {
        Objects.requireNonNull(reductionMode, "reductionMode");
        return load(language, storeOriginal, ReductionSettings.withDefaults(reductionMode));
    }

    /**
     * Loads the language's registered default model using settings for the supplied reduction
     * mode and returns a runtime-specialized trie whose values are compiled patch
     * commands.
     *
     * @param language      language whose stable default model is required
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param reductionMode reduction mode
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the dictionary cannot be found or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final Language language,
            final boolean storeOriginal, final ReductionMode reductionMode) throws IOException {
        return compilePatchTrie(load(language, storeOriginal, reductionMode));
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
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(Path, boolean, ReductionSettings)} so patch
     *             commands are represented as {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final Path path, final boolean storeOriginal,
            final ReductionSettings reductionSettings) throws IOException {
        return load(path, storeOriginal, reductionSettings, WordTraversalDirection.BACKWARD,
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT, DiacriticProcessingMode.AS_IS);
    }

    /**
     * Loads a dictionary from a filesystem path using explicit reduction settings
     * and returns a runtime-specialized trie whose values are compiled patch
     * commands.
     *
     * @param path              path to the dictionary file
     * @param storeOriginal     whether the stem itself should be inserted using the
     *                          canonical no-op patch command
     * @param reductionSettings reduction settings
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final Path path,
            final boolean storeOriginal, final ReductionSettings reductionSettings) throws IOException {
        return compilePatchTrie(load(path, storeOriginal, reductionSettings));
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
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(Path, boolean, ReductionSettings, WordTraversalDirection)}
     *             so patch commands are represented as
     *             {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final Path path, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final WordTraversalDirection traversalDirection)
            throws IOException {
        return load(path, storeOriginal, reductionSettings, traversalDirection,
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT, DiacriticProcessingMode.AS_IS);
    }

    /**
     * Loads a dictionary from a filesystem path using explicit reduction settings
     * and traversal direction, returning runtime-specialized compiled patch values.
     *
     * @param path               path to the dictionary file
     * @param storeOriginal      whether the stem itself should be inserted using
     *                           the canonical no-op patch command
     * @param reductionSettings  reduction settings
     * @param traversalDirection traversal direction used for both trie keys and
     *                           patch commands
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final Path path,
            final boolean storeOriginal, final ReductionSettings reductionSettings,
            final WordTraversalDirection traversalDirection) throws IOException {
        return compilePatchTrie(load(path, storeOriginal, reductionSettings, traversalDirection));
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
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(Path, boolean, ReductionSettings, WordTraversalDirection, CaseProcessingMode)}
     *             so patch commands are represented as
     *             {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final Path path, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final WordTraversalDirection traversalDirection,
            final CaseProcessingMode caseProcessingMode) throws IOException {
        return load(path, storeOriginal, reductionSettings, traversalDirection, caseProcessingMode,
                DiacriticProcessingMode.AS_IS);
    }

    /**
     * Loads a dictionary from a filesystem path using explicit reduction settings,
     * traversal direction, and case processing mode, returning runtime-specialized
     * compiled patch values.
     *
     * @param path               path to the dictionary file
     * @param storeOriginal      whether the stem itself should be inserted using
     *                           the canonical no-op patch command
     * @param reductionSettings  reduction settings
     * @param traversalDirection traversal direction used for both trie keys and
     *                           patch commands
     * @param caseProcessingMode case processing mode used during dictionary parsing
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final Path path,
            final boolean storeOriginal, final ReductionSettings reductionSettings,
            final WordTraversalDirection traversalDirection, final CaseProcessingMode caseProcessingMode)
            throws IOException {
        return compilePatchTrie(load(path, storeOriginal, reductionSettings, traversalDirection, caseProcessingMode));
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
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(Path, boolean, ReductionSettings, WordTraversalDirection, CaseProcessingMode, DiacriticProcessingMode)}
     *             so patch commands are represented as
     *             {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final Path path, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final WordTraversalDirection traversalDirection,
            final CaseProcessingMode caseProcessingMode, final DiacriticProcessingMode diacriticProcessingMode)
            throws IOException {
        Objects.requireNonNull(path, PARAMETER_PATH);
        final TrieMetadata metadata = metadataForCompilation(traversalDirection, reductionSettings, caseProcessingMode,
                diacriticProcessingMode);
        return load(path, storeOriginal, metadata);
    }

    /**
     * Loads a dictionary from a filesystem path using explicit semantic metadata
     * dimensions, returning runtime-specialized compiled patch values.
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
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final Path path,
            final boolean storeOriginal, final ReductionSettings reductionSettings,
            final WordTraversalDirection traversalDirection, final CaseProcessingMode caseProcessingMode,
            final DiacriticProcessingMode diacriticProcessingMode) throws IOException {
        return compilePatchTrie(load(path, storeOriginal, reductionSettings, traversalDirection, caseProcessingMode,
                diacriticProcessingMode));
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
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(Path, boolean, TrieMetadata)} so patch
     *             commands are represented as {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final Path path, final boolean storeOriginal, final TrieMetadata metadata)
            throws IOException {
        Objects.requireNonNull(path, PARAMETER_PATH);
        Objects.requireNonNull(metadata, "metadata");

        try (InputStream inputStream = openDictionaryInputStream(path);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return load(reader, path.toAbsolutePath().toString(), storeOriginal, metadata);
        }
    }

    /**
     * Loads a dictionary from a filesystem path using explicit trie compilation
     * metadata and returns a runtime-specialized trie whose values are compiled
     * patch commands.
     *
     * @param path          path to the dictionary file
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param metadata      trie metadata describing the compilation configuration
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final Path path,
            final boolean storeOriginal, final TrieMetadata metadata) throws IOException {
        return compilePatchTrie(load(path, storeOriginal, metadata));
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
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(Path, boolean, ReductionMode)} so patch
     *             commands are represented as {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final Path path, final boolean storeOriginal,
            final ReductionMode reductionMode) throws IOException {
        Objects.requireNonNull(reductionMode, "reductionMode");
        return load(path, storeOriginal, ReductionSettings.withDefaults(reductionMode));
    }

    /**
     * Loads a dictionary from a filesystem path using default settings for the
     * supplied reduction mode and returns runtime-specialized compiled patch
     * values.
     *
     * @param path          path to the dictionary file
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param reductionMode reduction mode
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final Path path,
            final boolean storeOriginal, final ReductionMode reductionMode) throws IOException {
        return compilePatchTrie(load(path, storeOriginal, reductionMode));
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
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(String, boolean, ReductionSettings)} so
     *             patch commands are represented as
     *             {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final String fileName, final boolean storeOriginal,
            final ReductionSettings reductionSettings) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return load(Path.of(fileName), storeOriginal, reductionSettings);
    }

    /**
     * Loads a dictionary from a filesystem path string using explicit reduction
     * settings and returns runtime-specialized compiled patch values.
     *
     * @param fileName          file name or path string
     * @param storeOriginal     whether the stem itself should be inserted using the
     *                          canonical no-op patch command
     * @param reductionSettings reduction settings
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final String fileName,
            final boolean storeOriginal, final ReductionSettings reductionSettings) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return loadCompiled(Path.of(fileName), storeOriginal, reductionSettings);
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
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(String, boolean, ReductionSettings, WordTraversalDirection)}
     *             so patch commands are represented as
     *             {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final String fileName, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final WordTraversalDirection traversalDirection)
            throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return load(Path.of(fileName), storeOriginal, reductionSettings, traversalDirection,
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
    }

    /**
     * Loads a dictionary from a filesystem path string using explicit reduction
     * settings and traversal direction, returning runtime-specialized compiled
     * patch values.
     *
     * @param fileName           file name or path string
     * @param storeOriginal      whether the stem itself should be inserted using
     *                           the canonical no-op patch command
     * @param reductionSettings  reduction settings
     * @param traversalDirection traversal direction used for both trie keys and
     *                           patch commands
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final String fileName,
            final boolean storeOriginal, final ReductionSettings reductionSettings,
            final WordTraversalDirection traversalDirection) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return loadCompiled(Path.of(fileName), storeOriginal, reductionSettings, traversalDirection);
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
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(String, boolean, ReductionSettings, WordTraversalDirection, CaseProcessingMode)}
     *             so patch commands are represented as
     *             {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final String fileName, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final WordTraversalDirection traversalDirection,
            final CaseProcessingMode caseProcessingMode) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return load(Path.of(fileName), storeOriginal, reductionSettings, traversalDirection, caseProcessingMode,
                DiacriticProcessingMode.AS_IS);
    }

    /**
     * Loads a dictionary from a filesystem path string using explicit reduction
     * settings, traversal direction, and case processing mode, returning
     * runtime-specialized compiled patch values.
     *
     * @param fileName           file name or path string
     * @param storeOriginal      whether the stem itself should be inserted using
     *                           the canonical no-op patch command
     * @param reductionSettings  reduction settings
     * @param traversalDirection traversal direction used for both trie keys and
     *                           patch commands
     * @param caseProcessingMode case processing mode used during dictionary parsing
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final String fileName,
            final boolean storeOriginal, final ReductionSettings reductionSettings,
            final WordTraversalDirection traversalDirection, final CaseProcessingMode caseProcessingMode)
            throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return loadCompiled(Path.of(fileName), storeOriginal, reductionSettings, traversalDirection,
                caseProcessingMode);
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
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(String, boolean, ReductionSettings, WordTraversalDirection, CaseProcessingMode, DiacriticProcessingMode)}
     *             so patch commands are represented as
     *             {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final String fileName, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final WordTraversalDirection traversalDirection,
            final CaseProcessingMode caseProcessingMode, final DiacriticProcessingMode diacriticProcessingMode)
            throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return load(Path.of(fileName), storeOriginal, reductionSettings, traversalDirection, caseProcessingMode,
                diacriticProcessingMode);
    }

    /**
     * Loads a dictionary from a filesystem path string using explicit semantic
     * metadata dimensions, returning runtime-specialized compiled patch values.
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
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final String fileName,
            final boolean storeOriginal, final ReductionSettings reductionSettings,
            final WordTraversalDirection traversalDirection, final CaseProcessingMode caseProcessingMode,
            final DiacriticProcessingMode diacriticProcessingMode) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return loadCompiled(Path.of(fileName), storeOriginal, reductionSettings, traversalDirection,
                caseProcessingMode, diacriticProcessingMode);
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
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadCompiled(String, boolean, TrieMetadata)} so patch
     *             commands are represented as {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final String fileName, final boolean storeOriginal,
            final TrieMetadata metadata) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return load(Path.of(fileName), storeOriginal, metadata);
    }

    /**
     * Loads a dictionary from a filesystem path string using explicit trie
     * compilation metadata and returns runtime-specialized compiled patch values.
     *
     * @param fileName      file name or path string
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param metadata      trie metadata describing the compilation configuration
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if the file cannot be opened or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final String fileName,
            final boolean storeOriginal, final TrieMetadata metadata) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return loadCompiled(Path.of(fileName), storeOriginal, metadata);
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
     * @deprecated Since 2.3.0 for runtime stemming. Convert {@code fileName} to a
     *             {@link Path} and use {@link #loadCompiled(Path, boolean, ReductionMode)}
     *             so patch commands are represented as {@link CompiledPatchCommand}
     *             values. The corresponding compiled {@code String} signature is
     *             reserved for stable model identifiers.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> load(final String fileName, final boolean storeOriginal,
            final ReductionMode reductionMode) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return load(Path.of(fileName), storeOriginal, reductionMode);
    }

    /**
     * Loads one exact stable model identifier and returns a runtime-specialized trie.
     *
     * <p>The registry is discovered through the thread context class loader.
     * Selection is exact: the method never resolves a language default, falls back
     * to another model, or depends on classpath order. Use
     * {@link #loadCompiled(Path, boolean, ReductionMode)} for a filesystem path.</p>
     *
     * @param modelId       exact stable model identifier
     * @param storeOriginal whether the stem itself should be inserted using the
     *                      canonical no-op patch command
     * @param reductionMode reduction mode
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code modelId} is blank
     * @throws IOException if registry discovery or dictionary reading fails
     * @throws StemmerModelNotFoundException if the exact model is not visible
     * @throws DuplicateStemmerModelException if discovery finds duplicate model IDs
     * @throws UnsupportedStemmerModelFormatException if a descriptor format is unsupported
     * @throws StemmerModelIntegrityException if descriptor or resource integrity validation fails
     */
    public static FrequencyTrie<CompiledPatchCommand> loadCompiled(final String modelId,
            final boolean storeOriginal, final ReductionMode reductionMode) throws IOException {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(reductionMode, "reductionMode");
        if (modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        final StemmerModelDescriptor descriptor = StemmerModelRegistry.fromContextClassLoader().require(modelId);
        return loadCompiled(descriptor, storeOriginal, reductionMode);
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
        final ReductionSettings patchReductionSettings = ReductionSettings
                .withUniformSubtreeContraction(reductionSettings);
        return TrieMetadata.forCompilation(traversalDirection, patchReductionSettings, diacriticProcessingMode,
                caseProcessingMode);
    }

    /**
     * Maps textual patch commands to runtime-specialized compiled patch commands.
     *
     * <p>
     * Equal textual patch commands are compiled once and shared by all trie values
     * that reference them. The returned trie preserves the source trie keys,
     * metadata, traversal direction, counts, and reduction settings.
     * </p>
     *
     * @param trie source trie containing textual patch commands
     * @return equivalent trie containing compiled patch commands
     * @throws NullPointerException if {@code trie} is {@code null}
     */
    private static FrequencyTrie<CompiledPatchCommand> compilePatchTrie(final FrequencyTrie<String> trie) {
        final FrequencyTrie<String> sourceTrie = Objects.requireNonNull(trie, "trie");
        final Map<String, CompiledPatchCommand> compiledPatches = new HashMap<>(4096);
        return FrequencyTrieBuilders.mapValues(sourceTrie, CompiledPatchCommand[]::new,
                sourceTrie.metadata().reductionSettings(),
                patch -> compiledPatches.computeIfAbsent(patch,
                        value -> CompiledPatchCommand.compile(value, sourceTrie.traversalDirection())));
    }

    /**
     * Loads a GZip-compressed binary patch-command trie from a filesystem path.
     *
     * @param path path to the compressed binary trie file
     * @return compiled patch-command trie
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if the file cannot be opened, decompressed, or
     *                              read
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadBinaryCompiled(Path)} so patch commands are
     *             represented as {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> loadBinary(final Path path) throws IOException {
        Objects.requireNonNull(path, PARAMETER_PATH);
        return StemmerPatchTrieBinaryIO.read(path);
    }

    /**
     * Loads a GZip-compressed binary patch-command trie from a filesystem path and
     * returns runtime-specialized compiled patch values.
     *
     * <p>
     * Serialized patch commands are compiled while the binary graph is read, so the
     * returned trie is materialized directly without an intermediate
     * {@code FrequencyTrie<String>} or a second node graph.
     * </p>
     *
     * @param path path to the compressed binary trie file
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if the file cannot be opened, decompressed, or
     *                              read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadBinaryCompiled(final Path path) throws IOException {
        Objects.requireNonNull(path, PARAMETER_PATH);
        return StemmerPatchTrieBinaryIO.readCompiled(path);
    }

    /**
     * Loads a GZip-compressed binary patch-command trie from a filesystem path
     * using a custom dense lookup span override.
     * <p>
     * This is a runtime-only tuning parameter that does not affect persisted
     * metadata.
     * </p>
     *
     * @param path             path to the compressed binary trie file
     * @param maxExpandedIndex dense lookup span override; negative values use
     *                         {@link FrequencyTrie#DEFAULT_MAX_EXPANDED_INDEX}
     * @return compiled patch-command trie
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if the file cannot be opened, decompressed, or
     *                              read
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadBinaryCompiled(Path, int)} so patch commands are
     *             represented as {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> loadBinary(final Path path, final int maxExpandedIndex) throws IOException {
        Objects.requireNonNull(path, PARAMETER_PATH);
        return StemmerPatchTrieBinaryIO.read(path, maxExpandedIndex);
    }

    /**
     * Loads a GZip-compressed binary patch-command trie from a filesystem path using
     * a custom dense lookup span override and returns runtime-specialized compiled
     * patch values.
     *
     * <p>
     * Serialized patch commands are compiled directly into the final node graph.
     * The dense lookup override is applied during that graph materialization.
     * </p>
     *
     * @param path             path to the compressed binary trie file
     * @param maxExpandedIndex dense lookup span override; negative values use
     *                         {@link FrequencyTrie#DEFAULT_MAX_EXPANDED_INDEX}
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws IOException          if the file cannot be opened, decompressed, or
     *                              read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadBinaryCompiled(final Path path,
            final int maxExpandedIndex) throws IOException {
        Objects.requireNonNull(path, PARAMETER_PATH);
        return StemmerPatchTrieBinaryIO.readCompiled(path, maxExpandedIndex);
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
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadBinaryCompiled(String)} so patch commands are
     *             represented as {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> loadBinary(final String fileName) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return StemmerPatchTrieBinaryIO.read(fileName);
    }

    /**
     * Loads a GZip-compressed binary patch-command trie from a filesystem path
     * string and returns runtime-specialized compiled patch values.
     *
     * <p>
     * Serialized patch commands are compiled while the binary graph is read, so no
     * intermediate String-valued trie is constructed.
     * </p>
     *
     * @param fileName file name or path string
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if {@code fileName} is {@code null}
     * @throws IOException          if the file cannot be opened, decompressed, or
     *                              read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadBinaryCompiled(final String fileName) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return StemmerPatchTrieBinaryIO.readCompiled(fileName);
    }

    /**
     * Loads a GZip-compressed binary patch-command trie from a filesystem path
     * string using a custom dense lookup span override.
     * <p>
     * This is a runtime-only tuning parameter that does not affect persisted
     * metadata.
     * </p>
     *
     * @param fileName         file name or path string
     * @param maxExpandedIndex dense lookup span override; negative values use
     *                         {@link FrequencyTrie#DEFAULT_MAX_EXPANDED_INDEX}
     * @return compiled patch-command trie
     * @throws NullPointerException if {@code fileName} is {@code null}
     * @throws IOException          if the file cannot be opened, decompressed, or
     *                              read
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadBinaryCompiled(String, int)} so patch commands are
     *             represented as {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> loadBinary(final String fileName, final int maxExpandedIndex)
            throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return StemmerPatchTrieBinaryIO.read(fileName, maxExpandedIndex);
    }

    /**
     * Loads a GZip-compressed binary patch-command trie from a filesystem path string
     * using a custom dense lookup span override and returns runtime-specialized
     * compiled patch values.
     *
     * <p>
     * Serialized patch commands are compiled directly into the final node graph.
     * The dense lookup override is applied during that graph materialization.
     * </p>
     *
     * @param fileName         file name or path string
     * @param maxExpandedIndex dense lookup span override; negative values use
     *                         {@link FrequencyTrie#DEFAULT_MAX_EXPANDED_INDEX}
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if {@code fileName} is {@code null}
     * @throws IOException          if the file cannot be opened, decompressed, or
     *                              read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadBinaryCompiled(final String fileName,
            final int maxExpandedIndex) throws IOException {
        Objects.requireNonNull(fileName, FILENAME_REQUIRED);
        return StemmerPatchTrieBinaryIO.readCompiled(fileName, maxExpandedIndex);
    }

    /**
     * Loads a GZip-compressed binary patch-command trie from an input stream.
     *
     * @param inputStream source input stream
     * @return compiled patch-command trie
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException          if the stream cannot be decompressed or read
     * @deprecated Since 2.3.0 for runtime stemming. Use
     *             {@link #loadBinaryCompiled(InputStream)} so patch commands are
     *             represented as {@link CompiledPatchCommand} values.
     */
    @Deprecated(since = "2.3.0", forRemoval = false)
    public static FrequencyTrie<String> loadBinary(final InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        return StemmerPatchTrieBinaryIO.read(inputStream);
    }

    /**
     * Loads a GZip-compressed binary patch-command trie from an input stream and
     * returns runtime-specialized compiled patch values.
     *
     * <p>
     * Serialized patch commands are compiled while the binary graph is read, so the
     * returned trie is materialized directly without an intermediate
     * {@code FrequencyTrie<String>} or graph-mapping pass.
     * </p>
     *
     * @param inputStream source input stream
     * @return compiled patch-command trie with runtime-specialized values
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException          if the stream cannot be decompressed or read
     */
    public static FrequencyTrie<CompiledPatchCommand> loadBinaryCompiled(final InputStream inputStream)
            throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        return StemmerPatchTrieBinaryIO.readCompiled(inputStream);
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
        Objects.requireNonNull(path, PARAMETER_PATH);
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
        Objects.requireNonNull(path, PARAMETER_PATH);
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
