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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Provide;
import net.jqwik.api.arbitraries.ListArbitrary;

/**
 * Shared jqwik generators and helpers for property-based tests covering the
 * Radixor algorithmic core.
 *
 * <p>
 * The generated domains are intentionally bounded to keep CI execution time
 * predictable while still exploring a broad range of trie shapes, duplicate
 * insertions, missing lookups, and patch-command transformations.
 */
abstract class PropertyBasedTestSupport {

    /**
     * Shared array factory for string tries.
     */
    protected static final IntFunction<String[]> STRING_ARRAY_FACTORY = String[]::new;

    /**
     * Provides bounded lowercase words suitable for trie keys, stems, and patch
     * encoder inputs.
     *
     * @return bounded word generator
     */
    @Provide
    protected Arbitrary<String> words() {
        return Arbitraries.strings().withChars('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l')
                .ofMinLength(0).ofMaxLength(12);
    }

    /**
     * Provides non-empty lowercase words suitable for dictionary variants and
     * stems.
     *
     * @return bounded non-empty word generator
     */
    @Provide
    protected Arbitrary<String> nonEmptyWords() {
        return Arbitraries.strings().withChars('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l')
                .ofMinLength(1).ofMaxLength(12);
    }

    /**
     * Provides bounded insertion scenarios for trie-focused properties.
     *
     * @return trie scenario generator
     */
    @Provide
    protected Arbitrary<TrieScenario> trieScenarios() {
        final Arbitrary<TrieInsertion> insertionArbitrary = Combinators
                .combine(words(), nonEmptyWords(), Arbitraries.integers().between(1, 5)).as(TrieInsertion::new);

        final ListArbitrary<TrieInsertion> insertions = insertionArbitrary.list().ofMinSize(1).ofMaxSize(24);
        final Arbitrary<List<String>> observedKeys = words().list().ofMinSize(0).ofMaxSize(16);

        return Combinators.combine(insertions, observedKeys)
                .as((scenarioInsertions, additionalObservedKeys) -> new TrieScenario(scenarioInsertions,
                        mergeObservedKeys(scenarioInsertions, additionalObservedKeys)));
    }

    /**
     * Provides bounded stemmer scenarios where each variant word maps to one or
     * more acceptable stems.
     *
     * @return stemmer scenario generator
     */
    @Provide
    protected Arbitrary<StemmerScenario> stemmerScenarios() {
        final Arbitrary<StemmerEntry> entryArbitrary = Combinators
                .combine(nonEmptyWords(), nonEmptyWords().set().ofMinSize(1).ofMaxSize(4)).as((stem, variants) -> {
                    final LinkedHashSet<String> normalizedVariants = new LinkedHashSet<>(variants);
                    normalizedVariants.add(stem);
                    return new StemmerEntry(stem, normalizedVariants);
                });

        return entryArbitrary.list().ofMinSize(1).ofMaxSize(10).map(StemmerScenario::new);
    }

    /**
     * Builds a compiled trie from one generated scenario.
     *
     * @param scenario      trie scenario
     * @param reductionMode reduction mode
     * @return compiled trie
     */
    protected FrequencyTrie<String> buildTrie(final TrieScenario scenario, final ReductionMode reductionMode) {
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(reductionMode, "reductionMode");

        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(STRING_ARRAY_FACTORY, reductionMode);
        for (TrieInsertion insertion : scenario.insertions()) {
            builder.put(insertion.key(), insertion.value(), insertion.count());
        }
        return builder.build();
    }

    /**
     * Builds a patch-command trie from one generated stemmer scenario.
     *
     * @param scenario      stemmer scenario
     * @param reductionMode reduction mode
     * @param storeOriginal whether original stems should be stored using the
     *                      canonical no-op patch
     * @return compiled patch-command trie
     */
    protected FrequencyTrie<String> buildStemmerTrie(final StemmerScenario scenario, final ReductionMode reductionMode,
            final boolean storeOriginal) {
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(reductionMode, "reductionMode");

        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(STRING_ARRAY_FACTORY, reductionMode);
        final PatchCommandEncoder encoder = PatchCommandEncoder.builder().build();

        for (StemmerEntry entry : scenario.entries()) {
            if (storeOriginal) {
                builder.put(entry.stem(), PatchCommandEncoder.NOOP_PATCH);
            }
            for (String variant : entry.variants()) {
                if (!variant.equals(entry.stem())) {
                    builder.put(variant, encoder.encode(variant, entry.stem()));
                }
            }
        }
        return builder.build();
    }

