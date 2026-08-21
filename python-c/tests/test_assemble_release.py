"""Tests for the radixor-c release inventory."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

from assemble_c_release import (  # noqa: E402
    PLATFORMS,
    PYTHON_TAGS,
    _classify_wheel,
    _release_files,
)


@pytest.mark.parametrize(
    ("filename", "expected"),
    [
        (
            "radixor_c-1.2.3-cp310-cp310-manylinux_2_17_x86_64.whl",
            ("cp310", "linux-x86_64"),
        ),
        (
            "radixor_c-1.2.3-cp314-cp314-manylinux_2_17_aarch64.whl",
            ("cp314", "linux-aarch64"),
        ),
        (
            "radixor_c-1.2.3-cp311-cp311-musllinux_1_2_x86_64.whl",
            ("cp311", "musllinux-x86_64"),
        ),
        (
            "radixor_c-1.2.3-cp312-cp312-macosx_11_0_universal2.whl",
            ("cp312", "macos-universal2"),
        ),
        (
            "radixor_c-1.2.3-cp313-cp313-win_amd64.whl",
            ("cp313", "windows-x86_64"),
        ),
    ],
)
def test_classifies_supported_wheels(filename: str, expected: tuple[str, str]) -> None:
    assert _classify_wheel(filename, "1.2.3") == expected


@pytest.mark.parametrize(
    "filename",
    [
        "radixor_c-1.2.3-cp39-cp39-manylinux_2_17_x86_64.whl",
        "radixor_c-1.2.3-cp310-abi3-manylinux_2_17_x86_64.whl",
        "radixor_c-1.2.3-cp310-cp310-linux_x86_64.whl",
        "radixor-1.2.3-cp310-cp310-manylinux_2_17_x86_64.whl",
    ],
)
def test_rejects_unpublished_wheel_variants(filename: str) -> None:
    with pytest.raises(ValueError):
        _classify_wheel(filename, "1.2.3")


def test_requires_complete_release_inventory(tmp_path: Path) -> None:
    (tmp_path / "radixor_c-1.2.3.tar.gz").touch()
    platform_tags = {
        "linux-x86_64": "manylinux2014_x86_64.manylinux_2_17_x86_64",
        "linux-aarch64": "manylinux2014_aarch64.manylinux_2_17_aarch64",
        "musllinux-x86_64": "musllinux_1_2_x86_64",
        "musllinux-aarch64": "musllinux_1_2_aarch64",
        "macos-universal2": "macosx_10_13_universal2",
        "windows-x86_64": "win_amd64",
    }
    for python_tag in PYTHON_TAGS:
        for platform in PLATFORMS:
            (tmp_path / f"radixor_c-1.2.3-{python_tag}-{python_tag}-"
             f"{platform_tags[platform]}.whl").touch()

    files = _release_files(tmp_path, "1.2.3")

    assert len(files) == 1 + len(PYTHON_TAGS) * len(PLATFORMS)


def test_rejects_incomplete_release_inventory(tmp_path: Path) -> None:
    (tmp_path / "radixor_c-1.2.3.tar.gz").touch()
    wheel = tmp_path / "radixor_c-1.2.3-cp310-cp310-manylinux_2_17_x86_64.whl"
    wheel.touch()

    with pytest.raises(ValueError, match="Incomplete wheel coverage"):
        _release_files(tmp_path, "1.2.3")
