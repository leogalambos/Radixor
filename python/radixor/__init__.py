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

"""Python API for the Rust-backed Radixor stemmer.

Usage::

    from radixor import Stemmer

    s = Stemmer("en")
    print(s.stem("running"))        # single word
    print(s.stem_batch(words))      # batch API for collections
"""

from __future__ import annotations

import gzip
import hashlib
import importlib.resources
import json
import re
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator, Optional

from radixor._radixor import StemmerCore
from radixor._radixor import compile as _compile

_LANGUAGE_ALIASES: dict[str, str] = {
    # Friendly aliases -> model ID
    "cs": "cs-cz-default",
    "czech": "cs-cz-default",
    "da": "da-dk-default",
    "danish": "da-dk-default",
    "de": "de-de-default",
    "german": "de-de-default",
    "en": "us-uk-default",
    "english": "us-uk-default",
    "es": "es-es-default",
    "spanish": "es-es-default",
    "fa": "fa-ir-default",
    "persian": "fa-ir-default",
    "fi": "fi-fi-default",
    "finnish": "fi-fi-default",
    "fr": "fr-fr-default",
    "french": "fr-fr-default",
    "he": "he-il-default",
    "hebrew": "he-il-default",
    "hu": "hu-hu-default",
    "hungarian": "hu-hu-default",
    "it": "it-it-default",
    "italian": "it-it-default",
    "nb": "nb-no-default",
    "norwegian": "nb-no-default",
    "nl": "nl-nl-default",
    "dutch": "nl-nl-default",
    "nn": "nn-no-default",
    "pl": "pl-pl-unimorph",
    "polish": "pl-pl-unimorph",
    "pt": "pt-pt-default",
    "portuguese": "pt-pt-default",
    "ru": "ru-ru-default",
    "russian": "ru-ru-default",
    "sv": "sv-se-default",
    "swedish": "sv-se-default",
    "uk": "uk-ua-default",
    "ukrainian": "uk-ua-default",
    "yi": "yi-default",
    "yiddish": "yi-default",
}

# Right-to-left languages use FORWARD traversal; everything else BACKWARD.
# Keyed by model ID prefix (language part).
_RIGHT_TO_LEFT_MODELS: frozenset[str] = frozenset(
    {"fa-ir-default", "he-il-default", "yi-default"}
)

_STANDARD_PACKAGE = "radixor_models_standard"
_STANDARD_CATALOG_VERSION = "2026.1"
_STANDARD_DISTRIBUTION_VERSION = re.compile(
    r"(?:0\.0\.0|1\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))\Z"
)
_MODEL_ID = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*\Z")
_SHA256 = re.compile(r"[0-9a-f]{64}\Z")
_V7_MAGIC = b"EGTR"
_V7_VERSION = 7


