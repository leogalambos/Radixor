"""Validate Python Trove language classifiers against standard-model membership."""

from __future__ import annotations

import tomllib
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[2]
STANDARD_MODELS = REPOSITORY / "models" / "standard-model-projects.properties"
ACTIVE_MODELS = REPOSITORY / "models" / "model-projects.properties"
ALTERNATIVE_MODELS = REPOSITORY / "models" / "alternative-model-projects.properties"
QUARANTINED_MODELS = REPOSITORY / "models" / "model-quarantine.properties"
PYTHON_PROJECTS = (
    REPOSITORY / "python" / "pyproject.toml",
    REPOSITORY / "python-c" / "pyproject.toml",
    REPOSITORY / "python" / "models-standard" / "pyproject.toml",
)
LANGUAGE_PREFIX = "Natural Language :: "

# Official PyPI Trove classifier names checked against pypi.org/classifiers on
# 2026-09-12. Southern Sotho has no corresponding official classifier.
OFFICIAL_LANGUAGE_CLASSIFIERS = {
    "Arabic",
    "Armenian",
    "Catalan",
    "Czech",
    "Danish",
    "Dutch",
    "English",
    "Estonian",
    "Finnish",
    "French",
    "German",
    "Greek",
    "Hebrew",
    "Hungarian",
    "Indonesian",
    "Irish",
    "Italian",
    "Lithuanian",
    "Norwegian",
    "Persian",
    "Polish",
    "Portuguese",
    "Romanian",
    "Russian",
    "Spanish",
    "Swedish",
    "Turkish",
    "Ukrainian",
    "Yiddish",
}
STANDARD_MODEL_CLASSIFIER = {
    "ar-default": "Arabic",
    "ca-es-default": "Catalan",
    "cs-cz-default": "Czech",
    "da-dk-default": "Danish",
    "de-de-default": "German",
    "el-gr-default": "Greek",
    "es-es-default": "Spanish",
    "et-ee-default": "Estonian",
    "fa-ir-default": "Persian",
    "fi-fi-default": "Finnish",
    "fr-fr-default": "French",
    "ga-ie-default": "Irish",
    "he-il-default": "Hebrew",
    "hu-hu-default": "Hungarian",
    "hy-am-default": "Armenian",
    "id-id-default": "Indonesian",
    "it-it-default": "Italian",
    "lt-lt-default": "Lithuanian",
    "nb-no-default": "Norwegian",
    "nl-nl-default": "Dutch",
    "nn-no-default": "Norwegian",
    "pl-pl-unimorph": "Polish",
    "pt-pt-default": "Portuguese",
    "ro-ro-default": "Romanian",
    "ru-ru-default": "Russian",
    "sv-se-default": "Swedish",
    "tr-tr-default": "Turkish",
    "uk-ua-default": "Ukrainian",
    "us-uk-default": "English",
    "yi-default": "Yiddish",
}
NO_OFFICIAL_CLASSIFIER = {"st-za-default"}


def _property_keys(path: Path) -> set[str]:
    keys: set[str] = set()
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, _value = line.partition("=")
        assert separator == "=", f"Malformed property in {path}: {raw_line!r}"
        keys.add(key.strip())
    return keys


def _language_classifiers(path: Path) -> list[str]:
    with path.open("rb") as stream:
        document = tomllib.load(stream)
    classifiers = document["project"]["classifiers"]
    return [
        classifier.removeprefix(LANGUAGE_PREFIX)
        for classifier in classifiers
        if classifier.startswith(LANGUAGE_PREFIX)
    ]


def test_standard_model_classifier_mapping_is_complete_and_exclusive() -> None:
    standard = _property_keys(STANDARD_MODELS)
    classified = set(STANDARD_MODEL_CLASSIFIER)

    assert len(standard) == 31
    assert classified | NO_OFFICIAL_CLASSIFIER == standard
    assert classified & NO_OFFICIAL_CLASSIFIER == set()
    assert NO_OFFICIAL_CLASSIFIER == {"st-za-default"}
    assert set(STANDARD_MODEL_CLASSIFIER.values()) == OFFICIAL_LANGUAGE_CLASSIFIERS

    alternatives = _property_keys(ALTERNATIVE_MODELS)
    quarantined = _property_keys(QUARANTINED_MODELS)
    extended = _property_keys(ACTIVE_MODELS) - standard
    assert classified.isdisjoint(extended)
    assert classified.isdisjoint(alternatives)
    assert classified.isdisjoint(quarantined)


def test_norwegian_classifier_intentionally_covers_both_standard_models() -> None:
    norwegian_models = {
        model_id
        for model_id, classifier in STANDARD_MODEL_CLASSIFIER.items()
        if classifier == "Norwegian"
    }
    assert norwegian_models == {"nb-no-default", "nn-no-default"}


def test_all_python_projects_declare_the_same_official_language_classifiers() -> None:
    expected = sorted(OFFICIAL_LANGUAGE_CLASSIFIERS)

    for project in PYTHON_PROJECTS:
        actual = _language_classifiers(project)
        assert actual == expected, project
        assert len(actual) == len(set(actual)) == 29
