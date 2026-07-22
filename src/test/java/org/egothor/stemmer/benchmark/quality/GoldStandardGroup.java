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
package org.egothor.stemmer.benchmark.quality;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable gold-standard equivalence class originating from one dictionary row. */
public record GoldStandardGroup(int rowNumber, List<String> forms) {
    private static final int FIRST_ROW_NUMBER = 1;
    /**
     * Creates a group while removing exact duplicates within this row.
     *
     * @param rowNumber positive physical dictionary row number
     * @param forms supplied forms; encounter order has no metric significance
     * @throws IllegalArgumentException if the row or forms are invalid
     */
    public GoldStandardGroup {
        if (rowNumber < FIRST_ROW_NUMBER) {
            throw new IllegalArgumentException("Dictionary row number must be positive.");
        }
        Objects.requireNonNull(forms, "forms");
        final Set<String> distinct = new LinkedHashSet<>();
        for (String form : forms) {
            if (form == null || form.isEmpty()) {
                throw new IllegalArgumentException("Dictionary group forms must be non-empty strings.");
            }
            distinct.add(form);
        }
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException("A dictionary group must contain at least one usable form.");
        }
        forms = List.copyOf(distinct);
    }
}
