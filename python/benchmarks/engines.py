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

"""Stemmer engine adapters for the benchmark.

Every engine exposes a uniform interface:

    engine.name            -> str
    engine.kind            -> "native-batch" | "c-batch" | "py-batch" | "py-loop"
    engine.supports(code)  -> bool     (ISO-639-1 language code)
    engine.make(code)      -> callable(list[str]) -> list[str]   (the batch fn)

``kind`` records how batching is implemented:

- ``native-batch``: one native batch call
- ``c-batch``: C extension with a list entry point
- ``py-batch``: pure-Python object with a list method
- ``py-loop``: repeated scalar calls in Python

Engines whose package is not installed are simply reported as unavailable, so
the benchmark runs with whatever the user has.
"""

from __future__ import annotations

import inspect
import importlib
import importlib.util
from importlib.machinery import PathFinder
import types
from pathlib import Path
from typing import Callable, Optional


def _module_info(obj) -> dict:
    """Provenance of the module that actually provides ``obj``.

    The returned metadata identifies the backing module and distinguishes
    native extensions from pure-Python implementations.

    Resolves via ``type(obj).__module__`` -> ``sys.modules`` because
    ``inspect.getmodule`` returns ``None`` for Cython extension instances.
    """
    import sys

    name = type(obj).__module__
    mod = sys.modules.get(name) or inspect.getmodule(obj)
    file = getattr(mod, "__file__", None) or ""
    lower = file.lower()
    compiled = (
        lower.endswith((".pyd", ".so", ".dll")) or "cpython" in lower or "abi3" in lower
    )
    return {
        "backing_module": name,
        "backing_file": file,
        "compiled_extension": compiled,
    }


_PATH_CACHE: dict[str, Optional[Path]] = {}


def _distribution_root(name: str) -> Optional[Path]:
    if name in _PATH_CACHE:
        cached = _PATH_CACHE[name]
        return cached
    try:
        import importlib.metadata as metadata

        root = metadata.distribution(name).locate_file("").resolve()
        _PATH_CACHE[name] = root
        return root
    except Exception:
        _PATH_CACHE[name] = None
        return None


def _module_from_distribution(module_name: str, distribution_name: str):
    """Load a module from a distribution root, ignoring other sys.path entries."""
    root = _distribution_root(distribution_name)
    if root is None:
        return None
    spec = PathFinder.find_spec(module_name, [str(root)])
    if spec is None or spec.origin is None or spec.loader is None:
        return None
    try:
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
    except Exception:
        return None


def _module_within_distribution(module, distribution_name: str) -> bool:
    import sys

    if not isinstance(module, types.ModuleType):
        module_name = getattr(type(module), "__module__", None)
        module = sys.modules.get(module_name) if module_name else None
    elif not hasattr(module, "__file__"):
        module_name = getattr(module, "__name__", None)
        module = sys.modules.get(module_name) if module_name else None

    if module is None:
        return False

    try:
        path = Path(getattr(module, "__file__", "")).resolve()
        root = _distribution_root(distribution_name)
        if not (path.is_file() and root):
            return False
        try:
            return path == root or path.is_relative_to(root)
        except Exception:
            root_s = str(root)
            path_s = str(path)
            if not root_s.endswith("/"):
                root_s = root_s + "/"
            return path_s.startswith(root_s)
    except Exception:
        return False


def _load_dist_module(module_name: str, distribution_name: str):
    """Resolve a module robustly from the named distribution."""
    try:
        module = importlib.import_module(module_name)
        if _module_within_distribution(module, distribution_name):
            return module
    except Exception:
        pass
    return _module_from_distribution(module_name, distribution_name)


