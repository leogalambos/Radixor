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

/** Loads gold-standard groups from authoritative bundled dictionary resources. */
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
        final String resource = language.resourcePath();
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
