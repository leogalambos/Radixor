package org.egothor.stemmer.benchmark.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.egothor.stemmer.StemmerPatchTrieLoader.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression tests for independent dictionary-resource and enumeration reconciliation. */
@Tag("integration")
@DisplayName("Authoritative Radixor language universe")
final class LanguageUniverseTest {
    /** Temporary resource tree. */ @TempDir Path temporaryDirectory;

    /** Verifies every production enumeration value has exactly one bundled dictionary. */
    @Test @DisplayName("Production resources reconcile with every language enumeration value")
    void productionResourcesReconcile() throws IOException {
        final LanguageUniverse universe = LanguageUniverse.discover(Path.of("src/main/resources"));
        assertEquals(Language.values().length, universe.dictionaries().size());
        assertTrue(universe.dictionaries().containsKey(Language.DA_DK));
        assertTrue(universe.dictionaries().containsKey(Language.YI));
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
