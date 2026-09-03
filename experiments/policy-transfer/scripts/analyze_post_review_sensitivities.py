#!/usr/bin/env python3
"""Generate post-review aggregate sensitivity analyses for Radixor paper 4.

The analyses are secondary and do not modify the frozen primary estimand:

* relation-specificity separates shared-positive, policy-disagreement, and
  GS1-negative relations to test whether the nested-policy result can be
  explained by a uniform shift in stemming aggressiveness;
* component-size sensitivity stratifies the directional effect by disagreement
  pair mass to expose the weighting behavior behind component-macro versus
  pair-micro estimates.
"""
from __future__ import annotations

import csv
import json
import math
from pathlib import Path
from typing import Mapping, Sequence

STRATEGIES = ("medoid", "shortest", "lexical")
SIZE_BINS = (
    ("1-10", 1, 10),
    ("11-100", 11, 100),
    ("101-1000", 101, 1000),
    (">1000", 1001, None),
)


def read_rows(path: Path) -> list[dict[str, str]]:
    """Read one UTF-8 CSV table."""
    with path.open("r", encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream))


def write_csv(path: Path, rows: Sequence[Mapping[str, object]]) -> None:
    """Write deterministic UTF-8 CSV rows."""
    if not rows:
        raise ValueError(f"No rows available for {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=tuple(rows[0].keys()), lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def relation_specificity(root: Path) -> list[dict[str, object]]:
    """Derive relation-stratified join counts from published confusion tables."""
    derived = root / "data/derived"
    preflight = json.loads((derived / "preflight.json").read_text(encoding="utf-8"))
    pairwise = {
        (row["representative"], row["model"], row["gold"]): row
        for row in read_rows(derived / "pairwise_summary.csv")
    }

    disagreement_pairs = int(preflight["disagreement_pairs"])
    if int(preflight["disagreement_g2_same"]) != 0:
        raise AssertionError("Specificity analysis assumes the observed GS2-within-GS1 refinement")

    full_info_rows = read_rows(derived / "full_information_summary.csv")
    medoid_gs2 = next(
        row
        for row in full_info_rows
        if row["representative"] == "medoid"
        and row["model"] == "M_GS2_FULL"
        and row["gold"] == "GS2"
    )
    shared_positive_pairs = int(medoid_gs2["tp"])

    rows: list[dict[str, object]] = []
    for strategy in STRATEGIES:
        for model in ("M_GS1", "M_GS2"):
            gs1 = pairwise[(strategy, model, "GS1")]
            gs2 = pairwise[(strategy, model, "GS2")]
            shared_positive_joins = int(gs2["tp"])
            disagreement_joins = int(gs1["tp"]) - shared_positive_joins
            gs1_negative_joins = int(gs1["fp"])
            if disagreement_joins < 0:
                raise AssertionError(f"Negative disagreement join count for {strategy}/{model}")
            rows.append(
                {
                    "representative": strategy,
                    "model": model,
                    "shared_positive_pairs": shared_positive_pairs,
                    "shared_positive_joins": shared_positive_joins,
                    "shared_positive_join_rate": shared_positive_joins / shared_positive_pairs,
                    "policy_disagreement_pairs": disagreement_pairs,
                    "policy_disagreement_joins": disagreement_joins,
                    "policy_disagreement_join_rate": disagreement_joins / disagreement_pairs,
                    "gs1_negative_joins": gs1_negative_joins,
                }
            )
    return rows


def size_sensitivity(root: Path) -> list[dict[str, object]]:
    """Stratify directional effects by disagreement-pair mass per component."""
    components = read_rows(root / "data/derived/component_switch_counts.csv")
    total_by_strategy = {
        strategy: sum(
            int(row["disagreement_pairs"])
            for row in components
            if row["representative"] == strategy
        )
        for strategy in STRATEGIES
    }
    rows: list[dict[str, object]] = []
    for strategy in STRATEGIES:
        selected_strategy = [row for row in components if row["representative"] == strategy]
        for label, lower, upper in SIZE_BINS:
            selected = [
                row
                for row in selected_strategy
                if int(row["disagreement_pairs"]) >= lower
                and (upper is None or int(row["disagreement_pairs"]) <= upper)
            ]
            if not selected:
                raise AssertionError(f"Empty size stratum {strategy}/{label}")
            disagreement = sum(int(row["disagreement_pairs"]) for row in selected)
            aligned = sum(int(row["aligned_switches"]) for row in selected)
            reverse = sum(int(row["reverse_switches"]) for row in selected)
            macro = sum(float(row["delta"]) for row in selected) / len(selected)
            micro = (aligned - reverse) / disagreement
            rows.append(
                {
                    "representative": strategy,
                    "disagreement_pair_bin": label,
                    "components": len(selected),
                    "disagreement_pairs": disagreement,
                    "disagreement_pair_share": disagreement / total_by_strategy[strategy],
                    "aligned_switches": aligned,
                    "reverse_switches": reverse,
                    "delta_macro_within_bin": macro,
                    "delta_micro_within_bin": micro,
                }
            )
    return rows


def main() -> None:
    """Write relation-specificity and component-size sensitivity tables."""
    root = Path(__file__).resolve().parents[1]
    derived = root / "data/derived"
    specificity = relation_specificity(root)
    size_rows = size_sensitivity(root)
    write_csv(derived / "policy_specificity_summary.csv", specificity)
    write_csv(derived / "component_size_sensitivity.csv", size_rows)

    medoid = [row for row in specificity if row["representative"] == "medoid"]
    for row in medoid:
        print(
            f"specificity {row['model']}: shared={row['shared_positive_join_rate']:.6f} "
            f"disagreement={row['policy_disagreement_join_rate']:.6f} "
            f"gs1_negative={row['gs1_negative_joins']}"
        )
    for row in size_rows:
        if row["representative"] == "medoid":
            print(
                f"size {row['disagreement_pair_bin']}: components={row['components']} "
                f"pair_share={row['disagreement_pair_share']:.6f} "
                f"macro={row['delta_macro_within_bin']:.6f} "
                f"micro={row['delta_micro_within_bin']:.6f}"
            )


if __name__ == "__main__":
    main()
