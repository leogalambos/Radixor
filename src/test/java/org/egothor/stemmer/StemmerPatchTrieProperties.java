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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.Set;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;

/**
 * Property-based tests for patch-command stemmer tries.
 *
 * <p>
 * These properties verify the most important semantic contract of compiled
 * stemmer dictionaries: every patch returned for a known input word must decode
 * to one of the acceptable stems declared by the source scenario, and binary
 * persistence must not alter that behavior.
 */
@Label("Stemmer patch trie properties")
@Tag("unit")
@Tag("property")
@Tag("stemming")
class StemmerPatchTrieProperties extends PropertyBasedTestSupport {

    /**
     * Verifies that every returned patch reconstructs only acceptable stems for the
     * observed word set represented by one generated stemmer scenario.
     *
     * @param scenario      generated stemmer scenario
     * @param reductionMode reduction mode
     */
    @Property(tries = 60)
    @Label("returned patches should reconstruct only acceptable stems")
    void returnedPatchesShouldReconstructOnlyAcceptableStems(@ForAll("stemmerScenarios") final StemmerScenario scenario,
            @ForAll final ReductionMode reductionMode) {
        final FrequencyTrie<String> trie = buildStemmerTrie(scenario, reductionMode, true);

        for (String observedWord : scenario.observedWords()) {
            final Set<String> acceptableStems = scenario.acceptableStemsFor(observedWord);
            final String preferredPatch = trie.get(observedWord);
            final String[] allPatches = trie.getAll(observedWord);

            assertTrue(preferredPatch != null && !preferredPatch.isEmpty(),
                    "preferred patch must exist for an observed word.");
            assertTrue(allPatches.length >= 1, "at least one patch must exist for an observed word.");
            assertTrue(acceptableStems.contains(PatchCommandEncoder.apply(observedWord, preferredPatch, trie.traversalDirection())),
                    "preferred patch reconstructed an unexpected stem.");

            final Set<String> producedStems = applyAll(trie, observedWord, allPatches);
            assertTrue(acceptableStems.containsAll(producedStems),
                    "getAll() must not expose a patch that reconstructs an undeclared stem.");

            if (acceptableStems.contains(observedWord)) {
                assertTrue(producedStems.contains(observedWord),
                        "storeOriginal semantics must preserve the original stem among returned results.");
            }
        }
    }

    /**
     * Verifies that GZip-compressed binary persistence preserves patch-command trie
     * lookups.
     *
     * @param scenario      generated stemmer scenario
     * @param reductionMode reduction mode
     */
    @Property(tries = 30)
    @Label("binary persistence should preserve patch-command trie lookups")
    void binaryPersistenceShouldPreservePatchCommandTrieLookups(
            @ForAll("stemmerScenarios") final StemmerScenario scenario, @ForAll final ReductionMode reductionMode) {
        final FrequencyTrie<String> original = buildStemmerTrie(scenario, reductionMode, true);
        final FrequencyTrie<String> roundTripped = roundTripCompressed(original);

        for (String observedWord : scenario.observedWords()) {
            assertEquals(original.get(observedWord), roundTripped.get(observedWord),
                    "preferred patch lookup drifted after persistence.");
            assertArrayEquals(original.getAll(observedWord), roundTripped.getAll(observedWord),
                    "complete patch result set drifted after persistence.");
        }
    }

    /**
     * Applies all returned patches to the supplied source word.
     *
     * @param source  source word
     * @param patches returned patches
     * @return decoded stem set
     */
    private static Set<String> applyAll(final FrequencyTrie<String> trie, final String source, final String[] patches) {
        final LinkedHashSet<String> stems = new LinkedHashSet<>();
        for (String patch : patches) {
            stems.add(PatchCommandEncoder.apply(source, patch, trie.traversalDirection()));
        }
        return stems;
    }

    /**
     * Round-trips one patch-command trie through the compressed binary helper.
     *
     * @param trie trie to persist and reload
     * @return reloaded trie
     */
    private static FrequencyTrie<String> roundTripCompressed(final FrequencyTrie<String> trie) {
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            StemmerPatchTrieBinaryIO.write(trie, byteArrayOutputStream);
            return StemmerPatchTrieBinaryIO.read(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unexpected compressed binary round-trip failure.", exception);
        }
    }
}
