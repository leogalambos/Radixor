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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Constructs and exercises a complete packaged model selected by a Gradle
 * property in a dedicated memory-sized test process.
 */
@Tag("large-model")
final class FullRuntimeModelIntegrationTest {
    /** System property containing the exact model identifier under verification. */
    private static final String MODEL_ID_PROPERTY = "radixor.test.modelId";

    /** Reduction mode used by the supported runtime construction path. */
    private static final ReductionMode REDUCTION_MODE =
            ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS;

    /**
     * Loads the entire selected resource, constructs its compiled trie, and applies
     * deterministic PoliMorf smoke fixtures when that model is selected.
     *
     * @throws IOException if discovery or complete dictionary processing fails
     */
    @Test
    @DisplayName("Complete packaged model constructs a compiled runtime trie")
    void constructsCompletePackagedRuntimeModel() throws IOException {
        final String modelId = requiredModelId();
        final long startedAt = System.nanoTime();
        final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
        final StemmerModelDescriptor descriptor = registry.require(modelId);

        assertNotNull(descriptor.source());
        assertNotNull(descriptor.classLoader().getResource(descriptor.resource()));
        final FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadCompiled(
                modelId, true, REDUCTION_MODE);
        final long elapsedNanos = System.nanoTime() - startedAt;

        assertTrue(trie.size() > 0, "The completely constructed trie must contain canonical nodes.");
        if ("pl-pl-polimorf".equals(modelId)) {
            verifyPolimorfFixtures(trie);
        }
        System.out.printf(
                "RUNTIME_MODEL_METRICS model=%s maxHeapBytes=%d elapsedMillis=%d canonicalNodes=%d%n",
                modelId, Runtime.getRuntime().maxMemory(), elapsedNanos / 1_000_000L, trie.size());
    }

    /**
     * Returns the nonblank exact model identifier supplied by the Gradle task.
     *
     * @return exact model identifier
     */
    private static String requiredModelId() {
        final String modelId = System.getProperty(MODEL_ID_PROPERTY);
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalStateException("System property " + MODEL_ID_PROPERTY + " must name a model.");
        }
        return modelId;
    }

    /**
     * Verifies stable forms selected from the immutable PoliMorf module input.
     * The first column of each source row is the expected lemma and subsequent
     * columns contain its forms.
     *
     * @param trie completely constructed PoliMorf trie
     */
    private static void verifyPolimorfFixtures(final FrequencyTrie<CompiledPatchCommand> trie) {
        assertStem(trie, "pies", "pies");
        assertStem(trie, "psami", "pies");
        assertStem(trie, "kotem", "kot");
        assertStem(trie, "zamkami", "zamek");

        final List<String> candidates = Arrays.stream(trie.getAll("mam"))
                .map(command -> command.apply("mam"))
                .toList();
        assertArrayEquals(new String[]{"mama", "mamić", "mieć"}, candidates.toArray(String[]::new));
        assertNull(trie.get("radixorbrakujacehaslo"));
        assertEquals(0, trie.getAll("radixorbrakujacehaslo").length);
    }

    /**
     * Applies the preferred compiled patch command and compares its result with a
     * reviewed source-dictionary lemma.
     *
     * @param trie PoliMorf trie
     * @param form inflected or lemma form
     * @param expectedLemma expected source-dictionary lemma
     */
    private static void assertStem(final FrequencyTrie<CompiledPatchCommand> trie, final String form,
            final String expectedLemma) {
        final CompiledPatchCommand command = trie.get(form);
        assertNotNull(command, "A reviewed PoliMorf form must have a patch command: " + form);
        assertEquals(expectedLemma, command.apply(form));
    }
}