def _load_standard_manifest() -> dict[str, Any]:
    """Load and validate the installed standard model catalog manifest."""
    try:
        ref = importlib.resources.files(_STANDARD_PACKAGE).joinpath("manifest.json")
    except (ModuleNotFoundError, TypeError) as exc:
        raise ModuleNotFoundError(
            "The standard Radixor model package is not installed. Install a compatible "
            "provider with 'pip install radixor-models-standard>=1.0,<2.0', "
            "or reinstall Radixor with 'pip install radixor'."
        ) from exc
    try:
        manifest = json.loads(ref.read_text(encoding="utf-8"))
    except (FileNotFoundError, OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise RuntimeError(
            "The installed radixor-models-standard manifest is missing or corrupt; "
            "reinstall radixor-models-standard."
        ) from exc

    try:
        models = manifest["models"]
        format_info = manifest["format"]
        if manifest["schema_version"] != 1:
            raise ValueError("unsupported schema_version")
        if manifest["catalog_version"] != _STANDARD_CATALOG_VERSION:
            raise ValueError(
                f"catalog {manifest['catalog_version']!r} is incompatible with "
                f"Radixor catalog {_STANDARD_CATALOG_VERSION!r}"
            )
        distribution_version = manifest["distribution_version"]
        if (
            not isinstance(distribution_version, str)
            or _STANDARD_DISTRIBUTION_VERSION.fullmatch(distribution_version) is None
        ):
            raise ValueError("incompatible distribution_version")
        if format_info != {"compression": "gzip", "magic": "EGTR", "version": 7}:
            raise ValueError("unsupported compiled model format")
        if not isinstance(models, list) or not models:
            raise ValueError("models must be a non-empty list")
        seen: set[str] = set()
        for model in models:
            model_id = model["id"]
            if (
                not isinstance(model_id, str)
                or _MODEL_ID.fullmatch(model_id) is None
                or model_id in seen
                or model["file"] != f"models/{model_id}.rxc"
                or not isinstance(model["version"], str)
                or _SHA256.fullmatch(model["sha256"]) is None
            ):
                raise ValueError("invalid model entry")
            seen.add(model_id)
    except (KeyError, TypeError, ValueError) as exc:
        raise RuntimeError(
            f"The installed radixor-models-standard manifest is incompatible or corrupt: {exc}. "
            "Install radixor-models-standard>=1.0,<2.0."
        ) from exc
    return manifest


def _manifest_model(model_id: str) -> dict[str, Any]:
    if not isinstance(model_id, str) or _MODEL_ID.fullmatch(model_id) is None:
        raise ValueError(
            f"Invalid Radixor model ID {model_id!r}; expected lowercase letters, digits, and hyphens."
        )
    manifest = _load_standard_manifest()
    for model in manifest["models"]:
        if model["id"] == model_id:
            return model
    raise FileNotFoundError(
        f"Model '{model_id}' is not in the standard Radixor catalog. "
        "Pass a custom source path via Stemmer(path=...) or a compiled v7 path "
        "via Stemmer(compiled=...)."
    )


def _validate_standard_model(path: Path, model: dict[str, Any]) -> None:
    try:
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
    except OSError as exc:
        raise RuntimeError(
            f"Standard model '{model['id']}' cannot be read; reinstall radixor-models-standard."
        ) from exc
    if digest != model["sha256"]:
        raise RuntimeError(
            f"Standard model '{model['id']}' failed SHA-256 validation; "
            "reinstall radixor-models-standard."
        )
    try:
        with gzip.open(path, "rb") as stream:
            header = stream.read(8)
    except (OSError, EOFError) as exc:
        raise RuntimeError(
            f"Standard model '{model['id']}' is not a valid gzip-compressed v7 resource; "
            "reinstall radixor-models-standard."
        ) from exc
    if header[:4] != _V7_MAGIC or len(header) != 8:
        raise RuntimeError(
            f"Standard model '{model['id']}' does not contain the Radixor EGTR format marker; "
            "reinstall radixor-models-standard."
        )
    version = int.from_bytes(header[4:8], "big", signed=True)
    if version != _V7_VERSION:
        raise RuntimeError(
            f"Standard model '{model['id']}' uses unsupported compiled format v{version}; "
            f"Radixor requires v{_V7_VERSION}."
        )


@contextmanager
def _standard_model_path(model_id: str) -> Iterator[Path]:
    """Yield a validated standard model path for synchronous native loading."""
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
            f"Standard model '{model_id}' is missing from radixor-models-standard; "
            "reinstall radixor-models-standard."
        ) from exc


def _is_backward(model_id: str) -> bool:
    """Traversal direction implied by the model's language (RTL => FORWARD)."""
    return model_id not in _RIGHT_TO_LEFT_MODELS


