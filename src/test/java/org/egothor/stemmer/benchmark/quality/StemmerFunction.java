package org.egothor.stemmer.benchmark.quality;

import java.io.IOException;

/** Contract used to apply one production stemmer during quality evaluation. */
@FunctionalInterface
public interface StemmerFunction {
    /**
     * Stems one word form without test-specific post-processing.
     * @param word input form, never {@code null}
     * @return output stem, never {@code null}
     * @throws IOException when an adapted production stemmer fails
     */
    String stem(String word) throws IOException;
}
