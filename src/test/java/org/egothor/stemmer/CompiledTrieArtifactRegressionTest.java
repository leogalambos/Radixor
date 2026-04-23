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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Regression tests for deterministic compiled trie artifacts.
 *
 * <p>
 * This suite protects the binary persistence contract of compiled tries by
 * validating committed golden GZip outputs and verifying representative
 * semantic probes after loading both historical and freshly compiled artifacts.
 *
 * <p>
 * The goal is to catch unintended changes in:
 * </p>
 * <ul>
 * <li>canonical subtree reduction</li>
 * <li>child ordering and node numbering</li>
 * <li>value ordering and frequency handling</li>
 * <li>stream layout backward readability</li>
 * <li>compressed artifact reproducibility within the active format version</li>
 * </ul>
 */
@Tag("unit")
@Tag("regression")
@Tag("determinism")
@Tag("serialization")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class CompiledTrieArtifactRegressionTest {

    /**
     * Temporary directory used for filesystem-based test operations.
     */
    @TempDir
    private Path tempDir;

    /**
     * Provides curated golden-artifact cases.
     *
     * @return parameter stream
     */
    static Stream<Arguments> artifactCases() {
        return Stream.of(
                // 01
                Arguments.of(new ArtifactCase("01-mini-ranked-store-original", "regression/sources/mini-en.stemmer",
                        "regression/golden/mini-en-ranked-storeorig.gz",
                        "regression/golden/mini-en-ranked-storeorig.gz.sha256", true,
                        ReductionSettings
                                .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                        List.of(new ProbeExpectation("running", "run", List.of("run")),
                                new ProbeExpectation("studies", "study", List.of("study")),
                                new ProbeExpectation("cities", "city", List.of("city")),
                                new ProbeExpectation("fly", "fly", List.of("fly"))))),

                // 02
                Arguments.of(new ArtifactCase("02-mini-unordered-store-original", "regression/sources/mini-en.stemmer",
                        "regression/golden/mini-en-unordered-storeorig.gz",
                        "regression/golden/mini-en-unordered-storeorig.gz.sha256", true,
                        ReductionSettings
                                .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_UNORDERED_GET_ALL_RESULTS),
                        List.of(new ProbeExpectation("running", "run", List.of("run")),
                                new ProbeExpectation("studying", "study", List.of("study")),
                                new ProbeExpectation("stopped", "stop", List.of("stop")),
                                new ProbeExpectation("fly", "fly", List.of("fly"))))),

                // 03
                Arguments.of(new ArtifactCase("03-branching-ranked-no-store-original",
                        "regression/sources/branching-en.stemmer",
                        "regression/golden/branching-en-ranked-no-storeorig.gz",
                        "regression/golden/branching-en-ranked-no-storeorig.gz.sha256", false,
                        ReductionSettings
                                .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                        List.of(new ProbeExpectation("connected", "connect", List.of("connect")),
                                new ProbeExpectation("collecting", "collect", List.of("collect")),
                                new ProbeExpectation("inspection", "inspect", List.of("inspect")),
                                new ProbeExpectation("direction", "direct", List.of("direct"))))));
    }

    /**
     * Verifies that each committed golden artifact remains internally consistent,
     * matches its committed digest, and can still be read by the current binary
     * loader.
     *
     * @param artifactCase regression case
     * @throws IOException if test I/O fails
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("artifactCases")
    @DisplayName("Committed golden artifacts must remain readable and hash-stable")
    void shouldKeepGoldenArtifactReadableAndHashStable(final ArtifactCase artifactCase) throws IOException {
        final byte[] goldenArtifactBytes = RegressionArtifactSupport
                .readResourceBytes(artifactCase.goldenArtifactResource());
        final String expectedSha256 = RegressionArtifactSupport.readSha256Resource(artifactCase.sha256Resource());
        final FrequencyTrie<String> trie = StemmerPatchTrieBinaryIO.read(new ByteArrayInputStream(goldenArtifactBytes));

        assertAll(
                () -> assertEquals(expectedSha256, RegressionArtifactSupport.sha256Hex(goldenArtifactBytes),
                        "Golden artifact SHA-256 must match its committed sidecar hash."),
                () -> assertGoldenArtifactSemanticProbes(trie, artifactCase));
    }

    /**
     * Verifies in-process determinism independently of the checked-in golden file
     * by compiling the same dictionary twice and requiring identical artifact
     * bytes.
     *
     * @param artifactCase regression case
     * @throws IOException if test I/O fails
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("artifactCases")
    @DisplayName("Compilation must be deterministic across repeated runs")
    void shouldProduceIdenticalBytesAcrossRepeatedCompilation(final ArtifactCase artifactCase) throws IOException {
        final Path sourcePath = RegressionArtifactSupport.copyResourceToFile(artifactCase.sourceResource(),
                this.tempDir.resolve(artifactCase.id() + "-repeat.stemmer"));

        final byte[] firstArtifactBytes = RegressionArtifactSupport.compileToArtifactBytes(sourcePath,
                artifactCase.storeOriginal(), artifactCase.reductionSettings());

        final byte[] secondArtifactBytes = RegressionArtifactSupport.compileToArtifactBytes(sourcePath,
                artifactCase.storeOriginal(), artifactCase.reductionSettings());

        org.junit.jupiter.api.Assertions.assertArrayEquals(firstArtifactBytes, secondArtifactBytes,
                "Two consecutive compilations of the same source must produce identical artifact bytes.");
    }

    /**
     * Verifies that the produced artifact can be loaded back and preserves expected
     * representative stemming behavior for each regression case.
     *
     * @param artifactCase regression case
     * @throws IOException if test I/O fails
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("artifactCases")
    @DisplayName("Golden-regression artifacts must remain semantically valid after reload")
    void shouldPreserveRepresentativeSemanticProbes(final ArtifactCase artifactCase) throws IOException {
        final Path sourcePath = RegressionArtifactSupport.copyResourceToFile(artifactCase.sourceResource(),
                this.tempDir.resolve(artifactCase.id() + "-semantic.stemmer"));
        final Path actualArtifactPath = this.tempDir.resolve(artifactCase.id() + "-semantic.gz");

        RegressionArtifactSupport.compileToArtifact(sourcePath, artifactCase.storeOriginal(),
                artifactCase.reductionSettings(), actualArtifactPath);

        final FrequencyTrie<String> trie = StemmerPatchTrieBinaryIO.read(actualArtifactPath);

        for (ProbeExpectation probe : artifactCase.probes()) {
            final String[] allPatchCommands = trie.getAll(probe.word());
            final String preferredPatchCommand = trie.get(probe.word());
            final String preferredStem = preferredPatchCommand == null ? null
                    : PatchCommandEncoder.apply(probe.word(), preferredPatchCommand, trie.traversalDirection());
            final Set<String> allStems = reconstructStemCandidates(trie, probe.word(), allPatchCommands);

            assertAll(
                    () -> assertFalse(allPatchCommands.length == 0,
                            "Representative probe must produce at least one result for word: " + probe.word()),

                    () -> assertEquals(probe.preferredStem(), preferredStem,
                            "Preferred stem mismatch for representative probe word: " + probe.word()),

                    () -> assertTrue(allStems.containsAll(probe.acceptableStems()),
                            "All acceptable stems must be present in getAll() for representative probe word: "
                                    + probe.word()));
        }
    }

    /**
     * Reconstructs all stem candidates for one surface word from serialized patch
     * commands returned by the compiled trie.
     *
     * @param word          surface word
     * @param patchCommands serialized patch commands
     * @return reconstructed stem candidates
     */
    private static Set<String> reconstructStemCandidates(final FrequencyTrie<String> trie, final String word,
            final String[] patchCommands) {
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
     * Verifies representative semantic probes against one already loaded trie.
     *
     * @param trie         trie to inspect
     * @param artifactCase regression case providing the expected probes
     */
    private static void assertGoldenArtifactSemanticProbes(final FrequencyTrie<String> trie,
            final ArtifactCase artifactCase) {
        for (ProbeExpectation probe : artifactCase.probes()) {
            final String[] allPatchCommands = trie.getAll(probe.word());
            final String preferredPatchCommand = trie.get(probe.word());
            final String preferredStem = preferredPatchCommand == null ? null
                    : PatchCommandEncoder.apply(probe.word(), preferredPatchCommand, trie.traversalDirection());
            final Set<String> allStems = reconstructStemCandidates(trie, probe.word(), allPatchCommands);

            assertAll(
                    () -> assertFalse(allPatchCommands.length == 0,
                            "Representative probe must produce at least one result for word: " + probe.word()),
                    () -> assertEquals(probe.preferredStem(), preferredStem,
                            "Preferred stem mismatch for representative probe word: " + probe.word()),
                    () -> assertTrue(allStems.containsAll(probe.acceptableStems()),
                            "All acceptable stems must be present in getAll() for representative probe word: "
                                    + probe.word()));
        }
    }

    /**
     * Immutable regression case definition.
     *
     * @param id                     stable case identifier
     * @param sourceResource         dictionary source classpath resource
     * @param goldenArtifactResource committed golden artifact classpath resource
     * @param sha256Resource         committed SHA-256 sidecar classpath resource
     * @param storeOriginal          whether original stems are stored as no-op
     *                               mappings
     * @param reductionSettings      reduction settings used for compilation
     * @param probes                 representative semantic probes
     */
    private record ArtifactCase(String id, String sourceResource, String goldenArtifactResource, String sha256Resource,
            boolean storeOriginal, ReductionSettings reductionSettings, List<ProbeExpectation> probes) {

        /**
         * Returns the stable display identifier.
         *
         * @return stable display identifier
         */
        @Override
        public String toString() {
            return this.id;
        }
    }

    /**
     * Immutable semantic probe definition.
     *
     * @param word            source word to stem
     * @param preferredStem   expected preferred stem from
     *                        {@link FrequencyTrie#get(String)}
     * @param acceptableStems expected values that must be present in
     *                        {@link FrequencyTrie#getAll(String)}
     */
    private record ProbeExpectation(String word, String preferredStem, List<String> acceptableStems) {
    }
}
