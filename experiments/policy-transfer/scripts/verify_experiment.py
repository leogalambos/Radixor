#!/usr/bin/env python3
"""Independently verify public aggregate outputs of Radixor paper 4."""
from __future__ import annotations

import csv
import json
import math
from pathlib import Path

STRATEGIES = ("medoid", "shortest", "lexical")
SIZE_BINS = (
    ("1-10", 1, 10),
    ("11-100", 11, 100),
    ("101-1000", 101, 1000),
    (">1000", 1001, None),
)


def read_rows(path: Path) -> list[dict[str, str]]:
    """Read a UTF-8 CSV table."""
    with path.open("r", encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream))


def close(left: float, right: float, tolerance: float = 1e-14) -> bool:
    """Return whether two floating-point values agree within absolute tolerance."""
    return math.isclose(left, right, rel_tol=0.0, abs_tol=tolerance)


def metric_triplet(tp: int, fp: int, fn: int) -> tuple[float, float, float]:
    """Return precision, recall, and F1 from confusion counts."""
    precision = tp / (tp + fp) if tp + fp else math.nan
    recall = tp / (tp + fn) if tp + fn else math.nan
    f1 = 2.0 * precision * recall / (precision + recall) if precision + recall else math.nan
    return precision, recall, f1


def verify_cistem_baseline(root: Path) -> None:
    """Verify fold-restricted and full-universe fixed CISTEM summaries."""
    fold_path = root / "data/derived/cistem_baseline_fold.csv"
    summary_path = root / "data/derived/cistem_baseline_summary.csv"
    full_path = root / "data/derived/cistem_baseline_full_universe.csv"
    if not fold_path.is_file() or not summary_path.is_file() or not full_path.is_file():
        raise AssertionError("CISTEM baseline outputs are incomplete; run evaluate_cistem_baseline.py")

    folds = read_rows(fold_path)
    summaries = {row["gold"]: row for row in read_rows(summary_path)}
    full = {row["gold"]: row for row in read_rows(full_path)}
    for gold in ("GS1", "GS2"):
        rows = [row for row in folds if row["gold"] == gold]
        if len(rows) != 5:
            raise AssertionError(f"expected five CISTEM fold rows for {gold}, got {len(rows)}")
        if any(row["relation_scope"] != "fold_restricted" for row in rows):
            raise AssertionError(f"unexpected CISTEM fold relation scope for {gold}")
        clusters = sum(int(row["clusters"]) for row in rows)
        tp = sum(int(row["tp"]) for row in rows)
        fp = sum(int(row["fp"]) for row in rows)
        fn = sum(int(row["fn"]) for row in rows)
        _, _, pair_f1 = metric_triplet(tp, fp, fn)
        macro_f1 = sum(int(row["clusters"]) * float(row["macro_f1"]) for row in rows) / clusters
        summary = summaries[gold]
        if summary["relation_scope"] != "fold_restricted":
            raise AssertionError(f"unexpected CISTEM summary relation scope for {gold}")
        if int(summary["clusters"]) != clusters:
            raise AssertionError(f"CISTEM cluster-count mismatch for {gold}")
        if not close(pair_f1, float(summary["pairwise_f1"])):
            raise AssertionError(f"CISTEM fold-restricted pairwise F1 mismatch for {gold}")
        if not close(macro_f1, float(summary["macro_f1"])):
            raise AssertionError(f"CISTEM fold-restricted macro F1 mismatch for {gold}")

        full_row = full[gold]
        if full_row["relation_scope"] != "full_restricted_universe":
            raise AssertionError(f"unexpected CISTEM full-universe scope for {gold}")
        full_tp = int(full_row["tp"])
        full_fp = int(full_row["fp"])
        full_fn = int(full_row["fn"])
        if full_tp != tp or full_fn != fn:
            raise AssertionError(f"CISTEM full-universe TP/FN must equal fold-restricted totals for {gold}")
        if full_fp < fp:
            raise AssertionError(f"CISTEM full-universe FP must include at least fold-restricted FP for {gold}")
        full_precision, full_recall, full_pair_f1 = metric_triplet(full_tp, full_fp, full_fn)
        if not close(full_precision, float(full_row["pairwise_precision"])):
            raise AssertionError(f"CISTEM full-universe pairwise precision mismatch for {gold}")
        if not close(full_recall, float(full_row["pairwise_recall"])):
            raise AssertionError(f"CISTEM full-universe pairwise recall mismatch for {gold}")
        if not close(full_pair_f1, float(full_row["pairwise_f1"])):
            raise AssertionError(f"CISTEM full-universe pairwise F1 mismatch for {gold}")
        if not close(float(full_row["macro_recall"]), float(summary["macro_recall"])):
            raise AssertionError(f"CISTEM macro recall should be scope-invariant for {gold}")
        if float(full_row["macro_f1"]) > float(summary["macro_f1"]) + 1e-14:
            raise AssertionError(f"CISTEM full-universe macro F1 unexpectedly exceeds fold-restricted F1 for {gold}")
        print(
            f"verified CISTEM {gold}: fold_macro={macro_f1:.12g} "
            f"full_macro={float(full_row['macro_f1']):.12g}"
        )


