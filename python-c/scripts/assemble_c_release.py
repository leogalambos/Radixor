#!/usr/bin/env python3
"""Validate and assemble the files published by the radixor-c workflow."""

from __future__ import annotations

import argparse
import hashlib
import re
import shutil
from pathlib import Path

VERSION = re.compile(
    r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\Z"
)
PYTHON_TAGS = ("cp310", "cp311", "cp312", "cp313", "cp314")
PLATFORMS = {
    "linux-x86_64": re.compile(r"manylinux[^-]*_x86_64\Z"),
    "linux-aarch64": re.compile(r"manylinux[^-]*_aarch64\Z"),
    "musllinux-x86_64": re.compile(r"musllinux[^-]*_x86_64\Z"),
    "musllinux-aarch64": re.compile(r"musllinux[^-]*_aarch64\Z"),
    "macos-universal2": re.compile(r"macosx[^-]*_universal2\Z"),
    "windows-x86_64": re.compile(r"win_amd64\Z"),
}


def _classify_wheel(name: str, version: str) -> tuple[str, str]:
    prefix = f"radixor_c-{version}-"
    if not name.startswith(prefix) or not name.endswith(".whl"):
        raise ValueError(f"Unexpected radixor-c wheel filename: {name}")
    remainder = name[len(prefix) : -4]
    python_tag, separator, remainder = remainder.partition("-")
    if not separator or python_tag not in PYTHON_TAGS:
        raise ValueError(f"Unexpected radixor-c Python tag: {name}")
    abi_tag, separator, platform_tag = remainder.partition("-")
    if not separator or abi_tag != python_tag:
        raise ValueError(f"Unexpected radixor-c ABI tag: {name}")
    matches = [
        platform
        for platform, pattern in PLATFORMS.items()
        if pattern.fullmatch(platform_tag)
    ]
    if len(matches) != 1:
        raise ValueError(f"Unexpected radixor-c platform tag: {name}")
    return python_tag, matches[0]


def _release_files(root: Path, version: str) -> list[Path]:
    files = sorted(
        path
        for path in root.rglob("*")
        if path.is_file()
        and (path.name.endswith(".whl") or path.name.endswith(".tar.gz"))
    )
    names = [path.name for path in files]
    if len(names) != len(set(names)):
        raise ValueError(f"Duplicate release filenames: {names}")
    sdists = [name for name in names if name.endswith(".tar.gz")]
    expected_sdist = f"radixor_c-{version}.tar.gz"
    if sdists != [expected_sdist]:
        raise ValueError(f"Expected only {expected_sdist}, found {sdists}")
    coverage: set[tuple[str, str]] = set()
    for name in names:
        if name.endswith(".whl"):
            identity = _classify_wheel(name, version)
            if identity in coverage:
                raise ValueError(f"Duplicate wheel coverage for {identity}: {name}")
            coverage.add(identity)
    expected = {(python, platform) for python in PYTHON_TAGS for platform in PLATFORMS}
    if coverage != expected:
        missing = sorted(expected - coverage)
        unexpected = sorted(coverage - expected)
        raise ValueError(f"Incomplete wheel coverage; missing={missing}, unexpected={unexpected}")
    return files


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    parser.add_argument("artifacts", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if VERSION.fullmatch(args.version) is None:
        raise SystemExit(f"Invalid stable release version: {args.version!r}")
    files = _release_files(args.artifacts.resolve(), args.version)
    output = args.output.resolve()
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    for source in files:
        shutil.copy2(source, output / source.name)
    checksums = [
        f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}"
        for path in sorted(output.iterdir())
    ]
    (output / "SHA256SUMS").write_text(
        "\n".join(checksums) + "\n", encoding="utf-8", newline="\n"
    )
    print(f"assembled {len(files)} radixor-c release artifacts in {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
