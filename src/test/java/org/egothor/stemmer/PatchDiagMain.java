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

import java.util.Locale;

/**
 * Prints patch commands for a small, fixed set of representative English word
 * and stem pairs.
 *
 * <p>This diagnostic uses the production backward traversal direction and the
 * baseline edit costs. It does not read dictionaries or modify project files;
 * its only side effect is writing a human-readable table to standard output.</p>
 */
public final class PatchDiagMain {

    /** Utility class. */
    private PatchDiagMain() {
        throw new AssertionError("No instances.");
    }

    /**
     * Encodes the built-in sample pairs and prints their patch commands.
     *
     * @param arguments command-line arguments; currently ignored
     */
    public static void main(final String[] arguments) {
        final PatchCommandEncoder encoder = PatchCommandEncoder.builder()
                .traversalDirection(WordTraversalDirection.BACKWARD)
                .deleteCost(1)
                .insertCost(1)
                .replaceCost(1)
                .matchCost(0)
                .build();
        final String[][] pairs = {
            { "running", "run" }, { "runs", "run" }, { "runner", "run" },
            { "went", "go" }, { "mice", "mouse" }, { "feet", "foot" },
            { "played", "play" }, { "playing", "play" }, { "plays", "play" },
            { "was", "be" }, { "is", "be" }, { "are", "be" },
            { "children", "child" }, { "men", "man" }, { "women", "woman" }
        };

        System.out.println("variant          -> stem       => patch");
        for (final String[] pair : pairs) {
            System.out.printf(Locale.ROOT, "%-16s -> %-10s => %s%n",
                    pair[0], pair[1], encoder.encode(pair[0], pair[1]));
        }
    }
}
