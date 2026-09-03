#!/usr/bin/env python3
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
