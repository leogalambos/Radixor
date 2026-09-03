#!/usr/bin/env python3
"""Evaluate full-information Radixor policy reconstruction on the controlled universe.

This post-execution secondary analysis is intentionally in-sample.  It reconstructs
one all-data model for every target encoding and policy from the already generated
cross-fit training dictionaries, then evaluates that model on the complete common
unambiguous universe.  The result is a reconstruction ceiling, not a generalization
estimate.  It is used only to quantify the gap introduced by removing every training
form from the held-out lexical components in the primary cross-fit experiment.
"""
from __future__ import annotations

import argparse
import csv
from pathlib import Path
from typing import Mapping, Sequence

import policy_transfer_experiment as experiment


def locate_frozen_jar(project: Path) -> Path:
    """Return the frozen Radixor JAR used by either publication layout."""
    candidates = (
        project / "third_party/radixor/Radixor-4.2.0-8-g0c3b13f.jar",
        project / "frozen/Radixor-4.2.0-8-g0c3b13f.jar",
    )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise FileNotFoundError("Frozen Radixor JAR not found in publication or repository layout.")


def canonical_map(project: Path, strategy: str, policy_index: int) -> dict[str, str]:
    """Recover deterministic class targets from the five cross-fit dictionaries."""
    result: dict[str, str] = {}
    for fold in range(experiment.FOLD_COUNT):
        dictionary = project / "build/private" / strategy / f"fold-{fold}" / f"gs{policy_index}.tsv"
        if not dictionary.is_file():
            raise FileNotFoundError(
                f"Missing {dictionary}; run the primary experiment before the full-information ceiling."
            )
        with dictionary.open("r", encoding="utf-8") as stream:
            for line in stream:
                fields = line.rstrip("\n").split("\t")
                if not fields or not fields[0]:
                    continue
                canonical = fields[0]
                for word in fields:
                    previous = result.get(word)
                    if previous is not None and previous != canonical:
                        raise RuntimeError(
                            f"Inconsistent canonical target for {word!r}: {previous!r} versus {canonical!r}."
                        )
                    result[word] = canonical
    return result


def write_full_dictionary(
    policy: experiment.Policy,
    canonical_by_word: Mapping[str, str],
    target: Path,
) -> int:
    """Write one all-data dictionary in the original restricted class order."""
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8", newline="\n") as stream:
        for cluster in policy.clusters:
            canonical = canonical_by_word.get(cluster[0])
            if canonical is None:
                raise RuntimeError(f"No recovered canonical target for cluster member {cluster[0]!r}.")
            if any(canonical_by_word.get(word) != canonical for word in cluster):
                raise RuntimeError("Recovered cross-fit dictionaries disagree inside one reference class.")
            variants = [word for word in cluster if word != canonical]
            stream.write("\t".join([canonical, *variants]))
            stream.write("\n")
    return len(policy.clusters)


def write_rows(path: Path, rows: Sequence[Mapping[str, object]]) -> None:
    """Write deterministic publication-safe full-information summary rows."""
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = list(rows[0].keys())
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    """Compile six full-information models and export their reconstruction metrics."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path("."), help="Policy-transfer project directory.")
    parser.add_argument(
        "--gs1",
        type=Path,
        default=None,
        help="Pinned GS1 path; defaults to data/external/goldstandard1.txt.",
    )
    parser.add_argument(
        "--gs2",
        type=Path,
        default=None,
        help="Pinned GS2 path; defaults to data/external/goldstandard2.txt.",
    )
    args = parser.parse_args()

    project = args.project.resolve()
    gs1 = (args.gs1 or project / "data/external/goldstandard1.txt").resolve()
    gs2 = (args.gs2 or project / "data/external/goldstandard2.txt").resolve()
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

    policy1, policy2, common = experiment.restrict_policies(gs1, gs2)
    policies = (policy1, policy2)
    jar = locate_frozen_jar(project)
    classes = experiment.compile_java_runner(project, jar)
    private = project / "build/private/full-information"
    words_path = private / "all-words.txt"
    private.mkdir(parents=True, exist_ok=True)
    words_path.write_text("\n".join(common) + "\n", encoding="utf-8")

    rows: list[dict[str, object]] = []
    for strategy in experiment.REPRESENTATIVES:
        for policy_index, policy in enumerate(policies, start=1):
            canonical_by_word = canonical_map(project, strategy, policy_index)
            missing = set(common).difference(canonical_by_word)
            if missing:
                raise RuntimeError(
                    f"Cross-fit dictionaries do not cover {len(missing)} common forms for {strategy}/GS{policy_index}."
                )

            directory = private / strategy
            dictionary = directory / f"gs{policy_index}.tsv"
            model = directory / f"m_gs{policy_index}.radixor.gz"
            predictions_path = directory / f"predictions-m{policy_index}.tsv"
            line_count = write_full_dictionary(policy, canonical_by_word, dictionary)
            compile_log = experiment.compile_model(jar, dictionary, model)
            (directory / f"compile-gs{policy_index}.log").write_text(compile_log + "\n", encoding="utf-8")
            metadata = experiment.run_model(jar, classes, model, words_path, predictions_path)
            predictions = experiment.load_predictions(predictions_path)

            for gold_name, gold in (("GS1", policy1.label_by_word), ("GS2", policy2.label_by_word)):
                true_positive, false_positive, false_negative = experiment.pairwise_confusion(
                    common, gold, predictions
                )
                cluster_count, macro_precision, macro_recall, macro_f1 = experiment.cistem_macro(
                    common, gold, predictions
                )
                pair_denominator = 2 * true_positive + false_positive + false_negative
                pairwise_f1 = (
                    2 * true_positive / pair_denominator if pair_denominator else 1.0
                )
                rows.append(
                    {
                        "representative": strategy,
                        "model": f"M_GS{policy_index}_FULL",
                        "gold": gold_name,
                        "forms": len(common),
                        "clusters": cluster_count,
                        "macro_precision": macro_precision,
                        "macro_recall": macro_recall,
                        "macro_f1": macro_f1,
                        "tp": true_positive,
                        "fp": false_positive,
                        "fn": false_negative,
                        "pairwise_precision": (
                            true_positive / (true_positive + false_positive)
                            if true_positive + false_positive
                            else 1.0
                        ),
                        "pairwise_recall": (
                            true_positive / (true_positive + false_negative)
                            if true_positive + false_negative
                            else 1.0
                        ),
                        "pairwise_f1": pairwise_f1,
                        "training_dictionary_lines": line_count,
                        "model_bytes": model.stat().st_size,
                        "model_sha256": experiment.sha256_file(model),
                        "nodes": metadata.get("nodes", ""),
                    }
                )

    for strategy in experiment.REPRESENTATIVES:
        for policy_index in (1, 2):
            matched = next(
                row
                for row in rows
                if row["representative"] == strategy
                and row["model"] == f"M_GS{policy_index}_FULL"
                and row["gold"] == f"GS{policy_index}"
            )
            if abs(float(matched["macro_f1"]) - 1.0) > 1.0e-12 or abs(float(matched["pairwise_f1"]) - 1.0) > 1.0e-12:
                raise RuntimeError(
                    f"Full-information {strategy}/GS{policy_index} failed exact partition reconstruction."
                )

    output = project / "data/derived/full_information_summary.csv"
    write_rows(output, rows)
    print(f"wrote {output}")


if __name__ == "__main__":
    main()
