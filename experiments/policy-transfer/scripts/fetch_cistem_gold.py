#!/usr/bin/env python3
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
