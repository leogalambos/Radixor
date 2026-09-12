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
package org.egothor.stemmer.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Writes the documentation-facing Snowball comparison catalog from
 * {@link SnowballLanguageCase}, the benchmark's technical authority.
 *
 * <p>The application is stateless and thread-safe. Its memory use is bounded
 * by the small fixed catalog, and its running time is linear in the number of
 * configured language cases.</p>
 */
public final class SnowballLanguageCatalogApplication {
    private SnowballLanguageCatalogApplication() { }

    /**
     * Writes or verifies the canonical UTF-8 CSV catalog.
     *
     * @param arguments output path followed by {@code update} or {@code verify}
     * @throws IOException if the catalog cannot be read or written
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected arguments: catalog path and update or verify mode.");
        }
        final Path path = Path.of(arguments[0]);
        final String expected = render();
        final String checksum = sha256(expected.getBytes(StandardCharsets.UTF_8));
        final Path checksumPath = path.resolveSibling(path.getFileName().toString().replace(".csv", ".sha256"));
        final String expectedChecksum = checksum + "  " + path.getFileName() + "\n";
        switch (arguments[1]) {
            case "update" -> {
                final Path parent = path.toAbsolutePath().normalize().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(path, expected, StandardCharsets.UTF_8);
                Files.writeString(checksumPath, expectedChecksum, StandardCharsets.UTF_8);
            }
            case "verify" -> {
                if (!Files.isRegularFile(path)
                        || !Files.readString(path, StandardCharsets.UTF_8).equals(expected)) {
                    throw new IllegalStateException("The Snowball language catalog is stale: " + path);
                }
                if (!Files.isRegularFile(checksumPath)
                        || !Files.readString(checksumPath, StandardCharsets.UTF_8).equals(expectedChecksum)) {
                    throw new IllegalStateException("The Snowball language catalog checksum is stale: "
                            + checksumPath);
                }
            }
            default -> throw new IllegalArgumentException("Catalog mode must be update or verify.");
        }
    }

    /** Returns the deterministic documentation catalog. */
    static String render() {
        final StringBuilder output = new StringBuilder(4096);
        output.append("Case,Language,Model ID,Display language,Speed method,Quality candidate,Direct available,Lucene available,Homepage chart,Homepage aggregate\n");
        for (SnowballLanguageCase languageCase : SnowballLanguageCase.values()) {
            output.append(languageCase.name()).append(',')
                    .append(languageCase.radixorLanguage().name()).append(',')
                    .append(languageCase.radixorLanguage().defaultModelId()).append(',')
                    .append(languageCase.displayLanguage()).append(',')
                    .append(languageCase.speedMethod()).append(',')
                    .append(languageCase.qualityCandidate()).append(',')
                    .append("true,").append(languageCase.hasLuceneSnowball()).append(',')
                    .append(languageCase.homepageChart()).append(',')
                    .append(languageCase.homepageAggregate()).append('\n');
        }
        return output.toString();
    }

    /** Calculates a lowercase SHA-256 digest for the generated UTF-8 content. */
    private static String sha256(final byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The required SHA-256 algorithm is unavailable.", exception);
        }
    }
}
