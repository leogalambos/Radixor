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
"""Build the Arabic Radixor dictionary from the two pinned UniMorph files."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import unicodedata
from pathlib import Path


SOURCES = {
    "ara_atb": "7591a52634b0bbe6b6e28b4f4455d863a0c2580653526d23c04217e5f08b0961",
    "ara_new": "2295e4db73427cf1690f04a7615c6cf64015d3341285e6914a672c177285602c",
    "README.md": "498e3d8e2ec409617e1f35b560eb6119981120d47bf1d66c1ed3d79f0fdd8ab5",
}
REVISION = "e9b7521ab75e3a7da6f996a428031164993e6f19"


def parse_arguments() -> argparse.Namespace:
    """Parse exact local inputs and the generated model destination."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ara-atb", type=Path, required=True)
    parser.add_argument("--ara-new", type=Path, required=True)
    parser.add_argument("--readme", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def verify_source(path: Path, source_name: str) -> None:
    """Reject a source whose bytes differ from the pinned upstream revision."""

    with path.open("rb") as source:
        actual = hashlib.file_digest(source, "sha256").hexdigest()
    expected = SOURCES[source_name]
    if actual != expected:
        raise ValueError(
            f"{source_name} SHA-256 mismatch: expected {expected}, found {actual}"
        )


def is_usable(value: str) -> bool:
    """Accept Unicode letters, numbers, and combining marks only."""

    return bool(value) and all(
        unicodedata.category(character)[0] in {"L", "M", "N"}
        for character in value
    )


def read_groups(paths: list[Path]) -> dict[str, set[str]]:
    """Read, normalize, validate, and merge lemma-to-form groups."""

    groups: dict[str, set[str]] = {}
    for path in paths:
        with path.open(encoding="utf-8", newline="") as source:
            for line_number, line in enumerate(source, start=1):
                columns = line.rstrip("\r\n").split("\t")
                if len(columns) != 3:
                    raise ValueError(
                        f"{path}:{line_number}: expected three tab-separated columns"
                    )
                lemma = unicodedata.normalize("NFC", columns[0])
                form = unicodedata.normalize("NFC", columns[1])
                if not is_usable(lemma) or not is_usable(form):
                    continue
                groups.setdefault(lemma, set()).add(form)
    return groups


def render(groups: dict[str, set[str]]) -> bytes:
    """Render deterministic UTF-8 Radixor dictionary content."""

    lines = [
        "# This file contains adapted data derived from the UniMorph project.",
        f"# Source revision: {REVISION}",
        f"# ara_atb SHA-256: {SOURCES['ara_atb']}",
        f"# ara_new SHA-256: {SOURCES['ara_new']}",
        "# Original license: CC BY-SA 3.0",
        "# Changes: NFC normalization; Unicode letter, number, and combining-mark filtering; grouping and exact deduplication.",
    ]
    for lemma in sorted(groups):
        variants = sorted(form for form in groups[lemma] if form != lemma)
        lines.append("\t".join((lemma, *variants)))
    return ("\n".join(lines) + "\n").encode("utf-8")


def write_gzip(path: Path, content: bytes) -> None:
    """Write a reproducible GZip stream with no filename or timestamp."""

    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as raw:
        with gzip.GzipFile(
            filename="", mode="wb", fileobj=raw, compresslevel=9, mtime=0
        ) as compressed:
            compressed.write(content)


def main() -> None:
    """Verify the pinned inputs and rebuild the checked-in dictionary."""

    arguments = parse_arguments()
    verify_source(arguments.ara_atb, "ara_atb")
    verify_source(arguments.ara_new, "ara_new")
    verify_source(arguments.readme, "README.md")
    readme = arguments.readme.read_text(encoding="utf-8")
    for required_text in ("## `ara_atb`", "## `ara_new`", "Creative Commons Attribution-ShareAlike 3.0"):
        if required_text not in readme:
            raise ValueError(f"Pinned Arabic README omits required provenance or license text: {required_text}")
    groups = read_groups([arguments.ara_atb, arguments.ara_new])
    if not groups:
        raise ValueError("The pinned Arabic sources produced no usable groups")
    content = render(groups)
    write_gzip(arguments.output, content)
    print(f"Wrote {len(groups):,} Arabic lemma groups to {arguments.output}")


if __name__ == "__main__":
    main()
