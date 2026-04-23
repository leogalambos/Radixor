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

import java.util.Objects;

/**
 * Immutable metadata persisted together with a compiled trie artifact.
 *
 * <p>
 * The metadata captures the semantic build configuration required to interpret
 * the compiled trie correctly after it is reloaded. Persisting the metadata as
 * part of the artifact makes the binary format self-describing and avoids
 * coupling runtime consumers to external side-channel configuration.
 * </p>
 *
 * <p>
 * The record is intentionally extensible. It already models traversal
 * direction, reduction settings, and diacritic processing strategy, even though
 * not every field necessarily influences all current code paths yet.
 * </p>
 *
 * @param formatVersion           persisted binary format version of the trie
 *                                artifact
 * @param traversalDirection      logical key traversal direction
 * @param reductionSettings       reduction settings used during compilation
 * @param diacriticProcessingMode diacritic processing strategy associated with
 *                                the artifact
 * @param caseProcessingMode      case processing strategy associated with the
 *                                artifact
 */
public record TrieMetadata(int formatVersion, WordTraversalDirection traversalDirection,
        ReductionSettings reductionSettings, DiacriticProcessingMode diacriticProcessingMode,
        CaseProcessingMode caseProcessingMode) {

    /**
     * Creates a new metadata instance.
     *
     * @param formatVersion           persisted binary format version, must be at
     *                                least {@code 1}
     * @param traversalDirection      logical key traversal direction
     * @param reductionSettings       reduction settings used during compilation
     * @param diacriticProcessingMode diacritic processing strategy
     * @param caseProcessingMode      case processing strategy
     */
    public TrieMetadata(final int formatVersion, final WordTraversalDirection traversalDirection,
            final ReductionSettings reductionSettings, final DiacriticProcessingMode diacriticProcessingMode,
            final CaseProcessingMode caseProcessingMode) {
        if (formatVersion < 1) { // NOPMD
            throw new IllegalArgumentException("formatVersion must be at least 1.");
        }
        this.formatVersion = formatVersion;
        this.traversalDirection = Objects.requireNonNull(traversalDirection, "traversalDirection");
        this.reductionSettings = Objects.requireNonNull(reductionSettings, "reductionSettings");
        this.diacriticProcessingMode = Objects.requireNonNull(diacriticProcessingMode, "diacriticProcessingMode");
        this.caseProcessingMode = Objects.requireNonNull(caseProcessingMode, "caseProcessingMode");
    }

    /**
     * Creates metadata populated with current-format defaults for freshly compiled
     * tries.
     *
     * @param formatVersion      persisted binary format version
     * @param traversalDirection logical key traversal direction
     * @param reductionSettings  reduction settings used during compilation
     * @return metadata initialized with current defaults
     */
    public static TrieMetadata current(final int formatVersion, final WordTraversalDirection traversalDirection,
            final ReductionSettings reductionSettings) {
        return new TrieMetadata(formatVersion, traversalDirection, reductionSettings, DiacriticProcessingMode.AS_IS,
                CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
    }

    /**
     * Creates metadata compatible with a legacy artifact version that did not store
     * the full configuration explicitly.
     *
     * @param formatVersion      legacy persisted binary format version
     * @param traversalDirection logical key traversal direction reconstructed from
     *                           the legacy stream
     * @return metadata reconstructed with conservative compatibility defaults
     */
    public static TrieMetadata legacy(final int formatVersion, final WordTraversalDirection traversalDirection) {
        return new TrieMetadata(formatVersion, traversalDirection,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                DiacriticProcessingMode.AS_IS, CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
    }
}
