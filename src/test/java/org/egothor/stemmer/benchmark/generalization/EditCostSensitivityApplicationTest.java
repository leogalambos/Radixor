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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.egothor.stemmer.CompiledPatchCommand;
import org.egothor.stemmer.FrequencyTrie;
import org.egothor.stemmer.benchmark.generalization.EditCostSensitivityApplication.CostConfig;
import org.egothor.stemmer.benchmark.generalization.EditCostSensitivityApplication.DictionaryRow;
import org.egothor.stemmer.benchmark.generalization.EditCostSensitivityApplication.Viability;
import org.egothor.stemmer.benchmark.quality.GoldStandardGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the deterministic, in-memory building blocks used by
 * {@link EditCostSensitivityApplication}.
 *
 * <p>The tests deliberately use a small synthetic dictionary. They exercise
 * grid construction, configuration validation, deterministic ranking, trie
 * compilation, stemming, and report-schema invariants without reading bundled
 * model resources or writing report files.</p>
 */
final class EditCostSensitivityApplicationTest {

    private static final List<DictionaryRow> SAMPLE_ROWS = List.of(
            new DictionaryRow(1, "run", new String[] { "running", "runs", "runner" }),
            new DictionaryRow(2, "walk", new String[] { "walking", "walks", "walked" }),
            new DictionaryRow(3, "play", new String[] { "playing", "plays", "played" }));

    @Test
    @DisplayName("generateGrid should produce the expected number of cost combinations")
    void generateGridShouldProduceExpectedCombinations() {
        final List<CostConfig> grid = EditCostSensitivityApplication.generateGrid();
        final int expected = EditCostSensitivityApplication.EDIT_COST_VALUES.size()
                * EditCostSensitivityApplication.EDIT_COST_VALUES.size()
                * EditCostSensitivityApplication.EDIT_COST_VALUES.size()
                * EditCostSensitivityApplication.MATCH_COST_VALUES.size();
        assertEquals(expected, grid.size(), "The grid must contain every Cartesian-product combination.");
    }

    @Test
    @DisplayName("grid should contain the baseline configuration")
    void gridShouldContainBaselineConfiguration() {
        final List<CostConfig> grid = EditCostSensitivityApplication.generateGrid();
        assertTrue(grid.contains(EditCostSensitivityApplication.BASELINE),
                "The configured grid must include the production baseline costs.");
    }

    @Test
    @DisplayName("countDistinctPatchCommands should return positive count for sample rows")
    void countDistinctPatchCommandsShouldReturnPositiveCount() {
        final long count = EditCostSensitivityApplication.countDistinctPatchCommands(
                SAMPLE_ROWS, EditCostSensitivityApplication.BASELINE);
        assertTrue(count > 0L, "The sample dictionary must generate at least one patch command.");
    }

    @Test
    @DisplayName("countDistinctPatchCommands baseline should be at most the word count")
    void patchCommandCountShouldNotExceedWordCount() {
        final long count = EditCostSensitivityApplication.countDistinctPatchCommands(
                SAMPLE_ROWS, EditCostSensitivityApplication.BASELINE);
        long totalForms = 0L;
        for (final DictionaryRow row : SAMPLE_ROWS) {
            totalForms += row.forms().size();
        }
        assertTrue(count <= totalForms, "Distinct commands cannot outnumber encoded forms.");
    }

    @Test
    @DisplayName("classifyViability should classify correctly")
    void classifyViabilityShouldClassifyCorrectly() {
        assertEquals(Viability.VIABLE, EditCostSensitivityApplication.classifyViability(1.0),
                "Ratios below the marginal threshold must remain viable.");
        assertEquals(Viability.VIABLE, EditCostSensitivityApplication.classifyViability(5.0),
                "The marginal boundary itself must remain viable.");
        assertEquals(Viability.MARGINAL, EditCostSensitivityApplication.classifyViability(5.001),
                "A ratio just above the marginal boundary must be marginal.");
        assertEquals(Viability.MARGINAL, EditCostSensitivityApplication.classifyViability(10.0),
                "The non-viable boundary itself must remain marginal.");
        assertEquals(Viability.NOT_VIABLE, EditCostSensitivityApplication.classifyViability(10.001),
                "A ratio just above the non-viable boundary must be rejected.");
        assertEquals(Viability.NOT_VIABLE, EditCostSensitivityApplication.classifyViability(100.0),
                "Ratios far above the non-viable threshold must be rejected.");
    }

    @Test
    @DisplayName("buildCompiledTrie with baseline costs should return a non-null trie")
    void buildCompiledTrieShouldReturnNonNullTrie() {
        final FrequencyTrie<CompiledPatchCommand> trie = EditCostSensitivityApplication.buildCompiledTrie(
                SAMPLE_ROWS, EditCostSensitivityApplication.BASELINE);
        assertNotNull(trie, "Trie compilation must return a usable trie.");
    }

