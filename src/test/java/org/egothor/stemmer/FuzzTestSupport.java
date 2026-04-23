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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Deterministic support utilities for fuzz-style tests of trie compilation and
 * stemming dictionary loading.
 *
 * <p>
 * The generators in this helper intentionally use bounded input sizes and fixed
 * seeds so that the resulting tests remain reproducible and suitable for CI.
 * The goal is not statistical randomness, but broad structured coverage of
 * unusual combinations that are cumbersome to author manually.
 */
final class FuzzTestSupport {

    /**
     * Shared deterministic seeds used across all generated scenarios.
     */
    private static final long[] SEEDS = { 7L, 19L, 43L, 71L, 101L, 211L };

    /**
     * Lower-case alphabet used for generated word material.
     */
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz".toCharArray();

    /**
     * Utility class.
     */
    private FuzzTestSupport() {
        throw new AssertionError("No instances.");
    }

    /**
     * Returns deterministic trie-compilation scenarios.
     *
     * @return stream of bounded deterministic scenarios
     */
    static Stream<TrieCompilationScenario> trieCompilationScenarios() {
        final List<TrieCompilationScenario> scenarios = new ArrayList<>(SEEDS.length);
        for (long seed : SEEDS) {
            scenarios.add(createTrieCompilationScenario(seed));
        }
        return scenarios.stream();
    }

    /**
     * Returns deterministic stemmer-dictionary scenarios.
     *
     * @return stream of bounded deterministic scenarios
     */
    static Stream<StemmerDictionaryScenario> stemmerDictionaryScenarios() {
        final List<StemmerDictionaryScenario> scenarios = new ArrayList<>(SEEDS.length);
        for (long seed : SEEDS) {
            scenarios.add(createStemmerDictionaryScenario(seed));
        }
        return scenarios.stream();
    }

    /**
     * Creates one trie scenario with repeated insertions, empty-key coverage, and a
     * stable set of observed keys.
     *
     * @param seed deterministic seed
     * @return generated scenario
     */
    private static TrieCompilationScenario createTrieCompilationScenario(final long seed) {
        final Random random = new Random(seed);
        final List<TrieInsertion> insertions = new ArrayList<>();
        final Set<String> observedKeys = new LinkedHashSet<>();

        observedKeys.add("");

        final int insertionCount = 50 + random.nextInt(15);
        for (int index = 0; index < insertionCount; index++) {
            final String key = random.nextInt(8) == 0 ? "" : nextWord(random, 1, 10);
            final String value = nextWord(random, 0, 8);
            final int count = 1 + random.nextInt(4);

            insertions.add(new TrieInsertion(key, value, count));
            observedKeys.add(key);

            if (!key.isEmpty() && random.nextBoolean()) {
                observedKeys.add(key.substring(0, Math.max(0, key.length() - 1)));
            }
            observedKeys.add(nextWord(random, 1, 8));
        }

        return new TrieCompilationScenario(seed, List.copyOf(insertions), List.copyOf(observedKeys));
    }

    /**
     * Creates one dictionary scenario made of compact stem-to-variants groups.
     *
     * @param seed deterministic seed
     * @return generated scenario
     */
    private static StemmerDictionaryScenario createStemmerDictionaryScenario(final long seed) {
        final Random random = new Random(seed);
        final Map<String, Set<String>> expectedStemsByWord = new LinkedHashMap<>();
        final StringBuilder dictionary = new StringBuilder(512);

        dictionary.append("# deterministic fuzz dictionary seed ").append(seed).append('\n');
        dictionary.append("// blank and remark handling is part of the exercised input\n\n");

        final int entryCount = 18 + random.nextInt(8);
        for (int index = 0; index < entryCount; index++) {
            final String stem = nextWord(random, 1, 8);
            final LinkedHashSet<String> variants = new LinkedHashSet<>();
            final int variantCount = 1 + random.nextInt(4);

            while (variants.size() < variantCount) {
                if (random.nextInt(6) == 0) {
                    variants.add(stem);
                } else {
                    variants.add(createVariant(random, stem));
                }
            }

            dictionary.append(stem);
            for (String variant : variants) {
                dictionary.append('\t').append(variant);
                expectedStemsByWord.computeIfAbsent(variant, ignored -> new LinkedHashSet<>()).add(stem);
            }
            dictionary.append("  # entry ").append(index).append('\n');

            if (random.nextInt(5) == 0) {
                dictionary.append("\n");
            }
        }

        return new StemmerDictionaryScenario(seed, dictionary.toString(), immutableMapOfSets(expectedStemsByWord));
    }

