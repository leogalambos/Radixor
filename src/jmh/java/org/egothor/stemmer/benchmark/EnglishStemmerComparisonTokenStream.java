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

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * Reusable token stream driven by a deterministic token corpus.
 *
 * <p>
 * The stream emits each token from a shared array and supports repeated
 * {@link #reset()} + {@link #incrementToken()} cycles without per-token
 * object allocation.
 * </p>
 */
final class EnglishStemmerComparisonTokenStream extends TokenStream {

    /**
     * Current token text.
     */
    private final CharTermAttribute charTermAttribute;

    /**
     * Token offsets for benchmark stream compliance.
     */
    private final OffsetAttribute offsetAttribute;

    /**
     * Position increment attribute for benchmark stream compliance.
     */
    private final PositionIncrementAttribute positionIncrementAttribute;

    /**
     * Reusable token source.
     */
    private final BenchmarkTokenSequence tokenSequence;

    /**
     * Creates a deterministic token stream for benchmark reuse.
     *
     * @param tokens tokens emitted by the stream
     */
    EnglishStemmerComparisonTokenStream(final String[] tokens) {
        this.tokenSequence = new BenchmarkTokenSequence(tokens);
        this.charTermAttribute = addAttribute(CharTermAttribute.class);
        this.offsetAttribute = addAttribute(OffsetAttribute.class);
        this.positionIncrementAttribute = addAttribute(PositionIncrementAttribute.class);
    }

    /**
     * Replaces the token corpus for this stream.
     *
     * @param tokens new token corpus
     */
    void setTokens(final String[] tokens) {
        this.tokenSequence.setTokens(tokens);
    }

    /**
     * Returns whether the stream is drained and ready to be exhausted.
     *
     * @return true if all configured tokens were consumed
     */
    boolean isDrained() {
        return !this.tokenSequence.hasNext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean incrementToken() throws IOException {
        if (!this.tokenSequence.advance()) {
            return false;
        }

        clearAttributes();
        final char[] token = this.tokenSequence.currentToken();
        this.charTermAttribute.copyBuffer(token, 0, token.length);
        this.positionIncrementAttribute.setPositionIncrement(1);
        this.offsetAttribute.setOffset(this.tokenSequence.currentStartOffset(), this.tokenSequence.currentEndOffset());
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() throws IOException {
        super.reset();
        this.tokenSequence.reset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void end() throws IOException {
        super.end();
        final int endOffset = this.tokenSequence.endOffset();
        this.offsetAttribute.setOffset(endOffset, endOffset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        super.close();
        this.charTermAttribute.setEmpty();
    }
}
