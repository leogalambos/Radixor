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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link StemmerKnowledgeExperiment}.
 */
@Tag("unit")
@Tag("integration")
@Tag("stemmer")
final class StemmerKnowledgeExperimentTest {

    /**
     * Deterministic seed used by all tests.
     */
    private static final long TEST_SEED = 20260421L;

    /**
     * Small deterministic morphology-shaped dictionary.
     */
    private static final String DICTIONARY = String.join(System.lineSeparator(), "run	running	runs	runner",
            "walk	walking	walks	walked", "play	playing	plays	played");

    /**
     * Temporary directory for report writing tests.
     */
    @TempDir
    private Path tempDir;

    /**
     * Verifies deterministic scenario generation and expected row count.
     *
     * @throws IOException if evaluation fails
     */
    @Test
    @DisplayName("evaluate should return deterministic full scenario matrix")
    void evaluateShouldReturnDeterministicScenarioMatrix() throws IOException {
        final StemmerKnowledgeExperiment experiment = new StemmerKnowledgeExperiment();

        final List<StemmerKnowledgeExperiment.ResultRow> first = experiment.evaluate(new StringReader(DICTIONARY),
                "synthetic", "SYNTHETIC", TEST_SEED);
        final List<StemmerKnowledgeExperiment.ResultRow> second = experiment.evaluate(new StringReader(DICTIONARY),
                "synthetic", "SYNTHETIC", TEST_SEED);

        assertEquals(ReductionMode.values().length * 2 * 2 * 10, first.size());
        assertEquals(first, second);
    }

    /**
     * Verifies that full knowledge with stored original stems reaches ideal
     * quality.
     *
     * @throws IOException if evaluation fails
     */
    @Test
    @DisplayName("100 percent knowledge with stored originals should achieve perfect scores")
    void fullKnowledgeWithStoredOriginalsShouldBePerfect() throws IOException {
        final StemmerKnowledgeExperiment experiment = new StemmerKnowledgeExperiment();
        final List<StemmerKnowledgeExperiment.ResultRow> rows = experiment.evaluate(new StringReader(DICTIONARY),
                "synthetic", "SYNTHETIC", TEST_SEED);

        final StemmerKnowledgeExperiment.ResultRow row = uniqueRow(rows,
                resultKey(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS, true, true, 100));

        assertEquals(1.0d, row.getAccuracy());
        assertEquals(1.0d, row.getAllPrecision());
        assertEquals(1.0d, row.getAllRecall());
        assertEquals(1.0d, row.getAllF1());
    }

    /**
     * Verifies that evaluating canonical stems without storing no-op patches lowers
     * recall at full knowledge, while {@code get()} still remains perfect due to
     * the implicit identity fallback for already canonical inputs.
     *
     * @throws IOException if evaluation fails
     */
    @Test
    @DisplayName("evaluating stems without stored originals should reduce recall but preserve get accuracy")
    void evaluatingStemsWithoutStoredOriginalsShouldReduceRecall() throws IOException {
        final StemmerKnowledgeExperiment experiment = new StemmerKnowledgeExperiment();
        final List<StemmerKnowledgeExperiment.ResultRow> rows = experiment.evaluate(new StringReader(DICTIONARY),
                "synthetic", "SYNTHETIC", TEST_SEED);

        final StemmerKnowledgeExperiment.ResultRow row = uniqueRow(rows,
                resultKey(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS, false, true, 100));

        assertTrue(row.getAllRecall() < 1.0d);
        assertEquals(1.0d, row.getAccuracy());
        assertTrue(row.getAllF1() < 1.0d);
    }

    /**
     * Verifies that storing original stems becomes irrelevant when canonical stems
     * themselves are not part of the evaluated input set.
     *
     * @throws IOException if evaluation fails
     */
    @Test
    @DisplayName("storeOriginal should not affect scores when stems are not evaluated")
    void storeOriginalShouldNotAffectScoresWhenStemsAreNotEvaluated() throws IOException {
        final StemmerKnowledgeExperiment experiment = new StemmerKnowledgeExperiment();
        final List<StemmerKnowledgeExperiment.ResultRow> rows = experiment.evaluate(new StringReader(DICTIONARY),
                "synthetic", "SYNTHETIC", TEST_SEED);

        final StemmerKnowledgeExperiment.ResultRow withoutStoredOriginals = uniqueRow(rows,
                resultKey(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS, false, false, 100));
        final StemmerKnowledgeExperiment.ResultRow withStoredOriginals = uniqueRow(rows,
                resultKey(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS, true, false, 100));

        assertEquals(withoutStoredOriginals.getAccuracy(), withStoredOriginals.getAccuracy());
        assertEquals(withoutStoredOriginals.getAllPrecision(), withStoredOriginals.getAllPrecision());
        assertEquals(withoutStoredOriginals.getAllRecall(), withStoredOriginals.getAllRecall());
        assertEquals(withoutStoredOriginals.getAllF1(), withStoredOriginals.getAllF1());
        assertEquals(withoutStoredOriginals.getCorrectCount(), withStoredOriginals.getCorrectCount());
        assertEquals(withoutStoredOriginals.getAllTruePositiveCount(), withStoredOriginals.getAllTruePositiveCount());
        assertEquals(withoutStoredOriginals.getAllFalsePositiveCount(), withStoredOriginals.getAllFalsePositiveCount());
        assertEquals(withoutStoredOriginals.getAllCoveredInputCount(), withStoredOriginals.getAllCoveredInputCount());
    }