# ISO-639-1 -> Snowball algorithm name (matches the Java SnowballLanguageCase
# mapping; nb/nn both map to the single Snowball "norwegian" algorithm).
_SNOWBALL_NAMES: dict[str, str] = {
    "cs": "czech",
    "fa": "persian",
    "pl": "polish",
    "da": "danish",
    "nl": "dutch",
    "en": "english",
    "fi": "finnish",
    "fr": "french",
    "de": "german",
    "hu": "hungarian",
    "it": "italian",
    "nb": "norwegian",
    "nn": "norwegian",
    "pt": "portuguese",
    "ru": "russian",
    "es": "spanish",
    "sv": "swedish",
    "yi": "yiddish",
}

BatchFn = Callable[[list[str]], list[str]]


class Engine:
    name: str = "engine"
    kind: str = "py-loop"

    def available(self) -> bool:
        raise NotImplementedError

    def supports(self, code: str) -> bool:
        raise NotImplementedError

    def make(self, code: str) -> BatchFn:
        raise NotImplementedError

    def provenance(self, code: str) -> dict:
        return {
            "backing_module": self.name,
            "backing_file": "",
            "compiled_extension": False,
            "algorithm": None,
        }


class RadixorEngine(Engine):
    name = "radixor"
    kind = "native-batch"

    def __init__(self, lowercase: bool = False) -> None:
        # The shared corpus is already lowercase. Disable Radixor's redundant
        # normalization unless a benchmark explicitly includes that cost.
        self._lowercase = lowercase

    def available(self) -> bool:
        try:
            import radixor  # noqa: F401

            return True
        except Exception:
            return False

    def supports(self, code: str) -> bool:
        try:
            from radixor import _LANGUAGE_ALIASES

            return code in _LANGUAGE_ALIASES
        except Exception:
            return False

    def make(self, code: str) -> BatchFn:
        from radixor import Stemmer

        s = Stemmer(code, lowercase=self._lowercase, cache_size=0)
        return s.stem_batch

    def provenance(self, code: str) -> dict:
        from radixor import Stemmer

        s = Stemmer(code, lowercase=self._lowercase, cache_size=0)
        info = _module_info(s._core)
        info["algorithm"] = "radixor-trie"
        info["lowercase"] = self._lowercase
        info["case_mode"] = s._core._case_mode()
        info["cache_disabled"] = True
        return info


class PyStemmerEngine(Engine):
    name = "PyStemmer"
    kind = "c-batch"

    @staticmethod
    def _stemmer_module():
        return _load_dist_module("Stemmer", "PyStemmer")

    def available(self) -> bool:
        return self._stemmer_module() is not None

    def _algorithms(self) -> set[str]:
        stemmer = self._stemmer_module()
        if stemmer is None:
            return set()
        return {a.lower() for a in stemmer.algorithms()}

    def supports(self, code: str) -> bool:
        name = _SNOWBALL_NAMES.get(code)
        return bool(name) and name in self._algorithms()

    @staticmethod
    def _new_stemmer(code: str):
        Stemmer = PyStemmerEngine._stemmer_module()
        if Stemmer is None:
            raise RuntimeError("PyStemmer distribution is not importable")
        stemmer = Stemmer.Stemmer(_SNOWBALL_NAMES[code])
        # Repeated passes would otherwise measure PyStemmer's default cache
        # after the first pass. Both native engines therefore run uncached.
        stemmer.maxCacheSize = 0
        return stemmer

    def make(self, code: str) -> BatchFn:
        stemmer = self._new_stemmer(code)
        # PyStemmer's native list entry point: one C call for the whole batch.
        return stemmer.stemWords

    def provenance(self, code: str) -> dict:
        stemmer = self._new_stemmer(code)
        info = _module_info(stemmer)
        info["algorithm"] = _SNOWBALL_NAMES[code]
        info["cache_disabled"] = True
        info["distribution"] = "PyStemmer"
        info["distribution_verified"] = _module_within_distribution(stemmer, "PyStemmer")
        # Record whether PyStemmer resolves to an independent native extension.
        try:
            import snowballstemmer

            snowball_dir = str(Path(snowballstemmer.__file__).resolve().parent).lower()
        except Exception:
            snowball_dir = None
        backing = info["backing_file"].lower()
        info["independent_of_snowballstemmer"] = bool(
            info["compiled_extension"]
            and (snowball_dir is None or snowball_dir not in backing)
        )
        return info


