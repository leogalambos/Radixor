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

"""Verify Radixor's native and standard-model release archives."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import stat
import subprocess
import sys
import tarfile
import tempfile
import zipfile
from email.parser import BytesParser
from pathlib import Path, PurePosixPath

EXPECTED_DEFAULT_COUNT = 20
EXPECTED_DEPENDENCY = "radixor-models-standard>=1.0,<2.0"
REPOSITORY = Path(__file__).resolve().parents[2]
EXPECTED_NATIVE_LICENSE = REPOSITORY.joinpath("LICENSE").read_bytes()
EXPECTED_MODEL_IDS = {
    "cs-cz-default",
    "da-dk-default",
    "de-de-default",
    "es-es-default",
    "fa-ir-default",
    "fi-fi-default",
    "fr-fr-default",
    "he-il-default",
    "hu-hu-default",
    "it-it-default",
    "nb-no-default",
    "nl-nl-default",
    "nn-no-default",
    "pl-pl-unimorph",
    "pt-pt-default",
    "ru-ru-default",
    "sv-se-default",
    "uk-ua-default",
    "us-uk-default",
    "yi-default",
}
STANDARD_NAME = "radixor-models-standard"
STANDARD_VERSION = "0.0.0"
MAIN_NAME = "radixor"
MAIN_VERSION = "0.0.0"


def _one(directory: Path, pattern: str) -> Path:
    matches = sorted(directory.glob(pattern))
    if len(matches) != 1:
        raise ValueError(
            f"Expected exactly one {pattern} in {directory}, found {len(matches)}"
        )
    return matches[0]


def _assert_no_repository_temp_gzip() -> None:
    offenders = sorted(
        path.name for path in REPOSITORY.glob("tmp*.gz") if path.is_file()
    )
    if offenders:
        raise ValueError(
            "Repository root contains unmanaged Python temporary gzip files; "
            f"remove them and fix the producing workflow: {offenders}"
        )


def _wheel_metadata(archive: zipfile.ZipFile) -> object:
    names = [
        name for name in archive.namelist() if name.endswith(".dist-info/METADATA")
    ]
    if len(names) != 1:
        raise ValueError("Wheel must contain exactly one METADATA file")
    return BytesParser().parsebytes(archive.read(names[0]))


def _normalized_member_name(name: str, archive_name: str) -> str:
    if not name or "\\" in name or name.startswith("/"):
        raise ValueError(f"{archive_name} contains unsafe archive path: {name!r}")
    stripped = name[:-1] if name.endswith("/") else name
    parts = stripped.split("/")
    if not stripped or any(part in {"", ".", ".."} for part in parts):
        raise ValueError(f"{archive_name} contains unsafe archive path: {name!r}")
    return "/".join(parts)


def _validate_zip_members(archive: zipfile.ZipFile, archive_name: str) -> list[str]:
    names: list[str] = []
    seen: set[str] = set()
    for info in archive.infolist():
        name = _normalized_member_name(info.filename, archive_name)
        if name in seen:
            raise ValueError(f"{archive_name} contains duplicate member: {name}")
        seen.add(name)
        mode = (info.external_attr >> 16) & 0xFFFF
        if stat.S_ISLNK(mode):
            raise ValueError(f"{archive_name} contains symlink-like ZIP member: {name}")
        names.append(name)
    return names


def _validate_tar_members(archive: tarfile.TarFile, archive_name: str) -> list[str]:
    names: list[str] = []
    seen: set[str] = set()
    for member in archive.getmembers():
        name = _normalized_member_name(member.name, archive_name)
        if name in seen:
            raise ValueError(f"{archive_name} contains duplicate member: {name}")
        seen.add(name)
        if not (member.isfile() or member.isdir()):
            raise ValueError(f"{archive_name} contains unsafe special member: {name}")
        names.append(name)
    return names


def _assert_distribution_metadata(
    metadata: object, name: str, version: str, archive_name: str
) -> None:
    if metadata.get("Name") != name or metadata.get("Version") != version:
        raise ValueError(
            f"{archive_name} has unexpected distribution identity: "
            f"{metadata.get('Name')} {metadata.get('Version')}"
        )


def _tar_file_bytes(archive: tarfile.TarFile, name: str) -> bytes:
    member = archive.getmember(name)
    stream = archive.extractfile(member)
    if stream is None:
        raise ValueError(f"Cannot read archive member: {name}")
    return stream.read()


def _validate_standard_manifest(
    manifest: object, expected_version: str = STANDARD_VERSION
) -> list[dict]:
    if not isinstance(manifest, dict) or set(manifest) != {
        "catalog_version",
        "distribution_version",
        "format",
        "models",
        "schema_version",
        "topology",
    }:
        raise ValueError("Standard model manifest has an invalid top-level schema")
    if (
        manifest["schema_version"] != 1
        or manifest["catalog_version"] != "2026.1"
        or manifest["distribution_version"] != expected_version
        or manifest["topology"] != "models/model-projects.properties"
        or manifest["format"] != {"compression": "gzip", "magic": "EGTR", "version": 7}
        or not isinstance(manifest["models"], list)
    ):
        raise ValueError("Standard model manifest has incompatible catalog metadata")
    models = manifest["models"]
    if {
        model.get("id") for model in models if isinstance(model, dict)
    } != EXPECTED_MODEL_IDS:
        raise ValueError(
            "Standard model manifest does not match the default model topology"
        )
    if len(models) != len(EXPECTED_MODEL_IDS):
        raise ValueError("Standard model manifest contains duplicate model entries")
    provenance_keys = {
        "attribution",
        "dataset",
        "license",
        "license_uri",
        "repository",
        "revision",
        "revision_status",
        "source_name",
        "source_project",
        "source_version",
        "transformations",
        "verification_date",
    }
    for model in models:
        if set(model) != {
            "file",
            "id",
            "notice",
            "provenance",
            "sha256",
            "source",
            "version",
        }:
            raise ValueError("Standard model manifest contains an invalid model entry")
        model_id = model["id"]
        if (
            model["file"] != f"models/{model_id}.rxc"
            or model["notice"] != f"notices/{model_id}/NOTICE-model-data.txt"
            or model["version"] != "1.0.0"
            or not isinstance(model["sha256"], str)
            or len(model["sha256"]) != 64
            or set(model["provenance"]) != provenance_keys
            or model["provenance"]["license"] != "CC-BY-SA-3.0"
            or set(model["source"]) != {"path", "sha256"}
            or model["source"]["path"] != f"models/{model_id}/src/modelInput/stemmer.gz"
        ):
            raise ValueError(f"Standard model manifest entry is invalid: {model_id}")
        if not all(isinstance(value, str) for value in model["provenance"].values()):
            raise ValueError(f"Standard model provenance is invalid: {model_id}")
    return models


def _assert_no_source_dictionaries(names: list[str], archive_name: str) -> None:
    offenders = [
        name
        for name in names
        if name.endswith("stemmer.gz") or "/radixor/models/" in name
    ]
    if offenders:
        raise ValueError(
            f"{archive_name} contains forbidden source dictionaries: {offenders}"
        )


def _assert_no_standard_model_payload(names: list[str], archive_name: str) -> None:
    offenders = []
    for name in names:
        parts = PurePosixPath(name).parts
        if (
            name.endswith(".rxc")
            or "models-standard" in parts
            or any(part.startswith("radixor_models_standard") for part in parts)
        ):
            offenders.append(name)
    if offenders:
        raise ValueError(
            f"{archive_name} contains standard-model payload owned by "
            f"radixor-models-standard: {offenders}"
        )


def _assert_no_local_build_outputs(names: list[str], archive_name: str) -> None:
    offenders = []
    for name in names:
        path = PurePosixPath(name)
        parts = path.parts
        is_benchmark_result = (
            "benchmarks" in parts
            and path.name.startswith("results")
            and path.suffix in {".csv", ".json"}
        )
        if "dist" in parts or is_benchmark_result:
            offenders.append(name)
    if offenders:
        raise ValueError(
            f"{archive_name} contains local distribution or benchmark outputs: {offenders}"
        )


def _assert_no_python_cache(
    names: list[str], archive_name: str, *, allow_native_pyd: bool = False
) -> None:
    offenders = []
    for name in names:
        path = PurePosixPath(name)
        is_native_module = (
            allow_native_pyd
            and path.suffix == ".pyd"
            and path.name.startswith("_radixor")
        )
        if (
            "__pycache__" in path.parts
            or path.suffix in {".pyc", ".pyo"}
            or (path.suffix == ".pyd" and not is_native_module)
        ):
            offenders.append(name)
    if offenders:
        raise ValueError(f"{archive_name} contains Python cache artifacts: {offenders}")


def _assert_main_dependency(metadata, archive_name: str) -> None:
    requirements = metadata.get_all("Requires-Dist", [])
    normalized = {requirement.replace(" ", "") for requirement in requirements}
    if EXPECTED_DEPENDENCY not in normalized:
        raise ValueError(
            f"Missing compatible standard-model dependency in {archive_name}: "
            f"{requirements}"
        )


def _verify_main_wheel(path: Path, expected_version: str = MAIN_VERSION) -> None:
    with zipfile.ZipFile(path) as archive:
        names = _validate_zip_members(archive, path.name)
        _assert_no_source_dictionaries(names, path.name)
        _assert_no_standard_model_payload(names, path.name)
        _assert_no_python_cache(names, path.name, allow_native_pyd=True)
        metadata = _wheel_metadata(archive)
        _assert_distribution_metadata(metadata, MAIN_NAME, expected_version, path.name)
        if metadata.get("License-Expression") != "BSD-3-Clause":
            raise ValueError(
                f"{path.name} must declare License-Expression: BSD-3-Clause"
            )
        license_names = [
            name for name in names if name.endswith(".dist-info/licenses/LICENSE")
        ]
        if (
            len(license_names) != 1
            or archive.read(license_names[0]) != EXPECTED_NATIVE_LICENSE
        ):
            raise ValueError(
                f"{path.name} does not contain the full repository BSD-3-Clause LICENSE"
            )
        _assert_main_dependency(metadata, path.name)


def _verify_main_sdist(path: Path, expected_version: str = MAIN_VERSION) -> None:
    with tarfile.open(path, "r:gz") as archive:
        names = _validate_tar_members(archive, path.name)
        _assert_no_source_dictionaries(names, path.name)
        _assert_no_standard_model_payload(names, path.name)
        _assert_no_local_build_outputs(names, path.name)
        _assert_no_python_cache(names, path.name)
        license_members = [
            member
            for member in archive.getmembers()
            if PurePosixPath(member.name).name == "LICENSE" and member.isfile()
        ]
        if len(license_members) != 1:
            raise ValueError(
                f"{path.name} must contain exactly one BSD-3-Clause LICENSE"
            )
        license_stream = archive.extractfile(license_members[0])
        if license_stream is None or license_stream.read() != EXPECTED_NATIVE_LICENSE:
            raise ValueError(
                f"{path.name} does not contain the full repository BSD-3-Clause LICENSE"
            )
        root = f"radixor-{expected_version}"
        metadata = BytesParser().parsebytes(
            _tar_file_bytes(archive, f"{root}/PKG-INFO")
        )
        _assert_distribution_metadata(metadata, MAIN_NAME, expected_version, path.name)
        _assert_main_dependency(metadata, path.name)


def _verify_standard_wheel(
    path: Path, expected_version: str = STANDARD_VERSION
) -> None:
    if not path.name.endswith("-py3-none-any.whl"):
        raise ValueError(f"Standard model wheel is not pure py3-none-any: {path.name}")
    with zipfile.ZipFile(path) as archive:
        names = _validate_zip_members(archive, path.name)
        _assert_no_source_dictionaries(names, path.name)
        _assert_no_python_cache(names, path.name)
        metadata = _wheel_metadata(archive)
        _assert_distribution_metadata(
            metadata, STANDARD_NAME, expected_version, path.name
        )
        if metadata.get("License-Expression") != "CC-BY-SA-3.0":
            raise ValueError("Standard model data must declare CC-BY-SA-3.0")
        manifest = json.loads(archive.read("radixor_models_standard/manifest.json"))
        models = _validate_standard_manifest(manifest, expected_version)
        dist_info = next(
            name.rsplit("/", 1)[0]
            for name in names
            if name.endswith(".dist-info/METADATA")
        )
        allowed = {
            "radixor_models_standard/__init__.py",
            "radixor_models_standard/manifest.json",
            "radixor_models_standard/models/__init__.py",
            "radixor_models_standard/notices/__init__.py",
            f"{dist_info}/METADATA",
            f"{dist_info}/WHEEL",
            f"{dist_info}/RECORD",
            f"{dist_info}/top_level.txt",
            f"{dist_info}/licenses/LICENSE-MODEL-DATA.txt",
        }
        for model in models:
            model_name = f"radixor_models_standard/{model['file']}"
            notice_name = f"radixor_models_standard/{model['notice']}"
            allowed.update({model_name, notice_name})
            data = archive.read(model_name)
            if hashlib.sha256(data).hexdigest() != model["sha256"]:
                raise ValueError(f"Checksum mismatch for {model['id']}")
            if gzip.decompress(data)[:8] != b"EGTR\x00\x00\x00\x07":
                raise ValueError(f"Invalid v7 marker/version for {model['id']}")
            if notice_name not in names:
                raise ValueError(f"Missing model notice for {model['id']}")
        unexpected = set(names) - allowed
        missing = allowed - set(names)
        if unexpected or missing:
            raise ValueError(
                f"Standard wheel allowlist mismatch; unexpected={sorted(unexpected)}, "
                f"missing={sorted(missing)}"
            )
        for info in archive.infolist():
            if info.filename in names and ((info.external_attr >> 16) & 0o111):
                raise ValueError(
                    f"Standard wheel contains executable member: {info.filename}"
                )


def _verify_standard_sdist(
    path: Path, expected_version: str = STANDARD_VERSION
) -> None:
    with tarfile.open(path, "r:gz") as archive:
        names = _validate_tar_members(archive, path.name)
        _assert_no_source_dictionaries(names, path.name)
        _assert_no_python_cache(names, path.name)
        root = f"radixor_models_standard-{expected_version}"
        metadata = BytesParser().parsebytes(
            _tar_file_bytes(archive, f"{root}/PKG-INFO")
        )
        _assert_distribution_metadata(
            metadata, STANDARD_NAME, expected_version, path.name
        )
        manifest_name = f"{root}/radixor_models_standard/manifest.json"
        models = _validate_standard_manifest(
            json.loads(_tar_file_bytes(archive, manifest_name)), expected_version
        )
        egg_info = f"{root}/radixor_models_standard.egg-info"
        allowed_files = {
            f"{root}/LICENSE-MODEL-DATA.txt",
            f"{root}/MANIFEST.in",
            f"{root}/PKG-INFO",
            f"{root}/README.md",
            f"{root}/pyproject.toml",
            f"{root}/setup.cfg",
            f"{root}/radixor_models_standard/__init__.py",
            manifest_name,
            f"{root}/radixor_models_standard/models/__init__.py",
            f"{root}/radixor_models_standard/notices/__init__.py",
            f"{egg_info}/PKG-INFO",
            f"{egg_info}/SOURCES.txt",
            f"{egg_info}/dependency_links.txt",
            f"{egg_info}/top_level.txt",
        }
        allowed_dirs = {
            root,
            f"{root}/radixor_models_standard",
            f"{root}/radixor_models_standard/models",
            f"{root}/radixor_models_standard/notices",
            egg_info,
        }
        for model in models:
            model_name = f"{root}/radixor_models_standard/{model['file']}"
            notice_name = f"{root}/radixor_models_standard/{model['notice']}"
            notice_dir = notice_name.rsplit("/", 1)[0]
            allowed_files.update({model_name, notice_name})
            allowed_dirs.add(notice_dir)
            data = _tar_file_bytes(archive, model_name)
            if hashlib.sha256(data).hexdigest() != model["sha256"]:
                raise ValueError(
                    f"Checksum mismatch for {model['id']} in standard sdist"
                )
            if gzip.decompress(data)[:8] != b"EGTR\x00\x00\x00\x07":
                raise ValueError(
                    f"Invalid v7 marker/version for {model['id']} in standard sdist"
                )
        files = {member.name for member in archive.getmembers() if member.isfile()}
        directories = {member.name for member in archive.getmembers() if member.isdir()}
        if files != allowed_files or directories != allowed_dirs:
            raise ValueError(
                f"Standard sdist allowlist mismatch; unexpected files={sorted(files - allowed_files)}, "
                f"missing files={sorted(allowed_files - files)}, "
                f"unexpected dirs={sorted(directories - allowed_dirs)}, "
                f"missing dirs={sorted(allowed_dirs - directories)}"
            )
        executable = [
            member.name
            for member in archive.getmembers()
            if member.isfile() and member.mode & 0o111
        ]
        if executable:
            raise ValueError(
                f"Standard sdist contains executable members: {executable}"
            )


def _verify_fresh_install(main_wheel: Path, standard_wheel: Path) -> None:
    managed_temp = REPOSITORY / "build" / "python" / "tmp"
    managed_temp.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(
        prefix="radixor-wheel-install-", dir=managed_temp
    ) as temporary:
        environment = Path(temporary) / "venv"
        subprocess.run([sys.executable, "-m", "venv", str(environment)], check=True)
        scripts = "Scripts" if os.name == "nt" else "bin"
        python = environment / scripts / ("python.exe" if os.name == "nt" else "python")
        if "radixor_models_standard-0.0.0-" in standard_wheel.name:
            subprocess.run(
                [
                    str(python),
                    "-m",
                    "pip",
                    "install",
                    "--no-index",
                    "--no-deps",
                    str(standard_wheel),
                    str(main_wheel),
                ],
                check=True,
            )
        else:
            subprocess.run(
                [
                    str(python),
                    "-m",
                    "pip",
                    "install",
                    "--no-index",
                    "--find-links",
                    str(main_wheel.parent),
                    "--find-links",
                    str(standard_wheel.parent),
                    "radixor",
                ],
                check=True,
            )
        result = subprocess.run(
            [
                str(python),
                "-c",
                "from radixor import Stemmer; print(Stemmer('en').stem('running'))",
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        if result.stdout.strip() != "run":
            raise ValueError(f"Unexpected installed model result: {result.stdout!r}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--main-wheel-dir", type=Path)
    parser.add_argument("--main-sdist-dir", type=Path)
    parser.add_argument("--standard-dir", type=Path)
    parser.add_argument("--main-version", default=MAIN_VERSION)
    parser.add_argument("--standard-version", default=STANDARD_VERSION)
    parser.add_argument("--skip-install", action="store_true")
    args = parser.parse_args()

    _assert_no_repository_temp_gzip()
    if (args.main_wheel_dir is None) != (args.main_sdist_dir is None):
        parser.error("--main-wheel-dir and --main-sdist-dir must be used together")
    if args.main_wheel_dir is None and args.standard_dir is None:
        parser.error("at least one distribution directory must be provided")

    main_wheel = None
    standard_wheel = None
    if args.main_wheel_dir is not None:
        main_wheel = _one(args.main_wheel_dir, "radixor-*.whl")
        main_sdist = _one(args.main_sdist_dir, "radixor-*.tar.gz")
        _verify_main_wheel(main_wheel, args.main_version)
        _verify_main_sdist(main_sdist, args.main_version)
    if args.standard_dir is not None:
        standard_wheel = _one(args.standard_dir, "radixor_models_standard-*.whl")
        standard_sdist = _one(args.standard_dir, "radixor_models_standard-*.tar.gz")
        _verify_standard_wheel(standard_wheel, args.standard_version)
        _verify_standard_sdist(standard_sdist, args.standard_version)
    if not args.skip_install and main_wheel is not None and standard_wheel is not None:
        _verify_fresh_install(main_wheel, standard_wheel)
    print("verified requested Radixor distributions")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
