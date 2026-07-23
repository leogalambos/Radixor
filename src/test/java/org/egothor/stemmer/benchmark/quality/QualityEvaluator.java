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

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.List;

import org.egothor.stemmer.benchmark.QualityStemmerMatrix.BatchStemmer;

/** Evaluates pairwise agreement with an overlapping gold-standard cover. */
public final class QualityEvaluator {
    /** Utility class. */
    private QualityEvaluator() { throw new AssertionError("No instances."); }

    /**
     * Evaluates one scenario in time proportional to forms and group-to-stem associations.
     * All combinatorial arithmetic is checked and overflow is reported.
     *
     * @param stemmerName stable stemmer name
     * @param language stable language identifier
     * @param mode processing mode
     * @param groups parsed gold-standard groups
     * @param stemmer stemmer implementation
     * @return immutable metric result
     */
    public static QualityResult evaluate(final String stemmerName, final String language, final ProcessingMode mode,
            final Iterable<GoldStandardGroup> groups, final StemmerFunction stemmer) {
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(stemmer, "stemmer");
        return evaluate(stemmerName, language, mode, GoldStandardCover.create(groups, mode), stemmer);
    }

    /** Evaluates one scenario over a prebuilt overlapping gold-standard cover. */
    private static QualityResult evaluate(final String stemmerName, final String language,
            final ProcessingMode mode, final GoldStandardCover cover, final StemmerFunction stemmer) {
        final Map<String, Long> global = new HashMap<>();
        long withinSameStem = 0;
        final Set<String> stems = new HashSet<>();
        final Map<String, Long> local = new HashMap<>();
        final String[] outputs = new String[cover.forms().size()];
        for (int index = 0; index < outputs.length; index++) {
            final String form = cover.forms().get(index);
            final String output;
            try {
                output = stemmer.stem(form);
            } catch (IOException exception) {
                throw failure(stemmerName, language, mode, cover.representativeRow(index), form,
                        "the stemmer threw an exception", exception);
            }
            if (output == null) {
                throw failure(stemmerName, language, mode, cover.representativeRow(index), form,
                        "the stemmer returned null", null);
            }
            outputs[index] = output;
            global.merge(output, 1L, (left, right) -> add(left, right, "global stem frequency"));
            stems.add(output);
        }
        for (GoldStandardGroup group : cover.groups()) {
            local.clear();
            for (String form : group.forms()) {
                final String output = outputs[cover.indexOf(form)];
                local.merge(output, 1L, (left, right) -> add(left, right, "group-to-stem frequency"));
            }
            for (long frequency : local.values()) {
                withinSameStem = add(withinSameStem, chooseTwo(frequency), "within-group merged pairs");
            }
        }
        for (GoldStandardCover.DuplicateRelation duplicate : cover.duplicateRelations()) {
            if (outputs[duplicate.leftFormIndex()].equals(outputs[duplicate.rightFormIndex()])) {
                withinSameStem = subtract(withinSameStem, duplicate.extraOccurrences(),
                        "duplicate within-group merged pairs");
            }
        }
        final long words = cover.forms().size();
        final long underPossible = cover.relatedPairs();
        long allPairs = chooseTwo(words);
        long overPossible = subtract(allPairs, underPossible, "over-stemming possible pairs");
        long allSameStem = 0;
        for (long frequency : global.values()) {
            allSameStem = add(allSameStem, chooseTwo(frequency), "same-stem pairs");
        }
        final long underError = subtract(underPossible, withinSameStem, "under-stemming error pairs");
        final long overError = subtract(allSameStem, withinSameStem, "over-stemming error pairs");
        return new QualityResult(stemmerName, language, mode, OutputPolicy.PRIMARY_OUTPUT,
                cover.groups().size(), words, cover.singletonRows(), cover.pairRows(), words, 0,
                words == 0 ? 0 : 1, words, stems.size(), overError, overPossible,
                underError, underPossible, null);
    }

    /**
     * Evaluates one scenario through an authoritative JMH batch adapter.
     * The temporary input and output arrays are required to preserve TokenStream
     * lifecycle and preprocessing semantics used by the JMH comparison.
     *
     * @param stemmerName stable JMH candidate name
     * @param language registered dictionary language
     * @param mode processing mode
     * @param groups parsed gold-standard groups
     * @param stemmer scenario-confined batch adapter
     * @return immutable pairwise result
     * @throws IOException when the benchmark adapter fails
     */
    public static QualityResult evaluateBatch(final String stemmerName, final String language,
            final ProcessingMode mode, final List<GoldStandardGroup> groups, final BatchStemmer stemmer)
            throws IOException {
        final GoldStandardCover cover = GoldStandardCover.create(groups, mode);
        final String[] outputs = stemmer.stem(cover.forms().toArray(String[]::new));
        if (outputs == null || outputs.length != cover.forms().size()) {
            throw new IOException("JMH stemmer " + stemmerName + " returned an invalid output batch for language "
                    + language + " and processing mode " + mode + ".");
        }
        final int[] index = {0};
        return evaluate(stemmerName, language, mode, cover, word -> {
            final String output = outputs[index[0]++];
            if (output == null) {
                throw new IOException("JMH stemmer " + stemmerName + " returned null for language " + language
                        + ", processing mode " + mode + ", and word form '" + word + "'.");
            }
            return output;
        });
    }

    /** Calculates C2(n) with checked arithmetic. */
    /* default */ static long chooseTwo(final long value) {
        if (value < 0) { throw new IllegalArgumentException("Pair population must not be negative."); }
        try {
            return value % 2 == 0 ? Math.multiplyExact(value / 2, value - 1)
                    : Math.multiplyExact(value, (value - 1) / 2);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Arithmetic overflow while calculating unordered word-form pairs.", exception);
        }
    }
    /** Checked addition with metric context. */
    private static long add(final long left, final long right, final String context) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException exception) { throw new IllegalStateException("Arithmetic overflow in " + context + ".", exception); }
    }
    /** Checked subtraction with metric context. */
    private static long subtract(final long left, final long right, final String context) {
        try { return Math.subtractExact(left, right); }
        catch (ArithmeticException exception) { throw new IllegalStateException("Arithmetic overflow in " + context + ".", exception); }
    }
    /** Builds a contextual failure without producing a partial result. */
    private static IllegalStateException failure(final String stemmer, final String language,
            final ProcessingMode mode, final int row, final String form, final String reason, final Exception cause) {
        final String message = "Quality evaluation failed for stemmer " + stemmer + ", language " + language
                + ", processing mode " + mode + ", dictionary row " + row + ", word form '" + form + "': " + reason + ".";
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }
}
