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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.IntFunction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Deterministic fuzz-style tests for trie compilation and generated stemming
 * dictionaries.
 *
 * <p>
 * These tests exercise bounded pseudo-random inputs with fixed seeds. The suite
 * focuses on invariants that are meaningful for CI: compilation must remain
 * stable, lookups must remain deterministic, binary round-trips must preserve
 * observable behavior, and generated patch commands must reconstruct one of the
 * stems declared by the source dictionary.
 */
@DisplayName("Deterministic fuzz-style trie and stemmer compilation")
@Tag("unit")
@Tag("fuzz")
@Tag("trie")
@Tag("stemming")
class FuzzStemmerAndTrieCompilationTest {

    /**
     * Shared array factory used by generated tries.
     */
    private static final IntFunction<String[]> ARRAY_FACTORY = String[]::new;

    /**
     * Binary codec used for generic trie round-trip assertions.
     */
    private static final FrequencyTrie.ValueStreamCodec<String> STRING_CODEC = new FrequencyTrie.ValueStreamCodec<String>() {

        @Override
        public void write(final DataOutputStream dataOutput, final String value) throws IOException {
            dataOutput.writeUTF(value);
        }

        @Override
        public String read(final DataInputStream dataInput) throws IOException {
            return dataInput.readUTF();
        }
    };

    /**
     * Temporary directory for generated dictionaries and binary artifacts.
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * Verifies that bounded pseudo-random trie insertions compile deterministically
     * and preserve observable semantics across rebuild, binary serialization, and
     * builder reconstruction.
     *
     * @throws IOException if an unexpected binary I/O failure occurs
     */
    @Test
    @DisplayName("generated trie insertions should preserve semantics across compilation forms")
    void generatedTrieInsertionsShouldPreserveSemanticsAcrossCompilationForms() throws IOException {
        for (ReductionMode reductionMode : ReductionMode.values()) {
            final ReductionSettings reductionSettings = ReductionSettings.withDefaults(reductionMode);
            for (FuzzTestSupport.TrieCompilationScenario scenario : FuzzTestSupport.trieCompilationScenarios()
                    .toList()) {
                final FrequencyTrie<String> compiled = buildTrie(scenario, reductionSettings);
                final FrequencyTrie<String> rebuilt = buildTrie(scenario, reductionSettings);
                final FrequencyTrie<String> roundTripped = roundTrip(compiled);
                final FrequencyTrie<String> reconstructed = FrequencyTrieBuilders.copyOf(compiled, ARRAY_FACTORY,
                        reductionSettings).build();

                for (String key : scenario.observedKeys()) {
                    assertTrieStateEquals(compiled, rebuilt, key,
                            describeScenario("repeated compilation drifted", reductionMode, scenario, key));
                    assertTrieStateEquals(compiled, roundTripped, key,
                            describeScenario("binary round-trip drifted", reductionMode, scenario, key));
                    assertTrieLookupSemanticsEqual(compiled, reconstructed, key,
                            describeScenario("builder reconstruction drifted", reductionMode, scenario, key));
                }
            }
        }
    }

    /**
     * Verifies that generated dictionaries compile without failure and that the
     * preferred patch command for each generated word reconstructs one acceptable
     * source stem.
     *
     * @throws IOException if the generated dictionary cannot be written or read
     */
    @Test
    @DisplayName("generated dictionaries should compile and stem consistently")
    void generatedDictionariesShouldCompileAndStemConsistently() throws IOException {
        for (ReductionMode reductionMode : ReductionMode.values()) {
            for (FuzzTestSupport.StemmerDictionaryScenario scenario : FuzzTestSupport.stemmerDictionaryScenarios()
                    .toList()) {
                final Path dictionaryFile = this.temporaryDirectory
                        .resolve("fuzz-dictionary-" + reductionMode.name() + "-" + scenario.seed() + ".txt");
                Files.writeString(dictionaryFile, scenario.dictionaryContent(), StandardCharsets.UTF_8);

                final FrequencyTrie<String> trie = assertDoesNotThrow(
                        () -> StemmerPatchTrieLoader.load(dictionaryFile, true, reductionMode),
                        describeScenario("generated dictionary must compile", reductionMode, scenario, null));

                for (String word : scenario.expectedStemsByWord().keySet()) {
                    final Set<String> acceptableStems = scenario.expectedStemsByWord().get(word);
                    final String preferredPatch = trie.get(word);
                    final String[] allPatches = trie.getAll(word);

                    assertAll(
                            () -> assertTrue(preferredPatch != null && !preferredPatch.isEmpty(),
                                    describeScenario("preferred patch must exist", reductionMode, scenario, word)),
                            () -> assertTrue(allPatches.length >= 1,
                                    describeScenario("at least one patch must exist", reductionMode, scenario, word)),
                            () -> assertTrue(acceptableStems.contains(PatchCommandEncoder.apply(word, preferredPatch)),
                                    describeScenario("preferred patch reconstructed an unexpected stem",
                                            reductionMode, scenario, word)),
                            () -> assertTrue(allPatchesProduceOnlyAcceptableStems(word, allPatches, acceptableStems),
                                    describeScenario("getAll() contained a patch outside the accepted stem set",
                                            reductionMode, scenario, word)));
                }
            }
        }
    }

