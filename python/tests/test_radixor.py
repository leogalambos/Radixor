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

"""Acceptance tests for the radixor Python extension.

Run after building the extension:

    cd python/
    pip install maturin pytest
    maturin develop --release
    pytest -q

The synthetic tests are self-contained and deterministic (no network, no
bundled data).
"""

from __future__ import annotations

import gzip
import inspect
from pathlib import Path
import pytest

import radixor
from radixor import Stemmer


def _write_gz_dict(lines: list[str], tmp_path: Path) -> str:
    """Write a gzipped TSV dictionary into pytest's temporary directory."""
    path = tmp_path / "dictionary.gz"
    with gzip.open(path, "wt", encoding="utf-8", newline="\n") as gz:
        gz.write("\n".join(lines))
    return str(path)


# Synthetic, deterministic pipeline tests.


def test_backward_suffix_stemming_roundtrip(tmp_path: Path):
    # stem<TAB>variant... ; backward (suffix) stemming.
    dict_lines = [
        "run\trunning\truns\tran",
        "cat\tcats",
        "walk\twalking\twalks\twalked",
    ]
    path = _write_gz_dict(dict_lines, tmp_path)
    s = Stemmer(path=path, backward=True, store_original=True)

    # Every listed variant must stem back to its canonical stem.
    assert s.stem("running") == "run"
    assert s.stem("runs") == "run"
    assert s.stem("ran") == "run"
    assert s.stem("cats") == "cat"
    assert s.stem("walking") == "walk"
    assert s.stem("walked") == "walk"

    # store_original: the stem itself is recognised (no-op patch).
    assert s.stem("run") == "run"
    assert s.stem("cat") == "cat"


def test_store_original_controls_bare_stem_identity(tmp_path: Path):
    # With a single rule and store_original=True, the stem maps to itself via
    # the no-op patch, and the "cat" vs "cats" terminals carry different values
    # so the trie does NOT collapse to a universal rule.
    path = _write_gz_dict(["cat\tcats"], tmp_path)
    s_keep = Stemmer(path=path, backward=True, store_original=True)
    assert s_keep.stem("cats") == "cat"
    assert s_keep.stem("cat") == "cat"

    # With store_original=False, only the single rule cats->cat is present.
    # Radixor's always-on uniform-subtree contraction generalizes that lone
    # rule to ALL input (this is the intended generalization behavior), so the
    # bare stem is rewritten by the same delete-one-suffix rule.
    s_drop = Stemmer(path=path, backward=True, store_original=False)
    assert s_drop.stem("cats") == "cat"
    assert s_drop.stem("cat") == "ca"  # generalized: delete final char
    assert s_drop.stem("dogs") == "dog"  # rule applies to unseen input too


def test_unknown_word_returns_none(tmp_path: Path):
    path = _write_gz_dict(["cat\tcats"], tmp_path)
    s = Stemmer(path=path, backward=True)
    assert s.stem("zzzunknown") is None


def test_pystemmer_scalar_api_returns_original_word_for_unknown(tmp_path: Path):
    path = _write_gz_dict(["cat\tcats"], tmp_path)
    s = Stemmer(path=path, backward=True)

    assert s.stemWord("cats") == "cat"
    assert s.stemWord("ZzZUnknown") == "ZzZUnknown"
    # The original Radixor API keeps its existing missing-value contract.
    assert s.stem("ZzZUnknown") is None


def test_pystemmer_batch_api_returns_original_words_for_unknowns(tmp_path: Path):
    path = _write_gz_dict(["run\trunning\truns", "cat\tcats"], tmp_path)
    s = Stemmer(path=path, backward=True)
    words = ["running", "Nope", "cats", "QzXqZx"]

    assert s.stemWords(words) == ["run", "Nope", "cat", "QzXqZx"]
    assert s.stem_batch(words) == ["run", None, "cat", None]


