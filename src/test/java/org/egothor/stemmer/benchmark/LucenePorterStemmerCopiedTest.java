/******************************************************************************
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the generated Lucene Porter stemmer benchmark adapter.
 */
@Tag("benchmark")
@Tag("unit")
@DisplayName("LucenePorterStemmerCopied")
final class LucenePorterStemmerCopiedTest {

    /**
     * Fully qualified benchmark class under test.
     */
    private static final String STEMMER_CLASS = "org.egothor.stemmer.benchmark.LucenePorterStemmerCopied";

    /**
     * Verifies repeated invocations for representative tokens are deterministic.
     */
    @Test
    @DisplayName("should produce stable stems for representative tokens")
    void shouldProduceStableStemsForRepresentativeTokens() throws Exception {
        final Object stemmer = createStemmer();
        final Method stemMethod = stemMethod();

        final String[] tokens = { "running", "caresses", "happiness", "connected", "dancing", "" };
        for (String token : tokens) {
            final String first = (String) stemMethod.invoke(stemmer, token);
            final String second = (String) stemMethod.invoke(stemmer, token);
            assertEquals(first, second);
            assertNotNull(first);
        }
    }

    /**
     * Verifies short tokens are accepted and remain non-null.
     */
    @Test
    @DisplayName("should return non-null stems for empty and short tokens")
    void shouldReturnNonNullStemsForEmptyAndShortTokens() throws Exception {
        final Object stemmer = createStemmer();
        final Method stemMethod = stemMethod();

        assertEquals("", stemMethod.invoke(stemmer, ""));
        assertEquals("a", stemMethod.invoke(stemmer, "a"));
        assertEquals("go", stemMethod.invoke(stemmer, "go"));
    }

    /**
     * Verifies mutable reuse on a single benchmark instance.
     */
    @Test
    @DisplayName("should preserve state across many repeated calls on one instance")
    void shouldPreserveStateAcrossManyRepeatedCallsOnOneInstance() throws Exception {
        final Object stemmer = createStemmer();
        final Method stemMethod = stemMethod();

        final String first = (String) stemMethod.invoke(stemmer, "connected");
        for (int index = 0; index < 64; index++) {
            assertEquals(first, stemMethod.invoke(stemmer, "connected"));
        }
    }

    /**
     * Instantiates the benchmark copied Lucene Porter class if it is present.
     *
     * @return benchmark stemmer instance
     * @throws Exception when reflective creation fails
     */
    private Object createStemmer() throws Exception {
        try {
            return Class.forName(STEMMER_CLASS).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException exception) {
            Assumptions.assumeTrue(false, "Benchmark Lucene Porter class is available only when JMH sources are compiled.");
            throw exception;
        }
    }

    /**
     * Resolves the benchmark stem method.
     *
     * @return `stem` reflection handle
     * @throws Exception when reflective lookup fails
     */
    private Method stemMethod() throws Exception {
        return Class.forName(STEMMER_CLASS).getDeclaredMethod("stem", String.class);
    }
}
