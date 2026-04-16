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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link Compile}.
 *
 * <p>
 * The suite verifies command-line orchestration, argument validation, overwrite
 * semantics, help output, processing failures, and successful compilation into
 * a compressed binary trie artifact.
 * </p>
 *
 * <p>
 * The tests target the package-visible {@link Compile#run(String...)} method so
 * that the CLI logic can be exercised without triggering
 * {@link System#exit(int)}.
 * </p>
 */
@Tag("unit")
@DisplayName("Compile")
class CompileTest {

    /**
     * Temporary directory for each test.
     */
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("should reject utility class instantiation")
    void shouldRejectUtilityClassInstantiation() throws Exception {
        final Constructor<Compile> constructor = Compile.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                constructor::newInstance);

        assertAll(() -> assertNotNull(exception.getCause(), "The root cause must be present."),
                () -> assertEquals(AssertionError.class, exception.getCause().getClass(),
                        "The utility constructor must fail with AssertionError."),
                () -> assertEquals("No instances.", exception.getCause().getMessage(),
                        "The utility constructor must expose the expected diagnostic message."));
    }

    @Test
    @DisplayName("should return success and print usage when help is requested")
    void shouldReturnSuccessAndPrintUsageWhenHelpIsRequested() {
        final CommandResult result = runWithCapturedStandardError("--help");

        assertAll(() -> assertEquals(0, result.exitCode(), "Help must terminate successfully."),
                () -> assertTrue(result.standardError().contains("Usage:"),
                        "Help output must contain the usage header."),
                () -> assertTrue(result.standardError().contains("--input <file>"),
                        "Help output must describe the input option."),
                () -> assertTrue(result.standardError().contains("--output <file>"),
                        "Help output must describe the output option."),
                () -> assertTrue(result.standardError().contains("--reduction-mode <mode>"),
                        "Help output must describe the reduction mode option."));
    }

    @Test
    @DisplayName("should compile minimal dictionary into non-empty output file")
    void shouldCompileMinimalDictionaryIntoNonEmptyOutputFile() throws Exception {
        final Path inputFile = createMinimalDictionaryFile("minimal-dictionary.txt");
        final Path outputFile = temporaryDirectory.resolve("compiled-trie.dat.gz");

        final int exitCode = Compile.run("--input", inputFile.toString(), "--output", outputFile.toString(),
                "--reduction-mode", validReductionModeName());

        assertAll(() -> assertEquals(0, exitCode, "Valid compilation must succeed."),
                () -> assertTrue(Files.exists(outputFile), "Compilation must create the output file."),
                () -> assertTrue(Files.size(outputFile) > 0L, "The written output file must not be empty."));
    }

    @Test
    @DisplayName("should compile successfully when store-original is enabled")
    void shouldCompileSuccessfullyWhenStoreOriginalIsEnabled() throws Exception {
        final Path inputFile = createMinimalDictionaryFile("store-original-dictionary.txt");
        final Path outputFile = temporaryDirectory.resolve("compiled-store-original.dat.gz");

        final int exitCode = Compile.run("--input", inputFile.toString(), "--output", outputFile.toString(),
                "--reduction-mode", validReductionModeName(), "--store-original");

        assertAll(() -> assertEquals(0, exitCode, "Compilation with store-original must succeed."),
                () -> assertTrue(Files.exists(outputFile), "Compilation must create the output file."),
                () -> assertTrue(Files.size(outputFile) > 0L, "The written output file must not be empty."));
    }

    @Test
    @DisplayName("should fail with processing error when output exists and overwrite is not enabled")
    void shouldFailWithProcessingErrorWhenOutputExistsAndOverwriteIsNotEnabled() throws Exception {
        final Path inputFile = createMinimalDictionaryFile("overwrite-protection-dictionary.txt");
        final Path outputFile = temporaryDirectory.resolve("already-present.dat.gz");
        Files.writeString(outputFile, "existing-content", StandardCharsets.UTF_8);

        final CommandResult result = runWithCapturedStandardError("--input", inputFile.toString(), "--output",
                outputFile.toString(), "--reduction-mode", validReductionModeName());

        assertAll(
                () -> assertEquals(1, result.exitCode(),
                        "Existing output without overwrite must be reported as processing failure."),
                () -> assertTrue(result.standardError().contains("Compilation failed:"),
                        "Processing failures must be reported to standard error."),
                () -> assertTrue(result.standardError().contains("Output file already exists"),
                        "The failure reason must mention overwrite protection."));
    }

    @Test
    @DisplayName("should overwrite existing output when overwrite is enabled")
    void shouldOverwriteExistingOutputWhenOverwriteIsEnabled() throws Exception {
        final Path inputFile = createMinimalDictionaryFile("overwrite-enabled-dictionary.txt");
        final Path outputFile = temporaryDirectory.resolve("overwrite-enabled.dat.gz");
        Files.writeString(outputFile, "obsolete-content", StandardCharsets.UTF_8);

        final int exitCode = Compile.run("--input", inputFile.toString(), "--output", outputFile.toString(),
                "--reduction-mode", validReductionModeName(), "--overwrite");

        assertAll(() -> assertEquals(0, exitCode, "Overwrite-enabled compilation must succeed."),
                () -> assertTrue(Files.exists(outputFile), "The output file must exist after overwrite."),
                () -> assertTrue(Files.size(outputFile) > 0L, "The overwritten output file must not be empty."),
                () -> assertFalse(
                        Files.readString(outputFile, StandardCharsets.ISO_8859_1).contains("obsolete-content"),
                        "The original placeholder content must be replaced by compiled binary output."));
    }

    @Nested
    @DisplayName("argument validation")
    class ArgumentValidationTest {

        @Test
        @DisplayName("should fail with usage error when input is missing")
        void shouldFailWithUsageErrorWhenInputIsMissing() {
            final CommandResult result = runWithCapturedStandardError("--output",
                    temporaryDirectory.resolve("out.dat.gz").toString(), "--reduction-mode", validReductionModeName());

            assertAll(() -> assertEquals(2, result.exitCode(), "Missing input must be treated as usage error."),
                    () -> assertTrue(result.standardError().contains("--input"),
                            "The diagnostic message must identify the missing input argument."),
                    () -> assertTrue(result.standardError().contains("Usage:"),
                            "Usage help must be printed for invalid invocation."));
        }

        @Test
        @DisplayName("should fail with usage error when output is missing")
        void shouldFailWithUsageErrorWhenOutputIsMissing() throws Exception {
            final Path inputFile = createMinimalDictionaryFile("missing-output.txt");

            final CommandResult result = runWithCapturedStandardError("--input", inputFile.toString(),
                    "--reduction-mode", validReductionModeName());

            assertAll(() -> assertEquals(2, result.exitCode(), "Missing output must be treated as usage error."),
                    () -> assertTrue(result.standardError().contains("--output"),
                            "The diagnostic message must identify the missing output argument."),
                    () -> assertTrue(result.standardError().contains("Usage:"),
                            "Usage help must be printed for invalid invocation."));
        }

        @Test
        @DisplayName("should fail with usage error when reduction mode is missing")
        void shouldFailWithUsageErrorWhenReductionModeIsMissing() throws Exception {
            final Path inputFile = createMinimalDictionaryFile("missing-mode.txt");

            final CommandResult result = runWithCapturedStandardError("--input", inputFile.toString(), "--output",
                    temporaryDirectory.resolve("out.dat.gz").toString());

            assertAll(
                    () -> assertEquals(2, result.exitCode(), "Missing reduction mode must be treated as usage error."),
                    () -> assertTrue(result.standardError().contains("--reduction-mode"),
                            "The diagnostic message must identify the missing reduction mode."),
                    () -> assertTrue(result.standardError().contains("Usage:"),
                            "Usage help must be printed for invalid invocation."));
        }

        @Test
        @DisplayName("should fail with usage error for unknown argument")
        void shouldFailWithUsageErrorForUnknownArgument() {
            final CommandResult result = runWithCapturedStandardError("--unknown-option");

            assertAll(() -> assertEquals(2, result.exitCode(), "Unknown options must be treated as usage errors."),
                    () -> assertTrue(result.standardError().contains("Unknown argument: --unknown-option"),
                            "The diagnostic message must identify the unknown option."),
                    () -> assertTrue(result.standardError().contains("Usage:"),
                            "Usage help must be printed for invalid invocation."));
        }

        @Test
        @DisplayName("should fail with usage error for invalid reduction mode")
        void shouldFailWithUsageErrorForInvalidReductionMode() throws Exception {
            final Path inputFile = createMinimalDictionaryFile("invalid-mode.txt");

            final CommandResult result = runWithCapturedStandardError("--input", inputFile.toString(), "--output",
                    temporaryDirectory.resolve("out.dat.gz").toString(), "--reduction-mode", "NOT_A_MODE");

            assertAll(
                    () -> assertEquals(2, result.exitCode(),
                            "An unsupported reduction mode must be treated as usage error."),
                    () -> assertTrue(
                            result.standardError().contains("NOT_A_MODE")
                                    || result.standardError().contains("No enum constant"),
                            "The diagnostic message must expose the invalid reduction mode."),
                    () -> assertTrue(result.standardError().contains("Usage:"),
                            "Usage help must be printed for invalid invocation."));
        }

        @Test
        @DisplayName("should fail with usage error for invalid dominant winner min percent")
        void shouldFailWithUsageErrorForInvalidDominantWinnerMinPercent() throws Exception {
            final Path inputFile = createMinimalDictionaryFile("invalid-min-percent.txt");

            final CommandResult result = runWithCapturedStandardError("--input", inputFile.toString(), "--output",
                    temporaryDirectory.resolve("out.dat.gz").toString(), "--reduction-mode", validReductionModeName(),
                    "--dominant-winner-min-percent", "invalid");

            assertAll(
                    () -> assertEquals(2, result.exitCode(),
                            "A non-integer dominant winner min percent must be treated as usage error."),
                    () -> assertTrue(result.standardError().contains("--dominant-winner-min-percent"),
                            "The diagnostic message must identify the invalid numeric option."),
                    () -> assertTrue(result.standardError().contains("invalid"),
                            "The diagnostic message should include the invalid value."),
                    () -> assertTrue(result.standardError().contains("Usage:"),
                            "Usage help must be printed for invalid invocation."));
        }

        @Test
        @DisplayName("should fail with usage error for invalid dominant winner over second ratio")
        void shouldFailWithUsageErrorForInvalidDominantWinnerOverSecondRatio() throws Exception {
            final Path inputFile = createMinimalDictionaryFile("invalid-ratio.txt");

            final CommandResult result = runWithCapturedStandardError("--input", inputFile.toString(), "--output",
                    temporaryDirectory.resolve("out.dat.gz").toString(), "--reduction-mode", validReductionModeName(),
                    "--dominant-winner-over-second-ratio", "invalid");

            assertAll(
                    () -> assertEquals(2, result.exitCode(),
                            "A non-integer dominant winner ratio must be treated as usage error."),
                    () -> assertTrue(result.standardError().contains("--dominant-winner-over-second-ratio"),
                            "The diagnostic message must identify the invalid numeric option."),
                    () -> assertTrue(result.standardError().contains("invalid"),
                            "The diagnostic message should include the invalid value."),
                    () -> assertTrue(result.standardError().contains("Usage:"),
                            "Usage help must be printed for invalid invocation."));
        }

        @Test
        @DisplayName("should fail with usage error when option value is missing")
        void shouldFailWithUsageErrorWhenOptionValueIsMissing() {
            final CommandResult result = runWithCapturedStandardError("--input");

            assertAll(
                    () -> assertEquals(2, result.exitCode(), "Missing option values must be treated as usage errors."),
                    () -> assertTrue(result.standardError().contains("Missing value for --input."),
                            "The diagnostic message must identify the missing option value."),
                    () -> assertTrue(result.standardError().contains("Usage:"),
                            "Usage help must be printed for invalid invocation."));
        }
    }

    @Test
    @DisplayName("should fail with processing error when input file does not exist")
    void shouldFailWithProcessingErrorWhenInputFileDoesNotExist() {
        final Path missingInputFile = temporaryDirectory.resolve("missing-dictionary.txt");
        final Path outputFile = temporaryDirectory.resolve("out.dat.gz");

        final CommandResult result = runWithCapturedStandardError("--input", missingInputFile.toString(), "--output",
                outputFile.toString(), "--reduction-mode", validReductionModeName());

        assertAll(
                () -> assertEquals(1, result.exitCode(), "Missing input file must be reported as processing failure."),
                () -> assertTrue(result.standardError().contains("Compilation failed:"),
                        "Processing failures must be reported to standard error."),
                () -> assertFalse(Files.exists(outputFile),
                        "The output file must not be created when the input file cannot be read."));
    }

    /**
     * Returns a valid reduction mode name from the current project enum.
     *
     * @return name of a valid reduction mode
     */
    private static String validReductionModeName() {
        return ReductionMode.values()[0].name();
    }

    /**
     * Creates a minimal valid dictionary file for CLI execution.
     *
     * @param fileName target file name
     * @return path to the created file
     * @throws Exception if the file cannot be written
     */
    private Path createMinimalDictionaryFile(final String fileName) throws Exception {
        final Path inputFile = temporaryDirectory.resolve(fileName);

        final String content = "" + "# minimal dictionary for CLI tests\n" + "run running runs runner\n"
                + "walk walking walks walked\n";

        Files.writeString(inputFile, content, StandardCharsets.UTF_8);
        return inputFile;
    }

    /**
     * Executes {@link Compile#run(String...)} while capturing {@code System.err}.
     *
     * @param arguments CLI arguments
     * @return captured command result
     */
    private static CommandResult runWithCapturedStandardError(final String... arguments) {
        final PrintStream originalStandardError = System.err;
        final ByteArrayOutputStream capturedStandardError = new ByteArrayOutputStream();

        try (PrintStream replacementStandardError = new PrintStream(capturedStandardError, true,
                StandardCharsets.UTF_8)) {
            System.setErr(replacementStandardError);
            final int exitCode = Compile.run(arguments);
            replacementStandardError.flush();
            return new CommandResult(exitCode, capturedStandardError.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(originalStandardError);
        }
    }

    /**
     * Immutable captured CLI execution result.
     *
     * @param exitCode      process-style exit code
     * @param standardError captured standard error
     */
    private record CommandResult(int exitCode, String standardError) {
        // No additional members.
    }
}
