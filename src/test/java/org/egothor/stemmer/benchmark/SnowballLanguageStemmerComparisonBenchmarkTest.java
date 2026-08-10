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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Param;

/**
 * Verifies that direct-only Snowball 3.1.0 algorithms cannot enter the Lucene
 * SnowballFilter benchmark domain.
 */
final class SnowballLanguageStemmerComparisonBenchmarkTest {

    /**
     * Verifies the direct and Lucene parameter domains independently.
     *
     * @throws ReflectiveOperationException if the benchmark state contract changes
     */
    @Test
    void directAndLuceneParameterDomainsRemainExplicit() throws ReflectiveOperationException {
        final Set<String> directCases = parameterValues(
                SnowballLanguageStemmerComparisonBenchmark.DirectSharedState.class);
        final Set<String> luceneCases = parameterValues(
                SnowballLanguageStemmerComparisonBenchmark.SharedState.class);
        final Set<String> registeredCases = Arrays.stream(SnowballLanguageCase.values())
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(registeredCases, directCases);
        assertEquals(17, directCases.size());
        assertEquals(14, luceneCases.size());
        assertEquals(Set.of("CZECH", "PERSIAN", "POLISH"), difference(directCases, luceneCases));

        for (String luceneCase : luceneCases) {
            SnowballLanguageCase.valueOf(luceneCase).luceneSnowballName();
        }
        for (String directOnlyCase : difference(directCases, luceneCases)) {
            assertThrows(IllegalStateException.class,
                    () -> SnowballLanguageCase.valueOf(directOnlyCase).luceneSnowballName());
        }
    }

    /**
     * Reads the declared JMH parameter values from a benchmark state.
     *
     * @param stateClass benchmark state class
     * @return immutable parameter-value set
     * @throws NoSuchFieldException if the state no longer declares the parameter
     */
    private static Set<String> parameterValues(final Class<?> stateClass) throws NoSuchFieldException {
        final Field field = stateClass.getField("languageCaseName");
        return Set.of(field.getAnnotation(Param.class).value());
    }

    /**
     * Returns the values present in {@code left} but absent from {@code right}.
     *
     * @param left  source set
     * @param right excluded set
     * @return immutable set difference
     */
    private static Set<String> difference(final Set<String> left, final Set<String> right) {
        return left.stream().filter(value -> !right.contains(value)).collect(Collectors.toUnmodifiableSet());
    }
}
