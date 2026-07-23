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
package org.egothor.stemmer.benchmark.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** UTF-8, formatting, ordering, escaping, and write-failure tests for reports. */
@Tag("unit")
@DisplayName("Stemming-quality report writer")
final class QualityReportWriterTest {
    /** Temporary output directory owned by JUnit. */
    @TempDir Path temporaryDirectory;

    /** Verifies required Markdown semantics and deterministic row ordering. */
    @Test @DisplayName("Markdown contains the required English columns and deterministic metrics")
    void markdownFormat() throws IOException {
        final Path report = this.temporaryDirectory.resolve("report.md");
        QualityReportWriter.writeMarkdown(report, List.of(result("Zulu", "B", 1, 2), result("Alpha|Stemmer", "A", 0, 0)), false);
        final String text = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(text.contains("| Stemmer | Language | Dictionary mode | Output policy | Applied dictionary rows | Processed word forms | Distinct output stems | Over-stemming | Under-stemming | Pairwise F0.5 | Pairwise F1 | Pairwise F2 |"));
        assertTrue(text.contains("0 / 0 (n/a)"));
        assertTrue(text.contains("1 / 2 (50.000000%)"));
        assertTrue(text.indexOf("Alpha\\|Stemmer") < text.indexOf("Zulu"));
        assertEquals(text, new String(Files.readAllBytes(report), StandardCharsets.UTF_8));
    }

    /** Verifies CSV headers, separate missing fields, ordering, and quoting. */
    @Test @DisplayName("CSV uses separate English columns, correct quoting, and empty undefined percentages")
    void csvFormat() throws IOException {
        final Path report = this.temporaryDirectory.resolve("report.csv");
        QualityReportWriter.writeCsv(report, List.of(result("Stemmer, \"quoted\"", "A", 0, 0)));
        final String text = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("Stemmer,Language,Dictionary model ID,Dictionary model version,Dictionary model SHA-256,Dictionary mode,Output policy,Applied dictionary rows,Processed word forms,Singleton dictionary rows,Forms with one candidate,"));
        assertTrue(text.contains("\"Stemmer, \"\"quoted\"\"\""));
        assertTrue(text.contains("Adjusted Rand Index,Homogeneity,Completeness,V-measure,Normalized mutual information"));
    }

    /** Verifies that filesystem failures are propagated. */
    @Test @DisplayName("A report write failure is propagated")
    void writeFailure() throws IOException {
        final Path file = this.temporaryDirectory.resolve("parent-file");
        Files.writeString(file, "occupied", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> QualityReportWriter.writeMarkdown(file.resolve("report.md"), List.of(), false));
    }

    /** Verifies that a second generation replaces stale content instead of appending. */
    @Test @DisplayName("Report generation replaces stale content")
    void reportReplacement() throws IOException {
        final Path report = this.temporaryDirectory.resolve("replacement.md");
        QualityReportWriter.writeMarkdown(report, List.of(result("Old", "A", 0, 1)), false);
        QualityReportWriter.writeMarkdown(report, List.of(result("New", "B", 0, 1)), false);
        final String text = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(text.contains("New"));
        assertTrue(!text.contains("Old"));
    }

    /** Creates a compact valid result for formatting tests. */
    private static QualityResult result(final String stemmer, final String language, final long errors, final long possible) {
        return new QualityResult(stemmer, language, ProcessingMode.ALL_WORDS, OutputPolicy.PRIMARY_OUTPUT,
                1, 1, 1, 0, 1, 0, 1, 1, 1, errors, possible, 0, 0,
                new PartitionMetrics(1.0, 1.0, 1.0, 1.0, 1.0));
    }
}
