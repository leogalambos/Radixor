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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Local descriptor preserving ranked {@code getAll()} semantics.
 */
/* default */ final class RankedLocalDescriptor {

    /**
     * Ordered values.
     */
    private final List<Object> orderedValues;

    /**
     * Creates a descriptor.
     *
     * @param orderedValues ordered values
     */
    private RankedLocalDescriptor(final List<Object> orderedValues) {
        this.orderedValues = orderedValues;
    }

    /**
     * Creates a descriptor from an ordered value array.
     *
     * @param orderedValues ordered values
     * @return descriptor
     */
    @SuppressWarnings("PMD.UseVarargs")
    /* default */ static RankedLocalDescriptor of(final Object[] orderedValues) {
        return new RankedLocalDescriptor(
                Collections.unmodifiableList(Arrays.asList(Arrays.copyOf(orderedValues, orderedValues.length))));
    }

    @Override
    public int hashCode() {
        return this.orderedValues.hashCode();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RankedLocalDescriptor)) {
            return false;
        }
        final RankedLocalDescriptor that = (RankedLocalDescriptor) other;
        return this.orderedValues.equals(that.orderedValues);
    }
}
