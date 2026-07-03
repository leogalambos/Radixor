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
 * LIABLE FOR ANY DIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package org.egothor.stemmer.benchmark;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests dictionary-derived benchmark corpus construction.
 */
@Tag("benchmark")
@Tag("unit")
@DisplayName("LanguageBenchmarkCorpus")
final class LanguageBenchmarkCorpusTest {

    /**
     * Fully qualified corpus helper class name.
     */
    private static final String CORPUS_CLASS = "org.egothor.stemmer.benchmark.LanguageBenchmarkCorpus";

    /**
     * Verifies large resources use the full dictionary-derived token sequence.
     *
     * @throws Exception if reflection or resource loading fails
     */
    @Test
    @DisplayName("should use full dictionary corpus when resource is larger than the timing minimum")
    void shouldUseFullDictionaryCorpusWhenResourceIsLargerThanTimingMinimum() throws Exception {
        final Object fullCorpus = invokeCorpus("createFullCorpus", StemmerPatchTrieLoader.Language.US_UK);
        final Object timingCorpus = invokeCorpus("createCorpus", StemmerPatchTrieLoader.Language.US_UK);

        assertTrue(tokens(fullCorpus).length > minimumTimingTokenCount());
        assertEquals(tokens(fullCorpus).length, tokens(timingCorpus).length);
        assertArrayEquals(tokens(fullCorpus), tokens(timingCorpus));
        assertArrayEquals(expectedRoots(fullCorpus), expectedRoots(timingCorpus));
    }

    /**
     * Verifies small resources repeat deterministically to the minimum timing size.
     *
     * @throws Exception if reflection or resource loading fails
     */
    @Test
    @DisplayName("should repeat small dictionary corpus to timing minimum")
    void shouldRepeatSmallDictionaryCorpusToTimingMinimum() throws Exception {
        final Object fullCorpus = invokeCorpus("createFullCorpus", StemmerPatchTrieLoader.Language.FA_IR);
        final Object timingCorpus = invokeCorpus("createCorpus", StemmerPatchTrieLoader.Language.FA_IR);

        assertTrue(tokens(fullCorpus).length < minimumTimingTokenCount());
        assertEquals(minimumTimingTokenCount(), tokens(timingCorpus).length);
        assertEquals(tokens(fullCorpus)[0], tokens(timingCorpus)[0]);
        assertEquals(expectedRoots(fullCorpus)[0], expectedRoots(timingCorpus)[0]);
        assertEquals(tokens(fullCorpus)[0], tokens(timingCorpus)[tokens(fullCorpus).length]);
        assertEquals(expectedRoots(fullCorpus)[0], expectedRoots(timingCorpus)[tokens(fullCorpus).length]);
    }

    /**
     * Verifies corpus token and expected-root arrays stay aligned.
     *
     * @throws Exception if reflection or resource loading fails
     */
    @Test
    @DisplayName("should keep token and expected-root arrays aligned")
    void shouldKeepTokenAndExpectedRootArraysAligned() throws Exception {
        final Object corpus = invokeCorpus("createFullCorpus", StemmerPatchTrieLoader.Language.PL_PL);

        assertEquals(tokens(corpus).length, expectedRoots(corpus).length);
        assertTrue(tokens(corpus).length > minimumTimingTokenCount());
        assertTrue(expectedRoots(corpus)[0].length() > 0);
    }

    /**
     * Verifies changed-token timing corpora exclude entries already equal to the
     * expected root.
     *
     * @throws Exception if reflection or resource loading fails
     */
    @Test
    @DisplayName("should create changed-token timing corpus")
    void shouldCreateChangedTokenTimingCorpus() throws Exception {
        final Object corpus = invokeCorpus("createChangedCorpus", StemmerPatchTrieLoader.Language.US_UK);
        final String[] corpusTokens = tokens(corpus);
        final String[] corpusExpectedRoots = expectedRoots(corpus);

        assertTrue(corpusTokens.length > minimumTimingTokenCount());
        for (int index = 0; index < corpusTokens.length; index++) {
            assertTrue(!corpusTokens[index].equals(corpusExpectedRoots[index]),
                    "Changed-token corpus must contain only token/root pairs where token differs from root.");
        }
    }

    /**
     * Verifies benchmark corpora are generated once per language and reused from
     * memory.
     *
     * @throws Exception if reflection or resource loading fails
     */
    @Test
    @DisplayName("should reuse cached corpus instances")
    void shouldReuseCachedCorpusInstances() throws Exception {
        final Object firstTimingCorpus = invokeCorpus("createCorpus", StemmerPatchTrieLoader.Language.US_UK);
        final Object secondTimingCorpus = invokeCorpus("createCorpus", StemmerPatchTrieLoader.Language.US_UK);
        final Object firstFullCorpus = invokeCorpus("createFullCorpus", StemmerPatchTrieLoader.Language.US_UK);
        final Object secondFullCorpus = invokeCorpus("createFullCorpus", StemmerPatchTrieLoader.Language.US_UK);
        final Object firstChangedCorpus = invokeCorpus("createChangedCorpus", StemmerPatchTrieLoader.Language.US_UK);
        final Object secondChangedCorpus = invokeCorpus("createChangedCorpus", StemmerPatchTrieLoader.Language.US_UK);

        assertSame(firstTimingCorpus, secondTimingCorpus);
        assertSame(firstFullCorpus, secondFullCorpus);
        assertSame(firstChangedCorpus, secondChangedCorpus);
    }

    /**
     * Invokes a static corpus factory.
     *
     * @param methodName factory method name
     * @param language Radixor language
     * @return corpus record instance
     * @throws Exception if reflection fails
     */
    private Object invokeCorpus(final String methodName, final StemmerPatchTrieLoader.Language language)
            throws Exception {
        final Class<?> type = corpusType();
        final Method method = type.getDeclaredMethod(methodName, StemmerPatchTrieLoader.Language.class);
        method.setAccessible(true);
        return method.invoke(null, language);
    }

    /**
     * Reads corpus tokens.
     *
     * @param corpus corpus record instance
     * @return token array
     * @throws Exception if reflection fails
     */
    private String[] tokens(final Object corpus) throws Exception {
        return stringArray(corpus, "tokens");
    }

    /**
     * Reads corpus expected roots.
     *
     * @param corpus corpus record instance
     * @return expected-root array
     * @throws Exception if reflection fails
     */
    private String[] expectedRoots(final Object corpus) throws Exception {
        return stringArray(corpus, "expectedRoots");
    }

    /**
     * Reads a string-array record component.
     *
     * @param corpus corpus record instance
     * @param methodName accessor name
     * @return string array
     * @throws Exception if reflection fails
     */
    private String[] stringArray(final Object corpus, final String methodName) throws Exception {
        final Method method = corpus.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (String[]) method.invoke(corpus);
    }

    /**
     * Reads the minimum timing token count constant.
     *
     * @return minimum timing token count
     * @throws Exception if reflection fails
     */
    private int minimumTimingTokenCount() throws Exception {
        final java.lang.reflect.Field field = corpusType().getDeclaredField("MINIMUM_TIMING_TOKEN_COUNT");
        field.setAccessible(true);
        return ((Number) field.get(null)).intValue();
    }

    /**
     * Resolves the benchmark corpus helper class.
     *
     * @return corpus helper type
     * @throws Exception if class loading fails
     */
    private Class<?> corpusType() throws Exception {
        try {
            return Class.forName(CORPUS_CLASS);
        } catch (ClassNotFoundException exception) {
            Assumptions.assumeTrue(false, "Language benchmark corpus is available only when JMH sources are compiled.");
            throw exception;
        }
    }
}
