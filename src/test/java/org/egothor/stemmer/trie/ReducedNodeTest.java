package org.egothor.stemmer.trie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReducedNode}.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("ReducedNode")
class ReducedNodeTest {

    @Test
    @DisplayName("constructor must defensively copy input maps")
    void shouldDefensivelyCopyInputMaps() {
        final ReductionSignature<String> signature = createLeafSignature("root");

        final Map<String, Integer> localCounts = new LinkedHashMap<>();
        localCounts.put("a", 1);

        final Map<Character, ReducedNode<String>> children = new LinkedHashMap<>();

        final ReducedNode<String> node = new ReducedNode<>(signature, localCounts, children);

        localCounts.put("b", 2);
        children.put('x', createReducedLeaf("child"));

        assertEquals(Map.of("a", 1), node.localCounts());
        assertTrue(node.children().isEmpty());
    }

    @Test
    @DisplayName("localCounts must expose internal backing map")
    void shouldExposeInternalBackingLocalCountsMap() {
        final ReducedNode<String> node = createReducedLeaf("root");

        final Map<String, Integer> localCounts = node.localCounts();
        localCounts.put("other", 7);

        assertSame(localCounts, node.localCounts());
        assertEquals(Integer.valueOf(7), node.localCounts().get("other"));
    }

    @Test
    @DisplayName("children must expose internal backing map")
    void shouldExposeInternalBackingChildrenMap() {
        final ReducedNode<String> node = createReducedLeaf("root");
        final ReducedNode<String> child = createReducedLeaf("child");

        final Map<Character, ReducedNode<String>> children = node.children();
        children.put('c', child);

        assertSame(children, node.children());
        assertSame(child, node.children().get('c'));
    }

    @Test
    @DisplayName("mergeLocalCounts must sum existing counts and append missing values")
    void shouldMergeLocalCountsBySummingAndAppending() {
        final ReducedNode<String> node = new ReducedNode<>(createLeafSignature("root"),
                new LinkedHashMap<>(Map.of("a", 2)), Map.of());

        final Map<String, Integer> additionalCounts = new LinkedHashMap<>();
        additionalCounts.put("a", 5);
        additionalCounts.put("b", 3);

        node.mergeLocalCounts(additionalCounts);

        assertEquals(Integer.valueOf(7), node.localCounts().get("a"));
        assertEquals(Integer.valueOf(3), node.localCounts().get("b"));
        assertEquals(2, node.localCounts().size());
    }

    @Test
    @DisplayName("mergeChildren must append child when edge is absent")
    void shouldAppendChildWhenEdgeIsAbsent() {
        final ReducedNode<String> node = createReducedLeaf("root");
        final ReducedNode<String> child = createReducedLeaf("child");

        node.mergeChildren(Map.of('a', child));

        assertSame(child, node.children().get('a'));
    }

    @Test
    @DisplayName("mergeChildren must allow the same canonical child instance for the same edge")
    void shouldAllowSameCanonicalChildInstanceForSameEdge() {
        final ReducedNode<String> child = createReducedLeaf("child");
        final ReducedNode<String> node = new ReducedNode<>(createLeafSignature("root"), Map.of(), Map.of('a', child));

        node.mergeChildren(Map.of('a', child));

        assertSame(child, node.children().get('a'));
    }

    @Test
    @DisplayName("mergeChildren must reject incompatible canonical child instance for the same edge")
    void shouldRejectIncompatibleCanonicalChildInstanceForSameEdge() {
        final ReducedNode<String> childA = createReducedLeaf("child-a");
        final ReducedNode<String> childB = createReducedLeaf("child-b");
        final ReducedNode<String> node = new ReducedNode<>(createLeafSignature("root"), Map.of(), Map.of('a', childA));

        final IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> node.mergeChildren(Map.of('a', childB)));

        assertTrue(exception.getMessage().contains("Incompatible canonical child"));
    }

    private static ReductionSignature<String> createLeafSignature(final String value) {
        final LocalValueSummary<String> summary = new LocalValueSummary<>(new String[] { value }, new int[] { 1 }, 1,
                value, 1, 0);

        return ReductionSignature.create(summary, Map.of(),
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
    }

    private static ReducedNode<String> createReducedLeaf(final String value) {
        return new ReducedNode<>(createLeafSignature(value), new LinkedHashMap<>(Map.of(value, 1)),
                new LinkedHashMap<>());
    }
}