def verify_full_information(root: Path) -> None:
    """Verify the post-execution full-information reconstruction ceiling."""
    path = root / "data/derived/full_information_summary.csv"
    if not path.is_file():
        raise AssertionError("Full-information output is absent; run evaluate_full_information_ceiling.py")
    rows = read_rows(path)
    if len(rows) != 12:
        raise AssertionError(f"expected 12 full-information rows, got {len(rows)}")
    for strategy in STRATEGIES:
        for policy_index in (1, 2):
            matched = [
                row
                for row in rows
                if row["representative"] == strategy
                and row["model"] == f"M_GS{policy_index}_FULL"
                and row["gold"] == f"GS{policy_index}"
            ]
            if len(matched) != 1:
                raise AssertionError(f"expected one matched full-information row for {strategy}/GS{policy_index}")
            row = matched[0]
            if not close(float(row["macro_f1"]), 1.0, 1e-15):
                raise AssertionError(f"full-information macro reconstruction is not exact for {strategy}/GS{policy_index}")
            if not close(float(row["pairwise_f1"]), 1.0, 1e-15):
                raise AssertionError(f"full-information pairwise reconstruction is not exact for {strategy}/GS{policy_index}")
            if int(row["fp"]) != 0 or int(row["fn"]) != 0:
                raise AssertionError(f"full-information matched confusion counts are not exact for {strategy}/GS{policy_index}")
    print("verified full-information ceiling: 6/6 policy-matched models reconstruct exactly")


def verify_external_full_model(root: Path) -> None:
    """Verify arithmetic and provenance fields of the frozen external full-model table."""
    path = root / "data/derived/external_full_model_benchmark.csv"
    if not path.is_file():
        raise AssertionError("Frozen external full-model benchmark table is absent")
    rows = read_rows(path)
    if len(rows) != 4:
        raise AssertionError(f"expected four external full-model rows, got {len(rows)}")
    expected_commit = "0c3b13f485a9ad0b460c0931e4497ea95bed66a1"
    for row in rows:
        precision, recall, f1 = metric_triplet(int(row["tp"]), int(row["fp"]), int(row["fn"]))
        if row["radixor_commit"] != expected_commit:
            raise AssertionError("external full-model row uses an unexpected Radixor commit")
        if not close(precision, float(row["precision"]), 5e-7):
            raise AssertionError(f"external precision mismatch: {row}")
        if not close(recall, float(row["recall"]), 5e-7):
            raise AssertionError(f"external recall mismatch: {row}")
        if not close(f1, float(row["f1"]), 5e-7):
            raise AssertionError(f"external F1 mismatch: {row}")
    print("verified frozen external full-model benchmark arithmetic and commit provenance")


