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

from __future__ import annotations

import gzip
import hashlib
import json
from contextlib import nullcontext
from importlib import resources
from pathlib import Path

import pytest

import radixor
import radixor_models_standard

REPOSITORY = Path(__file__).resolve().parents[2]

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


def _manifest() -> dict:
    ref = resources.files("radixor_models_standard").joinpath("manifest.json")
    return json.loads(ref.read_text(encoding="utf-8"))


def test_standard_manifest_model_set_versions_and_license():
    manifest = _manifest()
    expected_catalog_version = (
        REPOSITORY / "models/catalog-version.txt"
    ).read_text(encoding="utf-8").strip()
    expected_model_versions = {
        model_id: (REPOSITORY / "models" / model_id / "model-version.txt")
        .read_text(encoding="utf-8")
        .strip()
        for model_id in EXPECTED_MODEL_IDS
    }
    assert manifest["catalog_version"] == expected_catalog_version
    assert radixor_models_standard.CATALOG_VERSION == expected_catalog_version
    assert manifest["distribution_version"] == "0.0.0"
    assert radixor_models_standard.__version__ == manifest["distribution_version"]
    assert manifest["format"] == {"compression": "gzip", "magic": "EGTR", "version": 7}
    assert {model["id"] for model in manifest["models"]} == EXPECTED_MODEL_IDS
    assert "pl-pl-polimorf" not in {model["id"] for model in manifest["models"]}
    assert {
        model["id"]: model["version"] for model in manifest["models"]
    } == expected_model_versions
    assert {model["provenance"]["license"] for model in manifest["models"]} == {
        "CC-BY-SA-3.0"
    }


def test_standard_artifact_checksums_and_v7_headers():
    root = resources.files("radixor_models_standard")
    for model in _manifest()["models"]:
        data = root.joinpath("models").joinpath(f"{model['id']}.rxc").read_bytes()
        assert hashlib.sha256(data).hexdigest() == model["sha256"]
        assert gzip.decompress(data)[:8] == b"EGTR\x00\x00\x00\x07"


def test_missing_standard_data_package_is_actionable(monkeypatch):
    original_files = radixor.importlib.resources.files

    def missing(package: str):
        if package == radixor._STANDARD_PACKAGE:
            raise ModuleNotFoundError(package)
        return original_files(package)

    monkeypatch.setattr(radixor.importlib.resources, "files", missing)
    with pytest.raises(
        ModuleNotFoundError, match="pip install radixor-models-standard"
    ):
        radixor.Stemmer("en")


def test_missing_standard_model_is_actionable():
    with pytest.raises(FileNotFoundError, match="not in the standard Radixor catalog"):
        radixor.Stemmer("zz-zz-default")


@pytest.mark.parametrize(
    ("mutation", "message"),
    [
        (
            lambda manifest: manifest.update(catalog_version="not-a-catalog"),
            "incompatible or corrupt",
        ),
        (
            lambda manifest: manifest.update(format={"magic": "bad"}),
            "incompatible or corrupt",
        ),
        (
            lambda manifest: manifest.update(distribution_version="1.0.2"),
            "incompatible or corrupt",
        ),
    ],
)
def test_incompatible_manifest_is_actionable(
    tmp_path: Path, monkeypatch, mutation, message
):
    manifest = _manifest()
    mutation(manifest)
    (tmp_path / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    monkeypatch.setattr(radixor.importlib.resources, "files", lambda package: tmp_path)
    with pytest.raises(RuntimeError, match=message):
        radixor.Stemmer("en")


def test_new_catalog_release_remains_runtime_compatible(tmp_path: Path, monkeypatch):
    manifest = _manifest()
    manifest["catalog_version"] = "2027.1"
    (tmp_path / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    monkeypatch.setattr(radixor.importlib.resources, "files", lambda package: tmp_path)

    assert radixor._load_standard_manifest()["catalog_version"] == "2027.1"


def test_checksum_failure_is_detected(tmp_path: Path, monkeypatch):
    manifest = _manifest()
    (tmp_path / "models").mkdir()
    (tmp_path / "models" / "us-uk-default.rxc").write_bytes(b"not the model")
    (tmp_path / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    monkeypatch.setattr(radixor.importlib.resources, "files", lambda package: tmp_path)
    monkeypatch.setattr(radixor.importlib.resources, "as_file", nullcontext)
    with pytest.raises(RuntimeError, match="SHA-256 validation"):
        radixor.Stemmer("en")


@pytest.mark.parametrize(
    ("stream_header", "message"),
    [
        (b"NOPE\x00\x00\x00\x07", "EGTR format marker"),
        (b"EGTR\x00\x00\x00\x08", "unsupported compiled format v8"),
    ],
)
def test_format_marker_and_version_are_validated(
    tmp_path: Path, monkeypatch, stream_header: bytes, message: str
):
    manifest = _manifest()
    model = next(item for item in manifest["models"] if item["id"] == "us-uk-default")
    data = gzip.compress(stream_header, mtime=0)
    model["sha256"] = hashlib.sha256(data).hexdigest()
    (tmp_path / "models").mkdir()
    (tmp_path / "models" / "us-uk-default.rxc").write_bytes(data)
    (tmp_path / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    monkeypatch.setattr(radixor.importlib.resources, "files", lambda package: tmp_path)
    monkeypatch.setattr(radixor.importlib.resources, "as_file", nullcontext)
    with pytest.raises(RuntimeError, match=message):
        radixor.Stemmer("en")


def test_invalid_model_id_is_rejected_before_resource_lookup():
    with pytest.raises(ValueError, match="Invalid Radixor model ID"):
        radixor.Stemmer("../us-uk-default")
