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

/**
 * Defines how dictionary loading and trie traversal should treat diacritics.
 *
 * <p>
 * The selected mode is applied independently from other normalization modes
 * (for example {@link CaseProcessingMode}). This means case normalization and
 * diacritic normalization can be combined freely and each keeps its own
 * semantics.
 * </p>
 */
public enum DiacriticProcessingMode {

    /**
     * Preserves dictionary entries and lookup keys exactly as provided.
     */
    AS_IS,

    /**
     * Removes diacritics from dictionary entries before trie construction and
     * removes diacritics from lookup keys before traversal.
     */
    REMOVE,

    /**
     * Planned dual-path mode where lookup may continue along both the original
     * diacritic edge and a normalized non-diacritic alternative.
     *
     * <p>
     * This mode is currently not supported and using it triggers
     * {@link UnsupportedOperationException}.
     * </p>
     */
    AS_IS_AND_STRIPPED_FALLBACK
}
