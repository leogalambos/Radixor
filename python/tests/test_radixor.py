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


# TrieBuilder: unlock, modify, and rewrite a compiled dictionary.


_ROUNDTRIP_DICT = [
    "run\trunning\truns\tran",
    "cat\tcats",
    "walk\twalking\twalks\twalked",
]
_ROUNDTRIP_WORDS = [
    "running", "runs", "ran", "cats", "walking",
    "walked", "run", "cat", "walk", "unknownzzz",
]


def test_trie_builder_textual_roundtrip_is_faithful(tmp_path: Path):
    from radixor import TrieBuilder

    path = _write_gz_dict(_ROUNDTRIP_DICT, tmp_path)
    base = Stemmer(path=path, backward=True)
    rebuilt = TrieBuilder(path=path, backward=True).build()
    assert rebuilt.stem_batch(_ROUNDTRIP_WORDS) == base.stem_batch(_ROUNDTRIP_WORDS)


def test_trie_builder_compiled_roundtrip_is_faithful(tmp_path: Path):
    from radixor import TrieBuilder

    src = _write_gz_dict(_ROUNDTRIP_DICT, tmp_path)
    compiled = str(tmp_path / "model.rxc")
    radixor.compile(src, compiled, backward=True)

    base = Stemmer(compiled=compiled)
    rebuilt = TrieBuilder(compiled=compiled).build()
    assert rebuilt.stem_batch(_ROUNDTRIP_WORDS) == base.stem_batch(_ROUNDTRIP_WORDS)


def test_trie_builder_add_new_pair_takes_effect(tmp_path: Path):
    from radixor import TrieBuilder

    path = _write_gz_dict(_ROUNDTRIP_DICT, tmp_path)
    builder = TrieBuilder(path=path, backward=True)
    builder.add("gitlab", "git")
    stemmer = builder.build()

    # New pair on a fresh key is applied; existing rules are untouched.
    assert stemmer.stemWord("gitlab") == "git"
    assert stemmer.stem_batch(["running", "cats", "walked"]) == ["run", "cat", "walk"]


def test_trie_builder_add_many_and_chaining(tmp_path: Path):
    from radixor import TrieBuilder

    path = _write_gz_dict(_ROUNDTRIP_DICT, tmp_path)
    builder = TrieBuilder(path=path, backward=True)
    returned = builder.add("gitlab", "git").add_many([("foobar", "foo"), ("qux", "quux")])
    assert returned is builder

    stemmer = builder.build()
    assert stemmer.stemWord("gitlab") == "git"
    assert stemmer.stemWord("foobar") == "foo"
    assert stemmer.stemWord("qux") == "quux"


def test_trie_builder_save_reload_roundtrip(tmp_path: Path):
    import gzip as _gzip

    from radixor import TrieBuilder

    path = _write_gz_dict(_ROUNDTRIP_DICT, tmp_path)
    out = str(tmp_path / "custom.rxc")
    TrieBuilder(path=path, backward=True).add("gitlab", "git").save(out)

    # The written artifact is a gzip-wrapped Java-interoperable EGTR v7 stream.
    with _gzip.open(out, "rb") as fh:
        assert fh.read(4) == b"EGTR"

    reloaded = Stemmer(compiled=out)
    assert reloaded.stemWord("gitlab") == "git"
    assert reloaded.stem_batch(["running", "cats", "walked"]) == ["run", "cat", "walk"]


def test_trie_builder_to_bytes_from_bytes_roundtrip(tmp_path: Path):
    from radixor import TrieBuilder

    path = _write_gz_dict(_ROUNDTRIP_DICT, tmp_path)
    data = TrieBuilder(path=path, backward=True).add("gitlab", "git").to_bytes()
    assert data[:2] == b"\x1f\x8b"  # gzip framing

    rebuilt = TrieBuilder.from_bytes(data).build()
    assert rebuilt.stemWord("gitlab") == "git"
    assert rebuilt.stem_batch(["running", "cats", "walked"]) == ["run", "cat", "walk"]


def test_stemmer_to_builder_reopens_source(tmp_path: Path):
    path = _write_gz_dict(_ROUNDTRIP_DICT, tmp_path)
    stemmer = Stemmer(path=path, backward=True)
    custom = stemmer.to_builder().add("gitlab", "git").build()
    assert custom.stemWord("gitlab") == "git"
    assert custom.stem("running") == "run"


def test_trie_builder_build_returns_configurable_stemmer(tmp_path: Path):
    from radixor import TrieBuilder

    path = _write_gz_dict(_ROUNDTRIP_DICT, tmp_path)
    stemmer = TrieBuilder(path=path, backward=True).build(cache_size=5)
    assert stemmer.maxCacheSize == 5
    # Resizing the cache rebuilds the core from the same builder.
    stemmer.maxCacheSize = 0
    assert stemmer.maxCacheSize == 0
    assert stemmer.stem("running") == "run"


def test_trie_builder_build_is_an_immutable_snapshot(tmp_path: Path):
    from radixor import TrieBuilder

    path = _write_gz_dict(_ROUNDTRIP_DICT, tmp_path)
    builder = TrieBuilder(path=path, backward=True)
    stemmer = builder.add("gitlab", "git").build(cache_size=5, lookup="last")

    builder.set("gitlab", "lab")
    stemmer.maxCacheSize = 0

    assert stemmer.stemWord("gitlab") == "git"
    assert builder.build(lookup="last").stemWord("gitlab") == "lab"


