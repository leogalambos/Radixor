#!/usr/bin/env python3
"""Validate, analyze, publish, or verify the edit-cost sensitivity experiment."""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import math
import re
import shutil
import statistics
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path


PROTOCOL = "radixor-cost-sensitivity-v4"
LANGUAGES = {
    "CS_CZ": "Czech", "DA_DK": "Danish", "DE_DE": "German",
    "ES_ES": "Spanish", "FA_IR": "Persian", "FI_FI": "Finnish",
    "FR_FR": "French", "HE_IL": "Hebrew", "HU_HU": "Hungarian",
    "IT_IT": "Italian", "NB_NO": "Norwegian Bokmål", "NL_NL": "Dutch",
    "NN_NO": "Norwegian Nynorsk", "PL_PL": "Polish",
    "PT_PT": "Portuguese", "RU_RU": "Russian", "SV_SE": "Swedish",
    "UK_UA": "Ukrainian", "US_UK": "English", "YI": "Yiddish",
}
LANGUAGE_PAGES = {
    "CS_CZ": "czech.md", "DA_DK": "danish.md", "DE_DE": "german.md",
    "ES_ES": "spanish.md", "FA_IR": "persian.md", "FI_FI": "finnish.md",
    "FR_FR": "french.md", "HE_IL": "hebrew.md", "HU_HU": "hungarian.md",
    "IT_IT": "italian.md", "NB_NO": "norwegian-bokmal.md", "NL_NL": "dutch.md",
    "NN_NO": "norwegian-nynorsk.md", "PL_PL": "polish.md",
    "PT_PT": "portuguese.md", "RU_RU": "russian.md", "SV_SE": "swedish.md",
    "UK_UA": "ukrainian.md", "US_UK": "english.md", "YI": "yiddish.md",
}
SEEDS = {
    "2654435761", "2611923443488327891", "7046029254386353131",
    "11400714819323198485", "15111065706836454659",
}
PERCENTS = set(range(10, 101, 10))
BASELINE = "D1I1R1M0"
QUALITY_TOLERANCE_PERCENTAGE_POINTS = 0.25
LANGUAGE_CORRELATION_STRATA = len(SEEDS) * 9
LANGUAGE_SECTION_START = "<!-- EDIT-COST-GENERALIZATION:START -->"
LANGUAGE_SECTION_END = "<!-- EDIT-COST-GENERALIZATION:END -->"


@dataclass(frozen=True, slots=True)
class Observation:
    """Compact analysis projection of one validated measurement row."""

    language: str
    seed: str
    percent: int
    label: str
    delete: int
    insert: int
    replace: int
    match: int
    command_ratio: float
    nodes: int
    edges: int
    longest_path: int
    average_path: float
    dense_table_slots: int
    value_references: int
    logical_leaf_paths: int
    unseen_changed_exact: float | None
    unseen_f05: float | None
    unseen_over: float | None
    unseen_under: float | None
    viability: str
    whole_under: float | None = None


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("documentation_root", type=Path)
    parser.add_argument("mode", choices=("update", "verify", "check"))
    parser.add_argument("--allow-partial", action="store_true")
    return parser.parse_args()


