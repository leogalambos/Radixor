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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Test support utilities for compiled-artifact regression testing.
 *
 * <p>
 * This helper centralizes resource loading, artifact compilation, digest
 * calculation, and failure-message formatting so that regression tests stay
 * focused on contract verification.
 */
final class RegressionArtifactSupport {

    /**
     * Utility class.
     */
    private RegressionArtifactSupport() {
        throw new AssertionError("No instances.");
    }

    /**
     * Copies a classpath resource to a filesystem path.
     *
     * @param resourcePath source resource path
     * @param targetPath   target file path
     * @return target path
     * @throws IOException if copying fails
     */
    static Path copyResourceToFile(final String resourcePath, final Path targetPath) throws IOException {
        Objects.requireNonNull(resourcePath, "resourcePath");
        Objects.requireNonNull(targetPath, "targetPath");

        final Path parent = targetPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (InputStream inputStream = openResource(resourcePath)) {
            Files.copy(inputStream, targetPath);
        }

        return targetPath;
    }

    /**
     * Reads the complete bytes of a classpath resource.
     *
     * @param resourcePath resource path
     * @return resource bytes
     * @throws IOException if reading fails
     */
    static byte[] readResourceBytes(final String resourcePath) throws IOException {
        Objects.requireNonNull(resourcePath, "resourcePath");

        try (InputStream inputStream = openResource(resourcePath)) {
            return inputStream.readAllBytes();
        }
    }

    /**
     * Reads a SHA-256 sidecar resource.
     *
     * <p>
     * The sidecar may contain either just the hash or the conventional
     * {@code "<hash><space><space><filename>"} form. Only the first token is used.
     *
     * @param resourcePath SHA-256 sidecar resource path
     * @return normalized lowercase hex hash
     * @throws IOException if reading fails
     */
    static String readSha256Resource(final String resourcePath) throws IOException {
        final String content = new String(readResourceBytes(resourcePath), StandardCharsets.UTF_8).trim();
        final int firstWhitespace = findFirstWhitespace(content);
        final String hash = firstWhitespace < 0 ? content : content.substring(0, firstWhitespace);
        return hash.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Compiles a source dictionary into a compressed binary artifact and writes it
     * to the supplied file path.
     *
     * @param sourcePath         dictionary source file
     * @param storeOriginal      whether stems are stored using no-op mappings
     * @param reductionSettings  reduction settings
     * @param artifactOutputPath output artifact path
     * @return written artifact bytes
     * @throws IOException if compilation or writing fails
     */
    static byte[] compileToArtifact(final Path sourcePath, final boolean storeOriginal,
            final ReductionSettings reductionSettings, final Path artifactOutputPath) throws IOException {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(reductionSettings, "reductionSettings");
        Objects.requireNonNull(artifactOutputPath, "artifactOutputPath");

        final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(sourcePath, storeOriginal, reductionSettings);
        StemmerPatchTrieBinaryIO.write(trie, artifactOutputPath);
        return Files.readAllBytes(artifactOutputPath);
    }

    /**
     * Compiles a source dictionary into compressed binary artifact bytes without
     * persisting the result on disk.
     *
     * @param sourcePath        dictionary source file
     * @param storeOriginal     whether stems are stored using no-op mappings
     * @param reductionSettings reduction settings
     * @return artifact bytes
     * @throws IOException if compilation fails
     */
    static byte[] compileToArtifactBytes(final Path sourcePath, final boolean storeOriginal,
            final ReductionSettings reductionSettings) throws IOException {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(reductionSettings, "reductionSettings");

        final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(sourcePath, storeOriginal, reductionSettings);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            StemmerPatchTrieBinaryIO.write(trie, outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Computes the lowercase hexadecimal SHA-256 digest of the supplied bytes.
     *
     * @param bytes input bytes
     * @return lowercase hexadecimal SHA-256 digest
     */
    static String sha256Hex(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");

        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", exception);
        }
    }

    /**
     * Builds a descriptive mismatch message for golden-artifact failures.
     *
     * @param caseId         regression case identifier
     * @param expectedSha256 expected digest
     * @param actualSha256   actual digest
     * @param actualPath     location of the produced artifact
     * @return mismatch message
     */
    static String mismatchMessage(final String caseId, final String expectedSha256, final String actualSha256,
            final Path actualPath) {
        return "Golden artifact mismatch for case '" + caseId + "'. Expected SHA-256=" + expectedSha256
                + ", actual SHA-256=" + actualSha256 + ", produced artifact=" + actualPath.toAbsolutePath();
    }

    /**
     * Opens a classpath resource.
     *
     * @param resourcePath resource path
     * @return opened resource stream
     * @throws IOException if the resource does not exist
     */
    private static InputStream openResource(final String resourcePath) throws IOException {
        Objects.requireNonNull(resourcePath, "resourcePath");

        final String normalizedPath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;

        InputStream inputStream = RegressionArtifactSupport.class.getResourceAsStream(normalizedPath);
        if (inputStream != null) {
            return inputStream;
        }

        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            inputStream = contextClassLoader
                    .getResourceAsStream(normalizedPath.startsWith("/") ? normalizedPath.substring(1) : normalizedPath);
            if (inputStream != null) {
                return inputStream;
            }
        }

        final ClassLoader classLoader = RegressionArtifactSupport.class.getClassLoader();
        if (classLoader != null) {
            inputStream = classLoader
                    .getResourceAsStream(normalizedPath.startsWith("/") ? normalizedPath.substring(1) : normalizedPath);
            if (inputStream != null) {
                return inputStream;
            }
        }

        throw new IOException("Classpath resource not found: " + resourcePath);
    }

    /**
     * Finds the index of the first whitespace character.
     *
     * @param text text to inspect
     * @return first whitespace index, or {@code -1} when no whitespace is present
     */
    private static int findFirstWhitespace(final String text) {
        for (int index = 0; index < text.length(); index++) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }
}
