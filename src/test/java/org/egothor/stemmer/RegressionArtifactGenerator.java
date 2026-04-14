package org.egothor.stemmer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Command-line utility that generates deterministic compiled trie regression
 * artifacts for test resources.
 *
 * <p>
 * This helper is intended for build and maintenance workflows that prepare
 * golden binary artifacts used by regression tests. It compiles a textual
 * stemmer source file into a compressed binary trie artifact using the
 * project's real loading and serialization pipeline.
 *
 * <p>
 * Expected arguments:
 * <ul>
 * <li>{@code --input <file>}</li>
 * <li>{@code --output <file>}</li>
 * <li>{@code --store-original <true|false>}</li>
 * <li>{@code --reduction-mode <enum-name>}</li>
 * </ul>
 */
public final class RegressionArtifactGenerator {

    /**
     * Logger for regression artifact generation.
     */
    private static final Logger LOGGER = Logger.getLogger(RegressionArtifactGenerator.class.getName());

    /**
     * Hidden constructor for utility entry point class.
     */
    private RegressionArtifactGenerator() {
        throw new AssertionError("No instances.");
    }

    /**
     * Program entry point.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        final int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Executes the artifact generation workflow.
     *
     * @param args command-line arguments
     * @return process exit code, where {@code 0} means success
     */
    static int run(final String[] args) {
        Objects.requireNonNull(args, "args");

        try {
            final Arguments arguments = Arguments.parse(args);

            LOGGER.log(Level.INFO,
                    "Generating regression artifact from input {0} to output {1} with storeOriginal={2} and reductionMode={3}.",
                    new Object[] { arguments.inputPath(), arguments.outputPath(),
                            Boolean.valueOf(arguments.storeOriginal()), arguments.reductionMode() });

            ensureParentDirectoryExists(arguments.outputPath());

            final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(arguments.inputPath(),
                    arguments.storeOriginal(), ReductionSettings.withDefaults(arguments.reductionMode()));

            StemmerPatchTrieBinaryIO.write(trie, arguments.outputPath());

            LOGGER.log(Level.INFO, "Regression artifact generated successfully at {0}.", arguments.outputPath());

            return 0;
        } catch (IllegalArgumentException exception) {
            LOGGER.log(Level.SEVERE, "Invalid generator arguments: {0}", exception.getMessage());
            printUsage();
            return 2;
        } catch (IOException exception) {
            LOGGER.log(Level.SEVERE,
                    "I/O failure while generating regression artifact for input/output pair {0} -> {1}.",
                    new Object[] { extractArgumentValue(args, "--input"), extractArgumentValue(args, "--output") });
            LOGGER.log(Level.SEVERE, "Artifact generation failed.", exception);
            return 1;
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Artifact generation failed due to an unexpected runtime error.", exception);
            return 1;
        }
    }

    /**
     * Ensures that the parent directory of the supplied output path exists.
     *
     * @param outputPath output file path
     * @throws IOException if directory creation fails
     */
    private static void ensureParentDirectoryExists(final Path outputPath) throws IOException {
        final Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Prints command-line usage to standard error.
     */
    private static void printUsage() {
        System.err.println("Usage:");
        System.err
                .println("  --input <file> --output <file> --store-original <true|false> --reduction-mode <enum-name>");
        System.err.println();
        System.err.println("Example:");
        System.err.println("  --input src/test/resources/regression/sources/mini-en.stemmer "
                + "--output src/test/resources/regression/golden/mini-en-ranked-storeorig.gz "
                + "--store-original true " + "--reduction-mode MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS");
    }

    /**
     * Extracts a raw argument value for diagnostic logging only.
     *
     * @param args command-line arguments
     * @param key  argument key to locate
     * @return associated value, or {@code "<missing>"} when absent
     */
    private static String extractArgumentValue(final String[] args, final String key) {
        for (int index = 0; index < args.length - 1; index++) {
            if (key.equals(args[index])) {
                return args[index + 1];
            }
        }
        return "<missing>";
    }

    /**
     * Parsed command-line arguments.
     *
     * @param inputPath     source stemmer file path
     * @param outputPath    target compressed artifact path
     * @param storeOriginal whether original words are stored as identity rules
     * @param reductionMode reduction mode to apply during compilation
     */
    private record Arguments(Path inputPath, Path outputPath, boolean storeOriginal, ReductionMode reductionMode) {

        /**
         * Parses the supplied command-line arguments.
         *
         * @param args command-line arguments
         * @return parsed argument record
         */
        private static Arguments parse(final String[] args) {
            Objects.requireNonNull(args, "args");

            Path inputPath = null;
            Path outputPath = null;
            Boolean storeOriginal = null;
            ReductionMode reductionMode = null;

            int index = 0;
            while (index < args.length) {
                final String argument = args[index];

                switch (argument) {
                    case "--input":
                        inputPath = Path.of(readRequiredValue(args, index, argument));
                        index += 2;
                        break;
                    case "--output":
                        outputPath = Path.of(readRequiredValue(args, index, argument));
                        index += 2;
                        break;
                    case "--store-original":
                        storeOriginal = Boolean.valueOf(readRequiredValue(args, index, argument));
                        index += 2;
                        break;
                    case "--reduction-mode":
                        reductionMode = ReductionMode.valueOf(readRequiredValue(args, index, argument));
                        index += 2;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown argument: " + argument);
                }
            }

            if (inputPath == null) {
                throw new IllegalArgumentException("Missing required argument: --input");
            }
            if (outputPath == null) {
                throw new IllegalArgumentException("Missing required argument: --output");
            }
            if (storeOriginal == null) {
                throw new IllegalArgumentException("Missing required argument: --store-original");
            }
            if (reductionMode == null) {
                throw new IllegalArgumentException("Missing required argument: --reduction-mode");
            }

            return new Arguments(inputPath, outputPath, storeOriginal.booleanValue(), reductionMode);
        }

        /**
         * Reads the required value immediately following an option key.
         *
         * @param args     command-line arguments
         * @param index    current option index
         * @param argument option key
         * @return option value
         */
        private static String readRequiredValue(final String[] args, final int index, final String argument) {
            final int valueIndex = index + 1;
            if (valueIndex >= args.length) {
                throw new IllegalArgumentException("Missing value for argument: " + argument);
            }
            return args[valueIndex];
        }
    }
}