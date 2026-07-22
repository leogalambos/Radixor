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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import org.egothor.stemmer.CaseProcessingMode;
import org.egothor.stemmer.StemmerDictionaryParser;
import org.egothor.stemmer.StemmerPatchTrieLoader.Language;

/** Loads gold-standard groups from authoritative registered model resources. */
public final class BundledGoldStandardLoader {
    /** Utility class. */
    private BundledGoldStandardLoader() { throw new AssertionError("No instances."); }

    /**
     * Parses one compressed UTF-8 dictionary with case preserved.
     * @param language registered bundled language
     * @return immutable groups in source-row order
     * @throws IOException if the resource is absent, malformed, or unreadable
     */
    public static List<GoldStandardGroup> load(final Language language) throws IOException {
        Objects.requireNonNull(language, "language");
        final String resource = org.egothor.stemmer.StemmerModelRegistry.fromContextClassLoader()
                .requireDefault(language).resource();
        final List<GoldStandardGroup> groups = new ArrayList<>();
        try (InputStream raw = openResource(language, resource); InputStream gzip = new GZIPInputStream(raw);
                BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
            StemmerDictionaryParser.parse(reader, resource, CaseProcessingMode.AS_IS, (stem, variants, row) -> {
                final List<String> forms = new ArrayList<>(variants.length + 1);
                forms.add(stem);
                forms.addAll(Arrays.asList(variants));
                try {
                    groups.add(new GoldStandardGroup(row, forms));
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Invalid dictionary group for language " + language + ", resource "
                            + resource + ", row " + row + ": " + exception.getMessage(), exception);
                }
            });
        }
        return List.copyOf(groups);
    }

    /** Opens one required classpath resource with a precise language diagnostic. */
    private static InputStream openResource(final Language language, final String resource) throws IOException {
        final InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (input == null) {
            throw new IOException("Dictionary resource is missing for language " + language + ": " + resource + ".");
        }
        return input;
    }
}
