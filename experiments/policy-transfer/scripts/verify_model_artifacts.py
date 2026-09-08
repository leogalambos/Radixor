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

"""Verify persisted policy-model files against publication SHA-256 metadata."""
from __future__ import annotations

import csv
import hashlib
from pathlib import Path


def sha256_file(path: Path) -> str:
    """Return the SHA-256 digest of one persisted model file."""
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    """Verify all expected model files by key, byte size, and SHA-256 digest."""
    root = Path(__file__).resolve().parents[1]
    metadata_path = root / "data/derived/model_artifacts.csv"
    if not metadata_path.is_file():
        raise SystemExit("model metadata are absent; run the experiment first")

    with metadata_path.open("r", encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream))
    if len(rows) != 30:
        raise AssertionError(f"Expected 30 model metadata rows, found {len(rows)}.")

    seen: set[tuple[str, int, str]] = set()
    for row in rows:
        representative = row["representative"]
        fold = int(row["fold"])
        model_policy = row["model_policy"]
        key = (representative, fold, model_policy)
        if key in seen:
            raise AssertionError(f"Duplicate model metadata key: {key}")
        seen.add(key)

        suffix = "gs1" if model_policy == "GS1" else "gs2"
        model = root / "build/private" / representative / f"fold-{fold}" / f"m_{suffix}.radixor.gz"
        if not model.is_file():
            raise FileNotFoundError(model)
        expected_bytes = int(row["model_bytes"])
        actual_bytes = model.stat().st_size
        if actual_bytes != expected_bytes:
            raise AssertionError(
                f"Model size mismatch for {key}: expected {expected_bytes}, got {actual_bytes}."
            )
        expected_sha256 = row["model_sha256"]
        actual_sha256 = sha256_file(model)
        if actual_sha256 != expected_sha256:
            raise AssertionError(
                f"Model SHA-256 mismatch for {key}: expected {expected_sha256}, got {actual_sha256}."
            )

    print("verified 30 persisted policy models by byte size and SHA-256")


if __name__ == "__main__":
    main()
