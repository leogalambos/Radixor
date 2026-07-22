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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Executes the essential model discovery and loading examples used by maintained documentation. */
@Tag("documentation")
@Tag("integration")
final class StemmerModelDocumentationExamplesTest {
    /** Registry discovered from the standard test runtime classpath. */
    private static StemmerModelRegistry registry;

    /** Discovers the documented models once for this example suite. */
    @BeforeAll
    static void discoverDocumentedModels() throws IOException {
        registry = StemmerModelRegistry.fromContextClassLoader();
    }

    /** Verifies that language-oriented loading retains the documented Polish default. */
    @Test
    @DisplayName("Documentation example loads the default Polish UniMorph model")
    void loadsDefaultPolishModel() throws IOException {
        final FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadCompiled(
                StemmerPatchTrieLoader.Language.PL_PL,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        final CompiledPatchCommand command = trie.get("koty");
        assertNotNull(command);
        assertEquals("kot", command.apply("koty"));
        assertEquals("pl-pl-unimorph",
                registry.requireDefault(StemmerPatchTrieLoader.Language.PL_PL).id());
    }

    /** Verifies exact PoliMorf descriptor selection and its packaged runtime metadata. */
    @Test
    @DisplayName("Documentation example selects PoliMorf by exact model ID")
    void selectsExplicitPolimorfModel() {
        final StemmerModelDescriptor descriptor = registry.require("pl-pl-polimorf");
        assertEquals("PL_PL", descriptor.language().name());
        assertEquals("org/egothor/stemmer/models/pl-pl-polimorf/stemmer.gz", descriptor.resource());
        assertEquals("1.0.0", descriptor.version());
    }

    /** Verifies the documented explicit descriptor-to-compiled-trie API with a registered model. */
    @Test
    @DisplayName("Documentation example loads an exact descriptor into a usable trie")
    void loadsExplicitDescriptor() throws IOException {
        final StemmerModelDescriptor descriptor = registry.require("pl-pl-unimorph");
        final FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadCompiled(
                descriptor, true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        final CompiledPatchCommand command = trie.get("koty");
        assertEquals("kot", command.apply("koty"));
    }

    /** Verifies exact model-ID compiled loading without language-default fallback. */
    @Test
    @DisplayName("Documentation example loads an exact model ID into a compiled trie")
    void loadsExplicitModelId() throws IOException {
        final FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadCompiled(
                "pl-pl-unimorph", true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
        assertEquals("kot", trie.get("koty").apply("koty"));
    }

    /** Verifies null, blank, and unknown exact model-ID failure behavior. */
    @Test
    @DisplayName("Documentation example rejects invalid or unavailable explicit model IDs")
    void rejectsInvalidExplicitModelIds() {
        assertThrows(NullPointerException.class, () -> StemmerPatchTrieLoader.loadCompiled(
                (String) null, true, ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
        assertThrows(NullPointerException.class, () -> StemmerPatchTrieLoader.loadCompiled(
                (StemmerModelDescriptor) null, true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
        assertThrows(NullPointerException.class, () -> StemmerPatchTrieLoader.loadCompiled(
                registry.require("pl-pl-unimorph"), true, (ReductionMode) null));
        assertThrows(IllegalArgumentException.class, () -> StemmerPatchTrieLoader.loadCompiled(
                "  ", true, ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
        assertThrows(StemmerModelNotFoundException.class, () -> StemmerPatchTrieLoader.loadCompiled(
                "pl-pl-does-not-exist", true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
    }

    /** Verifies that two models coexist without changing default resolution. */
    @Test
    @DisplayName("Documentation example keeps both Polish models independent")
    void loadsBothPolishModelsIndependently() {
        final StemmerModelDescriptor unimorph = registry.require("pl-pl-unimorph");
        final StemmerModelDescriptor polimorf = registry.require("pl-pl-polimorf");
        final StemmerModelDescriptor defaultModel = registry.requireDefault(StemmerPatchTrieLoader.Language.PL_PL);
        assertEquals("pl-pl-unimorph", defaultModel.id());
        assertEquals(StemmerPatchTrieLoader.Language.PL_PL, unimorph.language());
        assertEquals(StemmerPatchTrieLoader.Language.PL_PL, polimorf.language());
        assertFalse(unimorph.id().equals(polimorf.id()));
    }

    /** Verifies deterministic discovery and language filtering shown in documentation. */
    @Test
    @DisplayName("Documentation example lists and filters models deterministically")
    void discoversAndFiltersModels() {
        final List<StemmerModelDescriptor> polish = registry.findByLanguage(StemmerPatchTrieLoader.Language.PL_PL);
        assertEquals(List.of("pl-pl-polimorf", "pl-pl-unimorph"),
                polish.stream().map(StemmerModelDescriptor::id).toList());
        assertEquals(registry.models().stream().sorted().toList(), registry.models());
        assertTrue(polish.stream().allMatch(model -> model.format().equals("radixor-dictionary-tsv-gzip")));
    }

    /** Verifies discovery through an explicitly supplied application class loader. */
    @Test
    @DisplayName("Documentation example discovers models through an explicit ClassLoader")
    void discoversThroughExplicitClassLoader() throws IOException {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final StemmerModelRegistry explicitRegistry = StemmerModelRegistry.fromClassLoader(classLoader);
        assertEquals("pl-pl-polimorf", explicitRegistry.require("pl-pl-polimorf").id());
        assertEquals(registry.models().stream().map(StemmerModelDescriptor::id).toList(),
                explicitRegistry.models().stream().map(StemmerModelDescriptor::id).toList());
    }

    /** Verifies the exact missing-model failure produced by an isolated empty loader. */
    @Test
    @DisplayName("Documentation example reports a model missing from an isolated ClassLoader")
    void reportsMissingModelFromIsolatedClassLoader() throws IOException {
        try (URLClassLoader isolated = new URLClassLoader(new URL[0], null)) {
            final StemmerModelRegistry emptyRegistry = StemmerModelRegistry.fromClassLoader(isolated);
            final StemmerModelNotFoundException exception = assertThrows(StemmerModelNotFoundException.class,
                    () -> emptyRegistry.require("pl-pl-polimorf"));
            assertTrue(exception.getMessage().contains(
                    "org.egothor:radixor-model-pl-pl-polimorf:<version>"));
        }
    }
}
