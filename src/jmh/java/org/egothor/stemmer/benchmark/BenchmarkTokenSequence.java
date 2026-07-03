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

import java.util.Objects;

/**
 * Reusable deterministic token sequence for benchmark-only token streams.
 *
 * <p>
 * The sequence keeps stable token ordering and offset progression while avoiding
 * per-token object creation during iteration.
 * </p>
 */
final class BenchmarkTokenSequence {

    /**
     * Shared backing corpus as character arrays.
     */
    private char[][] tokenCharacters;

    /**
     * Number of active tokens in the sequence.
     */
    private int tokenCount;

    /**
     * Cursor index for the currently emitted token.
     */
    private int cursor;

    /**
     * Current token character array.
     */
    private char[] currentToken;

    /**
     * Start offset of the current token.
     */
    private int currentStartOffset;

    /**
     * End offset of the current token.
     */
    private int currentEndOffset;

    /**
     * Offset of the next token start.
     */
    private int nextOffset;

    /**
     * Creates a reusable token sequence.
 *
     * @param tokens token corpus source
     */
    BenchmarkTokenSequence(final String[] tokens) {
        setTokens(tokens);
    }

    /**
     * Sets a new token corpus for this sequence.
     *
     * <p>
     * The sequence stores copied character arrays so token reads can be reused
     * without creating per-token objects during benchmark iteration.
     * </p>
     *
     * @param tokens new token corpus
     */
    void setTokens(final String[] tokens) {
        Objects.requireNonNull(tokens, "tokens");
        this.tokenCharacters = new char[tokens.length][];
        for (int index = 0; index < tokens.length; index++) {
            final String token = Objects.requireNonNull(tokens[index], "tokens[" + index + "]");
            this.tokenCharacters[index] = token.toCharArray();
        }

        this.tokenCount = this.tokenCharacters.length;
        reset();
    }

    /**
     * Resets stream position for reuse.
     */
    void reset() {
        this.cursor = 0;
        this.nextOffset = 0;
        this.currentStartOffset = 0;
        this.currentEndOffset = 0;
        this.currentToken = null;
    }

    /**
     * Returns whether at least one token remains in the sequence.
     *
     * @return true if a token can be emitted
     */
    boolean hasNext() {
        return this.cursor < this.tokenCount;
    }

    /**
     * Advances to the next token.
     *
     * @return true if a token was emitted
     */
    boolean advance() {
        if (!hasNext()) {
            return false;
        }

        final char[] token = this.tokenCharacters[this.cursor];
        this.currentToken = token;
        this.currentStartOffset = this.nextOffset;
        this.currentEndOffset = this.currentStartOffset + token.length;
        this.nextOffset = this.currentEndOffset + 1;
        this.cursor++;
        return true;
    }

    /**
     * Returns the current token in the sequence.
     *
     * @return current token character array
     */
    char[] currentToken() {
        return this.currentToken;
    }

    /**
     * Returns current token start offset for token stream attributes.
     *
     * @return start offset
     */
    int currentStartOffset() {
        return this.currentStartOffset;
    }

    /**
     * Returns current token end offset for token stream attributes.
 *
     * @return end offset
     */
    int currentEndOffset() {
        return this.currentEndOffset;
    }

    /**
     * Returns final stream offset value used by {@code end()}.
     *
     * @return final offset
     */
    int endOffset() {
        return this.nextOffset > 0 ? this.nextOffset - 1 : 0;
    }
}
