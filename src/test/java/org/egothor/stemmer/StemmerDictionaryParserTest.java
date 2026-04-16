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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link StemmerDictionaryParser}.
 *
 * <p>
 * The suite verifies:
 * </p>
 * <ul>
 * <li>parsing through all public overloads,</li>
 * <li>normalization to lower case,</li>
 * <li>handling of empty lines and remarks,</li>
 * <li>correct entry emission including line numbers,</li>
 * <li>propagation of I/O failures from the handler and file system,</li>
 * <li>argument validation,</li>
 * <li>validation rules of {@link StemmerDictionaryParser.ParseStatistics}.</li>
 * </ul>
 */
@DisplayName("StemmerDictionaryParser")
@Tag("unit")
@Tag("parser")
class StemmerDictionaryParserTest {

    /**
     * Temporary directory used by file-based parser tests.
     */
    @TempDir
    Path tempDir;

    /**
     * Parsed entry snapshot used to assert handler callbacks deterministically.
     *
     * @param stem       canonical stem
     * @param variants   parsed variants in encounter order
     * @param lineNumber physical source line number
     */
    private record CapturedEntry(String stem, String[] variants, int lineNumber) {
        // Record used only as a compact assertion carrier.
    }

    /**
     * Creates a handler that collects all parser callbacks into the supplied list.
     *
     * @param entries target entry list
     * @return collecting handler
     */
    private static StemmerDictionaryParser.EntryHandler collectingHandler(final List<CapturedEntry> entries) {
        return (stem, variants, lineNumber) -> entries.add(new CapturedEntry(stem, variants.clone(), lineNumber));
    }