def test_pystemmer_batch_cache_does_not_change_original_api(tmp_path: Path):
    path = _write_gz_dict(["cat\tcats"], tmp_path)
    s = Stemmer(path=path, backward=True, cache_size=100)

    assert s.stemWords(["Unknown", "cats", "Unknown"]) == ["Unknown", "cat", "Unknown"]
    assert s.stem_batch(["Unknown", "cats", "Unknown"]) == [None, "cat", None]


def test_pystemmer_compatible_positional_max_cache_size(tmp_path: Path):
    path = _write_gz_dict(["cat\tcats"], tmp_path)
    s = Stemmer("english", 7, path=path)
    assert s.maxCacheSize == 7

    s = Stemmer("english", 8, path=path, cache_size=4)
    assert s.maxCacheSize == 8

    with pytest.raises(TypeError):
        Stemmer("english", "7", path=path)

    with pytest.raises(ValueError):
        Stemmer("english", -1, path=path)


def test_max_cache_size_property_is_compatible_with_pystemmer(tmp_path: Path):
    path = _write_gz_dict(["cat\tcats"], tmp_path)
    s = Stemmer(path=path, backward=True, cache_size=10)
    assert s.maxCacheSize == 10

    s.maxCacheSize = 0
    assert s.maxCacheSize == 0

    s.maxCacheSize = 7
    assert s.maxCacheSize == 7

    with pytest.raises(TypeError):
        s.maxCacheSize = "0"

    with pytest.raises(TypeError):
        s.maxCacheSize = 1.0

    with pytest.raises(ValueError):
        s.maxCacheSize = -1


def test_wrapper_forwards_default_cache_size_and_zero_opt_out(monkeypatch):
    import radixor

    constructor_calls = []

    class RecordingStemmerCore:
        def __init__(self, *args):
            constructor_calls.append(args)

    monkeypatch.setattr(radixor, "StemmerCore", RecordingStemmerCore)

    radixor.Stemmer(path="model.rxc")
    radixor.Stemmer(path="model.rxc", cache_size=0)

    assert constructor_calls[0][-1] == 10_000
    assert constructor_calls[1][-1] == 0


def test_native_constructor_default_cache_size():
    from radixor._radixor import StemmerCore

    assert inspect.signature(StemmerCore).parameters["cache_size"].default == 10_000


def test_pystemmer_language_name_alias():
    import radixor as StemmerModule

    # Only the dependency/import line changes from PyStemmer's conventional
    # ``import Stemmer; Stemmer.Stemmer("english")`` usage.
    s = StemmerModule.Stemmer("english")
    assert s.stemWord("running") == "run"
    assert s.stemWords(["running", "unknown_word"]) == ["run", "unknown_word"]


def test_case_is_lowercased(tmp_path: Path):
    path = _write_gz_dict(["cat\tcats"], tmp_path)
    s = Stemmer(path=path, backward=True)
    assert s.stem("CATS") == "cat"
    assert s.stem("Cats") == "cat"


def test_batch_matches_scalar(tmp_path: Path):
    path = _write_gz_dict(["run\trunning\truns", "cat\tcats"], tmp_path)
    s = Stemmer(path=path, backward=True)
    words = ["running", "runs", "cats", "nope", "run"]
    assert s.stem_batch(words) == [s.stem(w) for w in words]


def test_compile_roundtrip_matches_from_text(tmp_path: Path):
    import os

    import radixor

    dict_lines = [
        "run\trunning\truns\tran",
        "cat\tcats",
        "walk\twalking\twalks\twalked",
    ]
    src = _write_gz_dict(dict_lines, tmp_path)
    out = src + ".rxc"
    radixor.compile(src, out, backward=True)

    from_text = Stemmer(path=src, backward=True)
    from_compiled = Stemmer(compiled=out)

    words = [
        "running",
        "runs",
        "ran",
        "cats",
        "walking",
        "walked",
        "run",
        "cat",
        "walk",
        "unknownzzz",
    ]
    assert from_compiled.stem_batch(words) == from_text.stem_batch(words)
    # The compiled artifact uses the gzip-wrapped EGTR v7 stream format.
    import gzip

    with gzip.open(out, "rb") as fh:
        assert fh.read(4) == b"EGTR"
    os.unlink(out)


