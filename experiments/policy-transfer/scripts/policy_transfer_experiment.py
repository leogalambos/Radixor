#!/usr/bin/env python3
"""Run the frozen Radixor GS1/GS2 policy-transfer experiment.

The script keeps CELEX-derived lexical forms, generated training dictionaries,
per-token predictions, and compiled fold models below ``build/private``.  Only
aggregate, non-lexical result tables are written below ``data/derived``.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import csv
from dataclasses import dataclass
import functools
import hashlib
import os
import shlex
import itertools
import json
import math
from pathlib import Path
import random
import shutil
import subprocess
import sys
import unicodedata
from typing import Callable, Iterable, Mapping, Sequence

RADIXOR_COMMIT = "0c3b13f485a9ad0b460c0931e4497ea95bed66a1"
CISTEM_COMMIT = "7c19867c2e062c8a7d44b394c19573845ac4bd89"
CISTEM_BLOBS = {
    "goldstandard1.txt": "8627bb28b67429f6488f8d017f510327b2c84d1c",
    "goldstandard2.txt": "2cb401638a67760f5fec47c8379646bf6d6d1b8e",
}
CISTEM_SIZES = {
    "goldstandard1.txt": 3_947_464,
    "goldstandard2.txt": 3_893_379,
}
FOLD_COUNT = 5
BOOTSTRAP_REPLICATES = 10_000
BOOTSTRAP_SEED = 0x52414449584F52
REDUCTION_MODE = "MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS"
CASE_MODE = "LOWERCASE_WITH_LOCALE_ROOT"


@dataclass(frozen=True)
class Policy:
    """One normalized, unambiguous policy partition over the common universe."""

    clusters: tuple[tuple[str, ...], ...]
    label_by_word: Mapping[str, int]
    original_normalized_forms: int
    ambiguous_forms: frozenset[str]


@dataclass(frozen=True)
class Component:
    """One join-partition component used as the leakage-safe experimental unit."""

    identifier: int
    words: tuple[str, ...]
    disagreement_pairs: int
    disagreement_g1_same: int
    disagreement_g2_same: int


class DisjointSet:
    """Minimal deterministic disjoint-set implementation for join components."""

    def __init__(self, words: Iterable[str]) -> None:
        self.parent = {word: word for word in words}
        self.rank = {word: 0 for word in words}

    def find(self, word: str) -> str:
        """Return the canonical representative of *word* with path compression."""
        parent = self.parent[word]
        if parent != word:
            self.parent[word] = self.find(parent)
        return self.parent[word]

    def union(self, left: str, right: str) -> None:
        """Merge the sets containing *left* and *right*."""
        root_left = self.find(left)
        root_right = self.find(right)
        if root_left == root_right:
            return
        rank_left = self.rank[root_left]
        rank_right = self.rank[root_right]
        if rank_left < rank_right:
            root_left, root_right = root_right, root_left
            rank_left, rank_right = rank_right, rank_left
        self.parent[root_right] = root_left
        if rank_left == rank_right:
            self.rank[root_left] += 1


def normalize_form(form: str) -> str:
    """Apply the frozen German type-level normalization used by this experiment."""
    return unicodedata.normalize("NFC", form).lower()


def git_blob_sha1(path: Path) -> str:
    """Return the Git blob SHA-1 of a local file."""
    payload = path.read_bytes()
    header = f"blob {len(payload)}\0".encode("ascii")
    return hashlib.sha1(header + payload).hexdigest()


def sha256_file(path: Path) -> str:
    """Return the SHA-256 digest of a local file."""
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def validate_external_file(path: Path, expected_blob: str, expected_size: int | None) -> None:
    """Validate one exact CISTEM input before any analysis is performed."""
    if not path.is_file():
        raise FileNotFoundError(
            f"Missing {path}. Run scripts/fetch_cistem_gold.py or place the pinned file there."
        )
    if expected_size is not None and path.stat().st_size != expected_size:
        raise RuntimeError(
            f"Unexpected size for {path}: expected {expected_size}, got {path.stat().st_size}"
        )
    actual_blob = git_blob_sha1(path)
    if actual_blob != expected_blob:
        raise RuntimeError(
            f"Unexpected Git blob for {path}: expected {expected_blob}, got {actual_blob}"
        )


def parse_raw_clusters(path: Path) -> tuple[list[tuple[str, ...]], int, frozenset[str]]:
    """Parse, normalize, and identify within-policy ambiguous surface forms."""
    clusters: list[tuple[str, ...]] = []
    memberships: dict[str, set[int]] = defaultdict(set)
    unique_forms: set[str] = set()
    with path.open("r", encoding="utf-8") as stream:
        for raw_line in stream:
            stripped = raw_line.strip()
            if not stripped:
                continue
            seen: set[str] = set()
            normalized: list[str] = []
            for token in stripped.split():
                word = normalize_form(token)
                if word and word not in seen:
                    seen.add(word)
                    normalized.append(word)
            if not normalized:
                continue
            cluster_index = len(clusters)
            cluster = tuple(normalized)
            clusters.append(cluster)
            for word in cluster:
                memberships[word].add(cluster_index)
                unique_forms.add(word)
    ambiguous = frozenset(word for word, values in memberships.items() if len(values) != 1)
    return clusters, len(unique_forms), ambiguous


def restrict_policies(gs1: Path, gs2: Path) -> tuple[Policy, Policy, tuple[str, ...]]:
    """Build two unambiguous policy partitions on exactly the same word universe."""
    raw1, forms1, ambiguous1 = parse_raw_clusters(gs1)
    raw2, forms2, ambiguous2 = parse_raw_clusters(gs2)

    membership1: dict[str, int] = {}
    membership2: dict[str, int] = {}
    for cluster_id, cluster in enumerate(raw1):
        for word in cluster:
            if word not in ambiguous1:
                membership1[word] = cluster_id
    for cluster_id, cluster in enumerate(raw2):
        for word in cluster:
            if word not in ambiguous2:
                membership2[word] = cluster_id

    common = tuple(sorted(set(membership1).intersection(membership2)))
    common_set = set(common)

    def build(raw: Sequence[Sequence[str]], forms: int, ambiguous: frozenset[str]) -> Policy:
        clusters: list[tuple[str, ...]] = []
        label: dict[str, int] = {}
        for cluster in raw:
            members = tuple(word for word in cluster if word in common_set)
            if not members:
                continue
            new_id = len(clusters)
            clusters.append(members)
            for word in members:
                if word in label:
                    raise AssertionError(f"restricted policy is not a partition: {word}")
                label[word] = new_id
        if set(label) != common_set:
            raise AssertionError("restricted policy does not cover the common universe")
        return Policy(tuple(clusters), label, forms, ambiguous)

    return build(raw1, forms1, ambiguous1), build(raw2, forms2, ambiguous2), common


def choose2(value: int) -> int:
    """Return the number of unordered pairs among *value* objects."""
    return value * (value - 1) // 2


def same_pair_count(rows: Sequence[Mapping[str, object]], keys: Sequence[str]) -> int:
    """Count unordered row pairs equal on all requested categorical keys."""
    frequencies: Counter[tuple[object, ...]] = Counter(
        tuple(row[key] for key in keys) for row in rows
    )
    return sum(choose2(count) for count in frequencies.values())


def policy_disagreement_counts(
    words: Sequence[str], p1: Mapping[str, int], p2: Mapping[str, int]
) -> tuple[int, int, int]:
    """Return disagreement counts split by which policy joins the pair."""
    rows = [{"g1": p1[word], "g2": p2[word]} for word in words]
    both = same_pair_count(rows, ("g1", "g2"))
    g1_same = same_pair_count(rows, ("g1",)) - both
    g2_same = same_pair_count(rows, ("g2",)) - both
    return g1_same + g2_same, g1_same, g2_same


def build_join_components(policy1: Policy, policy2: Policy, common: Sequence[str]) -> list[Component]:
    """Construct the join partition G1 v G2, the minimal leakage-safe blocking."""
    dsu = DisjointSet(common)
    for policy in (policy1, policy2):
        for cluster in policy.clusters:
            anchor = cluster[0]
            for word in cluster[1:]:
                dsu.union(anchor, word)
    grouped: dict[str, list[str]] = defaultdict(list)
    for word in common:
        grouped[dsu.find(word)].append(word)

    components_unordered: list[tuple[tuple[str, ...], int, int, int, str]] = []
    for words_list in grouped.values():
        words = tuple(sorted(words_list))
        total, g1_same, g2_same = policy_disagreement_counts(
            words, policy1.label_by_word, policy2.label_by_word
        )
        fingerprint = hashlib.sha256("\n".join(words).encode("utf-8")).hexdigest()
        components_unordered.append((words, total, g1_same, g2_same, fingerprint))
    components_unordered.sort(key=lambda item: item[4])
    return [
        Component(index, words, total, g1_same, g2_same)
        for index, (words, total, g1_same, g2_same, _fingerprint) in enumerate(components_unordered)
    ]


def assign_folds(components: Sequence[Component]) -> dict[int, int]:
    """Assign whole join components to five deterministic, disagreement-balanced folds."""
    assignments: dict[int, int] = {}
    disagreement_load = [0] * FOLD_COUNT
    form_load = [0] * FOLD_COUNT
    component_load = [0] * FOLD_COUNT

    informative = sorted(
        (component for component in components if component.disagreement_pairs > 0),
        key=lambda component: (
            -component.disagreement_pairs,
            -len(component.words),
            component.identifier,
        ),
    )
    remaining = sorted(
        (component for component in components if component.disagreement_pairs == 0),
        key=lambda component: (-len(component.words), component.identifier),
    )

    for component in informative:
        fold = min(
            range(FOLD_COUNT),
            key=lambda index: (
                disagreement_load[index],
                form_load[index],
                component_load[index],
                index,
            ),
        )
        assignments[component.identifier] = fold
        disagreement_load[fold] += component.disagreement_pairs
        form_load[fold] += len(component.words)
        component_load[fold] += 1

    for component in remaining:
        fold = min(
            range(FOLD_COUNT),
            key=lambda index: (form_load[index], component_load[index], index),
        )
        assignments[component.identifier] = fold
        form_load[fold] += len(component.words)
        component_load[fold] += 1
    return assignments


def levenshtein(left: str, right: str) -> int:
    """Compute exact Levenshtein distance with O(min(n,m)) working memory."""
    if left == right:
        return 0
    if len(left) < len(right):
        left, right = right, left
    previous = list(range(len(right) + 1))
    for row_index, left_character in enumerate(left, start=1):
        current = [row_index]
        for column_index, right_character in enumerate(right, start=1):
            current.append(
                min(
                    current[-1] + 1,
                    previous[column_index] + 1,
                    previous[column_index - 1] + (left_character != right_character),
                )
            )
        previous = current
    return previous[-1]


@functools.lru_cache(maxsize=None)
def _representative_medoid_cached(ordered: tuple[str, ...]) -> str:
    """Choose the exact Levenshtein medoid for one already ordered immutable class."""
    if len(ordered) == 1:
        return ordered[0]
    totals = [0] * len(ordered)
    for left_index in range(len(ordered)):
        for right_index in range(left_index + 1, len(ordered)):
            distance = levenshtein(ordered[left_index], ordered[right_index])
            totals[left_index] += distance
            totals[right_index] += distance
    best_index = min(
        range(len(ordered)),
        key=lambda index: (totals[index], len(ordered[index]), ordered[index]),
    )
    return ordered[best_index]


def representative_medoid(words: Sequence[str]) -> str:
    """Choose the exact Levenshtein medoid with deterministic tie breaking."""
    return _representative_medoid_cached(tuple(sorted(words)))


def representative_shortest(words: Sequence[str]) -> str:
    """Choose the shortest member with Unicode lexical tie breaking."""
    return min(words, key=lambda word: (len(word), word))


def representative_lexical(words: Sequence[str]) -> str:
    """Choose the lexicographically first normalized member."""
    return min(words)


REPRESENTATIVES: dict[str, Callable[[Sequence[str]], str]] = {
    "medoid": representative_medoid,
    "shortest": representative_shortest,
    "lexical": representative_lexical,
}


def java_command() -> list[str]:
    """Return the resource-bounded Java launcher used by the publication harness."""
    configured = os.environ.get(
        "RADIXOR_JAVA_OPTIONS",
        "-Xms128m -Xmx1536m -XX:+ExitOnOutOfMemoryError -XX:ActiveProcessorCount=2",
    )
    return ["java", *shlex.split(configured)]


def compile_java_runner(project: Path, jar: Path) -> Path:
    """Compile the JDK 21 helper used to execute persisted policy models."""
    source = project / "scripts/java/org/egothor/stemmer/experiment/PolicyModelRunner.java"
    classes = project / "build/private/java-classes"
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["javac", "--release", "21", "-cp", str(jar), "-d", str(classes), str(source)],
        check=True,
    )
    return classes


def compile_model(jar: Path, dictionary: Path, model: Path) -> str:
    """Compile one deterministic policy dictionary with the frozen Radixor CLI."""
    model.parent.mkdir(parents=True, exist_ok=True)
    command = [
        *java_command(),
        "-cp",
        str(jar),
        "org.egothor.stemmer.Compile",
        "--input",
        str(dictionary),
        "--output",
        str(model),
        "--reduction-mode",
        REDUCTION_MODE,
        "--traversal-direction",
        "BACKWARD",
        "--case-processing-mode",
        CASE_MODE,
        "--store-original",
        "--overwrite",
    ]
    completed = subprocess.run(command, check=True, text=True, capture_output=True)
    return (completed.stdout + completed.stderr).strip()


def run_model(jar: Path, classes: Path, model: Path, words: Path, output: Path) -> dict[str, str]:
    """Execute a compiled model on normalized words and return parsed metadata."""
    classpath = f"{classes}:{jar}"
    command = [
        *java_command(),
        "-cp",
        classpath,
        "org.egothor.stemmer.experiment.PolicyModelRunner",
        str(model),
        str(words),
        str(output),
    ]
    completed = subprocess.run(command, check=True, text=True, capture_output=True)
    metadata: dict[str, str] = {}
    for line in completed.stdout.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            metadata[key.strip()] = value.strip()
    return metadata


def write_dictionary(
    policy: Policy,
    component_by_word: Mapping[str, int],
    assignments: Mapping[int, int],
    test_fold: int,
    representative: Callable[[Sequence[str]], str],
    target: Path,
) -> int:
    """Write a training-only Radixor dictionary for one policy and fold."""
    target.parent.mkdir(parents=True, exist_ok=True)
    lines = 0
    with target.open("w", encoding="utf-8", newline="\n") as stream:
        for cluster in policy.clusters:
            component_id = component_by_word[cluster[0]]
            if assignments[component_id] == test_fold:
                continue
            if any(component_by_word[word] != component_id for word in cluster):
                raise AssertionError("a policy cluster crosses a join component")
            canonical = representative(cluster)
            variants = [word for word in cluster if word != canonical]
            stream.write("\t".join([canonical, *variants]))
            stream.write("\n")
            lines += 1
    return lines


def load_predictions(path: Path) -> dict[str, str]:
    """Read private word-to-stem predictions emitted by the Java runner."""
    output: dict[str, str] = {}
    with path.open("r", encoding="utf-8") as stream:
        for line in stream:
            word, stem = line.rstrip("\n").split("\t", 1)
            output[word] = stem
    return output


def component_switch_counts(
    words: Sequence[str],
    policy1: Policy,
    policy2: Policy,
    m1: Mapping[str, str],
    m2: Mapping[str, str],
) -> dict[str, int | float]:
    """Compute non-IID pair counts through exact grouped sufficient statistics."""
    rows = [
        {"g1": policy1.label_by_word[word], "g2": policy2.label_by_word[word], "m1": m1[word], "m2": m2[word]}
        for word in words
    ]
    same = lambda *keys: same_pair_count(rows, keys)
    both_gold = same("g1", "g2")
    d_a = same("g1") - both_gold
    d_b = same("g2") - both_gold

    m1_a = same("g1", "m1") - same("g1", "g2", "m1")
    m2_a = same("g1", "m2") - same("g1", "g2", "m2")
    both_model_a = same("g1", "m1", "m2") - same("g1", "g2", "m1", "m2")
    aligned_a = m1_a - both_model_a
    reverse_a = m2_a - both_model_a

    m1_b = same("g2", "m1") - same("g1", "g2", "m1")
    m2_b = same("g2", "m2") - same("g1", "g2", "m2")
    both_model_b = same("g2", "m1", "m2") - same("g1", "g2", "m1", "m2")
    aligned_b = m2_b - both_model_b
    reverse_b = m1_b - both_model_b

    aligned = aligned_a + aligned_b
    reverse = reverse_a + reverse_b
    disagreement = d_a + d_b
    if min(d_a, d_b, aligned, reverse) < 0:
        raise AssertionError("negative sufficient-statistic count")
    if aligned + reverse > disagreement:
        raise AssertionError("switch count exceeds disagreement evidence")
    delta = (aligned - reverse) / disagreement if disagreement else math.nan
    switch = (aligned + reverse) / disagreement if disagreement else math.nan
    correct = aligned / (aligned + reverse) if aligned + reverse else math.nan

    # Exact algebraic audit: the mirrored directional contrasts must be equal.
    g1_m1_agree = disagreement - (
        (d_a - m1_a) + m1_b
    )
    g1_m2_agree = disagreement - (
        (d_a - m2_a) + m2_b
    )
    directional = (g1_m1_agree - g1_m2_agree) / disagreement if disagreement else math.nan
    if disagreement and not math.isclose(delta, directional, rel_tol=0.0, abs_tol=1e-15):
        raise AssertionError("net-switch and directional policy contrasts disagree")
    if disagreement and aligned + reverse:
        identity = switch * (2.0 * correct - 1.0)
        if not math.isclose(delta, identity, rel_tol=0.0, abs_tol=1e-15):
            raise AssertionError("delta = switch*(2*correct-1) identity failed")

    return {
        "disagreement_g1_same": d_a,
        "disagreement_g2_same": d_b,
        "disagreement_pairs": disagreement,
        "aligned_switches": aligned,
        "reverse_switches": reverse,
        "delta": delta,
        "switch_coverage": switch,
        "correct_switch_share": correct,
        "directional_identity": directional,
    }


def pairwise_confusion(
    words: Sequence[str], gold: Mapping[str, int], predicted: Mapping[str, str]
) -> tuple[int, int, int]:
    """Return TP, FP, and FN for one held-out fold relation."""
    rows = [{"gold": gold[word], "model": predicted[word]} for word in words]
    tp = same_pair_count(rows, ("gold", "model"))
    positive = same_pair_count(rows, ("gold",))
    predicted_positive = same_pair_count(rows, ("model",))
    return tp, predicted_positive - tp, positive - tp


def cistem_macro(
    words: Sequence[str], gold: Mapping[str, int], predicted: Mapping[str, str]
) -> tuple[int, float, float, float]:
    """Compute original-CISTEM-style per-cluster macro P/R/F1 on a held-out fold."""
    cluster_words: dict[int, list[str]] = defaultdict(list)
    global_predictions = Counter(predicted[word] for word in words)
    for word in words:
        cluster_words[gold[word]].append(word)
    precision_values: list[float] = []
    recall_values: list[float] = []
    f1_values: list[float] = []
    for members in cluster_words.values():
        local = Counter(predicted[word] for word in members)
        main, count = min(local.items(), key=lambda item: (-item[1], item[0]))
        tp = count
        fp = global_predictions[main] - count
        fn = len(members) - count
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        f1 = 2.0 * precision * recall / (precision + recall) if precision + recall else 0.0
        precision_values.append(precision)
        recall_values.append(recall)
        f1_values.append(f1)
    count_clusters = len(cluster_words)
    if not count_clusters:
        return 0, math.nan, math.nan, math.nan
    return (
        count_clusters,
        sum(precision_values) / count_clusters,
        sum(recall_values) / count_clusters,
        sum(f1_values) / count_clusters,
    )


def percentile(values: Sequence[float], probability: float) -> float:
    """Return a linearly interpolated empirical percentile."""
    if not values:
        return math.nan
    ordered = sorted(values)
    position = (len(ordered) - 1) * probability
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    fraction = position - lower
    return ordered[lower] * (1.0 - fraction) + ordered[upper] * fraction


def bootstrap_stability(values: Sequence[float]) -> tuple[float, float]:
    """Return the fixed-seed component-resampling 95% stability interval."""
    if not values:
        return math.nan, math.nan
    random_generator = random.Random(BOOTSTRAP_SEED)
    size = len(values)
    means: list[float] = []
    for _ in range(BOOTSTRAP_REPLICATES):
        total = 0.0
        for _index in range(size):
            total += values[random_generator.randrange(size)]
        means.append(total / size)
    return percentile(means, 0.025), percentile(means, 0.975)


def write_csv(path: Path, rows: Sequence[Mapping[str, object]], fields: Sequence[str]) -> None:
    """Write a deterministic UTF-8 CSV table."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fields})


