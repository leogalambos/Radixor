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

"""Regression tests for Python release-archive metadata validation."""

from __future__ import annotations

import sys
from email.message import Message
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

from build_standard_distribution import (  # noqa: E402
    _ignore_build_artifacts,
    _validate_generated_project,
)
from verify_distributions import _assert_main_dependency  # noqa: E402


def test_standard_model_source_tree_contains_no_generated_payload() -> None:
    source = Path(__file__).resolve().parents[1] / "models-standard"
    package = source / "radixor_models_standard"

    assert not (package / "manifest.json").exists()
    assert not list((package / "models").glob("*.rxc"))
    assert not list((package / "notices").glob("*/NOTICE-model-data.txt"))


def test_standard_model_build_rejects_source_skeleton() -> None:
    source = Path(__file__).resolve().parents[1] / "models-standard"

    with pytest.raises(ValueError, match="manifest is missing"):
        _validate_generated_project(source)


def test_standard_model_build_ignores_local_build_state() -> None:
    names = [
        "build",
        "dist",
        "radixor_models_standard.egg-info",
        "__pycache__",
        "module.pyc",
        "module.pyo",
        "manifest.json",
    ]

    assert _ignore_build_artifacts("unused", names) == set(names[:-1])


def test_native_distribution_requires_compatible_standard_models() -> None:
    metadata = Message()
    metadata["Requires-Dist"] = "radixor-models-standard >=1.0, <2.0"

    _assert_main_dependency(metadata, "radixor-4.1.0.tar.gz")


def test_native_distribution_rejects_missing_standard_models() -> None:
    metadata = Message()

    with pytest.raises(ValueError, match="standard-model dependency"):
        _assert_main_dependency(metadata, "radixor-4.1.0.tar.gz")
