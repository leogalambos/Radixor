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

"""Deterministic benchmark corpus, mirroring the Java JMH LanguageBenchmarkCorpus.

The timing workload contains dictionary forms that differ from their canonical
root. Each field is normalized with ``strip().lower()``, retained when it needs
stemming, and repeated in stable dictionary order until the corpus reaches
``MINIMUM_TIMING_TOKEN_COUNT``. This mirrors
``LanguageBenchmarkCorpus.buildChangedTimingCorpus`` and supplies the same
token sequence to every benchmark engine.
"""

from __future__ import annotations

import gzip
from pathlib import Path

MINIMUM_TIMING_TOKEN_COUNT = 5_000


def _normalize(token: str) -> str:
    # Java: token.trim().toLowerCase(Locale.ROOT)
    return token.strip().lower()


def _contains_whitespace(token: str) -> bool:
    return any(ch.isspace() for ch in token)


def read_changed_tokens(dict_gz_path: str | Path) -> list[str]:
    """Return the changed-token list (token != root) in dictionary order.

    Not yet padded to the timing minimum; see :func:`build_timing_corpus`.
    """
    tokens: list[str] = []
    with gzip.open(dict_gz_path, "rt", encoding="utf-8") as fh:
        for line in fh:
            if not line or line.isspace():
                continue
            # Match the Java benchmark: only a leading marker starts a comment;
            # inline markers remain part of the dictionary field.
            if line.startswith("#") or line.startswith("//"):
                continue
            fields = line.split("\t")
            if not fields:
                continue
            root = _normalize(fields[0])
            if not root or _contains_whitespace(root):
                continue
            for field in fields:
                token = _normalize(field)
                if not token or _contains_whitespace(token):
                    continue
                if token != root:  # changed-token workload
                    tokens.append(token)
    return tokens


def build_timing_corpus(
    dict_gz_path: str | Path,
    minimum_token_count: int = MINIMUM_TIMING_TOKEN_COUNT,
) -> list[str]:
    """Return the padded changed-token timing corpus (>= minimum_token_count)."""
    changed = read_changed_tokens(dict_gz_path)
    if not changed:
        raise ValueError(f"No changed-token corpus tokens available in {dict_gz_path}")
    if len(changed) >= minimum_token_count:
        return changed
    # Repeat in stable order to reach the minimum, exactly like the Java code.
    out: list[str] = []
    n = len(changed)
    for i in range(minimum_token_count):
        out.append(changed[i % n])
    return out