def normalized_grid() -> set[str]:
    labels: set[str] = set()
    ratios: set[tuple[int, int, int, int]] = set()
    for delete in (1, 2, 3, 5, 10):
        for insert in (1, 2, 3, 5, 10):
            for replace in (1, 2, 3, 5, 10):
                for match in (0, 1):
                    divisor = math.gcd(math.gcd(delete, insert), math.gcd(replace, match))
                    ratio = (delete // divisor, insert // divisor, replace // divisor, match // divisor)
                    if ratio not in ratios:
                        ratios.add(ratio)
                        labels.add(f"D{delete}I{insert}R{replace}M{match}")
    if len(labels) != 234:
        raise AssertionError("The predeclared normalized grid must contain 234 configurations.")
    return labels


def open_text(path: Path):
    return gzip.open(path, "rt", encoding="utf-8", newline="") if path.suffix == ".gz" \
        else path.open(encoding="utf-8", newline="")


def optional_float(value: str) -> float | None:
    if not value:
        return None
    result = float(value)
    if not math.isfinite(result):
        raise ValueError("A metric contains NaN or infinity.")
    return result


def exact_percentage(correct: str, total: str) -> float | None:
    denominator = int(total)
    return None if denominator == 0 else 100.0 * int(correct) / denominator


def read_and_validate(path: Path, allow_partial: bool) -> tuple[list[Observation], dict[str, str], dict[str, dict[str, float]]]:
    expected_costs = normalized_grid()
    observations: list[Observation] = []
    identities: dict[str, set[str]] = defaultdict(set)
    keys: set[tuple[str, str, int, str]] = set()
    model_identity: dict[str, tuple[str, str, str]] = {}
    features: dict[str, dict[str, float]] = {}
    equivalence_classes: dict[str, set[tuple[str, ...]]] = defaultdict(set)
    with open_text(path) as handle:
        reader = csv.DictReader(handle)
        required = {
            "protocol_version", "record_type", "radixor_java_version", "source_revision",
            "source_state", "generator_sha256", "language", "model_id", "model_version",
            "model_sha256", "seed", "delete_cost", "insert_cost", "replace_cost",
            "match_cost", "cost_label", "training_percent", "selected_rows", "total_rows",
            "equivalent_cost_labels",
            "withheld_rows", "training_generated_distinct_patch_commands",
            "baseline_training_generated_distinct_patch_commands", "trie_distinct_patch_commands",
            "baseline_trie_distinct_patch_commands", "trie_distinct_patch_command_ratio",
            "viability", "whole_correct", "whole_total", "whole_changed_correct",
            "whole_changed_total", "whole_root_correct", "whole_root_total",
            "withheld_total", "unseen_total", "excluded_overlap_occurrences",
            "unseen_changed_correct", "unseen_changed_total", "trie_internal_nodes",
            "trie_leaves", "trie_edges", "trie_longest_path", "trie_avg_path_length",
            "trie_dense_table_slots", "trie_value_references", "trie_logical_leaf_paths",
            "unseen_f05", "unseen_over_percent", "unseen_under_percent",
            "whole_under_percent",
        }
        if reader.fieldnames is None or required.difference(reader.fieldnames):
            raise ValueError(f"Missing edit-cost columns: {sorted(required.difference(reader.fieldnames or []))}")
        for line_number, row in enumerate(reader, 2):
            if row["protocol_version"] != PROTOCOL or row["record_type"] != "MEASUREMENT":
                raise ValueError(f"Unexpected protocol or record type at line {line_number}.")
            language = row["language"]
            seed = row["seed"]
            percent = int(row["training_percent"])
            label = row["cost_label"]
            if language not in LANGUAGES or seed not in SEEDS or percent not in PERCENTS or label not in expected_costs:
                raise ValueError(f"Unexpected scenario identity at line {line_number}.")
            equivalent_labels = row["equivalent_cost_labels"].split(";")
            if label not in equivalent_labels or not set(equivalent_labels).issubset(expected_costs):
                raise ValueError(f"Invalid exact-equivalence class at line {line_number}.")
            equivalence_classes[language].add(tuple(sorted(equivalent_labels)))
            for field in ("radixor_java_version", "source_revision", "source_state", "generator_sha256"):
                identities[field].add(row[field])
            identity = (row["model_id"], row["model_version"], row["model_sha256"])
            if language in model_identity and model_identity[language] != identity:
                raise ValueError(f"Model identity changes within {language}.")
            model_identity[language] = identity
            total_rows = int(row["total_rows"])
            selected_rows = int(row["selected_rows"])
            if selected_rows != (total_rows if percent == 100 else max(1, (total_rows * percent + 50) // 100)):
                raise ValueError(f"Incorrect training-row count for {language, seed, percent, label}.")
            if int(row["withheld_rows"]) != total_rows - selected_rows:
                raise ValueError(f"Incorrect withheld-row count for {language, seed, percent, label}.")
            for scope in ("whole", "withheld", "unseen"):
                total = int(row[f"{scope}_total"])
                changed = int(row[f"{scope}_changed_total"])
                roots = int(row[f"{scope}_root_total"])
                correct = int(row[f"{scope}_correct"])
                if total != changed + roots or correct != int(row[f"{scope}_changed_correct"]) + int(row[f"{scope}_root_correct"]):
                    raise ValueError(f"Inconsistent exact counters for {language, seed, percent, label}, {scope}.")
            if int(row["withheld_total"]) != int(row["unseen_total"]) + int(row["excluded_overlap_occurrences"]):
                raise ValueError(f"Inconsistent unseen-overlap arithmetic for {language, seed, percent, label}.")
            ratio = float(row["trie_distinct_patch_command_ratio"])
            baseline_commands = int(row["baseline_trie_distinct_patch_commands"])
            commands = int(row["trie_distinct_patch_commands"])
            expected_ratio = 1.0 if baseline_commands == 0 else commands / baseline_commands
            if not math.isclose(ratio, expected_ratio, rel_tol=1e-12, abs_tol=1e-12):
                raise ValueError(f"Incorrect command ratio for {language, seed, percent, label}.")
            if row["viability"] not in {"VIABLE", "MARGINAL", "NOT_VIABLE"}:
                raise ValueError(f"Invalid viability for {language, seed, percent, label}.")
            features.setdefault(language, {
                "rows": float(total_rows),
                "forms": float(row["whole_total"]),
                "mean_family_size": int(row["whole_total"]) / total_rows,
                "changed_share": int(row["whole_changed_total"]) / int(row["whole_total"]),
            })
            if percent == 100 and BASELINE in equivalent_labels:
                features[language]["baseline_patch_commands"] = float(row["trie_distinct_patch_commands"])
            for equivalent_label in equivalent_labels:
                key = (language, seed, percent, equivalent_label)
                if key in keys:
                    raise ValueError(f"Duplicate logical experiment scenario: {key}")
                keys.add(key)
                match = re.fullmatch(r"D(\d+)I(\d+)R(\d+)M(\d+)", equivalent_label)
                if match is None:
                    raise ValueError(f"Malformed cost label: {equivalent_label}")
                observations.append(Observation(
                    language, seed, percent, equivalent_label,
                    *(int(value) for value in match.groups()), ratio,
                    int(row["trie_internal_nodes"]) + int(row["trie_leaves"]), int(row["trie_edges"]),
                    int(row["trie_longest_path"]), float(row["trie_avg_path_length"]),
                    int(row["trie_dense_table_slots"]), int(row["trie_value_references"]),
                    int(row["trie_logical_leaf_paths"]),
                    exact_percentage(row["unseen_changed_correct"], row["unseen_changed_total"]),
                    optional_float(row["unseen_f05"]), optional_float(row["unseen_over_percent"]),
                    optional_float(row["unseen_under_percent"]), row["viability"],
                    optional_float(row["whole_under_percent"])))
    if not observations:
        raise ValueError("The edit-cost report contains no measurements.")
    if any(len(values) != 1 or not next(iter(values)).strip() for values in identities.values()):
        raise ValueError("Experiment provenance must be nonblank and invariant.")
    identity = {name: next(iter(values)) for name, values in identities.items()}
    if len(identity["source_revision"]) != 40 or len(identity["generator_sha256"]) != 64:
        raise ValueError("Experiment source provenance is incomplete.")
    if not allow_partial:
        expected = len(LANGUAGES) * len(SEEDS) * len(PERCENTS) * len(expected_costs)
        if len(observations) != expected or set(model_identity) != set(LANGUAGES):
            raise ValueError(f"Incomplete experiment matrix: expected {expected:,}, found {len(observations):,}.")
        expected_keys = {(language, seed, percent, label) for language in LANGUAGES
                         for seed in SEEDS for percent in PERCENTS for label in expected_costs}
        if keys != expected_keys:
            raise ValueError("The experiment matrix has missing or unexpected scenarios.")
    for language, classes in equivalence_classes.items():
        features[language]["equivalence_classes"] = float(len(classes))
        features[language]["largest_equivalence_class"] = float(max(map(len, classes)))
    return observations, identity, features


def quantile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    position = fraction * (len(ordered) - 1)
    lower = math.floor(position)
    upper = math.ceil(position)
    return ordered[lower] if lower == upper else ordered[lower] * (upper - position) + ordered[upper] * (position - lower)


def ranks(values: list[float]) -> list[float]:
    ordered = sorted(range(len(values)), key=values.__getitem__)
    result = [0.0] * len(values)
    index = 0
    while index < len(ordered):
        end = index + 1
        while end < len(ordered) and values[ordered[end]] == values[ordered[index]]:
            end += 1
        rank = (index + end - 1) / 2.0
        for position in range(index, end):
            result[ordered[position]] = rank
        index = end
    return result


def pearson(left: list[float], right: list[float]) -> float | None:
    if len(left) < 3 or len(set(left)) < 2 or len(set(right)) < 2:
        return None
    left_mean = statistics.fmean(left)
    right_mean = statistics.fmean(right)
    numerator = sum((x - left_mean) * (y - right_mean) for x, y in zip(left, right, strict=True))
    denominator = math.sqrt(sum((x - left_mean) ** 2 for x in left) * sum((y - right_mean) ** 2 for y in right))
    return None if denominator == 0 else numerator / denominator


def correlation_rows(observations: list[Observation], language: str | None = None) -> list[dict[str, str]]:
    strata: dict[tuple[str, str, int], list[Observation]] = defaultdict(list)
    for observation in observations:
        if (observation.percent < 100 and observation.unseen_changed_exact is not None
                and (language is None or observation.language == language)):
            strata[observation.language, observation.seed, observation.percent].append(observation)
    cost_predictors = {
        "delete_cost": lambda item: float(item.delete), "insert_cost": lambda item: float(item.insert),
        "replace_cost": lambda item: float(item.replace), "match_cost": lambda item: float(item.match),
        "delete_to_insert_ratio": lambda item: item.delete / item.insert,
        "replace_to_delete_insert": lambda item: item.replace / (item.delete + item.insert),
        "edit_cost_imbalance": lambda item: max(item.delete, item.insert, item.replace)
        / min(item.delete, item.insert, item.replace),
    }
    representation_predictors = {
        "patch_command_ratio": lambda item: item.command_ratio, "trie_nodes": lambda item: float(item.nodes),
        "trie_edges": lambda item: float(item.edges), "longest_path": lambda item: float(item.longest_path),
        "average_path_length": lambda item: item.average_path,
        "dense_table_slots": lambda item: float(item.dense_table_slots),
        "value_references": lambda item: float(item.value_references),
        "logical_leaf_paths": lambda item: float(item.logical_leaf_paths),
    }
    quality_outcomes = {
        "unseen_changed_exact": lambda item: item.unseen_changed_exact,
        "unseen_f05": lambda item: item.unseen_f05,
        "unseen_over_percent": lambda item: item.unseen_over,
        "unseen_under_percent": lambda item: item.unseen_under,
    }
    representation_outcomes = {
        "patch_command_ratio": lambda item: item.command_ratio,
        "trie_nodes": lambda item: float(item.nodes),
        "trie_edges": lambda item: float(item.edges),
        "longest_path": lambda item: float(item.longest_path),
        "average_path_length": lambda item: item.average_path,
        "dense_table_slots": lambda item: float(item.dense_table_slots),
        "value_references": lambda item: float(item.value_references),
        "logical_leaf_paths": lambda item: float(item.logical_leaf_paths),
    }
    pairs = [(name, predictor, outcome_name, outcome)
             for name, predictor in cost_predictors.items()
             for outcome_name, outcome in (*quality_outcomes.items(), *representation_outcomes.items())]
    pairs.extend((name, predictor, outcome_name, outcome)
                 for name, predictor in representation_predictors.items()
                 for outcome_name, outcome in quality_outcomes.items())
    pairs.extend(("patch_command_ratio", representation_predictors["patch_command_ratio"],
                  outcome_name, outcome)
                 for outcome_name, outcome in representation_outcomes.items()
                 if outcome_name != "patch_command_ratio")
    result: list[dict[str, str]] = []
    for predictor_name, predictor, outcome_name, outcome in pairs:
        spearman_values: list[float] = []
        pearson_values: list[float] = []
        for items in strata.values():
            measured_pairs = [(predictor(item), outcome(item)) for item in items if outcome(item) is not None]
            x = [pair[0] for pair in measured_pairs]
            y = [float(pair[1]) for pair in measured_pairs]
            linear = pearson(x, y)
            monotonic = pearson(ranks(x), ranks(y))
            if linear is not None and monotonic is not None:
                pearson_values.append(linear)
                spearman_values.append(monotonic)
        if not spearman_values:
            continue
        result.append({
            "predictor": predictor_name, "outcome": outcome_name,
            "strata": str(len(spearman_values)),
            "spearman_median": f"{statistics.median(spearman_values):.6f}",
            "spearman_q025": f"{quantile(spearman_values, 0.025):.6f}",
            "spearman_q975": f"{quantile(spearman_values, 0.975):.6f}",
            "pearson_median": f"{statistics.median(pearson_values):.6f}",
        })
    return result


def language_correlation_rows(observations: list[Observation]) -> list[dict[str, str]]:
    """Summarizes within-stratum associations separately for every language."""
    result: list[dict[str, str]] = []
    for language in sorted(LANGUAGES):
        for row in correlation_rows(observations, language):
            result.append({
                "language": language,
                "language_name": LANGUAGES[language],
                **row,
            })
    return result


def dictionary_sensitivity_rows(features: dict[str, dict[str, float]]) -> list[dict[str, str]]:
    """Relates exact cost sensitivity to dictionary-level descriptive features."""
    predictors = {
        "dictionary_rows": "rows",
        "dictionary_forms": "forms",
        "mean_family_size": "mean_family_size",
        "changed_form_share": "changed_share",
        "baseline_patch_commands": "baseline_patch_commands",
    }
    ordered = [features[language] for language in sorted(features)]
    classes = [item["equivalence_classes"] for item in ordered]
    result: list[dict[str, str]] = []
    for predictor_name, feature_name in predictors.items():
        values = [item[feature_name] for item in ordered]
        monotonic = pearson(ranks(values), ranks(classes))
        linear = pearson(values, classes)
        if monotonic is None or linear is None:
            raise ValueError(f"Dictionary sensitivity association is undefined for {predictor_name}.")
        result.append({
            "predictor": predictor_name,
            "outcome": "exact_equivalence_classes",
            "languages": str(len(ordered)),
            "spearman": f"{monotonic:.6f}",
            "pearson": f"{linear:.6f}",
        })
    return result


def dictionary_recommendation_association_rows(
        features: dict[str, dict[str, float]],
        recommendations: list[dict[str, str]]) -> list[dict[str, str]]:
    """Relates dictionary descriptors to exploratory language-specific recommendations."""
    predictors = {
        "dictionary_rows": "rows",
        "mean_family_size": "mean_family_size",
        "changed_form_share": "changed_share",
        "baseline_patch_commands": "baseline_patch_commands",
        "exact_equivalence_classes": "equivalence_classes",
    }
    outcomes = {
        "recommended_delete_cost": "delete_cost",
        "recommended_insert_cost": "insert_cost",
        "recommended_replace_cost": "replace_cost",
        "recommended_match_cost": "match_cost",
        "recommended_command_ratio": "median_patch_command_ratio",
        "recommended_exact_delta_pp": "delta_vs_baseline_pp",
    }
    by_language = {row["language"]: row for row in recommendations}
    languages = sorted(features)
    if set(languages) != set(by_language):
        raise ValueError("Dictionary features and recommendations cover different languages.")
    result: list[dict[str, str]] = []
    for predictor_name, feature_name in predictors.items():
        left = [features[language][feature_name] for language in languages]
        for outcome_name, recommendation_name in outcomes.items():
            right = [float(by_language[language][recommendation_name]) for language in languages]
            monotonic = pearson(ranks(left), ranks(right))
            linear = pearson(left, right)
            if monotonic is None or linear is None:
                continue
            result.append({
                "predictor": predictor_name,
                "outcome": outcome_name,
                "languages": str(len(languages)),
                "spearman": f"{monotonic:.6f}",
                "pearson": f"{linear:.6f}",
            })
    return result


def recommendation_rows(observations: list[Observation]) -> list[dict[str, str]]:
    grouped: dict[tuple[str, str], list[Observation]] = defaultdict(list)
    for observation in observations:
        if observation.percent < 100 and observation.unseen_changed_exact is not None:
            grouped[observation.language, observation.label].append(observation)
    result: list[dict[str, str]] = []
    for language in sorted({key[0] for key in grouped}):
        summaries: list[tuple[float, float, float, str]] = []
        for (candidate_language, label), items in grouped.items():
            if candidate_language != language or any(item.viability != "VIABLE" for item in items):
                continue
            exact = statistics.median(item.unseen_changed_exact for item in items if item.unseen_changed_exact is not None)
            f05_values = [item.unseen_f05 for item in items if item.unseen_f05 is not None]
            f05 = statistics.median(f05_values) if f05_values else -1.0
            command_ratio = statistics.median(item.command_ratio for item in items)
            summaries.append((exact, f05, command_ratio, label))
        best_exact = max(item[0] for item in summaries)
        eligible = [item for item in summaries if item[0] >= best_exact - QUALITY_TOLERANCE_PERCENTAGE_POINTS]
        selected = min(eligible,
                       key=lambda item: (item[2], -item[1], -item[0], item[3] != BASELINE, item[3]))
        baseline = next(item for item in summaries if item[3] == BASELINE)
        costs = next(item for item in grouped[language, selected[3]])
        result.append({
            "language": language, "language_name": LANGUAGES[language], "recommended_cost": selected[3],
            "delete_cost": str(costs.delete), "insert_cost": str(costs.insert),
            "replace_cost": str(costs.replace), "match_cost": str(costs.match),
            "median_unseen_changed_exact": f"{selected[0]:.6f}",
            "delta_vs_baseline_pp": f"{selected[0] - baseline[0]:.6f}",
            "median_unseen_f05": f"{selected[1]:.6f}",
            "median_patch_command_ratio": f"{selected[2]:.6f}",
            "best_observed_exact": f"{best_exact:.6f}",
        })
    return result


def minimum_command_rows(observations: list[Observation],
                         features: dict[str, dict[str, float]]) -> list[dict[str, str]]:
    """Selects one quality-tiebroken representative of each full-dictionary command minimum."""
    full: dict[tuple[str, str], list[Observation]] = defaultdict(list)
    partial: dict[tuple[str, str], list[Observation]] = defaultdict(list)
    for observation in observations:
        target = full if observation.percent == 100 else partial
        target[observation.language, observation.label].append(observation)

    result: list[dict[str, str]] = []
    for language in sorted(LANGUAGES):
        ratios: dict[str, float] = {}
        labels = sorted(label for candidate_language, label in full if candidate_language == language)
        if not labels:
            continue
        for label in labels:
            items = full[language, label]
            if len(items) != len(SEEDS):
                raise ValueError(f"Incomplete full-dictionary command minimum for {language, label}.")
            first_ratio = items[0].command_ratio
            if any(not math.isclose(item.command_ratio, first_ratio, rel_tol=1e-12, abs_tol=1e-12)
                   for item in items[1:]):
                raise ValueError(f"Full-dictionary command count changes across seeds for {language, label}.")
            ratios[label] = first_ratio

        minimum_ratio = min(ratios.values())
        tied_labels = sorted(label for label, ratio in ratios.items()
                             if math.isclose(ratio, minimum_ratio, rel_tol=1e-12, abs_tol=1e-12))

        def tie_break(label: str) -> tuple[float, float, float, bool, tuple[int, int, int, int]]:
            items = partial[language, label]
            f05 = statistics.median(item.unseen_f05 for item in items if item.unseen_f05 is not None)
            over = statistics.median(item.unseen_over for item in items if item.unseen_over is not None)
            under = statistics.median(item.unseen_under for item in items if item.unseen_under is not None)
            example = items[0]
            return (-f05, over, under, label != BASELINE,
                    (example.delete, example.insert, example.replace, example.match))

        selected_label = min(tied_labels, key=tie_break)
        selected_items = partial[language, selected_label]
        baseline_items = partial[language, BASELINE]
        selected_exact = statistics.median(
            item.unseen_changed_exact for item in selected_items if item.unseen_changed_exact is not None)
        baseline_exact = statistics.median(
            item.unseen_changed_exact for item in baseline_items if item.unseen_changed_exact is not None)
        selected_f05 = statistics.median(
            item.unseen_f05 for item in selected_items if item.unseen_f05 is not None)
        baseline_commands = int(round(features[language]["baseline_patch_commands"]))
        minimum_commands = int(round(baseline_commands * minimum_ratio))
        example = selected_items[0]
        result.append({
            "language": language,
            "language_name": LANGUAGES[language],
            "minimum_command_cost": selected_label,
            "delete_cost": str(example.delete),
            "insert_cost": str(example.insert),
            "replace_cost": str(example.replace),
            "match_cost": str(example.match),
            "baseline_commands": str(baseline_commands),
            "minimum_commands": str(minimum_commands),
            "command_ratio": f"{minimum_ratio:.6f}",
            "command_reduction_percent": f"{100.0 * (1.0 - minimum_ratio):.6f}",
            "median_unseen_changed_exact": f"{selected_exact:.6f}",
            "exact_delta_vs_baseline_pp": f"{selected_exact - baseline_exact:.6f}",
            "median_unseen_f05": f"{selected_f05:.6f}",
            "tied_minima": str(len(tied_labels)),
        })
    return result


def quality_optimum_rows(observations: list[Observation]) -> list[dict[str, str]]:
    """Selects the best observed viable median unseen-form F0.5 configuration per language."""
    grouped: dict[tuple[str, str], list[Observation]] = defaultdict(list)
    full: dict[tuple[str, str], list[Observation]] = defaultdict(list)
    for observation in observations:
        if observation.percent < 100:
            grouped[observation.language, observation.label].append(observation)
        else:
            full[observation.language, observation.label].append(observation)

    result: list[dict[str, str]] = []
    for language in sorted(LANGUAGES):
        summaries: dict[str, tuple[float, float, float, float, Observation]] = {}
        labels = sorted(label for candidate_language, label in grouped if candidate_language == language)
        if not labels:
            continue
        for label in labels:
            items = grouped[language, label]
            if len(items) != len(SEEDS) * 9:
                raise ValueError(f"Incomplete partial-knowledge quality optimum for {language, label}.")
            if any(item.viability != "VIABLE" for item in items):
                continue
            summaries[label] = (
                statistics.median(item.unseen_f05 for item in items if item.unseen_f05 is not None),
                statistics.median(item.unseen_over for item in items if item.unseen_over is not None),
                statistics.median(item.unseen_under for item in items if item.unseen_under is not None),
                statistics.median(item.command_ratio for item in items),
                items[0],
            )

        baseline = summaries[BASELINE]
        non_worse = {
            label: summary for label, summary in summaries.items()
            if summary[1] <= baseline[1] + 1e-12 and summary[2] <= baseline[2] + 1e-12
        }
        best_f05 = max(summary[0] for summary in non_worse.values())
        f05_ties = {label: summary for label, summary in non_worse.items()
                    if math.isclose(summary[0], best_f05, rel_tol=1e-12, abs_tol=1e-12)}
        best_over = min(summary[1] for summary in f05_ties.values())
        over_ties = {label: summary for label, summary in f05_ties.items()
                     if math.isclose(summary[1], best_over, rel_tol=1e-12, abs_tol=1e-12)}
        best_under = min(summary[2] for summary in over_ties.values())
        tied_labels = sorted(label for label, summary in over_ties.items()
                             if math.isclose(summary[2], best_under, rel_tol=1e-12, abs_tol=1e-12))

        def tie_break(label: str) -> tuple[bool, tuple[int, int, int, int]]:
            example = summaries[label][4]
            return (label != BASELINE, (example.delete, example.insert, example.replace, example.match))

        selected_label = min(tied_labels, key=tie_break)
        selected = summaries[selected_label]
        full_items = full[language, selected_label]
        if len(full_items) != len(SEEDS) or any(item.whole_under is None for item in full_items):
            raise ValueError(f"Incomplete full-model under-stemming reference for {language, selected_label}.")
        full_model_under = statistics.median(
            item.whole_under for item in full_items if item.whole_under is not None)
        example = selected[4]
        result.append({
            "language": language,
            "language_name": LANGUAGES[language],
            "quality_cost": selected_label,
            "delete_cost": str(example.delete),
            "insert_cost": str(example.insert),
            "replace_cost": str(example.replace),
            "match_cost": str(example.match),
            "median_unseen_over_percent": f"{selected[1]:.9f}",
            "over_delta_vs_baseline_pp": f"{selected[1] - baseline[1]:.9f}",
            "median_unseen_under_percent": f"{selected[2]:.9f}",
            "under_delta_vs_baseline_pp": f"{selected[2] - baseline[2]:.9f}",
            "full_model_under_percent": f"{full_model_under:.9f}",
            "median_unseen_f05": f"{selected[0]:.9f}",
            "f05_delta_vs_baseline": f"{selected[0] - baseline[0]:.9f}",
            "median_patch_command_ratio": f"{selected[3]:.6f}",
            "tied_quality_optima": str(len(tied_labels)),
        })
    return result


def knowledge_curve_rows(observations: list[Observation],
                         recommendations: list[dict[str, str]]) -> list[dict[str, str]]:
    """Builds macro knowledge curves for baseline and language-specific recommendations."""
    selected = {row["language"]: row["recommended_cost"] for row in recommendations}
    result: list[dict[str, str]] = []
    for percent in sorted({item.percent for item in observations if item.percent < 100}):
        baseline = [item for item in observations if item.percent == percent and item.label == BASELINE]
        recommended = [item for item in observations
                       if item.percent == percent and item.label == selected[item.language]]
        expected = len(selected) * len(SEEDS)
        if len(baseline) != expected or len(recommended) != expected:
            raise ValueError(f"Incomplete knowledge curve at {percent}%.")
        baseline_exact = [item.unseen_changed_exact for item in baseline
                          if item.unseen_changed_exact is not None]
        recommended_exact = [item.unseen_changed_exact for item in recommended
                             if item.unseen_changed_exact is not None]
        baseline_f05 = [item.unseen_f05 for item in baseline if item.unseen_f05 is not None]
        recommended_f05 = [item.unseen_f05 for item in recommended if item.unseen_f05 is not None]
        if not baseline_exact or not recommended_exact or not baseline_f05 or not recommended_f05:
            raise ValueError(f"Undefined knowledge-curve quality at {percent}%.")
        baseline_exact_median = statistics.median(baseline_exact)
        recommended_exact_median = statistics.median(recommended_exact)
        result.append({
            "training_percent": str(percent),
            "baseline_unseen_changed_exact": f"{baseline_exact_median:.6f}",
            "recommended_unseen_changed_exact": f"{recommended_exact_median:.6f}",
            "exact_delta_pp": f"{recommended_exact_median - baseline_exact_median:.6f}",
            "baseline_unseen_f05": f"{statistics.median(baseline_f05):.6f}",
            "recommended_unseen_f05": f"{statistics.median(recommended_f05):.6f}",
            "baseline_patch_command_ratio": f"{statistics.median(item.command_ratio for item in baseline):.6f}",
            "recommended_patch_command_ratio":
                f"{statistics.median(item.command_ratio for item in recommended):.6f}",
        })
    return result


def language_knowledge_curve_rows(
        observations: list[Observation], recommendations: list[dict[str, str]]) -> list[dict[str, str]]:
    """Builds one five-split knowledge curve for each language."""
    selected = {row["language"]: row["recommended_cost"] for row in recommendations}
    result: list[dict[str, str]] = []
    for language in sorted(selected):
        for percent in sorted({item.percent for item in observations
                               if item.language == language and item.percent < 100}):
            baseline = [item for item in observations
                        if item.language == language and item.percent == percent
                        and item.label == BASELINE]
            recommended = [item for item in observations
                           if item.language == language and item.percent == percent
                           and item.label == selected[language]]
            if len(baseline) != len(SEEDS) or len(recommended) != len(SEEDS):
                raise ValueError(f"Incomplete language knowledge curve for {language} at {percent}%.")
            baseline_exact = [item.unseen_changed_exact for item in baseline
                              if item.unseen_changed_exact is not None]
            recommended_exact = [item.unseen_changed_exact for item in recommended
                                 if item.unseen_changed_exact is not None]
            baseline_f05 = [item.unseen_f05 for item in baseline if item.unseen_f05 is not None]
            recommended_f05 = [item.unseen_f05 for item in recommended if item.unseen_f05 is not None]
            if not baseline_exact or not recommended_exact or not baseline_f05 or not recommended_f05:
                raise ValueError(f"Undefined language knowledge-curve quality for {language} at {percent}%.")
            baseline_exact_median = statistics.median(baseline_exact)
            recommended_exact_median = statistics.median(recommended_exact)
            result.append({
                "language": language,
                "language_name": LANGUAGES[language],
                "training_percent": str(percent),
                "baseline_unseen_changed_exact": f"{baseline_exact_median:.6f}",
                "recommended_unseen_changed_exact":
                    f"{statistics.median(recommended_exact):.6f}",
                "exact_delta_pp":
                    f"{statistics.median(recommended_exact) - baseline_exact_median:.6f}",
                "baseline_unseen_f05": f"{statistics.median(baseline_f05):.6f}",
                "recommended_unseen_f05": f"{statistics.median(recommended_f05):.6f}",
                "baseline_patch_command_ratio":
                    f"{statistics.median(item.command_ratio for item in baseline):.6f}",
                "recommended_patch_command_ratio":
                    f"{statistics.median(item.command_ratio for item in recommended):.6f}",
            })
    return result


def csv_text(rows: list[dict[str, str]]) -> str:
    if not rows:
        raise ValueError("Cannot render an empty derived table.")
    import io
    buffer = io.StringIO(newline="")
    writer = csv.DictWriter(buffer, fieldnames=list(rows[0]), lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return buffer.getvalue()


def validate_update_paths(source: Path, archive: Path, mode: str) -> None:
    """Rejects an update that would overwrite its input while it is being read."""
    if mode == "update" and source.resolve() == archive.resolve():
        raise ValueError("The edit-cost update source must differ from its documentation archive.")


def replace_language_section(document: str, section: str) -> str:
    """Replaces one generated language section or inserts it before quality details."""
    block = f"{LANGUAGE_SECTION_START}\n\n{section.rstrip()}\n\n{LANGUAGE_SECTION_END}"
    start_count = document.count(LANGUAGE_SECTION_START)
    end_count = document.count(LANGUAGE_SECTION_END)
    if start_count == 1 and end_count == 1:
        pattern = (re.escape(LANGUAGE_SECTION_START) + r".*?"
                   + re.escape(LANGUAGE_SECTION_END))
        return re.sub(pattern, block, document, count=1, flags=re.S)
    if start_count != 0 or end_count != 0:
        raise ValueError("A language page contains incomplete or duplicate edit-cost markers.")
    anchor = "<!-- STEMMING-QUALITY:START -->"
    if document.count(anchor) != 1:
        raise ValueError("A language page has no unique stemming-quality insertion anchor.")
    return document.replace(anchor, f"{block}\n\n{anchor}", 1)


def render_language_section(
        language: str, feature: dict[str, float], recommendation: dict[str, str],
        curve: list[dict[str, str]], correlations: list[dict[str, str]]) -> str:
    """Renders evidence and bounded conclusions for one language experiment."""
    if len(curve) != 9:
        raise ValueError(f"Expected nine partial-knowledge points for {language}.")
    structural_outcomes = {
        "patch_command_ratio", "trie_nodes", "trie_edges", "longest_path",
        "average_path_length", "dense_table_slots", "value_references",
        "logical_leaf_paths",
    }
    quality_outcomes = {
        "unseen_changed_exact", "unseen_f05", "unseen_over_percent", "unseen_under_percent",
    }
    stable_structural = sorted(
        (row for row in correlations
         if row["outcome"] in structural_outcomes
         and int(row["strata"]) == LANGUAGE_CORRELATION_STRATA
         and float(row["spearman_q025"]) * float(row["spearman_q975"]) > 0.0),
        key=lambda row: min(abs(float(row["spearman_q025"])),
                            abs(float(row["spearman_q975"]))),
        reverse=True,
    )[:6]
    stable_quality = [row for row in correlations
                      if row["outcome"] in quality_outcomes
                      and int(row["strata"]) == LANGUAGE_CORRELATION_STRATA
                      and float(row["spearman_q025"]) * float(row["spearman_q975"]) > 0.0]
    available_quality_outcomes = {
        row["outcome"] for row in correlations if row["outcome"] in quality_outcomes
    }
    strongest_quality = [
        max((row for row in correlations if row["outcome"] == outcome),
            key=lambda row: abs(float(row["spearman_median"])))
        for outcome in sorted(available_quality_outcomes)
    ]
    undefined_quality_outcomes = sorted(quality_outcomes.difference(available_quality_outcomes))
    first = curve[0]
    last = curve[-1]
    baseline_gain = (float(last["baseline_unseen_changed_exact"])
                     - float(first["baseline_unseen_changed_exact"]))
    selected_gain = (float(last["recommended_unseen_changed_exact"])
                     - float(first["recommended_unseen_changed_exact"]))
    command_saving = 100.0 * (1.0 - float(recommendation["median_patch_command_ratio"]))
    is_baseline = recommendation["recommended_cost"] == BASELINE
    if math.isclose(command_saving, 0.0, abs_tol=0.005):
        command_effect = ("does not change the median retained-command count "
                          f"({float(recommendation['median_patch_command_ratio']):.3f}× baseline)")
    elif command_saving > 0.0:
        command_effect = (f"reduces the median retained-command count by **{command_saving:.2f}%** "
                          f"({float(recommendation['median_patch_command_ratio']):.3f}× baseline)")
    else:
        command_effect = (f"increases the median retained-command count by **{-command_saving:.2f}%** "
                          f"({float(recommendation['median_patch_command_ratio']):.3f}× baseline)")
    lines = [
        "## Edit Costs and Dictionary-Knowledge Generalization", "",
        f"This section interprets the edit-cost and held-out-family experiment for `{language}`",
        "separately from the cross-language macro summary. Each knowledge point is the median of",
        "five frozen, nested splits. The primary exactness outcome covers changed forms in withheld",
        "families after excluding normalized surfaces seen in training. Thus the complete dictionary",
        "is the evaluation population, while only genuinely unseen surfaces contribute to this outcome.", "",
        "Cost labels have the fixed form `D<delete>I<insert>R<replace>M<match>`. `D` is the cost",
        "of deleting a source character, `I` of inserting a target character, `R` of replacing a",
        "source character, and `M` of keeping an equal source/target character unchanged (the match",
        "or skip step). For example, `D2I5R3M0` means delete cost 2, insert cost 5, replace cost 3,",
        "and match cost 0. The numbers are relative dynamic-programming costs, not command counts.", "",
        "### Evidence", "",
        "| Dictionary rows | Evaluated forms | Changed-form share | Baseline commands | Exact cost classes | Grid reduction | Largest exact class |",
        "| ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
        f"| {int(feature['rows']):,} | {int(feature['forms']):,} | "
        f"{100.0 * feature['changed_share']:.2f}% | "
        f"{int(feature['baseline_patch_commands']):,} | "
        f"{int(feature['equivalence_classes'])} | "
        f"{234.0 / feature['equivalence_classes']:.2f}× | "
        f"{int(feature['largest_equivalence_class'])} |", "",
        "The exact classes are based on command-by-command equality over the complete dictionary,",
        "not equality of aggregate trie metrics. A higher class count means that this dictionary",
        "exposes more cost-dependent encoder decisions; it does not by itself mean better quality.", "",
        "| Knowledge | Baseline unseen changed exact | Selected-cost exact | Δ | Baseline F0.5 | Selected F0.5 | Baseline commands | Selected commands |",
        "| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in curve:
        lines.append(
            f"| {row['training_percent']}% | "
            f"{float(row['baseline_unseen_changed_exact']):.3f}% | "
            f"{float(row['recommended_unseen_changed_exact']):.3f}% | "
            f"{float(row['exact_delta_pp']):+.3f} pp | "
            f"{float(row['baseline_unseen_f05']):.4f} | "
            f"{float(row['recommended_unseen_f05']):.4f} | "
            f"{float(row['baseline_patch_command_ratio']):.3f}× | "
            f"{float(row['recommended_patch_command_ratio']):.3f}× |"
        )
    lines.extend(["", "### Within-language associations", "",
                  "Spearman coefficients are calculated independently inside each seed × knowledge",
                  "stratum across the normalized cost grid. The table reports the median and central",
                  f"95% empirical interval across up to {LANGUAGE_CORRELATION_STRATA} strata. A relationship is called stable",
                  f"only when it is defined in all {LANGUAGE_CORRELATION_STRATA} strata and the interval retains one sign.",
                  "These intervals are descriptive, not multiplicity-adjusted confidence intervals.",
                  "Every predictor and outcome label is defined in the "
                  "[methodology glossary](../reference/edit-cost-methodology.md#predictor-and-outcome-glossary).", ""])
    if stable_structural:
        lines.extend([
            "The strongest structural pairs whose central interval retains one sign are:", "",
            "| Predictor | Structural outcome | Median Spearman ρ | Central 95% | Strata |",
            "| --- | --- | ---: | ---: | ---: |",
        ])
        for row in stable_structural:
            lines.append(
                f"| `{row['predictor']}` | `{row['outcome']}` | "
                f"{float(row['spearman_median']):+.3f} | "
                f"{float(row['spearman_q025']):+.3f}…{float(row['spearman_q975']):+.3f} | "
                f"{row['strata']} |"
            )
        lines.append("")
    lines.extend([
        "For each quality outcome, the largest absolute median association is shown even when its",
        "interval crosses zero. This prevents a large median in heterogeneous strata from being",
        "misreported as a portable language-level effect.", "",
        "| Predictor | Quality outcome | Median Spearman ρ | Central 95% | Stable | Defined strata |",
        "| --- | --- | ---: | ---: | --- | ---: |",
    ])
    for row in strongest_quality:
        stable = (int(row["strata"]) == LANGUAGE_CORRELATION_STRATA
                  and float(row["spearman_q025"]) * float(row["spearman_q975"]) > 0.0)
        lines.append(
            f"| `{row['predictor']}` | `{row['outcome']}` | "
            f"{float(row['spearman_median']):+.3f} | "
            f"{float(row['spearman_q025']):+.3f}…{float(row['spearman_q975']):+.3f} | "
            f"{'yes' if stable else 'no'} | {row['strata']} / {LANGUAGE_CORRELATION_STRATA} |"
        )
    if undefined_quality_outcomes:
        lines.extend([
            "",
            "No within-stratum coefficient is defined for "
            + ", ".join(f"`{outcome}`" for outcome in undefined_quality_outcomes)
            + " because these outcomes do not vary across cost configurations in the measured "
            "language strata. Within this matrix, that is observed cost insensitivity for those "
            "outcomes, not missing measurement.",
        ])
    lines.extend(["", "### Edit-cost conclusion", "",
                  f"- With baseline costs, median unseen changed-form exactness changes from "
                  f"**{float(first['baseline_unseen_changed_exact']):.3f}%** at 10% knowledge to "
                  f"**{float(last['baseline_unseen_changed_exact']):.3f}%** at 90%, a "
                  f"**{baseline_gain:+.3f} pp** measured knowledge effect.",
                  f"- The predeclared selection is `{recommendation['recommended_cost']}`. Its median "
                  f"unseen changed-form exactness differs from baseline by "
                  f"**{float(recommendation['delta_vs_baseline_pp']):+.3f} pp** and it {command_effect}.",
                  f"- Under the selected costs, the 10%–90% knowledge change is "
                  f"**{selected_gain:+.3f} pp**. This quantifies generalization for this dictionary; "
                  "it is not a claim about unrelated domains or lexical resources."])
    if is_baseline:
        lines.append(
            "- The selection rule retains the production baseline, so this experiment supplies no "
            "measured reason to change edit costs for this language under the predeclared objective."
        )
    else:
        lines.append(
            "- The non-baseline setting is an efficiency candidate, not a production default: it was "
            "selected and evaluated on the same matrix and therefore requires external-corpus or "
            "external-dictionary validation before adoption."
        )
    if stable_quality:
        lines.append(
            f"- {len(stable_quality)} cost/representation-to-quality association(s) are defined in all "
            f"{LANGUAGE_CORRELATION_STRATA} strata and retain one sign over their central 95% interval. "
            "Their direction is evidence for this "
            "resource only; inspect the table and machine-readable coefficients before extrapolating."
        )
    else:
        lines.append(
            f"- No cost or representation predictor is both defined in all {LANGUAGE_CORRELATION_STRATA} "
            "strata and retains one association sign over the central 95% interval for an unseen-form "
            "quality outcome. Effects with partial coverage are insufficient for a stable language-level "
            "claim; the remaining measured effects are heterogeneous across knowledge levels and splits."
        )
    lines.extend([
        "", "The complete evidence is available in the "
        "[raw logical matrix](../data/edit-cost-sensitivity.csv.gz), the "
        "[per-language knowledge curves](../data/edit-cost-language-knowledge-curve.csv), and the "
        "[per-language association table](../data/edit-cost-language-correlations.csv). See the "
        "[cross-language analysis](../edit-cost-sensitivity.md) and "
        "[frozen methodology](../reference/edit-cost-methodology.md) for scope and limitations.",
    ])
    return "\n".join(lines)


def render_results(observations: list[Observation], identity: dict[str, str],
                   features: dict[str, dict[str, float]], recommendations: list[dict[str, str]],
                   minimum_commands: list[dict[str, str]], quality_optima: list[dict[str, str]],
                   knowledge_curve: list[dict[str, str]],
                   language_knowledge_curves: list[dict[str, str]],
                   correlations: list[dict[str, str]],
                   sensitivity_associations: list[dict[str, str]],
                   recommendation_associations: list[dict[str, str]], checksum: str,
                   analysis_checksum: str) -> str:
    exact_classes = int(sum(item["equivalence_classes"] for item in features.values()))
    logical_configurations = len(features) * len(normalized_grid())
    physical_measurements = exact_classes * len(SEEDS) * len(PERCENTS)
    baseline_rows = [item for item in observations if item.label == BASELINE and item.percent < 100]
    baseline_exact = statistics.median(item.unseen_changed_exact for item in baseline_rows
                                       if item.unseen_changed_exact is not None)
    stable_associations = sorted(
        (row for row in correlations
         if float(row["spearman_q025"]) * float(row["spearman_q975"]) > 0.0),
        key=lambda row: min(abs(float(row["spearman_q025"])),
                            abs(float(row["spearman_q975"]))), reverse=True)
    quality_outcomes = {
        "unseen_changed_exact", "unseen_f05", "unseen_over_percent", "unseen_under_percent",
    }
    heterogeneous_quality = [
        max((row for row in correlations if row["outcome"] == outcome),
            key=lambda row: abs(float(row["spearman_median"])))
        for outcome in sorted(quality_outcomes)
    ]
    strongest_recommendation_associations = sorted(
        recommendation_associations, key=lambda row: abs(float(row["spearman"])), reverse=True)[:8]
    smallest_envelopes = sorted(correlations,
                                key=lambda row: max(abs(float(row["spearman_q025"])),
                                                    abs(float(row["spearman_q975"]))))[:8]
    first_curve = knowledge_curve[0]
    last_curve = knowledge_curve[-1]
    knowledge_gain = (float(last_curve["baseline_unseen_changed_exact"])
                      - float(first_curve["baseline_unseen_changed_exact"]))
    curve_savings = [100.0 * (1.0 - float(row["recommended_patch_command_ratio"]))
                     for row in knowledge_curve]
    maximum_curve_delta = max(abs(float(row["exact_delta_pp"])) for row in knowledge_curve)
    saving_recommendations = [row for row in recommendations
                              if float(row["median_patch_command_ratio"]) < 1.0 - 1e-12]
    largest_saving = min(recommendations, key=lambda row: float(row["median_patch_command_ratio"]))
    non_baseline_quality_optima = [row for row in quality_optima if row["quality_cost"] != BASELINE]
    command_class_association = next(
        row for row in sensitivity_associations if row["predictor"] == "baseline_patch_commands")
    row_class_association = next(
        row for row in sensitivity_associations if row["predictor"] == "dictionary_rows")
    language_curves: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in language_knowledge_curves:
        language_curves[row["language"]].append(row)
    minimum_classes = min(features, key=lambda language: features[language]["equivalence_classes"])
    maximum_classes = max(features, key=lambda language: features[language]["equivalence_classes"])
    lines = [
        "# Edit-Cost Sensitivity and Generalization", "",
        "This experiment evaluates the normalized 234-point delete/insert/replace/match cost grid",
        "against deterministic 10%–100% dictionary-knowledge curves for five frozen splits and all",
        "20 default models. Its primary generalization outcome is exact stemming of changed forms",
        "whose normalized surface was absent from training. Pairwise F0.5, over-stemming, and",
        "under-stemming are independent guards against a superficially good exact-root result.", "",
        "A cost label uses `D<delete>I<insert>R<replace>M<match>`. `D`, `I`, and `R` are the",
        "relative costs of deleting a source character, inserting a target character, and replacing",
        "a source character. `M` is the cost of keeping an equal source/target character unchanged",
        "(the match or skip step). Thus `D2I5R3M0` means costs 2, 5, 3, and 0 respectively; these",
        "numbers are edit-path costs, not counts of generated or retained commands.", "",
        "## Campaign identity", "",
        f"- Protocol: `{PROTOCOL}`", f"- Source identity: `{identity['radixor_java_version']}`",
        f"- Git revision: `{identity['source_revision']}` ({identity['source_state']})",
        f"- Generator SHA-256: `{identity['generator_sha256']}`",
        f"- Analysis script SHA-256: `{analysis_checksum}`",
        f"- Raw CSV SHA-256: `{checksum}`", f"- Exact classes across languages: {exact_classes:,}",
        f"- Physically measured scenarios: {physical_measurements:,}",
        f"- Expanded logical observations: {len(observations):,}", "",
        "The baseline median unseen changed-form exactness across language, seed, and 10%–90%",
        f"knowledge observations is **{baseline_exact:.3f}%**. This macro-style statement gives each",
        "scenario equal weight; it is not a token-weighted production estimate.", "",
        "## Key findings", "",
        f"- The {logical_configurations:,} language × normalized-cost combinations collapse to "
        f"{exact_classes} exact dictionary-specific command classes, a "
        f"**{logical_configurations / exact_classes:.2f}×** reduction. The range is "
        f"{int(features[minimum_classes]['equivalence_classes'])} classes for "
        f"{LANGUAGES[minimum_classes]} through {int(features[maximum_classes]['equivalence_classes'])} "
        f"for {LANGUAGES[maximum_classes]}.",
        f"- Baseline macro unseen changed-form exactness rises from "
        f"**{float(first_curve['baseline_unseen_changed_exact']):.3f}%** at "
        f"{first_curve['training_percent']}% knowledge to "
        f"**{float(last_curve['baseline_unseen_changed_exact']):.3f}%** at "
        f"{last_curve['training_percent']}%, a **{knowledge_gain:+.3f} pp** gain.",
        f"- The predeclared language-specific selections reduce median command count for "
        f"**{len(saving_recommendations)}/{len(recommendations)}** dictionaries. Across the macro "
        f"knowledge curve the reduction is {min(curve_savings):.2f}%–{max(curve_savings):.2f}% while "
        f"the largest absolute exactness change is {maximum_curve_delta:.3f} pp. The largest "
        f"language-level median reduction is "
        f"{100.0 * (1.0 - float(largest_saving['median_patch_command_ratio'])):.2f}% "
        f"for {largest_saving['language_name']}.",
        "- Retained-command ratio has a stable positive association with trie nodes and value",
        "  references, so it is a useful structural-size proxy. No cost or representation predictor",
        "  keeps one association sign across the central 95% of strata for any unseen-form quality",
        "  outcome; quality effects are language- and knowledge-dependent.",
        f"- Exact-class count is more associated with baseline command vocabulary "
        f"(Spearman {float(command_class_association['spearman']):+.3f}) than with raw dictionary rows "
        f"({float(row_class_association['spearman']):+.3f}). This supports command-aware, not merely "
        "size-based, resource classification.", "",
        "## Dictionary sensitivity census", "",
        "Common positive scaling has already been removed from the 234-point normalized grid. The",
        "remaining points collapse only when their generated command is identical for every",
        "full-dictionary pair, verified command by command after fingerprint bucketing. More classes",
        "therefore mean that the dictionary exposes more cost-dependent encoder decisions; this is",
        "sensitivity, not automatically better quality.", "",
        "| Language | Rows | Mean family size | Changed forms | Baseline commands | Exact classes | Grid reduction | Largest class |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for language in sorted(features, key=lambda item: features[item]["baseline_patch_commands"]):
        item = features[language]
        lines.append(f"| {LANGUAGES[language]} | {int(item['rows']):,} | {item['mean_family_size']:.2f} | "
                     f"{100.0 * item['changed_share']:.2f}% | {int(item['baseline_patch_commands']):,} | "
                     f"{int(item['equivalence_classes'])} | "
                     f"{234.0 / item['equivalence_classes']:.2f}× | "
                     f"{int(item['largest_equivalence_class'])} |")
    lines.extend(["", "### Dictionary-level associations", "",
                  "The following across-language coefficients describe whether dictionaries with a",
                  "given property expose more distinct cost-dependent command sequences. There are only",
                  f"{len(features)} language observations, so these are exploratory effect descriptions,",
                  "not causal estimates or evidence that unlisted properties are irrelevant.", "",
                  "| Dictionary property | Outcome | Spearman ρ | Pearson r | Languages |",
                  "| --- | --- | ---: | ---: | ---: |"])
    for row in sensitivity_associations:
        lines.append(f"| `{row['predictor']}` | `{row['outcome']}` | "
                     f"{float(row['spearman']):+.3f} | {float(row['pearson']):+.3f} | "
                     f"{row['languages']} |")
    lines.extend(["",
        "## Language-specific observed optima", "",
        "Here, *optimal* always means optimal for the stated objective inside the measured normalized",
        "234-point grid. It does not mean that the same setting is optimal for an unmeasured corpus,",
        "a different dictionary, or a cost ratio outside the grid.", "",
        "### Smallest full-dictionary patch-command vocabulary", "",
        "This table minimizes the number of distinct patch commands retained by the compiled trie at",
        "100% dictionary knowledge. All five seeds compile the same complete dictionary and must produce",
        "the same count. When several cost configurations reach that minimum, the displayed representative",
        "is chosen by higher median unseen F0.5 over the 10%–90% observations, then lower median over- and",
        "under-stemming; `Tied minima` records how many grid configurations reach the same minimum count.",
        "The exactness delta and F0.5 columns describe the displayed representative on partial-knowledge",
        "generalization observations and expose the quality consequence of pursuing structural size alone.", "",
        "| Language | Minimum-command costs | Commands | Baseline | Reduction | Unseen exact Δ | Unseen F0.5 | Tied minima |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |"])
    for row in minimum_commands:
        lines.append(f"| {row['language_name']} | `{row['minimum_command_cost']}` | "
                     f"{int(row['minimum_commands']):,} | {int(row['baseline_commands']):,} | "
                     f"{float(row['command_reduction_percent']):.2f}% | "
                     f"{float(row['exact_delta_vs_baseline_pp']):+.3f} pp | "
                     f"{float(row['median_unseen_f05']):.6f} | {row['tied_minima']} |")
    lines.extend(["", "Machine-readable values: "
        "[minimum-command configurations](data/edit-cost-minimum-commands.csv).", "",
        "### Best observed pairwise generalization quality on unseen forms", "",
        "!!! important \"This is not full-model production quality\"", "",
        "    `Unseen-family OI` and `Unseen-family UI` are medians over 45 deliberately difficult",
        "    generalization scenarios: five frozen splits at each 10%–90% dictionary-knowledge level.",
        "    Evaluation includes only forms from withheld dictionary families whose normalized surface",
        "    never appeared in training. The values therefore measure how a partial trie connects",
        "    previously unseen members of previously unseen families; they do not describe a model built",
        "    from the complete dictionary.", "",
        "Under-stemming is pairwise: `UI = FN / (TP + FN)`, where every gold-related pair of forms is",
        "one positive relation. One incorrectly separated form can break several relations inside its",
        "family, so unseen-family UI is neither a per-form error rate nor the complement of exact-root",
        "accuracy. At 100% knowledge no withheld unseen-family population remains. The `Full-model UI`",
        "column therefore supplies a separate reference measured over the complete dictionary with the",
        "displayed cost configuration; it is the relevant column for judging full-model behavior.", "",
        "Only configurations classified `VIABLE` in every partial-knowledge language × seed observation",
        "are eligible. A candidate must not increase either median unseen-family OI or median unseen-family",
        "UI relative to `D1I1R1M0`. Among those candidates, the table maximizes median unseen F0.5; ties",
        "prefer lower OI, then lower UI, and finally the production baseline. Lower OI/UI values are",
        "better; higher F0.5 is better. Deltas are against the baseline and retain six decimals because",
        "several measured effects are small.", "",
        f"A non-baseline configuration is selected for **{len(non_baseline_quality_optima)}/"
        f"{len(quality_optima)}** dictionaries. This establishes that edit costs can alter",
        "measured stemming quality for particular dictionaries, but the magnitude and direction remain",
        "dictionary-dependent. Selection and reporting use the same matrix, so these are exploratory",
        "observed optima rather than externally validated production defaults.", "",
        "| Language | Quality costs | Median unseen-family OI (10%–90%) | Δ OI | Median unseen-family UI (10%–90%) | Δ UI | Full-model UI (100%) | Unseen F0.5 | Δ F0.5 |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"])
    for row in quality_optima:
        lines.append(f"| {row['language_name']} | `{row['quality_cost']}` | "
                     f"{float(row['median_unseen_over_percent']):.6f}% | "
                     f"{float(row['over_delta_vs_baseline_pp']):+.6f} pp | "
                     f"{float(row['median_unseen_under_percent']):.6f}% | "
                     f"{float(row['under_delta_vs_baseline_pp']):+.6f} pp | "
                     f"{float(row['full_model_under_percent']):.6f}% | "
                     f"{float(row['median_unseen_f05']):.6f} | "
                     f"{float(row['f05_delta_vs_baseline']):+.6f} |")
    lines.extend(["", "Machine-readable values: "
        "[quality configurations](data/edit-cost-quality-optima.csv).", "",
        "## Predeclared recommendation rule", "",
        "For each language, configurations classified `VIABLE` at every partial-knowledge observation",
        f"are retained. The rule finds the best median unseen changed-form exactness, admits settings within",
        f"{QUALITY_TOLERANCE_PERCENTAGE_POINTS:.2f} percentage points, then minimizes the median retained-command",
        "ratio; remaining ties prefer unseen F0.5 and exactness. This is an exploratory operating-point",
        "selection, not an external-test estimate.", "",
        "| Language | Recommended costs | Unseen changed exact | Δ vs baseline | Unseen F0.5 | Command ratio |",
        "| --- | --- | ---: | ---: | ---: | ---: |"])
    for row in recommendations:
        lines.append(f"| {row['language_name']} | `{row['recommended_cost']}` | "
                     f"{float(row['median_unseen_changed_exact']):.3f}% | "
                     f"{float(row['delta_vs_baseline_pp']):+.3f} pp | "
                     f"{float(row['median_unseen_f05']):.4f} | "
                     f"{float(row['median_patch_command_ratio']):.3f}× |")
    lines.extend(["", "## Per-language conclusions", "",
                  "The macro result is not substituted for language evidence. Each language page now",
                  "publishes its five-split knowledge curve, dictionary-specific exact cost classes,",
                  "within-language association coverage, and a bounded conclusion. The compact index",
                  "below exposes the main measured differences; follow the link for the supporting rows",
                  "and interpretation limits.", "",
                  "| Language | Baseline exact at 10% | Baseline exact at 90% | Knowledge effect | Selected costs | Median command change | Conclusion |",
                  "| --- | ---: | ---: | ---: | --- | ---: | --- |"])
    for recommendation in recommendations:
        language = recommendation["language"]
        curve = language_curves[language]
        if len(curve) != 9:
            raise ValueError(f"Incomplete per-language conclusion curve for {language}.")
        first_language_point = curve[0]
        last_language_point = curve[-1]
        gain = (float(last_language_point["baseline_unseen_changed_exact"])
                - float(first_language_point["baseline_unseen_changed_exact"]))
        command_change = 100.0 * (float(recommendation["median_patch_command_ratio"]) - 1.0)
        page = LANGUAGE_PAGES[language].removesuffix(".md")
        lines.append(
            f"| {recommendation['language_name']} | "
            f"{float(first_language_point['baseline_unseen_changed_exact']):.3f}% | "
            f"{float(last_language_point['baseline_unseen_changed_exact']):.3f}% | "
            f"{gain:+.3f} pp | `{recommendation['recommended_cost']}` | "
            f"{command_change:+.2f}% | [Evidence and conclusion](languages/{page}.md#edit-cost-conclusion) |"
        )
    lines.extend(["", "## Generalization across knowledge levels", "",
                  "Each cell is the median across 20 languages × five frozen splits at the stated",
                  "knowledge level. The recommended curve applies each language's independently selected",
                  "setting; it is not a single global configuration. Percentages remain macro summaries",
                  "rather than token-weighted production estimates.", "",
                  "| Knowledge | Baseline unseen changed exact | Recommended | Δ | Baseline F0.5 | Recommended F0.5 | Baseline commands | Recommended commands |",
                  "| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"])
    for row in knowledge_curve:
        lines.append(f"| {row['training_percent']}% | "
                     f"{float(row['baseline_unseen_changed_exact']):.3f}% | "
                     f"{float(row['recommended_unseen_changed_exact']):.3f}% | "
                     f"{float(row['exact_delta_pp']):+.3f} pp | "
                     f"{float(row['baseline_unseen_f05']):.4f} | "
                     f"{float(row['recommended_unseen_f05']):.4f} | "
                     f"{float(row['baseline_patch_command_ratio']):.3f}× | "
                     f"{float(row['recommended_patch_command_ratio']):.3f}× |")
    lines.extend(["", "## Dictionary properties and selected costs", "",
                  "These across-language coefficients connect dictionary descriptors to the exploratory",
                  "language-specific selections above. With 20 dictionaries and selection on the same",
                  "data, they generate hypotheses about dictionary types; they do not establish a portable",
                  "cost-selection rule for an unseen resource. Predictor and selected-outcome labels are",
                  "defined in the [methodology glossary](reference/edit-cost-methodology.md#predictor-and-outcome-glossary).", "",
                  "| Dictionary property | Selected outcome | Spearman ρ | Pearson r | Languages |",
                  "| --- | --- | ---: | ---: | ---: |"])
    for row in strongest_recommendation_associations:
        lines.append(f"| `{row['predictor']}` | `{row['outcome']}` | "
                     f"{float(row['spearman']):+.3f} | {float(row['pearson']):+.3f} | "
                     f"{row['languages']} |")
    lines.extend(["", "## Associations across cost configurations", "",
                  "Correlations are calculated separately inside every language × seed × knowledge stratum;",
                  "the table reports the median and central 95% empirical interval of those within-stratum",
                  "coefficients. This avoids allowing language size or knowledge level to manufacture a pooled",
                  "association. The interval is descriptive, not an independence-adjusted confidence interval.",
                  "Every predictor and outcome label is defined in the "
                  "[methodology glossary](reference/edit-cost-methodology.md#predictor-and-outcome-glossary).",
                  "Only the following pairs keep the same sign throughout that central interval.", "",
                  "| Predictor | Outcome | Median Spearman ρ | Central 95% | Median Pearson r | Strata |",
                  "| --- | --- | ---: | ---: | ---: | ---: |"])
    for row in stable_associations:
        lines.append(f"| `{row['predictor']}` | `{row['outcome']}` | {float(row['spearman_median']):+.3f} | "
                     f"{float(row['spearman_q025']):+.3f}…{float(row['spearman_q975']):+.3f} | "
                     f"{float(row['pearson_median']):+.3f} | {row['strata']} |")
    lines.extend(["", "### Quality associations are heterogeneous", "",
                  "No cost or representation predictor keeps one Spearman sign across the central 95%",
                  "of strata for any unseen-form quality outcome. The largest absolute median for each",
                  "outcome is shown below to make that heterogeneity visible; it must not be interpreted",
                  "as a global effect that transfers between languages or knowledge levels.", "",
                  "| Predictor | Outcome | Median Spearman ρ | Central 95% | Median Pearson r | Strata |",
                  "| --- | --- | ---: | ---: | ---: | ---: |"])
    for row in heterogeneous_quality:
        lines.append(f"| `{row['predictor']}` | `{row['outcome']}` | {float(row['spearman_median']):+.3f} | "
                     f"{float(row['spearman_q025']):+.3f}…{float(row['spearman_q975']):+.3f} | "
                     f"{float(row['pearson_median']):+.3f} | {row['strata']} |")
    lines.extend(["", "### Smallest observed association envelopes", "",
                  "These pairs have the smallest central-interval envelope in the measured matrix.",
                  "They are candidates for practical insensitivity, not proof of independence; an interval",
                  "that spans substantial positive and negative values instead indicates heterogeneity.", "",
                  "| Predictor | Outcome | Median Spearman ρ | Central 95% | Median Pearson r | Strata |",
                  "| --- | --- | ---: | ---: | ---: | ---: |"])
    for row in smallest_envelopes:
        lines.append(f"| `{row['predictor']}` | `{row['outcome']}` | {float(row['spearman_median']):+.3f} | "
                     f"{float(row['spearman_q025']):+.3f}…{float(row['spearman_q975']):+.3f} | "
                     f"{float(row['pearson_median']):+.3f} | {row['strata']} |")
    lines.extend(["", "## Interpretation boundaries", "",
                  "The experiment establishes sensitivity and within-resource generalization, not causality",
                  "or performance on unrelated domains. A weak median correlation is not described as proof",
                  "of no relationship: heterogeneous signs, nonlinear effects, and repeated splits remain",
                  "possible. Recommendations therefore remain language-specific until validated on an external",
                  "dictionary or corpus. See the [full protocol](reference/edit-cost-methodology.md) and",
                  "[raw-data instructions](reference/reproducibility.md).", ""])
    return "\n".join(lines)


def methodology() -> str:
    return """# Edit-Cost Experiment Methodology

## Question and frozen design

The experiment asks how relative patch-command edit costs affect compiled-trie structure,
command vocabulary, exact-root transfer, and pairwise stemming behavior as dictionary knowledge
increases. Delete, insert, and replace costs use `1, 2, 3, 5, 10`; match uses `0, 1`.
Configurations differing only by a common positive scale are deduplicated, leaving 234 points.
`D1I1R1M0` is the production baseline.

Every label uses the fixed form `D<delete>I<insert>R<replace>M<match>`:

- `D` is the dynamic-programming cost of deleting one source character;
- `I` is the cost of inserting one target character;
- `R` is the cost of replacing one source character with one target character;
- `M` is the cost of keeping an equal source/target character unchanged—the match or skip step.

For example, `D2I5R3M0` means delete cost 2, insert cost 5, replace cost 3, and match cost 0.
The numbers are relative edit-path costs. They are not numbers of patch commands, trie nodes, or
dictionary observations.

For each language, the application also fingerprints the complete ordered sequence of commands
generated for every dictionary pair. Grid points with identical sequences are exactly equivalent:
they build identical tries for every nested subset, not merely statistically similar ones. One
representative is evaluated and every member label is retained in `equivalent_cost_labels`; the
validator expands these classes back to the complete logical 234-point matrix for analysis. Because
scale-equivalent points were already removed, this second collapse measures dictionary-specific
insensitivity to genuinely different relative cost settings. SHA-256 is only a candidate-bucketing
mechanism; membership is confirmed by direct command equality over the complete dictionary.

Every dictionary is ranked by the protocol hash under five predeclared seeds. Nested exact-size
prefixes provide 10% through 100% knowledge. Before evaluation, dictionaries are ordered by their
full-dictionary baseline count of distinct patch commands, smallest first. The report is streamed
through a `.partial` file and flushed after each language, so an intentionally stopped late run
retains completed observations.

## Outcomes

The complete dictionary is always evaluated because that is the operating population whose
generalization is being studied. The primary outcome for partial knowledge is `unseen_changed_exact`:
changed forms from withheld dictionary families after removing any normalized surface already seen
in training. The broader whole and withheld scopes remain diagnostic. At 100% knowledge the unseen
denominator is correctly zero.

Pairwise precision, recall, F0.5/F1/F2, balanced accuracy, MCC, over-stemming, and under-stemming
are calculated from raw pair counts. Trie nodes, edges, depths, dense lookup slots, generated
commands, retained commands, and baseline-relative retained-command ratios describe representation
cost. These structural counts are reproducible representation proxies, not measurements of JVM heap
occupancy or serialized artifact bytes. Undefined ratios remain empty; they are never replaced with
zero.

The unseen pairwise scope is a generalization stress test, not a full-model quality report. It keeps
only forms from withheld dictionary families whose normalized surface was absent from training.
Unseen-family under-stemming is `FN / (TP + FN)` over gold-related form pairs, so one incorrectly
separated form can break several relations and the percentage is not a per-form error rate. Published
unseen-family OI/UI summaries are medians over five splits at each 10%–90% knowledge level. At 100%
knowledge the unseen scope is empty; the central comparison table therefore reports a separate
full-model UI calculated over the complete dictionary.

## Predictor and outcome glossary

`Predictor` and `outcome` describe a quantity's role in one reported association; they are not
permanent types. A predictor is the left-hand quantity whose variation is compared with the
right-hand outcome. For example, `patch_command_ratio` is an outcome in a cost-to-representation
association and a predictor in a representation-to-quality association. These analyses measure
association, not causation. The labels below are also the column identifiers used by the derived
machine-readable tables.

### Edit-cost predictors

| Label | Definition | Unit and interpretation |
| --- | --- | --- |
| `delete_cost` | Dynamic-programming cost of deleting one source character (`D`). | Relative cost; no independently meaningful absolute unit. |
| `insert_cost` | Dynamic-programming cost of inserting one target character (`I`). | Relative cost. |
| `replace_cost` | Dynamic-programming cost of replacing one source character with one target character (`R`). | Relative cost. |
| `match_cost` | Cost of retaining an equal source/target character—the match or skip step (`M`). | Relative cost; zero is valid. |
| `delete_to_insert_ratio` | `delete_cost / insert_cost`. | Dimensionless balance; `1` means equal delete and insert costs. |
| `replace_to_delete_insert` | `replace_cost / (delete_cost + insert_cost)`. | Dimensionless comparison of direct replacement with deletion followed by insertion. |
| `edit_cost_imbalance` | `max(delete_cost, insert_cost, replace_cost) / min(delete_cost, insert_cost, replace_cost)`. Match cost is deliberately excluded because zero is valid. | Dimensionless spread; `1` means equal non-match costs. |

### Dictionary-level quantities

| Label | Definition | Unit and interpretation |
| --- | --- | --- |
| `dictionary_rows` | Number of source dictionary rows; each row defines one stem family. | Rows/families. |
| `dictionary_forms` | Number of evaluated form occurrences over the complete dictionary, including each row's stem and variants. | Form occurrences; not asserted to be globally unique surfaces. |
| `mean_family_size` | `dictionary_forms / dictionary_rows`. | Mean evaluated forms per stem family. |
| `changed_form_share` | Complete-dictionary proportion of form occurrences whose surface differs from the row's stem. | Ratio in `[0, 1]`. |
| `baseline_patch_commands` | Number of distinct patch-command values retained by the compiled full-dictionary trie under `D1I1R1M0`. | Commands. |
| `exact_equivalence_classes` | Number of command-sequence equivalence classes induced by the 234 normalized cost configurations for one complete dictionary. Configurations share a class only when every generated command agrees in order. | Classes; an outcome of the dictionary-sensitivity analysis and a predictor in the selected-cost analysis. More classes mean greater observed cost sensitivity, not better quality. |

### Compiled-trie representation quantities

| Label | Definition | Unit and interpretation |
| --- | --- | --- |
| `patch_command_ratio` | Candidate trie's distinct retained patch-command count divided by the matching `D1I1R1M0` count for the same language, seed, and knowledge level. | Ratio; `1` equals baseline, below `1` uses fewer distinct commands. |
| `trie_nodes` | Unique physical internal nodes plus unique physical leaf nodes in the reduced trie graph. | Nodes; shared reduced subtrees are counted once. |
| `trie_edges` | Outgoing child edges stored by unique physical nodes. | Edges. |
| `longest_path` | Maximum logical root-to-leaf path length. | Edges traversed. |
| `average_path_length` | Arithmetic mean logical root-to-leaf path length, with each distinct logical path weighted once. | Edges traversed; paths that converge on a shared reduced subtree remain distinct. |
| `dense_table_slots` | Total addressable slots allocated by dense child-lookup tables across unique physical nodes. | Slots, including unoccupied positions inside those tables. |
| `value_references` | Patch-value references stored across unique physical nodes. | References; repeated references to the same distinct value are counted separately. |
| `logical_leaf_paths` | Number of distinct logical root-to-leaf paths represented by the reduced trie graph. | Paths; converging paths remain distinct even when they share physical nodes. |

These quantities are outcomes in cost-to-representation analysis. They can also be predictors in
representation-to-quality analysis. They describe the compiled representation and are not direct
measurements of heap occupancy, serialized file size, or runtime latency.

### Unseen-family quality outcomes

All four labels below use only withheld-family forms whose normalized surface did not occur in the
training subset. They are defined only at 10%–90% knowledge; the unseen scope is empty at 100%.
Pairwise metrics treat two forms as a positive pair when their dictionary-family memberships
intersect and as a negative pair when they are disjoint.

| Label | Definition | Unit and preferred direction |
| --- | --- | --- |
| `unseen_changed_exact` | `100 × unseen_changed_correct / unseen_changed_total`, restricted to surface forms that differ from their expected stem. | Percent of form occurrences; higher is better. |
| `unseen_f05` | Pairwise F0.5 computed from precision and recall in the unseen-family scope. The `β = 0.5` weighting favors precision and therefore penalizes over-stemming more strongly than F1. | Score in `[0, 1]`; higher is better. |
| `unseen_over_percent` | `100 × FP / (TN + FP)` for pairwise relations in the unseen-family scope. | Percent of gold-unrelated pairs incorrectly joined; lower is better. |
| `unseen_under_percent` | `100 × FN / (TP + FN)` for pairwise relations in the unseen-family scope. | Percent of gold-related pairs incorrectly separated; lower is better. This is not a per-form error rate. |

### Selected-cost outcomes

These labels occur in the across-dictionary analysis of the exploratory recommendation selected
for each language from all 45 partial-knowledge observations.

| Label | Definition | Unit and interpretation |
| --- | --- | --- |
| `recommended_delete_cost` | Delete component of the selected cost configuration. | Relative cost. |
| `recommended_insert_cost` | Insert component of the selected cost configuration. | Relative cost. |
| `recommended_replace_cost` | Replace component of the selected cost configuration. | Relative cost. |
| `recommended_match_cost` | Match component of the selected cost configuration. | Relative cost. |
| `recommended_command_ratio` | Median `patch_command_ratio` of the selected configuration across five seeds and nine partial-knowledge levels. | Ratio to the matching baseline. |
| `recommended_exact_delta_pp` | Selected configuration's median `unseen_changed_exact` minus the baseline median over the same 45 observations. | Percentage points; positive means higher exactness than baseline. |

## Analysis protocol

Spearman correlation is primary for monotonic association and Pearson correlation is secondary.
Cost predictors include individual normalized costs, delete/insert balance, replacement cost versus
delete-plus-insert, and edit-cost imbalance. Outcomes include command ratio, trie nodes, edges, path
lengths, dense-table slots, value references, logical leaf paths, and the four unseen-form quality
measures; representation-to-quality associations are also reported. All coefficients are calculated
within language × seed × knowledge strata before their
distribution is summarized, avoiding a pooled language-size or knowledge-level confound. No weak
result is called proof of no effect, and pairs without within-stratum variance are omitted.
Recommendation uses only configurations that remain `VIABLE`
(at most five times the matching baseline command count), a frozen 0.25 percentage-point exactness
tolerance, then command ratio, unseen F0.5, and exactness in that order.

Two additional descriptive selectors expose the objective trade-off without replacing that
recommendation. The structural selector minimizes the distinct patch-command count at 100%
dictionary knowledge; seed invariance is required, and partial-knowledge F0.5, over-stemming, and
under-stemming break equal-count ties. The quality selector considers only configurations that are
`VIABLE` throughout all 45 partial-knowledge observations and do not worsen either median unseen
over-stemming or median unseen under-stemming relative to `D1I1R1M0`. It then maximizes median
unseen F0.5, with lower over-stemming and under-stemming as tie-breakers. These selectors are
post-experiment descriptive optima inside the measured grid, not predeclared production choices.

The central report summarizes all 900 language × seed × partial-knowledge strata. Each language
page also reports a separate distribution across its 45 seed × knowledge strata. A language-level
association is called stable only when the coefficient is defined in all 45 strata and the central
95% empirical interval retains one sign. Partial coverage remains visible but cannot support that
claim. When a quality outcome is constant across cost configurations, the absence of a coefficient
is reported as observed cost insensitivity in this matrix rather than as missing measurement.

The cost grid and recommendation rule are exploratory because the same resources support selection
and reporting. A claim about a new domain requires a separate external dictionary or corpus that
was not used for configuration selection.

## Reproduction

Run the experiment with an explicit stable campaign identity:

```bash
./gradlew --no-daemon \\
  -PdictionaryGeneralizationReleaseVersion=<source-identity> \\
  editCostSensitivity

python3 tools/update-edit-cost-documentation.py \\
  build/reports/generalization/edit-cost-sensitivity.csv docs update
```

On interruption, preserve `edit-cost-sensitivity.csv.partial`; it contains every buffer flushed
through the last completed language plus any subsequently written complete rows. Publication rejects
partial matrices unless `--allow-partial` is explicitly used for a non-publishing pilot check.
"""


def write_if_changed(path: Path, content: str) -> None:
    if not path.exists() or path.read_text(encoding="utf-8") != content:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


def deterministic_gzip(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with source.open("rb") as input_handle, destination.open("wb") as output_handle:
        with gzip.GzipFile(filename="", mode="wb", fileobj=output_handle, mtime=0, compresslevel=9) as compressed:
            shutil.copyfileobj(input_handle, compressed)


def digest_uncompressed(path: Path) -> str:
    digest = hashlib.sha256()
    with open_text(path) as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), ""):
            digest.update(chunk.encode("utf-8"))
    return digest.hexdigest()


def main() -> None:
    args = arguments()
    docs = args.documentation_root
    archive = docs / "benchmarks/data/edit-cost-sensitivity.csv.gz"
    validate_update_paths(args.source, archive, args.mode)
    observations, identity, features = read_and_validate(args.source, args.allow_partial)
    checksum = digest_uncompressed(args.source)
    if args.mode == "check":
        print(f"Validated {len(observations):,} edit-cost observations ({checksum}).")
        return
    recommendations = recommendation_rows(observations)
    minimum_commands = minimum_command_rows(observations, features)
    quality_optima = quality_optimum_rows(observations)
    knowledge_curve = knowledge_curve_rows(observations, recommendations)
    language_knowledge_curves = language_knowledge_curve_rows(observations, recommendations)
    correlations = correlation_rows(observations)
    language_correlations = language_correlation_rows(observations)
    sensitivity_associations = dictionary_sensitivity_rows(features)
    recommendation_associations = dictionary_recommendation_association_rows(features, recommendations)
    analysis_checksum = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    results = render_results(observations, identity, features, recommendations,
                             minimum_commands, quality_optima, knowledge_curve,
                             language_knowledge_curves, correlations, sensitivity_associations,
                             recommendation_associations, checksum, analysis_checksum)
    outputs = {
        docs / "benchmarks/edit-cost-sensitivity.md": results,
        docs / "benchmarks/reference/edit-cost-methodology.md": methodology(),
        docs / "benchmarks/data/edit-cost-recommendations.csv": csv_text(recommendations),
        docs / "benchmarks/data/edit-cost-minimum-commands.csv": csv_text(minimum_commands),
        docs / "benchmarks/data/edit-cost-quality-optima.csv": csv_text(quality_optima),
        docs / "benchmarks/data/edit-cost-knowledge-curve.csv": csv_text(knowledge_curve),
        docs / "benchmarks/data/edit-cost-language-knowledge-curve.csv":
            csv_text(language_knowledge_curves),
        docs / "benchmarks/data/edit-cost-correlations.csv": csv_text(correlations),
        docs / "benchmarks/data/edit-cost-language-correlations.csv":
            csv_text(language_correlations),
        docs / "benchmarks/data/edit-cost-dictionary-sensitivity.csv": csv_text(sensitivity_associations),
        docs / "benchmarks/data/edit-cost-dictionary-recommendations.csv":
            csv_text(recommendation_associations),
        docs / "benchmarks/data/edit-cost-sensitivity.csv.sha256": checksum + "  edit-cost-sensitivity.csv\n",
    }
    recommendations_by_language = {row["language"]: row for row in recommendations}
    curves_by_language: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in language_knowledge_curves:
        curves_by_language[row["language"]].append(row)
    correlations_by_language: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in language_correlations:
        correlations_by_language[row["language"]].append(row)
    for language, page_name in LANGUAGE_PAGES.items():
        page = docs / "benchmarks/languages" / page_name
        if not page.is_file():
            raise ValueError(f"Missing language benchmark page: {page}")
        section = render_language_section(
            language, features[language], recommendations_by_language[language],
            curves_by_language[language], correlations_by_language[language])
        outputs[page] = replace_language_section(page.read_text(encoding="utf-8"), section)
    if args.mode == "update":
        if args.allow_partial:
            raise ValueError("Partial experiment data cannot be published.")
        for path, content in outputs.items():
            write_if_changed(path, content)
        deterministic_gzip(args.source, archive)
    else:
        for path, content in outputs.items():
            if not path.exists() or path.read_text(encoding="utf-8") != content:
                raise ValueError(f"Checked-in edit-cost documentation is stale: {path}")
        if not archive.is_file() or digest_uncompressed(archive) != checksum:
            raise ValueError("Checked-in compressed edit-cost observations are stale.")
    completed_action = "Updated" if args.mode == "update" else "Verified"
    print(f"{completed_action} {len(observations):,} edit-cost observations ({checksum}).")


if __name__ == "__main__":
    main()
