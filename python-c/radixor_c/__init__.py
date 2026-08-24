"""Python API for the C-backed Radixor stemmer.

Drop-in replacement for ``radixor`` with identical public interface.
The underlying trie engine is a pure CPython C extension instead of Rust/PyO3.

Only pre-compiled ``.rxc`` trie files are supported.
Use ``radixor.compile()`` to compile a TSV dictionary.
"""

from __future__ import annotations

import gzip
import hashlib
import importlib.resources
import importlib.metadata as metadata
import json
import re
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterable, Iterator, Optional, overload

from radixor_c._radixor_c import StemmerCore

_PYSTEMMER_MODEL_MAP: tuple[tuple[str, bool, tuple[str, ...], tuple[str, ...]], ...] = (
    ("cs-cz-default", True, ("czech", "cs", "ces", "cze"), ()),
    ("da-dk-default", True, ("danish", "da", "dan"), ()),
    (
        "nl-nl-default",
        True,
        ("dutch", "nl", "dut", "nld", "kraaij_pohlmann"),
        ("dutch",),
    ),
    ("us-uk-default", True, ("english", "en", "eng"), ()),
    ("fi-fi-default", True, ("finnish", "fi", "fin"), ()),
    ("fr-fr-default", True, ("french", "fr", "fre", "fra"), ()),
    ("de-de-default", True, ("german", "de", "ger", "deu"), ()),
    ("hu-hu-default", True, ("hungarian", "hu", "hun"), ()),
    ("it-it-default", True, ("italian", "it", "ita"), ()),
    ("nb-no-default", True, ("norwegian", "no", "nor"), ("nb",)),
    ("nn-no-default", False, tuple(), ("nn",)),
    ("fa-ir-default", True, ("persian", "fa", "fas", "pers"), ()),
    ("pl-pl-unimorph", True, ("polish", "pl", "pol"), ()),
    ("pt-pt-default", True, ("portuguese", "pt", "por"), ()),
    ("ru-ru-default", True, ("russian", "ru", "rus"), ()),
    ("es-es-default", True, ("spanish", "es", "esl", "spa"), ()),
    ("sv-se-default", True, ("swedish", "sv", "swe"), ()),
    ("yi-default", True, ("yiddish", "yi", "yid"), ()),
    ("he-il-default", False, tuple(), ("he", "hebrew")),
    ("uk-ua-default", False, tuple(), ("uk", "ukrainian")),
)

_LANGUAGE_ALIASES: dict[str, str] = {mid: mid for mid, *_ in _PYSTEMMER_MODEL_MAP}
_SUPPORTED_PYSTEMMER_ALGORITHMS: list[str] = []
_SUPPORTED_PYSTEMMER_ALIASES: list[str] = []
for _mid, _supported, _aliases, _native in _PYSTEMMER_MODEL_MAP:
    for _a in _aliases:
        _LANGUAGE_ALIASES[_a] = _mid
    for _a in _native:
        _LANGUAGE_ALIASES[_a] = _mid
    if _supported:
        _SUPPORTED_PYSTEMMER_ALGORITHMS.append(_aliases[0])
        _SUPPORTED_PYSTEMMER_ALIASES.extend(_aliases)

_SUPPORTED_PYSTEMMER_ALIASES = list(dict.fromkeys(_SUPPORTED_PYSTEMMER_ALIASES))
_SUPPORTED_PYSTEMMER_MODEL_IDS = frozenset(mid for mid, *_ in _PYSTEMMER_MODEL_MAP)
_STANDARD_PACKAGE = "radixor_models_standard"
_CATALOG_VERSION_PATTERN = re.compile(r"[1-9][0-9]{3}\.[1-9][0-9]*\Z")
_STANDARD_DISTRIBUTION_VERSION = re.compile(
    r"(?:0\.0\.0|2\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))\Z"
)
_MODEL_ID = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*\Z")
_SHA256 = re.compile(r"[0-9a-f]{64}\Z")
_V7_MAGIC = b"EGTR"
_V7_VERSION = 7


