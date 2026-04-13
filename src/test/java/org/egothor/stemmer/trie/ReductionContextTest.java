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