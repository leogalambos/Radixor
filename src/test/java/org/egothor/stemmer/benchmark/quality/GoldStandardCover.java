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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable overlapping gold-standard cover over unique surface forms.
 *
 * <p>A form may belong to several dictionary groups. Two distinct forms are a
 * gold-positive pair when they share at least one included group, and the pair
 * is counted once even when it shares several groups.</p>
 */
final class GoldStandardCover {
    private final List<GoldStandardGroup> groups;
    private final List<String> forms;
    private final List<Integer> representativeRows;
    private final Map<String, Integer> formIndexes;
    private final List<DuplicateRelation> duplicateRelations;
    private final long singletonRows;
    private final long pairRows;
    private final long relatedPairs;

    /** Builds the cover selected by one processing mode. */
    static GoldStandardCover create(final Iterable<GoldStandardGroup> source, final ProcessingMode mode) {
        final List<GoldStandardGroup> groups = new ArrayList<>();
        final Map<String, Integer> membershipCounts = new HashMap<>();
        final LinkedHashMap<String, Integer> representativeRows = new LinkedHashMap<>();
        long singletonRows = 0;
        long pairRows = 0;
        long rawRelatedPairs = 0;
        for (GoldStandardGroup group : source) {
            if (!mode.includes(group.forms())) {
                continue;
            }
            groups.add(group);
            if (group.forms().size() == 1) {
                singletonRows = add(singletonRows, 1, "singleton dictionary rows");
            } else {
                pairRows = add(pairRows, 1, "dictionary rows contributing related pairs");
            }
            rawRelatedPairs = add(rawRelatedPairs, QualityEvaluator.chooseTwo(group.forms().size()),
                    "raw gold-related pairs");
            for (String form : group.forms()) {
                membershipCounts.merge(form, 1, Math::addExact);
                representativeRows.putIfAbsent(form, group.rowNumber());
            }
        }

        final List<String> forms = List.copyOf(representativeRows.keySet());
        final Map<String, Integer> formIndexes = new HashMap<>(forms.size() * 2);
        final List<Integer> rows = new ArrayList<>(forms.size());
        for (int index = 0; index < forms.size(); index++) {
            final String form = forms.get(index);
            formIndexes.put(form, index);
            rows.add(representativeRows.get(form));
        }

        final Set<Long> seenRelations = new HashSet<>();
        final Map<Long, Integer> extraOccurrences = new HashMap<>();
        for (GoldStandardGroup group : groups) {
            final List<Integer> repeated = new ArrayList<>();
            for (String form : group.forms()) {
                if (membershipCounts.get(form) > 1) {
                    repeated.add(formIndexes.get(form));
                }
            }
            for (int left = 0; left < repeated.size(); left++) {
                for (int right = left + 1; right < repeated.size(); right++) {
                    final long key = pairKey(repeated.get(left), repeated.get(right));
                    if (!seenRelations.add(key)) {
                        extraOccurrences.merge(key, 1, Math::addExact);
                    }
                }
            }
        }
        final List<DuplicateRelation> duplicates = new ArrayList<>(extraOccurrences.size());
        long duplicateCount = 0;
        for (Map.Entry<Long, Integer> entry : extraOccurrences.entrySet()) {
            final long key = entry.getKey();
            final int extra = entry.getValue();
            duplicates.add(new DuplicateRelation((int) (key >>> 32), (int) key, extra));
            duplicateCount = add(duplicateCount, extra, "duplicate gold-relation occurrences");
        }
        duplicates.sort(null);
        return new GoldStandardCover(List.copyOf(groups), forms, List.copyOf(rows), Map.copyOf(formIndexes),
                List.copyOf(duplicates), singletonRows, pairRows,
                subtract(rawRelatedPairs, duplicateCount, "unique gold-related pairs"));
    }

    private GoldStandardCover(final List<GoldStandardGroup> groups, final List<String> forms,
            final List<Integer> representativeRows, final Map<String, Integer> formIndexes,
            final List<DuplicateRelation> duplicateRelations, final long singletonRows,
            final long pairRows, final long relatedPairs) {
        this.groups = groups;
        this.forms = forms;
        this.representativeRows = representativeRows;
        this.formIndexes = formIndexes;
        this.duplicateRelations = duplicateRelations;
        this.singletonRows = singletonRows;
        this.pairRows = pairRows;
        this.relatedPairs = relatedPairs;
    }

    /** Returns the included source groups. */
    List<GoldStandardGroup> groups() { return groups; }
    /** Returns every included surface form exactly once. */
    List<String> forms() { return forms; }
    /** Returns a source row suitable for diagnostics for one unique form. */
    int representativeRow(final int formIndex) { return representativeRows.get(formIndex); }
    /** Returns the unique index of a surface form. */
    int indexOf(final String form) { return formIndexes.get(form); }
    /** Returns relations repeated by more than one group. */
    List<DuplicateRelation> duplicateRelations() { return duplicateRelations; }
    /** Returns the number of included singleton rows. */
    long singletonRows() { return singletonRows; }
    /** Returns the number of included rows containing a relation. */
    long pairRows() { return pairRows; }
    /** Returns the number of unique gold-positive form pairs. */
    long relatedPairs() { return relatedPairs; }

    /** Encodes an unordered pair of non-negative form indexes. */
    private static long pairKey(final int first, final int second) {
        final int left = Math.min(first, second);
        final int right = Math.max(first, second);
        return ((long) left << 32) | (right & 0xffffffffL);
    }

    /** Checked addition with metric context. */
    private static long add(final long left, final long right, final String context) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Arithmetic overflow in " + context + ".", exception);
        }
    }

    /** Checked subtraction with metric context. */
    private static long subtract(final long left, final long right, final String context) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Arithmetic overflow in " + context + ".", exception);
        }
    }

    /** One relation counted by more than one source group. */
    record DuplicateRelation(int leftFormIndex, int rightFormIndex, int extraOccurrences)
            implements Comparable<DuplicateRelation> {
        /** Orders relations deterministically by their form indexes. */
        @Override
        public int compareTo(final DuplicateRelation other) {
            final int leftComparison = Integer.compare(leftFormIndex, other.leftFormIndex);
            return leftComparison != 0 ? leftComparison : Integer.compare(rightFormIndex, other.rightFormIndex);
        }
    }
}
