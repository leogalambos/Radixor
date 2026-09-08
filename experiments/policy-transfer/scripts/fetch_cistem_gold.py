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

"""Fetch and verify the exact CISTEM resources used by paper 4.

The CELEX-derived gold-standard files are intentionally not redistributed with
the article package. The official CISTEM Python implementation is also fetched
from the same pinned repository revision so the fixed-rule baseline can be
reproduced without relying on a locally installed package.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import urllib.request

CISTEM_COMMIT = "7c19867c2e062c8a7d44b394c19573845ac4bd89"
RESOURCES = {
    "goldstandard1.txt": (
        "gold_standards/goldstandard1.txt",
        "8627bb28b67429f6488f8d017f510327b2c84d1c",
        3_947_464,
    ),
    "goldstandard2.txt": (
        "gold_standards/goldstandard2.txt",
        "2cb401638a67760f5fec47c8379646bf6d6d1b8e",
        3_893_379,
    ),
    "Cistem.py": (
        "Cistem.py",
        "dbc90836bb6361712b52b2e504b85c702294a29f",
        4_585,
    ),
}
RAW_BASE = "https://raw.githubusercontent.com/LeonieWeissweiler/CISTEM/" + CISTEM_COMMIT + "/"


def git_blob_sha1(payload: bytes) -> str:
    """Return the Git SHA-1 blob identity for *payload*."""
    header = f"blob {len(payload)}\0".encode("ascii")
    return hashlib.sha1(header + payload).hexdigest()


def fetch_one(
    target_name: str,
    source_path: str,
    expected_blob: str,
    expected_size: int,
    output_dir: Path,
) -> None:
    """Download one pinned CISTEM resource and verify size and Git blob identity."""
    request = urllib.request.Request(
        RAW_BASE + source_path,
        headers={"User-Agent": "Radixor-policy-transfer-reproducibility/1.1"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        payload = response.read()
    if len(payload) != expected_size:
        raise RuntimeError(
            f"Size mismatch for {target_name}: expected {expected_size}, got {len(payload)}"
        )
    actual = git_blob_sha1(payload)
    if actual != expected_blob:
        raise RuntimeError(
            f"Blob mismatch for {target_name}: expected {expected_blob}, got {actual}"
        )
    output_dir.mkdir(parents=True, exist_ok=True)
    target = output_dir / target_name
    target.write_bytes(payload)
    print(f"verified {target_name}: bytes={len(payload)} git-blob={actual}")


def main() -> None:
    """Parse command-line arguments and fetch all frozen CISTEM resources."""
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("data/external"),
        help="Destination directory (default: data/external)",
    )
    arguments = parser.parse_args()
    for target_name, (source_path, expected_blob, expected_size) in RESOURCES.items():
        fetch_one(
            target_name,
            source_path,
            expected_blob,
            expected_size,
            arguments.output_dir,
        )


if __name__ == "__main__":
    main()