    /**
     * Creates a variant related to a supplied stem.
     *
     * @param random source of deterministic pseudo-randomness
     * @param stem   canonical stem
     * @return generated variant
     */
    private static String createVariant(final Random random, final String stem) {
        final int mode = random.nextInt(6);
        switch (mode) {
            case 0:
                return stem + suffix(random);
            case 1:
                return prefix(random) + stem;
            case 2:
                return stem.length() > 1 ? stem.substring(0, stem.length() - 1) + nextLetter(random)
                        : stem + nextLetter(random);
            case 3:
                return stem + nextLetter(random) + nextLetter(random);
            case 4:
                return stem.length() > 2 ? stem.substring(0, stem.length() - 2) : stem;
            default:
                return new StringBuilder(stem).reverse().append(nextLetter(random)).toString();
        }
    }

    /**
     * Returns a generated word in lower case.
     *
     * @param random    source of deterministic pseudo-randomness
     * @param minLength minimum inclusive length
     * @param maxLength maximum inclusive length
     * @return generated word
     */
    private static String nextWord(final Random random, final int minLength, final int maxLength) {
        final int length = minLength + random.nextInt(maxLength - minLength + 1);
        final StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(nextLetter(random));
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns one generated prefix fragment.
     *
     * @param random source of deterministic pseudo-randomness
     * @return prefix fragment
     */
    private static String prefix(final Random random) {
        return String.valueOf(nextLetter(random));
    }

    /**
     * Returns one generated suffix fragment.
     *
     * @param random source of deterministic pseudo-randomness
     * @return suffix fragment
     */
    private static String suffix(final Random random) {
        final String[] suffixes = { "s", "ed", "ing", "er", "ly", "ness", "ment" };
        return suffixes[random.nextInt(suffixes.length)];
    }

    /**
     * Returns one generated lower-case letter.
     *
     * @param random source of deterministic pseudo-randomness
     * @return generated character
     */
    private static char nextLetter(final Random random) {
        return ALPHABET[random.nextInt(ALPHABET.length)];
    }

    /**
     * Creates an immutable map view whose nested sets are also immutable.
     *
     * @param source mutable source map
     * @return immutable copy
     */
    private static Map<String, Set<String>> immutableMapOfSets(final Map<String, Set<String>> source) {
        final Map<String, Set<String>> copy = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    /**
     * Generated trie scenario for deterministic fuzz testing.
     *
     * @param seed         deterministic seed
     * @param insertions   generated insertions to apply to the builder
     * @param observedKeys keys that should be checked after compilation
     */
    record TrieCompilationScenario(long seed, List<TrieInsertion> insertions, List<String> observedKeys) {

        /**
         * Creates a validated scenario.
         *
         * @param seed         deterministic seed
         * @param insertions   generated insertions to apply to the builder
         * @param observedKeys keys that should be checked after compilation
         */
        TrieCompilationScenario {
            Objects.requireNonNull(insertions, "insertions");
            Objects.requireNonNull(observedKeys, "observedKeys");
        }

        @Override
        public String toString() {
            return "seed=" + this.seed;
        }
    }

    /**
     * One generated insertion into a trie builder.
     *
     * @param key   target key
     * @param value stored value
     * @param count positive occurrence count
     */
    record TrieInsertion(String key, String value, int count) {

        /**
         * Creates a validated insertion.
         *
         * @param key   target key
         * @param value stored value
         * @param count positive occurrence count
         */
        TrieInsertion {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (count < 1) {
                throw new IllegalArgumentException("count must be positive.");
            }
        }
    }

    /**
     * Generated dictionary scenario for deterministic fuzz testing of stemming.
     *
     * @param seed                deterministic seed
     * @param dictionaryContent   generated dictionary content
     * @param expectedStemsByWord acceptable stems for each generated word
     */
    record StemmerDictionaryScenario(long seed, String dictionaryContent,
            Map<String, Set<String>> expectedStemsByWord) {

        /**
         * Creates a validated scenario.
         *
         * @param seed                deterministic seed
         * @param dictionaryContent   generated dictionary content
         * @param expectedStemsByWord acceptable stems for each generated word
         */
        StemmerDictionaryScenario {
            Objects.requireNonNull(dictionaryContent, "dictionaryContent");
            Objects.requireNonNull(expectedStemsByWord, "expectedStemsByWord");
        }

        @Override
        public String toString() {
            return "seed=" + this.seed;
        }
    }
}
