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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Reflection-based tests for the Lucene-dependent benchmark token stream.
 */
@Tag("benchmark")
@Tag("unit")
@DisplayName("EnglishStemmerComparisonTokenStream")
final class EnglishStemmerComparisonTokenStreamTest {

    /**
     * Fully qualified token stream class name.
     */
    private static final String TOKEN_STREAM_CLASS = "org.egothor.stemmer.benchmark.EnglishStemmerComparisonTokenStream";

    /**
     * Verifies stream reuse and reset behavior through repeated iteration.
     *
     * @throws Exception when reflection calls fail
     */
    @Test
    @DisplayName("should reuse token stream without losing order or count")
    void shouldReuseTokenStreamWithoutLosingOrderOrCount() throws Exception {
        final Object stream = createStream(new String[] { "running", "caresses", "happiness" });
        final Class<?> type = streamType();
        final Method increment = method(type, "incrementToken");
        final Method reset = method(type, "reset");
        final Method isDrained = method(type, "isDrained");
        final Method setTokens = method(type, "setTokens", String[].class);

        int count = consume(increment, stream);
        assertEquals(3, count);
        assertEquals(Boolean.TRUE, isDrained.invoke(stream));

        reset.invoke(stream);
        assertEquals(3, consume(increment, stream));

        setTokens.invoke(stream, (Object) new String[] { "single" });
        reset.invoke(stream);
        assertEquals(1, consume(increment, stream));
    }

    /**
     * Verifies empty stream handling, end-of-stream, and reset behavior.
     *
     * @throws Exception when reflection calls fail
     */
    @Test
    @DisplayName("should handle empty corpus with immediate drain")
    void shouldHandleEmptyCorpusWithImmediateDrain() throws Exception {
        final Object stream = createStream(new String[0]);
        final Class<?> type = streamType();
        final Method increment = method(type, "incrementToken");
        final Method isDrained = method(type, "isDrained");
        final Method reset = method(type, "reset");

        assertFalse((Boolean) increment.invoke(stream));
        assertEquals(Boolean.TRUE, isDrained.invoke(stream));

        reset.invoke(stream);
        assertFalse((Boolean) increment.invoke(stream));
    }

    /**
     * Verifies the last emitted token is stable across repeated passes.
     *
     * @throws Exception when reflection calls fail
     */
    @Test
    @DisplayName("should expose stable terminal token text")
    void shouldExposeStableTerminalTokenText() throws Exception {
        final Object stream = createStream(new String[] { "caresses", "running", "connected" });
        final Class<?> type = streamType();
        final Class<?> charTermClass = Class.forName("org.apache.lucene.analysis.tokenattributes.CharTermAttribute");
        final Method increment = method(type, "incrementToken");
        final Method getAttribute = method(type, "getAttribute", false, Class.class);
        final Method end = method(type, "end");
        final Method close = method(type, "close");
        final Object termAttribute = getAttribute.invoke(stream, charTermClass);

        String lastToken = null;
        while ((Boolean) increment.invoke(stream)) {
            lastToken = termAttribute.toString();
        }
        end.invoke(stream);
        close.invoke(stream);
        assertEquals("connected", lastToken);
    }

    /**
     * Resolves and instantiate the benchmark token stream class when available.
     *
     * @param tokens input tokens
     * @return created stream
     * @throws Exception when class or constructor fails
     */
    private Object createStream(final String[] tokens) throws Exception {
        final Class<?> type = streamType();
        final Constructor<?> constructor = type.getDeclaredConstructor(String[].class);
        constructor.setAccessible(true);
        return constructor.newInstance((Object) tokens);
    }

    /**
     * Resolves the benchmark token stream class.
     *
     * @return stream class
     * @throws Exception when class loading fails
     */
    private Class<?> streamType() throws Exception {
        try {
            return Class.forName(TOKEN_STREAM_CLASS);
        } catch (ClassNotFoundException exception) {
            Assumptions.assumeTrue(false, "Token stream class is available only when JMH sources are compiled.");
            throw exception;
        }
    }

    /**
     * Resolves a method for invocation.
     *
     * @param type      target class
     * @param name      method name
     * @param arguments argument types
     * @return reflected method
     * @throws NoSuchMethodException when method is missing
     */
    private Method method(final Class<?> type, final String name, final Class<?>... arguments) throws NoSuchMethodException {
        return method(type, name, true, arguments);
    }

    /**
     * Resolves a method for invocation.
     *
     * @param type      target class
     * @param name      method name
     * @param declared  whether to require declaration in the target class
     * @param arguments argument types
     * @return reflected method
     * @throws NoSuchMethodException when method is missing
     */
    private Method method(final Class<?> type, final String name, final boolean declared, final Class<?>... arguments)
            throws NoSuchMethodException {
        final Method method = declared ? type.getDeclaredMethod(name, arguments) : type.getMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }

    /**
     * Consumes stream and counts tokens.
     *
     * @param increment increment method
     * @param stream    stream object
     * @return tokens emitted
     * @throws Exception on reflection failure
     */
    private int consume(final Method increment, final Object stream) throws Exception {
        int count = 0;
        while ((Boolean) increment.invoke(stream)) {
            count++;
        }
        return count;
    }

    /**
     * Confirms invocation paths fail fast for invalid signatures.
     */
    @Test
    @DisplayName("should enforce method contract on reflection")
    void shouldEnforceMethodContractOnReflection() {
        Assumptions.assumeTrue(streamTypeAvailable(), "Token stream class is available only when JMH sources are compiled.");
        assertThrows(NoSuchMethodException.class, () -> streamType().getDeclaredMethod("nonExistentMethod"));
    }

    /**
     * Checks whether the benchmark token stream class can be loaded.
     *
     * @return true when class is available
     */
    private boolean streamTypeAvailable() {
        try {
            Class.forName(TOKEN_STREAM_CLASS);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
