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

"""Create an isolated, tag-versioned Python release source tree."""

from __future__ import annotations

import argparse
import re
import shutil
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[2]
PYTHON_ROOT = REPOSITORY / "python"
BUILD_ROOT = REPOSITORY / "build"
VERSION = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\Z")


def _replace_once(path: Path, pattern: str, replacement: str) -> None:
    text = path.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise ValueError(f"Expected exactly one version field in {path}")
    path.write_text(updated, encoding="utf-8", newline="\n")


def _prepare_output(path: Path) -> Path:
    output = path.resolve()
    try:
        output.relative_to(BUILD_ROOT.resolve())
    except ValueError as exc:
        raise ValueError(f"Release staging output must be below {BUILD_ROOT}") from exc
    if output == BUILD_ROOT.resolve():
        raise ValueError("Release staging output cannot be the build root")
    if output.exists():
        shutil.rmtree(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    return output


def _copy_native(output: Path, version: str) -> None:
    shutil.copytree(
        PYTHON_ROOT,
        output,
        ignore=shutil.ignore_patterns(
            ".gitignore",
            ".pytest_cache",
            ".ruff_cache",
            ".venv",
            "__pycache__",
            "*.pyc",
            "_radixor*.dll",
            "_radixor*.dylib",
            "_radixor*.pyd",
            "_radixor*.so",
            "_native*.dll",
            "_native*.dylib",
            "_native*.pyd",
            "_native*.so",
            "benchmarks",
            "dist",
            "models",
            "models-standard",
            "target",
            "tests",
        ),
    )
    _replace_once(
        output / "pyproject.toml",
        r'^version = "0\.0\.0"$',
        f'version = "{version}"',
    )
    _replace_once(
        output / "Cargo.toml",
        r'^version = "0\.0\.0"$',
        f'version = "{version}"',
    )
    lock = output / "Cargo.lock"
    lock_text = lock.read_text(encoding="utf-8")
    pattern = r'(\[\[package\]\]\nname = "radixor"\n)version = "0\.0\.0"'
    lock_text, count = re.subn(pattern, rf'\g<1>version = "{version}"', lock_text)
    if count != 1:
        raise ValueError("Expected exactly one radixor package entry in Cargo.lock")
    lock.write_text(lock_text, encoding="utf-8", newline="\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("distribution", choices=("native",))
    parser.add_argument("version")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    if VERSION.fullmatch(args.version) is None:
        raise SystemExit(f"Invalid stable release version: {args.version!r}")
    output = _prepare_output(args.output)
    _copy_native(output, args.version)
    print(f"prepared {args.distribution} {args.version} in {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