class Stemmer:
    """Thread-safe stemmer backed by a Radixor patch-command trie.

    Standard language models are loaded from validated, precompiled v7 resources
    supplied by the mandatory ``radixor-models-standard`` distribution.

    Parameters
    ----------
    language:
        Two-letter ISO 639-1 code (e.g. ``"en"``) or a full model ID
        (e.g. ``"us-uk-default"``).  Ignored when ``path`` is given.
    path:
        Explicit path to either a gzipped source dictionary or a compiled
        ``.rxc`` trie (Java-interoperable v7 format); the format is
        auto-detected.  Takes precedence over ``language``.
    compiled:
        Alias for ``path`` intended for compiled ``.rxc`` files (see
        :func:`compile`).  For compiled input, ``backward`` / ``store_original``
        are baked into the file and ignored.
    backward:
        Traversal direction override.  When ``None`` (default) it is derived
        from the language (BACKWARD, except right-to-left fa/he/yi which use
        FORWARD).  Only consulted for ``path``-based construction if given.
    store_original:
        When ``True`` (default) each canonical stem maps to the no-op patch,
        so the stem itself is recognised.
    lowercase:
        When ``True`` (default) lookups lowercase the input word. Set to
        ``False`` when you guarantee the input is already lowercased (skips the
        per-lookup normalization; the model's keys are always lowercase).
    cache_size:
        Maximum entries in the bounded result cache (default ``10_000``,
        matching PyStemmer). Set to ``0`` to disable caching. Cached results are
        shared by :meth:`stem`, :meth:`stemWord`, :meth:`stem_batch`, and
        :meth:`stemWords`; ``stem_all`` methods are not cached.
    """

    def __init__(
        self,
        language: Optional[str] = None,
        *,
        path: Optional[str] = None,
        compiled: Optional[str] = None,
        backward: Optional[bool] = None,
        store_original: bool = True,
        lowercase: bool = True,
        cache_size: int = 10_000,
    ) -> None:
        source = path if path is not None else compiled
        if source is not None:
            model_path = source
            is_backward = True if backward is None else backward
        elif language is not None:
            model_id = _LANGUAGE_ALIASES.get(language, language)
            is_backward = _is_backward(model_id) if backward is None else backward
            with _standard_model_path(model_id) as model_path:
                self._core = StemmerCore(
                    str(model_path), is_backward, store_original, lowercase, cache_size
                )
            return
        else:
            raise ValueError("Provide 'language', 'path', or 'compiled'.")
        self._core = StemmerCore(
            model_path, is_backward, store_original, lowercase, cache_size
        )

    def stem(self, word: str) -> Optional[str]:
        """Return a stem, or ``None`` when no patch command applies."""
        return self._core.stem(word)

    def stem_batch(self, words: list[str]) -> list[Optional[str]]:
        """Stem many words in one call.

        Preferred over calling :meth:`stem` in a loop: the Python→Rust bridge
        overhead is amortised across the whole batch, making this significantly
        faster for large word lists.

        Returns a list of the same length; entries are ``None`` when the
        compiled trie finds no applicable patch command.
        """
        return self._core.stem_batch(words)

    def stemWord(self, word: str) -> str:
        """Return a stem using PyStemmer-compatible fallback semantics.

        If no patch command can be found, return *word* unchanged. Use
        :meth:`stem` when a missing result must remain distinguishable as
        ``None``.
        """
        return self._core.stemWord(word)

    def stemWords(self, words: list[str]) -> list[str]:
        """Stem words using PyStemmer-compatible fallback semantics.

        The returned list has the same length and order as *words*; each word
        without a matching patch command is returned unchanged.
        """
        return self._core.stemWords(words)

    def stem_all(self, word: str) -> list[str]:
        """Return all stems for *word* ordered by descending frequency."""
        return self._core.stem_all(word)

    def stem_all_batch(self, words: list[str]) -> list[list[str]]:
        """Return all stems for each word in *words* as a list of lists."""
        return self._core.stem_all_batch(words)


def compile(
    source: str,
    out_path: str,
    *,
    language: Optional[str] = None,
    backward: Optional[bool] = None,
    store_original: bool = True,
    lowercase: bool = True,
) -> None:
    """Compile a textual source dictionary into a Java-interoperable compiled
    trie file (v7 format) that :class:`Stemmer` can load instantly.

    Parameters
    ----------
    source:
        Path to a gzipped (or plain) TSV source dictionary.
    out_path:
        Destination compiled file (conventionally ``*.rxc``).
    language:
        Optional language code/model ID used only to derive ``backward`` when
        it is not given (right-to-left fa/he/yi compile FORWARD).
    backward:
        Traversal direction.  When ``None`` it is derived from ``language`` if
        provided, otherwise defaults to BACKWARD.
    store_original, lowercase:
        Same meaning as :class:`Stemmer`; baked into the compiled file.

    The resulting file is byte-compatible (inner stream) with the Radixor Java
    ``StemmerPatchTrieBinaryIO`` v7 format, so Java and Python can share it.
    """
    if backward is None:
        if language is not None:
            backward = _is_backward(_LANGUAGE_ALIASES.get(language, language))
        else:
            backward = True
    _compile(source, out_path, backward, store_original, lowercase)


__all__ = ["Stemmer", "compile"]
