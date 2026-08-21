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

"""Regression checks for the Python release ABI contract."""

from __future__ import annotations

import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

import assemble_release

TEST_VERSION = "1.2.3"


def test_native_release_uses_python_310_stable_abi() -> None:
    assert assemble_release.NATIVE_ABI_TAG == "cp310-abi3"


def test_native_release_recognizes_all_platform_wheels() -> None:
    predicates = assemble_release._native_wheel_predicates(TEST_VERSION)
    names = {
        "linux-x86_64": f"radixor-{TEST_VERSION}-cp310-abi3-manylinux_2_34_x86_64.whl",
        "linux-aarch64": f"radixor-{TEST_VERSION}-cp310-abi3-manylinux_2_34_aarch64.whl",
        "macos-universal2": f"radixor-{TEST_VERSION}-cp310-abi3-macosx_10_12_universal2.whl",
        "windows-x86_64": f"radixor-{TEST_VERSION}-cp310-abi3-win_amd64.whl",
    }

    for platform, name in names.items():
        assert predicates[platform](name)


def test_native_release_rejects_obsolete_python_39_abi_tag() -> None:
    predicates = assemble_release._native_wheel_predicates(TEST_VERSION)
    obsolete = f"radixor-{TEST_VERSION}-cp39-abi3-manylinux_2_34_x86_64.whl"

    assert not predicates["linux-x86_64"](obsolete)