def run_experiment(project: Path, gs1: Path, gs2: Path, validate_pins: bool) -> None:
    """Execute all policy encodings, folds, measurements, and public aggregation."""
    jar = project / "frozen/Radixor-4.2.0-8-g0c3b13f.jar"
    if not jar.is_file():
        raise FileNotFoundError(jar)
    if validate_pins:
        validate_external_file(gs1, CISTEM_BLOBS["goldstandard1.txt"], CISTEM_SIZES["goldstandard1.txt"])
        validate_external_file(gs2, CISTEM_BLOBS["goldstandard2.txt"], CISTEM_SIZES["goldstandard2.txt"])

    policy1, policy2, common = restrict_policies(gs1, gs2)
    components = build_join_components(policy1, policy2, common)
    assignments = assign_folds(components)
    component_by_word = {
        word: component.identifier for component in components for word in component.words
    }
    classes = compile_java_runner(project, jar)
    private = project / "build/private"
    derived = project / "data/derived"
    private.mkdir(parents=True, exist_ok=True)
    derived.mkdir(parents=True, exist_ok=True)

    disagreement_components = [component for component in components if component.disagreement_pairs > 0]
    total_disagreement = sum(component.disagreement_pairs for component in disagreement_components)
    max_component = max((len(component.words) for component in components), default=0)
    max_component_disagreement = max((component.disagreement_pairs for component in components), default=0)
    sorted_sizes = sorted(len(component.words) for component in components)
    quantile = lambda p: percentile([float(value) for value in sorted_sizes], p)

    preflight = {
        "radixor_commit": RADIXOR_COMMIT,
        "cistem_commit": CISTEM_COMMIT,
        "gs1_git_blob": git_blob_sha1(gs1),
        "gs2_git_blob": git_blob_sha1(gs2),
        "gs1_bytes": gs1.stat().st_size,
        "gs2_bytes": gs2.stat().st_size,
        "gs1_normalized_forms": policy1.original_normalized_forms,
        "gs2_normalized_forms": policy2.original_normalized_forms,
        "gs1_ambiguous_forms": len(policy1.ambiguous_forms),
        "gs2_ambiguous_forms": len(policy2.ambiguous_forms),
        "common_unambiguous_forms": len(common),
        "gs1_restricted_clusters": len(policy1.clusters),
        "gs2_restricted_clusters": len(policy2.clusters),
        "join_components": len(components),
        "disagreement_components": len(disagreement_components),
        "disagreement_pairs": total_disagreement,
        "disagreement_g1_same": sum(component.disagreement_g1_same for component in components),
        "disagreement_g2_same": sum(component.disagreement_g2_same for component in components),
        "largest_component_forms": max_component,
        "largest_component_form_share": max_component / len(common) if common else math.nan,
        "largest_component_disagreement_pairs": max_component_disagreement,
        "largest_component_disagreement_share": max_component_disagreement / total_disagreement if total_disagreement else math.nan,
        "component_size_q50": quantile(0.50),
        "component_size_q90": quantile(0.90),
        "component_size_q95": quantile(0.95),
        "component_size_q99": quantile(0.99),
    }
    (derived / "preflight.json").write_text(json.dumps(preflight, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    fold_rows: list[dict[str, object]] = []
    for fold in range(FOLD_COUNT):
        selected = [component for component in components if assignments[component.identifier] == fold]
        fold_rows.append({
            "fold": fold,
            "components": len(selected),
            "disagreement_components": sum(component.disagreement_pairs > 0 for component in selected),
            "forms": sum(len(component.words) for component in selected),
            "disagreement_pairs": sum(component.disagreement_pairs for component in selected),
        })
    write_csv(derived / "fold_preflight.csv", fold_rows, tuple(fold_rows[0].keys()))

    model_rows: list[dict[str, object]] = []
    component_rows: list[dict[str, object]] = []
    pair_rows: list[dict[str, object]] = []
    cistem_rows: list[dict[str, object]] = []

    for strategy_name, representative in REPRESENTATIVES.items():
        for fold in range(FOLD_COUNT):
            fold_dir = private / strategy_name / f"fold-{fold}"
            fold_dir.mkdir(parents=True, exist_ok=True)
            dictionaries = [fold_dir / "gs1.tsv", fold_dir / "gs2.tsv"]
            models = [fold_dir / "m_gs1.radixor.gz", fold_dir / "m_gs2.radixor.gz"]
            policies = [policy1, policy2]
            metadata_by_policy: list[dict[str, str]] = []
            for policy_index, (policy, dictionary, model) in enumerate(zip(policies, dictionaries, models), start=1):
                dictionary_lines = write_dictionary(
                    policy, component_by_word, assignments, fold, representative, dictionary
                )
                compile_log = compile_model(jar, dictionary, model)
                (fold_dir / f"compile-gs{policy_index}.log").write_text(compile_log + "\n", encoding="utf-8")
                metadata_by_policy.append({"dictionary_lines": str(dictionary_lines)})

            heldout_components = [
                component for component in components if assignments[component.identifier] == fold
            ]
            heldout_words = tuple(sorted(word for component in heldout_components for word in component.words))
            words_path = fold_dir / "heldout-words.txt"
            words_path.write_text("\n".join(heldout_words) + "\n", encoding="utf-8")
            predictions: list[dict[str, str]] = []
            for policy_index, model in enumerate(models, start=1):
                prediction_path = fold_dir / f"predictions-m{policy_index}.tsv"
                runtime_metadata = run_model(jar, classes, model, words_path, prediction_path)
                predictions.append(load_predictions(prediction_path))
                metadata_by_policy[policy_index - 1].update(runtime_metadata)
                model_rows.append({
                    "representative": strategy_name,
                    "fold": fold,
                    "model_policy": f"GS{policy_index}",
                    "training_dictionary_lines": int(metadata_by_policy[policy_index - 1]["dictionary_lines"]),
                    "model_bytes": model.stat().st_size,
                    "model_sha256": sha256_file(model),
                    "fingerprint": runtime_metadata.get("fingerprint", ""),
                    "nodes": runtime_metadata.get("nodes", ""),
                    "format_version": runtime_metadata.get("formatVersion", ""),
                    "traversal_direction": runtime_metadata.get("traversalDirection", ""),
                    "case_processing_mode": runtime_metadata.get("caseProcessingMode", ""),
                    "diacritic_processing_mode": runtime_metadata.get("diacriticProcessingMode", ""),
                    "reduction_mode": runtime_metadata.get("reductionMode", ""),
                })

            m1, m2 = predictions
            for component in heldout_components:
                if component.disagreement_pairs == 0:
                    continue
                statistics = component_switch_counts(component.words, policy1, policy2, m1, m2)
                component_rows.append({
                    "representative": strategy_name,
                    "fold": fold,
                    "component_id": component.identifier,
                    "forms": len(component.words),
                    **statistics,
                })

            for model_name, predicted in (("M_GS1", m1), ("M_GS2", m2)):
                for gold_name, gold in (("GS1", policy1.label_by_word), ("GS2", policy2.label_by_word)):
                    tp, fp, fn = pairwise_confusion(heldout_words, gold, predicted)
                    pair_rows.append({
                        "representative": strategy_name,
                        "fold": fold,
                        "model": model_name,
                        "gold": gold_name,
                        "tp": tp,
                        "fp": fp,
                        "fn": fn,
                    })
                    cluster_count, precision, recall, f1 = cistem_macro(heldout_words, gold, predicted)
                    cistem_rows.append({
                        "representative": strategy_name,
                        "fold": fold,
                        "model": model_name,
                        "gold": gold_name,
                        "clusters": cluster_count,
                        "macro_precision": precision,
                        "macro_recall": recall,
                        "macro_f1": f1,
                    })

    write_csv(derived / "model_artifacts.csv", model_rows, tuple(model_rows[0].keys()))
    write_csv(derived / "component_switch_counts.csv", component_rows, tuple(component_rows[0].keys()))
    write_csv(derived / "fold_pairwise_counts.csv", pair_rows, tuple(pair_rows[0].keys()))
    write_csv(derived / "fold_cistem_macro.csv", cistem_rows, tuple(cistem_rows[0].keys()))

    summary_rows: list[dict[str, object]] = []
    for strategy in REPRESENTATIVES:
        selected = [row for row in component_rows if row["representative"] == strategy]
        deltas = [float(row["delta"]) for row in selected]
        macro = sum(deltas) / len(deltas) if deltas else math.nan
        aligned = sum(int(row["aligned_switches"]) for row in selected)
        reverse = sum(int(row["reverse_switches"]) for row in selected)
        disagreement = sum(int(row["disagreement_pairs"]) for row in selected)
        switched = aligned + reverse
        low, high = bootstrap_stability(deltas)
        if len(deltas) > 1:
            leave_one_out = [(sum(deltas) - value) / (len(deltas) - 1) for value in deltas]
            loo_min, loo_max = min(leave_one_out), max(leave_one_out)
        else:
            loo_min = loo_max = math.nan
        summary_rows.append({
            "representative": strategy,
            "disagreement_components": len(deltas),
            "delta_macro": macro,
            "delta_micro": (aligned - reverse) / disagreement if disagreement else math.nan,
            "switch_coverage_micro": switched / disagreement if disagreement else math.nan,
            "correct_switch_share_micro": aligned / switched if switched else math.nan,
            "aligned_switches": aligned,
            "reverse_switches": reverse,
            "disagreement_pairs": disagreement,
            "bootstrap_stability_low": low,
            "bootstrap_stability_high": high,
            "leave_one_component_out_min": loo_min,
            "leave_one_component_out_max": loo_max,
        })
    write_csv(derived / "policy_transfer_summary.csv", summary_rows, tuple(summary_rows[0].keys()))

    fold_policy_rows: list[dict[str, object]] = []
    for strategy in REPRESENTATIVES:
        for fold in range(FOLD_COUNT):
            selected = [
                row for row in component_rows
                if row["representative"] == strategy and int(row["fold"]) == fold
            ]
            deltas = [float(row["delta"]) for row in selected]
            aligned = sum(int(row["aligned_switches"]) for row in selected)
            reverse = sum(int(row["reverse_switches"]) for row in selected)
            disagreement = sum(int(row["disagreement_pairs"]) for row in selected)
            fold_policy_rows.append({
                "representative": strategy,
                "fold": fold,
                "disagreement_components": len(selected),
                "delta_macro": sum(deltas) / len(deltas) if deltas else math.nan,
                "delta_micro": (aligned - reverse) / disagreement if disagreement else math.nan,
                "aligned_switches": aligned,
                "reverse_switches": reverse,
                "disagreement_pairs": disagreement,
            })
    write_csv(derived / "fold_policy_transfer.csv", fold_policy_rows, tuple(fold_policy_rows[0].keys()))

    pair_summary: list[dict[str, object]] = []
    for strategy in REPRESENTATIVES:
        for model in ("M_GS1", "M_GS2"):
            for gold in ("GS1", "GS2"):
                selected = [
                    row for row in pair_rows
                    if row["representative"] == strategy and row["model"] == model and row["gold"] == gold
                ]
                tp = sum(int(row["tp"]) for row in selected)
                fp = sum(int(row["fp"]) for row in selected)
                fn = sum(int(row["fn"]) for row in selected)
                precision = tp / (tp + fp) if tp + fp else math.nan
                recall = tp / (tp + fn) if tp + fn else math.nan
                f1 = 2.0 * precision * recall / (precision + recall) if precision + recall else math.nan
                pair_summary.append({
                    "representative": strategy,
                    "model": model,
                    "gold": gold,
                    "tp": tp,
                    "fp": fp,
                    "fn": fn,
                    "precision": precision,
                    "recall": recall,
                    "f1": f1,
                })
    write_csv(derived / "pairwise_summary.csv", pair_summary, tuple(pair_summary[0].keys()))

    # Cluster-macro scores are pooled by cluster count, preserving the original
    # per-cluster equal-weighting across held-out folds.
    cistem_summary: list[dict[str, object]] = []
    for strategy in REPRESENTATIVES:
        for model in ("M_GS1", "M_GS2"):
            for gold in ("GS1", "GS2"):
                selected = [
                    row for row in cistem_rows
                    if row["representative"] == strategy and row["model"] == model and row["gold"] == gold
                ]
                cluster_count = sum(int(row["clusters"]) for row in selected)
                def weighted(field: str) -> float:
                    if not cluster_count:
                        return math.nan
                    return sum(int(row["clusters"]) * float(row[field]) for row in selected) / cluster_count
                cistem_summary.append({
                    "representative": strategy,
                    "model": model,
                    "gold": gold,
                    "clusters": cluster_count,
                    "macro_precision": weighted("macro_precision"),
                    "macro_recall": weighted("macro_recall"),
                    "macro_f1": weighted("macro_f1"),
                })
    write_csv(derived / "cistem_macro_summary.csv", cistem_summary, tuple(cistem_summary[0].keys()))

    print(json.dumps(preflight, indent=2, sort_keys=True))
    for row in summary_rows:
        print(row)


def main() -> None:
    """Parse command-line arguments and execute the frozen experiment."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", type=Path, default=Path("."))
    parser.add_argument("--gs1", type=Path, default=Path("data/external/goldstandard1.txt"))
    parser.add_argument("--gs2", type=Path, default=Path("data/external/goldstandard2.txt"))
    parser.add_argument(
        "--skip-pin-validation",
        action="store_true",
        help="Only for synthetic smoke tests; never use for publication results.",
    )
    arguments = parser.parse_args()
    project = arguments.project.resolve()
    gs1 = arguments.gs1 if arguments.gs1.is_absolute() else (project / arguments.gs1)
    gs2 = arguments.gs2 if arguments.gs2.is_absolute() else (project / arguments.gs2)
    run_experiment(project, gs1, gs2, not arguments.skip_pin_validation)


if __name__ == "__main__":
    main()
