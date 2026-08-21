"""Regression tests for Python release tags and the static package index."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[2]
TAG_PARSER = REPOSITORY / "tools" / "parse-python-release-tag.sh"
INDEX_UPDATER = REPOSITORY / "python" / "scripts" / "update_simple_index.py"


def test_python_c_release_tag() -> None:
    result = subprocess.run(
        ["bash", str(TAG_PARSER), "python-c@1.2.3"],
        check=True,
        capture_output=True,
        text=True,
    )

    assert result.stdout.splitlines() == [
        "PYTHON_DISTRIBUTION=radixor-c",
        "PYTHON_VERSION=1.2.3",
    ]


def test_python_c_static_index_entry(tmp_path: Path) -> None:
    root = tmp_path / "python" / "simple"
    artifacts = tmp_path / "artifacts"
    artifacts.mkdir()
    wheel = artifacts / "radixor_c-1.2.3-cp310-cp310-manylinux_2_17_x86_64.whl"
    wheel.write_bytes(b"test wheel")

    subprocess.run(
        [
            sys.executable,
            str(INDEX_UPDATER),
            "--root",
            str(root),
            "--repository",
            "leogalambos/Radixor",
            "--package",
            "radixor-c",
            "--version",
            "1.2.3",
            "--tag",
            "python-c@1.2.3",
            "--artifacts",
            str(artifacts),
        ],
        check=True,
    )

    root_page = (root / "index.html").read_text(encoding="utf-8")
    project_page = (root / "radixor-c" / "index.html").read_text(encoding="utf-8")
    assert '<a href="radixor-c/">radixor-c</a>' in root_page
    assert "releases/download/python-c@1.2.3/" in project_page
    assert 'data-requires-python="&gt;=3.10"' in project_page
