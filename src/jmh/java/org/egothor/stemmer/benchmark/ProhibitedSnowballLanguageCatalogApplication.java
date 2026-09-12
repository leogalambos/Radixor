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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Writes the exact prohibited-model/Snowball intersection used by guarded runners. */
public final class ProhibitedSnowballLanguageCatalogApplication {
    private ProhibitedSnowballLanguageCatalogApplication() {
        throw new AssertionError("No instances.");
    }

    /**
     * Writes a deterministic TSV catalog after matching the closed comparator
     * authority to the lifecycle-verified prohibited manifest.
     *
     * @param arguments guarded model manifest and output catalog paths
     * @throws IOException if an input cannot be read or output cannot be written
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected prohibited model manifest and comparator catalog paths.");
        }
        final List<ProhibitedModelBenchmarkManifest.Entry> entries =
                ProhibitedModelBenchmarkManifest.read(Path.of(arguments[0]));
        final Map<String, ProhibitedModelBenchmarkManifest.Entry> byId = new HashMap<>();
        for (ProhibitedModelBenchmarkManifest.Entry entry : entries) {
            byId.put(entry.modelId(), entry);
        }
        final StringBuilder output = new StringBuilder(256);
        output.append("model_id\tlanguage\tdisplay_language\tcandidate\tspeed_benchmark\n");
        for (ProhibitedSnowballLanguageCase languageCase : ProhibitedSnowballLanguageCase.values()) {
            final ProhibitedModelBenchmarkManifest.Entry entry = byId.get(languageCase.modelId());
            if (entry == null || !entry.language().equals(languageCase.language())
                    || !entry.displayName().equals(languageCase.modelDisplayName())) {
                throw new IllegalStateException(
                        "Prohibited Snowball authority differs from the guarded manifest for "
                                + languageCase.modelId() + ".");
            }
            output.append(languageCase.modelId()).append('\t')
                    .append(languageCase.language()).append('\t')
                    .append(languageCase.displayLanguage()).append('\t')
                    .append(languageCase.candidate()).append('\t')
                    .append("ProhibitedModelStemmerBenchmark.snowballDirect\n");
        }
        final Path path = Path.of(arguments[1]);
        final Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, output, StandardCharsets.UTF_8);
    }
}