def test_lookup_last_makes_shadowed_pair_effective():
    # The standard English model has a robust "-s" generalization that shadows a
    # custom "kubernetes -> kube" pair added through it. (A tiny synthetic dict
    # cannot reproduce this: adding a long branch breaks the uniform-subtree
    # contraction that forms the accepting leaf in the first place.)
    builder = Stemmer("en").to_builder()
    builder.add("kubernetes", "kube")

    first = builder.build(lookup="first")
    last = builder.build(lookup="last")

    # 'first' follows the shallow -s generalization; 'last' honors the specific pair.
    assert first.stemWord("kubernetes") == "kubernete"
    assert last.stemWord("kubernetes") == "kube"
    # Generalization and existing rules stay intact under 'last'.
    assert last.stem_batch(["dogs", "cats", "running", "walked"]) == [
        "dog", "cat", "run", "walk",
    ]


def test_lookup_all_collects_candidates_most_specific_first():
    builder = Stemmer("en").to_builder()
    builder.add("kubernetes", "kube")
    stemmer = builder.build(lookup="all")

    candidates = stemmer.stem_all("kubernetes")
    # Most specific (the custom pair) first, general -s rule after it.
    assert candidates[0] == "kube"
    assert "kubernete" in candidates
    assert candidates.index("kube") < candidates.index("kubernete")


def test_lookup_first_and_last_agree_on_standard_model():
    first = Stemmer("en", lookup="first")
    last = Stemmer("en", lookup="last")
    words = ["running", "walked", "cats", "organized", "flies", "happiness", "studies"]
    # Standard compiled models have childless contracted leaves, so the two
    # policies are indistinguishable there — existing benchmarks are unaffected.
    assert first.stem_batch(words) == last.stem_batch(words)


def test_invalid_lookup_mode_rejected(tmp_path: Path):
    path = _write_gz_dict(["cat\tcats"], tmp_path)
    with pytest.raises(ValueError):
        Stemmer(path=path, lookup="bogus")


# Installed standard-model smoke test.


def test_installed_english_compiled_model():
    s = Stemmer("en")
    assert s.stem_batch(["running", "walked", "cats"]) == ["run", "walk", "cat"]


def test_trie_builder_unlocks_installed_english_model():
    from radixor import TrieBuilder

    base = Stemmer("en")
    words = ["running", "walked", "cats", "organized", "happiness", "flies"]
    # Unlocking and recompiling the standard compiled model is faithful.
    rebuilt = base.to_builder().build()
    assert rebuilt.stem_batch(words) == base.stem_batch(words)


# TrieBuilder value-update operations.


def test_add_default_dominant_protects_overstemmed_word():
    # The English model over-stems "windows" -> "window" via the -s rule.
    # add() defaults to making the rule dominant, so under "last" it wins while
    # the alternative stays visible in stem_all("all").
    builder = Stemmer("en").to_builder().add("windows", "windows")
    assert builder.build(lookup="last").stemWord("windows") == "windows"
    assert builder.build(lookup="all").stem_all("windows") == ["windows", "window"]
    # Other -s words are unaffected.
    assert builder.build(lookup="last").stem_batch(["cats", "dogs", "running"]) == [
        "cat", "dog", "run",
    ]


def test_add_count_is_raw_weight():
    low = Stemmer("en").to_builder()
    low.add("windows", "windows", count=1)     # weight 1 loses to the existing rule
    high = Stemmer("en").to_builder()
    high.add("windows", "windows", count=5)     # weight 5 out-ranks it
    assert low.build(lookup="last").stemWord("windows") == "window"
    assert high.build(lookup="last").stemWord("windows") == "windows"


def test_add_only_if_absent_never_overrides():
    builder = Stemmer("en").to_builder()
    builder.add("windows", "windows", only_if_absent=True)   # "windows" has a rule -> untouched
    builder.add("zzgadget", "gadget", only_if_absent=True)    # fresh word -> stored
    stemmer = builder.build(lookup="last")
    assert stemmer.stemWord("windows") == "window"
    assert stemmer.stemWord("zzgadget") == "gadget"


def test_set_replaces_alternatives():
    builder = Stemmer("en").to_builder().set("windows", "windows")
    assert builder.build(lookup="last").stemWord("windows") == "windows"
    # set() discards the prior candidate, unlike add().
    assert builder.build(lookup="all").stem_all("windows") == ["windows"]


def test_remove_drops_a_custom_rule():
    builder = Stemmer("en").to_builder().add("gitlab", "git")
    assert builder.build(lookup="last").stemWord("gitlab") == "git"
    builder.remove("gitlab")
    assert builder.build(lookup="last").stemWord("gitlab") == "gitlab"


def test_add_rejects_non_positive_count():
    with pytest.raises(ValueError):
        Stemmer("en").to_builder().add("a", "b", count=0)


def test_add_rejects_frequency_overflow():
    builder = Stemmer("en").to_builder()
    builder.add("zzoverflow", "overflow", count=2_147_483_647)
    with pytest.raises(OverflowError):
        builder.add("zzoverflow", "overflow", count=1)


def test_remove_can_delete_contracted_generalization(tmp_path: Path):
    source = _write_gz_dict(
        [
            "s\tas\tbs\tcs\tds\tes\tfs",
            "qq\tax\tbx\tcx\tdx\tex\tfx",
        ],
        tmp_path,
    )
    compiled = str(tmp_path / "contracted.rxc")
    radixor.compile(source, compiled, store_original=False)

    builder = radixor.TrieBuilder(path=compiled, store_original=False)
    builder.remove("s")
    stemmer = builder.build(lookup="first")

    assert stemmer.stemWord("zs") == "zs"
    assert stemmer.stemWord("zx") == "qq"


def test_update_operations_are_chainable():
    from radixor import TrieBuilder

    builder = Stemmer("en").to_builder()
    returned = (
        builder.add("gitlab", "git")
        .set("windows", "windows")
        .add_many([("foo", "bar")])
        .remove("gitlab")
    )
    assert returned is builder
    assert isinstance(builder, TrieBuilder)
