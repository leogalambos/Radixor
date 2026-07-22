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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import org.egothor.stemmer.StemmerPatchTrieLoader;

/**
 * Builds deterministic language-specific benchmark corpora from bundled
 * Radixor dictionary resources.
 *
 * <p>
 * Corpus construction is setup work only. It is intentionally based on the same
 * resource that backs the Radixor benchmark path so every competitor for a
 * language consumes the same changed-token timing workload, while quality
 * benchmarks can still use the complete dictionary workload.
 * </p>
 */
final class LanguageBenchmarkCorpus {

    /**
     * Minimum token count for timing benchmark operations.
     */
    static final int MINIMUM_TIMING_TOKEN_COUNT = 5_000;

    /**
     * Shared timing corpora keyed by bundled Radixor language.
     */
    private static final Map<StemmerPatchTrieLoader.Language, Corpus> TIMING_CORPORA =
            new EnumMap<>(StemmerPatchTrieLoader.Language.class);

    /**
     * Shared changed-token timing corpora keyed by bundled Radixor language.
     */
    private static final Map<StemmerPatchTrieLoader.Language, Corpus> CHANGED_TIMING_CORPORA =
            new EnumMap<>(StemmerPatchTrieLoader.Language.class);

    /**
     * Shared complete corpora keyed by bundled Radixor language.
     */
    private static final Map<StemmerPatchTrieLoader.Language, Corpus> FULL_CORPORA =
            new EnumMap<>(StemmerPatchTrieLoader.Language.class);

    /**
     * Utility class.
     */
    private LanguageBenchmarkCorpus() {
        throw new AssertionError("No instances.");
    }

    /**
     * Creates a deterministic changed-token timing corpus from a bundled language
     * dictionary.
     *
     * <p>
     * Only token/root pairs where the token differs from the expected root are
     * included. Smaller changed-token resources are repeated in stable order until
     * the timing corpus reaches 5,000 tokens.
     * </p>
     *
     * @param language bundled Radixor language
     * @return token array containing changed-token dictionary entries, repeated
     *         only when the changed-token resource is smaller than 5,000 tokens
     * @throws IOException if the resource cannot be read
     */
    static String[] createTokens(final StemmerPatchTrieLoader.Language language) throws IOException {
        return createChangedCorpus(language).tokens();
    }

    /**
     * Creates a deterministic changed-token timing corpus from a bundled language
     * dictionary.
     *
     * @param language bundled Radixor language
     * @return changed-token corpus with expected roots
     * @throws IOException if the resource cannot be read
     */
    static Corpus createChangedCorpus(final StemmerPatchTrieLoader.Language language) throws IOException {
        return cachedChangedCorpus(language);
    }

    /**
     * Creates a deterministic full-dictionary timing corpus and expected root
     * array from a bundled language dictionary.
     *
     * @param language bundled Radixor language
     * @return token corpus with expected roots
     * @throws IOException if the resource cannot be read
     */
    static Corpus createCorpus(final StemmerPatchTrieLoader.Language language) throws IOException {
        return cachedCorpus(TIMING_CORPORA, language, true);
    }

    /**
     * Creates a deterministic full-dictionary timing corpus and expected root
     * array from a bundled language dictionary.
     *
     * <p>
     * The complete dictionary token sequence is used when it contains at least
     * {@code minimumTokenCount} tokens. Smaller resources are repeated in stable
     * order until the minimum is reached.
     * </p>
     *
     * @param language          bundled Radixor language
     * @param minimumTokenCount minimum token count for timing
     * @return token corpus with expected roots
     * @throws IOException if the resource cannot be read
     */
    static Corpus createCorpus(final StemmerPatchTrieLoader.Language language, final int minimumTokenCount)
            throws IOException {
        Objects.requireNonNull(language, "language");
        if (minimumTokenCount < 1) {
            throw new IllegalArgumentException("minimumTokenCount must be at least 1.");
        }
        if (minimumTokenCount == MINIMUM_TIMING_TOKEN_COUNT) {
            return createCorpus(language);
        }

        return buildTimingCorpus(language, minimumTokenCount);
    }

    /**
     * Creates or returns the shared complete corpus for a bundled language.
     *
     * @param language bundled Radixor language
     * @return complete token corpus with expected roots
     * @throws IOException if the resource cannot be read
     */
    static Corpus createFullCorpus(final StemmerPatchTrieLoader.Language language) throws IOException {
        return cachedCorpus(FULL_CORPORA, language, false);
    }

