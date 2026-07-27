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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.egothor.stemmer.trie.CompiledNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

/**
 * Unit tests for {@link StemmerPatchTrieBinaryIO}.
 *
 * <p>
 * The test suite verifies the externally observable contract of the binary I/O
 * helper:
 * </p>
 * <ul>
 * <li>null-argument validation for all public overloads,</li>
 * <li>utility-class constructor behavior,</li>
 * <li>delegation to
 * {@link FrequencyTrie#writeTo(DataOutputStream, FrequencyTrie.ValueStreamCodec)},</li>
 * <li>delegation to
 * {@link FrequencyTrie#readFrom(DataInputStream, java.util.function.IntFunction, FrequencyTrie.ValueStreamCodec)},</li>
 * <li>GZip wrapping of persisted data,</li>
 * <li>filesystem convenience behavior such as parent directory creation,
 * and</li>
 * <li>propagation of malformed-input failures.</li>
 * </ul>
 *
 * <p>
 * These tests intentionally validate the helper in isolation and therefore rely
 * on Mockito static mocking for {@link FrequencyTrie#readFrom(...)}.
 * </p>
 */
@Tag("unit")
@Tag("io")
@Tag("persistence")
@Tag("serialization")
@Tag("trie")
@DisplayName("StemmerPatchTrieBinaryIO")
class StemmerPatchTrieBinaryIOTest {

    /**
     * Maximum invalid patch-command length retained verbatim in production
     * diagnostics.
     */
    private static final int DIAGNOSTIC_PATCH_BOUNDARY = 128;

    /**
     * Marker that introduces the original length after diagnostic truncation.
     */
    private static final String DIAGNOSTIC_TRUNCATION_MARKER = "... (length ";

    /**
     * Temporary directory provided by JUnit.
     */
    @TempDir
    Path temporaryDirectory;

    /**
     * Verifies that the utility-class constructor is inaccessible in practice and
     * fails with the documented assertion.
     *
     * @throws Exception if reflective access unexpectedly fails for a reason other
     *                   than the constructor throwing its assertion
     */
    @Test
    @DisplayName("Constructor should reject instantiation")
    void shouldRejectInstantiation() throws Exception {
        final Constructor<StemmerPatchTrieBinaryIO> constructor = StemmerPatchTrieBinaryIO.class
                .getDeclaredConstructor();
        constructor.setAccessible(true);

        final InvocationTargetException invocationTargetException = assertThrows(InvocationTargetException.class,
                constructor::newInstance, "Utility-class constructor must not allow instantiation.");

        final Throwable cause = invocationTargetException.getCause();

        assertAll(() -> assertNotNull(cause, "Constructor failure must expose the root cause."),
                () -> assertInstanceOf(AssertionError.class, cause, "Constructor must fail with AssertionError."),
                () -> assertEquals("No instances.", cause.getMessage(),
                        "Constructor must communicate the non-instantiability contract."));
    }

    /**
     * Tests for write operations.
     */
    @Nested
    @DisplayName("write(...)")
    @Tag("unit")
    @Tag("io")
    @Tag("trie")
    @Tag("persistence")
    class WriteTests {

