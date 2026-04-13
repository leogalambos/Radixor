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
package org.egothor.stemmer;

import java.util.Objects;

/**
 * Immutable reduction configuration used by {@link FrequencyTrie.Builder}.
 *
 * <p>
 * The settings influence how mutable trie nodes are merged into canonical
 * read-only nodes during compilation.
 * 
 * @param reductionMode                 reduction mode
 * @param dominantWinnerMinPercent      minimum dominant winner percentage
 * @param dominantWinnerOverSecondRatio minimum winner-over-second ratio
 */
@SuppressWarnings("PMD.LongVariable")
public record ReductionSettings(ReductionMode reductionMode, int dominantWinnerMinPercent,
        int dominantWinnerOverSecondRatio) {

    /**
     * Default minimum dominant winner percentage.
     */
    public static final int DEFAULT_DOMINANT_WINNER_MIN_PERCENT = 75;

    /**
     * Default minimum winner-over-second ratio.
     */
    public static final int DEFAULT_DOMINANT_WINNER_OVER_SECOND_RATIO = 3;

    /**
     * Creates a new instance.
     *
     * @param reductionMode                 reduction mode
     * @param dominantWinnerMinPercent      minimum dominant winner percentage in
     *                                      the inclusive range {@code 1..100}
     * @param dominantWinnerOverSecondRatio minimum winner-over-second ratio, must
     *                                      be at least {@code 1}
     * @throws NullPointerException     if {@code reductionMode} is {@code null}
     * @throws IllegalArgumentException if any numeric value is outside the valid
     *                                  range
     */
    public ReductionSettings(final ReductionMode reductionMode, final int dominantWinnerMinPercent,
            final int dominantWinnerOverSecondRatio) {
        this.reductionMode = Objects.requireNonNull(reductionMode, "reductionMode");
        if (dominantWinnerMinPercent < 1 || dominantWinnerMinPercent > 100) {
            throw new IllegalArgumentException("dominantWinnerMinPercent must be in range 1..100.");
        }
        if (dominantWinnerOverSecondRatio < 1) { // NOPMD
            throw new IllegalArgumentException("dominantWinnerOverSecondRatio must be at least 1.");
        }
        this.dominantWinnerMinPercent = dominantWinnerMinPercent;
        this.dominantWinnerOverSecondRatio = dominantWinnerOverSecondRatio;
    }

    /**
     * Creates settings with default dominance thresholds.
     *
     * @param reductionMode reduction mode
     * @return new settings instance
     * @throws NullPointerException if {@code reductionMode} is {@code null}
     */
    public static ReductionSettings withDefaults(final ReductionMode reductionMode) {
        return new ReductionSettings(reductionMode, DEFAULT_DOMINANT_WINNER_MIN_PERCENT,
                DEFAULT_DOMINANT_WINNER_OVER_SECOND_RATIO);
    }
}
