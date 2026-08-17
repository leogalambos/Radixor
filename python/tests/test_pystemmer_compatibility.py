"""Compatibility-focused tests for PyStemmer 3.1.0-style APIs.

These tests validate Radixor's drop-in compatibility surface for supported
PyStemmer algorithms, cache aliases, byte handling, iterable dispatch and the top-level
``Stemmer`` compatibility module.
"""

from __future__ import annotations

import importlib
import pytest

import radixor


_PY_STEMMER_ALIASES: dict[str, tuple[str, ...]] = {
    "czech": ("czech", "cs", "ces", "cze"),
    "danish": ("danish", "da", "dan"),
    "dutch": ("dutch", "nl", "dut", "nld", "kraaij_pohlmann"),
    "english": ("english", "en", "eng"),
    "finnish": ("finnish", "fi", "fin"),
    "french": ("french", "fr", "fre", "fra"),
    "german": ("german", "de", "ger", "deu"),
    "hungarian": ("hungarian", "hu", "hun"),
    "italian": ("italian", "it", "ita"),
    "norwegian": ("norwegian", "no", "nor"),
    "persian": ("persian", "fa", "fas", "pers"),
    "polish": ("polish", "pl", "pol"),
    "portuguese": ("portuguese", "pt", "por"),
    "russian": ("russian", "ru", "rus"),
    "spanish": ("spanish", "es", "esl", "spa"),
    "swedish": ("swedish", "sv", "swe"),
    "yiddish": ("yiddish", "yi", "yid"),
}

_ALIASES_EXPECTED_ORDER = [
    "czech",
    "danish",
    "dutch",
    "english",
    "finnish",
    "french",
    "german",
    "hungarian",
    "italian",
    "norwegian",
    "persian",
    "polish",
    "portuguese",
    "russian",
    "spanish",
    "swedish",
    "yiddish",
]

_ALIASES_MODEL_IDS: dict[str, str] = {
    "czech": "cs-cz-default",
    "danish": "da-dk-default",
    "dutch": "nl-nl-default",
    "english": "us-uk-default",
    "finnish": "fi-fi-default",
    "french": "fr-fr-default",
    "german": "de-de-default",
    "hungarian": "hu-hu-default",
    "italian": "it-it-default",
    "norwegian": "nb-no-default",
    "persian": "fa-ir-default",
    "polish": "pl-pl-unimorph",
    "portuguese": "pt-pt-default",
    "russian": "ru-ru-default",
    "spanish": "es-es-default",
    "swedish": "sv-se-default",
    "yiddish": "yi-default",
}

_PY_STEMMER_ALIASES_FLAT = [alias for aliases in _PY_STEMMER_ALIASES.values() for alias in aliases]


def test_algorithms_is_deterministic_and_contains_supported_canonical_names() -> None:
    assert radixor.algorithms() == _ALIASES_EXPECTED_ORDER
    assert list(radixor.algorithms()) == list(radixor.algorithms(aliases=False))


def test_algorithms_includes_supported_aliases_and_no_duplicates() -> None:
    all_aliases = radixor.algorithms(True)
    assert all_aliases[: len(_PY_STEMMER_ALIASES_FLAT)] == _PY_STEMMER_ALIASES_FLAT
    assert len(all_aliases) == len(set(all_aliases))


@pytest.mark.parametrize("alias, expected_model", _ALIASES_MODEL_IDS.items())
def test_pystemmer_language_aliases_resolve_to_expected_model(alias: str, expected_model: str):
    stemmer = radixor.Stemmer(alias)
    assert stemmer._model_id == expected_model


@pytest.mark.parametrize("alias", _PY_STEMMER_ALIASES_FLAT)
def test_aliases_from_py_stemmer_modern_alias_set_work(alias: str):
    stemmer = radixor.Stemmer(alias)
    assert stemmer.version() == radixor.version()


def test_unsupported_algorithm_raises_key_error() -> None:
    with pytest.raises(KeyError):
        radixor.Stemmer("porter")
    with pytest.raises(KeyError):
        radixor.Stemmer("turkish")


def test_version_is_exposed_via_module_and_stemmer() -> None:
    assert isinstance(radixor.version(), str)
    assert radixor.version() == radixor.Stemmer.version()


def test_stemword_bytes_roundtrip() -> None:
    stemmer = radixor.Stemmer("english")
    assert stemmer.stemWord("running") == "run"
    assert stemmer.stemWord(b"running") == b"run"
    assert stemmer.stemWord(b"Unknown") == b"Unknown"


def test_stemword_scalar_types_and_payloads() -> None:
    stemmer = radixor.Stemmer("english")
    assert isinstance(stemmer.stemWord("running"), str)
    assert isinstance(stemmer.stemWord(b"running"), bytes)
    assert stemmer.stemWord("") == ""
    assert stemmer.stemWord(b"") == b""


def test_stemword_bytes_malformed_utf8_rejected() -> None:
    stemmer = radixor.Stemmer("english")
    with pytest.raises(UnicodeDecodeError):
        stemmer.stemWord(b"\xff")


def test_stemword_str_unknown_preserved() -> None:
    stemmer = radixor.Stemmer("english")
    assert stemmer.stemWord("UnKnOwN") == "UnKnOwN"


