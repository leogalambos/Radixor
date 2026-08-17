"""PyStemmer compatibility entry point for Radixor.

Import this module to run existing ``import Stemmer`` call sites without changing
application code when Radixor replaces PyStemmer in the environment.
"""

from __future__ import annotations

from radixor import Stemmer, algorithms, version

__all__ = ["Stemmer", "algorithms", "version"]