    /**
     * Verifies that binary persistence of generated stemmer tries preserves all
     * observable lookups for the generated vocabulary.
     *
     * @throws IOException if persistence unexpectedly fails
     */
    @Test
    @DisplayName("generated stemmer tries should survive binary persistence")
    void generatedStemmerTriesShouldSurviveBinaryPersistence() throws IOException {
        for (FuzzTestSupport.StemmerDictionaryScenario scenario : FuzzTestSupport.stemmerDictionaryScenarios()
                .toList()) {
            final Path dictionaryFile = this.temporaryDirectory.resolve("binary-fuzz-" + scenario.seed() + ".txt");
            final Path binaryFile = this.temporaryDirectory.resolve("binary-fuzz-" + scenario.seed() + ".dat.gz");

            Files.writeString(dictionaryFile, scenario.dictionaryContent(), StandardCharsets.UTF_8);

            final FrequencyTrie<String> original = StemmerPatchTrieLoader.load(dictionaryFile, true,
                    ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
            StemmerPatchTrieLoader.saveBinary(original, binaryFile);
            final FrequencyTrie<String> reloaded = StemmerPatchTrieLoader.loadBinary(binaryFile);

            for (String word : scenario.expectedStemsByWord().keySet()) {
                assertTrieStateEquals(original, reloaded, word,
                        "Binary stemmer round-trip drifted for seed=" + scenario.seed() + ", word='" + word + "'.");
            }
        }
    }

    /**
     * Builds one trie from the supplied generated scenario.
     *
     * @param scenario generated scenario
     * @param reductionSettings reduction settings
     * @return compiled trie
     */
    private static FrequencyTrie<String> buildTrie(final FuzzTestSupport.TrieCompilationScenario scenario,
            final ReductionSettings reductionSettings) {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(ARRAY_FACTORY, reductionSettings);
        for (FuzzTestSupport.TrieInsertion insertion : scenario.insertions()) {
            builder.put(insertion.key(), insertion.value(), insertion.count());
        }
        return builder.build();
    }

    /**
     * Performs a generic binary round-trip of a compiled trie.
     *
     * @param trie source trie
     * @return deserialized trie
     * @throws IOException if persistence fails
     */
    private static FrequencyTrie<String> roundTrip(final FrequencyTrie<String> trie) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        trie.writeTo(outputStream, STRING_CODEC);
        return FrequencyTrie.readFrom(new ByteArrayInputStream(outputStream.toByteArray()), ARRAY_FACTORY, STRING_CODEC);
    }

    /**
     * Compares all observable lookup views for one key.
     *
     * @param expected reference trie
     * @param actual candidate trie
     * @param key key to inspect
     * @param failureMessage assertion message
     */
    private static void assertTrieStateEquals(final FrequencyTrie<String> expected, final FrequencyTrie<String> actual,
            final String key, final String failureMessage) {
        assertAll(
                () -> assertEquals(expected.get(key), actual.get(key), failureMessage),
                () -> assertArrayEquals(expected.getAll(key), actual.getAll(key), failureMessage),
                () -> assertIterableEquals(expected.getEntries(key), actual.getEntries(key), failureMessage));
    }

    /**
     * Compares only lookup semantics that are expected to survive reconstruction
     * from a reduced compiled trie.
     *
     * <p>
     * Some reduction modes intentionally ignore absolute local frequencies when
     * identifying equivalent subtrees. Reconstructing a mutable builder from the
     * reduced compiled form and compiling it again must therefore preserve
     * observable lookup semantics, but it does not necessarily preserve original
     * local counts reported by {@link FrequencyTrie#getEntries(String)}.
     *
     * @param expected reference trie
     * @param actual candidate trie
     * @param key key to inspect
     * @param failureMessage assertion message
     */
    private static void assertTrieLookupSemanticsEqual(final FrequencyTrie<String> expected,
            final FrequencyTrie<String> actual, final String key, final String failureMessage) {
        assertAll(
                () -> assertEquals(expected.get(key), actual.get(key), failureMessage),
                () -> assertArrayEquals(expected.getAll(key), actual.getAll(key), failureMessage));
    }

    /**
     * Verifies that every patch in the array reconstructs one acceptable stem.
     *
     * @param word original surface form
     * @param patches patch commands
     * @param acceptableStems acceptable stems
     * @return {@code true} when all patches are acceptable
     */
    private static boolean allPatchesProduceOnlyAcceptableStems(final String word, final String[] patches,
            final Set<String> acceptableStems) {
        for (String patch : patches) {
            if (!acceptableStems.contains(PatchCommandEncoder.apply(word, patch))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds a contextual assertion message.
     *
     * @param prefix failure prefix
     * @param reductionMode reduction mode under test
     * @param scenario source scenario
     * @param word current word or key, may be {@code null}
     * @return contextual message
     */
    private static String describeScenario(final String prefix, final ReductionMode reductionMode, final Object scenario,
            final String word) {
        final StringBuilder builder = new StringBuilder(128);
        builder.append(prefix).append(". reductionMode=").append(reductionMode).append(", scenario=")
                .append(scenario);
        if (word != null) {
            builder.append(", token='").append(word).append('\'');
        }
        return builder.toString();
    }
}
