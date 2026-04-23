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
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Command-line entry point for the stemmer knowledge experiment.
 */
public final class StemmerKnowledgeExperimentCli {

    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(StemmerKnowledgeExperimentCli.class.getName());

    /**
     * Exit status indicating success.
     */
    private static final int EXIT_SUCCESS = 0;

    /**
     * Exit status indicating processing failure.
     */
    private static final int EXIT_PROCESSING_ERROR = 1;

    /**
     * Exit status indicating invalid command-line usage.
     */
    private static final int EXIT_USAGE_ERROR = 2;

    /**
     * Default deterministic seed.
     */
    private static final long DEFAULT_SEED = 20_260_421L;

    /**
     * Default output report location.
     */
    private static final Path DEFAULT_OUTPUT_PATH = Path.of("build", "reports", "stemmer-knowledge-experiment.csv");

    /**
     * Usage banner.
     */
    private static final String USAGE = String.join(System.lineSeparator(),
            "Usage: StemmerKnowledgeExperimentCli [--bundled-all | --bundled-language <LANG> | --input <PATH>]",
            "       [--seed <LONG>] [--output <CSV_PATH>]", "", "Examples:", "  --bundled-all",
            "  --bundled-language US_UK_PROFI --seed 20260421",
            "  --input src/main/resources/us_uk/stemmer --output build/reports/knowledge.csv");

    /**
     * Utility class.
     */
    private StemmerKnowledgeExperimentCli() {
        throw new AssertionError("No instances.");
    }

    /**
     * Executes the CLI as a standalone process.
     *
     * @param arguments command-line arguments
     */
    public static void main(final String[] arguments) {
        final int exitCode = execute(arguments);
        System.exit(exitCode);
    }

    /**
     * Executes the CLI and translates all outcomes to process exit codes.
     *
     * @param arguments command-line arguments
     * @return process exit code
     */
    /* default */ static int execute(final String... arguments) {
        Objects.requireNonNull(arguments, "arguments");
        try {
            final CliOptions options = CliOptions.parse(arguments);
            if (options.command() == Command.HELP) {
                printUsage(System.out);
                return EXIT_SUCCESS;
            }
            return runExperiment(options);
        } catch (final CliUsageException exception) {
            if (LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.log(Level.SEVERE, "Invalid command-line usage for arguments {0}: {1}",
                        new Object[] { Arrays.toString(arguments), exception.getMessage() });
            }
            printUsage(System.err);
            return EXIT_USAGE_ERROR;
        } catch (final IOException exception) {
            if (LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.log(Level.SEVERE, "Experiment processing failed for arguments {0}", Arrays.toString(arguments));
                LOGGER.log(Level.SEVERE, "Processing failure details.", exception);
            }
            return EXIT_PROCESSING_ERROR;
        } catch (final RuntimeException exception) { // NOPMD
            if (LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.log(Level.SEVERE, "Unexpected runtime failure for arguments {0}", Arrays.toString(arguments));
                LOGGER.log(Level.SEVERE, "Unexpected processing failure details.", exception);
            }
            return EXIT_PROCESSING_ERROR;
        }
    }

