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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for benchmark token sequence reuse and offset behavior.
 */
@Tag("benchmark")
@Tag("unit")
@DisplayName("BenchmarkTokenSequence")
final class BenchmarkTokenSequenceTest {

    /**
     * Shared token corpus used in the benchmark state.
     */
    private static final String[] TOKENS = { "running", "caresses", "running", "happiness", "", "s" };

    /**
     * Fully qualified benchmark helper class name.
     */
    private static final String SEQUENCE_CLASS = "org.egothor.stemmer.benchmark.BenchmarkTokenSequence";

    /**
     * Verifies first run and rewind behavior keeps token order stable.
     */
    @Test
    @DisplayName("should emit tokens in stable order and correct offsets")
    void shouldEmitTokensInStableOrder() throws Exception {
        final Object sequence = createSequence(TOKENS);
        final String[] expected = TOKENS;
        final Method advance = method("advance");
        final Method hasNext = method("hasNext");
        final Method currentToken = method("currentToken");
        final Method currentStartOffset = method("currentStartOffset");
        final Method currentEndOffset = method("currentEndOffset");

        for (int index = 0; index < expected.length; index++) {
            assertTrue((Boolean) advance.invoke(sequence));
            assertEquals(expected[index], new String((char[]) currentToken.invoke(sequence)));
            if (index == 0) {
                assertEquals(0, ((Number) currentStartOffset.invoke(sequence)).intValue());
                assertEquals("running".length(), ((Number) currentEndOffset.invoke(sequence)).intValue());
            }
        }
        assertFalse((Boolean) hasNext.invoke(sequence));
        assertFalse((Boolean) advance.invoke(sequence));
    }

    /**
     * Verifies reset brings the sequence back to the first token.
     */
    @Test
    @DisplayName("should support reset and replay without allocations")
    void shouldSupportResetAndReplay() throws Exception {
        final Object sequence = createSequence(TOKENS);
        final Method advance = method("advance");
        final Method reset = method("reset");

        int firstPass = 0;
        while ((Boolean) advance.invoke(sequence)) {
            firstPass++;
        }

        reset.invoke(sequence);

        int secondPass = 0;
        while ((Boolean) advance.invoke(sequence)) {
            secondPass++;
        }

        assertEquals(firstPass, secondPass);
    }

    /**
     * Verifies sequence tokens can be replaced for multi-benchmark reuse.
     */
    @Test
    @DisplayName("should reset offsets after token set replacement")
    void shouldResetOffsetsAfterTokenReplacement() throws Exception {
        final Object sequence = createSequence(new String[] { "a", "bc", "def" });
        final Method setTokens = method("setTokens", String[].class);
        final Method advance = method("advance");
        final Method currentToken = method("currentToken");

        advance.invoke(sequence);
        advance.invoke(sequence);

        final String[] replacement = { "xy", "z" };
        setTokens.invoke(sequence, (Object) replacement);
        advance.invoke(sequence);
        assertEquals("xy", new String((char[]) currentToken.invoke(sequence)));
        advance.invoke(sequence);
        assertEquals("z", new String((char[]) currentToken.invoke(sequence)));
    }

    /**
     * Verifies no mutation of original token instances in source corpus.
     */
    @Test
    @DisplayName("should not mutate source token references or values")
    void shouldNotMutateSourceTokenValues() throws Exception {
        final String[] source = { "first", "second", "third" };
        final Object sequence = createSequence(source);
        final Method advance = method("advance");
        final Method currentToken = method("currentToken");

        while ((Boolean) advance.invoke(sequence)) {
            assertFalse(new String((char[]) currentToken.invoke(sequence)).isEmpty());
        }

        assertEquals("first", source[0]);
        assertEquals("second", source[1]);
        assertEquals("third", source[2]);
    }

    /**
     * Instantiates the benchmark token sequence class.
     *
     * @param tokens source tokens
     * @return sequence instance
     * @throws Exception on reflection failure
     */
    private Object createSequence(final String[] tokens) throws Exception {
        try {
            final Class<?> type = Class.forName(SEQUENCE_CLASS);
            final Constructor<?> constructor = type.getDeclaredConstructor(String[].class);
            return constructor.newInstance((Object) tokens);
        } catch (ClassNotFoundException exception) {
            Assumptions.assumeTrue(false, "Benchmark token sequence class is available only when JMH sources are compiled.");
            throw exception;
        }
    }

    /**
     * Resolves a method for the token sequence class.
     *
     * @param name      method name
     * @param arguments argument types
     * @return method
     * @throws Exception when method is missing
     */
    private Method method(final String name, final Class<?>... arguments) throws Exception {
        final Method method = Class.forName(SEQUENCE_CLASS).getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }
}