def test_stemword_bytes_unknown_preserved() -> None:
    stemmer = radixor.Stemmer("english")
    original = b"UnKnOwN"
    assert stemmer.stemWord(original) == original


def test_stemwords_accepts_arbitrary_iterables_and_mixed_types():
    stemmer = radixor.Stemmer("english")

    words_list = ["running", b"running", "unknown", b"unknown"]
    assert stemmer.stemWords(words_list) == ["run", b"run", "unknown", b"unknown"]

    words_tuple = ("running", "cats")
    assert stemmer.stemWords(words_tuple) == ["run", "cat"]

    def generator():
        yield "running"
        yield b"cats"

    assert stemmer.stemWords(generator()) == ["run", b"cat"]

    class _Once:
        def __init__(self):
            self._iterated = False

        def __iter__(self):
            if self._iterated:
                raise AssertionError("iterable reused")
            self._iterated = True
            return iter(["running", b"cats"])

    assert stemmer.stemWords(_Once()) == ["run", b"cat"]

    assert stemmer.stemWords([]) == []


def test_stemwords_homogeneous_list_bytes() -> None:
    stemmer = radixor.Stemmer("english")
    assert stemmer.stemWords([b"running", b"cats", b"unknown"]) == [b"run", b"cat", b"unknown"]


def test_stemwords_tuple_str_and_tuple_bytes() -> None:
    stemmer = radixor.Stemmer("english")
    assert stemmer.stemWords(("running", "cats")) == ["run", "cat"]
    assert stemmer.stemWords((b"running", b"cars")) == [b"run", b"cars"]
    assert stemmer.stemWords(("running", b"running")) == ["run", b"run"]


def test_stemwords_rejects_malformed_utf8_bytes() -> None:
    stemmer = radixor.Stemmer("english")
    with pytest.raises(UnicodeDecodeError):
        stemmer.stemWords([b"\xff"])


def test_stemwords_generator_of_bytes() -> None:
    stemmer = radixor.Stemmer("english")
    source = (b"running", b"cars", b"unknown")
    assert stemmer.stemWords((b for b in source)) == [b"run", b"cars", b"unknown"]


def test_stemwords_invalid_scalar_type_rejected() -> None:
    stemmer = radixor.Stemmer("english")
    with pytest.raises(TypeError):
        stemmer.stemWord(123)


def test_stemwords_rejects_invalid_element_type() -> None:
    stemmer = radixor.Stemmer("english")
    with pytest.raises(TypeError):
        stemmer.stemWords([1])


def test_stemwords_empty_tuple_and_generator() -> None:
    stemmer = radixor.Stemmer("english")
    assert stemmer.stemWords(()) == []
    assert list(stemmer.stemWords((b for b in []))) == []


def test_stemwords_preserves_output_order_and_types() -> None:
    stemmer = radixor.Stemmer("english")
    words = ["running", b"cars", "unknown", b"unknown"]
    result = stemmer.stemWords(words)
    assert result == ["run", b"cars", "unknown", b"unknown"]
    assert isinstance(result[0], str)
    assert isinstance(result[1], bytes)
    assert isinstance(result[2], str)
    assert isinstance(result[3], bytes)


def test_stemwords_bytes_custom_one_shot_iterable() -> None:
    stemmer = radixor.Stemmer("english")

    class _OnceBytes:
        def __init__(self) -> None:
            self._used = False

        def __iter__(self):
            if self._used:
                raise AssertionError("iterator reused")
            self._used = True
            return iter((b"running", b"cars"))

    assert stemmer.stemWords(_OnceBytes()) == [b"run", b"cars"]


def test_supported_and_unsupported_algorithm_names():
    assert "porter" not in radixor.algorithms()
    assert "porter" not in radixor.algorithms(True)


def test_stemwords_uses_native_batch_fast_path_for_list_str(monkeypatch):
    calls = {"count": 0}

    original = radixor.StemmerCore.stemWords

    def counting(self, words):
        calls["count"] += 1
        return original(self, words)

    monkeypatch.setattr(radixor.StemmerCore, "stemWords", counting)
    stemmer = radixor.Stemmer("english")
    assert stemmer.stemWords(["running", "cats"]) == ["run", "cat"]
    assert calls["count"] == 1


def test_top_level_compat_module_exports_core_entry_points() -> None:
    compat = importlib.import_module("Stemmer")
    assert hasattr(compat, "Stemmer")
    assert hasattr(compat, "algorithms")
    assert hasattr(compat, "version")
    assert callable(compat.algorithms)
    assert isinstance(compat.version(), str)
    assert compat.algorithms() == radixor.algorithms()
    assert set(getattr(compat, "__all__", ())) == {"Stemmer", "algorithms", "version"}


def test_mixed_case_and_lowercase_toggle_compat_behavior() -> None:
    stemmer_default = radixor.Stemmer("english")
    assert stemmer_default.stemWord("rUnning") == "run"
    assert stemmer_default.stemWords(["rUnning", b"rUnning"]) == ["run", b"run"]

    stemmer_no_lowercase = radixor.Stemmer("english", lowercase=False)
    assert stemmer_no_lowercase.stemWord("rUnning") == "rUnning"
