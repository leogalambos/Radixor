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

import java.util.HashMap;
import java.util.Map;
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
     * Header identifying the human-readable metadata block layout.
     */
    private static final String TEXT_BLOCK_HEADER = "radixor.metadata.v1";

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
     * Creates metadata for a newly compiled trie using the currently persisted
     * binary stream format version.
     *
     * @param traversalDirection      logical key traversal direction
     * @param reductionSettings       reduction settings used during compilation
     * @param diacriticProcessingMode diacritic processing strategy
     * @param caseProcessingMode      case processing strategy
     * @return metadata aligned with the current persisted stream format
     */
    public static TrieMetadata forCompilation(final WordTraversalDirection traversalDirection,
            final ReductionSettings reductionSettings, final DiacriticProcessingMode diacriticProcessingMode,
            final CaseProcessingMode caseProcessingMode) {
        return new TrieMetadata(FrequencyTrie.currentFormatVersion(), traversalDirection, reductionSettings,
                diacriticProcessingMode, caseProcessingMode);
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

    /**
     * Returns metadata encoded as a deterministic human-readable text block.
     *
     * <p>
     * The format intentionally uses plain {@code key=value} lines so users can
     * inspect metadata quickly from a decompressed trie payload without additional
     * dependencies.
     * </p>
     *
     * @return persisted metadata text block
     */
    @SuppressWarnings("PMD.ConsecutiveLiteralAppends")
    public String toTextBlock() {
        final StringBuilder textBlockBuilder = new StringBuilder(1024);
        textBlockBuilder.append(TEXT_BLOCK_HEADER).append('\n')
                //
                .append("formatVersion=").append(this.formatVersion).append('\n')
                //
                .append("traversalDirection=").append(this.traversalDirection.name()).append('\n')
                //
                .append("rightToLeft=").append(this.traversalDirection == WordTraversalDirection.FORWARD).append('\n')
                //
                .append("reductionMode=").append(this.reductionSettings.reductionMode().name()).append('\n')
                //
                .append("dominantWinnerMinPercent=").append(this.reductionSettings.dominantWinnerMinPercent())
                .append('\n')
                //
                .append("dominantWinnerOverSecondRatio=").append(this.reductionSettings.dominantWinnerOverSecondRatio())
                .append('\n')
                //
                .append("diacriticProcessingMode=").append(this.diacriticProcessingMode.name()).append('\n')
                //
                .append("caseProcessingMode=").append(this.caseProcessingMode.name()).append('\n');
        return textBlockBuilder.toString();
    }

    /**
     * Parses metadata from a text block produced by {@link #toTextBlock()}.
     *
     * @param formatVersion persisted binary format version
     * @param textBlock     metadata text block
     * @return parsed metadata
     */
    public static TrieMetadata fromTextBlock(final int formatVersion, final String textBlock) {
        Objects.requireNonNull(textBlock, "textBlock");

        final String[] lines = textBlock.split("\\R");
        if (lines.length == 0 || !TEXT_BLOCK_HEADER.equals(lines[0])) {
            throw new IllegalArgumentException("Unsupported metadata block header.");
        }

        final Map<String, String> entries = new HashMap<>();
        for (int index = 1; index < lines.length; index++) {
            final String line = lines[index];
            if (line.isBlank()) {
                continue;
            }
            final int delimiterIndex = line.indexOf('=');
            if (delimiterIndex <= 0 || delimiterIndex == line.length() - 1) {
                throw new IllegalArgumentException("Invalid metadata line: " + line);
            }
            entries.put(line.substring(0, delimiterIndex), line.substring(delimiterIndex + 1));
        }

        final WordTraversalDirection traversalDirection = WordTraversalDirection
                .valueOf(requireEntry(entries, "traversalDirection"));
        final ReductionMode reductionMode = ReductionMode.valueOf(requireEntry(entries, "reductionMode"));
        final int dominantWinnerMinPercent = Integer.parseInt(requireEntry(entries, "dominantWinnerMinPercent"));
        final int dominantWinnerOverSecondRatio = Integer // NOPMD
                .parseInt(requireEntry(entries, "dominantWinnerOverSecondRatio"));
        final DiacriticProcessingMode diacriticProcessingMode = DiacriticProcessingMode
                .valueOf(requireEntry(entries, "diacriticProcessingMode"));
        final CaseProcessingMode caseProcessingMode = CaseProcessingMode
                .valueOf(requireEntry(entries, "caseProcessingMode"));

        return new TrieMetadata(formatVersion, traversalDirection,
                new ReductionSettings(reductionMode, dominantWinnerMinPercent, dominantWinnerOverSecondRatio),
                diacriticProcessingMode, caseProcessingMode);
    }

    private static String requireEntry(final Map<String, String> entries, final String key) {
        final String value = entries.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing metadata entry: " + key);
        }
        return value;
    }
}