    /**
     * Merges observed lookup keys while preserving order and keeping scenario keys
     * relevant to actual trie content.
     *
     * @param insertions             inserted trie mappings
     * @param additionalObservedKeys extra lookup probes
     * @return merged lookup-key set
     */
    private static Set<String> mergeObservedKeys(final List<TrieInsertion> insertions,
            final List<String> additionalObservedKeys) {
        final LinkedHashSet<String> observedKeys = new LinkedHashSet<>();
        for (TrieInsertion insertion : insertions) {
            observedKeys.add(insertion.key());
        }
        observedKeys.addAll(additionalObservedKeys);
        return observedKeys;
    }

    /**
     * Generated insertion into a trie builder.
     *
     * @param key   trie key
     * @param value stored value
     * @param count positive insertion count
     */
    protected record TrieInsertion(String key, String value, int count) {

        /**
         * Creates a validated insertion descriptor.
         *
         * @param key   trie key
         * @param value stored value
         * @param count positive insertion count
         */
        public TrieInsertion {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (count < 1) {
                throw new IllegalArgumentException("count must be at least 1.");
            }
        }
    }

    /**
     * Generated trie scenario used by multiple properties.
     *
     * @param insertions   generated insertions
     * @param observedKeys lookup probes
     */
    protected record TrieScenario(List<TrieInsertion> insertions, Set<String> observedKeys) {

        /**
         * Creates a validated trie scenario.
         *
         * @param insertions   generated insertions
         * @param observedKeys lookup probes
         */
        public TrieScenario {
            Objects.requireNonNull(insertions, "insertions");
            Objects.requireNonNull(observedKeys, "observedKeys");
            insertions = List.copyOf(insertions);
            observedKeys = Set.copyOf(observedKeys);
            if (insertions.isEmpty()) {
                throw new IllegalArgumentException("insertions must not be empty.");
            }
        }

        @Override
        public String toString() {
            return "TrieScenario[insertions=" + this.insertions.size() + ", observedKeys=" + this.observedKeys.size()
                    + "]";
        }
    }

    /**
     * Generated stemmer dictionary line equivalent.
     *
     * @param stem     canonical stem
     * @param variants variants accepted for the stem
     */
    protected record StemmerEntry(String stem, Set<String> variants) {

        /**
         * Creates a validated stemmer entry.
         *
         * @param stem     canonical stem
         * @param variants variants accepted for the stem
         */
        public StemmerEntry {
            Objects.requireNonNull(stem, "stem");
            Objects.requireNonNull(variants, "variants");
            variants = Set.copyOf(variants);
            if (stem.isEmpty()) {
                throw new IllegalArgumentException("stem must not be empty.");
            }
            if (variants.isEmpty()) {
                throw new IllegalArgumentException("variants must not be empty.");
            }
        }
    }

    /**
     * Generated stemmer scenario used by patch-command trie properties.
     *
     * @param entries generated entries
     */
    protected record StemmerScenario(List<StemmerEntry> entries) {

        /**
         * Creates a validated stemmer scenario.
         *
         * @param entries generated entries
         */
        public StemmerScenario {
            Objects.requireNonNull(entries, "entries");
            entries = List.copyOf(entries);
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("entries must not be empty.");
            }
        }

        /**
         * Returns all known source words that should be probeable in the resulting
         * trie.
         *
         * @return observed lookup words
         */
        public Set<String> observedWords() {
            final LinkedHashSet<String> observedWords = new LinkedHashSet<>();
            for (StemmerEntry entry : this.entries) {
                observedWords.add(entry.stem());
                observedWords.addAll(entry.variants());
            }
            return observedWords;
        }

        /**
         * Returns all acceptable stems for one observed word.
         *
         * @param word observed word
         * @return acceptable stems
         */
        public Set<String> acceptableStemsFor(final String word) {
            final LinkedHashSet<String> stems = new LinkedHashSet<>();
            for (StemmerEntry entry : this.entries) {
                if (entry.stem().equals(word) || entry.variants().contains(word)) {
                    stems.add(entry.stem());
                }
            }
            return stems;
        }

        @Override
        public String toString() {
            return "StemmerScenario[entries=" + this.entries.size() + "]";
        }
    }
}
