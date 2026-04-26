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
package org.egothor.stemmer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;

/**
 * Property-based tests for {@link PatchCommandEncoder}.
 *
 * <p>
 * These properties protect the most important behavioral contract of the patch
 * language: encoding must be deterministic and applying an encoded patch must
 * reconstruct the exact requested target.
 */
@Label("PatchCommandEncoder properties")
@Tag("unit")
@Tag("property")
@Tag("patch")
class PatchCommandEncoderProperties extends PropertyBasedTestSupport {

    /**
     * Verifies that encoding followed by application reconstructs the original
     * target word for bounded generated inputs.
     *
     * @param source source word
     * @param target target word
     */
    @Property(tries = 200)
    @Label("encode followed by apply should reconstruct the target word")
    void encodeFollowedByApplyShouldReconstructTheTargetWord(@ForAll("words") final String source,
            @ForAll("words") final String target) {
        final PatchCommandEncoder encoder = PatchCommandEncoder.builder().build();
        final String patch = encoder.encode(source, target);

        assertNotNull(patch, "patch generation must succeed for non-null inputs.");
        assertEquals(target, PatchCommandEncoder.apply(source, patch),
                "applying the encoded patch must reconstruct the target word.");
    }

    /**
     * Verifies that encoding is deterministic for the same source-target pair, both
     * within one encoder instance and across fresh instances.
     *
     * @param source source word
     * @param target target word
     */
    @Property(tries = 150)
    @Label("encode should be deterministic for one source-target pair")
    void encodeShouldBeDeterministicForOneSourceTargetPair(@ForAll("words") final String source,
            @ForAll("words") final String target) {
        final PatchCommandEncoder sharedEncoder = PatchCommandEncoder.builder().build();
        final String first = sharedEncoder.encode(source, target);
        final String second = sharedEncoder.encode(source, target);
        final String fresh = PatchCommandEncoder.builder().build().encode(source, target);

        assertEquals(first, second, "one encoder instance must produce stable output.");
        assertEquals(first, fresh, "fresh encoder instances must produce the same patch output.");
    }
}
