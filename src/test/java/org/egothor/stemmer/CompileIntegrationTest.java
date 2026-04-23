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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
 * Integration tests for the {@link Compile} CLI.
 *
 * <p>
 * This suite validates the command end to end through real filesystem-based
 * execution:
 * </p>
 *
 * <ol>
 * <li>copying a source dictionary resource to a temporary input file</li>
 * <li>running the CLI against that input file</li>
 * <li>writing a GZip-compressed binary artifact</li>
 * <li>reloading the artifact through {@link StemmerPatchTrieBinaryIO}</li>
 * <li>verifying representative stemming behavior</li>
 * </ol>
 *
 * <p>
 * The suite intentionally has two layers:
 * </p>
 *
 * <ul>
 * <li>a focused fixture dictionary that validates remarks, UTF-8, nested output
 * paths, and {@code --store-original}</li>
 * <li>real bundled project dictionaries that validate multidictionary CLI
 * compilation against shipped resources</li>
 * </ul>
 */
@Tag("integration")
@Tag("cli")
@Tag("stemmer")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Compile integration")
final class CompileIntegrationTest {

    /**
     * Dedicated fixture dictionary used for deterministic parser-oriented CLI
     * integration checks.
     */
    private static final String REMARK_AWARE_DICTIONARY_RESOURCE = "org/egothor/stemmer/compile/remark-aware-dictionary.txt";

    /**
     * Reduction mode used by integration scenarios.
     */
    private static final ReductionMode DEFAULT_REDUCTION_MODE = ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS;

    /**
     * Reader charset used for extraction of representative probes from bundled
     * project dictionaries.
     *
     * <p>
     * Bundled project dictionaries are expected to be encoded in UTF-8.
     * </p>
     */
    private static final Charset BUNDLED_PROBE_SCAN_CHARSET = StandardCharsets.UTF_8;

    /**
     * Maximum number of representative bundled variants asserted per dictionary.
     */
    private static final int REPRESENTATIVE_VARIANT_LIMIT = 32;

    /**
     * Temporary directory used for filesystem-based command execution.
     */
    @TempDir
    private Path tempDir;

    /**
     * Provides bundled project dictionary scenarios.
     *
     * @return parameter stream
     */
    static Stream<Arguments> bundledDictionaryCases() {
        return Stream.of(
                //
                Arguments.of("cs_cz", "cs_cz/stemmer.gz"),
                //
                Arguments.of("da_dk", "da_dk/stemmer.gz"),
                //
                Arguments.of("de_de", "de_de/stemmer.gz"),
                //
                Arguments.of("es_es", "es_es/stemmer.gz"),
                //
                Arguments.of("fa_ir", "fa_ir/stemmer.gz"),
                //
                Arguments.of("fi_fi", "fi_fi/stemmer.gz"),
                //
                Arguments.of("fr_fr", "fr_fr/stemmer.gz"),
                //
                Arguments.of("he_il", "he_il/stemmer.gz"),
                //
                Arguments.of("hu_hu", "hu_hu/stemmer.gz"),
                //
                Arguments.of("it_it", "it_it/stemmer.gz"),
                //
                Arguments.of("nb_no", "nb_no/stemmer.gz"),
                //
                Arguments.of("nl_nl", "nl_nl/stemmer.gz"),
                //
                Arguments.of("nn_no", "nn_no/stemmer.gz"),
                //
                Arguments.of("pl_pl", "pl_pl/stemmer.gz"),
                //
                Arguments.of("pt_pt", "pt_pt/stemmer.gz"),
                //
                Arguments.of("ru_ru", "ru_ru/stemmer.gz"),
                //
                Arguments.of("sv_se", "sv_se/stemmer.gz"),
                //
                Arguments.of("uk_ua", "uk_ua/stemmer.gz"),
                //
                Arguments.of("us_uk", "us_uk/stemmer.gz"),
                //
                Arguments.of("yi", "yi/stemmer.gz"));
    }

    @Nested
    @DisplayName("Remark-aware fixture workflow")
    final class RemarkAwareFixtureWorkflow {