    @Test
    @DisplayName("stem should return the correct stem for training words")
    void stemShouldReturnCorrectStemForTrainingWords() {
        final FrequencyTrie<CompiledPatchCommand> trie = EditCostSensitivityApplication.buildCompiledTrie(
                SAMPLE_ROWS, EditCostSensitivityApplication.BASELINE);
        assertEquals("run", EditCostSensitivityApplication.stem(trie, "running"),
                "A trained variant must map to its run stem.");
        assertEquals("walk", EditCostSensitivityApplication.stem(trie, "walking"),
                "A trained variant must map to its walk stem.");
        assertEquals("play", EditCostSensitivityApplication.stem(trie, "playing"),
                "A trained variant must map to its play stem.");
    }

    @Test
    @DisplayName("costConfig label should encode all four cost values")
    void costConfigLabelShouldEncodeAllFourValues() {
        final CostConfig config = new CostConfig(2, 3, 5, 1);
        assertEquals("D2I3R5M1", config.label(), "The label must encode costs in D-I-R-M order.");
    }

    @Test
    @DisplayName("costConfig should reject negative operation costs")
    void costConfigShouldRejectNegativeCosts() {
        assertThrows(IllegalArgumentException.class, () -> new CostConfig(-1, 1, 1, 0),
                "Negative operation costs must be rejected at construction time.");
    }

    @Test
    @DisplayName("rankRows should return the same size list")
    void rankRowsShouldReturnSameSizeList() {
        final List<DictionaryRow> ranked = EditCostSensitivityApplication.rankRows(
                SAMPLE_ROWS, "test-model", EditCostSensitivityApplication.DEFAULT_SEED);
        assertEquals(SAMPLE_ROWS.size(), ranked.size(), "Ranking must neither add nor remove dictionary rows.");
    }

    @Test
    @DisplayName("rankRows should be deterministic for the same seed")
    void rankRowsShouldBeDeterministic() {
        final List<DictionaryRow> first = EditCostSensitivityApplication.rankRows(
                SAMPLE_ROWS, "test-model", EditCostSensitivityApplication.DEFAULT_SEED);
        final List<DictionaryRow> second = EditCostSensitivityApplication.rankRows(
                SAMPLE_ROWS, "test-model", EditCostSensitivityApplication.DEFAULT_SEED);
        for (int index = 0; index < first.size(); index++) {
            assertEquals(first.get(index).stem(), second.get(index).stem(),
                    "The same seed must produce the same row at index " + index + '.');
        }
    }

    @Test
    @DisplayName("toGoldGroups should produce one group per dictionary row")
    void toGoldGroupsShouldProduceOneGroupPerRow() {
        final List<GoldStandardGroup> groups = EditCostSensitivityApplication.toGoldGroups(SAMPLE_ROWS);
        assertEquals(SAMPLE_ROWS.size(), groups.size(), "Every dictionary row must produce one gold group.");
    }

    @Test
    @DisplayName("different valid costs should both produce patch commands")
    void differentValidCostsShouldProducePatchCommands() {
        final long baseline = EditCostSensitivityApplication.countDistinctPatchCommands(
                SAMPLE_ROWS, EditCostSensitivityApplication.BASELINE);
        final long expensive = EditCostSensitivityApplication.countDistinctPatchCommands(
                SAMPLE_ROWS, new CostConfig(10, 1, 1, 0));
        assertTrue(baseline > 0L, "Baseline costs must produce patch commands.");
        assertTrue(expensive > 0L, "Alternative valid costs must produce patch commands.");
    }

    @Test
    @DisplayName("header should contain expected key column names")
    void headerShouldContainExpectedColumnNames() {
        assertTrue(EditCostSensitivityApplication.HEADER.contains("delete_cost"),
                "The report schema must identify delete costs.");
        assertTrue(EditCostSensitivityApplication.HEADER.contains("patch_command_count"),
                "The report schema must expose patch-command counts.");
        assertTrue(EditCostSensitivityApplication.HEADER.contains("viability"),
                "The report schema must expose viability.");
        assertTrue(EditCostSensitivityApplication.HEADER.contains("unseen_correct"),
                "The report schema must expose unseen-form correctness.");
        assertTrue(EditCostSensitivityApplication.HEADER.contains("trie_internal_nodes"),
                "The report schema must expose trie structure.");
        assertTrue(EditCostSensitivityApplication.HEADER.contains("over_error_pairs"),
                "The report schema must expose over-stemming errors.");
        assertTrue(EditCostSensitivityApplication.HEADER.contains("mcc"),
                "The report schema must expose Matthews correlation.");
    }

    @Test
    @DisplayName("grid configurations should have valid non-negative costs")
    void gridConfigurationsShouldHaveNonNegativeCosts() {
        final List<CostConfig> configs = new ArrayList<>();
        configs.add(EditCostSensitivityApplication.BASELINE);
        configs.addAll(EditCostSensitivityApplication.generateGrid());
        for (final CostConfig config : configs) {
            assertTrue(config.deleteCost() >= 0, "Delete cost must not be negative.");
            assertTrue(config.insertCost() >= 0, "Insert cost must not be negative.");
            assertTrue(config.replaceCost() >= 0, "Replace cost must not be negative.");
            assertTrue(config.matchCost() >= 0, "Match cost must not be negative.");
        }
    }
}
