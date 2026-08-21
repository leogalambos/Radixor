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

"""Validate and assemble the exact files allowed in a Python GitHub Release."""

from __future__ import annotations

import argparse
import hashlib
import re
import shutil
from pathlib import Path

from verify_distributions import (
    _verify_main_sdist,
    _verify_main_wheel,
    _verify_standard_sdist,
    _verify_standard_wheel,
)

REPOSITORY = Path(__file__).resolve().parents[2]
BUILD_ROOT = (REPOSITORY / "build").resolve()
VERSION = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\Z")


def _release_files(root: Path) -> list[Path]:
    files = sorted(
        path
        for path in root.rglob("*")
        if path.is_file()
        and (path.name.endswith(".whl") or path.name.endswith(".tar.gz"))
    )
    names = [path.name for path in files]
    if len(names) != len(set(names)):
        raise ValueError(f"Duplicate release filenames: {names}")
    return files


def _require_one(files: list[Path], predicate, description: str) -> Path:
    matches = [path for path in files if predicate(path.name)]
    if len(matches) != 1:
        raise ValueError(
            f"Expected one {description}, found {[path.name for path in matches]}"
        )
    return matches[0]


def _validate_native(files: list[Path], version: str) -> None:
    prefix = f"radixor-{version}-cp39-abi3-"
    wheels = [path for path in files if path.suffix == ".whl"]
    sdist = _require_one(
        files, lambda name: name == f"radixor-{version}.tar.gz", "native sdist"
    )
    if len(wheels) != 4:
        raise ValueError(
            f"Expected four native wheels, found {[path.name for path in wheels]}"
        )
    expected = {
        "linux-x86_64": lambda name: name.startswith(prefix)
        and "manylinux" in name
        and name.endswith("x86_64.whl"),
        "linux-aarch64": lambda name: name.startswith(prefix)
        and "manylinux" in name
        and name.endswith("aarch64.whl"),
        "macos-universal2": lambda name: name.startswith(prefix)
        and "macosx" in name
        and name.endswith("universal2.whl"),
        "windows-x86_64": lambda name: name == f"{prefix}win_amd64.whl",
    }
    for description, predicate in expected.items():
        _require_one(wheels, predicate, description)
    for wheel in wheels:
        _verify_main_wheel(wheel, version)
    _verify_main_sdist(sdist, version)


def _validate_models(files: list[Path], version: str) -> None:
    wheel = _require_one(
        files,
        lambda name: name == f"radixor_models_standard-{version}-py3-none-any.whl",
        "standard-model wheel",
    )
    sdist = _require_one(
        files,
        lambda name: name == f"radixor_models_standard-{version}.tar.gz",
        "standard-model sdist",
    )
    if len(files) != 2:
        raise ValueError(
            f"Unexpected standard-model release files: {[path.name for path in files]}"
        )
    _verify_standard_wheel(wheel, version)
    _verify_standard_sdist(sdist, version)


def _output_directory(path: Path) -> Path:
    output = path.resolve()
    try:
        output.relative_to(BUILD_ROOT)
    except ValueError as exc:
        raise ValueError(f"Release output must be below {BUILD_ROOT}") from exc
    if output == BUILD_ROOT:
        raise ValueError("Release output cannot be the build root")
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("distribution", choices=("native", "models-standard"))
    parser.add_argument("version")
    parser.add_argument("artifacts", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    if VERSION.fullmatch(args.version) is None:
        raise SystemExit(f"Invalid stable release version: {args.version!r}")
    files = _release_files(args.artifacts.resolve())
    if args.distribution == "native":
        _validate_native(files, args.version)
    else:
        _validate_models(files, args.version)

    output = _output_directory(args.output)
    for source in files:
        shutil.copy2(source, output / source.name)
    checksums = [
        f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}"
        for path in sorted(output.iterdir())
        if path.is_file()
    ]
    (output / "SHA256SUMS").write_text(
        "\n".join(checksums) + "\n", encoding="utf-8", newline="\n"
    )
    print(f"assembled {len(files)} release artifacts in {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
