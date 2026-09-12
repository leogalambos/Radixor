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

"""Standard-model and alias contracts shared by the Python runtimes."""

from __future__ import annotations

import radixor_c


ADDED_STANDARD_ALIASES = {
    "arabic": "ar-default",
    "armenian": "hy-am-default",
    "catalan": "ca-es-default",
    "estonian": "et-ee-default",
    "greek": "el-gr-default",
    "indonesian": "id-id-default",
    "irish": "ga-ie-default",
    "lithuanian": "lt-lt-default",
    "romanian": "ro-ro-default",
    "sesotho": "st-za-default",
    "turkish": "tr-tr-default",
}


def test_added_standard_aliases_resolve_to_exact_models() -> None:
    for alias, model_id in ADDED_STANDARD_ALIASES.items():
        assert radixor_c._LANGUAGE_ALIASES[alias] == model_id
        assert radixor_c._LANGUAGE_ALIASES[model_id] == model_id


def test_python_c_accepts_only_standard_models_distribution_major_three() -> None:
    pattern = radixor_c._STANDARD_DISTRIBUTION_VERSION

    assert pattern.fullmatch("3.0.0") is not None
    assert pattern.fullmatch("3.12.4") is not None
    assert pattern.fullmatch("2.99.0") is None
    assert pattern.fullmatch("4.0.0") is None