def verify_post_review_sensitivities(root: Path) -> None:
    """Verify relation-specificity and component-size sensitivity aggregates."""
    derived = root / "data/derived"
    specificity_path = derived / "policy_specificity_summary.csv"
    size_path = derived / "component_size_sensitivity.csv"
    if not specificity_path.is_file() or not size_path.is_file():
        raise AssertionError("Post-review sensitivity outputs are absent")

    pairwise = {
        (row["representative"], row["model"], row["gold"]): row
        for row in read_rows(derived / "pairwise_summary.csv")
    }
    specificity = {
        (row["representative"], row["model"]): row for row in read_rows(specificity_path)
    }
    preflight = json.loads((derived / "preflight.json").read_text(encoding="utf-8"))
    disagreement = int(preflight["disagreement_pairs"])
    for strategy in STRATEGIES:
        for model in ("M_GS1", "M_GS2"):
            row = specificity[(strategy, model)]
            gs1 = pairwise[(strategy, model, "GS1")]
            gs2 = pairwise[(strategy, model, "GS2")]
            shared = int(gs2["tp"])
            disagreement_joins = int(gs1["tp"]) - shared
            if int(row["shared_positive_joins"]) != shared:
                raise AssertionError(f"shared-positive specificity mismatch for {strategy}/{model}")
            if int(row["policy_disagreement_joins"]) != disagreement_joins:
                raise AssertionError(f"disagreement specificity mismatch for {strategy}/{model}")
            if int(row["gs1_negative_joins"]) != int(gs1["fp"]):
                raise AssertionError(f"GS1-negative specificity mismatch for {strategy}/{model}")
            if int(row["policy_disagreement_pairs"]) != disagreement:
                raise AssertionError(f"disagreement denominator mismatch for {strategy}/{model}")

    components = read_rows(derived / "component_switch_counts.csv")
    size_rows = {
        (row["representative"], row["disagreement_pair_bin"]): row for row in read_rows(size_path)
    }
    for strategy in STRATEGIES:
        strategy_rows = [row for row in components if row["representative"] == strategy]
        total_d = sum(int(row["disagreement_pairs"]) for row in strategy_rows)
        for label, lower, upper in SIZE_BINS:
            selected = [
                row
                for row in strategy_rows
                if int(row["disagreement_pairs"]) >= lower
                and (upper is None or int(row["disagreement_pairs"]) <= upper)
            ]
            expected = size_rows[(strategy, label)]
            d = sum(int(row["disagreement_pairs"]) for row in selected)
            a = sum(int(row["aligned_switches"]) for row in selected)
            r = sum(int(row["reverse_switches"]) for row in selected)
            macro = sum(float(row["delta"]) for row in selected) / len(selected)
            micro = (a - r) / d
            if int(expected["components"]) != len(selected) or int(expected["disagreement_pairs"]) != d:
                raise AssertionError(f"size-stratum count mismatch for {strategy}/{label}")
            if not close(float(expected["disagreement_pair_share"]), d / total_d):
                raise AssertionError(f"size-stratum pair-share mismatch for {strategy}/{label}")
            if not close(float(expected["delta_macro_within_bin"]), macro):
                raise AssertionError(f"size-stratum macro mismatch for {strategy}/{label}")
            if not close(float(expected["delta_micro_within_bin"]), micro):
                raise AssertionError(f"size-stratum micro mismatch for {strategy}/{label}")
    print("verified post-review relation-specificity and component-size sensitivities")


def main() -> None:
    """Recompute headline policy-transfer identities from aggregate counts."""
    root = Path(__file__).resolve().parents[1]
    component_path = root / "data/derived/component_switch_counts.csv"
    summary_path = root / "data/derived/policy_transfer_summary.csv"
    if not component_path.is_file() or not summary_path.is_file():
        raise SystemExit("experiment outputs are absent; run policy_transfer_experiment.py first")
    components = read_rows(component_path)
    summaries = {row["representative"]: row for row in read_rows(summary_path)}
    for strategy in STRATEGIES:
        rows = [row for row in components if row["representative"] == strategy]
        if not rows:
            raise AssertionError(f"no component rows for {strategy}")
        deltas: list[float] = []
        aligned = reverse = disagreement = 0
        for row in rows:
            d = int(row["disagreement_pairs"])
            a = int(row["aligned_switches"])
            r = int(row["reverse_switches"])
            delta = float(row["delta"])
            if d <= 0 or a < 0 or r < 0 or a + r > d:
                raise AssertionError(f"invalid component counts: {row}")
            expected = (a - r) / d
            if not close(delta, expected, 1e-15):
                raise AssertionError("component delta mismatch")
            if not close(delta, float(row["directional_identity"]), 1e-15):
                raise AssertionError("mirrored directional identity mismatch")
            switched = a + r
            if switched:
                switch = switched / d
                aligned_share = a / switched
                factorization = switch * (2.0 * aligned_share - 1.0)
                if not close(delta, factorization, 1e-15):
                    raise AssertionError("switch factorization mismatch")
            deltas.append(delta)
            aligned += a
            reverse += r
            disagreement += d
        summary = summaries[strategy]
        macro = sum(deltas) / len(deltas)
        micro = (aligned - reverse) / disagreement
        if not close(macro, float(summary["delta_macro"])):
            raise AssertionError(f"macro mismatch for {strategy}")
        if not close(micro, float(summary["delta_micro"])):
            raise AssertionError(f"micro mismatch for {strategy}")
        print(f"verified {strategy}: components={len(rows)} delta_macro={macro:.12g} delta_micro={micro:.12g}")
    verify_cistem_baseline(root)
    verify_full_information(root)
    verify_external_full_model(root)
    verify_post_review_sensitivities(root)


if __name__ == "__main__":
    main()
