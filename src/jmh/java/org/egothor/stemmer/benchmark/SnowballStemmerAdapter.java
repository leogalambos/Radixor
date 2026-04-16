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

import org.tartarus.snowball.SnowballStemmer;

/**
 * Small adapter around a Snowball stemmer instance used by benchmarks.
 *
 * <p>
 * The adapter keeps the benchmark code focused on the actual workload while
 * still allowing a professional separation between benchmark orchestration and
 * third-party stemming API calls.
 * </p>
 */
final class SnowballStemmerAdapter {

    /**
     * Factory of Snowball stemmer instances.
     */
    @FunctionalInterface
    interface Factory {

        /**
         * Creates a new Snowball stemmer instance.
         *
         * @return new Snowball stemmer
         */
        SnowballStemmer create();
    }

    /**
     * Reusable Snowball stemmer instance.
     */
    private final SnowballStemmer stemmer;

    /**
     * Creates a new adapter.
     *
     * @param factory factory creating the concrete Snowball stemmer
     */
    SnowballStemmerAdapter(final Factory factory) {
        this.stemmer = Objects.requireNonNull(factory, "factory").create();
    }

    /**
     * Applies stemming to the supplied token.
     *
     * @param token input token
     * @return produced stem
     */
    String stem(final String token) {
        this.stemmer.setCurrent(token);
        this.stemmer.stem();
        return this.stemmer.getCurrent();
    }
}