        /**
         * Verifies that the CLI can compile the dedicated remark-aware test dictionary,
         * create nested output directories, preserve expected lookup behavior, and
         * store canonical stems when {@code --store-original} is enabled.
         *
         * @throws IOException if reading or writing fails
         */
        @Test
        @DisplayName("CLI should compile the remark-aware fixture and preserve expected lookups")
        void shouldCompileRemarkAwareFixtureAndPreserveExpectedLookups() throws IOException {
            final Path inputFile = copyResourceToTemporaryFile(REMARK_AWARE_DICTIONARY_RESOURCE,
                    "remark-aware-dictionary.txt");
            final Path outputFile = tempDir.resolve("fixture").resolve("nested").resolve("fixture.dat.gz");

            final CommandResult result = runWithCapturedStandardError("--input", inputFile.toString(), "--output",
                    outputFile.toString(), "--reduction-mode", DEFAULT_REDUCTION_MODE.name(), "--store-original");

            assertEquals(0, result.exitCode(), "Fixture compilation must succeed. stderr:\n" + result.standardError());
            assertTrue(Files.exists(outputFile), "The CLI must create the nested output artifact.");
            assertTrue(Files.isDirectory(outputFile.getParent()), "The CLI must create missing parent directories.");
            assertTrue(Files.size(outputFile) > 0L, "The compiled output artifact must not be empty.");
            assertGzipCompressed(outputFile);

            final FrequencyTrie<String> trie = StemmerPatchTrieBinaryIO.read(outputFile);

            assertVariantResolvesToExpectedStem(trie, "running", Set.of("run"));
            assertVariantResolvesToExpectedStem(trie, "walked", Set.of("walk"));
            assertVariantResolvesToExpectedStem(trie, "cities", Set.of("city"));
            assertVariantResolvesToExpectedStem(trie, "cafés", Set.of("café"));
            assertVariantResolvesToExpectedStem(trie, "played", Set.of("play"));

            assertAll(
                    () -> assertEquals(PatchCommandEncoder.NOOP_PATCH, trie.get("run"),
                            "Stored canonical stem 'run' must resolve through the no-op patch."),
                    () -> assertEquals(PatchCommandEncoder.NOOP_PATCH, trie.get("walk"),
                            "Stored canonical stem 'walk' must resolve through the no-op patch."),
                    () -> assertEquals(PatchCommandEncoder.NOOP_PATCH, trie.get("city"),
                            "Stored canonical stem 'city' must resolve through the no-op patch."),
                    () -> assertEquals(PatchCommandEncoder.NOOP_PATCH, trie.get("café"),
                            "Stored canonical stem 'café' must resolve through the no-op patch."),
                    () -> assertEquals("run", PatchCommandEncoder.apply("run", trie.get("run")),
                            "Stored canonical stem 'run' must reconstruct to itself."),
                    () -> assertEquals("café", PatchCommandEncoder.apply("café", trie.get("café")),
                            "Stored canonical stem 'café' must reconstruct to itself."));
        }

