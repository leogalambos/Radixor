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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link CompiledPatchCommand}.
 */
@DisplayName("CompiledPatchCommand")
@Tag("unit")
@Tag("stemmer")
@Tag("patch")
@SuppressWarnings("deprecation")
final class CompiledPatchCommandTest {

    /**
     * Provides representative source-target pairs for compiled command validation.
     *
     * @return test arguments
     */
    private static Stream<Arguments> provideRoundTripPairs() {
        return Stream.of(
                Arguments.of(WordTraversalDirection.BACKWARD, "", ""),
                Arguments.of(WordTraversalDirection.BACKWARD, "a", "a"),
                Arguments.of(WordTraversalDirection.BACKWARD, "a", "b"),
                Arguments.of(WordTraversalDirection.BACKWARD, "abc", "ab"),
                Arguments.of(WordTraversalDirection.BACKWARD, "ab", "abc"),
                Arguments.of(WordTraversalDirection.BACKWARD, "teacher", "teach"),
                Arguments.of(WordTraversalDirection.BACKWARD, "running", "run"),
                Arguments.of(WordTraversalDirection.BACKWARD, "cities", "city"),
                Arguments.of(WordTraversalDirection.BACKWARD, "mississippi", "missouri"),
                Arguments.of(WordTraversalDirection.FORWARD, "", ""),
                Arguments.of(WordTraversalDirection.FORWARD, "a", "a"),
                Arguments.of(WordTraversalDirection.FORWARD, "a", "b"),
                Arguments.of(WordTraversalDirection.FORWARD, "abc", "bc"),
                Arguments.of(WordTraversalDirection.FORWARD, "bc", "abc"),
                Arguments.of(WordTraversalDirection.FORWARD, "transformation", "transform"),
                Arguments.of(WordTraversalDirection.FORWARD, "cities", "city"));
    }

