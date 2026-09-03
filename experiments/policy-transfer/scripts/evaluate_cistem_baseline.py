#!/usr/bin/env python3
"""Evaluate the pinned official CISTEM stemmer for paper 4.

Two relation scopes are reported deliberately:

* ``fold_restricted`` retains the five held-out subsets used by the Radixor
  cross-fit experiment.  This is the scope that is structurally comparable to
  cross-fit Radixor relation metrics, because cross-fold Radixor outputs come
  from different fitted models.
* ``full_restricted_universe`` scores the one fixed CISTEM model over all
  common-universe forms at once.  This includes cross-fold false-positive
  collisions and is therefore CISTEM's unrestricted score on the controlled
  298,259-form universe.

CISTEM is never retrained per fold and does not enter the primary policy
interaction statistic.
"""
from __future__ import annotations

import argparse
import csv
import importlib.util
import math
from pathlib import Path
from types import ModuleType
from typing import Mapping, Sequence

import policy_transfer_experiment as experiment

CISTEM_PYTHON_BLOB = "dbc90836bb6361712b52b2e504b85c702294a29f"
CISTEM_PYTHON_SIZE = 4_585
FOLD_SCOPE = "fold_restricted"
FULL_SCOPE = "full_restricted_universe"


def load_cistem(path: Path) -> ModuleType:
    """Load and validate the pinned official CISTEM Python implementation."""
    experiment.validate_external_file(path, CISTEM_PYTHON_BLOB, CISTEM_PYTHON_SIZE)
    spec = importlib.util.spec_from_file_location("radixor_paper4_cistem", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load CISTEM implementation from {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    if not callable(getattr(module, "stem", None)):
        raise RuntimeError("Pinned CISTEM implementation does not expose stem(word)")
    return module


def metric_triplet(tp: int, fp: int, fn: int) -> tuple[float, float, float]:
    """Return precision, recall, and F1 from pairwise confusion counts."""
    precision = tp / (tp + fp) if tp + fp else math.nan
    recall = tp / (tp + fn) if tp + fn else math.nan
    f1 = (
        2.0 * precision * recall / (precision + recall)
        if precision + recall
        else math.nan
    )
    return precision, recall, f1


def write_csv(path: Path, rows: Sequence[Mapping[str, object]]) -> None:
    """Write a non-empty sequence of dictionaries as deterministic UTF-8 CSV."""
    if not rows:
        raise ValueError(f"No rows available for {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = tuple(rows[0].keys())
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def scored_row(
    gold_name: str,
    words: Sequence[str],
    gold: Mapping[str, int],
    predicted: Mapping[str, str],
    relation_scope: str,
) -> dict[str, object]:
    """Create one complete CISTEM result row for a supplied relation scope."""
    tp, fp, fn = experiment.pairwise_confusion(words, gold, predicted)
    pair_precision, pair_recall, pair_f1 = metric_triplet(tp, fp, fn)
    clusters, macro_precision, macro_recall, macro_f1 = experiment.cistem_macro(
        words, gold, predicted
    )
    return {
        "model": "CISTEM",
        "gold": gold_name,
        "relation_scope": relation_scope,
        "forms": len(words),
        "clusters": clusters,
        "macro_precision": macro_precision,
        "macro_recall": macro_recall,
        "macro_f1": macro_f1,
        "tp": tp,
        "fp": fp,
        "fn": fn,
        "pairwise_precision": pair_precision,
        "pairwise_recall": pair_recall,
        "pairwise_f1": pair_f1,
        "cistem_commit": experiment.CISTEM_COMMIT,
        "cistem_python_blob": CISTEM_PYTHON_BLOB,
    }


def main() -> None:
    """Score CISTEM on fold-restricted and full-universe relation scopes."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", type=Path, default=Path("."))
    parser.add_argument("--gs1", type=Path, default=Path("data/external/goldstandard1.txt"))
    parser.add_argument("--gs2", type=Path, default=Path("data/external/goldstandard2.txt"))
    parser.add_argument("--cistem", type=Path, default=Path("data/external/Cistem.py"))
    arguments = parser.parse_args()

    project = arguments.project.resolve()
    gs1 = (project / arguments.gs1).resolve() if not arguments.gs1.is_absolute() else arguments.gs1
    gs2 = (project / arguments.gs2).resolve() if not arguments.gs2.is_absolute() else arguments.gs2
    cistem_path = (
        (project / arguments.cistem).resolve()
        if not arguments.cistem.is_absolute()
        else arguments.cistem
    )

    experiment.validate_external_file(
        gs1,
        experiment.CISTEM_BLOBS["goldstandard1.txt"],
        experiment.CISTEM_SIZES["goldstandard1.txt"],
    )
    experiment.validate_external_file(
        gs2,
        experiment.CISTEM_BLOBS["goldstandard2.txt"],
        experiment.CISTEM_SIZES["goldstandard2.txt"],
    )
    cistem = load_cistem(cistem_path)

    policy1, policy2, common = experiment.restrict_policies(gs1, gs2)
    components = experiment.build_join_components(policy1, policy2, common)
    assignments = experiment.assign_folds(components)
    predicted = {word: str(cistem.stem(word)) for word in common}

    fold_rows: list[dict[str, object]] = []
    for fold in range(experiment.FOLD_COUNT):
        heldout_components = [
            component
            for component in components
            if assignments[component.identifier] == fold
        ]
        heldout_words = tuple(
            sorted(word for component in heldout_components for word in component.words)
        )
        for gold_name, gold in (
            ("GS1", policy1.label_by_word),
            ("GS2", policy2.label_by_word),
        ):
            row = scored_row(gold_name, heldout_words, gold, predicted, FOLD_SCOPE)
            fold_rows.append({"fold": fold, **row})

    summary_rows: list[dict[str, object]] = []
    for gold_name in ("GS1", "GS2"):
        selected = [row for row in fold_rows if row["gold"] == gold_name]
        clusters = sum(int(row["clusters"]) for row in selected)
        tp = sum(int(row["tp"]) for row in selected)
        fp = sum(int(row["fp"]) for row in selected)
        fn = sum(int(row["fn"]) for row in selected)
        pair_precision, pair_recall, pair_f1 = metric_triplet(tp, fp, fn)

        def weighted(field: str) -> float:
            """Return the cluster-count-weighted fold mean for one macro metric."""
            return (
                sum(int(row["clusters"]) * float(row[field]) for row in selected)
                / clusters
            )

        summary_rows.append(
            {
                "model": "CISTEM",
                "gold": gold_name,
                "relation_scope": FOLD_SCOPE,
                "forms": len(common),
                "clusters": clusters,
                "macro_precision": weighted("macro_precision"),
                "macro_recall": weighted("macro_recall"),
                "macro_f1": weighted("macro_f1"),
                "tp": tp,
                "fp": fp,
                "fn": fn,
                "pairwise_precision": pair_precision,
                "pairwise_recall": pair_recall,
                "pairwise_f1": pair_f1,
                "cistem_commit": experiment.CISTEM_COMMIT,
                "cistem_python_blob": CISTEM_PYTHON_BLOB,
            }
        )

    full_words = tuple(sorted(common))
    full_rows = [
        scored_row("GS1", full_words, policy1.label_by_word, predicted, FULL_SCOPE),
        scored_row("GS2", full_words, policy2.label_by_word, predicted, FULL_SCOPE),
    ]

    derived = project / "data/derived"
    write_csv(derived / "cistem_baseline_fold.csv", fold_rows)
    write_csv(derived / "cistem_baseline_summary.csv", summary_rows)
    write_csv(derived / "cistem_baseline_full_universe.csv", full_rows)

    for scope, rows in ((FOLD_SCOPE, summary_rows), (FULL_SCOPE, full_rows)):
        for row in rows:
            print(
                f"{scope} {row['gold']}: macro_f1={float(row['macro_f1']):.12g} "
                f"pairwise_f1={float(row['pairwise_f1']):.12g}"
            )


if __name__ == "__main__":
    main()
