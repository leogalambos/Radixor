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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.SplittableRandom;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.PatchCommandEncoder;
import org.egothor.stemmer.ReductionSettings;
import org.egothor.stemmer.StemmerDictionaryParser;

/**
 * Builds deterministic benchmark corpora used by the JMH suite.
 *
 * <p>
 * The generated corpus is intentionally synthetic but morphology-shaped: it
 * creates a stable base vocabulary and derives common inflectional and
 * derivational variants from each stem. The corpus also injects a controlled
 * amount of homograph ambiguity so that {@link FrequencyTrie#getAll(String)} is
 * measured on keys that really produce multiple candidate patch commands.
 * </p>
 */
final class BenchmarkCorpusSupport {

    /**
     * Prefixes used to synthesize pronounceable stems.
     */
    private static final String[] PREFIXES = {
            "adapt", "align", "anchor", "answer", "apply", "balance", "build", "capture", "center",
            "change", "collect", "connect", "convert", "cover", "create", "cycle", "declare", "define",
            "deliver", "derive", "design", "detect", "develop", "drive", "encode", "extend", "filter",
            "form", "govern", "handle", "improve", "index", "inform", "inspect", "join", "launch",
            "limit", "manage", "map", "model", "move", "observe", "operate", "organ", "pattern",
            "perform", "plan", "predict", "prepare", "process", "project", "protect", "publish", "query",
            "reduce", "refresh", "render", "repeat", "resolve", "return", "scale", "search", "select",
            "shape", "signal", "sort", "state", "store", "stream", "structure", "supply", "support",
            "switch", "trace", "transform", "update", "validate", "value"
    };

    /**
     * Suffixes used to diversify stems.
     */
    private static final String[] STEM_SUFFIXES = {
            "", "er", "or", "al", "ive", "ion", "ent", "ant", "ure", "ment", "ist", "ity"
    };

    /**
     * Number of neighboring stems sharing one ambiguous surface form.
     */
    private static final int HOMOGRAPH_GROUP_SIZE = 4;

    /**
     * Utility class.
     */
    private BenchmarkCorpusSupport() {
        throw new AssertionError("No instances.");
    }

    /**
     * Creates a deterministic benchmark corpus.
     *
     * @param stemCount number of canonical stems to generate
     * @return immutable benchmark corpus description
     */
    static BenchmarkCorpus createCorpus(final int stemCount) {
        if (stemCount < 1) {
            throw new IllegalArgumentException("stemCount must be at least 1.");
        }

        final StringBuilder dictionaryBuilder = new StringBuilder(stemCount * 120);
        final LinkedHashSet<String> lookupKeys = new LinkedHashSet<>(stemCount * 8);
        final LinkedHashSet<String> ambiguousLookupKeys = new LinkedHashSet<>(Math.max(1, stemCount / 4));
        final SplittableRandom random = new SplittableRandom(20260414L);

        for (int index = 0; index < stemCount; index++) {
            final String stem = createStem(index);
            final String[] variants = createVariants(stem, random, index);

            dictionaryBuilder.append(stem);
            lookupKeys.add(stem);
            for (String variant : variants) {
                dictionaryBuilder.append(' ').append(variant);
                lookupKeys.add(variant);
            }

            final String homograph = createHomograph(index);
            dictionaryBuilder.append(' ').append(homograph);
            lookupKeys.add(homograph);
            ambiguousLookupKeys.add(homograph);

            dictionaryBuilder.append('\n');
        }

        return new BenchmarkCorpus(
                dictionaryBuilder.toString(),
                lookupKeys.toArray(String[]::new),
                ambiguousLookupKeys.toArray(String[]::new));
    }

    /**
     * Builds a compiled trie from benchmark corpus text.
     *
     * @param corpusText        line-oriented dictionary text
     * @param reductionSettings reduction settings
     * @param storeOriginalStem whether the canonical stem itself should also be
     *                          inserted with the no-op patch
     * @return compiled trie containing patch commands
     * @throws IOException if parsing fails
     */
    static FrequencyTrie<String> compilePatchTrie(
            final String corpusText,
            final ReductionSettings reductionSettings,
            final boolean storeOriginalStem) throws IOException {
        Objects.requireNonNull(corpusText, "corpusText");
        Objects.requireNonNull(reductionSettings, "reductionSettings");

        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<>(String[]::new, reductionSettings);
        final PatchCommandEncoder encoder = new PatchCommandEncoder();

        StemmerDictionaryParser.parse(
                new StringReader(corpusText),
                "benchmark-corpus",
                (stem, variants, lineNumber) -> {
                    if (storeOriginalStem) {
                        builder.put(stem, encoder.encode(stem, stem));
                    }
                    for (String variant : variants) {
                        builder.put(variant, encoder.encode(variant, stem));
                    }
                });

        return builder.build();
    }

    /**
     * Creates one deterministic stem.
     *
     * @param index stem ordinal
     * @return generated stem
     */
    private static String createStem(final int index) {
        final String prefix = PREFIXES[index % PREFIXES.length];
        final String suffix = STEM_SUFFIXES[(index / PREFIXES.length) % STEM_SUFFIXES.length];
        return (prefix + suffix + base36(index)).toLowerCase(Locale.ROOT);
    }

    /**
     * Creates a set of deterministic variants for one stem.
     *
     * @param stem   canonical stem
     * @param random deterministic random source
     * @param index  stem ordinal
     * @return generated variants in stable order
     */
    private static String[] createVariants(final String stem, final SplittableRandom random, final int index) {
        final List<String> variants = new ArrayList<>(8);
        variants.add(stem + "s");
        variants.add(stem + "ed");
        variants.add(stem + "ing");
        variants.add(stem + "er");
        variants.add(stem + "ers");
        variants.add("pre" + stem);
        variants.add(stem + random.nextInt(10));

        if ((index & 1) == 0) {
            variants.add(stem + "ly");
        }
        if (stem.length() > 5) {
            variants.add(stem.substring(0, stem.length() - 1));
        }
        return variants.toArray(String[]::new);
    }

    /**
     * Creates an ambiguous surface form shared by a small group of stems.
     *
     * @param index stem ordinal
     * @return shared homograph form
     */
    private static String createHomograph(final int index) {
        return "shared" + base36(index / HOMOGRAPH_GROUP_SIZE);
    }

    /**
     * Converts an ordinal into a compact base-36 discriminator.
     *
     * @param value numeric value
     * @return compact discriminator
     */
    private static String base36(final int value) {
        return Integer.toString(value, Character.MAX_RADIX);
    }

    /**
     * Immutable benchmark corpus.
     *
     * @param dictionaryText      full line-oriented dictionary text
     * @param lookupKeys          keys used for general lookup measurements
     * @param ambiguousLookupKeys keys that return multiple patch candidates from
     *                            {@code getAll()}
     */
    record BenchmarkCorpus(String dictionaryText, String[] lookupKeys, String[] ambiguousLookupKeys) {
    }
}
