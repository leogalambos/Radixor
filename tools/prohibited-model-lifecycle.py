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

"""Validate and transition the closed set of privately retained model sources."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import signal
import subprocess
import sys
from pathlib import Path


QUARANTINE = Path("models/model-quarantine.properties")
SOURCE_HASHES = Path("models/prohibited-model-sources.sha256")
ACTIVE_TOPOLOGY = Path("models/model-projects.properties")
ALTERNATIVE_TOPOLOGY = Path("models/alternative-model-projects.properties")
PRIVATE_ROOT = Path("models.prohibited")
ACTIVE_ROOT = Path("models")
MARKER = Path("build/prohibited-model-benchmark/staged-models.txt")
MARKER_TEMP = MARKER.with_name(MARKER.name + ".tmp")
IGNORE_HEADER = "# Privately retained model sources whose redistribution status is unresolved."
PROPERTY_PATTERN = re.compile(r"^\s*([A-Za-z][A-Za-z0-9]*)\s*=\s*'([^']*)'\s*$", re.MULTILINE)


class LifecycleError(RuntimeError):
    """Indicates an unsafe or inconsistent prohibited-model lifecycle state."""


class LifecycleSignal(BaseException):
    """Carries a process signal through transactional stage cleanup."""

    def __init__(self, signum: int) -> None:
        super().__init__(f"interrupted by signal {signum}")
        self.signum = signum


def read_properties(path: Path) -> dict[str, str]:
    """Read the repository's simple, escape-free properties format."""

    result: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise LifecycleError(f"Malformed property at {path}:{line_number}.")
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key or not value or key in result:
            raise LifecycleError(f"Invalid or duplicate property at {path}:{line_number}.")
        result[key] = value
    return result


def prohibited_ids() -> tuple[str, ...]:
    """Return the exact sorted prohibited model identifiers."""

    records = read_properties(QUARANTINE)
    for model_id, record in records.items():
        if not re.fullmatch(r"https://github\.com/unimorph/[a-z0-9-]+\|\S.*\S", record):
            raise LifecycleError(f"Invalid prohibited-model evidence record for {model_id}.")
    return tuple(sorted(records))


def read_hash_manifest() -> dict[str, str]:
    """Read and validate the closed source-file SHA-256 manifest."""

    expected: dict[str, str] = {}
    for line_number, line in enumerate(SOURCE_HASHES.read_text(encoding="utf-8").splitlines(), 1):
        match = re.fullmatch(r"([0-9a-f]{64})  ([a-z0-9-]+/.+)", line)
        if match is None or match.group(2) in expected:
            raise LifecycleError(f"Malformed or duplicate hash at {SOURCE_HASHES}:{line_number}.")
        relative = match.group(2)
        if Path(relative).is_absolute() or ".." in Path(relative).parts:
            raise LifecycleError(f"Unsafe source path at {SOURCE_HASHES}:{line_number}.")
        expected[relative] = match.group(1)
    ids = prohibited_ids()
    covered = {Path(relative).parts[0] for relative in expected}
    if covered != set(ids):
        raise LifecycleError("The source hash manifest does not exactly cover prohibited model IDs.")
    return expected