def test_cache_does_not_change_results(tmp_path: Path):
    path = _write_gz_dict(["run\trunning\truns", "cat\tcats"], tmp_path)
    plain = Stemmer(path=path, backward=True, cache_size=0)
    cached = Stemmer(path=path, backward=True, cache_size=1000)
    words = ["running", "runs", "cats", "nope", "run", "running", "cats"]
    assert cached.stem_batch(words) == plain.stem_batch(words)
    # Repeated lookups exercise the cache-hit path.
    assert cached.stem_batch(["running"] * 5) == ["run"] * 5


def test_default_cache_is_shared_across_scalar_and_batch_apis(tmp_path: Path):
    root = "cacheable-root-value"
    variant = "cacheable-root-values"
    path = _write_gz_dict([f"{root}\t{variant}"], tmp_path)
    cached = Stemmer(path=path, backward=True)

    first = cached.stem(variant)
    assert first == root
    assert cached.stem(variant) is first
    assert cached.stemWord(variant) is first
    assert cached.stem_batch([variant])[0] is first
    assert cached.stemWords([variant])[0] is first

    disabled = Stemmer(path=path, backward=True, cache_size=0)
    uncached_first = disabled.stem(variant)
    uncached_second = disabled.stem(variant)
    assert uncached_first == uncached_second == root
    assert uncached_first is not uncached_second


def test_full_cache_keeps_existing_entries_without_admitting_new_ones(tmp_path: Path):
    roots = ("first-cacheable-root", "second-cacheable-root")
    variants = tuple(f"{root}-value" for root in roots)
    path = _write_gz_dict(
        [f"{root}\t{variant}" for root, variant in zip(roots, variants)], tmp_path
    )
    stemmer = Stemmer(path=path, backward=True, cache_size=1)

    first = stemmer.stem(variants[0])
    assert stemmer.stem(variants[0]) is first

    uncached = stemmer.stem(variants[1])
    assert uncached == roots[1]
    assert stemmer.stem(variants[1]) == uncached
    assert stemmer.stem(variants[1]) is not uncached
    assert stemmer.stem(variants[0]) is first


def test_lowercase_false_assumes_prelowered(tmp_path: Path):
    path = _write_gz_dict(["cat\tcats"], tmp_path)
    s = Stemmer(path=path, backward=True, lowercase=False)
    assert s.stem("cats") == "cat"  # already-lowercase input works
    assert s.stem("CATS") is None  # not lowercased -> no match


def test_forward_prefix_stemming(tmp_path: Path):
    # Forward traversal remains available for deliberately prefix-oriented data.
    path = _write_gz_dict(["kitab\talkitab\talkitabu"], tmp_path)
    s = Stemmer(path=path, backward=False, store_original=True)
    assert s.stem("alkitab") == "kitab"
    assert s.stem("alkitabu") == "kitab"
    assert s.stem("kitab") == "kitab"


def test_language_does_not_change_suffix_traversal(tmp_path: Path):
    source = _write_gz_dict(["כתב\tכתבים"], tmp_path)
    compiled = tmp_path / "hebrew.rxc"

    radixor.compile(source, str(compiled), language="he")
    stemmer = Stemmer(compiled=str(compiled))

    assert stemmer.stem("כתבים") == "כתב"


def test_stem_all_returns_candidates(tmp_path: Path):
    path = _write_gz_dict(["run\trunning", "runn\trunning"], tmp_path)
    s = Stemmer(path=path, backward=True, store_original=True)
    alls = s.stem_all("running")
    # "running" maps to both "run" and "runn"; both must be reachable.
    assert set(alls) >= {"run", "runn"}


# Installed standard-model smoke test.


def test_installed_english_compiled_model():
    s = Stemmer("en")
    assert s.stem_batch(["running", "walked", "cats"]) == ["run", "walk", "cat"]
