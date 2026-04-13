package org.egothor.stemmer.trie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MutableNode}.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("MutableNode")
class MutableNodeTest {

    @Test
    @DisplayName("must create empty maps on construction")
    void shouldCreateEmptyMapsOnConstruction() {
        final MutableNode<String> node = new MutableNode<>();

        assertTrue(node.children().isEmpty());
        assertTrue(node.valueCounts().isEmpty());
    }

    @Test
    @DisplayName("children must expose mutable backing map")
    void shouldExposeMutableBackingChildrenMap() {
        final MutableNode<String> node = new MutableNode<>();
        final MutableNode<String> child = new MutableNode<>();

        final Map<Character, MutableNode<String>> children = node.children();
        children.put('x', child);

        assertSame(children, node.children());
        assertSame(child, node.children().get('x'));
    }

    @Test
    @DisplayName("valueCounts must expose mutable backing map")
    void shouldExposeMutableBackingValueCountsMap() {
        final MutableNode<String> node = new MutableNode<>();

        final Map<String, Integer> valueCounts = node.valueCounts();
        valueCounts.put("stem", 3);

        assertSame(valueCounts, node.valueCounts());
        assertEquals(Integer.valueOf(3), node.valueCounts().get("stem"));
    }
}