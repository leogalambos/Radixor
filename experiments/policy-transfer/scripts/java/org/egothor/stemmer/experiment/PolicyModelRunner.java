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

package org.egothor.stemmer.experiment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.egothor.stemmer.TrieMetadata;

/**
 * Executes one persisted Radixor policy model over an already normalized list
 * of lexical forms and emits deterministic word-to-output mappings.
 *
 * <p>The experiment normalizes lexical forms before model construction and
 * evaluation. Consequently this runner deliberately calls
 * {@link FrequencyTrie#getNormalizedString(String)} and applies each selected
 * patch to that same normalized form. This keeps normalization outside the
 * measured policy contrast and makes the two policy paths mechanically
 * identical.</p>
 */
public final class PolicyModelRunner {

    /**
     * Number of positional arguments required by the command-line protocol.
     */
    private static final int ARGUMENT_COUNT = 3;

    /**
     * Prevents construction of this command-line utility class.
     */
    private PolicyModelRunner() {
    }

    /**
     * Loads one compiled model, stems every non-empty input line, and writes a
     * tab-separated mapping containing the normalized form and its output.
     *
     * @param arguments model path, input-word path, and output path
     * @throws IOException if the model or a text file cannot be read or written
     */
    public static void main(final String[] arguments) throws IOException {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length != ARGUMENT_COUNT) {
            throw new IllegalArgumentException(
                    "Usage: PolicyModelRunner <model.radixor.gz> <words.txt> <predictions.tsv>");
        }

        final Path modelPath = Path.of(arguments[0]);
        final Path inputPath = Path.of(arguments[1]);
        final Path outputPath = Path.of(arguments[2]);
        final FrequencyTrie<CompiledPatchCommand> trie = StemmerPatchTrieLoader.loadBinaryCompiled(modelPath);
        final TrieMetadata metadata = trie.metadata();

        final Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
                BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            String word = reader.readLine();
            while (word != null) {
                if (!word.isEmpty()) {
                    final String stem = stemNormalized(trie, word);
                    writer.write(word);
                    writer.write('\t');
                    writer.write(stem);
                    writer.newLine();
                }
                word = reader.readLine();
            }
        }

        System.out.println("fingerprint=" + trie.getFingerprint());
        System.out.println("nodes=" + trie.size());
        System.out.println("formatVersion=" + metadata.formatVersion());
        System.out.println("traversalDirection=" + metadata.traversalDirection());
        System.out.println("caseProcessingMode=" + metadata.caseProcessingMode());
        System.out.println("diacriticProcessingMode=" + metadata.diacriticProcessingMode());
        System.out.println("reductionMode=" + metadata.reductionSettings().reductionMode());
    }

    /**
     * Applies the preferred compiled patch command to one normalized lexical
     * form, falling back to identity when no learned patch is available.
     *
     * @param trie compiled Radixor policy trie
     * @param word normalized lexical form
     * @return deterministic normalized stem output
     */
    private static String stemNormalized(final FrequencyTrie<CompiledPatchCommand> trie, final String word) {
        final CompiledPatchCommand patch = trie.getNormalizedString(word);
        if (patch == null || patch.preservesAllSources()) {
            return word;
        }
        return patch.apply(word);
    }
}
