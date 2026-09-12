#!/usr/bin/env python3
###############################################################################
# Copyright (C) 2026, Leo Galambos
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice,
#    this list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors
#    may be used to endorse or promote products derived from this software
#    without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.
###############################################################################
"""Fetch pinned UniMorph model inputs into an offline-import cache.

This bootstrap fetcher is intentionally separate from Gradle and the offline
importer. It performs no model writes. Run it once per manifest refresh, review
the resulting inventory, and then check in the finalized source manifest.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import subprocess
from pathlib import Path


REVISION_PATTERN = re.compile(r"[0-9a-f]{40}")
INVENTORY_FIELDS = (
    "model_id",
    "repository",
    "revision",
    "data_sources",
    "excluded_source_audit",
    "license_evidence_status",
    "license_evidence_reason",
    "evidence_paths",
    "evidence_sha256",
)

AUXILIARY_DATA_PATHS = {
    "be-by-default": ("bel.args",),
    "ca-es-default": ("cat.segmentations",),
    "grc-default": ("grc.args",),
    "hy-am-default": ("hye.args",),
    "kk-kz-default": ("kaz.noun.tsv", "kaz.sm"),
    "swc-default": ("swc.sm",),
    "uz-uz-default": ("uzb_verbs",),
}

EXCLUSION_REASONS = {
    "duplicate-args": (
        "Duplicate .args projection was audited and excluded; only bel.args, grc.args, "
        "and hye.args add an approved compatible projection."
    ),
    "derivation": "Derivational data is outside the inflectional stem-form model contract.",
    "latin-segmentation": (
        "Latin segmentation data was excluded because roughly one third of new pairs have "
        "cross-source lemma conflicts."
    ),
    "khalkha-segmentation": (
        "Khalkha segmentation data was excluded after 105 conflicts and unrelated-verb "
        "anomalies were found."
    ),
    "karelian-dialect": (
        "Karelian dialect files are separate CC-BY-4.0 dialect products and are reserved "
        "for possible future models."
    ),
    "zulu-sm-conflict": (
        "zul.sm was excluded because incompatible lemma conventions create 15 "
        "cross-source conflicts and 14 newly ambiguous surfaces; its source attribution "
        "is also recorded upstream as TBA."
    ),
}
EVIDENCE_BASENAMES = {
    "readme",
    "readme.md",
    "readme.txt",
    "license",
    "license.md",
    "license.txt",
    "copying",
    "copying.md",
    "copying.txt",
}


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--topology", type=Path, required=True)
    parser.add_argument("--models-root", type=Path, required=True)
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--inventory", type=Path, required=True)
    return parser.parse_args()


def active_standalones(path: Path) -> list[str]:
    entries: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            model_id, role = stripped.split("=", 1)
            if role == "standalone":
                entries.append(model_id)
    return sorted(entries)


def gradle_value(text: str, name: str) -> str:
    match = re.search(rf"^\s*{re.escape(name)}\s*=\s*'([^']+)'\s*$", text, re.MULTILINE)
    if match is None:
        raise ValueError(f"Missing {name} in model build metadata")
    return match.group(1)


def resolve_head(repository: str) -> str:
    result = subprocess.run(
        ["git", "ls-remote", repository + ".git", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    )
    revision = result.stdout.split()[0]
    if not REVISION_PATTERN.fullmatch(revision):
        raise ValueError(f"Repository returned an invalid HEAD revision: {repository}")
    return revision


def pinned_revision(repository: str, configured: str, model_cache: Path) -> str:
    """Return a full revision, caching resolution of legacy unpinned metadata."""

    if REVISION_PATTERN.fullmatch(configured):
        return configured
    cached_path = model_cache / "resolved-revision"
    if cached_path.is_file():
        cached = cached_path.read_text(encoding="ascii").strip()
        if not REVISION_PATTERN.fullmatch(cached):
            raise ValueError(f"Invalid cached revision in {cached_path}: {cached!r}")
        return cached
    revision = resolve_head(repository)
    model_cache.mkdir(parents=True, exist_ok=True)
    temporary = cached_path.with_suffix(".tmp")
    temporary.write_text(revision + "\n", encoding="ascii")
    temporary.replace(cached_path)
    return revision


def repository_tree(repository: str, revision: str, clone: Path) -> list[str]:
    if not clone.exists():
        subprocess.run(
            ["git", "clone", "--bare", "--filter=blob:none", repository + ".git", str(clone)],
            check=True,
        )
    elif subprocess.run(
        ["git", "--git-dir", str(clone), "cat-file", "-e", f"{revision}^{{commit}}"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    ).returncode != 0:
        subprocess.run(["git", "--git-dir", str(clone), "fetch", "origin", revision], check=True)
    result = subprocess.run(
        ["git", "--git-dir", str(clone), "ls-tree", "-r", "--name-only", revision],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.splitlines()


def extract_blob(clone: Path, revision: str, source_path: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".tmp")
    with temporary.open("wb") as output:
        subprocess.run(
            ["git", "--git-dir", str(clone), "show", f"{revision}:{source_path}"],
            check=True,
            stdout=output,
        )
    if temporary.stat().st_size == 0:
        raise ValueError(f"Fetched empty source: {source_path}")
    temporary.replace(target)


def sha256(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def repository_evidence_paths(tree: list[str]) -> list[str]:
    """Return every repository-owned README or license evidence path."""

    return sorted(
        path for path in tree if Path(path).name.casefold() in EVIDENCE_BASENAMES
    )


def excluded_source_audit(
    model_id: str, repository_code: str, tree: list[str]
) -> list[dict[str, str]]:
    """Return the owner-approved deterministic audit record for excluded data files."""

    selected = set(AUXILIARY_DATA_PATHS.get(model_id, ()))
    result: list[dict[str, str]] = []
    for path in sorted(tree):
        reason_key: str | None = None
        if path.endswith(".args") and path not in selected:
            reason_key = "duplicate-args"
        elif "derivation" in path.casefold():
            reason_key = "derivation"
        elif repository_code == "lat" and path == "lat.segmentations":
            reason_key = "latin-segmentation"
        elif repository_code == "khk" and path == "khk.segmentations":
            reason_key = "khalkha-segmentation"
        elif repository_code == "krl" and path != "krl" and path.startswith("krl-"):
            reason_key = "karelian-dialect"
        elif repository_code == "zul" and path == "zul.sm":
            reason_key = "zulu-sm-conflict"
        if reason_key is not None:
            result.append({
                "path": path,
                "reason": reason_key,
                "detail": EXCLUSION_REASONS[reason_key],
            })
    return result


def write_inventory(path: Path, rows: list[dict[str, str]]) -> None:
    """Atomically checkpoint a deterministic inventory, including unresolved rows."""

    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=INVENTORY_FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    temporary.replace(path)


def main() -> None:
    arguments = parse_arguments()
    rows: list[dict[str, str]] = []
    model_metadata: list[tuple[str, str, str, Path, str]] = []
    for model_id in active_standalones(arguments.topology):
        if model_id == "ar-default":
            continue
        text = (arguments.models_root / model_id / "build.gradle").read_text(encoding="utf-8")
        repository = gradle_value(text, "sourceRepository")
        model_cache = arguments.cache / model_id
        revision_value = gradle_value(text, "sourceRevision")
        revision = pinned_revision(repository, revision_value, model_cache)
        repository_code = repository.rstrip("/").rsplit("/", 1)[-1]
        model_metadata.append((model_id, repository, revision, model_cache, repository_code))
    for model_id, repository, revision, model_cache, repository_code in model_metadata:
        clone = arguments.cache / ".git-cache" / f"{repository_code}.git"
        tree = repository_tree(repository, revision, clone)
        data_candidates = [path for path in tree if Path(path).name == repository_code]
        if len(data_candidates) != 1:
            raise ValueError(
                f"Expected one declared {repository_code} data path for {model_id}, found {data_candidates}"
            )
        selected_data_paths = (data_candidates[0], *AUXILIARY_DATA_PATHS.get(model_id, ()))
        missing_selected = sorted(set(selected_data_paths) - set(tree))
        if missing_selected:
            raise ValueError(
                f"Pinned repository is missing audited selected data for {model_id}: "
                f"{missing_selected}"
            )
        data_sources: list[dict[str, str]] = []
        for index, selected_data_path in enumerate(selected_data_paths):
            local_name = "data" if index == 0 else f"data-{index:02d}"
            data_path = model_cache / local_name
            extract_blob(clone, revision, selected_data_path, data_path)
            data_sources.append({
                "key": "primary" if index == 0 else f"auxiliary-{index:02d}",
                "path": selected_data_path,
                "local_name": local_name,
                "sha256": sha256(data_path),
            })
        evidence_paths = repository_evidence_paths(tree)
        evidence_names: list[str] = []
        for index, evidence_path in enumerate(evidence_paths):
            local_name = f"evidence-{index:02d}-{Path(evidence_path).name}"
            extract_blob(clone, revision, evidence_path, model_cache / local_name)
            evidence_names.append(local_name)
        rows.append({
            "model_id": model_id,
            "repository": repository,
            "revision": revision,
            "data_sources": json.dumps(data_sources, ensure_ascii=False, sort_keys=True, separators=(",", ":")),
            "excluded_source_audit": json.dumps(
                excluded_source_audit(model_id, repository_code, tree),
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            ),
            "license_evidence_status": "present" if evidence_paths else "unresolved",
            "license_evidence_reason": "" if evidence_paths else (
                "No repository-owned README, LICENSE, or COPYING evidence at pinned revision"
            ),
            "evidence_paths": ";".join(
                f"{source}:{local}" for source, local in zip(evidence_paths, evidence_names, strict=True)
            ),
            "evidence_sha256": ";".join(
                f"{name}:{sha256(model_cache / name)}" for name in evidence_names
            ),
        })
        write_inventory(arguments.inventory, rows)
    unresolved = sum(row["license_evidence_status"] == "unresolved" for row in rows)
    print(
        f"Fetched {len(rows)} pinned UniMorph source sets into {arguments.cache}; "
        f"license evidence unresolved for {unresolved}"
    )


if __name__ == "__main__":
    main()