    /**
     * Verifies that implicit identity fallback for {@code get()} does not propagate
     * into {@code getAll()}, which still requires an explicit command to cover an
     * input.
     *
     * @throws IOException if evaluation fails
     */
    @Test
    @DisplayName("get should accept implicit identity while getAll still requires explicit coverage")
    void getShouldAcceptImplicitIdentityWhileGetAllStillRequiresExplicitCoverage() throws IOException {
        final StemmerKnowledgeExperiment experiment = new StemmerKnowledgeExperiment();
        final String minimalDictionary = "run	running";

        final List<StemmerKnowledgeExperiment.ResultRow> rows = experiment.evaluate(new StringReader(minimalDictionary),
                "minimal", "MINIMAL", TEST_SEED);

        final StemmerKnowledgeExperiment.ResultRow row = uniqueRow(rows,
                resultKey(ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS, false, true, 100));

        assertEquals(2L, row.evaluatedInputCount());
        assertEquals(2L, row.getCorrectCount());
        assertEquals(1.0d, row.getAccuracy());

        assertEquals(1L, row.getAllCoveredInputCount());
        assertEquals(0.5d, row.getAllRecall());
        assertTrue(row.getAllPrecision() > 0.0d);
        assertTrue(row.getAllPrecision() <= 1.0d);
        assertTrue(row.getAllF1() < 1.0d);
    }

    /**
     * Verifies CSV report generation.
     *
     * @throws IOException if report writing fails
     */
    @Test
    @DisplayName("writeCsv should emit header and data rows")
    void writeCsvShouldEmitHeaderAndDataRows() throws IOException {
        final StemmerKnowledgeExperiment experiment = new StemmerKnowledgeExperiment();
        final List<StemmerKnowledgeExperiment.ResultRow> rows = experiment.evaluate(new StringReader(DICTIONARY),
                "synthetic", "SYNTHETIC", TEST_SEED);

        final Path output = this.tempDir.resolve("knowledge.csv");
        StemmerKnowledgeExperiment.writeCsv(output, rows);

        final List<String> writtenLines = Files.readAllLines(output, StandardCharsets.UTF_8);
        assertFalse(writtenLines.isEmpty());
        assertEquals(StemmerKnowledgeExperiment.ResultRow.csvHeader(), writtenLines.get(0));
        assertEquals(rows.size() + 1, writtenLines.size());
    }

    /**
     * Verifies that the result row key lookup remains stable for all generated
     * rows.
     *
     * @throws IOException if evaluation fails
     */
    @Test
    @DisplayName("all generated rows should be addressable by the synthetic key")
    void allGeneratedRowsShouldBeAddressableBySyntheticKey() throws IOException {
        final StemmerKnowledgeExperiment experiment = new StemmerKnowledgeExperiment();
        final List<StemmerKnowledgeExperiment.ResultRow> rows = experiment.evaluate(new StringReader(DICTIONARY),
                "synthetic", "SYNTHETIC", TEST_SEED);

        for (StemmerKnowledgeExperiment.ResultRow row : rows) {
            assertDoesNotThrow(() -> uniqueRow(rows, resultKey(row)));
        }
    }

    /**
     * Finds one unique row by a synthetic key.
     *
     * @param rows result rows
     * @param key  synthetic key
     * @return matching row
     */
    private static StemmerKnowledgeExperiment.ResultRow uniqueRow(final List<StemmerKnowledgeExperiment.ResultRow> rows,
            final String key) {
        final Map<String, StemmerKnowledgeExperiment.ResultRow> indexed = rows.stream()
                .collect(Collectors.toMap(StemmerKnowledgeExperimentTest::resultKey, Function.identity()));
        final StemmerKnowledgeExperiment.ResultRow row = indexed.get(key);
        assertNotNull(row);
        return row;
    }

    /**
     * Creates a lookup key from a row.
     *
     * @param row result row
     * @return lookup key
     */
    private static String resultKey(final StemmerKnowledgeExperiment.ResultRow row) {
        return resultKey(ReductionMode.valueOf(row.reductionMode()), row.storeOriginal(), row.includeStemInEvaluation(),
                row.knowledgePercent());
    }

    /**
     * Creates a lookup key from scenario components.
     *
     * @param reductionMode           reduction mode
     * @param storeOriginal           whether no-op patches were stored
     * @param includeStemInEvaluation whether stems were evaluated
     * @param knowledgePercent        knowledge percentage
     * @return lookup key
     */
    private static String resultKey(final ReductionMode reductionMode, final boolean storeOriginal,
            final boolean includeStemInEvaluation, final int knowledgePercent) {
        return reductionMode.name() + '|' + storeOriginal + '|' + includeStemInEvaluation + '|' + knowledgePercent;
    }
}
