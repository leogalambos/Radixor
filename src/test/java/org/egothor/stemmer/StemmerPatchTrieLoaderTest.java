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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Professional test suite for {@link StemmerPatchTrieLoader}.
 *
 * <p>
 * The suite combines focused API-level verification with integration validation
 * against bundled dictionaries. It verifies:
 * </p>
 * <ul>
 * <li>all public loading overloads</li>
 * <li>binary persistence round-trips</li>
 * <li>null-argument contracts</li>
 * <li>comment-aware parsing delegated to {@link StemmerDictionaryParser}</li>
 * <li>preservation of all valid stem candidates returned by
 * {@link FrequencyTrie#getAll(String)}</li>
 * <li>the current bundled language set, including right-to-left metadata</li>
 * </ul>
 */
@Tag("unit")
@Tag("integration")
@Tag("stemmer")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class StemmerPatchTrieLoaderTest {

    /**
     * Temporary directory for filesystem-based tests.
     */
    @TempDir
    private Path tempDir;

    /**
     * Reduction mode used for deterministic getAll-preserving checks in focused
     * tests.
     */
    private static final ReductionMode DEFAULT_REDUCTION_MODE = ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS;

    /**
     * Representative number of bundled words used for overload consistency checks.
     */
    private static final int REPRESENTATIVE_BUNDLED_WORD_COUNT = 25;

    /**
     * Provides arguments for bundled dictionary verification across both supported
     * getAll-preserving reduction modes.
     *
     * <p>
     * The stream is derived directly from the current {@link Language} enum so the
     * test suite follows the supported bundled language set automatically.
     * </p>
     *
     * @return parameter stream
     */
    static Stream<Arguments> bundledDictionaryCases() {
        final ReductionMode[] reductionModes = new ReductionMode[] {
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS };

        return Arrays.stream(StemmerPatchTrieLoader.Language.values()).flatMap(language -> IntStream
                .range(0, reductionModes.length)
                .mapToObj(index -> Arguments.of(
                        String.format("%02d-%s-%s", index + 1, language.name().toLowerCase(),
                                reductionModes[index].name().toLowerCase()),
                        language, reductionModes[index])));
    }

    /**
     * Provides representative bundled languages for overload consistency checks.
     *
     * <p>
     * The sample intentionally covers both traversal directions.
     * </p>
     *
     * @return parameter stream
     */
    static Stream<Arguments> bundledLanguageSamples() {
        return Stream.of(
                Arguments.of("01-us_uk", StemmerPatchTrieLoader.Language.US_UK),
                Arguments.of("02-de_de", StemmerPatchTrieLoader.Language.DE_DE),
                Arguments.of("03-fa_ir", StemmerPatchTrieLoader.Language.FA_IR),
                Arguments.of("04-he_il", StemmerPatchTrieLoader.Language.HE_IL),
                Arguments.of("05-yi", StemmerPatchTrieLoader.Language.YI));
    }

    /**
     * Provides invalid null-argument scenarios for public methods.
     *
     * @return parameter stream
     */
    static Stream<Arguments> nullContractCases() {
        final ReductionSettings settings = ReductionSettings.withDefaults(DEFAULT_REDUCTION_MODE);
        final FrequencyTrie<String> trie = new FrequencyTrie.Builder<String>(String[]::new, settings)
                .put("running", new PatchCommandEncoder().encode("running", "run")).build();

        return Stream.of(
                Arguments.of("01-load-language-settings",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.load((StemmerPatchTrieLoader.Language) null,
                                true, settings),
                        "language"),
                Arguments.of("02-load-language-mode",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.load((StemmerPatchTrieLoader.Language) null,
                                true, DEFAULT_REDUCTION_MODE),
                        "language"),
                Arguments.of("03-load-language-null-settings",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.load(StemmerPatchTrieLoader.Language.US_UK,
                                true, (ReductionSettings) null),
                        "reductionSettings"),
                Arguments.of("04-load-language-null-mode",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.load(StemmerPatchTrieLoader.Language.US_UK,
                                true, (ReductionMode) null),
                        "reductionMode"),
                Arguments.of("05-load-path-settings",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.load((Path) null, true, settings), "path"),
                Arguments.of("06-load-path-mode",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.load((Path) null, true,
                                DEFAULT_REDUCTION_MODE),
                        "path"),
                Arguments.of("07-load-path-null-settings",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.load(tempPath(), true,
                                (ReductionSettings) null),
                        "reductionSettings"),
                Arguments.of("08-load-path-null-mode",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.load(tempPath(), true, (ReductionMode) null),
                        "reductionMode"),
                Arguments.of("09-load-string-settings",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.load((String) null, true, settings),
                        "fileName"),
                Arguments.of("10-load-string-mode",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.load((String) null, true,
                                DEFAULT_REDUCTION_MODE),
                        "fileName"),
                Arguments.of("11-load-string-null-settings",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.load(tempPath().toString(), true,
                                (ReductionSettings) null),
                        "reductionSettings"),
                Arguments.of("12-load-string-null-mode",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.load(tempPath().toString(), true,
                                (ReductionMode) null),
                        "reductionMode"),
                Arguments.of("13-load-binary-path",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.loadBinary((Path) null), "path"),
                Arguments.of("14-load-binary-string",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.loadBinary((String) null), "fileName"),
                Arguments.of("15-load-binary-stream",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.loadBinary((InputStream) null),
                        "inputStream"),
                Arguments.of("16-save-binary-null-trie-path",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.saveBinary(null, tempPath()), "trie"),
                Arguments.of("17-save-binary-null-path",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.saveBinary(trie, (Path) null), "path"),
                Arguments.of("18-save-binary-null-trie-string",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.saveBinary(null, tempPath().toString()),
                        "trie"),
                Arguments.of("19-save-binary-null-string",
                        (ExecutableOperation) () -> StemmerPatchTrieLoader.saveBinary(trie, (String) null),
                        "fileName"));
    }

    /**
     * Returns a representative temporary path for null-contract method sources.
     *
     * @return representative path
     */
    private static Path tempPath() {
        return Path.of("target", "test-loader-null-contracts.dict");
    }

    /**
     * Focused API contract tests.
     */
    @Nested
    @DisplayName("API contracts")
    final class ApiContractTests {

        /**
         * Verifies that all documented null contracts are enforced consistently by
         * public methods.
         *
         * @param scenario                expected scenario identifier
         * @param operation               operation that must fail
         * @param expectedMessageFragment expected message fragment
         */
        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("org.egothor.stemmer.StemmerPatchTrieLoaderTest#nullContractCases")
        @DisplayName("Public methods must reject null arguments with precise diagnostics")
        void shouldRejectNullArguments(final String scenario, final ExecutableOperation operation,
                final String expectedMessageFragment) {
            final NullPointerException exception = assertThrows(NullPointerException.class, operation::execute,
                    "Scenario " + scenario + " must reject null input.");

            assertNotNull(exception.getMessage(), "NullPointerException message must be present.");
            assertEquals(expectedMessageFragment, exception.getMessage(),
                    "Scenario " + scenario + " must identify the offending argument.");
        }

        /**
         * Verifies that loading from a missing file fails with an {@link IOException}.
         */
        @Test
        @DisplayName("Loading from a missing dictionary file must fail with IOException")
        void shouldFailWhenDictionaryFileDoesNotExist() {
            final Path missingFile = tempDir.resolve("missing-dictionary.dict");

            assertThrows(IOException.class,
                    () -> StemmerPatchTrieLoader.load(missingFile, true, DEFAULT_REDUCTION_MODE));
        }

        /**
         * Verifies that loading a missing binary file fails with an
         * {@link IOException}.
         */
        @Test
        @DisplayName("Loading a missing binary trie file must fail with IOException")
        void shouldFailWhenBinaryFileDoesNotExist() {
            final Path missingFile = tempDir.resolve("missing-trie.bin.gz");

            assertThrows(IOException.class, () -> StemmerPatchTrieLoader.loadBinary(missingFile));
        }
    }

    /**
     * Focused filesystem and parser behavior tests.
     */
    @Nested
    @DisplayName("Filesystem and parser behavior")
    final class FilesystemAndParserTests {

        /**
         * Verifies that all textual loading overloads produce equivalent tries for the
         * same source dictionary.
         *
         * @throws IOException if the test file cannot be written or read
         */
        @Test
        @DisplayName("Path and String overloads must load equivalent tries")
        void shouldLoadEquivalentTrieFromPathAndStringOverloads() throws IOException {
            final Path dictionaryFile = writeDictionary("""
                    run	running	runs	runner
                    play	playing	played	plays
                    city	cities
                    """);

            final ReductionSettings settings = ReductionSettings.withDefaults(DEFAULT_REDUCTION_MODE);

            final FrequencyTrie<String> fromPathWithSettings = StemmerPatchTrieLoader.load(dictionaryFile, true,
                    settings);
            final FrequencyTrie<String> fromPathWithMode = StemmerPatchTrieLoader.load(dictionaryFile, true,
                    DEFAULT_REDUCTION_MODE);
            final FrequencyTrie<String> fromStringWithSettings = StemmerPatchTrieLoader.load(dictionaryFile.toString(),
                    true, settings);
            final FrequencyTrie<String> fromStringWithMode = StemmerPatchTrieLoader.load(dictionaryFile.toString(),
                    true, DEFAULT_REDUCTION_MODE);

            assertTriePatchSemanticsEqual(fromPathWithSettings, fromPathWithMode, "running", "played", "cities",
                    "run");
            assertTriePatchSemanticsEqual(fromPathWithSettings, fromStringWithSettings, "running", "played",
                    "cities", "run");
            assertTriePatchSemanticsEqual(fromPathWithSettings, fromStringWithMode, "running", "played", "cities",
                    "run");
        }

        /**
         * Verifies that the loader honors {@code storeOriginal=true} by inserting the
         * canonical no-op patch for the stem itself.
         *
         * @throws IOException if the test file cannot be written or read
         */
        @Test
        @DisplayName("storeOriginal=true must make the stem itself resolvable through the no-op patch")
        void shouldStoreOriginalStemWhenRequested() throws IOException {
            final Path dictionaryFile = writeDictionary("""
                    run	running	runs
                    """);

            final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(dictionaryFile, true,
                    DEFAULT_REDUCTION_MODE);

            final String[] patches = trie.getAll("run");
            final Set<String> reconstructedStems = reconstructAllStemCandidates(trie, "run");

            assertAll(() -> assertNotNull(patches, "Patch array must be returned for stored stem."),
                    () -> assertFalse(reconstructedStems.isEmpty(),
                            "Stored stem must yield at least one reconstructed candidate."),
                    () -> assertEquals(Set.of("run"), reconstructedStems,
                            "Stored stem must reconstruct exactly itself."));
        }

        /**
         * Verifies that the loader honors {@code storeOriginal=false}.
         *
         * @throws IOException if the test file cannot be written or read
         */
        @Test
        @DisplayName("storeOriginal=false must not insert the stem itself unless present as a variant elsewhere")
        void shouldNotStoreOriginalStemWhenDisabled() throws IOException {
            final Path dictionaryFile = writeDictionary("""
                    run	running	runs
                    play	playing	played	plays
                    """);

            final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(dictionaryFile, false,
                    DEFAULT_REDUCTION_MODE);

            assertNull(trie.get("run"),
                    "Stem itself must not be resolvable when storeOriginal is disabled and the stem is not a variant.");
            assertEquals(Set.of("run"), reconstructAllStemCandidates(trie, "running"),
                    "Variants must still reconstruct the proper stem.");
        }

        /**
         * Verifies that the loader honors forward traversal for right-to-left
         * dictionaries loaded from filesystem overloads.
         *
         * @throws IOException if the test file cannot be written or read
         */
        @Test
        @DisplayName("Explicit right-to-left loading must use forward traversal semantics")
        void shouldUseForwardTraversalForExplicitRightToLeftLoading() throws IOException {
            final Path dictionaryFile = writeDictionary("""
                    كتب	كتابة	كتاب
                    """);

            final ReductionSettings settings = ReductionSettings.withDefaults(DEFAULT_REDUCTION_MODE);
            final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(dictionaryFile, true, settings,
                    WordTraversalDirection.FORWARD);

            assertEquals(WordTraversalDirection.FORWARD, trie.traversalDirection(),
                    "Right-to-left loading must produce a forward-traversed trie.");
            assertEquals(Set.of("كتب"), reconstructAllStemCandidates(trie, "كتابة"),
                    "Patch reconstruction must use the trie traversal direction.");
        }

        /**
         * Verifies that comment syntax documented by the loader is effectively honored
         * through delegated parsing.
         *
         * @throws IOException if the test file cannot be written or read
         */
        @Test
        @DisplayName("Parser must ignore hash and slash-slash remarks")
        void shouldIgnoreHashAndDoubleSlashRemarks() throws IOException {
            final Path dictionaryFile = writeDictionary("""
                    # full-line hash comment
                    // full-line slash comment
                    run	running	runs // inline slash comment
                    play	playing	played # inline hash comment

                    city	cities
                    """);

            final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(dictionaryFile, true,
                    DEFAULT_REDUCTION_MODE);

            assertAll(() -> assertEquals(Set.of("run"), reconstructAllStemCandidates(trie, "running")),
                    () -> assertEquals(Set.of("play"), reconstructAllStemCandidates(trie, "played")),
                    () -> assertEquals(Set.of("city"), reconstructAllStemCandidates(trie, "cities")),
                    () -> assertNull(trie.get("#"), "Comment markers must not become dictionary terms."),
                    () -> assertNull(trie.get("//"), "Comment markers must not become dictionary terms."));
        }

        /**
         * Verifies binary save/load round-trip equivalence for the filesystem and
         * stream overloads.
         *
         * @throws IOException if writing or reading fails
         */
        @Test
        @DisplayName("Binary save and load overloads must preserve trie semantics")
        void shouldRoundTripBinaryTrieAcrossAllBinaryOverloads() throws IOException {
            final Path dictionaryFile = writeDictionary("""
                    run	running	runs	runner
                    city	cities
                    study	studies	studying
                    """);
            final Path binaryFile = tempDir.resolve("stemmer-trie.bin.gz");

            final FrequencyTrie<String> original = StemmerPatchTrieLoader.load(dictionaryFile, true,
                    DEFAULT_REDUCTION_MODE);

            StemmerPatchTrieLoader.saveBinary(original, binaryFile);
            final FrequencyTrie<String> fromPath = StemmerPatchTrieLoader.loadBinary(binaryFile);
            final FrequencyTrie<String> fromString = StemmerPatchTrieLoader.loadBinary(binaryFile.toString());

            final byte[] binaryBytes = Files.readAllBytes(binaryFile);
            try (InputStream inputStream = new ByteArrayInputStream(binaryBytes)) {
                final FrequencyTrie<String> fromStream = StemmerPatchTrieLoader.loadBinary(inputStream);

                assertTriePatchSemanticsEqual(original, fromPath, "run", "running", "runner", "cities",
                        "studying");
                assertTriePatchSemanticsEqual(original, fromString, "run", "running", "runner", "cities",
                        "studying");
                assertTriePatchSemanticsEqual(original, fromStream, "run", "running", "runner", "cities",
                        "studying");
            }
        }

        /**
         * Writes a dictionary file into the temporary directory.
         *
         * @param content dictionary content
         * @return written file path
         * @throws IOException if writing fails
         */
        private Path writeDictionary(final String content) throws IOException {
            final Path file = tempDir.resolve("dictionary-" + System.nanoTime() + ".dict");
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        }
    }

    /**
     * Bundled dictionary integration tests.
     */
    @Nested
    @DisplayName("Bundled dictionaries")
    final class BundledDictionaryTests {

        /**
         * Verifies that the current language enumeration exactly matches the bundled
         * language set expected by this project revision.
         */
        @Test
        @DisplayName("Language enum must expose the current bundled language set")
        void shouldExposeCurrentBundledLanguageSet() {
            final Set<StemmerPatchTrieLoader.Language> expectedLanguages = new LinkedHashSet<StemmerPatchTrieLoader.Language>(
                    Arrays.asList(StemmerPatchTrieLoader.Language.CS_CZ, StemmerPatchTrieLoader.Language.DA_DK,
                            StemmerPatchTrieLoader.Language.DE_DE, StemmerPatchTrieLoader.Language.ES_ES,
                            StemmerPatchTrieLoader.Language.FA_IR, StemmerPatchTrieLoader.Language.FI_FI,
                            StemmerPatchTrieLoader.Language.FR_FR, StemmerPatchTrieLoader.Language.HE_IL,
                            StemmerPatchTrieLoader.Language.HU_HU, StemmerPatchTrieLoader.Language.IT_IT,
                            StemmerPatchTrieLoader.Language.NB_NO, StemmerPatchTrieLoader.Language.NL_NL,
                            StemmerPatchTrieLoader.Language.NN_NO, StemmerPatchTrieLoader.Language.PL_PL,
                            StemmerPatchTrieLoader.Language.PT_PT, StemmerPatchTrieLoader.Language.RU_RU,
                            StemmerPatchTrieLoader.Language.SV_SE, StemmerPatchTrieLoader.Language.UK_UA,
                            StemmerPatchTrieLoader.Language.US_UK, StemmerPatchTrieLoader.Language.YI));

            final Set<StemmerPatchTrieLoader.Language> actualLanguages = new LinkedHashSet<StemmerPatchTrieLoader.Language>(
                    Arrays.asList(StemmerPatchTrieLoader.Language.values()));

            assertEquals(expectedLanguages, actualLanguages,
                    "The bundled language enum must match the project's supported language set exactly.");
        }

        /**
         * Verifies that the right-to-left metadata is correctly assigned for the
         * currently supported bundled languages.
         */
        @Test
        @DisplayName("Language enum must mark right-to-left bundled languages correctly")
        void shouldExposeCorrectRightToLeftMetadata() {
            final Set<StemmerPatchTrieLoader.Language> expectedRightToLeftLanguages = Set.of(
                    StemmerPatchTrieLoader.Language.FA_IR, StemmerPatchTrieLoader.Language.HE_IL,
                    StemmerPatchTrieLoader.Language.YI);

            for (StemmerPatchTrieLoader.Language language : StemmerPatchTrieLoader.Language.values()) {
                if (expectedRightToLeftLanguages.contains(language)) {
                    assertTrue(language.isRightToLeft(),
                            () -> language.name() + " must be marked as right-to-left.");
                } else {
                    assertFalse(language.isRightToLeft(),
                            () -> language.name() + " must not be marked as right-to-left.");
                }
            }
        }

        /**
         * Verifies that each bundled dictionary compiles into a trie whose
         * {@link FrequencyTrie#getAll(String)} results still reconstruct exactly the
         * same set of stems as the source dictionary.
         *
         * @param scenario      human-readable numbered scenario identifier
         * @param language      tested bundled language
         * @param reductionMode reduction mode
         * @throws IOException if a bundled dictionary cannot be read
         */
        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("org.egothor.stemmer.StemmerPatchTrieLoaderTest#bundledDictionaryCases")
        @DisplayName("Bundled dictionaries must preserve all valid stem candidates in getAll()")
        void shouldPreserveAllStemCandidatesForBundledDictionaries(final String scenario,
                final StemmerPatchTrieLoader.Language language, final ReductionMode reductionMode) throws IOException {
            Objects.requireNonNull(scenario, "scenario");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(reductionMode, "reductionMode");

            final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(language, true, reductionMode);
            final Map<String, Set<String>> expectedStemsByWord = readExpectedStems(language);

            assertNotNull(trie, "Compiled trie must be created.");
            assertFalse(expectedStemsByWord.isEmpty(), "Bundled dictionary must not be empty.");
            assertEquals(language.isRightToLeft() ? WordTraversalDirection.FORWARD : WordTraversalDirection.BACKWARD,
                    trie.traversalDirection(), "Trie traversal direction must match language metadata.");

            for (Map.Entry<String, Set<String>> entry : expectedStemsByWord.entrySet()) {
                final String word = entry.getKey();
                final Set<String> expectedStems = entry.getValue();
                final Set<String> actualStems = reconstructAllStemCandidates(trie, word);

                assertFalse(actualStems.isEmpty(),
                        () -> "No patch candidates returned for word '" + word + "' in scenario " + scenario + ".");

                assertEquals(expectedStems, actualStems,
                        () -> "Reconstructed stem candidates differ for word '" + word + "' in scenario " + scenario
                                + "'. Expected: " + expectedStems + ", actual: " + actualStems);
            }
        }

        /**
         * Verifies that representative bundled dictionaries load equivalently through
         * both reduction-setting and reduction-mode overloads.
         *
         * @param scenario scenario identifier
         * @param language tested language
         * @throws IOException if reading fails
         */
        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("org.egothor.stemmer.StemmerPatchTrieLoaderTest#bundledLanguageSamples")
        @DisplayName("Bundled dictionary overloads must produce equivalent trie semantics")
        void shouldLoadBundledDictionariesEquivalentlyAcrossOverloads(final String scenario,
                final StemmerPatchTrieLoader.Language language) throws IOException {
            final ReductionSettings settings = ReductionSettings.withDefaults(DEFAULT_REDUCTION_MODE);

            final FrequencyTrie<String> viaSettings = StemmerPatchTrieLoader.load(language, true, settings);
            final FrequencyTrie<String> viaMode = StemmerPatchTrieLoader.load(language, true, DEFAULT_REDUCTION_MODE);

            final Map<String, Set<String>> expectedStemsByWord = readExpectedStems(language);
            int counter = 0;

            for (Map.Entry<String, Set<String>> entry : expectedStemsByWord.entrySet()) {
                assertTriePatchSemanticsEqual(viaSettings, viaMode, entry.getKey());
                counter++;
                if (counter >= REPRESENTATIVE_BUNDLED_WORD_COUNT) {
                    break;
                }
            }

            assertFalse(expectedStemsByWord.isEmpty(),
                    "Scenario " + scenario + " must provide at least one bundled dictionary entry.");
        }
    }

    /**
     * Reads the bundled dictionary and builds a mapping of surface word to all
     * stems it is associated with in the source data.
     *
     * <p>
     * The method intentionally delegates parsing to {@link StemmerDictionaryParser}
     * so that expected values follow the same comment and normalization rules as
     * the production loader.
     * </p>
     *
     * @param language bundled language
     * @return expected stems by surface word
     * @throws IOException if the bundled resource cannot be read
     */
    private static Map<String, Set<String>> readExpectedStems(final StemmerPatchTrieLoader.Language language)
            throws IOException {
        final Map<String, Set<String>> expectedStemsByWord = new LinkedHashMap<String, Set<String>>();
        final String resourcePath = language.resourcePath();

        try (InputStream inputStream = openBundledResource(resourcePath);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            StemmerDictionaryParser.parse(reader, resourcePath, (stem, variants, lineNumber) -> {
                registerExpectedStem(expectedStemsByWord, stem, stem);

                for (String variant : variants) {
                    registerExpectedStem(expectedStemsByWord, variant, stem);
                }
            });
        }

        return expectedStemsByWord;
    }

    /**
     * Registers one expected stem for one surface word.
     *
     * @param expectedStemsByWord expected stem mapping
     * @param word                surface word
     * @param stem                expected stem
     */
    private static void registerExpectedStem(final Map<String, Set<String>> expectedStemsByWord, final String word,
            final String stem) {
        Set<String> stems = expectedStemsByWord.get(word);
        if (stems == null) {
            stems = new LinkedHashSet<String>();
            expectedStemsByWord.put(word, stems);
        }
        stems.add(stem);
    }

    /**
     * Reconstructs all stem candidates for the supplied word from all patch
     * commands returned by {@link FrequencyTrie#getAll(String)}.
     *
     * @param trie compiled patch trie
     * @param word surface word
     * @return reconstructed stem candidates
     */
    private static Set<String> reconstructAllStemCandidates(final FrequencyTrie<String> trie, final String word) {
        final String[] patchCommands = trie.getAll(word);
        final Set<String> stems = new LinkedHashSet<String>();

        if (patchCommands == null) {
            return stems;
        }

        for (String patchCommand : patchCommands) {
            stems.add(PatchCommandEncoder.apply(word, patchCommand, trie.traversalDirection()));
        }

        return stems;
    }

    /**
     * Verifies semantic equality of two tries for the supplied words by comparing
     * both their raw patch arrays and reconstructed stem sets.
     *
     * @param expected reference trie
     * @param actual   compared trie
     * @param words    words to verify
     */
    private static void assertTriePatchSemanticsEqual(final FrequencyTrie<String> expected,
            final FrequencyTrie<String> actual, final String... words) {
        for (String word : words) {
            assertAll(
                    () -> assertArrayEquals(expected.getAll(word), actual.getAll(word),
                            "Patch arrays must match for word '" + word + "'."),
                    () -> assertEquals(reconstructAllStemCandidates(expected, word),
                            reconstructAllStemCandidates(actual, word),
                            "Reconstructed stems must match for word '" + word + "'."));
        }
    }

    /**
     * Opens one bundled dictionary resource.
     *
     * @param resourcePath classpath resource path
     * @return opened input stream
     * @throws IOException if the resource cannot be found
     */
    private static InputStream openBundledResource(final String resourcePath) throws IOException {
        final InputStream inputStream = StemmerPatchTrieLoaderTest.class.getClassLoader()
                .getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Bundled stemmer resource not found: " + resourcePath);
        }
        return new GZIPInputStream(inputStream);
    }

    /**
     * Minimal checked-exception-friendly operation used by null-contract tests.
     */
    @FunctionalInterface
    private interface ExecutableOperation {

        /**
         * Executes the operation.
         *
         * @throws Exception if execution fails
         */
        void execute() throws Exception;
    }
}
