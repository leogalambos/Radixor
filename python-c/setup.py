"""Build script for the radixor-c C extension."""

import os
import sys
from setuptools import Extension, setup

# GCC-compatible and MSVC toolchains use different option syntaxes.
compiler = os.environ.get("DISTUTILS_COMPILER", "")
if sys.platform != "win32" or "mingw" in compiler.lower():
    extra_compile_args = ["-O3", "-Wall", "-Wno-misleading-indentation"]
    extra_link_args = []
else:
    extra_compile_args = ["/O2", "/W3"]
    extra_link_args = []

ext = Extension(
    "radixor_c._radixor_c",
    sources=["src/_radixor_c.c"],
    extra_compile_args=extra_compile_args,
    extra_link_args=extra_link_args,
)

setup(ext_modules=[ext])