class SnowballStemmerEngine(Engine):
    """Pure-Python Snowball backend.

    ``snowballstemmer.stemmer()`` delegates to PyStemmer (the C extension) when
    PyStemmer is installed, which would make this engine a duplicate of
    ``PyStemmer``. To retain a distinct implementation, this adapter imports
    the language's pure-Python class directly from
    ``snowballstemmer.<name>_stemmer``.
    """

    name = "snowballstemmer-pure"
    kind = "py-batch"

    def available(self) -> bool:
        try:
            import snowballstemmer  # noqa: F401

            return True
        except Exception:
            return False

    def _load_class(self, code: str):
        import importlib

        name = _SNOWBALL_NAMES.get(code)
        if not name:
            return None
        module_name = f"snowballstemmer.{name}_stemmer"
        module = _load_dist_module(module_name, "snowballstemmer")
        if module is None:
            return None
        module_file = getattr(module, "__file__", "").lower()
        if not module_file.endswith(".py"):
            return None
        class_name = name.capitalize() + "Stemmer"
        stemmer_class = getattr(module, class_name, None)
        if not inspect.isclass(stemmer_class):
            return None
        stem_method = getattr(stemmer_class, "stemWords", None)
        if not inspect.isfunction(stem_method):
            return None
        return stemmer_class

    def supports(self, code: str) -> bool:
        try:
            return self._load_class(code) is not None
        except Exception:
            return False

    def make(self, code: str) -> BatchFn:
        stemmer = self._load_class(code)()
        return stemmer.stemWords  # pure-Python loop over the list, internally

    def provenance(self, code: str) -> dict:
        stemmer = self._load_class(code)()
        info = _module_info(stemmer)
        info["algorithm"] = _SNOWBALL_NAMES[code]
        info["distribution"] = "snowballstemmer"
        info["distribution_verified"] = _module_within_distribution(stemmer, "snowballstemmer")
        return info


class NltkPorterEngine(Engine):
    name = "nltk-porter"
    kind = "py-loop"

    def available(self) -> bool:
        try:
            from nltk.stem import PorterStemmer  # noqa: F401

            return True
        except Exception:
            return False

    def supports(self, code: str) -> bool:
        return code == "en"  # Porter is English-only

    def make(self, code: str) -> BatchFn:
        from nltk.stem import PorterStemmer

        ps = PorterStemmer()
        stem = ps.stem

        def batch(words: list[str]) -> list[str]:
            return [stem(w) for w in words]

        return batch

    def provenance(self, code: str) -> dict:
        from nltk.stem import PorterStemmer

        info = _module_info(PorterStemmer())
        info["algorithm"] = "porter"
        return info


class CistemEngine(Engine):
    """CISTEM — a fast lightweight German stemmer (German only)."""

    name = "cistem"
    kind = "py-loop"

    def available(self) -> bool:
        try:
            from nltk.stem.cistem import Cistem  # noqa: F401

            return True
        except Exception:
            return False

    def supports(self, code: str) -> bool:
        return code == "de"

    def make(self, code: str) -> BatchFn:
        from nltk.stem.cistem import Cistem

        stem = Cistem().stem

        def batch(words: list[str]) -> list[str]:
            return [stem(w) for w in words]

        return batch

    def provenance(self, code: str) -> dict:
        from nltk.stem.cistem import Cistem

        info = _module_info(Cistem())
        info["algorithm"] = "cistem"
        return info


ALL_ENGINES: list[Engine] = [
    RadixorEngine(),
    PyStemmerEngine(),
    SnowballStemmerEngine(),
    NltkPorterEngine(),
    CistemEngine(),
]


def available_engines(names: Optional[set[str]] = None) -> list[Engine]:
    engines = [e for e in ALL_ENGINES if e.available()]
    if names:
        engines = [e for e in engines if e.name in names]
    return engines