        /**
         * Verifies that the CLI rejects an already existing output path unless
         * overwrite is explicitly enabled.
         *
         * @throws IOException if reading or writing fails
         */
        @Test
        @DisplayName("CLI should require overwrite before replacing an existing output artifact")
        void shouldRequireOverwriteForExistingOutput() throws IOException {
            final Path inputFile = copyResourceToTemporaryFile(REMARK_AWARE_DICTIONARY_RESOURCE,
                    "remark-aware-dictionary-overwrite.txt");
            final Path outputFile = tempDir.resolve("fixture").resolve("overwrite").resolve("fixture.dat.gz");

            final CommandResult firstRun = runWithCapturedStandardError("--input", inputFile.toString(), "--output",
                    outputFile.toString(), "--reduction-mode", DEFAULT_REDUCTION_MODE.name());

            assertEquals(0, firstRun.exitCode(),
                    "Initial fixture compilation must succeed. stderr:\n" + firstRun.standardError());
            assertTrue(Files.exists(outputFile), "The initial compilation must create the output artifact.");
            assertTrue(Files.size(outputFile) > 0L, "The initial output artifact must not be empty.");

            final CommandResult secondRunWithoutOverwrite = runWithCapturedStandardError("--input",
                    inputFile.toString(), "--output", outputFile.toString(), "--reduction-mode",
                    DEFAULT_REDUCTION_MODE.name());

            assertEquals(1, secondRunWithoutOverwrite.exitCode(),
                    "Compilation without overwrite must fail when the output file already exists.");
            assertFalse(secondRunWithoutOverwrite.standardError().isBlank(),
                    "The CLI must report a meaningful error when overwrite is not enabled.");

            final CommandResult thirdRunWithOverwrite = runWithCapturedStandardError("--input", inputFile.toString(),
                    "--output", outputFile.toString(), "--reduction-mode", DEFAULT_REDUCTION_MODE.name(),
                    "--overwrite");

            assertEquals(0, thirdRunWithOverwrite.exitCode(),
                    "Compilation with overwrite must succeed. stderr:\n" + thirdRunWithOverwrite.standardError());
            assertTrue(Files.exists(outputFile), "Overwrite compilation must preserve the output artifact.");
            assertTrue(Files.size(outputFile) > 0L, "Overwrite compilation must produce a non-empty artifact.");
            assertGzipCompressed(outputFile);

            final FrequencyTrie<String> trie = StemmerPatchTrieBinaryIO.read(outputFile);
            assertVariantResolvesToExpectedStem(trie, "running", Set.of("run"));
            assertVariantResolvesToExpectedStem(trie, "walked", Set.of("walk"));
        }

        /**
         * Verifies one representative fixture word end to end.
         *
         * @param trie          compiled and reloaded trie
         * @param word          probe word
         * @param expectedStems acceptable expected stems
         */
        private void assertVariantResolvesToExpectedStem(final FrequencyTrie<String> trie, final String word,
                final Set<String> expectedStems) {
            final String preferredPatch = trie.get(word);
            final Set<String> actualStems = reconstructAllStemCandidates(trie, word);

            assertAll(
                    () -> assertNotNull(preferredPatch,
                            "A preferred patch must be available for fixture word '" + word + "'."),
                    () -> assertEquals(expectedStems, actualStems,
                            "Fixture word '" + word + "' must preserve all expected stem candidates."),
                    () -> assertTrue(
                            expectedStems.contains(
                                    PatchCommandEncoder.apply(word, preferredPatch, trie.traversalDirection())),
                            "The preferred stem must be one of the acceptable stems for fixture word '" + word + "'."));
        }
    }

    @Nested
    @DisplayName("Bundled project dictionary workflows")
    final class BundledProjectDictionaryWorkflows {

