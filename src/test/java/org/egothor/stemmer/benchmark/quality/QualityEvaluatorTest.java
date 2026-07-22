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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Mathematical and filtering tests for pairwise quality evaluation. */
@Tag("unit")
@DisplayName("Pairwise stemming-quality evaluator")
final class QualityEvaluatorTest {
    /** Verifies a perfect partition. */
    @Test @DisplayName("A perfect predicted partition has no errors")
    void perfectPartition() {
        final QualityResult result = evaluate(List.of(group(1, "a", "b"), group(2, "c", "d")),
                Map.of("a", "x", "b", "x", "c", "y", "d", "y"));
        assertEquals(0, result.overErrorPairs()); assertEquals(4, result.overPossiblePairs());
        assertEquals(0, result.underErrorPairs()); assertEquals(2, result.underPossiblePairs());
        assertEquals(2, result.distinctOutputStems());
        assertEquals(1.0, result.partitionMetrics().adjustedRandIndex(), 1.0e-12);
        assertEquals(1.0, result.partitionMetrics().homogeneity(), 1.0e-12);
        assertEquals(1.0, result.partitionMetrics().completeness(), 1.0e-12);
        assertEquals(1.0, result.partitionMetrics().vMeasure(), 1.0e-12);
        assertEquals(1.0, result.partitionMetrics().normalizedMutualInformation(), 1.0e-12);
    }
    /** Verifies partial merge and pure under-stemming pair counts. */
    @Test @DisplayName("A partial within-group merge is counted by pairs")
    void partialMerge() {
        final QualityResult result = evaluate(List.of(group(1, "a", "b", "c"), group(2, "d")),
                Map.of("a", "x", "b", "x", "c", "z", "d", "q"));
        assertEquals(2, result.underErrorPairs()); assertEquals(3, result.underPossiblePairs());
        assertEquals(0, result.overErrorPairs()); assertEquals(3, result.overPossiblePairs());
    }
    /** Verifies multi-group over-stemming combinatorics. */
    @Test @DisplayName("Several gold groups colliding in one stem count every cross-group pair")
    void multipleGroupsCollide() {
        final QualityResult result = evaluate(List.of(group(1, "a", "b"), group(2, "c"), group(3, "d", "e", "f")),
                Map.of("a", "x", "b", "x", "c", "x", "d", "x", "e", "x", "f", "x"));
        assertEquals(11, result.overErrorPairs()); assertEquals(11, result.overPossiblePairs());
        assertEquals(0, result.underErrorPairs());
    }
    /** Verifies combined split and collision counts. */
    @Test @DisplayName("Combined over-stemming and under-stemming are independent")
    void combinedErrors() {
        final QualityResult result = evaluate(List.of(group(1, "a", "b", "c"), group(2, "d", "e")),
                Map.of("a", "x", "b", "x", "c", "y", "d", "y", "e", "y"));
        assertEquals(2, result.overErrorPairs()); assertEquals(6, result.overPossiblePairs());
        assertEquals(2, result.underErrorPairs()); assertEquals(4, result.underPossiblePairs());
    }
    /** Verifies duplicate scope and singleton undefined denominator. */
    @Test @DisplayName("Duplicates are removed only within a group and singleton under-stemming is undefined")
    void duplicateScope() {
        final QualityResult result = evaluate(List.of(group(1, "same", "same"), group(2, "same")), Map.of("same", "x"));
        assertEquals(2, result.processedWordForms()); assertEquals(1, result.overErrorPairs());
        assertTrue(result.underPercentage().isEmpty());
    }
    /** Verifies the zero over-stemming denominator. */
    @Test @DisplayName("One gold group has an undefined over-stemming percentage")
    void zeroOverDenominator() {
        final QualityResult result = evaluate(List.of(group(1, "a", "b")), Map.of("a", "x", "b", "y"));
        assertTrue(result.overPercentage().isEmpty()); assertFalse(result.underPercentage().isEmpty());
    }
    /** Verifies Unicode code-point filtering and uncased data. */
    @Test @DisplayName("Lowercase filtering detects uppercase and titlecase code points without excluding uncased symbols")
    void lowercaseFiltering() {
        assertTrue(ProcessingMode.LOWERCASE_GROUPS_ONLY.includes(List.of("žluťoučký-123", "தமிழ்")));
        assertFalse(ProcessingMode.LOWERCASE_GROUPS_ONLY.includes(List.of("Upper")));
        assertFalse(ProcessingMode.LOWERCASE_GROUPS_ONLY.includes(List.of("ǅungla")));
        assertFalse(ProcessingMode.LOWERCASE_GROUPS_ONLY.includes(List.of("a\uD801\uDC00")));
    }
    /** Verifies contextual stemmer failures. */
    @Test @DisplayName("Stemmer exceptions contain complete scenario context")
    void stemmerFailure() {
        final IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> QualityEvaluator.evaluate("Broken", "TEST", ProcessingMode.ALL_WORDS,
                        List.of(group(7, "word")), word -> { throw new IOException("failure"); }));
        assertTrue(exception.getMessage().contains("dictionary row 7")); assertTrue(exception.getMessage().contains("word form 'word'"));
    }
    /** Verifies the largest safe and first overflowing combinatorial values. */
    @Test @DisplayName("Pair calculation detects arithmetic overflow")
    void arithmeticBoundary() {
        assertEquals(4_611_686_013_944_624_251L, QualityEvaluator.chooseTwo(3_037_000_499L));
        assertThrows(IllegalStateException.class, () -> QualityEvaluator.chooseTwo(Long.MAX_VALUE));
    }

    /** Demonstrates the documented denominator difference from exact accuracy. */
    @Test @DisplayName("Ninety-nine percent exact accuracy can coexist with sixteen percent pairwise under-stemming")
    void exactAccuracyAndPairwiseRateUseDifferentDenominators() {
        final List<GoldStandardGroup> groups = new ArrayList<>();
        final Map<String, String> stems = new HashMap<>();
        for (int index = 0; index < 88; index++) {
            final String form = "singleton-" + index;
            groups.add(group(index + 1, form));
            stems.put(form, form);
        }
        final String[] largeGroup = new String[12];
        for (int index = 0; index < largeGroup.length; index++) {
            largeGroup[index] = "form-" + index;
            stems.put(largeGroup[index], index == 11 ? "different" : "shared");
        }
        groups.add(group(89, largeGroup));
        final QualityResult result = evaluate(groups, stems);
        assertEquals(100, result.processedWordForms());
        assertEquals(66, result.underPossiblePairs());
        assertEquals(11, result.underErrorPairs());
        assertEquals(16.666666666666668, result.underPercentage().orElseThrow(), 0.000000000000001);
    }

    /** Compares the optimized accumulator with an independent explicit pair oracle. */
    @Test @DisplayName("Deterministic randomized partitions agree with a brute-force pair oracle")
    void randomizedOracleAgreement() {
        final Random random = new Random(0x52414449584f52L);
        for (int trial = 0; trial < 250; trial++) {
            final List<GoldStandardGroup> groups = new ArrayList<>();
            final Map<String, String> stems = new HashMap<>();
            final Map<String, Integer> gold = new HashMap<>();
            final int groupCount = 1 + random.nextInt(7);
            int formIndex = 0;
            for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
                final int size = 1 + random.nextInt(6);
                final String[] forms = new String[size];
                for (int index = 0; index < size; index++) {
                    final String form = "t" + trial + "-f" + formIndex++;
                    forms[index] = form;
                    gold.put(form, groupIndex);
                    stems.put(form, "s" + random.nextInt(6));
                }
                groups.add(group(groupIndex + 1, forms));
            }
            final QualityResult optimized = evaluate(groups, stems);
            final long[] oracle = bruteForce(new ArrayList<>(gold.keySet()), gold, stems);
            assertEquals(oracle[0], optimized.underErrorPairs(), "Under-stemming errors differed in trial " + trial);
            assertEquals(oracle[1], optimized.underPossiblePairs(), "Under-stemming denominator differed in trial " + trial);
            assertEquals(oracle[2], optimized.overErrorPairs(), "Over-stemming errors differed in trial " + trial);
            assertEquals(oracle[3], optimized.overPossiblePairs(), "Over-stemming denominator differed in trial " + trial);
        }
    }

    /** Explicit quadratic oracle used only for small controlled test data. */
    private static long[] bruteForce(final List<String> forms, final Map<String, Integer> gold,
            final Map<String, String> stems) {
        long underError = 0;
        long underPossible = 0;
        long overError = 0;
        long overPossible = 0;
        for (int left = 0; left < forms.size(); left++) {
            for (int right = left + 1; right < forms.size(); right++) {
                final boolean sameGold = gold.get(forms.get(left)).equals(gold.get(forms.get(right)));
                final boolean sameStem = stems.get(forms.get(left)).equals(stems.get(forms.get(right)));
                if (sameGold) {
                    underPossible++;
                    if (!sameStem) { underError++; }
                } else {
                    overPossible++;
                    if (sameStem) { overError++; }
                }
            }
        }
        return new long[] {underError, underPossible, overError, overPossible};
    }
    /** Builds a group. */
    private static GoldStandardGroup group(final int row, final String... forms) { return new GoldStandardGroup(row, List.of(forms)); }
    /** Runs the common synthetic evaluator. */
    private static QualityResult evaluate(final List<GoldStandardGroup> groups, final Map<String, String> stems) {
        return QualityEvaluator.evaluate("Synthetic", "TEST", ProcessingMode.ALL_WORDS, groups, stems::get);
    }
}