def algorithms(aliases: bool = False) -> list[str]:
    if aliases:
        return list(_SUPPORTED_PYSTEMMER_ALIASES)
    return list(_SUPPORTED_PYSTEMMER_ALGORITHMS)


def version() -> str:
    try:
        return metadata.version("radixor-c")
    except metadata.PackageNotFoundError:
        return "0.0.0"


def _load_standard_manifest() -> dict[str, Any]:
    try:
        ref = importlib.resources.files(_STANDARD_PACKAGE).joinpath("manifest.json")
    except (ModuleNotFoundError, TypeError) as exc:
        raise ModuleNotFoundError(
            "The standard Radixor model package is not installed. "
            "Install with 'pip install radixor-models-standard>=2.0,<3.0'."
        ) from exc
    try:
        manifest = json.loads(ref.read_text(encoding="utf-8"))
    except (FileNotFoundError, OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise RuntimeError("radixor-models-standard manifest is missing or corrupt.") from exc

    try:
        models = manifest["models"]
        format_info = manifest["format"]
        if manifest["schema_version"] != 1:
            raise ValueError("unsupported schema_version")
        catalog_version = manifest["catalog_version"]
        if not isinstance(catalog_version, str):
            raise ValueError("invalid catalog_version")
        if _CATALOG_VERSION_PATTERN.fullmatch(catalog_version) is None:
            raise ValueError("invalid catalog_version")
        dv = manifest["distribution_version"]
        if not isinstance(dv, str) or _STANDARD_DISTRIBUTION_VERSION.fullmatch(dv) is None:
            raise ValueError("incompatible distribution_version")
        if format_info != {"compression": "gzip", "magic": "EGTR", "version": 7}:
            raise ValueError("unsupported compiled model format")
        if not isinstance(models, list) or not models:
            raise ValueError("models must be a non-empty list")
        seen: set[str] = set()
        for model in models:
            mid = model["id"]
            if (
                not isinstance(mid, str)
                or _MODEL_ID.fullmatch(mid) is None
                or mid in seen
                or model["file"] != f"models/{mid}.rxc"
                or not isinstance(model["version"], str)
                or _SHA256.fullmatch(model["sha256"]) is None
            ):
                raise ValueError("invalid model entry")
            seen.add(mid)
    except (KeyError, TypeError, ValueError) as exc:
        raise RuntimeError(f"radixor-models-standard manifest incompatible: {exc}.") from exc
    return manifest


def _manifest_model(model_id: str) -> dict[str, Any]:
    if not isinstance(model_id, str) or _MODEL_ID.fullmatch(model_id) is None:
        raise ValueError(f"Invalid model ID {model_id!r}.")
    manifest = _load_standard_manifest()
    for model in manifest["models"]:
        if model["id"] == model_id:
            return model
    raise FileNotFoundError(f"Model '{model_id}' not in standard catalog.")


def _validate_standard_model(path: Path, model: dict[str, Any]) -> None:
    try:
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
    except OSError as exc:
        raise RuntimeError(f"Cannot read standard model '{model['id']}'.") from exc
    if digest != model["sha256"]:
        raise RuntimeError(f"Standard model '{model['id']}' failed SHA-256 validation.")
    try:
        with gzip.open(path, "rb") as stream:
            header = stream.read(8)
    except (OSError, EOFError) as exc:
        raise RuntimeError(f"Standard model '{model['id']}' is not valid gzip.") from exc
    if header[:4] != _V7_MAGIC or len(header) != 8:
        raise RuntimeError(f"Standard model '{model['id']}' missing EGTR marker.")
    version_int = int.from_bytes(header[4:8], "big", signed=True)
    if version_int != _V7_VERSION:
        raise RuntimeError(f"Unsupported model format v{version_int}.")


@contextmanager
def _standard_model_path(model_id: str) -> Iterator[Path]:
    model = _manifest_model(model_id)
    ref = (
        importlib.resources.files(_STANDARD_PACKAGE)
        .joinpath("models")
        .joinpath(f"{model_id}.rxc")
    )
    try:
        with importlib.resources.as_file(ref) as path:
            if not path.is_file():
                raise FileNotFoundError
            _validate_standard_model(path, model)
            yield path
    except FileNotFoundError as exc:
        raise FileNotFoundError(
            f"Standard model '{model_id}' missing; reinstall radixor-models-standard."
        ) from exc


class Stemmer:
    """Thread-safe Radixor stemmer backed by the C extension.

    Identical interface to ``radixor.Stemmer``.
    """

    def __init__(
        self,
        language: Optional[str] = None,
        maxCacheSize: Optional[int] = None,
        *,
        path: Optional[str] = None,
        compiled: Optional[str] = None,
        backward: Optional[bool] = None,
        store_original: bool = True,
        lowercase: bool = True,
        cache_size: int = 10_000,
    ) -> None:
        source = path if path is not None else compiled
        if maxCacheSize is not None:
            if not isinstance(maxCacheSize, int):
                raise TypeError("maxCacheSize must be an int")
            if maxCacheSize < 0:
                raise ValueError("maxCacheSize must be non-negative")
            cache_size = maxCacheSize
        elif cache_size < 0:
            raise ValueError("cache_size must be non-negative")

        if source is not None:
            model_path = source
            is_backward = True if backward is None else backward
            model_id_val = None
        elif language is not None:
            if language in _LANGUAGE_ALIASES:
                model_id_val = _LANGUAGE_ALIASES[language]
            elif language in _SUPPORTED_PYSTEMMER_MODEL_IDS:
                model_id_val = language
            elif "-" in language and _MODEL_ID.fullmatch(language) is not None:
                model_id_val = language
            elif ".." in language or "/" in language or "\\" in language:
                raise ValueError(f"Invalid model ID {language!r}.")
            else:
                raise KeyError(language)
            is_backward = True if backward is None else backward
            model_path = None
        else:
            raise ValueError("Provide 'language', 'path', or 'compiled'.")

        self._backward = is_backward
        self._store_original = store_original
        self._lowercase = lowercase
        self._cache_size = cache_size
        self._source_path = model_path
        self._model_id = model_id_val
        self._core = self._create_core(cache_size)

    def _create_core(self, cache_size: int) -> StemmerCore:
        if self._source_path is not None:
            return StemmerCore(
                self._source_path,
                self._backward,
                self._store_original,
                self._lowercase,
                cache_size,
            )
        with _standard_model_path(self._model_id or "") as model_path:
            return StemmerCore(
                str(model_path),
                self._backward,
                self._store_original,
                self._lowercase,
                cache_size,
            )

    @staticmethod
    def version() -> str:
        return version()

    @property
    def maxCacheSize(self) -> int:
        return self._cache_size

    @maxCacheSize.setter
    def maxCacheSize(self, size: int) -> None:
        if not isinstance(size, int):
            raise TypeError("maxCacheSize must be an int")
        if size < 0:
            raise ValueError("maxCacheSize must be non-negative")
        if size == self._cache_size:
            return
        self._cache_size = size
        self._core = self._create_core(size)

    def stem(self, word: str) -> Optional[str]:
        return self._core.stem(word)

    def stem_batch(self, words: list[str]) -> list[Optional[str]]:
        return self._core.stem_batch(words)

    @overload
    def stemWord(self, word: str) -> str: ...

    @overload
    def stemWord(self, word: bytes) -> bytes: ...

    def stemWord(self, word: str | bytes) -> str | bytes:
        return self._core.stemWord(word)

    def stemWords(self, words: Iterable[str | bytes]) -> list[str | bytes]:
        return self._core.stemWords(words)

    def stem_all(self, word: str) -> list[str]:
        return self._core.stem_all(word)

    def stem_all_batch(self, words: list[str]) -> list[list[str]]:
        return self._core.stem_all_batch(words)


__all__ = ["Stemmer", "StemmerCore", "algorithms", "version"]
