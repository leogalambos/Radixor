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
package org.egothor.stemmer.benchmark.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.egothor.stemmer.StemmerPatchTrieLoader.Language;
import org.egothor.stemmer.StemmerModelRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression tests for independent dictionary-resource and enumeration reconciliation. */
@Tag("integration")
@DisplayName("Authoritative Radixor language universe")
final class LanguageUniverseTest {
    /** Temporary resource tree. */ @TempDir Path temporaryDirectory;

    /** Verifies every production language has exactly one registered default model. */
    @Test @DisplayName("Production resources reconcile with every language enumeration value")
    void productionResourcesReconcile() throws IOException {
        final StemmerModelRegistry registry = StemmerModelRegistry.fromContextClassLoader();
        assertEquals(Language.values().length,
                java.util.Arrays.stream(Language.values()).map(registry::requireDefault).count());
        assertEquals("da-dk-default", registry.requireDefault(Language.DA_DK).id());
        assertEquals("yi-default", registry.requireDefault(Language.YI).id());
    }

    /** Verifies a missing enumerated resource produces an exact diagnostic. */
    @Test @DisplayName("Missing enumeration resources fail validation")
    void missingResourceFails() throws IOException {
        final Path first = this.temporaryDirectory.resolve(Language.CS_CZ.resourceDirectory());
        Files.createDirectories(first); Files.createFile(first.resolve("stemmer.gz"));
        final IOException exception = assertThrows(IOException.class,
                () -> LanguageUniverse.discover(this.temporaryDirectory));
        assertTrue(exception.getMessage().contains("DA_DK"));
    }

    /** Verifies an unenumerated dictionary directory is rejected. */
    @Test @DisplayName("Unmapped dictionary directories fail validation")
    void extraResourceFails() throws IOException {
        for (Language language : Language.values()) {
            final Path directory = this.temporaryDirectory.resolve(language.resourceDirectory());
            Files.createDirectories(directory); Files.createFile(directory.resolve("stemmer.gz"));
        }
        final Path extra = this.temporaryDirectory.resolve("unmapped_language");
        Files.createDirectories(extra); Files.createFile(extra.resolve("stemmer.gz"));
        final IOException exception = assertThrows(IOException.class,
                () -> LanguageUniverse.discover(this.temporaryDirectory));
        assertTrue(exception.getMessage().contains("unmapped_language"));
    }
}
