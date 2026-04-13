package org.egothor.stemmer.trie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RankedLocalDescriptor}.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("RankedLocalDescriptor")
class RankedLocalDescriptorTest {

    @Test
    @DisplayName("of must preserve order in equality semantics")
    void shouldPreserveOrderInEqualitySemantics() {
        final RankedLocalDescriptor descriptor = RankedLocalDescriptor.of(new Object[] { "a", "b", "c" });
        final RankedLocalDescriptor equalDescriptor = RankedLocalDescriptor.of(new Object[] { "a", "b", "c" });
        final RankedLocalDescriptor differentOrder = RankedLocalDescriptor.of(new Object[] { "b", "a", "c" });

        assertEquals(descriptor, equalDescriptor);
        assertEquals(descriptor.hashCode(), equalDescriptor.hashCode());
        assertNotEquals(descriptor, differentOrder);
        assertNotEquals(descriptor, null);
        assertNotEquals(descriptor, "x");
    }

    @Test
    @DisplayName("of must defensively copy source array")
    void shouldDefensivelyCopySourceArray() {
        final Object[] orderedValues = new Object[] { "a", "b" };

        final RankedLocalDescriptor descriptor = RankedLocalDescriptor.of(orderedValues);
        orderedValues[0] = "mutated";

        assertEquals(descriptor, RankedLocalDescriptor.of(new Object[] { "a", "b" }));
    }
}