    /**
     * Creates a UTF-8 file with the provided content.
     *
     * @param fileName target file name
     * @param content  file content
     * @return created file path
     * @throws IOException if writing fails
     */
    private Path createFile(final String fileName, final String content) throws IOException {
        final Path file = this.tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Nested
    @DisplayName("parse(Reader, String, EntryHandler)")
    class ReaderParsingTests {

        @Test
        @DisplayName("should parse normalized entries and collect accurate statistics")
        void shouldParseNormalizedEntriesAndCollectAccurateStatistics() throws IOException {
            final String input = "# full line remark\n" + "   \n"
                    + "Root Running Runs RUNNER   # trailing hash remark\n"
                    + "House HOUSEHOLD houseS // trailing slash remark\n" + "SingleStem\n"
                    + "// full line slash remark\n";

            final List<CapturedEntry> entries = new ArrayList<CapturedEntry>();
            final Reader reader = new StringReader(input);

            final StemmerDictionaryParser.ParseStatistics statistics = StemmerDictionaryParser.parse(reader,
                    "reader-source", collectingHandler(entries));

            assertNotNull(statistics);
            assertEquals(6, statistics.lineCount(), "All physical lines must be counted.");
            assertEquals(3, statistics.entryCount(), "Three logical entries must be emitted.");
            assertEquals(3, statistics.ignoredLineCount(), "Remark-only and blank lines must be ignored.");
            assertEquals("reader-source", statistics.sourceDescription(), "Source description must be preserved.");

            assertEquals(3, entries.size(), "Exactly three parsed entries are expected.");

            final CapturedEntry first = entries.get(0);
            assertAll("First entry", () -> assertEquals("root", first.stem(), "Stem must be normalized to lower case."),
                    () -> assertArrayEquals(new String[] { "running", "runs", "runner" }, first.variants(),
                            "Variants must be normalized and kept in encounter order."),
                    () -> assertEquals(3, first.lineNumber(), "Line number must refer to the physical source line."));

            final CapturedEntry second = entries.get(1);
            assertAll("Second entry", () -> assertEquals("house", second.stem()),
                    () -> assertArrayEquals(new String[] { "household", "houses" }, second.variants()),
                    () -> assertEquals(4, second.lineNumber()));

            final CapturedEntry third = entries.get(2);
            assertAll("Third entry", () -> assertEquals("singlestem", third.stem()),
                    () -> assertArrayEquals(new String[0], third.variants(),
                            "A line containing only the stem must produce zero variants."),
                    () -> assertEquals(5, third.lineNumber()));
        }

        @Test
        @DisplayName("should prefer earliest remark marker regardless of marker type")
        void shouldPreferEarliestRemarkMarkerRegardlessOfMarkerType() throws IOException {
            final String input = "alpha beta // slash remark before # hash remark # ignored\n"
                    + "gamma delta # hash remark before // slash remark // ignored\n";

            final List<CapturedEntry> entries = new ArrayList<CapturedEntry>();

            final StemmerDictionaryParser.ParseStatistics statistics = StemmerDictionaryParser
                    .parse(new StringReader(input), "mixed-remarks", collectingHandler(entries));

            assertAll("Statistics", () -> assertEquals(2, statistics.lineCount()),
                    () -> assertEquals(2, statistics.entryCount()),
                    () -> assertEquals(0, statistics.ignoredLineCount()));

            assertEquals(2, entries.size(), "Both logical entries must be parsed.");

            assertAll("First parsed line", () -> assertEquals("alpha", entries.get(0).stem()),
                    () -> assertArrayEquals(new String[] { "beta" }, entries.get(0).variants()));

            assertAll("Second parsed line", () -> assertEquals("gamma", entries.get(1).stem()),
                    () -> assertArrayEquals(new String[] { "delta" }, entries.get(1).variants()));
        }

        @Test
        @DisplayName("should propagate handler IOException without swallowing it")
        void shouldPropagateHandlerIOExceptionWithoutSwallowingIt() {
            final IOException expected = new IOException("Simulated handler failure.");
            final Reader reader = new StringReader("stem variant\n");

            final IOException exception = assertThrows(IOException.class,
                    () -> StemmerDictionaryParser.parse(reader, "failing-handler", (stem, variants, lineNumber) -> {
                        throw expected;
                    }), "Handler I/O failure must be propagated.");

            assertEquals(expected, exception, "The original exception instance should be preserved.");
        }

        @Test
        @DisplayName("should reject null reader")
        void shouldRejectNullReader() {
            assertThrows(NullPointerException.class,
                    () -> StemmerDictionaryParser.parse((Reader) null, "source", (stem, variants, lineNumber) -> {
                        // no-op
                    }));
        }

        @Test
        @DisplayName("should reject null source description")
        void shouldRejectNullSourceDescription() {
            assertThrows(NullPointerException.class,
                    () -> StemmerDictionaryParser.parse(new StringReader("a b"), null, (stem, variants, lineNumber) -> {
                        // no-op
                    }));
        }

        @Test
        @DisplayName("should reject null entry handler")
        void shouldRejectNullEntryHandler() {
            assertThrows(NullPointerException.class,
                    () -> StemmerDictionaryParser.parse(new StringReader("a b"), "source", null));
        }
    }

    @Nested
    @DisplayName("parse(Path, EntryHandler) and parse(String, EntryHandler)")
    class FileParsingTests {

        @Test
        @DisplayName("should parse same content through path and string overloads")
        void shouldParseSameContentThroughPathAndStringOverloads() throws IOException {
            final String content = "walk walking walked\n" + "run running\n" + "\n" + "# ignored\n";

            final Path file = createFile("dictionary.txt", content);

            final List<CapturedEntry> pathEntries = new ArrayList<CapturedEntry>();
            final StemmerDictionaryParser.ParseStatistics pathStatistics = StemmerDictionaryParser.parse(file,
                    collectingHandler(pathEntries));

            final List<CapturedEntry> stringEntries = new ArrayList<CapturedEntry>();
            final StemmerDictionaryParser.ParseStatistics stringStatistics = StemmerDictionaryParser
                    .parse(file.toString(), collectingHandler(stringEntries));

            assertAll("Path statistics",
                    () -> assertEquals(file.toAbsolutePath().toString(), pathStatistics.sourceDescription()),
                    () -> assertEquals(4, pathStatistics.lineCount()),
                    () -> assertEquals(2, pathStatistics.entryCount()),
                    () -> assertEquals(2, pathStatistics.ignoredLineCount()));

            assertAll("String statistics",
                    () -> assertEquals(file.toAbsolutePath().toString(), stringStatistics.sourceDescription()),
                    () -> assertEquals(pathStatistics.lineCount(), stringStatistics.lineCount()),
                    () -> assertEquals(pathStatistics.entryCount(), stringStatistics.entryCount()),
                    () -> assertEquals(pathStatistics.ignoredLineCount(), stringStatistics.ignoredLineCount()));

            assertEquals(pathEntries.size(), stringEntries.size(),
                    "Both overloads must emit the same number of entries.");

            for (int index = 0; index < pathEntries.size(); index++) {
                final CapturedEntry pathEntry = pathEntries.get(index);
                final CapturedEntry stringEntry = stringEntries.get(index);

                assertAll("Entry " + index, () -> assertEquals(pathEntry.stem(), stringEntry.stem()),
                        () -> assertArrayEquals(pathEntry.variants(), stringEntry.variants()),
                        () -> assertEquals(pathEntry.lineNumber(), stringEntry.lineNumber()));
            }
        }

        @Test
        @DisplayName("should reject null path")
        void shouldRejectNullPath() {
            assertThrows(NullPointerException.class,
                    () -> StemmerDictionaryParser.parse((Path) null, (stem, variants, lineNumber) -> {
                        // no-op
                    }));
        }

        @Test
        @DisplayName("should reject null file name")
        void shouldRejectNullFileName() {
            assertThrows(NullPointerException.class,
                    () -> StemmerDictionaryParser.parse((String) null, (stem, variants, lineNumber) -> {
                        // no-op
                    }));
        }

        @Test
        @DisplayName("should reject null handler for path overload")
        void shouldRejectNullHandlerForPathOverload() throws IOException {
            final Path file = createFile("path-null-handler.txt", "root roots\n");

            assertThrows(NullPointerException.class, () -> StemmerDictionaryParser.parse(file, null));
        }

        @Test
        @DisplayName("should reject null handler for string overload")
        void shouldRejectNullHandlerForStringOverload() throws IOException {
            final Path file = createFile("string-null-handler.txt", "root roots\n");

            assertThrows(NullPointerException.class, () -> StemmerDictionaryParser.parse(file.toString(), null));
        }

        @Test
        @DisplayName("should propagate file access failure for missing path")
        void shouldPropagateFileAccessFailureForMissingPath() {
            final Path missingFile = StemmerDictionaryParserTest.this.tempDir.resolve("missing-dictionary.txt");

            assertThrows(IOException.class,
                    () -> StemmerDictionaryParser.parse(missingFile, (stem, variants, lineNumber) -> {
                        // no-op
                    }), "Missing file must surface as an I/O failure.");
        }
    }

    @Nested
    @DisplayName("ParseStatistics")
    class ParseStatisticsTests {

        @Test
        @DisplayName("should create record when all values are valid")
        void shouldCreateRecordWhenAllValuesAreValid() {
            final StemmerDictionaryParser.ParseStatistics statistics = new StemmerDictionaryParser.ParseStatistics(
                    "source", 7, 4, 3);

            assertAll("Record state", () -> assertEquals("source", statistics.sourceDescription()),
                    () -> assertEquals(7, statistics.lineCount()), () -> assertEquals(4, statistics.entryCount()),
                    () -> assertEquals(3, statistics.ignoredLineCount()));
        }

        @Test
        @DisplayName("should reject null source description")
        void shouldRejectNullSourceDescription() {
            assertThrows(NullPointerException.class, () -> new StemmerDictionaryParser.ParseStatistics(null, 0, 0, 0));
        }

        @Test
        @DisplayName("should reject negative line count")
        void shouldRejectNegativeLineCount() {
            assertThrows(IllegalArgumentException.class,
                    () -> new StemmerDictionaryParser.ParseStatistics("source", -1, 0, 0));
        }

        @Test
        @DisplayName("should reject negative entry count")
        void shouldRejectNegativeEntryCount() {
            assertThrows(IllegalArgumentException.class,
                    () -> new StemmerDictionaryParser.ParseStatistics("source", 0, -1, 0));
        }

        @Test
        @DisplayName("should reject negative ignored line count")
        void shouldRejectNegativeIgnoredLineCount() {
            assertThrows(IllegalArgumentException.class,
                    () -> new StemmerDictionaryParser.ParseStatistics("source", 0, 0, -1));
        }
    }
}
