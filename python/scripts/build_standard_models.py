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

"""Regenerate the pure-Python standard model package deterministically.

The canonical build topology selects ``default`` models. Source dictionaries
are compiler inputs only and are never copied into either Python distribution.
Run this script with a built Radixor extension importable by the selected
Python interpreter.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
import tempfile
from pathlib import Path
from typing import Any

REPOSITORY = Path(__file__).resolve().parents[2]
PYTHON_ROOT = REPOSITORY / "python"
BUILD_ROOT = REPOSITORY / "build"
SOURCE_PROJECT = PYTHON_ROOT / "models-standard"
TOPOLOGY = REPOSITORY / "models" / "model-projects.properties"
CATALOG_VERSION = REPOSITORY / "models" / "catalog-version.txt"
EXPECTED_FORMAT = {"compression": "gzip", "magic": "EGTR", "version": 7}
VERSION = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\Z")
CATALOG_VERSION_PATTERN = re.compile(r"[1-9][0-9]{3}\.[1-9][0-9]*\Z")
METADATA_FIELDS = (
    "sourceName",
    "sourceVersion",
    "sourceRevision",
    "sourceProject",
    "sourceRepository",
    "sourceDataset",
    "sourceRevisionStatus",
    "sourceLicense",
    "sourceLicenseUri",
    "sourceAttribution",
    "sourceVerificationDate",
    "transformationsSummary",
)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _default_models() -> list[str]:
    entries: dict[str, str] = {}
    for raw_line in TOPOLOGY.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        model_id, separator, membership = line.partition("=")
        if not separator or not model_id or membership not in {"default", "optional"}:
            raise ValueError(f"Invalid model topology line: {raw_line!r}")
        entries[model_id] = membership
    model_ids = sorted(
        model_id for model_id, membership in entries.items() if membership == "default"
    )
    if len(model_ids) != 20 or "pl-pl-polimorf" in model_ids:
        raise ValueError(
            "Standard Python catalog must contain 20 defaults and exclude pl-pl-polimorf"
        )
    return model_ids


def _gradle_metadata(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    result: dict[str, str] = {}
    for field in METADATA_FIELDS:
        match = re.search(rf"^\s*{field}\s*=\s*'([^']*)'\s*$", text, re.MULTILINE)
        if match is None:
            raise ValueError(f"Missing {field} in {path}")
        result[field] = match.group(1)
    return result


def _replace_once(path: Path, pattern: str, replacement: str) -> None:
    text = path.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise ValueError(f"Expected exactly one version field in {path}")
    path.write_text(updated, encoding="utf-8", newline="\n")


def _catalog_version() -> str:
    """Return the validated catalog identity tracked by the repository."""

    catalog_version = CATALOG_VERSION.read_text(encoding="utf-8").strip()
    if CATALOG_VERSION_PATTERN.fullmatch(catalog_version) is None:
        raise ValueError(
            f"Invalid catalog version in {CATALOG_VERSION}: {catalog_version!r}"
        )
    return catalog_version


def _stage_project(output: Path, distribution_version: str) -> Path:
    project = output.resolve()
    try:
        project.relative_to(BUILD_ROOT.resolve())
    except ValueError as exc:
        raise ValueError(f"Generated model project must be below {BUILD_ROOT}") from exc
    if project == BUILD_ROOT.resolve():
        raise ValueError("Generated model project cannot be the build root")
    if project.exists():
        shutil.rmtree(project)
    project.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(
        SOURCE_PROJECT,
        project,
        ignore=shutil.ignore_patterns(
            "build",
            "dist",
            "*.egg-info",
            "__pycache__",
            "*.pyc",
            "manifest.json",
            "*.rxc",
            "NOTICE-model-data.txt",
        ),
    )
    _replace_once(
        project / "pyproject.toml",
        r'^version = "0\.0\.0"$',
        f'version = "{distribution_version}"',
    )
    return project


def _model_manifest(
    model_id: str,
    compile_model: Any,
    package_root: Path,
    reproducibility_directory: Path,
) -> dict[str, Any]:
    project = REPOSITORY / "models" / model_id
    source = project / "src" / "modelInput" / "stemmer.gz"
    notice = project / "src" / "modelInput" / "NOTICE-model-data.txt"
    version = (project / "model-version.txt").read_text(encoding="utf-8").strip()
    if not source.is_file() or not notice.is_file() or not version:
        raise FileNotFoundError(f"Incomplete canonical model project: {project}")

    destination = package_root / "models" / f"{model_id}.rxc"
    destination.parent.mkdir(parents=True, exist_ok=True)
    compile_model(str(source), str(destination), language=model_id)
    reproduction = reproducibility_directory / destination.name
    compile_model(str(source), str(reproduction), language=model_id)
    if destination.read_bytes() != reproduction.read_bytes():
        raise ValueError(f"Non-deterministic compiled model output for {model_id}")

    notice_destination = package_root / "notices" / model_id / notice.name
    notice_destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(notice, notice_destination)

    metadata = _gradle_metadata(project / "build.gradle")
    if metadata["sourceLicense"] != "CC-BY-SA-3.0":
        raise ValueError(f"Standard model {model_id} must be CC-BY-SA-3.0")
    return {
        "file": f"models/{model_id}.rxc",
        "id": model_id,
        "notice": f"notices/{model_id}/{notice.name}",
        "provenance": {
            "attribution": metadata["sourceAttribution"],
            "dataset": metadata["sourceDataset"],
            "license": metadata["sourceLicense"],
            "license_uri": metadata["sourceLicenseUri"],
            "repository": metadata["sourceRepository"],
            "revision": metadata["sourceRevision"],
            "revision_status": metadata["sourceRevisionStatus"],
            "source_name": metadata["sourceName"],
            "source_project": metadata["sourceProject"],
            "source_version": metadata["sourceVersion"],
            "transformations": metadata["transformationsSummary"],
            "verification_date": metadata["sourceVerificationDate"],
        },
        "sha256": _sha256(destination),
        "source": {
            "path": f"models/{model_id}/src/modelInput/stemmer.gz",
            "sha256": _sha256(source),
        },
        "version": version,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", required=True, type=Path)
    parser.add_argument("--distribution-version", required=True)
    args = parser.parse_args()
    if VERSION.fullmatch(args.distribution_version) is None:
        raise SystemExit(
            f"Invalid standard-model distribution version: {args.distribution_version!r}"
        )

    try:
        from radixor import compile as compile_model
    except ImportError as exc:
        print(
            "error: build the Radixor extension first (for example, "
            "maturin develop --release) and rerun this script",
            file=sys.stderr,
        )
        print(f"detail: {exc}", file=sys.stderr)
        return 2

    catalog_version = _catalog_version()
    project = _stage_project(args.project, args.distribution_version)
    package_root = project / "radixor_models_standard"
    model_ids = _default_models()
    expected_files = {f"{model_id}.rxc" for model_id in model_ids}
    models_directory = package_root / "models"
    models_directory.mkdir(parents=True, exist_ok=True)
    for stale in models_directory.glob("*.rxc"):
        if stale.name not in expected_files:
            stale.unlink()
    notices_directory = package_root / "notices"
    notices_directory.mkdir(parents=True, exist_ok=True)
    for stale in notices_directory.iterdir():
        if stale.is_dir() and stale.name not in model_ids:
            shutil.rmtree(stale)

    reproducibility_root = BUILD_ROOT / "python" / "tmp"
    reproducibility_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(
        prefix="radixor-model-reproducibility-", dir=reproducibility_root
    ) as temporary:
        models = [
            _model_manifest(
                model_id,
                compile_model,
                package_root,
                Path(temporary),
            )
            for model_id in model_ids
        ]
    manifest = {
        "catalog_version": catalog_version,
        "distribution_version": args.distribution_version,
        "format": EXPECTED_FORMAT,
        "models": models,
        "schema_version": 1,
        "topology": "models/model-projects.properties",
    }
    manifest_path = package_root / "manifest.json"
    with manifest_path.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write(
            json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
        )
    print(f"generated {len(models)} standard compiled models in {package_root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
