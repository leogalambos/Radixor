"""Unit tests for edit-cost experiment validation and analysis helpers."""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "update-edit-cost-documentation.py"
SPEC = importlib.util.spec_from_file_location("update_edit_cost_documentation", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class EditCostDocumentationTest(unittest.TestCase):
    """Checks protocol constants and statistics without reading repository reports."""

    def test_normalized_grid_has_expected_size_and_baseline(self) -> None:
        grid = MODULE.normalized_grid()
        self.assertEqual(234, len(grid))
        self.assertIn("D1I1R1M0", grid)
        self.assertNotIn("D10I10R10M0", grid)

    def test_ranks_average_ties(self) -> None:
        self.assertEqual([0.0, 1.5, 1.5, 3.0], MODULE.ranks([1.0, 2.0, 2.0, 4.0]))

    def test_pearson_detects_opposite_linear_order(self) -> None:
        self.assertAlmostEqual(-1.0, MODULE.pearson([1.0, 2.0, 3.0], [6.0, 4.0, 2.0]))

    def test_pearson_rejects_constant_input(self) -> None:
        self.assertIsNone(MODULE.pearson([1.0, 1.0, 1.0], [1.0, 2.0, 3.0]))

    def test_derived_csv_uses_repository_line_endings(self) -> None:
        rendered = MODULE.csv_text([{"language": "US_UK", "cost": "D1I1R1M0"}])

        self.assertEqual("language,cost\nUS_UK,D1I1R1M0\n", rendered)
        self.assertNotIn("\r", rendered)

    def test_update_rejects_its_target_archive_as_source(self) -> None:
        archive = Path("docs/benchmarks/data/edit-cost-sensitivity.csv.gz")

        with self.assertRaisesRegex(ValueError, "source must differ"):
            MODULE.validate_update_paths(archive, archive, "update")
        MODULE.validate_update_paths(archive, archive, "verify")

    def test_language_section_is_inserted_and_replaced_once(self) -> None:
        document = "# Language\n\n<!-- STEMMING-QUALITY:START -->\nquality\n"

        inserted = MODULE.replace_language_section(document, "## Experiment\n\nfirst")
        replaced = MODULE.replace_language_section(inserted, "## Experiment\n\nsecond")

        self.assertEqual(1, replaced.count(MODULE.LANGUAGE_SECTION_START))
        self.assertEqual(1, replaced.count(MODULE.LANGUAGE_SECTION_END))
        self.assertNotIn("first", replaced)
        self.assertIn("second", replaced)
        self.assertLess(replaced.index(MODULE.LANGUAGE_SECTION_START),
                        replaced.index("<!-- STEMMING-QUALITY:START -->"))

    def test_dictionary_sensitivity_uses_language_level_observations(self) -> None:
        features = {
            "A": {"rows": 10.0, "forms": 20.0, "mean_family_size": 2.0,
                  "changed_share": 0.2, "baseline_patch_commands": 5.0,
                  "equivalence_classes": 3.0},
            "B": {"rows": 20.0, "forms": 40.0, "mean_family_size": 3.0,
                  "changed_share": 0.4, "baseline_patch_commands": 10.0,
                  "equivalence_classes": 6.0},
            "C": {"rows": 30.0, "forms": 60.0, "mean_family_size": 4.0,
                  "changed_share": 0.6, "baseline_patch_commands": 15.0,
                  "equivalence_classes": 9.0},
        }

        rows = MODULE.dictionary_sensitivity_rows(features)

        self.assertEqual(5, len(rows))
        self.assertTrue(all(row["languages"] == "3" for row in rows))
        self.assertTrue(all(row["spearman"] == "1.000000" for row in rows))

        recommendations = [
            {"language": "A", "delete_cost": "1", "insert_cost": "3", "replace_cost": "5",
             "match_cost": "0", "median_patch_command_ratio": "0.7", "delta_vs_baseline_pp": "0.1"},
            {"language": "B", "delete_cost": "2", "insert_cost": "2", "replace_cost": "3",
             "match_cost": "1", "median_patch_command_ratio": "0.8", "delta_vs_baseline_pp": "0.2"},
            {"language": "C", "delete_cost": "3", "insert_cost": "1", "replace_cost": "2",
             "match_cost": "0", "median_patch_command_ratio": "0.9", "delta_vs_baseline_pp": "0.3"},
        ]
        recommendation_rows = MODULE.dictionary_recommendation_association_rows(features, recommendations)
        self.assertEqual(30, len(recommendation_rows))
        self.assertIn(("baseline_patch_commands", "recommended_command_ratio"),
                      {(row["predictor"], row["outcome"]) for row in recommendation_rows})

    def test_correlation_matrix_covers_cost_structure_and_quality(self) -> None:
        costs = ((1, 1, 1, 0), (2, 1, 3, 1), (3, 2, 1, 0), (5, 3, 2, 1))
        observations = [
            MODULE.Observation(
                "US_UK", "2654435761", 10, f"C{index}", *cost,
                0.8 + index / 10, 100 + index, 120 + 2 * index,
                5 + index, 2.0 + index / 10,
                200 + 3 * index, 50 + index, 75 + 2 * index,
                80.0 + index, 0.8 + index / 100,
                10.0 - index, 5.0 + index, "VIABLE")
            for index, cost in enumerate(costs)
        ]

        rows = MODULE.correlation_rows(observations)

        self.assertEqual(123, len(rows))
        self.assertEqual(123, len({(row["predictor"], row["outcome"]) for row in rows}))
        self.assertIn(("replace_to_delete_insert", "trie_nodes"),
                      {(row["predictor"], row["outcome"]) for row in rows})
        self.assertIn(("trie_edges", "unseen_f05"),
                      {(row["predictor"], row["outcome"]) for row in rows})

        documented = MODULE.methodology()
        labels = {row["predictor"] for row in rows} | {row["outcome"] for row in rows}
        for label in labels:
            with self.subTest(label=label):
                self.assertIn(f"`{label}`", documented)

    def test_methodology_defines_dictionary_and_recommendation_labels(self) -> None:
        documented = MODULE.methodology()
        labels = {
            "dictionary_rows", "dictionary_forms", "mean_family_size", "changed_form_share",
            "baseline_patch_commands", "exact_equivalence_classes", "recommended_delete_cost",
            "recommended_insert_cost", "recommended_replace_cost", "recommended_match_cost",
            "recommended_command_ratio", "recommended_exact_delta_pp",
        }

        for label in labels:
            with self.subTest(label=label):
                self.assertIn(f"`{label}`", documented)

    def test_knowledge_curve_compares_same_language_seed_matrix(self) -> None:
        observations = []
        for language, recommended_exact, recommended_ratio in (
                ("CS_CZ", 82.0, 0.8), ("DA_DK", 84.0, 0.9)):
            for seed in MODULE.SEEDS:
                observations.extend((
                    MODULE.Observation(language, seed, 10, MODULE.BASELINE, 1, 1, 1, 0,
                                       1.0, 100, 120, 5, 2.0, 200, 50, 75,
                                       80.0, 0.80, 10.0, 5.0, "VIABLE"),
                    MODULE.Observation(language, seed, 10, "D1I1R2M0", 1, 1, 2, 0,
                                       recommended_ratio, 90, 110, 5, 1.9, 180, 45, 70,
                                       recommended_exact, 0.82, 9.0, 4.0, "VIABLE"),
                ))
        recommendations = [
            {"language": "CS_CZ", "recommended_cost": "D1I1R2M0"},
            {"language": "DA_DK", "recommended_cost": "D1I1R2M0"},
        ]

        rows = MODULE.knowledge_curve_rows(observations, recommendations)

        self.assertEqual(1, len(rows))
        self.assertEqual("3.000000", rows[0]["exact_delta_pp"])
        self.assertEqual("0.850000", rows[0]["recommended_patch_command_ratio"])

        language_rows = MODULE.language_knowledge_curve_rows(observations, recommendations)
        self.assertEqual(2, len(language_rows))
        self.assertEqual({"CS_CZ", "DA_DK"}, {row["language"] for row in language_rows})
        self.assertEqual("2.000000", language_rows[0]["exact_delta_pp"])
        self.assertEqual("4.000000", language_rows[1]["exact_delta_pp"])

    def test_structural_and_quality_optima_use_distinct_objectives(self) -> None:
        observations = []
        candidates = {
            MODULE.BASELINE: (1, 1, 1, 0, 1.0, 80.0, 0.80, 2.0, 20.0),
            "D1I1R2M0": (1, 1, 2, 0, 0.5, 79.0, 0.82, 1.9, 19.0),
            "D1I1R3M0": (1, 1, 3, 0, 0.5, 79.5, 0.81, 1.8, 19.5),
            "D1I1R5M0": (1, 1, 5, 0, 0.7, 82.0, 0.90, 2.1, 10.0),
        }
        for label, values in candidates.items():
            delete, insert, replace, match, ratio, exact, f05, over, under = values
            for seed in MODULE.SEEDS:
                for percent in range(10, 100, 10):
                    observations.append(MODULE.Observation(
                        "CS_CZ", seed, percent, label, delete, insert, replace, match,
                        ratio, 100, 120, 5, 2.0, 200, 50, 75,
                        exact, f05, over, under, "VIABLE"))
                observations.append(MODULE.Observation(
                    "CS_CZ", seed, 100, label, delete, insert, replace, match,
                    ratio, 100, 120, 5, 2.0, 200, 50, 75,
                    None, None, None, None, "VIABLE", 4.0))

        minimum = MODULE.minimum_command_rows(
            observations, {"CS_CZ": {"baseline_patch_commands": 100.0}})
        quality = MODULE.quality_optimum_rows(observations)

        self.assertEqual("D1I1R2M0", minimum[0]["minimum_command_cost"])
        self.assertEqual("50", minimum[0]["minimum_commands"])
        self.assertEqual("2", minimum[0]["tied_minima"])
        self.assertEqual("D1I1R2M0", quality[0]["quality_cost"])
        self.assertEqual("-0.100000000", quality[0]["over_delta_vs_baseline_pp"])
        self.assertEqual("-1.000000000", quality[0]["under_delta_vs_baseline_pp"])
        self.assertEqual("4.000000000", quality[0]["full_model_under_percent"])
        self.assertEqual("0.020000000", quality[0]["f05_delta_vs_baseline"])


if __name__ == "__main__":
    unittest.main()
