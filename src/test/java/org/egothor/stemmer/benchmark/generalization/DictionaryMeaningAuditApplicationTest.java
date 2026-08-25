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
package org.egothor.stemmer.benchmark.generalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.egothor.stemmer.StemmerPatchTrieLoader;

/** Tests internal-evidence filtering without reading bundled dictionaries. */
final class DictionaryMeaningAuditApplicationTest {

    /** Temporary location for synthetic compressed dictionaries. */
    @TempDir
    private Path temporaryDirectory;

    /** Verifies ordinary UTF-16 edit distance, including empty inputs. */
    @Test
    void editDistanceMeasuresMinimumCharacterOperations() {
        assertEquals(0, DictionaryMeaningAuditApplication.editDistance("gehen", "gehen"));
        assertEquals(3, DictionaryMeaningAuditApplication.editDistance("kitten", "sitting"));
        assertEquals(4, DictionaryMeaningAuditApplication.editDistance("", "Haus"));
    }

    /** Verifies that rare, substantially worse duplicate assignments are removed. */
    @Test
    void dominatedMappingRequiresStrongInternalAlternative() {
        final DictionaryMeaningAuditApplication.BestAssignment best =
                new DictionaryMeaningAuditApplication.BestAssignment("hauptmann", "Dc", 8L, 2, false);

        assertTrue(DictionaryMeaningAuditApplication.isDominated("enthaupten", 7, 1L, best));
        assertFalse(DictionaryMeaningAuditApplication.isDominated("hauptmann", 7, 1L, best));
        assertFalse(DictionaryMeaningAuditApplication.isDominated("enthaupten", 7, 3L, best));
        assertFalse(DictionaryMeaningAuditApplication.isDominated("enthaupten", 3, 1L, best));
    }

    /** Verifies that uncorroborated rare divergence is retained for review. */
    @Test
    void rareDivergenceIsReviewEvidenceRatherThanAutomaticRemoval() {
        assertTrue(DictionaryMeaningAuditApplication.isRareDivergent(
                "hauptmännern", "enthaupten", 8, 1L));
        assertFalse(DictionaryMeaningAuditApplication.isRareDivergent(
                "ging", "gehen", 3, 1L));
        assertFalse(DictionaryMeaningAuditApplication.isRareDivergent(
                "hauptmännern", "enthaupten", 8, 3L));
    }

    /** Verifies classification against a small ambiguous dictionary. */
    @Test
    void analysisRemovesOnlyDominatedDuplicateMappings() {
        final List<EditCostSensitivityApplication.DictionaryRow> rows = List.of(
                new EditCostSensitivityApplication.DictionaryRow(
                        1, "hauptmann", new String[] { "hauptmann", "hauptmänner", "hauptmännern" }),
                new EditCostSensitivityApplication.DictionaryRow(
                        2, "enthaupten", new String[] {
                                "enthauptet", "hauptmann", "hauptmänner", "hauptmännern" }),
                new EditCostSensitivityApplication.DictionaryRow(
                        3, "gehen", new String[] { "ging" }));

        final DictionaryMeaningAuditApplication.Analysis analysis =
                DictionaryMeaningAuditApplication.analyze(rows);

        assertEquals(3, analysis.removals().size());
        assertTrue(analysis.removals().contains(
                new DictionaryMeaningAuditApplication.MappingKey(2, "hauptmännern")));
        assertFalse(analysis.removals().contains(
                new DictionaryMeaningAuditApplication.MappingKey(3, "ging")));
    }

    /** Verifies objective cleanup without consulting an external dictionary. */
    @Test
    void sanitationRemovesArtifactsAndFormatCharacters() {
        final List<EditCostSensitivityApplication.DictionaryRow> rows = List.of(
                new EditCostSensitivityApplication.DictionaryRow(
                        8, "Überführung", new String[] {
                                "Audio", "file", "Überführung", "\u200eüberführen", "überf\"uhren" }));

        final DictionaryMeaningAuditApplication.SanitationResult result =
                DictionaryMeaningAuditApplication.sanitize(
                        rows, StemmerPatchTrieLoader.Language.DE_DE);

        assertEquals(1, result.rows().size());
        assertEquals(List.of("Überführung", "überführen", "überf\"uhren"),
                List.of(result.rows().getFirst().variants()));
        assertEquals(2, result.removedMappings());
        assertEquals(1, result.normalizedTokens());
        assertEquals(1, result.legacyEncodingReviews());
    }

    /** Verifies that whitespace reporting follows production parser rejection rules. */
    @Test
    void whitespaceAuditDistinguishesRejectedRowsAndVariants() throws IOException {
        final Path dictionary = temporaryDirectory.resolve("whitespace-dictionary.gz");
        try (OutputStream file = Files.newOutputStream(dictionary);
                GZIPOutputStream gzip = new GZIPOutputStream(file);
                OutputStreamWriter streamWriter = new OutputStreamWriter(gzip, StandardCharsets.UTF_8);
                BufferedWriter writer = new BufferedWriter(streamWriter)) {
            writer.write("# ignored comment with spaces");
            writer.newLine();
            writer.write("valid\tplain\ttwo words");
            writer.newLine();
            writer.write("two word stem\tvariant");
            writer.newLine();
            writer.write("another\tplain # trailing remark with spaces");
            writer.newLine();
        }

        final DictionaryMeaningAuditApplication.WhitespaceResult result =
                DictionaryMeaningAuditApplication.inspectWhitespace(dictionary);

        assertEquals(1, result.ignoredRows());
        assertEquals(1, result.ignoredVariants());
        assertEquals(List.of(
                DictionaryMeaningAuditApplication.SanitationDecision.PARSER_REJECT_WHITESPACE_VARIANT,
                DictionaryMeaningAuditApplication.SanitationDecision.PARSER_REJECT_WHITESPACE_ROW),
                result.evidence().stream()
                        .map(DictionaryMeaningAuditApplication.SanitationEvidence::decision)
                        .toList());
    }
}
