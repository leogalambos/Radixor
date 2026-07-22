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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Tests deterministic model discovery and language/model separation. */
@Tag("integration")
final class StemmerModelRegistryTest {
    /** Verifies discovery, ordering, coexistence, and the Polish default. */
    @Test
    @DisplayName("Registry discovers all models deterministically and keeps UniMorph as Polish default")
    void discoversModelsDeterministically() throws IOException {
        final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
        final List<StemmerModelDescriptor> polish = registry.findByLanguage(StemmerPatchTrieLoader.Language.PL_PL);
        assertEquals(List.of("pl-pl-polimorf", "pl-pl-unimorph"),
                polish.stream().map(StemmerModelDescriptor::id).toList());
        assertEquals("pl-pl-unimorph", registry.requireDefault(StemmerPatchTrieLoader.Language.PL_PL).id());
        assertEquals("pl-pl-polimorf", registry.require("pl-pl-polimorf").id());
        assertEquals(registry.models().stream().sorted().toList(), registry.models());
    }

    /** Verifies that missing explicit models never fall back arbitrarily. */
    @Test
    @DisplayName("Missing explicit model reports its exact dependency")
    void rejectsMissingModel() throws IOException {
        final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
        final StemmerModelNotFoundException exception = assertThrows(StemmerModelNotFoundException.class,
                () -> registry.require("pl-pl-unknown"));
        assertTrue(exception.getMessage().contains("org.egothor:radixor-model-pl-pl-unknown:<version>"));
        assertFalse(exception.getMessage().isBlank());
    }

    /** Verifies the source-compatible language loader resolves the registered default. */
    @Test
    @Tag("slow")
    @DisplayName("Polish language loading resolves the UniMorph model")
    void loadsDefaultPolishModel() throws IOException {
        final FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadCompiled(
                StemmerPatchTrieLoader.Language.PL_PL, true,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
        assertTrue(trie.metadata() != null);
    }
}
