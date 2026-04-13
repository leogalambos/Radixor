package org.egothor.stemmer.trie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;

import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.ReductionSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ChildDescriptor}.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("ChildDescriptor")
class ChildDescriptorTest {

    @Test
    @DisplayName("must implement equality and hash code from edge and child signature")
    void shouldImplementEqualityAndHashCodeFromEdgeAndChildSignature() {
        final ReductionSignature<String> signatureA = createLeafSignature("alpha");
        final ReductionSignature<String> signatureB = createLeafSignature("beta");

        final ChildDescriptor<String> descriptor = new ChildDescriptor<>('a', signatureA);
        final ChildDescriptor<String> equalDescriptor = new ChildDescriptor<>('a', signatureA);
        final ChildDescriptor<String> differentEdge = new ChildDescriptor<>('b', signatureA);
        final ChildDescriptor<String> differentSignature = new ChildDescriptor<>('a', signatureB);

        assertEquals(descriptor, equalDescriptor);
        assertEquals(descriptor.hashCode(), equalDescriptor.hashCode());
        assertNotEquals(descriptor, differentEdge);
        assertNotEquals(descriptor, differentSignature);
        assertNotEquals(descriptor, null);
        assertNotEquals(descriptor, "x");
    }

    private static ReductionSignature<String> createLeafSignature(final String value) {
        final LocalValueSummary<String> summary = new LocalValueSummary<>(new String[] { value }, new int[] { 1 }, 1,
                value, 1, 0);

        return ReductionSignature.create(summary, Map.of(),
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
    }
}