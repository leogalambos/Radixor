package org.egothor.stemmer.benchmark;

import java.util.Objects;

import org.tartarus.snowball.SnowballStemmer;

/**
 * Small adapter around a Snowball stemmer instance used by benchmarks.
 *
 * <p>
 * The adapter keeps the benchmark code focused on the actual workload while
 * still allowing a professional separation between benchmark orchestration and
 * third-party stemming API calls.
 * </p>
 */
final class SnowballStemmerAdapter {

    /**
     * Factory of Snowball stemmer instances.
     */
    @FunctionalInterface
    interface Factory {

        /**
         * Creates a new Snowball stemmer instance.
         *
         * @return new Snowball stemmer
         */
        SnowballStemmer create();
    }

    /**
     * Reusable Snowball stemmer instance.
     */
    private final SnowballStemmer stemmer;

    /**
     * Creates a new adapter.
     *
     * @param factory factory creating the concrete Snowball stemmer
     */
    SnowballStemmerAdapter(final Factory factory) {
        this.stemmer = Objects.requireNonNull(factory, "factory").create();
    }

    /**
     * Applies stemming to the supplied token.
     *
     * @param token input token
     * @return produced stem
     */
    String stem(final String token) {
        this.stemmer.setCurrent(token);
        this.stemmer.stem();
        return this.stemmer.getCurrent();
    }
}