        /**
         * Verifies that the CLI can compile each bundled project dictionary, create a
         * compressed artifact, reload it, and preserve representative variant stemming
         * behavior derived from the source dictionary itself at the level of acceptable
         * reconstructed candidates.
         *
         * <p>
         * Representative probes are derived directly from the same bundled source
         * dictionary that is being compiled. Items containing Unicode whitespace are
         * intentionally ignored by the representative-probe helper because the current
         * probe policy does not yet support multi-token dictionary items.
         * </p>
         *
         * @param scenario     scenario identifier
         * @param resourcePath bundled dictionary resource path
         * @throws IOException if reading or writing fails
         */
        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("org.egothor.stemmer.CompileIntegrationTest#bundledDictionaryCases")
        @DisplayName("CLI should compile bundled project dictionaries and preserve representative variant semantics")
        void shouldCompileBundledProjectDictionaryAndPreserveRepresentativeVariantSemantics(final String scenario,
                final String resourcePath) throws IOException {
            final Path inputFile = copyResourceToTemporaryFile(resourcePath, scenario + "-stemmer.gz");
            final Path outputFile = tempDir.resolve("bundled").resolve(scenario).resolve("compiled.dat.gz");

            final CommandResult result = runWithCapturedStandardError("--input", inputFile.toString(), "--output",
                    outputFile.toString(), "--reduction-mode", DEFAULT_REDUCTION_MODE.name());

            assertEquals(0, result.exitCode(), "Bundled dictionary compilation must succeed for " + scenario
                    + ". stderr:\n" + result.standardError());
            assertTrue(Files.exists(outputFile), "The CLI must create the output artifact for " + scenario + '.');
            assertTrue(Files.size(outputFile) > 0L, "The output artifact must not be empty for " + scenario + '.');
            assertGzipCompressed(outputFile);

            final FrequencyTrie<String> trie = StemmerPatchTrieBinaryIO.read(outputFile);
            final Map<String, Set<String>> representativeStemsByVariant = readRepresentativeVariantExpectations(
                    resourcePath, REPRESENTATIVE_VARIANT_LIMIT);

            assertFalse(representativeStemsByVariant.isEmpty(), "The bundled dictionary must provide at least one "
                    + "representative variant without Unicode whitespace for " + scenario + '.');

            for (Map.Entry<String, Set<String>> entry : representativeStemsByVariant.entrySet()) {
                final String variant = entry.getKey().toLowerCase(Locale.ROOT);
                final Set<String> expectedStems = entry.getValue().stream().map(s -> s.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
                final String preferredPatch = trie.get(variant);
                final Set<String> actualStems = reconstructAllStemCandidates(trie, variant);
                final String preferredStem = preferredPatch == null ? null
                        : PatchCommandEncoder.apply(variant, preferredPatch, trie.traversalDirection());

                assertAll(
                        () -> assertNotNull(preferredPatch,
                                "A preferred patch must be available for representative variant '" + variant + "' in "
                                        + scenario + '.'),
                        () -> assertFalse(actualStems.isEmpty(),
                                "At least one stem candidate must be returned for representative variant '" + variant
                                        + "' in " + scenario + '.'),
                        () -> assertTrue(expectedStems.stream().anyMatch(actualStems::contains),
                                "At least one acceptable stem must be preserved for representative variant '" + variant
                                        + "' in " + scenario + ". Expected one of=" + expectedStems + ", actual="
                                        + actualStems),
                        () -> {
                            if (expectedStems.size() == 1 && actualStems.size() == 1) {
                                assertEquals(expectedStems.iterator().next(), preferredStem,
                                        "The preferred stem must match the only expected surviving stem for "
                                                + "representative variant '" + variant + "' in " + scenario + '.');
                            } else {
                                assertTrue(expectedStems.contains(preferredStem) || actualStems.contains(preferredStem),
                                        "The preferred stem must remain among the reconstructed candidates for "
                                                + "representative variant '" + variant + "' in " + scenario
                                                + ". Preferred=" + preferredStem + ", actual=" + actualStems);
                            }
                        });
            }
        }
    }

    /**
     * Copies one classpath resource to a temporary file so that the CLI is
     * exercised through its real file-based contract.
     *
     * @param resourcePath classpath resource path
     * @param fileName     target temporary file name
     * @return copied temporary file
     * @throws IOException if the resource cannot be found or copied
     */
    private Path copyResourceToTemporaryFile(final String resourcePath, final String fileName) throws IOException {
        final Path targetFile = tempDir.resolve(fileName);
        final Path parentDirectory = targetFile.toAbsolutePath().getParent();

        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        try (InputStream inputStream = openResource(resourcePath)) {
            Files.copy(inputStream, targetFile);
        }

        return targetFile;
    }

    /**
     * Opens one classpath resource.
     *
     * @param resourcePath classpath resource path
     * @return opened input stream
     * @throws IOException if the resource cannot be found
     */
    private static InputStream openResource(final String resourcePath) throws IOException {
        final InputStream inputStream = CompileIntegrationTest.class.getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Classpath resource not found: " + resourcePath);
        }
        return inputStream;
    }