def sha256(path: Path) -> str:
    """Hash one file with bounded auxiliary memory."""

    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def sync_directory(path: Path) -> None:
    """Request persistence of directory-entry changes on the local filesystem."""

    descriptor = os.open(path, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def write_marker() -> None:
    """Durably publish the closed recovery journal before moving any model."""

    MARKER.parent.mkdir(parents=True, exist_ok=True)
    content = "\n".join(prohibited_ids()) + "\n"
    try:
        with MARKER_TEMP.open("x", encoding="utf-8", newline="") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(MARKER_TEMP, MARKER)
        sync_directory(MARKER.parent)
    except BaseException:
        MARKER_TEMP.unlink(missing_ok=True)
        raise


def source_files(root: Path, model_id: str) -> set[str]:
    """Return source-file paths relative to one model directory."""

    directory = root / model_id
    return {
        path.relative_to(directory).as_posix()
        for path in directory.rglob("*")
        if path.is_file() and "build" not in path.relative_to(directory).parts
    }


def verify_sources(root: Path) -> None:
    """Verify exact source paths and bytes below ``root``."""

    manifest = read_hash_manifest()
    for model_id in prohibited_ids():
        expected_files = {
            Path(relative).relative_to(model_id).as_posix()
            for relative in manifest
            if relative.startswith(model_id + "/")
        }
        actual_files = source_files(root, model_id)
        if actual_files != expected_files:
            raise LifecycleError(
                f"Prohibited source inventory differs for {model_id}: "
                f"missing={sorted(expected_files - actual_files)}, extra={sorted(actual_files - expected_files)}."
            )
        for relative in sorted(expected_files):
            logical = f"{model_id}/{relative}"
            actual = sha256(root / logical)
            if actual != manifest[logical]:
                raise LifecycleError(f"SHA-256 mismatch for {root / logical}.")


def verify_topology() -> None:
    """Reject prohibited overlap with either build topology."""

    prohibited = set(prohibited_ids())
    active = set(read_properties(ACTIVE_TOPOLOGY))
    alternative = set(read_properties(ALTERNATIVE_TOPOLOGY))
    overlap = prohibited & (active | alternative)
    if overlap:
        raise LifecycleError(f"Prohibited IDs remain in a build topology: {sorted(overlap)}.")


def expected_ignore_rules() -> tuple[str, ...]:
    """Return exact required ignore rules in deterministic order."""

    return ("/models.prohibited/", *(f"/models/{model_id}/" for model_id in prohibited_ids()))


def verify_ignore_rules() -> None:
    """Verify the dedicated ignore block exactly covers the prohibited set."""

    lines = Path(".gitignore").read_text(encoding="utf-8").splitlines()
    try:
        start = lines.index(IGNORE_HEADER)
    except ValueError as exception:
        raise LifecycleError("The prohibited-model .gitignore block is missing.") from exception
    actual = tuple(line for line in lines[start + 1 :] if line.startswith("/models"))
    expected = expected_ignore_rules()
    if actual != expected:
        raise LifecycleError(f"Prohibited-model ignore rules differ: expected {expected}, found {actual}.")


def verify_untracked() -> None:
    """Reject tracked prohibited sources at either lifecycle location."""

    arguments = ["git", "ls-files", "--", "models.prohibited"]
    arguments.extend(f"models/{model_id}" for model_id in prohibited_ids())
    completed = subprocess.run(arguments, check=True, capture_output=True, text=True)
    tracked = [line for line in completed.stdout.splitlines() if line]
    if tracked:
        raise LifecycleError(f"Prohibited model source is tracked: {tracked}.")


def classify_state() -> tuple[list[str], list[str], list[str]]:
    """Classify model IDs as private, staged, or missing."""

    private: list[str] = []
    staged: list[str] = []
    missing: list[str] = []
    for model_id in prohibited_ids():
        private_exists = (PRIVATE_ROOT / model_id).is_dir()
        staged_exists = (ACTIVE_ROOT / model_id).is_dir()
        if private_exists and staged_exists:
            raise LifecycleError(f"Both lifecycle locations exist for {model_id}.")
        if private_exists:
            private.append(model_id)
        elif staged_exists:
            staged.append(model_id)
        else:
            missing.append(model_id)
    return private, staged, missing


def verify_normal() -> None:
    """Verify normal-build isolation; an entirely absent private store is valid."""

    verify_topology()
    verify_ignore_rules()
    verify_untracked()
    private, staged, missing = classify_state()
    if staged:
        raise LifecycleError(f"Prohibited model directories remain under models/: {staged}.")
    if private and missing:
        raise LifecycleError(f"The private prohibited-model store is incomplete; missing {missing}.")
    if private:
        verify_sources(PRIVATE_ROOT)
    stale_markers = [path for path in (MARKER, MARKER_TEMP) if path.exists()]
    if stale_markers:
        raise LifecycleError(f"Stale prohibited-model stage marker remains: {stale_markers}.")


def read_marker() -> tuple[str, ...]:
    """Read the exact closed staged-ID marker."""

    if not MARKER.is_file():
        raise LifecycleError(f"Missing prohibited-model stage marker: {MARKER}.")
    values = tuple(MARKER.read_text(encoding="utf-8").splitlines())
    if values != prohibited_ids():
        raise LifecycleError("The staged prohibited-model marker is not the exact closed ID set.")
    return values


def restore() -> None:
    """Recover any safely identifiable staged directories into private storage."""

    ids = read_marker()
    PRIVATE_ROOT.mkdir(parents=True, exist_ok=True)
    for model_id in reversed(ids):
        source = ACTIVE_ROOT / model_id
        destination = PRIVATE_ROOT / model_id
        if source.is_dir() and not destination.exists():
            source.rename(destination)
            sync_directory(ACTIVE_ROOT)
            sync_directory(PRIVATE_ROOT)
        elif source.exists() or not destination.is_dir():
            raise LifecycleError(f"Cannot safely restore prohibited model {model_id}.")
    MARKER.unlink()
    sync_directory(MARKER.parent)
    verify_normal()


def recover() -> None:
    """Restore a stale guarded stage, or validate an ordinary private state."""

    if MARKER.exists():
        restore()
    elif MARKER_TEMP.exists():
        _private, staged, _missing = classify_state()
        if staged:
            raise LifecycleError(
                f"Unpublished recovery journal accompanies staged directories: {staged}."
            )
        MARKER_TEMP.unlink()
        sync_directory(MARKER_TEMP.parent)
        verify_normal()
    else:
        verify_normal()


def stage() -> None:
    """Move the exact closed set into temporary Gradle project locations."""

    verify_normal()
    if not PRIVATE_ROOT.is_dir():
        raise LifecycleError("The private prohibited-model source store is absent.")
    try:
        write_marker()
        for model_id in prohibited_ids():
            (PRIVATE_ROOT / model_id).rename(ACTIVE_ROOT / model_id)
            sync_directory(PRIVATE_ROOT)
            sync_directory(ACTIVE_ROOT)
        verify_staged()
    except BaseException as exception:
        try:
            if MARKER.exists():
                restore()
            else:
                recover()
        except BaseException as cleanup_exception:
            raise LifecycleError(
                f"Staging failed and recovery also failed: {cleanup_exception}"
            ) from exception
        raise


def signal_interruption(signum: int, _frame: object) -> None:
    """Convert supported termination signals into recoverable control flow."""

    raise LifecycleSignal(signum)


def verify_staged() -> None:
    """Verify the exact guarded stage and its source bytes."""

    read_marker()
    private, staged, missing = classify_state()
    if private or missing or tuple(staged) != prohibited_ids():
        raise LifecycleError(
            f"Invalid staged state: private={private}, staged={staged}, missing={missing}."
        )
    verify_sources(ACTIVE_ROOT)
    verify_topology()
    verify_untracked()


def required_property(script: str, key: str, model_id: str) -> str:
    """Read one required single-quoted model build property."""

    values = {name: value for name, value in PROPERTY_PATTERN.findall(script)}
    value = values.get(key)
    if value is None or not value:
        raise LifecycleError(f"Model {model_id} does not declare {key}.")
    return value


def write_benchmark_manifest(output: Path) -> None:
    """Write private benchmark input identities from a verified stage."""

    verify_staged()
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as stream:
        stream.write("model_id\tlanguage\tdisplay_name\tmodel_version\tmodel_sha256\tdictionary\n")
        for model_id in prohibited_ids():
            directory = ACTIVE_ROOT / model_id
            script = (directory / "build.gradle").read_text(encoding="utf-8")
            dictionary = directory / "src/modelInput/stemmer.gz"
            declared_model_id = required_property(script, "modelId", model_id)
            if declared_model_id != model_id:
                raise LifecycleError(
                    f"Prohibited source directory {model_id} declares model ID {declared_model_id}."
                )
            fields = (
                declared_model_id,
                required_property(script, "language", model_id),
                required_property(script, "displayName", model_id),
                (directory / "model-version.txt").read_text(encoding="utf-8").strip(),
                sha256(dictionary),
                str(dictionary.resolve()),
            )
            if any("\t" in field or "\n" in field or "\r" in field for field in fields):
                raise LifecycleError(f"Model {model_id} metadata is unsafe for the benchmark manifest.")
            if not re.fullmatch(r"[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*", fields[1]) \
                    or not fields[2].strip() or not fields[3].strip():
                raise LifecycleError(f"Model {model_id} benchmark metadata is invalid.")
            stream.write("\t".join(fields) + "\n")


def parse_arguments() -> argparse.Namespace:
    """Parse the closed command-line interface."""

    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("verify-normal", "verify-staged", "recover", "stage", "restore", "write-benchmark-manifest"))
    parser.add_argument("output", nargs="?", type=Path)
    return parser.parse_args()


