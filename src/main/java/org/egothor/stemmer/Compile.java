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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Command-line compiler of stemmer dictionary files into compressed binary
 * {@link FrequencyTrie} artifacts.
 *
 * <p>
 * The CLI reads an input file in the same syntax as the project's stemmer
 * resource files, compiles it into a read-only {@link FrequencyTrie} of patch
 * commands, applies the selected subtree reduction strategy, and writes the
 * resulting trie in the project binary format under GZip compression.
 *
 * <p>
 * Remarks introduced by {@code #} or {@code //} are supported through
 * {@link StemmerDictionaryParser}.
 *
 * <p>
 * Supported arguments:
 * </p>
 *
 * <pre>
 * --input &lt;file&gt;
 * --output &lt;file&gt;
 * --reduction-mode &lt;mode&gt;
 * [--store-original]
 * [--dominant-winner-min-percent &lt;1..100&gt;]
 * [--dominant-winner-over-second-ratio &lt;1..n&gt;]
 * [--overwrite]
 * [--help]
 * </pre>
 */
public final class Compile {

    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(Compile.class.getName());

    /**
     * Exit status indicating success.
     */
    private static final int EXIT_SUCCESS = 0;

    /**
     * Exit status indicating invalid command-line usage.
     */
    private static final int EXIT_USAGE_ERROR = 2;

    /**
     * Exit status indicating processing failure.
     */
    private static final int EXIT_PROCESSING_ERROR = 1;

    /**
     * Utility class.
     */
    private Compile() {
        throw new AssertionError("No instances.");
    }

    /**
     * CLI entry point.
     *
     * @param arguments command-line arguments
     */
    public static void main(final String[] arguments) {
        final int exitCode = run(arguments);
        if (exitCode != EXIT_SUCCESS) {
            System.exit(exitCode);
        }
    }

    /**
     * Executes the CLI.
     *
     * @param arguments command-line arguments
     * @return process exit code
     */
    /* default */ static int run(final String... arguments) {
        try {
            final Arguments parsedArguments = Arguments.parse(arguments);
            if (parsedArguments.help()) {
                printUsage();
                return EXIT_SUCCESS;
            }

            compile(parsedArguments);
            return EXIT_SUCCESS;
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.err.println();
            printUsage();
            return EXIT_USAGE_ERROR;
        } catch (IOException exception) {
            if (LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.log(Level.SEVERE, "CLI compilation failed for input {0} and output {1}.",
                        new Object[] { safeInput(arguments), safeOutput(arguments) });
            }
            System.err.println("Compilation failed: " + exception.getMessage());
            return EXIT_PROCESSING_ERROR;
        }
    }

    /**
     * Compiles the input dictionary and writes the compressed binary trie.
     *
     * @param arguments parsed command-line arguments
     * @throws IOException if compilation or output writing fails
     */
    private static void compile(final Arguments arguments) throws IOException {
        final ReductionSettings reductionSettings = new ReductionSettings(arguments.reductionMode(),
                arguments.dominantWinnerMinPercent(), arguments.dominantWinnerOverSecondRatio());

        final FrequencyTrie<String> trie = StemmerPatchTrieLoader.load(arguments.inputFile(), arguments.storeOriginal(),
                reductionSettings);

        final Path outputFile = arguments.outputFile();
        final Path parent = outputFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.exists(outputFile) && !arguments.overwrite()) {
            throw new IOException("Output file already exists: " + outputFile.toAbsolutePath());
        }

        StemmerPatchTrieBinaryIO.write(trie, outputFile);

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                    "Compiled dictionary {0} to {1} using mode {2}, storeOriginal={3}, dominantWinnerMinPercent={4}, dominantWinnerOverSecondRatio={5}.",
                    new Object[] { arguments.inputFile().toAbsolutePath().toString(),
                            arguments.outputFile().toAbsolutePath().toString(), arguments.reductionMode().name(),
                            arguments.storeOriginal(), arguments.dominantWinnerMinPercent(),
                            arguments.dominantWinnerOverSecondRatio() });
        }
    }

    /**
     * Prints CLI usage help.
     */
    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  java org.egothor.stemmer.Compile \\");
        System.err.println("      --input <file> \\");
        System.err.println("      --output <file> \\");
        System.err.println("      --reduction-mode <mode> \\");
        System.err.println("      [--store-original] \\");
        System.err.println("      [--dominant-winner-min-percent <1..100>] \\");
        System.err.println("      [--dominant-winner-over-second-ratio <1..n>] \\");
        System.err.println("      [--overwrite]");
        System.err.println();
        System.err.println("Supported reduction modes:");
        for (ReductionMode mode : ReductionMode.values()) {
            System.err.println("  " + mode.name());
        }
    }

    /**
     * Returns a best-effort input value for diagnostic logging.
     *
     * @param arguments raw command-line arguments
     * @return input value if present, otherwise {@code "<unknown>"}
     */
    private static String safeInput(final String... arguments) {
        return safeOptionValue(arguments, "--input");
    }

    /**
     * Returns a best-effort output value for diagnostic logging.
     *
     * @param arguments raw command-line arguments
     * @return output value if present, otherwise {@code "<unknown>"}
     */
    private static String safeOutput(final String... arguments) {
        return safeOptionValue(arguments, "--output");
    }

    /**
     * Returns a best-effort option value from raw arguments.
     *
     * @param arguments raw command-line arguments
     * @param option    option name
     * @return option value if present, otherwise {@code "<unknown>"}
     */
    private static String safeOptionValue(final String[] arguments, final String option) {
        if (arguments == null) {
            return "<unknown>";
        }
        for (int index = 0; index < arguments.length - 1; index++) {
            if (option.equals(arguments[index])) {
                return arguments[index + 1];
            }
        }
        return "<unknown>";
    }

    /**
     * Immutable parsed CLI arguments.
     *
     * @param inputFile                     input dictionary file
     * @param outputFile                    output compressed trie file
     * @param reductionMode                 subtree reduction mode
     * @param storeOriginal                 whether original stems are stored
     * @param dominantWinnerMinPercent      dominant winner minimum percent
     * @param dominantWinnerOverSecondRatio dominant winner over second ratio
     * @param overwrite                     whether an existing output may be
     *                                      replaced
     * @param help                          whether usage help was requested
     */
    @SuppressWarnings("PMD.LongVariable")
    private record Arguments(Path inputFile, Path outputFile, ReductionMode reductionMode, boolean storeOriginal,
            int dominantWinnerMinPercent, int dominantWinnerOverSecondRatio, boolean overwrite, boolean help) {

        /**
         * Parses raw command-line arguments.
         *
         * @param arguments raw command-line arguments
         * @return parsed arguments
         */
        @SuppressWarnings({ "PMD.AvoidReassigningLoopVariables", "PMD.CyclomaticComplexity" })
        private static Arguments parse(final String... arguments) {
            Objects.requireNonNull(arguments, "arguments");

            Path inputFile = null;
            Path outputFile = null;
            ReductionMode reductionMode = null;
            boolean storeOriginal = false;
            boolean overwrite = false;
            boolean help = false;
            int dominantWinnerMinPercent = ReductionSettings.DEFAULT_DOMINANT_WINNER_MIN_PERCENT;
            int dominantWinnerOverSecondRatio = ReductionSettings.DEFAULT_DOMINANT_WINNER_OVER_SECOND_RATIO;

            for (int index = 0; index < arguments.length; index++) {
                final String argument = arguments[index];

                switch (argument) {
                    case "--help":
                    case "-h":
                        help = true;
                        break;

                    case "--store-original":
                        storeOriginal = true;
                        break;

                    case "--overwrite":
                        overwrite = true;
                        break;

                    case "--input":
                        inputFile = Path.of(requireValue(arguments, ++index, "--input"));
                        break;

                    case "--output":
                        outputFile = Path.of(requireValue(arguments, ++index, "--output"));
                        break;

                    case "--reduction-mode":
                        reductionMode = ReductionMode
                                .valueOf(requireValue(arguments, ++index, "--reduction-mode").toUpperCase(Locale.ROOT));
                        break;

                    case "--dominant-winner-min-percent":
                        dominantWinnerMinPercent = parseInteger(
                                requireValue(arguments, ++index, "--dominant-winner-min-percent"),
                                "--dominant-winner-min-percent");
                        break;

                    case "--dominant-winner-over-second-ratio":
                        dominantWinnerOverSecondRatio = parseInteger(
                                requireValue(arguments, ++index, "--dominant-winner-over-second-ratio"),
                                "--dominant-winner-over-second-ratio");
                        break;

                    default:
                        throw new IllegalArgumentException("Unknown argument: " + argument);
                }
            }

            if (help) {
                return new Arguments(inputFile, outputFile, reductionMode, storeOriginal, dominantWinnerMinPercent,
                        dominantWinnerOverSecondRatio, overwrite, true);
            }

            if (inputFile == null) {
                throw new IllegalArgumentException("Missing required argument --input.");
            }
            if (outputFile == null) {
                throw new IllegalArgumentException("Missing required argument --output.");
            }
            if (reductionMode == null) {
                throw new IllegalArgumentException("Missing required argument --reduction-mode.");
            }

            return new Arguments(inputFile, outputFile, reductionMode, storeOriginal, dominantWinnerMinPercent,
                    dominantWinnerOverSecondRatio, overwrite, false);
        }

        /**
         * Returns the required value of an option.
         *
         * @param arguments raw arguments
         * @param index     value index
         * @param option    option name
         * @return option value
         */
        private static String requireValue(final String[] arguments, final int index, final String option) {
            if (index >= arguments.length) {
                throw new IllegalArgumentException("Missing value for " + option + ".");
            }
            return arguments[index];
        }

        /**
         * Parses an integer option value.
         *
         * @param value      raw value
         * @param optionName option name
         * @return parsed integer
         */
        private static int parseInteger(final String value, final String optionName) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid integer for " + optionName + ": " + value, exception);
            }
        }
    }
}
