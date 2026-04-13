package org.egothor.stemmer.trie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DominantLocalDescriptor}.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("DominantLocalDescriptor")
class DominantLocalDescriptorTest {

    @Test
    @DisplayName("must implement equality and hash code from dominant value")
    void shouldImplementEqualityAndHashCodeFromDominantValue() {
        final DominantLocalDescriptor<String> descriptor = new DominantLocalDescriptor<>("stem");
        final DominantLocalDescriptor<String> equalDescriptor = new DominantLocalDescriptor<>("stem");
        final DominantLocalDescriptor<String> differentDescriptor = new DominantLocalDescriptor<>("other");

        assertEquals(descriptor, equalDescriptor);
        assertEquals(descriptor.hashCode(), equalDescriptor.hashCode());
        assertNotEquals(descriptor, differentDescriptor);
        assertNotEquals(descriptor, null);
        assertNotEquals(descriptor, "x");
    }

    @Test
    @DisplayName("must support null dominant value in equality semantics")
    void shouldSupportNullDominantValueInEqualitySemantics() {
        final DominantLocalDescriptor<String> descriptor = new DominantLocalDescriptor<>(null);
        final DominantLocalDescriptor<String> equalDescriptor = new DominantLocalDescriptor<>(null);

        assertEquals(descriptor, equalDescriptor);
        assertEquals(descriptor.hashCode(), equalDescriptor.hashCode());
    }
}