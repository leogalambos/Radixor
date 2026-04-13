package org.egothor.stemmer.trie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UnorderedLocalDescriptor}.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("UnorderedLocalDescriptor")
class UnorderedLocalDescriptorTest {

    @Test
    @DisplayName("of must ignore ordering and duplicates in equality semantics")
    void shouldIgnoreOrderingAndDuplicatesInEqualitySemantics() {
        final UnorderedLocalDescriptor descriptor = UnorderedLocalDescriptor.of(new Object[] { "a", "b", "a" });
        final UnorderedLocalDescriptor equalDescriptor = UnorderedLocalDescriptor.of(new Object[] { "b", "a" });
        final UnorderedLocalDescriptor differentDescriptor = UnorderedLocalDescriptor.of(new Object[] { "a", "c" });

        assertEquals(descriptor, equalDescriptor);
        assertEquals(descriptor.hashCode(), equalDescriptor.hashCode());
        assertNotEquals(descriptor, differentDescriptor);
        assertNotEquals(descriptor, null);
        assertNotEquals(descriptor, "x");
    }

    @Test
    @DisplayName("of must defensively isolate descriptor from source array mutation")
    void shouldDefensivelyIsolateDescriptorFromSourceArrayMutation() {
        final Object[] values = new Object[] { "a", "b" };

        final UnorderedLocalDescriptor descriptor = UnorderedLocalDescriptor.of(values);
        values[0] = "mutated";

        assertEquals(descriptor, UnorderedLocalDescriptor.of(new Object[] { "a", "b" }));
    }
}