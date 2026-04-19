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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link PatchCommandEncoder}.
 *
 * <p>
 * The suite verifies both major public responsibilities of the encoder:
 * generation of compact patch commands and application of those commands back
 * to source terms.
 * </p>
 *
 * <p>
 * The implementation intentionally exposes some historical compatibility
 * behavior, especially when malformed patch commands cause index-related
 * failures during patch application. Those cases are covered explicitly so that
 * future refactoring does not silently alter externally observable semantics.
 * </p>
 */
@DisplayName("PatchCommandEncoder")
@Tag("unit")
@Tag("stemmer")
@Tag("patch")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatchCommandEncoderTest {

    /**
     * Provides representative source-target pairs for round-trip validation.
     *
     * @return test arguments
     */
    private static Stream<Arguments> provideRoundTripPairs() {
        return Stream.of(
                // 1
                Arguments.of(1, "", ""),
                // 2
                Arguments.of(2, "a", "a"),
                // 3
                Arguments.of(3, "a", "b"),
                // 4
                Arguments.of(4, "ab", "ab"),
                // 5
                Arguments.of(5, "ab", "abc"),
                // 6
                Arguments.of(6, "abc", "ab"),
                // 7
                Arguments.of(7, "teacher", "teach"),
                // 8
                Arguments.of(8, "running", "run"),
                // 9
                Arguments.of(9, "cities", "city"),
                // 10
                Arguments.of(10, "walked", "walk"),
                // 11
                Arguments.of(11, "redo", "undo"),
                // 12
                Arguments.of(12, "stemming", "stem"),
                // 13
                Arguments.of(13, "abcdef", "azced"),
                // 14
                Arguments.of(14, "x", ""),
                // 15
                Arguments.of(15, "mississippi", "missouri"),
                // 16
                Arguments.of(16, "transformation", "transform"),
                // 17
                Arguments.of(17, "preprocessing", "process"),
                // 18
                Arguments.of(18, "internationalization", "i18n"),
                // 19
                Arguments.of(19, "bookkeeper", "bookkeeping"));
    }

    /**
     * Provides explicit patch application cases.
     *
     * @return test arguments
     */
    private static Stream<Arguments> provideApplyCases() {
        return Stream.of(
                // 1
                Arguments.of(1, "teacher", "Db", "teach"),
                // 2
                Arguments.of(2, "abc", "Ic", "abcc"),
                // 3
                Arguments.of(3, "abc", "Rx", "abx"),
                // 4
                Arguments.of(4, "abc", "-bRx", "xbc"),
                // 5
                Arguments.of(5, "abcd", "Dc", "a"),
                // 6
                Arguments.of(6, "abcd", "-c", "abcd"),
                // 7
                Arguments.of(7, "kitten", "DbIg", "kittg"),
                // 8
                Arguments.of(8, "", "Ix", "x"),
                // 9
                Arguments.of(9, "", "IbIa", "ab"),
                // 10
                Arguments.of(10, "teacher", PatchCommandEncoder.NOOP_PATCH, "teacher"));
    }

    /**
     * Provides malformed or index-invalid patch inputs that must preserve the
     * original source according to the implementation contract.
     *
     * @return test arguments
     */
    private static Stream<Arguments> provideMalformedPatchCases() {
        return Stream.of(
                // 1
                Arguments.of(1, "abc", "Dz"),
                // 2
                Arguments.of(2, "abc", "-z"),
                // 3
                Arguments.of(3, "abc", "R"),
                // 4
                Arguments.of(4, "abc", "I"),
                // 5
                Arguments.of(5, "abc", "D"),
                // 6
                Arguments.of(6, "abc", "-"),
                // 7
                Arguments.of(7, "abc", "IuDz"),
                // 8
                Arguments.of(8, "", "Da"),
                // 9
                Arguments.of(9, "", "-a"),
                // 10
                Arguments.of(10, "", "Ra"),
                // 11
                Arguments.of(11, "abc", "D`"),
                // 12
                Arguments.of(12, "abc", "-`"),
                // 13
                Arguments.of(13, "", "D`"));
    }

    /**
     * Provides representative source-target pairs for mirrored-orientation tests.
     *
     * @return test arguments
     */
    private static Stream<Arguments> provideReversedRoundTripPairs() {
        return Stream.of(
                // 1
                Arguments.of(1, "", ""),
                // 2
                Arguments.of(2, "a", "a"),
                // 3
                Arguments.of(3, "a", "b"),
                // 4
                Arguments.of(4, "teacher", "teach"),
                // 5
                Arguments.of(5, "running", "run"),
                // 6
                Arguments.of(6, "cities", "city"),
                // 7
                Arguments.of(7, "walked", "walk"),
                // 8
                Arguments.of(8, "redo", "undo"),
                // 9
                Arguments.of(9, "stemming", "stem"),
                // 10
                Arguments.of(10, "abcdef", "azced"),
                // 11
                Arguments.of(11, "mississippi", "missouri"),
                // 12
                Arguments.of(12, "transformation", "transform"),
                // 13
                Arguments.of(13, "preprocessing", "process"),
                // 14
                Arguments.of(14, "bookkeeper", "bookkeeping"),
                // 15
                Arguments.of(15, "", "x"),
                // 16
                Arguments.of(16, "", "ab"),
                // 17
                Arguments.of(17, "", "stem"));
    }

    /**
     * Returns a reversed copy of the supplied text.
     *
     * @param text input text
     * @return reversed text
     */
    private static String reverse(String text) {
        return new StringBuilder(text).reverse().toString();
    }

    /**
     * Tests constructor validation and basic instantiation behavior.
     */
    @Nested
    @DisplayName("construction")
    @Tag("constructor")
    class ConstructionTests {

        /**
         * Verifies that the default constructor creates a usable encoder instance.
         */
        @Test
        @DisplayName("creates encoder with default cost model")
        void shouldCreateEncoderWithDefaultCostModel() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            assertNotNull(encoder);
            assertEquals("teach", PatchCommandEncoder.apply("teacher", encoder.encode("teacher", "teach")));
        }

        /**
         * Verifies that a negative insert cost is rejected.
         */
        @Test
        @DisplayName("rejects negative insert cost")
        void shouldRejectNegativeInsertCost() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> new PatchCommandEncoder(-1, 1, 1, 0));

            assertEquals("insertCost must be non-negative.", exception.getMessage());
        }

        /**
         * Verifies that a negative delete cost is rejected.
         */
        @Test
        @DisplayName("rejects negative delete cost")
        void shouldRejectNegativeDeleteCost() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> new PatchCommandEncoder(1, -1, 1, 0));

            assertEquals("deleteCost must be non-negative.", exception.getMessage());
        }

        /**
         * Verifies that a negative replace cost is rejected.
         */
        @Test
        @DisplayName("rejects negative replace cost")
        void shouldRejectNegativeReplaceCost() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> new PatchCommandEncoder(1, 1, -1, 0));

            assertEquals("replaceCost must be non-negative.", exception.getMessage());
        }

        /**
         * Verifies that a negative match cost is rejected.
         */
        @Test
        @DisplayName("rejects negative match cost")
        void shouldRejectNegativeMatchCost() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> new PatchCommandEncoder(1, 1, 1, -1));

            assertEquals("matchCost must be non-negative.", exception.getMessage());
        }
    }

    /**
     * Tests {@link PatchCommandEncoder#encode(String, String)}.
     */
    @Nested
    @DisplayName("encode(String, String)")
    @Tag("encode")
    class EncodeTests {

        /**
         * Verifies that trailing SKIP instructions are omitted from the generated patch
         * command because they do not affect reconstruction.
         */
        @Test
        @DisplayName("does not emit trailing SKIP instructions into patch command")
        void shouldNotEmitTrailingSkipInstructionsIntoPatchCommand() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            String patch = encoder.encode("abcd", "ab");

            assertAll(() -> assertNotNull(patch), () -> assertEquals("Db", patch),
                    () -> assertEquals("ab", PatchCommandEncoder.apply("abcd", patch)), () -> assertEquals(-1,
                            patch.indexOf('-'), () -> "Patch must not contain a trailing SKIP instruction: " + patch));
        }

        /**
         * Verifies that a null source yields a null patch.
         */
        @Test
        @DisplayName("returns null when source is null")
        void shouldReturnNullWhenSourceIsNull() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            String patch = encoder.encode(null, "target");

            assertNull(patch);
        }

        /**
         * Verifies that a null target yields a null patch.
         */
        @Test
        @DisplayName("returns null when target is null")
        void shouldReturnNullWhenTargetIsNull() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            String patch = encoder.encode("source", null);

            assertNull(patch);
        }

        /**
         * Verifies that equal words always produce the canonical identity patch.
         */
        @Test
        @DisplayName("returns canonical NOOP patch for equal words")
        void shouldReturnCanonicalNoopPatchForEqualWords() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            String patch = encoder.encode("teacher", "teacher");

            assertAll(() -> assertNotNull(patch), () -> assertEquals(PatchCommandEncoder.NOOP_PATCH, patch),
                    () -> assertEquals("teacher", PatchCommandEncoder.apply("teacher", patch)));
        }

        /**
         * Verifies deterministic identity encoding for empty words.
         */
        @Test
        @DisplayName("returns canonical NOOP patch for equal empty words")
        void shouldReturnCanonicalNoopPatchForEqualEmptyWords() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            String patch = encoder.encode("", "");

            assertAll(() -> assertEquals(PatchCommandEncoder.NOOP_PATCH, patch),
                    () -> assertEquals("", PatchCommandEncoder.apply("", patch)));
        }

        /**
         * Verifies round-trip reconstruction on representative pairs.
         *
         * @param caseId numeric case identifier
         * @param source source word
         * @param target target word
         */
        @ParameterizedTest(name = "[{index}] case {0}: {1} -> {2}")
        @MethodSource("org.egothor.stemmer.PatchCommandEncoderTest#provideRoundTripPairs")
        @DisplayName("produces patches that reconstruct the target")
        void shouldReconstructTargetForRoundTripPairs(int caseId, String source, String target) {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            String patch = encoder.encode(source, target);
            String reconstructed = PatchCommandEncoder.apply(source, patch);

            assertAll(
                    () -> assertNotNull(patch,
                            () -> "Case " + caseId + " unexpectedly produced a null patch for source='" + source
                                    + "', target='" + target + "'."),
                    () -> assertEquals(target, reconstructed, () -> "Case " + caseId + " failed for source='" + source
                            + "', target='" + target + "', patch='" + patch + "'."));
        }

        /**
         * Verifies that one encoder instance remains correct across varying matrix
         * sizes.
         */
        @Test
        @DisplayName("remains correct when reused across different input sizes")
        void shouldRemainCorrectWhenReusedAcrossDifferentInputSizes() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            assertAll(
                    () -> assertEquals("transformation",
                            PatchCommandEncoder.apply("transform", encoder.encode("transform", "transformation"))),
                    () -> assertEquals("cat", PatchCommandEncoder.apply("cats", encoder.encode("cats", "cat"))),
                    () -> assertEquals("book", PatchCommandEncoder.apply("back", encoder.encode("back", "book"))),
                    () -> assertEquals("", PatchCommandEncoder.apply("x", encoder.encode("x", ""))));
        }

        /**
         * Verifies that custom operation costs still produce a usable patch.
         */
        @Test
        @DisplayName("supports custom operation costs")
        void shouldSupportCustomOperationCosts() {
            PatchCommandEncoder encoder = new PatchCommandEncoder(1, 1, 2, 0);

            String patch = encoder.encode("teacher", "teach");
            String reconstructed = PatchCommandEncoder.apply("teacher", patch);

            assertAll(() -> assertNotNull(patch), () -> assertEquals("teach", reconstructed));
        }
    }

    /**
     * Tests {@link PatchCommandEncoder#apply(String, String)}.
     */
    @Nested
    @DisplayName("apply(String, String)")
    @Tag("apply")
    class ApplyTests {

        /**
         * Verifies that a null source returns null.
         */
        @Test
        @DisplayName("returns null when source is null")
        void shouldReturnNullWhenSourceIsNull() {
            assertNull(PatchCommandEncoder.apply(null, "Da"));
        }

        /**
         * Verifies that a null patch returns the original source.
         */
        @Test
        @DisplayName("returns original source when patch is null")
        void shouldReturnSourceWhenPatchIsNull() {
            String source = "teacher";

            assertSame(source, PatchCommandEncoder.apply(source, null));
        }

        /**
         * Verifies that an empty patch returns the original source.
         */
        @Test
        @DisplayName("returns original source when patch is empty")
        void shouldReturnSourceWhenPatchIsEmpty() {
            String source = "teacher";

            assertSame(source, PatchCommandEncoder.apply(source, ""));
        }

        /**
         * Verifies that the canonical NOOP patch returns the original source.
         */
        @Test
        @DisplayName("returns original source when patch is canonical NOOP")
        void shouldReturnSourceWhenPatchIsCanonicalNoop() {
            String source = "teacher";

            assertSame(source, PatchCommandEncoder.apply(source, PatchCommandEncoder.NOOP_PATCH));
        }

        /**
         * Verifies explicit patch application cases.
         *
         * @param caseId   numeric case identifier
         * @param source   source word
         * @param patch    patch command
         * @param expected expected transformed word
         */
        @ParameterizedTest(name = "[{index}] case {0}: apply({1}, {2}) -> {3}")
        @MethodSource("org.egothor.stemmer.PatchCommandEncoderTest#provideApplyCases")
        @DisplayName("applies explicit patch commands correctly")
        void shouldApplyExplicitPatchCommandsCorrectly(int caseId, String source, String patch, String expected) {
            assertEquals(expected, PatchCommandEncoder.apply(source, patch),
                    () -> "Case " + caseId + " failed for source='" + source + "', patch='" + patch + "'.");
        }

        /**
         * Verifies that unsupported opcodes fail fast.
         */
        @Test
        @DisplayName("throws IllegalArgumentException for unsupported opcode")
        void shouldThrowForUnsupportedOpcode() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> PatchCommandEncoder.apply("abc", "Xa"));

            assertEquals("Unsupported patch opcode: X", exception.getMessage());
        }

        /**
         * Verifies that an unsupported NOOP argument fails fast.
         */
        @Test
        @DisplayName("throws IllegalArgumentException for unsupported NOOP argument")
        void shouldThrowForUnsupportedNoopArgument() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> PatchCommandEncoder.apply("abc", "Nb"));

            assertEquals("Unsupported NOOP patch argument: b", exception.getMessage());
        }

        /**
         * Verifies malformed and index-invalid compatibility behavior.
         *
         * @param caseId         numeric case identifier
         * @param source         original source
         * @param malformedPatch malformed patch
         */
        @ParameterizedTest(name = "[{index}] case {0}: malformed patch {2} preserves {1}")
        @MethodSource("org.egothor.stemmer.PatchCommandEncoderTest#provideMalformedPatchCases")
        @DisplayName("returns the original source for malformed or index-invalid patch commands")
        void shouldReturnOriginalSourceForMalformedOrIndexInvalidPatchCommands(int caseId, String source,
                String malformedPatch) {
            assertEquals(source, PatchCommandEncoder.apply(source, malformedPatch), () -> "Case " + caseId
                    + " failed for source='" + source + "', malformedPatch='" + malformedPatch + "'.");
        }
    }

    /**
     * Tests representative stemming-style scenarios.
     */
    @Nested
    @DisplayName("stemming-oriented scenarios")
    @Tag("regression")
    class StemmingScenarioTests {

        /**
         * Verifies deletion-heavy suffix stripping.
         */
        @Test
        @DisplayName("handles deletion-heavy suffix stripping")
        void shouldHandleDeletionHeavySuffixStripping() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            String patch = encoder.encode("teacher", "teach");

            assertEquals("teach", PatchCommandEncoder.apply("teacher", patch));
        }

        /**
         * Verifies plural to singular transformation.
         */
        @Test
        @DisplayName("handles plural to singular transformation")
        void shouldHandlePluralToSingularTransformation() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            String patch = encoder.encode("cities", "city");

            assertEquals("city", PatchCommandEncoder.apply("cities", patch));
        }

        /**
         * Verifies reduction to a shorter derivational stem.
         */
        @Test
        @DisplayName("handles derivational reduction to a shorter stem")
        void shouldHandleDerivationalReductionToShorterStem() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            String patch = encoder.encode("stemming", "stem");

            assertEquals("stem", PatchCommandEncoder.apply("stemming", patch));
        }

        /**
         * Verifies single-character replacement.
         */
        @Test
        @DisplayName("handles single-character replacement")
        void shouldHandleSingleCharacterReplacement() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            String patch = encoder.encode("a", "z");

            assertEquals("z", PatchCommandEncoder.apply("a", patch));
        }
    }

    /**
     * Tests reversed-word processing.
     */
    @Nested
    @DisplayName("reversed-word processing")
    @Tag("reverse")
    class ReversedWordProcessingTests {

        /**
         * Verifies reconstruction for reversed source and target pairs.
         *
         * @param caseId numeric case identifier
         * @param source source word
         * @param target target word
         */
        @ParameterizedTest(name = "[{index}] case {0}: reverse({1}) -> reverse({2})")
        @MethodSource("org.egothor.stemmer.PatchCommandEncoderTest#provideReversedRoundTripPairs")
        @DisplayName("reconstructs reversed targets from reversed sources")
        void shouldReconstructReversedTargetsFromReversedSources(int caseId, String source, String target) {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            String reversedSource = reverse(source);
            String reversedTarget = reverse(target);

            String patch = encoder.encode(reversedSource, reversedTarget);
            String reconstructed = PatchCommandEncoder.apply(reversedSource, patch);

            assertAll(
                    () -> assertNotNull(patch,
                            () -> "Case " + caseId + " unexpectedly produced a null patch for reversedSource='"
                                    + reversedSource + "', reversedTarget='" + reversedTarget + "'."),
                    () -> assertEquals(reversedTarget, reconstructed,
                            () -> "Case " + caseId + " failed for reversedSource='" + reversedSource
                                    + "', reversedTarget='" + reversedTarget + "', patch='" + patch + "'."));
        }

        /**
         * Verifies representative mirrored stemming transformations.
         */
        @Test
        @DisplayName("handles mirrored stemming transformations")
        void shouldHandleMirroredStemmingTransformations() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            assertAll(
                    () -> assertEquals(reverse("teach"),
                            PatchCommandEncoder.apply(reverse("teacher"),
                                    encoder.encode(reverse("teacher"), reverse("teach")))),
                    () -> assertEquals(reverse("run"),
                            PatchCommandEncoder.apply(reverse("running"),
                                    encoder.encode(reverse("running"), reverse("run")))),
                    () -> assertEquals(reverse("city"),
                            PatchCommandEncoder.apply(reverse("cities"),
                                    encoder.encode(reverse("cities"), reverse("city")))),
                    () -> assertEquals(reverse("walk"), PatchCommandEncoder.apply(reverse("walked"),
                            encoder.encode(reverse("walked"), reverse("walk")))));
        }

        /**
         * Verifies encoder reuse on reversed words of different sizes.
         */
        @Test
        @DisplayName("remains correct when reused on reversed words of different sizes")
        void shouldRemainCorrectWhenReusedOnReversedWordsOfDifferentSizes() {
            PatchCommandEncoder encoder = new PatchCommandEncoder();

            assertAll(
                    () -> assertEquals(reverse("transformation"),
                            PatchCommandEncoder.apply(reverse("transform"),
                                    encoder.encode(reverse("transform"), reverse("transformation")))),
                    () -> assertEquals(reverse("cat"),
                            PatchCommandEncoder.apply(reverse("cats"),
                                    encoder.encode(reverse("cats"), reverse("cat")))),
                    () -> assertEquals(reverse("book"),
                            PatchCommandEncoder.apply(reverse("back"),
                                    encoder.encode(reverse("back"), reverse("book")))),
                    () -> assertEquals("",
                            PatchCommandEncoder.apply(reverse("x"), encoder.encode(reverse("x"), reverse("")))));
        }
    }

    /**
     * Verifies correctness under mirrored input orientation.
     *
     * @param caseId numeric case identifier
     * @param source source word
     * @param target target word
     */
    @ParameterizedTest(name = "[{index}] case {0}: mirrored consistency for {1} -> {2}")
    @MethodSource("org.egothor.stemmer.PatchCommandEncoderTest#provideReversedRoundTripPairs")
    @DisplayName("preserves correctness under mirrored input orientation")
    void shouldPreserveCorrectnessUnderMirroredInputOrientation(int caseId, String source, String target) {
        PatchCommandEncoder encoder = new PatchCommandEncoder();

        String normalPatch = encoder.encode(source, target);
        String normalResult = PatchCommandEncoder.apply(source, normalPatch);

        String reversedSource = reverse(source);
        String reversedTarget = reverse(target);
        String reversedPatch = encoder.encode(reversedSource, reversedTarget);
        String reversedResult = PatchCommandEncoder.apply(reversedSource, reversedPatch);

        assertAll(
                () -> assertEquals(target, normalResult,
                        () -> "Case " + caseId + " failed in normal orientation for source='" + source + "', target='"
                                + target + "', patch='" + normalPatch + "'."),
                () -> assertEquals(reversedTarget, reversedResult,
                        () -> "Case " + caseId + " failed in mirrored orientation for reversedSource='" + reversedSource
                                + "', reversedTarget='" + reversedTarget + "', patch='" + reversedPatch + "'."));
    }
}