    /**
     * Returns a cached corpus, creating it once per JVM when necessary.
     *
     * @param cache corpus cache
     * @param language bundled Radixor language
     * @param timing whether the timing-minimum corpus should be built
     * @return cached corpus instance
     * @throws IOException if the resource cannot be read
     */
    private static Corpus cachedCorpus(final Map<StemmerPatchTrieLoader.Language, Corpus> cache,
            final StemmerPatchTrieLoader.Language language, final boolean timing) throws IOException {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(language, "language");

        synchronized (LanguageBenchmarkCorpus.class) {
            final Corpus existing = cache.get(language);
            if (existing != null) {
                return existing;
            }

            final Corpus created = timing ? buildTimingCorpus(language, MINIMUM_TIMING_TOKEN_COUNT)
                    : buildFullCorpus(language);
            cache.put(language, created);
            return created;
        }
    }

    /**
     * Returns a cached changed-token timing corpus, creating it once per JVM when
     * necessary.
     *
     * @param language bundled Radixor language
     * @return changed-token timing corpus
     * @throws IOException if the resource cannot be read
     */
    private static Corpus cachedChangedCorpus(final StemmerPatchTrieLoader.Language language) throws IOException {
        Objects.requireNonNull(language, "language");

        synchronized (LanguageBenchmarkCorpus.class) {
            final Corpus existing = CHANGED_TIMING_CORPORA.get(language);
            if (existing != null) {
                return existing;
            }

            final Corpus created = buildChangedTimingCorpus(language, MINIMUM_TIMING_TOKEN_COUNT);
            CHANGED_TIMING_CORPORA.put(language, created);
            return created;
        }
    }

    /**
     * Builds a deterministic timing corpus from a bundled language dictionary.
     *
     * @param language bundled Radixor language
     * @param minimumTokenCount minimum token count for timing
     * @return token corpus with expected roots
     * @throws IOException if the resource cannot be read
     */
    private static Corpus buildTimingCorpus(final StemmerPatchTrieLoader.Language language, final int minimumTokenCount)
            throws IOException {
        Objects.requireNonNull(language, "language");
        if (minimumTokenCount < 1) {
            throw new IllegalArgumentException("minimumTokenCount must be at least 1.");
        }

        final List<Entry> candidates = readCandidates(language, Integer.MAX_VALUE);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No benchmark corpus tokens were available for " + language + ".");
        }