    /**
     * Provides malformed compatibility patch commands.
     *
     * @return test arguments
     */
    private static Stream<Arguments> providePreservePatchCommands() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(""),
                Arguments.of("D`"),
                Arguments.of("-`"),
                Arguments.of("DaX"));
    }

    /**
     * Provides compound patch commands that stress direct compiled execution.
     *
     * @return test arguments
     */
    private static Stream<Arguments> provideCompoundPatchCommands() {
        return Stream.of(
                Arguments.of(WordTraversalDirection.FORWARD, "abcdef", "IaIbIc"),
                Arguments.of(WordTraversalDirection.FORWARD, "abcdef", "-bDcIxRy"),
                Arguments.of(WordTraversalDirection.FORWARD, "abcdef", "DbIxIy-cRz"),
                Arguments.of(WordTraversalDirection.FORWARD, "abcdef", "-z"),
                Arguments.of(WordTraversalDirection.FORWARD, "abcdef", "-zIx"),
                Arguments.of(WordTraversalDirection.BACKWARD, "abcdef", "IxIyIz"),
                Arguments.of(WordTraversalDirection.BACKWARD, "abcdef", "-bDcIxRy"),
                Arguments.of(WordTraversalDirection.BACKWARD, "abcdef", "DbIxIy-cRz"),
                Arguments.of(WordTraversalDirection.BACKWARD, "abcdef", "-z"),
                Arguments.of(WordTraversalDirection.BACKWARD, "abcdef", "-zIx"),
                Arguments.of(WordTraversalDirection.BACKWARD, "a", "DaDa"));
    }

    /**
     * Provides patch commands whose length delta would produce an empty stem.
     *
     * @return test arguments
     */
    private static Stream<Arguments> provideEmptyStemPatchCommands() {
        return Stream.of(
                Arguments.of(WordTraversalDirection.BACKWARD, "a", "Da"),
                Arguments.of(WordTraversalDirection.FORWARD, "a", "Da"),
                Arguments.of(WordTraversalDirection.FORWARD, "a", "DaDa"));
    }

    /**
     * Verifies that representative serialized commands compile to concrete command
     * classes instead of one universal runtime-dispatched command shape.
     */
    @Test
    @DisplayName("compiles representative commands to concrete command classes")
    void shouldCompileRepresentativeCommandsToConcreteClasses() {
        assertAll(
                () -> assertEquals("DeleteSuffixCommand",
                        CompiledPatchCommand.compile("Da", WordTraversalDirection.BACKWARD)
                                .getClass().getSimpleName()),
                () -> assertEquals("DeletePrefixCommand",
                        CompiledPatchCommand.compile("Da", WordTraversalDirection.FORWARD)
                                .getClass().getSimpleName()),
                () -> assertEquals("AppendCharacterCommand",
                        CompiledPatchCommand.compile("Ix", WordTraversalDirection.BACKWARD)
                                .getClass().getSimpleName()),
                () -> assertEquals("PrependCharacterCommand",
                        CompiledPatchCommand.compile("Ix", WordTraversalDirection.FORWARD)
                                .getClass().getSimpleName()),
                () -> assertEquals("ReplaceLastCharacterCommand",
                        CompiledPatchCommand.compile("Rx", WordTraversalDirection.BACKWARD)
                                .getClass().getSimpleName()),
                () -> assertEquals("ReplaceFirstCharacterCommand",
                        CompiledPatchCommand.compile("Rx", WordTraversalDirection.FORWARD)
                                .getClass().getSimpleName()),
                () -> assertEquals("BackwardCompoundCommand",
                        CompiledPatchCommand.compile("-aDa", WordTraversalDirection.BACKWARD)
                                .getClass().getSimpleName()),
                () -> assertEquals("ForwardCompoundCommand",
                        CompiledPatchCommand.compile("-aDa", WordTraversalDirection.FORWARD)
                                .getClass().getSimpleName()));
    }

    /**
     * Verifies the no-op fast-path marker used by high-throughput callers.
     */
    @Test
    @DisplayName("marks only all-source preserve commands as preserving")
    void shouldMarkOnlyAllSourcePreserveCommandsAsPreserving() {
        assertAll(
                () -> assertEquals(true,
                        CompiledPatchCommand.compile(null, WordTraversalDirection.BACKWARD).preservesAllSources()),
                () -> assertEquals(true,
                        CompiledPatchCommand.compile("", WordTraversalDirection.BACKWARD).preservesAllSources()),
                () -> assertEquals(true,
                        CompiledPatchCommand.compile("Na", WordTraversalDirection.BACKWARD).preservesAllSources()),
                () -> assertEquals(true,
                        CompiledPatchCommand.compile("-a", WordTraversalDirection.BACKWARD).preservesAllSources()),
                () -> assertEquals(false,
                        CompiledPatchCommand.compile("Da", WordTraversalDirection.BACKWARD).preservesAllSources()),
                () -> assertEquals(false,
                        CompiledPatchCommand.compile("Ix", WordTraversalDirection.FORWARD).preservesAllSources()),
                () -> assertEquals(false,
                        CompiledPatchCommand.compile("-aDa", WordTraversalDirection.BACKWARD)
                                .preservesAllSources()));
    }

    /**
     * Verifies that compiled commands match the string interpreter.
     *
     * @param traversalDirection traversal direction
     * @param source             source word
     * @param target             target word
     */
    @ParameterizedTest
    @MethodSource("provideRoundTripPairs")
    @DisplayName("matches interpreted patch application")
    void shouldMatchInterpretedPatchApplication(final WordTraversalDirection traversalDirection, final String source,
            final String target) {
        final PatchCommandEncoder encoder = PatchCommandEncoder.builder()
                .traversalDirection(traversalDirection)
                .build();
        final String patch = encoder.encode(source, target);
        final CompiledPatchCommand compiled = encoder.compile(patch);
        final String expected = PatchCommandEncoder.apply(source, patch, traversalDirection);

        final char[] sequenceOutput = new char[Math.max(source.length(), expected.length()) + 8];
        final int sequenceLength = compiled.applyTo(source, sequenceOutput, 2, sequenceOutput.length - 2);

        final char[] sourceArray = source.toCharArray();
        final char[] arrayOutput = new char[Math.max(source.length(), expected.length()) + 8];
        final int arrayLength = compiled.applyTo(sourceArray, 0, sourceArray.length, arrayOutput, 1,
                arrayOutput.length - 1);

        assertAll(
                () -> assertEquals(expected, compiled.apply(source)),
                () -> assertEquals(expected.length(), sequenceLength),
                () -> assertEquals(expected, new String(sequenceOutput, 2, sequenceLength)),
                () -> assertEquals(expected.length(), arrayLength),
                () -> assertEquals(expected, new String(arrayOutput, 1, arrayLength)));
    }

    /**
     * Verifies compound direct execution against the compatibility interpreter.
     *
     * @param traversalDirection traversal direction
     * @param source             source word
     * @param patch              serialized compound patch command
     */
    @ParameterizedTest
    @MethodSource("provideCompoundPatchCommands")
    @DisplayName("matches interpreted compound patch application")
    void shouldMatchInterpretedCompoundPatchApplication(final WordTraversalDirection traversalDirection,
            final String source, final String patch) {
        final CompiledPatchCommand compiled = CompiledPatchCommand.compile(patch, traversalDirection);
        final String expected = PatchCommandEncoder.apply(source, patch, traversalDirection);
        final char[] sequenceOutput = new char[Math.max(source.length(), expected.length()) + 8];
        final char[] sourceArray = source.toCharArray();
        final char[] arrayOutput = new char[Math.max(source.length(), expected.length()) + 8];

        final int sequenceLength = compiled.applyTo(source, sequenceOutput, 2, sequenceOutput.length - 2);
        final int arrayLength = compiled.applyTo(sourceArray, 0, sourceArray.length, arrayOutput, 1,
                arrayOutput.length - 1);

        assertAll(
                () -> assertEquals(expected, compiled.apply(source)),
                () -> assertEquals(expected.length(), sequenceLength),
                () -> assertEquals(expected, new String(sequenceOutput, 2, sequenceLength)),
                () -> assertEquals(expected.length(), arrayLength),
                () -> assertEquals(expected, new String(arrayOutput, 1, arrayLength)));
    }

    /**
     * Verifies that the compiled hot path never produces an empty stem.
     *
     * @param traversalDirection traversal direction
     * @param source             source word
     * @param patch              serialized patch command
     */
    @ParameterizedTest
    @MethodSource("provideEmptyStemPatchCommands")
    @DisplayName("preserves source when a patch would produce an empty stem")
    void shouldPreserveSourceWhenPatchWouldProduceEmptyStem(final WordTraversalDirection traversalDirection,
            final String source, final String patch) {
        final CompiledPatchCommand compiled = CompiledPatchCommand.compile(patch, traversalDirection);
        final char[] sequenceOutput = new char[source.length() + 4];
        final char[] sourceArray = source.toCharArray();
        final char[] arrayOutput = new char[source.length() + 4];

        final int sequenceLength = compiled.applyTo(source, sequenceOutput, 1, sequenceOutput.length - 1);
        final int arrayLength = compiled.applyTo(sourceArray, 0, sourceArray.length, arrayOutput, 2,
                arrayOutput.length - 2);

        assertAll(
                () -> assertSame(source, compiled.apply(source)),
                () -> assertEquals(source.length(), sequenceLength),
                () -> assertEquals(source, new String(sequenceOutput, 1, sequenceLength)),
                () -> assertEquals(source.length(), arrayLength),
                () -> assertEquals(source, new String(arrayOutput, 2, arrayLength)));
    }

    /**
     * Verifies preserve-only patch commands.
     *
     * @param patchCommand serialized patch command
     */
    @ParameterizedTest
    @MethodSource("providePreservePatchCommands")
    @DisplayName("preserves source for interpreted preserve-only commands")
    void shouldPreserveSourceForPreserveOnlyCommands(final String patchCommand) {
        final String source = "teacher";
        final CompiledPatchCommand compiled = CompiledPatchCommand.compile(patchCommand, WordTraversalDirection.BACKWARD);

        assertSame(source, compiled.apply(source));
    }

    /**
     * Verifies insufficient output capacity reporting.
     */
    @ParameterizedTest
    @MethodSource("provideRoundTripPairs")
    @DisplayName("reports insufficient output capacity")
    void shouldReportInsufficientOutputCapacity(final WordTraversalDirection traversalDirection, final String source,
            final String target) {
        final PatchCommandEncoder encoder = PatchCommandEncoder.builder()
                .traversalDirection(traversalDirection)
                .build();
        final String patch = encoder.encode(source, target);
        final CompiledPatchCommand compiled = encoder.compile(patch);
        final String expected = compiled.apply(source);
        final char[] output = new char[Math.max(0, expected.length() - 1)];

        if (expected.isEmpty()) {
            assertEquals(0, compiled.applyTo(source, output, 0, output.length));
        } else {
            assertEquals(CompiledPatchCommand.APPLY_INSUFFICIENT_CAPACITY,
                    compiled.applyTo(source, output, 0, output.length));
        }
    }

    /**
     * Verifies compile-time rejection of unsupported serialized commands.
     */
    @ParameterizedTest
    @MethodSource("provideInvalidPatchCommands")
    @DisplayName("rejects unsupported patch commands")
    void shouldRejectUnsupportedPatchCommands(final String patchCommand) {
        assertThrows(IllegalArgumentException.class,
                () -> CompiledPatchCommand.compile(patchCommand, WordTraversalDirection.BACKWARD));
    }

    /**
     * Provides invalid patch commands.
     *
     * @return test arguments
     */
    private static Stream<Arguments> provideInvalidPatchCommands() {
        return Stream.of(
                Arguments.of("Xa"),
                Arguments.of("N`"),
                Arguments.of("DaN`"));
    }
}
