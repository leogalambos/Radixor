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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the benchmark Paice/Husk Lancaster implementation.
 */
@Tag("benchmark")
@Tag("unit")
@DisplayName("PaiceHuskLancasterStemmer")
final class PaiceHuskLancasterStemmerTest {

    /**
     * Benchmark class under test.
     */
    private static final String STEMMER_CLASS = "org.egothor.stemmer.benchmark.PaiceHuskLancasterStemmer";

    /**
     * Expected outputs used to verify deterministic stems for this benchmark adapter.
     */
    private static final String[][] SAMPLE_STEMS = {
            { "running", "run" },
            { "caresses", "caress" },
            { "happiness", "happy" },
            { "connected", "connect" },
            { "dancing", "dant" },
            { "happy", "happy" }
    };

    /**
     * Verifies selected representative words produce stable stems.
     */
    @Test
    @DisplayName("should produce stable benchmark stems for representative words")
    void shouldProduceStableStemsForRepresentativeWords() throws Exception {
        final Object stemmer = createStemmer();
        final Method stemMethod = stemMethod();

        for (String[] sample : SAMPLE_STEMS) {
            assertEquals(sample[1], stemMethod.invoke(stemmer, sample[0]));
        }
    }

    /**
     * Verifies short and empty inputs remain stable and non-null.
     */
    @Test
    @DisplayName("should handle empty and short tokens without null output")
    void shouldHandleEmptyAndShortTokensWithoutNullOutput() throws Exception {
        final Object stemmer = createStemmer();
        final Method stemMethod = stemMethod();

        assertEquals("", stemMethod.invoke(stemmer, ""));
        assertEquals("a", stemMethod.invoke(stemmer, "a"));
        assertEquals("x", stemMethod.invoke(stemmer, "x"));
    }

    /**
     * Verifies that one mutable instance can be reused.
     */
    @Test
    @DisplayName("should be reusable across many repeated calls")
    void shouldBeReusableAcrossRepeatedCalls() throws Exception {
        final Object stemmer = createStemmer();
        final Method stemMethod = stemMethod();

        final String first = (String) stemMethod.invoke(stemmer, "running");
        final String second = (String) stemMethod.invoke(stemmer, "running");
        assertEquals(first, second);
    }

    /**
     * Verifies null input handling for normal integration path expectations.
     */
    @Test
    @DisplayName("should return null only for null inputs")
    void shouldReturnNullOnlyForNullInputs() throws Exception {
        final Object stemmer = createStemmer();
        final Method stemMethod = stemMethod();

        assertEquals("run", stemMethod.invoke(stemmer, "running"));
        assertEquals(null, stemMethod.invoke(stemmer, new Object[] { null }));
        assertNotNull(stemMethod.invoke(stemmer, "connected"));
    }

    /**
     * Creates a benchmark stemmer instance.
     *
     * @return benchmark stemmer instance
     * @throws Exception when reflection fails
     */
    private Object createStemmer() throws Exception {
        try {
            return Class.forName(STEMMER_CLASS).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException exception) {
            Assumptions.assumeTrue(false, "Benchmark Paice/Husk stemmer is available only when JMH sources are compiled.");
            throw exception;
        }
    }

    /**
     * Resolves the stem method for benchmark execution.
     *
     * @return stem method
     * @throws Exception when lookup fails
     */
    private Method stemMethod() throws Exception {
        return Class.forName(STEMMER_CLASS).getDeclaredMethod("stem", String.class);
    }
}
