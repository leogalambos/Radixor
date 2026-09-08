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

"""Run a complete synthetic end-to-end smoke test through the real Radixor JAR."""
from __future__ import annotations

from pathlib import Path
import shutil
import subprocess
import sys

BASES = ("spiel", "lern", "fahr", "koch", "mal", "sing", "tanz", "frag", "zahl", "plan")


def run_checked(command: list[str]) -> None:
    """Run one smoke-test subprocess and expose captured diagnostics only on failure."""
    completed = subprocess.run(command, capture_output=True, text=True, check=False)
    if completed.returncode == 0:
        return
    if completed.stdout:
        print(completed.stdout, file=sys.stderr, end="")
    if completed.stderr:
        print(completed.stderr, file=sys.stderr, end="")
    raise subprocess.CalledProcessError(completed.returncode, command)


def main() -> None:
    """Create two synthetic policies, compile all folds, and verify aggregates."""
    root = Path(__file__).resolve().parents[1]
    smoke = root / "build/smoke"
    if smoke.exists():
        shutil.rmtree(smoke)
    smoke.mkdir(parents=True)
    gs1 = smoke / "goldstandard1.txt"
    gs2 = smoke / "goldstandard2.txt"
    gs1_lines: list[str] = []
    gs2_lines: list[str] = []
    for base in BASES:
        inflection = (base, base + "e", base + "t")
        derivation = (base + "er", base + "ers")
        gs1_lines.append(" ".join((*inflection, *derivation)))
        gs2_lines.append(" ".join(inflection))
        gs2_lines.append(" ".join(derivation))
    gs1.write_text("\n".join(gs1_lines) + "\n", encoding="utf-8")
    gs2.write_text("\n".join(gs2_lines) + "\n", encoding="utf-8")

    # Use a temporary project copy for outputs so publication data/derived is not polluted.
    scratch = smoke / "project"
    (scratch / "scripts/java/org/egothor/stemmer/experiment").mkdir(parents=True)
    (scratch / "frozen").mkdir(parents=True)
    shutil.copy2(root / "scripts/policy_transfer_experiment.py", scratch / "scripts/policy_transfer_experiment.py")
    shutil.copy2(root / "scripts/verify_experiment.py", scratch / "scripts/verify_experiment.py")
    shutil.copy2(
        root / "scripts/java/org/egothor/stemmer/experiment/PolicyModelRunner.java",
        scratch / "scripts/java/org/egothor/stemmer/experiment/PolicyModelRunner.java",
    )
    shutil.copy2(
        root / "frozen/Radixor-4.2.0-8-g0c3b13f.jar",
        scratch / "frozen/Radixor-4.2.0-8-g0c3b13f.jar",
    )
    run_checked(
        [
            sys.executable,
            str(scratch / "scripts/policy_transfer_experiment.py"),
            "--project",
            str(scratch),
            "--gs1",
            str(gs1),
            "--gs2",
            str(gs2),
            "--skip-pin-validation",
        ]
    )
    run_checked([sys.executable, str(scratch / "scripts/verify_experiment.py")])
    print("synthetic end-to-end smoke test passed")


if __name__ == "__main__":
    main()
