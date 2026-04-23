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
 * Defines the logical direction in which word characters are traversed.
 *
 * <p>
 * The same direction is used consistently in two places:
 * </p>
 * <ul>
 * <li>when a word key is traversed through a trie</li>
 * <li>when patch commands are serialized and then applied back to a source
 * word</li>
 * </ul>
 *
 * <p>
 * {@link #FORWARD} means that processing starts at the logical beginning of the
 * stored form and moves toward its end. {@link #BACKWARD} means that processing
 * starts at the logical end of the stored form and moves toward its beginning.
 * </p>
 *
 * <p>
 * For traditional suffix-oriented Egothor data, {@link #BACKWARD} matches the
 * historical behavior. For right-to-left languages whose affix logic should
 * operate on the stored form as written, {@link #FORWARD} can be used so that
 * neither trie construction nor patch application needs to reverse words
 * externally.
 * </p>
 */
public enum WordTraversalDirection {

    /**
     * Traverses a word from its logical beginning toward its logical end.
     */
    FORWARD,

    /**
     * Traverses a word from its logical end toward its logical beginning.
     */
    BACKWARD;

    /**
     * Returns the traversal start index for a character sequence of the supplied
     * length.
     *
     * @param length sequence length
     * @return start index, or {@code -1} when the sequence is empty and traversal
     *         should therefore not begin
     * @throws IllegalArgumentException if {@code length} is negative
     */
    public int startIndex(final int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative.");
        }
        if (length == 0) {
            return -1;
        }
        return this == FORWARD ? 0 : length - 1;
    }

    /**
     * Returns the logical character index addressed by the supplied traversal
     * offset.
     *
     * <p>
     * A traversal offset of {@code 0} addresses the first character seen in this
     * direction, {@code 1} the second character, and so on.
     * </p>
     *
     * @param length          sequence length
     * @param traversalOffset zero-based offset from the traversal start
     * @return corresponding logical character index
     * @throws IllegalArgumentException if any argument is outside the valid range
     */
    public int logicalIndex(final int length, final int traversalOffset) {
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative.");
        }
        if (traversalOffset < 0 || traversalOffset >= length) {
            throw new IllegalArgumentException("traversalOffset is outside the valid range.");
        }
        return this == FORWARD ? traversalOffset : length - 1 - traversalOffset;
    }

    /**
     * Returns the characters of the supplied word in this traversal order.
     *
     * @param word source word
     * @return traversal-ordered characters
     * @throws NullPointerException if {@code word} is {@code null}
     */
    public char[] toTraversalCharacters(final String word) {
        Objects.requireNonNull(word, "word");
        final char[] characters = word.toCharArray();
        if (this == FORWARD) {
            return characters;
        }

        for (int left = 0, right = characters.length - 1; left < right; left++, right--) { // NOPMD
            final char swap = characters[left];
            characters[left] = characters[right];
            characters[right] = swap;
        }
        return characters;
    }

    /**
     * Converts a path represented in traversal order back to the logical key form.
     *
     * @param traversalPath key path in traversal order
     * @return logical key form
     * @throws NullPointerException if {@code traversalPath} is {@code null}
     */
    public String traversalPathToLogicalKey(final CharSequence traversalPath) {
        Objects.requireNonNull(traversalPath, "traversalPath");
        if (this == FORWARD) {
            return traversalPath.toString();
        }
        return new StringBuilder(traversalPath).reverse().toString();
    }
}
