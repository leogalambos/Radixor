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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reads the closed, lifecycle-verified input manifest used by prohibited-model benchmarks.
 *
 * <p>The manifest is intentionally separate from production descriptors. Reading
 * is linear in the number of records and retains only the small immutable record
 * list. Instances cannot be created and this type is thread-safe.</p>
 */
public final class ProhibitedModelBenchmarkManifest {
    private static final String HEADER =
            "model_id\tlanguage\tdisplay_name\tmodel_version\tmodel_sha256\tdictionary";

    private ProhibitedModelBenchmarkManifest() {
        throw new AssertionError("No instances.");
    }

    /**
     * Reads and validates a UTF-8 benchmark manifest.
     *
     * @param path exact manifest path
     * @return immutable records in manifest order
     * @throws IOException if the manifest or a dictionary cannot be read
     * @throws IllegalArgumentException if the manifest is malformed or contains duplicate IDs
     */
    public static List<Entry> read(final Path path) throws IOException {
        final List<Entry> entries = readEntries(path);
        for (Entry entry : entries) {
            verifyDictionary(entry);
        }
        return entries;
    }

    /**
     * Reads one exact model entry from a UTF-8 benchmark manifest and verifies
     * that entry's dictionary checksum.
     *
     * <p>This targeted form is intended for one JMH trial. It validates the
     * syntax and uniqueness of every manifest record but hashes only the selected
     * dictionary, avoiding repeated I/O over the entire cohort in every fork.</p>
     *
     * @param path manifest path
     * @param modelId exact selected model ID
     * @return matching verified entry
     * @throws IOException if the manifest or selected dictionary cannot be read
     * @throws IllegalArgumentException if the manifest is malformed or does not
     *                                  contain the exact model ID once
     */
    public static Entry readEntry(final Path path, final String modelId) throws IOException {
        Objects.requireNonNull(modelId, "modelId");
        final List<Entry> entries = readEntries(path);
        Entry selected = null;
        for (Entry entry : entries) {
            if (entry.modelId().equals(modelId)) {
                selected = entry;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("Prohibited benchmark manifest omits model ID " + modelId + ".");
        }
        verifyDictionary(selected);
        return selected;
    }

    private static List<Entry> readEntries(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(lines.get(0))) {
            throw new IllegalArgumentException("Invalid prohibited benchmark manifest header at " + path + ".");
        }
        final List<Entry> entries = new ArrayList<>(lines.size() - 1);
        final Set<String> identifiers = new HashSet<>(Math.max(16, lines.size() * 2));
        for (int index = 1; index < lines.size(); index++) {
            final String[] fields = lines.get(index).split("\t", -1);
            if (fields.length != 6) {
                throw new IllegalArgumentException("Invalid prohibited benchmark manifest row " + (index + 1) + ".");
            }
            final Entry entry = new Entry(fields[0], fields[1], fields[2], fields[3], fields[4], Path.of(fields[5]));
            if (!identifiers.add(entry.modelId())) {
                throw new IllegalArgumentException("Duplicate prohibited benchmark model ID " + entry.modelId() + ".");
            }
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("The prohibited benchmark manifest is empty.");
        }
        return List.copyOf(entries);
    }

    private static void verifyDictionary(final Entry entry) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
        try (DigestInputStream input = new DigestInputStream(Files.newInputStream(entry.dictionary()), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        final String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(entry.modelSha256())) {
            throw new IllegalArgumentException("Dictionary SHA-256 mismatch for " + entry.modelId() + ".");
        }
    }

    /**
     * One immutable, filesystem-backed prohibited benchmark identity.
     *
     * @param modelId stable model ID
     * @param language stable report language ID
     * @param displayName human-readable language name
     * @param modelVersion privately retained model version
     * @param modelSha256 dictionary SHA-256
     * @param dictionary absolute staged dictionary path
     */
    public record Entry(String modelId, String language, String displayName, String modelVersion,
            String modelSha256, Path dictionary) {
        /** Validates identity syntax and the exact dictionary input. */
        public Entry {
            Objects.requireNonNull(modelId, "modelId");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(modelVersion, "modelVersion");
            Objects.requireNonNull(modelSha256, "modelSha256");
            Objects.requireNonNull(dictionary, "dictionary");
            if (!modelId.matches("[a-z]{2,3}(?:-[a-z]{2})?-default")
                    || !language.matches("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*")
                    || displayName.isBlank() || modelVersion.isBlank()
                    || !modelSha256.matches("[0-9a-f]{64}")
                    || !dictionary.isAbsolute() || !Files.isRegularFile(dictionary)) {
                throw new IllegalArgumentException("Invalid prohibited benchmark identity for " + modelId + ".");
            }
            dictionary = dictionary.normalize();
        }
    }
}
