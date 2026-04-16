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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a deterministic English token corpus for side-by-side stemming
 * benchmarks.
 *
 * <p>
 * The generated corpus mixes:
 * </p>
 * <ul>
 * <li>simple inflections</li>
 * <li>common derivational forms</li>
 * <li>US/UK spelling families</li>
 * <li>forms that are suitable for comparison against the bundled
 * {@code US_UK_PROFI} Radixor dictionary</li>
 * </ul>
 *
 * <p>
 * The goal is not to simulate natural language frequency distribution exactly,
 * but to provide a stable and reproducible comparison workload for benchmark
 * runs and regression tracking.
 * </p>
 */
final class EnglishComparisonCorpus {

    /**
     * Canonical lexical bases used to generate the token workload.
     */
    private static final String[] BASES = { "analyze", "analyse", "color", "colour", "center", "centre", "organize",
            "organise", "optimize", "optimise", "characterize", "characterise", "connect", "construct", "compute",
            "design", "develop", "engineer", "govern", "improve", "index", "inform", "manage", "model", "observe",
            "operate", "perform", "predict", "prepare", "process", "project", "protect", "publish", "query", "reduce",
            "refresh", "render", "resolve", "return", "search", "select", "signal", "store", "structure", "support",
            "transform", "update", "validate", "value" };

    /**
     * Utility class.
     */
    private EnglishComparisonCorpus() {
        throw new AssertionError("No instances.");
    }

    /**
     * Creates a deterministic token corpus for English stemming comparison.
     *
     * @param familyCount number of generated lexical families
     * @return token array in stable order
     */
    static String[] createTokens(final int familyCount) {
        if (familyCount < 1) {
            throw new IllegalArgumentException("familyCount must be at least 1.");
        }

        final List<String> tokens = new ArrayList<>(familyCount * 14);

        for (int index = 0; index < familyCount; index++) {
            final String base = createBase(index);

            tokens.add(base);
            tokens.add(base + "s");
            tokens.add(base + "ed");
            tokens.add(base + "ing");
            tokens.add(base + "er");
            tokens.add(base + "ers");
            tokens.add(base + "ly");
            tokens.add(base + "ness");
            tokens.add(base + "ment");
            tokens.add(base + "ments");
            tokens.add(base + "able");
            tokens.add(base + "ability");

            if (base.endsWith("ize")) {
                tokens.add(base.substring(0, base.length() - 3) + "isation");
                tokens.add(base.substring(0, base.length() - 3) + "ised");
            }

            if (base.endsWith("ise")) {
                tokens.add(base.substring(0, base.length() - 3) + "ization");
                tokens.add(base.substring(0, base.length() - 3) + "ized");
            }
        }

        return tokens.toArray(String[]::new);
    }

    /**
     * Creates one deterministic base token.
     *
     * @param index base ordinal
     * @return generated lexical base
     */
    private static String createBase(final int index) {
        return (BASES[index % BASES.length] + suffix(index)).toLowerCase(Locale.ROOT);
    }

    /**
     * Creates a compact discriminator suffix so that large corpora remain unique
     * while retaining stable lexical families.
     *
     * @param value ordinal value
     * @return compact discriminator
     */
    private static String suffix(final int value) {
        return Integer.toString(value, Character.MAX_RADIX);
    }
}
