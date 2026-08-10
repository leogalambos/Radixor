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

"""Build the standard-model wheel and sdist through its declared backend."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import tempfile
from pathlib import Path

EXPECTED_MODEL_COUNT = 20


def _ignore_build_artifacts(_directory: str, names: list[str]) -> set[str]:
    """Exclude local build state from the isolated distribution source tree."""

    ignored = {
        name
        for name in names
        if name in {"build", "dist", "__pycache__"}
        or name.endswith((".egg-info", ".pyc", ".pyo"))
    }
    return ignored


def _validate_generated_project(project: Path) -> None:
    """Reject an ungenerated or incomplete standard-model project."""

    package = project / "radixor_models_standard"
    manifest_path = package / "manifest.json"
    if not manifest_path.is_file():
        raise ValueError(
            "standard-model manifest is missing; run build_standard_models.py first"
        )

    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        model_ids = {
            model["id"] for model in manifest["models"] if isinstance(model, dict)
        }
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        raise ValueError("standard-model manifest is invalid") from exc

    compiled = {path.stem for path in (package / "models").glob("*.rxc")}
    notices = {
        path.parent.name
        for path in (package / "notices").glob("*/NOTICE-model-data.txt")
    }
    if len(model_ids) != EXPECTED_MODEL_COUNT:
        raise ValueError(
            f"expected {EXPECTED_MODEL_COUNT} standard models, found {len(model_ids)}"
        )
    if compiled != model_ids:
        raise ValueError("compiled standard models do not match the manifest")
    if notices != model_ids:
        raise ValueError("standard-model notices do not match the manifest")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", required=True, type=Path)
    parser.add_argument("--outdir", required=True, type=Path)
    args = parser.parse_args()

    try:
        from setuptools.build_meta import build_sdist, build_wheel
    except ImportError as exc:
        raise SystemExit(
            "setuptools>=77 is required to build radixor-models-standard"
        ) from exc

    source_project = args.project.resolve()
    try:
        _validate_generated_project(source_project)
    except ValueError as exc:
        parser.error(str(exc))
    output = args.outdir.resolve()
    output.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="radixor-models-build-") as temporary:
        project = Path(temporary, "project")
        shutil.copytree(
            source_project,
            project,
            ignore=_ignore_build_artifacts,
        )
        os.chdir(project)
        wheel = build_wheel(str(output))
        sdist = build_sdist(str(output))

    print(f"built {wheel} and {sdist}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
