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

"""Regression tests for the closed model release-tag grammar and authority."""

from __future__ import annotations

import subprocess
from pathlib import Path


REPOSITORY = Path(__file__).resolve().parents[2]
PARSER = REPOSITORY / "tools" / "parse-model-release-tag.sh"


def read_properties(path: Path) -> dict[str, str]:
    """Read the repository's simple model topology files."""

    result: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line and not line.startswith("#"):
            key, value = line.split("=", 1)
            result[key] = value
    return result


def run_parser(tag: str, repository: Path = REPOSITORY) -> subprocess.CompletedProcess[str]:
    """Run the model tag parser without mutating repository state."""

    return subprocess.run(
        ["bash", str(PARSER), tag, str(repository)],
        capture_output=True,
        text=True,
    )


def test_accepts_every_distributable_model_tag() -> None:
    active = read_properties(REPOSITORY / "models" / "model-projects.properties")
    alternatives = read_properties(REPOSITORY / "models" / "alternative-model-projects.properties")
    model_ids = sorted(active.keys() | alternatives.keys())

    assert len(model_ids) == 154
    for model_id in model_ids:
        version = (REPOSITORY / "models" / model_id / "model-version.txt").read_text(
            encoding="utf-8"
        ).strip()
        result = run_parser(f"model/{model_id}@{version}")
        assert result.returncode == 0, (model_id, result.stderr)
        assert f"MODEL_ID={model_id}\n" in result.stdout
        assert f"MODEL_VERSION={version}\n" in result.stdout


def test_accepts_core_and_catalog_tags() -> None:
    core = run_parser("release@4.4.0")
    catalog = run_parser("models-catalog@2026.3")

    assert core.returncode == 0
    assert core.stdout == "CORE_VERSION=4.4.0\n"
    assert catalog.returncode == 0
    assert catalog.stdout == "CATALOG_VERSION=2026.3\n"


def test_rejects_malformed_traversal_unknown_and_prohibited_tags() -> None:
    invalid_tags = (
        "model/../../fa-ir-default@1.0.1",
        "model/fa-ir-default/extra@1.0.1",
        "model/fa_ir-default@1.0.1",
        "model/FA-IR-default@1.0.1",
        "model/a-default@1.0.0",
        "model/abcd-default@1.0.0",
        "model/not-real-default@1.0.0",
        "model/eus-default@1.0.0",
        "model/fa-ir-default@01.0.1",
    )

    for tag in invalid_tags:
        result = run_parser(tag)
        assert result.returncode != 0, tag


def test_rejects_version_and_descriptor_identity_mismatch(tmp_path: Path) -> None:
    models = tmp_path / "models"
    module = models / "abc-default"
    module.mkdir(parents=True)
    (models / "model-projects.properties").write_text(
        "abc-default=standalone\n", encoding="utf-8"
    )
    (models / "alternative-model-projects.properties").write_text("", encoding="utf-8")
    (models / "catalog-version.txt").write_text("2026.3\n", encoding="utf-8")
    (module / "model-version.txt").write_text("1.2.3\n", encoding="utf-8")
    (module / "build.gradle").write_text("modelId = 'wrong-default'\n", encoding="utf-8")

    version_mismatch = run_parser("model/abc-default@1.2.4", tmp_path)
    identity_mismatch = run_parser("model/abc-default@1.2.3", tmp_path)

    assert version_mismatch.returncode != 0
    assert "does not match" in version_mismatch.stderr
    assert identity_mismatch.returncode != 0
    assert "Descriptor model ID does not match" in identity_mismatch.stderr


def test_filtered_hebrew_model_declares_right_to_left_metadata() -> None:
    build_file = (REPOSITORY / "models" / "he-il-filtered" / "build.gradle").read_text(
        encoding="utf-8"
    )

    assert "language = 'HE_IL'" in build_file
    assert "rightToLeft = true" in build_file
