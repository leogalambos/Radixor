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

"""Runtime tests for read-only lookup policies in the C extension."""

from __future__ import annotations

import gzip
import struct
from pathlib import Path

import pytest

radixor_c = pytest.importorskip("radixor_c")


def _i32(value: int) -> bytes:
    """Encode one signed Java ``DataOutputStream.writeInt`` value."""
    return struct.pack(">i", value)


def _u16(value: int) -> bytes:
    """Encode one unsigned Java ``DataOutputStream.writeChar`` value."""
    return struct.pack(">H", value)


def _java_utf(text: str) -> bytes:
    """Encode ASCII fixture text in Java ``writeUTF`` framing.

    The fixture intentionally uses only non-NUL ASCII, for which Java modified
    UTF-8 is byte-identical to UTF-8.
    """
    encoded = text.encode("ascii")
    return _u16(len(encoded)) + encoded


def _write_lookup_fixture(path: Path) -> None:
    """Write a minimal valid version 7 DAG with an accepting node and child.

    The backward path is ``root -s-> general -x-> exact``. The general ``Da``
    patch removes the trailing ``s``; the exact ``Na`` patch preserves ``xs``.
    Thus ``first``, ``last``, and ``all`` have distinct observable results.
    """
    metadata = (
        "radixor.metadata.v1\n"
        "formatVersion=7\n"
        "traversalDirection=BACKWARD\n"
        "reductionMode=MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS\n"
        "dominantWinnerMinPercent=75\n"
        "dominantWinnerOverSecondRatio=3\n"
        "contractUniformSubtrees=true\n"
        "diacriticProcessingMode=AS_IS\n"
        "caseProcessingMode=AS_IS\n"
    )

    stream = bytearray()
    stream += _i32(0x45475452)  # stream magic
    stream += _i32(7)  # format version
    stream += _i32(3)  # node count
    stream += _i32(0)  # root id
    stream += _java_utf(metadata)
    stream += _i32(2) + _java_utf("Da") + _java_utf("Na")

    stream += b"\x00" + _i32(1) + _u16(ord("s")) + _i32(1) + _i32(0)
    stream += b"\x01" + _i32(1) + _u16(ord("x")) + _i32(2)
    stream += _i32(1) + _i32(0) + _i32(3)
    stream += b"\x00" + _i32(0) + _i32(1) + _i32(1) + _i32(1)

    path.write_bytes(gzip.compress(bytes(stream), mtime=0))


def test_lookup_modes_select_expected_nodes(tmp_path: Path) -> None:
    """Verify shallow, specific, and collect-all policies on one trie path."""
    model = tmp_path / "lookup-modes.rxc"
    _write_lookup_fixture(model)

    first = radixor_c.Stemmer(compiled=str(model), lowercase=False, lookup="first")
    last = radixor_c.Stemmer(compiled=str(model), lowercase=False, lookup="last")
    all_matches = radixor_c.Stemmer(
        compiled=str(model), lowercase=False, lookup="all"
    )

    assert first.stemWord("xs") == "x"
    assert last.stemWord("xs") == "xs"
    assert all_matches.stem_all("xs") == ["xs", "x"]
    assert last.stemWord("ys") == "y"


def test_invalid_lookup_mode_is_rejected(tmp_path: Path) -> None:
    """Verify that unsupported policy names fail before runtime lookup."""
    model = tmp_path / "lookup-modes.rxc"
    _write_lookup_fixture(model)

    with pytest.raises(ValueError, match="invalid lookup mode"):
        radixor_c.Stemmer(compiled=str(model), lowercase=False, lookup="deepest")
