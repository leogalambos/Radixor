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
 *******************************************************************************/
package org.egothor.stemmer.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProhibitedModelBenchmarkManifestTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsOnlyAnExactChecksumBoundModelEntry() throws IOException {
        final Path dictionary = writeDictionary("root\tvariant\n");
        final Path manifest = writeManifest(dictionary, sha256(dictionary));

        final ProhibitedModelBenchmarkManifest.Entry entry =
                ProhibitedModelBenchmarkManifest.readEntry(manifest, "aa-default");

        assertEquals("AA", entry.language());
        assertEquals(dictionary, entry.dictionary());
        assertThrows(IllegalArgumentException.class,
                () -> ProhibitedModelBenchmarkManifest.readEntry(manifest, "bb-default"));
    }

    @Test
    void rejectsSelectedDictionaryMutation() throws IOException {
        final Path dictionary = writeDictionary("root\tvariant\n");
        final Path manifest = writeManifest(dictionary, sha256(dictionary));
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(dictionary))) {
            output.write("changed\tform\n".getBytes(StandardCharsets.UTF_8));
        }

        assertThrows(IllegalArgumentException.class,
                () -> ProhibitedModelBenchmarkManifest.readEntry(manifest, "aa-default"));
    }

    private Path writeDictionary(final String content) throws IOException {
        final Path dictionary = this.temporaryDirectory.resolve("stemmer.gz").toAbsolutePath();
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(dictionary))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return dictionary;
    }

    private Path writeManifest(final Path dictionary, final String sha256) throws IOException {
        final Path manifest = this.temporaryDirectory.resolve("models.tsv");
        Files.writeString(manifest,
                "model_id\tlanguage\tdisplay_name\tmodel_version\tmodel_sha256\tdictionary\n"
                        + "aa-default\tAA\tExample\t1.0.0\t" + sha256 + "\t" + dictionary + "\n",
                StandardCharsets.UTF_8);
        return manifest;
    }

    private static String sha256(final Path path) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }
}
