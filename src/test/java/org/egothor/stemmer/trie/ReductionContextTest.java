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
package org.egothor.stemmer.trie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.LinkedHashMap;
import java.util.Map;

import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReductionContext}.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("ReductionContext")
class ReductionContextTest {

    @Test
    @DisplayName("must expose settings and manage canonical node registry")
    void shouldExposeSettingsAndManageCanonicalNodeRegistry() {
        final ReductionSettings settings = ReductionSettings
                .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);

        final ReductionContext<String> context = new ReductionContext<>(settings);
        final ReductionSignature<String> signature = createLeafSignature("stem");
        final ReducedNode<String> node = new ReducedNode<>(signature, new LinkedHashMap<>(Map.of("stem", 1)),
                new LinkedHashMap<>());

        assertSame(settings, context.settings());
        assertEquals(0, context.canonicalNodeCount());
        assertNull(context.lookup(signature));

        context.register(signature, node);

        assertEquals(1, context.canonicalNodeCount());
        assertSame(node, context.lookup(signature));
    }

    @Test
    @DisplayName("register must replace previous canonical node for the same signature")
    void shouldReplacePreviousCanonicalNodeForTheSameSignature() {
        final ReductionContext<String> context = new ReductionContext<>(
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));

        final ReductionSignature<String> signature = createLeafSignature("stem");
        final ReducedNode<String> first = new ReducedNode<>(signature, new LinkedHashMap<>(Map.of("first", 1)),
                new LinkedHashMap<>());
        final ReducedNode<String> second = new ReducedNode<>(signature, new LinkedHashMap<>(Map.of("second", 1)),
                new LinkedHashMap<>());

        context.register(signature, first);
        context.register(signature, second);

        assertEquals(1, context.canonicalNodeCount());
        assertSame(second, context.lookup(signature));
    }

    private static ReductionSignature<String> createLeafSignature(final String value) {
        final LocalValueSummary<String> summary = new LocalValueSummary<>(new String[] { value }, new int[] { 1 }, 1,
                value, 1, 0);

        return ReductionSignature.create(summary, Map.of(),
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
    }
}