    /**
     * Runs the experiment for already validated options.
     *
     * @param options validated CLI options
     * @return process exit code
     * @throws IOException if experiment execution fails
     */
    private static int runExperiment(final CliOptions options) throws IOException {
        final StemmerKnowledgeExperiment experiment = new StemmerKnowledgeExperiment();
        final List<StemmerKnowledgeExperiment.ResultRow> rows = switch (options.sourceMode()) {
            case INPUT_PATH -> experiment.evaluatePath(options.inputPath(), options.seed());
            case SINGLE_BUNDLED_LANGUAGE -> experiment.evaluateBundledLanguage(options.language(), options.seed());
            case ALL_BUNDLED_LANGUAGES -> experiment.evaluateAllBundledLanguages(options.seed());
        };

        StemmerKnowledgeExperiment.writeCsv(options.outputPath(), rows);
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "Knowledge experiment report written to {0} with {1} rows.",
                    new Object[] { options.outputPath().toAbsolutePath(), rows.size() });
        }
        return EXIT_SUCCESS;
    }

    /**
     * Prints the CLI usage text.
     *
     * @param stream target output stream
     */
    private static void printUsage(final PrintStream stream) {
        stream.println(USAGE);
    }

    /**
     * Supported top-level CLI commands.
     */
    private enum Command {

        /**
         * Executes the experiment.
         */
        EXECUTE,

        /**
         * Prints usage text.
         */
        HELP
    }

    /**
     * Supported experiment source selection modes.
     */
    private enum ExperimentSourceMode {

        /**
         * Runs the experiment for all bundled languages.
         */
        ALL_BUNDLED_LANGUAGES,

        /**
         * Runs the experiment for one bundled language.
         */
        SINGLE_BUNDLED_LANGUAGE,

        /**
         * Runs the experiment for one external dictionary path.
         */
        INPUT_PATH
    }

    /**
     * Exception indicating invalid command-line usage.
     */
    private static final class CliUsageException extends Exception {

        private static final long serialVersionUID = -3904751711104596247L;

        /**
         * Creates a new usage exception.
         *
         * @param message failure description
         */
        private CliUsageException(final String message) {
            super(message);
        }

        /**
         * Creates a new usage exception.
         *
         * @param message failure description
         * @param cause   original cause
         */
        private CliUsageException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Parsed CLI options.
     *
     * @param command    selected top-level command
     * @param sourceMode selected experiment source mode
     * @param inputPath  optional filesystem dictionary path
     * @param language   optional bundled language
     * @param seed       deterministic sampling seed
     * @param outputPath CSV report path
     */
    private record CliOptions(Command command, ExperimentSourceMode sourceMode, Path inputPath,
            StemmerPatchTrieLoader.Language language, long seed, Path outputPath) {

        /**
         * Parses the command line.
         *
         * @param arguments command-line arguments
         * @return parsed options
         * @throws CliUsageException if the command line is invalid
         */
        @SuppressWarnings("PMD.AvoidReassigningLoopVariables")
        private static CliOptions parse(final String... arguments) throws CliUsageException {
            Objects.requireNonNull(arguments, "arguments");

            Command command = Command.EXECUTE;
            ExperimentSourceMode sourceMode = ExperimentSourceMode.ALL_BUNDLED_LANGUAGES;
            Path inputPath = null;
            StemmerPatchTrieLoader.Language language = null;
            long seed = DEFAULT_SEED;
            Path outputPath = DEFAULT_OUTPUT_PATH;

            final List<String> tokens = new ArrayList<>(List.of(arguments));
            for (int index = 0; index < tokens.size(); index++) {
                final String token = tokens.get(index);
                switch (token) {
                    case "--input" -> {
                        sourceMode = ExperimentSourceMode.INPUT_PATH;
                        inputPath = Path.of(requireValue(tokens, ++index, token));
                        language = null;
                    }
                    case "--bundled-language" -> {
                        sourceMode = ExperimentSourceMode.SINGLE_BUNDLED_LANGUAGE;
                        language = parseLanguage(requireValue(tokens, ++index, token));
                        inputPath = null;
                    }
                    case "--bundled-all" -> {
                        sourceMode = ExperimentSourceMode.ALL_BUNDLED_LANGUAGES;
                        inputPath = null;
                        language = null;
                    }
                    case "--seed" -> seed = parseSeed(requireValue(tokens, ++index, token));
                    case "--output" -> outputPath = Path.of(requireValue(tokens, ++index, token));
                    case "--help", "-h" -> command = Command.HELP;
                    default -> throw new CliUsageException("Unknown argument: " + token);
                }
            }

            return new CliOptions(command, sourceMode, inputPath, language, seed, outputPath);
        }

        /**
         * Returns the required value after one option token.
         *
         * @param tokens all tokens
         * @param index  expected value index
         * @param option current option token
         * @return option value
         * @throws CliUsageException if the option value is missing
         */
        private static String requireValue(final List<String> tokens, final int index, final String option)
                throws CliUsageException {
            if (index >= tokens.size()) {
                throw new CliUsageException("Missing value for option " + option + '.');
            }
            return tokens.get(index);
        }

        /**
         * Parses the deterministic seed.
         *
         * @param value textual seed value
         * @return parsed seed
         * @throws CliUsageException if the seed value is invalid
         */
        private static long parseSeed(final String value) throws CliUsageException {
            try {
                return Long.parseLong(value);
            } catch (final NumberFormatException exception) {
                throw new CliUsageException("Invalid value for --seed: " + value, exception);
            }
        }

        /**
         * Parses the bundled language selector.
         *
         * @param value textual language name
         * @return parsed language
         * @throws CliUsageException if the language value is invalid
         */
        private static StemmerPatchTrieLoader.Language parseLanguage(final String value) throws CliUsageException {
            try {
                return StemmerPatchTrieLoader.Language.valueOf(value);
            } catch (final IllegalArgumentException exception) {
                throw new CliUsageException("Invalid value for --bundled-language: " + value, exception);
            }
        }
    }
}
