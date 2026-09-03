#!/usr/bin/env python3
"""Audit the CISTEM policy hierarchy and canonical-target sensitivity.

The script writes aggregate-only diagnostics. It never exports lexical forms from
CELEX-derived inputs or generated training dictionaries.
"""
from __future__ import annotations

import argparse
import csv
from collections import Counter, defaultdict
from pathlib import Path
from statistics import mean, median
from typing import Iterable, Mapping

import policy_transfer_experiment as experiment


REPRESENTATIVES: tuple[str, ...] = ("medoid", "shortest", "lexical")
POLICIES: tuple[str, ...] = ("gs1", "gs2")


def raw_memberships(clusters: Iterable[tuple[str, ...]]) -> dict[str, set[int]]:
    """Build normalized word-to-cluster memberships for raw public clusters."""
    memberships: dict[str, set[int]] = defaultdict(set)
    for cluster_index, cluster in enumerate(clusters):
        for word in cluster:
            memberships[word].add(cluster_index)
    return memberships


def count_raw_refinement_violations(
    raw_gs2: Iterable[tuple[str, ...]],
    common: set[str],
    gs1_membership: Mapping[str, set[int]],
) -> int:
    """Count GS2 co-clustered pairs lacking any GS1 co-clustering.

    Duplicate pair occurrences caused by raw ambiguity are harmless because the
    diagnostic is used only to establish whether any counterexample exists.
    """
    violations = 0
    for cluster in raw_gs2:
        members = [word for word in cluster if word in common]
        for left_index in range(len(members)):
            left = members[left_index]
            left_membership = gs1_membership[left]
            for right_index in range(left_index + 1, len(members)):
                right = members[right_index]
                if left_membership.isdisjoint(gs1_membership[right]):
                    violations += 1
    return violations


def collect_targets(project: Path, representative: str, policy: str) -> dict[frozenset[str], str]:
    """Recover deterministic class targets from private fold dictionaries."""
    targets: dict[frozenset[str], str] = {}
    for fold in range(experiment.FOLD_COUNT):
        dictionary = project / "build/private" / representative / f"fold-{fold}" / f"{policy}.tsv"
        if not dictionary.is_file():
            raise FileNotFoundError(
                f"Missing {dictionary}. Run the full experiment before target-sensitivity auditing."
            )
        with dictionary.open("r", encoding="utf-8") as stream:
            for raw_line in stream:
                fields = raw_line.rstrip("\n").split("\t")
                if not fields or not fields[0]:
                    continue
                target = fields[0]
                members = frozenset(fields)
                previous = targets.setdefault(members, target)
                if previous != target:
                    raise AssertionError(
                        f"Non-deterministic target for {representative}/{policy}."
                    )
    return targets


def target_agreement(project: Path, policy: str) -> tuple[int, int]:
    """Return class count and count choosing the same target under all encodings."""
    by_representative = {
        representative: collect_targets(project, representative, policy)
        for representative in REPRESENTATIVES
    }
    key_sets = [set(values) for values in by_representative.values()]
    if not all(keys == key_sets[0] for keys in key_sets[1:]):
        raise AssertionError(f"Canonicalization dictionaries disagree on {policy} class membership.")
    classes = key_sets[0]
    all_same = sum(
        len({by_representative[representative][members] for representative in REPRESENTATIVES}) == 1
        for members in classes
    )
    return len(classes), all_same


def write_metrics(path: Path, metrics: Mapping[str, object]) -> None:
    """Write deterministic aggregate metrics as a two-column CSV file."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(("metric", "value"))
        for key in sorted(metrics):
            writer.writerow((key, metrics[key]))


def main() -> None:
    """Validate the hierarchy and export publication-safe structural diagnostics."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", type=Path, default=Path("."))
    parser.add_argument("--gs1", type=Path, default=Path("data/external/goldstandard1.txt"))
    parser.add_argument("--gs2", type=Path, default=Path("data/external/goldstandard2.txt"))
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("data/derived/policy_structure_summary.csv"),
    )
    arguments = parser.parse_args()

    project = arguments.project.resolve()
    gs1 = arguments.gs1 if arguments.gs1.is_absolute() else project / arguments.gs1
    gs2 = arguments.gs2 if arguments.gs2.is_absolute() else project / arguments.gs2
    output = arguments.output if arguments.output.is_absolute() else project / arguments.output

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

    raw1, _forms1, _ambiguous1 = experiment.parse_raw_clusters(gs1)
    raw2, _forms2, _ambiguous2 = experiment.parse_raw_clusters(gs2)
    raw_membership1 = raw_memberships(raw1)
    raw_membership2 = raw_memberships(raw2)
    raw_common = set(raw_membership1).intersection(raw_membership2)
    raw_violations = count_raw_refinement_violations(raw2, raw_common, raw_membership1)

    policy1, policy2, common = experiment.restrict_policies(gs1, gs2)
    g1_to_g2: dict[int, set[int]] = defaultdict(set)
    g2_to_g1: dict[int, set[int]] = defaultdict(set)
    for word in common:
        g1 = policy1.label_by_word[word]
        g2 = policy2.label_by_word[word]
        g1_to_g2[g1].add(g2)
        g2_to_g1[g2].add(g1)

    refinement_violations = sum(len(parent_ids) != 1 for parent_ids in g2_to_g1.values())
    split_counts = [len(children) for children in g1_to_g2.values() if len(children) > 1]
    split_classes = len(split_counts)

    gs1_pairs = sum(experiment.choose2(len(cluster)) for cluster in policy1.clusters)
    gs2_pairs = sum(experiment.choose2(len(cluster)) for cluster in policy2.clusters)
    if refinement_violations == 0:
        split_pairs = gs1_pairs - gs2_pairs
    else:
        raise AssertionError("Pinned restricted GS2 is not a refinement of GS1.")

    gs1_class_count, gs1_all_same = target_agreement(project, "gs1")
    gs2_class_count, gs2_all_same = target_agreement(project, "gs2")

    metrics: dict[str, object] = {
        "raw_gs1_nonempty_cluster_lines": len(raw1),
        "raw_gs2_nonempty_cluster_lines": len(raw2),
        "raw_common_normalized_forms": len(raw_common),
        "raw_gs2_pair_refinement_violations": raw_violations,
        "restricted_common_forms": len(common),
        "restricted_gs1_classes": len(policy1.clusters),
        "restricted_gs2_classes": len(policy2.clusters),
        "restricted_gs2_refinement_violations": refinement_violations,
        "gs1_classes_split_by_gs2": split_classes,
        "gs1_classes_split_by_gs2_share": split_classes / len(policy1.clusters),
        "mean_gs2_subclasses_per_split_gs1_class": mean(split_counts),
        "median_gs2_subclasses_per_split_gs1_class": median(split_counts),
        "max_gs2_subclasses_per_split_gs1_class": max(split_counts),
        "gs1_within_class_pairs": gs1_pairs,
        "gs2_within_class_pairs": gs2_pairs,
        "gs1_pairs_split_by_gs2": split_pairs,
        "gs1_pairs_split_by_gs2_share": split_pairs / gs1_pairs,
        "gs1_classes_with_same_target_all_encodings": gs1_all_same,
        "gs1_same_target_all_encodings_share": gs1_all_same / gs1_class_count,
        "gs2_classes_with_same_target_all_encodings": gs2_all_same,
        "gs2_same_target_all_encodings_share": gs2_all_same / gs2_class_count,
    }
    write_metrics(output, metrics)
    print(f"policy structure audit passed: {output}")


if __name__ == "__main__":
    main()
