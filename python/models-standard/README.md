# Radixor standard models

This pure-Python distribution supplies Radixor's 20 precompiled standard
language models. It is installed automatically by `pip install radixor`; users
normally do not import it directly.

The aggregate Java catalog and individual model versions are recorded in the
generated `radixor_models_standard/manifest.json`. Release staging reads them from
`models/catalog-version.txt` and the individual `model-version.txt` files; they
are not maintained separately in this packaging skeleton. The optional Polish
PoliMorf model is intentionally not part of the standard catalog.
The distribution version is independent of both the catalog identity and the
individual model versions. Its major version is the runtime compatibility
boundary; catalog releases within a supported major line are provenance only.
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
