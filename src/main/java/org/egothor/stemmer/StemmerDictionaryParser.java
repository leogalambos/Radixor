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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parser of line-oriented stemmer dictionary files.
 *
 * <p>
 * Each non-empty logical line uses a tab-separated values layout. The first
 * column is interpreted as the canonical stem, and every following
 * tab-separated column on the same line is interpreted as a variant belonging
 * to that stem.
 *
 * <p>
 * Input line case normalization is controlled by {@link CaseProcessingMode}.
 * Leading and trailing whitespace around each column is ignored.
 *
 * <p>
 * The parser supports line remarks and trailing remarks. The remark markers
 * {@code #} and {@code //} terminate the logical content of the line, and the
 * remainder of that line is ignored.
 *
 * <p>
 * Dictionary items containing any Unicode whitespace character are currently
 * not supported. Such items are ignored and reported through a single
 * {@link Level#WARNING warning}-level log entry per physical line together with
 * the source line number, the normalized stem column, and the list of ignored
 * items from that line.
 *
 * <p>
 * This class is intentionally stateless and allocation-light so it can be used
 * both by runtime loading and by offline compilation tooling.
 */
public final class StemmerDictionaryParser {

    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(StemmerDictionaryParser.class.getName());

    /**
     * Utility class.
     */
    private StemmerDictionaryParser() {
        throw new AssertionError("No instances.");
    }

    /**
     * Callback receiving one parsed dictionary line.
     */
    @FunctionalInterface
    public interface EntryHandler {

        /**
         * Accepts one parsed dictionary entry.
         *
         * @param stem       canonical stem, never {@code null}
         * @param variants   variants in encounter order, never {@code null}
         * @param lineNumber original physical line number in the parsed source
         * @throws IOException if processing fails
         */
        void onEntry(String stem, String[] variants, int lineNumber) throws IOException;
    }

    /**
     * Parses a dictionary file from a filesystem path.
     *
     * @param path         dictionary file path
     * @param entryHandler handler receiving parsed entries
     * @return parsing statistics
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if reading fails
     */
    public static ParseStatistics parse(final Path path, final EntryHandler entryHandler) throws IOException {
        return parse(path, CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT, entryHandler);
    }

    /**
     * Parses a dictionary file from a filesystem path.
     *
     * @param path               dictionary file path
     * @param caseProcessingMode case processing mode
     * @param entryHandler       handler receiving parsed entries
     * @return parsing statistics
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if reading fails
     */
    public static ParseStatistics parse(final Path path, final CaseProcessingMode caseProcessingMode,
            final EntryHandler entryHandler) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(caseProcessingMode, "caseProcessingMode");
        Objects.requireNonNull(entryHandler, "entryHandler");

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader, path.toAbsolutePath().toString(), caseProcessingMode, entryHandler);
        }
    }

    /**
     * Parses a dictionary file from a path string.
     *
     * @param fileName     dictionary file name or path string
     * @param entryHandler handler receiving parsed entries
     * @return parsing statistics
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if reading fails
     */
    public static ParseStatistics parse(final String fileName, final EntryHandler entryHandler) throws IOException {
        Objects.requireNonNull(fileName, "fileName");
        return parse(Path.of(fileName), CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT, entryHandler);
    }

    /**
     * Parses a dictionary file from a path string.
     *
     * @param fileName           dictionary file name or path string
     * @param caseProcessingMode case processing mode
     * @param entryHandler       handler receiving parsed entries
     * @return parsing statistics
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if reading fails
     */
    public static ParseStatistics parse(final String fileName, final CaseProcessingMode caseProcessingMode,
            final EntryHandler entryHandler) throws IOException {
        Objects.requireNonNull(fileName, "fileName");
        return parse(Path.of(fileName), caseProcessingMode, entryHandler);
    }

    /**
     * Parses a dictionary from a reader.
     *
     * @param reader            source reader
     * @param sourceDescription logical source description for diagnostics
     * @param entryHandler      handler receiving parsed entries
     * @return parsing statistics
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if reading or handler processing fails
     */
    public static ParseStatistics parse(final Reader reader, final String sourceDescription,
            final EntryHandler entryHandler) throws IOException {
        return parse(reader, sourceDescription, CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT, entryHandler);
    }

    /**
     * Parses a dictionary from a reader.
     *
     * @param reader             source reader
     * @param sourceDescription  logical source description for diagnostics
     * @param caseProcessingMode case processing mode
     * @param entryHandler       handler receiving parsed entries
     * @return parsing statistics
     * @throws NullPointerException if any argument is {@code null}
     * @throws IOException          if reading or handler processing fails
     */
    public static ParseStatistics parse(final Reader reader, final String sourceDescription,
            final CaseProcessingMode caseProcessingMode, final EntryHandler entryHandler) throws IOException {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(sourceDescription, "sourceDescription");
        Objects.requireNonNull(caseProcessingMode, "caseProcessingMode");
        Objects.requireNonNull(entryHandler, "entryHandler");

        final BufferedReader bufferedReader = reader instanceof BufferedReader ? (BufferedReader) reader
                : new BufferedReader(reader);

        int lineNumber = 0;
        int logicalEntryCount = 0;
        int ignoredLineCount = 0;

        for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
            lineNumber++;

            final String normalizedLine = normalizeLineCase(stripRemark(line).trim(), caseProcessingMode);
            if (normalizedLine.isEmpty()) {
                ignoredLineCount++;
                continue;
            }

            final String[] rawColumns = normalizedLine.split("\t", -1);
            if (rawColumns.length == 0) {
                ignoredLineCount++;
                continue;
            }

            final String stem = rawColumns[0].strip();
            final List<String> acceptedVariants = new ArrayList<String>(Math.max(0, rawColumns.length - 1)); // NOPMD

            if (stem.isEmpty()) {
                ignoredLineCount++;
                continue;
            }

            if (containsWhitespaceCharacter(stem)) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                            "Ignoring dictionary line containing whitespace in source {0} at line {1}, stem {2}.",
                            new Object[] { sourceDescription, lineNumber, stem }); // NOPMD
                }
                continue;
            }

            int ignored = 0;

            for (int index = 1; index < rawColumns.length; index++) {
                final String variant = rawColumns[index].strip();
                if (variant.isEmpty()) {
                    continue;
                }
                if (containsWhitespaceCharacter(variant)) {
                    ignored++;
                    continue;
                }
                acceptedVariants.add(variant);
            }

            if (ignored > 0 && LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                        "Ignoring dictionary items containing whitespace in source {0} at line {1}, stem {2}, ignored {3}:{4}.",
                        new Object[] { sourceDescription, lineNumber, stem, ignored, rawColumns.length }); // NOPMD
            }

            entryHandler.onEntry(stem, acceptedVariants.toArray(String[]::new), lineNumber);
            logicalEntryCount++;
        }

        final ParseStatistics statistics = new ParseStatistics(sourceDescription, lineNumber, logicalEntryCount,
                ignoredLineCount);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Parsed dictionary source {0}: lines={1}, entries={2}, ignoredLines={3}.",
                    new Object[] { statistics.sourceDescription(), statistics.lineCount(), statistics.entryCount(),
                            statistics.ignoredLineCount() });
        }

        return statistics;
    }

    /**
     * Applies case normalization to one line according to the selected mode.
     *
     * @param line               line to normalize
     * @param caseProcessingMode case processing mode
     * @return normalized line
     */
    private static String normalizeLineCase(final String line, final CaseProcessingMode caseProcessingMode) {
        if (caseProcessingMode == CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT) {
            return line.toLowerCase(Locale.ROOT);
        }
        return line;
    }

    /**
     * Determines whether one dictionary item contains any Unicode whitespace
     * character.
     *
     * @param item dictionary item to inspect
     * @return {@code true} when the item contains at least one whitespace character
     */
    private static boolean containsWhitespaceCharacter(final String item) {
        for (int index = 0; index < item.length(); index++) {
            if (Character.isWhitespace(item.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a trailing remark from one physical line.
     *
     * <p>
     * The earliest occurrence of either supported remark marker terminates the
     * logical line content.
     *
     * @param line physical line
     * @return line content without a trailing remark
     */
    private static String stripRemark(final String line) {
        final int hashIndex = line.indexOf('#');
        final int slashIndex = line.indexOf("//");

        final int remarkIndex;
        if (hashIndex < 0) {
            remarkIndex = slashIndex;
        } else if (slashIndex < 0) {
            remarkIndex = hashIndex;
        } else {
            remarkIndex = Math.min(hashIndex, slashIndex);
        }

        if (remarkIndex < 0) {
            return line;
        }
        return line.substring(0, remarkIndex);
    }

    /**
     * Immutable parsing statistics.
     *
     * @param sourceDescription logical source description
     * @param lineCount         number of physical lines read
     * @param entryCount        number of logical dictionary entries emitted
     * @param ignoredLineCount  number of ignored empty or remark-only lines
     */
    public record ParseStatistics(String sourceDescription, int lineCount, int entryCount, int ignoredLineCount) {

        /**
         * Creates parsing statistics.
         *
         * @param sourceDescription logical source description
         * @param lineCount         number of physical lines read
         * @param entryCount        number of logical dictionary entries emitted
         * @param ignoredLineCount  number of ignored empty or remark-only lines
         * @throws NullPointerException     if {@code sourceDescription} is {@code null}
         * @throws IllegalArgumentException if any numeric value is negative
         */
        public ParseStatistics {
            Objects.requireNonNull(sourceDescription, "sourceDescription");
            if (lineCount < 0) {
                throw new IllegalArgumentException("lineCount must not be negative.");
            }
            if (entryCount < 0) {
                throw new IllegalArgumentException("entryCount must not be negative.");
            }
            if (ignoredLineCount < 0) {
                throw new IllegalArgumentException("ignoredLineCount must not be negative.");
            }
        }
    }
}
