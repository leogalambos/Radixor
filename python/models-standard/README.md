# Radixor standard models

This pure-Python distribution supplies Radixor's 20 precompiled standard
language models. It is installed automatically by `pip install radixor`; users
normally do not import it directly.

The catalog version is `2026.1`. Individual model versions recorded in the
generated `radixor_models_standard/manifest.json` are currently `1.0.0`. The
optional Polish PoliMorf model is intentionally not part of the standard
catalog.
The first Python model distribution is released as `1.0.0`; its version is
independent of both the catalog identity and the individual model versions.
The checked-in descriptors use `0.0.0` as a deliberate non-release placeholder.
The release workflow creates an isolated project below `build/`, injects the Git
tag version, and deterministically compiles all model resources there.

Only gzip-compressed Radixor v7 (`.rxc`) tries are shipped. They are release
artifacts, not checked-in repository files. Canonical textual dictionaries
remain in the Radixor source repository and are not included in this wheel or
source distribution. Model data is licensed under CC BY-SA 3.0; see the
[model-data license](https://github.com/leogalambos/Radixor/blob/main/python/models-standard/LICENSE-MODEL-DATA.txt)
and the generated per-model notices.

The checked-out directory is intentionally only a packaging skeleton and is
not directly buildable as the complete data distribution. From the repository
root, use `./gradlew pythonBuildStandardModels`; the generated project and its
wheel/sdist are written below `build/python/`.
