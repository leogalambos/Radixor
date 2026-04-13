package org.egothor.stemmer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

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
@DisplayName("StemmerPatchTrieBinaryIO")
class StemmerPatchTrieBinaryIOTest {

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
                            () -> StemmerPatchTrieBinaryIO.read((ByteArrayInputStream) null),
                            "read(InputStream) must reject null input stream."));
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