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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.egothor.stemmer.StemmerPatchTrieLoader.Language;
import org.junit.jupiter.api.Test;

/** Tests explicit legacy and complete language-coverage publication modes. */
final class StemmingQualityDocumentationPublisherTest {
    private static final String LEGACY_SHA256 =
            "85763189eab4d0fbb047c2d5d3554c66abf9732182bd0d8fd758d7aef680e66f";

    /** Verifies a future non-legacy report cannot silently omit one default. */
    @Test
    void rejectsIncompleteCurrentLanguageUniverse() {
        final Set<String> languages = new HashSet<>();
        for (Language language : Language.values()) {
            languages.add(language.name());
        }
        languages.remove(Language.ZU_ZA.name());

        assertThrows(IllegalStateException.class,
                () -> StemmingQualityDocumentationPublisher.publicationLanguageUniverse(
                        languages, false, Path.of("ignored.csv"), Path.of("docs"), "ignored"));
    }

    /** Verifies optional PoliMorf rows cannot leak into the default-language snapshot. */
    @Test
    void rejectsCompleteCurrentLanguageUniverseWithOptionalPolimorf() {
        final Set<String> languages = new HashSet<>();
        for (Language language : Language.values()) {
            languages.add(language.name());
        }
        languages.add("pl-pl-polimorf");

        assertThrows(IllegalStateException.class,
                () -> StemmingQualityDocumentationPublisher.publicationLanguageUniverse(
                        languages, true, Path.of("ignored.csv"), Path.of("docs"), "ignored"));
    }

    /** Verifies update mode never republishes the frozen 20-language snapshot. */
    @Test
    void rejectsLegacySnapshotInUpdateMode() {
        final Path documentationRoot = Path.of("docs");
        final Path source = documentationRoot.resolve("benchmarks/data/stemming-quality.csv");

        assertThrows(IllegalStateException.class,
                () -> StemmingQualityDocumentationPublisher.publicationLanguageUniverse(
                        legacyLanguages(), true, source, documentationRoot, LEGACY_SHA256));
    }

    /** Verifies legacy verification is bound to the historical path and checksum. */
    @Test
    void acceptsOnlyChecksumBoundFrozenLegacyVerification() {
        final Path documentationRoot = Path.of("docs");
        final Path source = documentationRoot.resolve("benchmarks/data/stemming-quality.csv");

        assertEquals(20, StemmingQualityDocumentationPublisher.publicationLanguageUniverse(
                legacyLanguages(), false, source, documentationRoot, LEGACY_SHA256).size());
        assertThrows(IllegalStateException.class,
                () -> StemmingQualityDocumentationPublisher.publicationLanguageUniverse(
                        legacyLanguages(), false, source, documentationRoot, "0".repeat(64)));
        assertThrows(IllegalStateException.class,
                () -> StemmingQualityDocumentationPublisher.publicationLanguageUniverse(
                        legacyLanguages(), false, Path.of("other", "stemming-quality.csv"),
                        documentationRoot, LEGACY_SHA256));
    }

    /** Verifies frozen rows survive and degenerate pair classes are explicitly inapplicable. */
    @Test
    void activeSnapshotPreservesLegacyRowsAndRendersZeroDenominators() throws IOException {
        final Path documentationRoot = Path.of("docs");
        final Path archive = documentationRoot.resolve("benchmarks/data/stemming-quality.csv");
        final Path active = documentationRoot.resolve("benchmarks/data/stemming-quality-2026-09-11.csv");
        final Path overview = documentationRoot.resolve("benchmarks/index.md");
        final Set<String> activeRows = new HashSet<>(Files.readAllLines(active, StandardCharsets.UTF_8));
        final List<String> archiveRows = Files.readAllLines(archive, StandardCharsets.UTF_8);
        final String originalOverview = Files.readString(overview, StandardCharsets.UTF_8);

        assertTrue(activeRows.containsAll(archiveRows.subList(1, archiveRows.size())));
        assertDoesNotThrow(() -> StemmingQualityDocumentationPublisher.publish(
                active, documentationRoot, false, "stemming-quality-2026-09-11.csv"));
        assertTrue(originalOverview.contains(
                "<!-- STEMMING-QUALITY-OVERVIEW:END -->\n\n## Java Radixor and Snowball runtime comparison"));
        assertDoesNotThrow(() -> StemmingQualityDocumentationPublisher.publish(
                active, documentationRoot, false, "stemming-quality-2026-09-11.csv"));
        assertEquals(originalOverview, Files.readString(overview, StandardCharsets.UTF_8));

        final String manx = Files.readString(
                documentationRoot.resolve("benchmarks/languages/gv-im.md"), StandardCharsets.UTF_8);
        assertTrue(manx.contains("balanced-accuracy ranking is **n/a**"));
        assertTrue(manx.contains("|0 / 0|"));
        assertTrue(manx.contains("|n/a|Radixor|n/a|"));
    }

    /** Returns the exact frozen default-language set. */
    private Set<String> legacyLanguages() {
        return Set.of(
                "CS_CZ", "DA_DK", "DE_DE", "ES_ES", "FA_IR", "FI_FI", "FR_FR", "HE_IL", "HU_HU", "IT_IT",
                "NB_NO", "NL_NL", "NN_NO", "PL_PL", "PT_PT", "RU_RU", "SV_SE", "UK_UA", "US_UK", "YI");
    }
}
