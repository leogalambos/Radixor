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
 * 3. All advertising materials mentioning features or use of this software must
 *    display the following acknowledgement:
 *    This product includes software developed by the Egothor project.
 * 
 * 4. Neither the name of the copyright holder nor the names of its contributors
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
package org.egothor.stemmer.trie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import org.egothor.stemmer.ReductionSettings;

/**
 * Local terminal value summary of a node.
 *
 * @param <V> value type
 */
public final class LocalValueSummary<V> {

    /**
     * Locally stored values ordered by descending frequency.
     */
    private final V[] orderedValues;

    /**
     * Frequencies aligned with {@link #orderedValues}.
     */
    private final int[] orderedCounts;

    /**
     * Total local frequency.
     */
    private final int totalCount;

    /**
     * Winning value, or {@code null} if the node has no local value.
     */
    /* default */ final V dominantValue;

    /**
     * Winning value frequency.
     */
    private final int dominantCount;

    /**
     * Second best value frequency.
     */
    private final int secondCount;

    /**
     * Creates a summary.
     *
     * @param orderedValues ordered values
     * @param orderedCounts ordered counts
     * @param totalCount    total count
     * @param dominantValue dominant value
     * @param dominantCount dominant count
     * @param secondCount   second count
     */
    public LocalValueSummary(final V[] orderedValues, final int[] orderedCounts, final int totalCount,
            final V dominantValue, final int dominantCount, final int secondCount) {
        this.orderedValues = orderedValues;
        this.orderedCounts = orderedCounts;
        this.totalCount = totalCount;
        this.dominantValue = dominantValue;
        this.dominantCount = dominantCount;
        this.secondCount = secondCount;
    }

    /**
     * Builds a summary from local counts.
     *
     * @param counts       local counts
     * @param arrayFactory array factory
     * @param <V>          value type
     * @return summary
     */
    public static <V> LocalValueSummary<V> of(final Map<V, Integer> counts, final IntFunction<V[]> arrayFactory) {
        final List<SortableValue<V>> entries = new ArrayList<>(counts.size());
        int insertionOrder = 0;
        for (Map.Entry<V, Integer> entry : counts.entrySet()) {
            entries.add(new SortableValue<>(entry.getKey(), entry.getValue(), String.valueOf(entry.getKey()),
                    insertionOrder++));
        }

        entries.sort((left, right) -> {
            final int frequencyCompare = Integer.compare(right.count(), left.count());
            if (frequencyCompare != 0) {
                return frequencyCompare;
            }

            final int lengthCompare = Integer.compare(left.textLength(), right.textLength());
            if (lengthCompare != 0) {
                return lengthCompare;
            }

            final int textCompare = left.text().compareTo(right.text());
            if (textCompare != 0) {
                return textCompare;
            }

            return Integer.compare(left.insertionOrder(), right.insertionOrder());
        });

        final V[] orderedValues = arrayFactory.apply(entries.size());
        final int[] orderedCounts = new int[entries.size()];

        int totalCount = 0;
        for (int index = 0; index < entries.size(); index++) {
            final SortableValue<V> entry = entries.get(index);
            orderedValues[index] = entry.value();
            orderedCounts[index] = entry.count();
            totalCount += orderedCounts[index];
        }

        final V dominantValue = orderedValues.length == 0 ? null : orderedValues[0];
        final int dominantCount = orderedCounts.length == 0 ? 0 : orderedCounts[0];
        final int secondCount = orderedCounts.length < 2 ? 0 : orderedCounts[1];

        return new LocalValueSummary<>(orderedValues, orderedCounts, totalCount, dominantValue, dominantCount,
                secondCount);
    }

    /**
     * Returns ordered values.
     *
     * @return ordered values
     */
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    public V[] orderedValues() {
        return this.orderedValues;
    }

    /**
     * Returns ordered counts.
     *
     * @return ordered counts
     */
    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    public int[] orderedCounts() {
        return this.orderedCounts;
    }

    /**
     * Indicates whether the dominant value satisfies the configured dominance
     * constraints.
     *
     * @param settings reduction settings
     * @return {@code true} if dominant, otherwise {@code false}
     */
    /* default */ boolean hasQualifiedDominantWinner(final ReductionSettings settings) {
        if (this.dominantValue == null) {
            return false;
        }

        final int thresholdPercent = settings.dominantWinnerMinPercent();
        final int ratio = settings.dominantWinnerOverSecondRatio();

        final boolean percentSatisfied = this.dominantCount * 100L >= (long) this.totalCount * thresholdPercent;

        final boolean ratioSatisfied;
        if (this.secondCount == 0) {
            ratioSatisfied = true;
        } else {
            ratioSatisfied = this.dominantCount >= (long) this.secondCount * ratio;
        }

        return percentSatisfied && ratioSatisfied;
    }
}