    /**
     * Reads representative variant expectations from a bundled project dictionary.
     *
     * <p>
     * This helper scans the source dictionary as UTF-8 text and derives
     * representative stem-to-variant expectations directly from that bundled
     * source. Only dictionary items that do not contain Unicode whitespace are
     * considered eligible representative probes. This keeps the multidictionary
     * integration assertions aligned with the current single-token probe policy
     * while still validating the CLI against the real shipped dictionaries and
     * their actual script repertoire.
     * </p>
     *
     * <p>
     * The bundled dictionary format is expected to be tab-separated values, meaning
     * that columns are separated by the tab character:
     * </p>
     *
     * <pre>
     * stem	variant1	variant2 ...
     * </pre>
     *
     * <p>
     * Lines beginning with comment prefixes or blank lines are ignored. Canonical
     * stems are intentionally excluded from the expectation map unless they also
     * appear as distinct variants on a source line. Dictionary items containing any
     * Unicode whitespace are intentionally ignored by this representative-probe
     * helper.
     * </p>
     *
     * @param resourcePath bundled dictionary resource path
     * @param limit        maximum number of representative variants to collect
     * @return representative variants mapped to their acceptable stems
     * @throws IOException if reading fails
     */
    private static Map<String, Set<String>> readRepresentativeVariantExpectations(final String resourcePath,
            final int limit) throws IOException {
        final Map<String, Set<String>> expectations = new LinkedHashMap<String, Set<String>>();

        try (InputStream inputStream = openResource(resourcePath);
                InputStream decompressedStream = new GZIPInputStream(inputStream);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(decompressedStream, BUNDLED_PROBE_SCAN_CHARSET))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (expectations.size() >= limit) {
                    break;
                }

                final String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith("//")) {
                    continue;
                }

                final String[] tokens = trimmedLine.split("\\t+");
                if (tokens.length < 2) {
                    continue;
                }

                final String stem = tokens[0];
                if (containsWhitespaceCharacter(stem)) {
                    continue;
                }

                for (int index = 1; index < tokens.length && expectations.size() < limit; index++) {
                    final String variant = tokens[index];

                    if (containsWhitespaceCharacter(variant) || variant.equals(stem)) {
                        continue;
                    }

                    registerExpectedStem(expectations, variant, stem);
                }
            }
        }

        return expectations;
    }

    /**
     * Determines whether one token contains any Unicode whitespace character.
     *
     * @param token token to inspect
     * @return {@code true} when the token contains at least one whitespace
     *         character
     */
    private static boolean containsWhitespaceCharacter(final String token) {
        if (token == null) {
            return false;
        }

        for (int index = 0; index < token.length(); index++) {
            if (Character.isWhitespace(token.charAt(index))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Registers one acceptable stem for one input word.
     *
     * @param expectedStemsByWord expectation map
     * @param word                input word
     * @param stem                acceptable stem
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
     * Reconstructs all stem candidates returned by the trie for one input word.
     *
     * @param trie compiled trie
     * @param word input word
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
     * Verifies that one compiled artifact starts with the standard GZip magic
     * header.
     *
     * @param artifactFile compiled artifact file
     * @throws IOException if the file cannot be read
     */
    private static void assertGzipCompressed(final Path artifactFile) throws IOException {
        final byte[] bytes = Files.readAllBytes(artifactFile);

        assertTrue(bytes.length >= 2, "A GZip artifact must contain at least the two magic header bytes.");
        assertEquals(0x1F, bytes[0] & 0xFF, "The first GZip magic byte must match.");
        assertEquals(0x8B, bytes[1] & 0xFF, "The second GZip magic byte must match.");
    }

    /**
     * Executes {@link Compile#run(String...)} while capturing {@code System.err}.
     *
     * @param arguments CLI arguments
     * @return captured command result
     */
    private static CommandResult runWithCapturedStandardError(final String... arguments) {
        final PrintStream originalStandardError = System.err;
        final ByteArrayOutputStream capturedStandardError = new ByteArrayOutputStream();

        try (PrintStream replacementStandardError = new PrintStream(capturedStandardError, true,
                StandardCharsets.UTF_8)) {
            System.setErr(replacementStandardError);
            final int exitCode = Compile.run(arguments);
            replacementStandardError.flush();
            return new CommandResult(exitCode, capturedStandardError.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(originalStandardError);
        }
    }

    /**
     * Captured CLI result.
     *
     * @param exitCode      process exit code
     * @param standardError captured standard error
     */
    private record CommandResult(int exitCode, String standardError) {
    }
}
