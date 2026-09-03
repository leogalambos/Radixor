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

    /** Expected number of command-line arguments. */
    private static final int ARGUMENT_COUNT = 3;

    /** Prevents construction of this command-line utility class. */
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