        final int timingTokenCount = Math.max(candidates.size(), minimumTokenCount);
        final String[] tokens = new String[timingTokenCount];
        final String[] expectedRoots = new String[timingTokenCount];
        for (int index = 0; index < tokens.length; index++) {
            final Entry entry = candidates.get(index % candidates.size());
            tokens[index] = entry.token();
            expectedRoots[index] = entry.root();
        }
        return new Corpus(tokens, expectedRoots);
    }

    /**
     * Builds a deterministic changed-token timing corpus from a bundled language
     * dictionary.
     *
     * @param language bundled Radixor language
     * @param minimumTokenCount minimum token count for timing
     * @return changed-token corpus with expected roots
     * @throws IOException if the resource cannot be read
     */
    private static Corpus buildChangedTimingCorpus(final StemmerPatchTrieLoader.Language language,
            final int minimumTokenCount) throws IOException {
        Objects.requireNonNull(language, "language");
        if (minimumTokenCount < 1) {
            throw new IllegalArgumentException("minimumTokenCount must be at least 1.");
        }

        final List<Entry> allCandidates = readCandidates(language, Integer.MAX_VALUE);
        final List<Entry> changedCandidates = new ArrayList<>(allCandidates.size());
        for (Entry entry : allCandidates) {
            if (!Objects.equals(entry.token(), entry.root())) {
                changedCandidates.add(entry);
            }
        }
        if (changedCandidates.isEmpty()) {
            throw new IllegalStateException("No changed-token benchmark corpus tokens were available for "
                    + language + ".");
        }

        final int timingTokenCount = Math.max(changedCandidates.size(), minimumTokenCount);
        final String[] tokens = new String[timingTokenCount];
        final String[] expectedRoots = new String[timingTokenCount];
        for (int index = 0; index < tokens.length; index++) {
            final Entry entry = changedCandidates.get(index % changedCandidates.size());
            tokens[index] = entry.token();
            expectedRoots[index] = entry.root();
        }
        return new Corpus(tokens, expectedRoots);
    }

    /**
     * Creates a complete deterministic token corpus and expected root array from a
     * bundled language dictionary.
     *
     * <p>
     * This method is intended for exact-root quality accounting. It includes all
     * single-token fields available in the dictionary resource and does not repeat
     * small dictionaries to the timing minimum.
     * </p>
     *
     * @param language bundled Radixor language
     * @return complete token corpus with expected roots
     * @throws IOException if the resource cannot be read
     */
    private static Corpus buildFullCorpus(final StemmerPatchTrieLoader.Language language) throws IOException {
        Objects.requireNonNull(language, "language");

        final List<Entry> candidates = readCandidates(language, Integer.MAX_VALUE);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No benchmark corpus tokens were available for " + language + ".");
        }

        final String[] tokens = new String[candidates.size()];
        final String[] expectedRoots = new String[candidates.size()];
        for (int index = 0; index < tokens.length; index++) {
            final Entry entry = candidates.get(index);
            tokens[index] = entry.token();
            expectedRoots[index] = entry.root();
        }
        return new Corpus(tokens, expectedRoots);
    }

    /**
     * Reads token candidates from a bundled compressed dictionary.
     *
     * @param language bundled Radixor language
     * @param maximumTokenCount maximum token count to read
     * @return deterministic candidate list
     * @throws IOException if the resource cannot be read
     */
    private static List<Entry> readCandidates(final StemmerPatchTrieLoader.Language language, final int maximumTokenCount)
            throws IOException {
        final String resourcePath = org.egothor.stemmer.StemmerModelRegistry.fromContextClassLoader()
                .requireDefault(language).resource();
        final InputStream resource = StemmerPatchTrieLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Missing bundled benchmark resource " + resourcePath + ".");
        }

        final List<Entry> candidates = new ArrayList<>(MINIMUM_TIMING_TOKEN_COUNT);
        try (InputStream inputStream = resource;
                GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
                InputStreamReader inputStreamReader = new InputStreamReader(gzipInputStream, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(inputStreamReader)) {
            String line = reader.readLine();
            while (line != null && candidates.size() < maximumTokenCount) {
                collectLineCandidates(line, candidates, maximumTokenCount);
                line = reader.readLine();
            }
        }
        return candidates;
    }

    /**
     * Collects lower-case token candidates from one dictionary line.
     *
     * @param line       dictionary line
     * @param candidates mutable candidate list
     * @param maximumTokenCount maximum token count to read
     */
    private static void collectLineCandidates(final String line, final List<Entry> candidates,
            final int maximumTokenCount) {
        if (line == null || line.isBlank() || line.startsWith("#") || line.startsWith("//")) {
            return;
        }

        final String[] fields = line.split("\t");
        if (fields.length == 0) {
            return;
        }

        final String root = normalizeToken(fields[0]);
        if (root.isEmpty() || containsWhitespace(root)) {
            return;
        }

        for (String field : fields) {
            if (candidates.size() >= maximumTokenCount) {
                return;
            }
            final String token = normalizeToken(field);
            if (!token.isEmpty() && !containsWhitespace(token)) {
                candidates.add(new Entry(token, root));
            }
        }
    }

    /**
     * Normalizes dictionary token text for deterministic benchmark lookup.
     *
     * @param token dictionary token field
     * @return normalized token
     */
    private static String normalizeToken(final String token) {
        return token.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns whether a token contains Unicode whitespace.
     *
     * @param token token candidate
     * @return {@code true} when whitespace is present
     */
    private static boolean containsWhitespace(final String token) {
        for (int index = 0; index < token.length(); index++) {
            if (Character.isWhitespace(token.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Immutable token corpus with expected roots.
     *
     * @param tokens        benchmark token corpus
     * @param expectedRoots expected root for each token
     */
    record Corpus(String[] tokens, String[] expectedRoots) {

        /**
         * Creates corpus data.
         *
         * @param tokens        benchmark token corpus
         * @param expectedRoots expected root for each token
         */
        Corpus {
            Objects.requireNonNull(tokens, "tokens");
            Objects.requireNonNull(expectedRoots, "expectedRoots");
            if (tokens.length != expectedRoots.length) {
                throw new IllegalArgumentException("tokens and expectedRoots must have the same length.");
            }
        }
    }

    /**
     * Immutable dictionary-derived token/root entry.
     *
     * @param token token form
     * @param root  expected root
     */
    private record Entry(String token, String root) {
    }
}