def main() -> int:
    """Run one lifecycle operation with concise diagnostics."""

    arguments = parse_arguments()
    previous_handlers: dict[int, object] = {}
    if arguments.command == "stage":
        for signum in (signal.SIGHUP, signal.SIGINT, signal.SIGTERM):
            previous_handlers[signum] = signal.signal(signum, signal_interruption)
    try:
        if arguments.command == "verify-normal":
            verify_normal()
        elif arguments.command == "verify-staged":
            verify_staged()
        elif arguments.command == "recover":
            recover()
        elif arguments.command == "stage":
            stage()
        elif arguments.command == "restore":
            restore()
        elif arguments.command == "write-benchmark-manifest":
            if arguments.output is None:
                raise LifecycleError("write-benchmark-manifest requires an output path.")
            write_benchmark_manifest(arguments.output)
        print(f"Prohibited model lifecycle {arguments.command}: PASS")
        return 0
    except LifecycleSignal as exception:
        print(
            f"Prohibited model lifecycle {arguments.command}: {exception}",
            file=sys.stderr,
        )
        return 128 + exception.signum
    except (LifecycleError, OSError, subprocess.CalledProcessError) as exception:
        print(f"Prohibited model lifecycle {arguments.command}: {exception}", file=sys.stderr)
        return 1
    finally:
        for signum, previous_handler in previous_handlers.items():
            signal.signal(signum, previous_handler)


if __name__ == "__main__":
    sys.exit(main())