        /**
         * Verifies null handling for all write overloads.
         */
        @Test
        @DisplayName("Should reject null arguments across all overloads")
        void shouldRejectNullArgumentsAcrossAllWriteOverloads() {
            @SuppressWarnings("unchecked")
            final FrequencyTrie<String> trie = mock(FrequencyTrie.class);
            final OutputStream outputStream = new ByteArrayOutputStream();
            final Path path = temporaryDirectory.resolve("stemmer.bin.gz");

            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> StemmerPatchTrieBinaryIO.write(null, path),
                            "write(FrequencyTrie, Path) must reject null trie."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.write(trie, (Path) null),
                            "write(FrequencyTrie, Path) must reject null path."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.write(null, "file.bin.gz"),
                            "write(FrequencyTrie, String) must reject null trie."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.write(trie, (String) null),
                            "write(FrequencyTrie, String) must reject null file name."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.write(null, outputStream),
                            "write(FrequencyTrie, OutputStream) must reject null trie."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.write(trie, (OutputStream) null),
                            "write(FrequencyTrie, OutputStream) must reject null output stream."));
        }

        /**
         * Verifies that the stream overload compresses the payload and delegates trie
         * serialization once.
         *
         * @throws IOException if the helper unexpectedly fails
         */
        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should compress output and delegate trie serialization")
        void shouldCompressOutputAndDelegateTrieSerialization() throws IOException {
            final FrequencyTrie<String> trie = mock(FrequencyTrie.class);
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            StemmerPatchTrieBinaryIO.write(trie, byteArrayOutputStream);

            verify(trie).writeTo(any(DataOutputStream.class), any(FrequencyTrie.ValueStreamCodec.class));
            verifyNoMoreInteractions(trie);

            final byte[] compressedBytes = byteArrayOutputStream.toByteArray();

            assertAll(
                    () -> assertTrue(compressedBytes.length > 2,
                            "Compressed output must contain at least the GZip header."),
                    () -> assertEquals(0x1f, compressedBytes[0] & 0xff, "First byte must match the GZip magic header."),
                    () -> assertEquals(0x8b, compressedBytes[1] & 0xff,
                            "Second byte must match the GZip magic header."));
        }

        /**
         * Verifies that the path overload creates missing parent directories and writes
         * a readable GZip payload.
         *
         * @throws IOException if the helper unexpectedly fails
         */
        @Test
        @DisplayName("Should create parent directories and write gzip file")
        void shouldCreateParentDirectoriesAndWriteGzipFile() throws IOException {
            @SuppressWarnings("unchecked")
            final FrequencyTrie<String> trie = mock(FrequencyTrie.class);
            final Path targetFile = temporaryDirectory.resolve("nested").resolve("deeper").resolve("stemmer.bin.gz");

            StemmerPatchTrieBinaryIO.write(trie, targetFile);

            assertAll(() -> assertTrue(Files.exists(targetFile), "Target file must be created."),
                    () -> assertTrue(Files.isDirectory(targetFile.getParent()),
                            "Missing parent directories must be created."));

            final byte[] bytes = Files.readAllBytes(targetFile);

            assertAll(() -> assertTrue(bytes.length > 2, "Persisted file must not be empty."),
                    () -> assertEquals(0x1f, bytes[0] & 0xff, "Persisted file must start with the GZip magic header."),
                    () -> assertEquals(0x8b, bytes[1] & 0xff, "Persisted file must start with the GZip magic header."));
        }

        /**
         * Verifies that the string-path overload delegates correctly to
         * filesystem-based persistence.
         *
         * @throws IOException if the helper unexpectedly fails
         */
        @Test
        @DisplayName("Should write to filesystem when file name string is used")
        void shouldWriteToFilesystemWhenFileNameStringIsUsed() throws IOException {
            @SuppressWarnings("unchecked")
            final FrequencyTrie<String> trie = mock(FrequencyTrie.class);
            final Path targetFile = temporaryDirectory.resolve("string-path-stemmer.bin.gz");

            StemmerPatchTrieBinaryIO.write(trie, targetFile.toString());

            assertAll(() -> assertTrue(Files.exists(targetFile), "String-based overload must create the target file."),
                    () -> assertTrue(Files.size(targetFile) > 0L,
                            "String-based overload must write non-empty output."));
        }

        /**
         * Verifies that the helper closes the supplied output stream because the
         * implementation owns the wrapping GZip/DataOutput streams in a
         * try-with-resources block.
         *
         * @throws IOException if the helper unexpectedly fails
         */
        @Test
        @DisplayName("Should close supplied output stream")
        void shouldCloseSuppliedOutputStream() throws IOException {
            @SuppressWarnings("unchecked")
            final FrequencyTrie<String> trie = mock(FrequencyTrie.class);
            final TrackingOutputStream trackingOutputStream = new TrackingOutputStream();

            StemmerPatchTrieBinaryIO.write(trie, trackingOutputStream);

            assertTrue(trackingOutputStream.isClosed(), "Output stream must be closed when write completes.");
        }

        /**
         * Verifies that write failures raised by the trie serializer are propagated
         * unchanged to the caller.
         *
         * @throws IOException if the mock setup unexpectedly fails
         */
        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should propagate write failure from trie serialization")
        void shouldPropagateWriteFailureFromTrieSerialization() throws IOException {
            final FrequencyTrie<String> trie = mock(FrequencyTrie.class);
            final IOException expectedException = new IOException("write failure");

            org.mockito.Mockito.doThrow(expectedException).when(trie).writeTo(any(DataOutputStream.class),
                    any(FrequencyTrie.ValueStreamCodec.class));

            final IOException actualException = assertThrows(IOException.class,
                    () -> StemmerPatchTrieBinaryIO.write(trie, new ByteArrayOutputStream()),
                    "Write-side serialization failures must be propagated unchanged.");

            assertSame(expectedException, actualException,
                    "The helper must propagate the original write exception instance.");
        }
    }

    /**
     * Tests for read operations.
     */
    @Nested
    @DisplayName("read(...)")
    @Tag("unit")
    @Tag("io")
    @Tag("trie")
    @Tag("persistence")
    class ReadTests {

        /**
         * Verifies null handling for all read overloads.
         */
        @Test
        @DisplayName("Should reject null arguments across all overloads")
        void shouldRejectNullArgumentsAcrossAllReadOverloads() {
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> StemmerPatchTrieBinaryIO.read((Path) null),
                            "read(Path) must reject null path."),
                    () -> assertThrows(NullPointerException.class, () -> StemmerPatchTrieBinaryIO.read((String) null),
                            "read(String) must reject null file name."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.read((Path) null, FrequencyTrie.DEFAULT_MAX_EXPANDED_INDEX),
                            "read(Path, int) must reject null path."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.read((String) null,
                                    FrequencyTrie.DEFAULT_MAX_EXPANDED_INDEX),
                            "read(String, int) must reject null file name."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.read((ByteArrayInputStream) null),
                            "read(InputStream) must reject null input stream."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.read((ByteArrayInputStream) null, FrequencyTrie.DEFAULT_MAX_EXPANDED_INDEX),
                            "read(InputStream, int) must reject null input stream."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.readCompiled((Path) null),
                            "readCompiled(Path) must reject null path."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.readCompiled((Path) null, 0),
                            "readCompiled(Path, int) must reject null path."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.readCompiled((String) null),
                            "readCompiled(String) must reject null file name."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.readCompiled((String) null, 0),
                            "readCompiled(String, int) must reject null file name."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.readCompiled((InputStream) null),
                            "readCompiled(InputStream) must reject null input stream."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.readCompiled((InputStream) null, 0),
                            "readCompiled(InputStream, int) must reject null input stream."),
                    () -> assertThrows(NullPointerException.class,
                            () -> StemmerPatchTrieBinaryIO.readCompiled(new ByteArrayInputStream(new byte[0]), 0,
                                    null),
                            "Injected compiled read must reject a null command compiler."));
        }

        /**
         * Verifies that direct compiled reading stores compiled commands in final
         * nodes and shares one version 7 table object across repeated slots.
         *
         * @throws IOException if test I/O fails unexpectedly
         */
        @Test
        @DisplayName("Should materialize version 7 directly as shared compiled commands")
        void shouldMaterializeVersionSevenDirectlyAsSharedCompiledCommands() throws IOException {
            final FrequencyTrie<String> sourceTrie = sharedPatchTrie();
            final byte[] artifactBytes = writeCompressed(sourceTrie);

            final FrequencyTrie<CompiledPatchCommand> compiledTrie = StemmerPatchTrieBinaryIO
                    .readCompiled(new ByteArrayInputStream(artifactBytes));
            final FrequencyTrie<String> stringTrie = StemmerPatchTrieBinaryIO
                    .read(new ByteArrayInputStream(artifactBytes));
            final CompiledNode<CompiledPatchCommand> suffixBNode = compiledTrie.root().findChild('b');
            final CompiledNode<CompiledPatchCommand> abNode = suffixBNode.findChild('a');
            final CompiledNode<CompiledPatchCommand> cbNode = suffixBNode.findChild('c');

            assertAll(() -> assertEquals(sourceTrie.metadata(), compiledTrie.metadata()),
                    () -> assertEquals(sourceTrie.size(), compiledTrie.size()),
                    () -> assertEquals(CompiledPatchCommand[].class, abNode.orderedValues().getClass()),
                    () -> assertSame(compiledTrie.get("ab"), compiledTrie.get("cb")),
                    () -> assertSame(abNode.orderedValues()[0], cbNode.orderedValues()[0]),
                    () -> assertEquals("a", compiledTrie.get("ab").apply("ab")),
                    () -> assertEquals("c", compiledTrie.get("cb").apply("cb")),
                    () -> assertEquals("x", compiledTrie.get("xab").apply("xab")),
                    () -> assertEquals("y", compiledTrie.get("ycb").apply("ycb")),
                    () -> assertInstanceOf(String.class, stringTrie.get("ab")),
                    () -> assertInstanceOf(CompiledPatchCommand.class, compiledTrie.get("ab")));
        }

        /**
         * Verifies that version 7 direct loading compiles once per distinct value
         * table entry.
         *
         * @throws IOException if test I/O fails unexpectedly
         */
        @Test
        @DisplayName("Should compile each version 7 table entry once")
        void shouldCompileEachVersionSevenTableEntryOnce() throws IOException {
            final FrequencyTrie<String> sourceTrie = sharedPatchTrie();
            final byte[] artifactBytes = writeCompressed(sourceTrie);
            final AtomicInteger compilationCount = new AtomicInteger();

            final FrequencyTrie<CompiledPatchCommand> compiledTrie = StemmerPatchTrieBinaryIO.readCompiled(
                    new ByteArrayInputStream(artifactBytes), -1, (serializedPatch, traversalDirection) -> {
                        compilationCount.incrementAndGet();
                        return CompiledPatchCommand.compile(serializedPatch, traversalDirection);
                    });

            assertAll(() -> assertEquals(2, compilationCount.get()),
                    () -> assertSame(compiledTrie.get("ab"), compiledTrie.get("cb")),
                    () -> assertEquals("x", compiledTrie.get("xab").apply("xab")));
        }

        /**
         * Verifies that the direct historical reader compiles repeated inline
         * commands once through its reader-local compatibility cache.
         *
         * @throws IOException if test I/O fails unexpectedly
         */
        @Test
        @DisplayName("Should compile repeated version 6 inline commands once")
        void shouldCompileRepeatedVersionSixInlineCommandsOnce() throws IOException {
            final String serializedPatch = PatchCommandEncoder.builder().build().encode("ab", "a");
            final byte[] artifactBytes = createVersionSixArtifactWithRepeatedPatch(serializedPatch);
            final AtomicInteger compilationCount = new AtomicInteger();

            final FrequencyTrie<CompiledPatchCommand> compiledTrie = StemmerPatchTrieBinaryIO.readCompiled(
                    new ByteArrayInputStream(artifactBytes), -1, (patch, traversalDirection) -> {
                        compilationCount.incrementAndGet();
                        return CompiledPatchCommand.compile(patch, traversalDirection);
                    });
            final CompiledPatchCommand rootCommand = compiledTrie.root().orderedValues()[0];
            final CompiledPatchCommand childCommand = compiledTrie.root().findChild('a').orderedValues()[0];

            assertAll(() -> assertEquals(1, compilationCount.get()),
                    () -> assertSame(rootCommand, childCommand),
                    () -> assertEquals(6, compiledTrie.metadata().formatVersion()));
        }

        /**
         * Verifies that invalid persisted patch commands are rejected only by the
         * direct compiled path and retain their validation cause.
         *
         * @throws IOException if test setup I/O fails unexpectedly
         */
        @Test
        @DisplayName("Should wrap invalid compiled patch commands as IOException")
        void shouldWrapInvalidCompiledPatchCommandsAsIOException() throws IOException {
            final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(String[]::new,
                    ReductionSettings
                            .withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
            builder.put("invalid", "Zz");
            final byte[] artifactBytes = writeCompressed(builder.build());

            final FrequencyTrie<String> stringTrie = StemmerPatchTrieBinaryIO
                    .read(new ByteArrayInputStream(artifactBytes));
            final IOException exception = assertThrows(IOException.class,
                    () -> StemmerPatchTrieBinaryIO.readCompiled(new ByteArrayInputStream(artifactBytes)));

            assertAll(() -> assertEquals("Zz", stringTrie.get("invalid")),
                    () -> assertTrue(exception.getMessage().contains("Zz")),
                    () -> assertTrue(exception.getMessage().contains("BACKWARD")),
                    () -> assertInstanceOf(IllegalArgumentException.class, exception.getCause()));
        }

        /**
         * Verifies that an invalid command exactly at the diagnostic boundary remains
         * complete and does not receive a truncation marker.
         *
         * @throws IOException if test setup I/O fails unexpectedly
         */
        @Test
        @DisplayName("Should retain an invalid command exactly at the diagnostic boundary")
        void shouldRetainInvalidCommandAtDiagnosticBoundary() throws IOException {
            final String invalidPatch = "Z".repeat(DIAGNOSTIC_PATCH_BOUNDARY);

            final IOException exception = assertInvalidCompiledPatchDiagnostic(invalidPatch, invalidPatch);

            assertAll(() -> assertTrue(exception.getMessage().contains(invalidPatch),
                    "The exact-boundary command must remain complete."),
                    () -> assertFalse(exception.getMessage().contains(DIAGNOSTIC_TRUNCATION_MARKER),
                            "The exact-boundary command must not be marked as truncated."));
        }

        /**
         * Verifies that an invalid command one character beyond the diagnostic
         * boundary is truncated at the boundary and reports its original length.
         *
         * @throws IOException if test setup I/O fails unexpectedly
         */
        @Test
        @DisplayName("Should truncate an invalid command immediately above the diagnostic boundary")
        void shouldTruncateInvalidCommandAboveDiagnosticBoundary() throws IOException {
            final String invalidPatch = "Z".repeat(DIAGNOSTIC_PATCH_BOUNDARY + 1);
            final String expectedDiagnostic = invalidPatch.substring(0, DIAGNOSTIC_PATCH_BOUNDARY)
                    + DIAGNOSTIC_TRUNCATION_MARKER + invalidPatch.length() + ')';

            final IOException exception = assertInvalidCompiledPatchDiagnostic(invalidPatch, expectedDiagnostic);

            assertAll(() -> assertTrue(exception.getMessage().contains(DIAGNOSTIC_TRUNCATION_MARKER),
                    "The boundary-plus-one command must be marked as truncated."),
                    () -> assertFalse(exception.getMessage().contains("'" + invalidPatch + "'"),
                            "The complete boundary-plus-one command must not appear in the diagnostic."));
        }

        /**
         * Verifies that substantially oversized invalid commands produce bounded
         * diagnostics containing only the retained prefix, truncation marker, and
         * original length.
         *
         * @throws IOException if test setup I/O fails unexpectedly
         */
        @Test
        @DisplayName("Should bound diagnostics for substantially oversized invalid commands")
        void shouldBoundDiagnosticForOversizedInvalidCommand() throws IOException {
            final String invalidPatch = "Z".repeat(512);
            final String expectedDiagnostic = invalidPatch.substring(0, DIAGNOSTIC_PATCH_BOUNDARY)
                    + DIAGNOSTIC_TRUNCATION_MARKER + invalidPatch.length() + ')';

            final IOException exception = assertInvalidCompiledPatchDiagnostic(invalidPatch, expectedDiagnostic);

            assertAll(() -> assertTrue(exception.getMessage().contains(DIAGNOSTIC_TRUNCATION_MARKER),
                    "The oversized command must be marked as truncated."),
                    () -> assertFalse(exception.getMessage().contains("'" + invalidPatch + "'"),
                            "The complete oversized command must not appear in the diagnostic."),
                    () -> assertTrue(exception.getMessage().length() < invalidPatch.length(),
                            "The diagnostic must remain shorter than the oversized persisted command."));
        }

        /**
         * Verifies that the stream overload delegates deserialization to
         * {@link FrequencyTrie#readFrom(...)} and returns its result unchanged.
         *
         * @throws IOException if the helper unexpectedly fails
         */
        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should decompress input and delegate trie deserialization")
        void shouldDecompressInputAndDelegateTrieDeserialization() throws IOException {
            final FrequencyTrie<String> expectedTrie = mock(FrequencyTrie.class);
            final byte[] gzipPayload = gzip("binary-content-not-interpreted-directly");

            try (@SuppressWarnings("rawtypes")
            MockedStatic<FrequencyTrie> mockedStatic = mockStatic(FrequencyTrie.class)) {
                mockedStatic.when(() -> FrequencyTrie.readFrom(any(DataInputStream.class), any(),
                        any(FrequencyTrie.ValueStreamCodec.class))).thenReturn(expectedTrie);

                final FrequencyTrie<String> actualTrie = StemmerPatchTrieBinaryIO
                        .read(new ByteArrayInputStream(gzipPayload));

                assertSame(expectedTrie, actualTrie,
                        "read(InputStream) must return exactly the trie produced by FrequencyTrie.readFrom(...).");

                mockedStatic.verify(() -> FrequencyTrie.readFrom(any(DataInputStream.class), any(),
                        any(FrequencyTrie.ValueStreamCodec.class)));
            }
        }

        /**
         * Verifies that the path overload reads from the filesystem and delegates to
         * the same deserialization path.
         *
         * @throws IOException if the helper unexpectedly fails
         */
        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should read gzip payload from path")
        void shouldReadGzipPayloadFromPath() throws IOException {
            final FrequencyTrie<String> expectedTrie = mock(FrequencyTrie.class);
            final Path sourceFile = temporaryDirectory.resolve("input-stemmer.bin.gz");
            Files.write(sourceFile, gzip("path-based-payload"));

            try (@SuppressWarnings("rawtypes")
            MockedStatic<FrequencyTrie> mockedStatic = mockStatic(FrequencyTrie.class)) {
                mockedStatic.when(() -> FrequencyTrie.readFrom(any(DataInputStream.class), any(),
                        any(FrequencyTrie.ValueStreamCodec.class))).thenReturn(expectedTrie);

                final FrequencyTrie<String> actualTrie = StemmerPatchTrieBinaryIO.read(sourceFile);

                assertSame(expectedTrie, actualTrie,
                        "read(Path) must return the trie created by FrequencyTrie.readFrom(...).");
            }
        }

        /**
         * Verifies that the string-path overload reads from the filesystem and
         * delegates to the same deserialization path.
         *
         * @throws IOException if the helper unexpectedly fails
         */
        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should read gzip payload from file name string")
        void shouldReadGzipPayloadFromFileNameString() throws IOException {
            final FrequencyTrie<String> expectedTrie = mock(FrequencyTrie.class);
            final Path sourceFile = temporaryDirectory.resolve("input-string-stemmer.bin.gz");
            Files.write(sourceFile, gzip("string-based-payload"));

            try (@SuppressWarnings("rawtypes")
            MockedStatic<FrequencyTrie> mockedStatic = mockStatic(FrequencyTrie.class)) {
                mockedStatic.when(() -> FrequencyTrie.readFrom(any(DataInputStream.class), any(),
                        any(FrequencyTrie.ValueStreamCodec.class))).thenReturn(expectedTrie);

                final FrequencyTrie<String> actualTrie = StemmerPatchTrieBinaryIO.read(sourceFile.toString());

                assertSame(expectedTrie, actualTrie,
                        "read(String) must return the trie created by FrequencyTrie.readFrom(...).");
            }
        }

        /**
         * Verifies that stream overload with dense span override delegates to the
         * four-argument readFrom method.
         */
        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should delegate stream read with dense span override")
        void shouldDelegateInputStreamReadWithDenseSpanOverride() throws IOException {
            final FrequencyTrie<String> expectedTrie = mock(FrequencyTrie.class);
            final byte[] gzipPayload = gzip("binary-content-with-max-expanded-index");

            try (@SuppressWarnings("rawtypes")
            MockedStatic<FrequencyTrie> mockedStatic = mockStatic(FrequencyTrie.class)) {
                mockedStatic.when(() -> FrequencyTrie.readFrom(any(DataInputStream.class), any(),
                        any(FrequencyTrie.ValueStreamCodec.class), anyInt())).thenReturn(expectedTrie);

                final FrequencyTrie<String> actualTrie = StemmerPatchTrieBinaryIO
                        .read(new ByteArrayInputStream(gzipPayload), 17);

                assertSame(expectedTrie, actualTrie,
                        "read(InputStream, int) must return the trie produced by FrequencyTrie.readFrom(...).");

                mockedStatic.verify(() -> FrequencyTrie.readFrom(any(DataInputStream.class), any(),
                        any(FrequencyTrie.ValueStreamCodec.class), eq(17)));
            }
        }

        /**
         * Verifies that path overload with dense span override delegates to the
         * same method overload with the override parameter.
         */
        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should delegate path read with dense span override")
        void shouldDelegatePathReadWithDenseSpanOverride() throws IOException {
            final FrequencyTrie<String> expectedTrie = mock(FrequencyTrie.class);
            final Path sourceFile = temporaryDirectory.resolve("input-max-expanded.bin.gz");
            Files.write(sourceFile, gzip("path-based-max-expanded-index"));

            try (@SuppressWarnings("rawtypes")
            MockedStatic<FrequencyTrie> mockedStatic = mockStatic(FrequencyTrie.class)) {
                mockedStatic.when(() -> FrequencyTrie.readFrom(any(DataInputStream.class), any(),
                        any(FrequencyTrie.ValueStreamCodec.class), anyInt())).thenReturn(expectedTrie);

                final FrequencyTrie<String> actualTrie = StemmerPatchTrieBinaryIO.read(sourceFile, 0);

                assertSame(expectedTrie, actualTrie,
                        "read(Path, int) must return the trie produced by FrequencyTrie.readFrom(...).");

                mockedStatic.verify(() -> FrequencyTrie.readFrom(any(DataInputStream.class), any(),
                        any(FrequencyTrie.ValueStreamCodec.class), eq(0)));
            }
        }

        /**
         * Verifies that string path overload with dense span override delegates to the
         * same method overload with the override parameter.
         */
        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should delegate file name read with dense span override")
        void shouldDelegateStringReadWithDenseSpanOverride() throws IOException {
            final FrequencyTrie<String> expectedTrie = mock(FrequencyTrie.class);
            final Path sourceFile = temporaryDirectory.resolve("input-string-max-expanded.bin.gz");
            Files.write(sourceFile, gzip("string-based-max-expanded-index"));

            try (@SuppressWarnings("rawtypes")
            MockedStatic<FrequencyTrie> mockedStatic = mockStatic(FrequencyTrie.class)) {
                mockedStatic.when(() -> FrequencyTrie.readFrom(any(DataInputStream.class), any(),
                        any(FrequencyTrie.ValueStreamCodec.class), anyInt())).thenReturn(expectedTrie);

                final FrequencyTrie<String> actualTrie = StemmerPatchTrieBinaryIO.read(sourceFile.toString(), 32);

                assertSame(expectedTrie, actualTrie,
                        "read(String, int) must return the trie produced by FrequencyTrie.readFrom(...).");

                mockedStatic.verify(() -> FrequencyTrie.readFrom(any(DataInputStream.class), any(),
                        any(FrequencyTrie.ValueStreamCodec.class), eq(32)));
            }
        }

        /**
         * Verifies that metadata-only read parses and returns the persisted metadata.
         */
        @Test
        @DisplayName("Should read metadata from gzip payload")
        void shouldReadMetadataFromGzipPayload() throws IOException {
            final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(String[]::new,
                    ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
            builder.put("run", PatchCommandEncoder.builder().build().encode("running", "run"));
            final FrequencyTrie<String> trie = builder.build();

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            StemmerPatchTrieBinaryIO.write(trie, outputStream);

            final TrieMetadata metadata = StemmerPatchTrieBinaryIO.readMetadata(new ByteArrayInputStream(outputStream.toByteArray()));

            assertEquals(trie.metadata(), metadata,
                    "readMetadata(InputStream) must return the same metadata persisted by write().");
        }

        /**
         * Verifies that metadata can be read from a binary file path.
         */
        @Test
        @DisplayName("Should read metadata from file path")
        void shouldReadMetadataFromPath() throws IOException {
            final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(String[]::new,
                    ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
            builder.put("city", PatchCommandEncoder.builder().build().encode("cities", "city"));
            final FrequencyTrie<String> trie = builder.build();

            final Path sourceFile = temporaryDirectory.resolve("metadata-path.bin.gz");
            StemmerPatchTrieBinaryIO.write(trie, sourceFile);

            final TrieMetadata metadata = StemmerPatchTrieBinaryIO.readMetadata(sourceFile);
            assertEquals(trie.metadata(), metadata);
        }

        /**
         * Verifies that metadata can be read from a binary file name.
         */
        @Test
        @DisplayName("Should read metadata from file name")
        void shouldReadMetadataFromStringPath() throws IOException {
            final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(String[]::new,
                    ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
            builder.put("city", PatchCommandEncoder.builder().build().encode("cities", "city"));
            final FrequencyTrie<String> trie = builder.build();

            final Path sourceFile = temporaryDirectory.resolve("metadata-string.bin.gz");
            StemmerPatchTrieBinaryIO.write(trie, sourceFile);

            final TrieMetadata metadata = StemmerPatchTrieBinaryIO.readMetadata(sourceFile.toString());
            assertEquals(trie.metadata(), metadata);
        }

        /**
         * Verifies that malformed non-GZip input is reported as an I/O failure.
         */
        @Test
        @DisplayName("Should fail for malformed non-gzip input")
        void shouldFailForMalformedNonGzipInput() {
            final ByteArrayInputStream malformedInput = new ByteArrayInputStream(
                    "not-a-gzip-stream".getBytes(StandardCharsets.UTF_8));

            assertThrows(IOException.class, () -> StemmerPatchTrieBinaryIO.read(malformedInput),
                    "Malformed non-GZip input must be reported as an I/O failure.");
        }

        /**
         * Verifies that the helper closes the supplied input stream because the
         * implementation owns the wrapping GZip/DataInput streams in a
         * try-with-resources block.
         *
         * @throws IOException if the helper unexpectedly fails
         */
        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should close supplied input stream")
        void shouldCloseSuppliedInputStream() throws IOException {
            final FrequencyTrie<String> expectedTrie = mock(FrequencyTrie.class);
            final TrackingInputStream trackingInputStream = new TrackingInputStream(gzip("close-check"));

            try (@SuppressWarnings("rawtypes")
            MockedStatic<FrequencyTrie> mockedStatic = mockStatic(FrequencyTrie.class)) {
                mockedStatic.when(() -> FrequencyTrie.readFrom(any(DataInputStream.class), any(),
                        any(FrequencyTrie.ValueStreamCodec.class))).thenReturn(expectedTrie);

                final FrequencyTrie<String> actualTrie = StemmerPatchTrieBinaryIO.read(trackingInputStream);

                assertAll(
                        () -> assertSame(expectedTrie, actualTrie,
                                "Read operation must still return the deserialized trie."),
                        () -> assertTrue(trackingInputStream.isClosed(),
                                "Input stream must be closed when read completes."));
            }
        }

        /**
         * Verifies that read failures raised by the trie deserializer are propagated
         * unchanged to the caller.
         *
         * @throws IOException if the mock setup unexpectedly fails
         */
        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Should propagate read failure from trie deserialization")
        void shouldPropagateReadFailureFromTrieDeserialization() throws IOException {
            final IOException expectedException = new IOException("read failure");
            final byte[] gzipPayload = gzip("deserialization-input");

            try (@SuppressWarnings("rawtypes")
            MockedStatic<FrequencyTrie> mockedStatic = mockStatic(FrequencyTrie.class)) {
                mockedStatic.when(() -> FrequencyTrie.readFrom(any(DataInputStream.class), any(),
                        any(FrequencyTrie.ValueStreamCodec.class))).thenThrow(expectedException);

                final IOException actualException = assertThrows(IOException.class,
                        () -> StemmerPatchTrieBinaryIO.read(new ByteArrayInputStream(gzipPayload)),
                        "Read-side deserialization failures must be propagated unchanged.");

                assertSame(expectedException, actualException,
                        "The helper must propagate the original read exception instance.");
            }
        }
    }

    /**
     * Builds a version 7 trie with equal patch strings on distinct nonmergeable
     * nodes and one additional distinct patch.
     *
     * @return representative patch-command trie
     */
    private static FrequencyTrie<String> sharedPatchTrie() {
        final PatchCommandEncoder encoder = PatchCommandEncoder.builder().build();
        final String sharedPatch = encoder.encode("ab", "a");
        final String longerDeletionPatch = encoder.encode("xab", "x");
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(String[]::new,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
        builder.put("ab", new String(sharedPatch), 2);
        builder.put("xab", longerDeletionPatch);
        builder.put("cb", new String(sharedPatch), 3);
        builder.put("ycb", new String(longerDeletionPatch));
        return builder.build();
    }

    /**
     * Serializes one String-valued trie through the production compressed writer.
     *
     * @param trie source trie
     * @return compressed artifact bytes
     * @throws IOException if writing fails
     */
    private static byte[] writeCompressed(final FrequencyTrie<String> trie) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StemmerPatchTrieBinaryIO.write(trie, outputStream);
        return outputStream.toByteArray();
    }

    /**
     * Loads an artifact through an injected rejecting compiler and verifies the
     * exact bounded trust-boundary diagnostic independently of patch-parser
     * compatibility behavior for odd command lengths.
     *
     * @param invalidPatch       invalid serialized patch command
     * @param expectedDiagnostic complete or bounded command representation expected
     *                           inside the diagnostic
     * @return thrown I/O exception for additional boundary-specific assertions
     * @throws IOException if fixture serialization fails unexpectedly
     */
    private static IOException assertInvalidCompiledPatchDiagnostic(final String invalidPatch,
            final String expectedDiagnostic) throws IOException {
        final FrequencyTrie.Builder<String> builder = new FrequencyTrie.Builder<String>(String[]::new,
                ReductionSettings.withDefaults(
                        ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
        builder.put("invalid", invalidPatch);
        final byte[] artifactBytes = writeCompressed(builder.build());
        final IOException exception = assertThrows(IOException.class,
                () -> StemmerPatchTrieBinaryIO.readCompiled(new ByteArrayInputStream(artifactBytes), -1,
                        (serializedPatch, traversalDirection) -> {
                            throw new IllegalArgumentException("Injected invalid persisted command.");
                        }),
                "Direct compiled loading must translate an invalid persisted command to IOException.");
        final String expectedMessage = "Invalid persisted patch command '" + expectedDiagnostic
                + "' for traversal direction BACKWARD.";

        assertAll(() -> assertEquals(IOException.class, exception.getClass(),
                "IOException must be the exact externally visible exception type."),
                () -> assertEquals(expectedMessage, exception.getMessage(),
                        "The invalid-command diagnostic must match the bounded production contract."),
                () -> assertNotNull(exception.getCause(), "The validation cause must be retained."),
                () -> assertEquals(IllegalArgumentException.class, exception.getCause().getClass(),
                        "The original patch-command validation failure must remain the cause."));
        return exception;
    }

    /**
     * Creates a valid version 6 artifact containing the same inline patch command
     * in two distinct node slots.
     *
     * @param serializedPatch repeated serialized patch command
     * @return compressed historical artifact bytes
     * @throws IOException if fixture creation fails
     */
    private static byte[] createVersionSixArtifactWithRepeatedPatch(final String serializedPatch) throws IOException {
        final TrieMetadata metadata = new TrieMetadata(6, WordTraversalDirection.BACKWARD,
                ReductionSettings.withDefaults(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS),
                DiacriticProcessingMode.AS_IS, CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT);
        final ByteArrayOutputStream rawOutputStream = new ByteArrayOutputStream();
        try (DataOutputStream dataOutput = new DataOutputStream(rawOutputStream)) {
            dataOutput.writeInt(0x45475452);
            dataOutput.writeInt(6);
            dataOutput.writeInt(2);
            dataOutput.writeInt(0);
            dataOutput.writeUTF(metadata.toTextBlock());

            dataOutput.writeBoolean(false);
            dataOutput.writeInt(1);
            dataOutput.writeChar('a');
            dataOutput.writeInt(1);
            dataOutput.writeInt(1);
            dataOutput.writeUTF(serializedPatch);
            dataOutput.writeInt(1);

            dataOutput.writeBoolean(false);
            dataOutput.writeInt(0);
            dataOutput.writeInt(1);
            dataOutput.writeUTF(serializedPatch);
            dataOutput.writeInt(2);
        }
        return gzip(rawOutputStream.toByteArray());
    }

    /**
     * Compresses a binary payload using GZip.
     *
     * @param payload uncompressed bytes
     * @return compressed bytes
     * @throws IOException if compression fails
     */
    private static byte[] gzip(final byte[] payload) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(payload);
        }
        return outputStream.toByteArray();
    }

    /**
     * Utility method that produces a small GZip-compressed byte array.
     *
     * @param payload textual payload to compress
     * @return compressed bytes
     * @throws IOException if compression fails unexpectedly
     */
    private static byte[] gzip(final String payload) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try (java.util.zip.GZIPOutputStream gzipOutputStream = new java.util.zip.GZIPOutputStream(
                byteArrayOutputStream)) {
            gzipOutputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        final byte[] compressedBytes = byteArrayOutputStream.toByteArray();

        try (GZIPInputStream ignored = new GZIPInputStream(new ByteArrayInputStream(compressedBytes))) {
            assertTrue(compressedBytes.length > 0, "Test fixture must create a valid non-empty GZip payload.");
        }

        return compressedBytes;
    }

    /**
     * Output stream that records whether it has been closed.
     */
    @Tag("unit")
    @Tag("io")
    @Tag("trie")
    @Tag("persistence")
    private static final class TrackingOutputStream extends ByteArrayOutputStream {

        /**
         * Whether {@link #close()} has been invoked.
         */
        private boolean closed;

        @Override
        public void close() throws IOException {
            this.closed = true;
            super.close();
        }

        /**
         * Returns whether the stream has been closed.
         *
         * @return {@code true} if the stream has been closed; {@code false} otherwise
         */
        boolean isClosed() {
            return this.closed;
        }
    }

    /**
     * Input stream that records whether it has been closed.
     */
    @Tag("unit")
    @Tag("io")
    @Tag("trie")
    @Tag("persistence")
    private static final class TrackingInputStream extends ByteArrayInputStream {

        /**
         * Whether {@link #close()} has been invoked.
         */
        private boolean closed;

        /**
         * Creates a tracking stream backed by the given bytes.
         *
         * @param buffer input bytes
         */
        TrackingInputStream(final byte[] buffer) {
            super(buffer);
        }

        @Override
        public void close() throws IOException {
            this.closed = true;
            super.close();
        }

        /**
         * Returns whether the stream has been closed.
         *
         * @return {@code true} if the stream has been closed; {@code false} otherwise
         */
        boolean isClosed() {
            return this.closed;
        }
    }
}
