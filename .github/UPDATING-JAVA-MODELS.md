# Updating Java stemmer models

This maintainer checklist covers the independently versioned Java model JARs,
the two POM-only Java catalog artifacts (`radixor-models-standard` and
`radixor-models-bom`), and the separately packaged precompiled Python models.
A model, the Java core, the Java model catalog, and the Python standard-model
distribution have separate version sequences. Never change unrelated
`model-version.txt` files.

In the commands below, replace `<model-id>`, `<model-version>`, and
`<catalog-version>` with concrete values such as `us-uk-default`, `1.0.1`, and
`2026.2`.

## 1. Update one model

Edit these files manually:

- Always replace `models/<model-id>/src/modelInput/stemmer.gz` when the
  dictionary changes.
- Change `models/<model-id>/src/modelInput/NOTICE-model-data.txt` when the
  upstream attribution, license, source, or transformation disclosure changes.
- Keep the metadata in `models/<model-id>/build.gradle` synchronized with the
  input. In particular, review the source version, immutable revision, revision
  status, verification date, attribution, license, and transformations.
- Set only `models/<model-id>/model-version.txt` to the new semantic version.
  Do not modify the version files of unaffected models.

Do not edit generated descriptors, checksums, JARs, or anything below a
`build/` directory. Gradle generates those files from the inputs above.

Validate the changed module and its runtime integration:

```bash
./gradlew --no-daemon ":models:<model-id>:clean"
./gradlew --no-daemon ":models:<model-id>:check"
./gradlew --no-daemon runtimeModelIntegrationTest "-PmodelId=<model-id>"
```

The checked-in model catalog contains the model version, source metadata,
dictionary checksum, and byte size. Regenerate it after changing any of those
values:

```bash
./gradlew --no-daemon publishModelCatalogDocumentation
./gradlew --no-daemon verifyModelCatalogDocumentation
```

`publishModelCatalogDocumentation` is the only supported way to update
`docs/stemmer-model-catalog.md`; never edit the generated values manually.
Commit the regenerated file together with the model change. The Pages workflow
then verifies that the checked-in catalog and the independently staged catalog
are byte-identical before publishing the site.

Do not regenerate files below `docs/benchmarks/` as part of a model release.
Those files are historical measurement snapshots and intentionally retain the
exact model version and checksum that were measured. They are refreshed only
as a separately planned benchmark publication.

Review the resulting scope before committing:

```bash
git diff --check
git diff -- models/<model-id> docs/stemmer-model-catalog.md
```

## 2. Validate and publish the individual model

The model must be published before a catalog that refers to its new version.
Validate the exact release version locally:

```bash
./tools/parse-model-release-tag.sh \
  "model/<model-id>@<model-version>" .
./gradlew --no-daemon ":models:<model-id>:validateModelRelease" \
  "-PmodelReleaseVersion=<model-version>"
./gradlew --no-daemon ":models:<model-id>:packageModelReleaseCandidate" \
  "-PmodelReleaseVersion=<model-version>"
```

After the change is merged into `main`, create and push the model tag:

```bash
git tag -a "model/<model-id>@<model-version>" \
  -m "Release <model-id> model <model-version>"
git push origin "model/<model-id>@<model-version>"
```

Wait for **Model Release** to publish
`org.egothor:radixor-model-<model-id>:<model-version>` successfully before
publishing the updated catalog.

## 3. Update the standard metapackage and BOM

The standard metapackage and BOM share a catalog version, but their POM entries
use each model's own version from its `model-version.txt`:

- `radixor-models-standard` contains every default model as a runtime
  dependency.
- `radixor-models-bom` manages every default and optional model version.

To release updated catalog metadata, manually increment only
`models/catalog-version.txt`. Use the next repository catalog version matching
the `YYYY.N` format. Do not edit `models/standard/build.gradle` or
`models/bom/build.gradle` merely to update model versions; both projects read
the per-model version files automatically.

Build and verify the exact POM-only catalog bundle locally:

```bash
./gradlew --no-daemon :models:standard:check :models:bom:check
./gradlew --no-daemon verifyModelCatalogReleaseCandidate
```

The second command verifies that the standard POM references every default
model and the BOM constrains every model at its individually recorded version.
It also rejects JARs, dictionaries, Gradle module metadata, missing checksums,
and unexpected publication files in the catalog bundle.

Commit `models/catalog-version.txt` separately or together with the catalog
release preparation. After the commit is merged into `main`, publish the two
metadata artifacts with:

```bash
git tag -a "models-catalog@<catalog-version>" \
  -m "Release Radixor model catalog <catalog-version>"
git push origin "models-catalog@<catalog-version>"
```

The **Model Catalog Release** workflow publishes only
`org.egothor:radixor-models-standard:<catalog-version>` and
`org.egothor:radixor-models-bom:<catalog-version>`. It does not republish any
individual model JAR.

## 4. Rebuild and publish the precompiled Python models

Every default Java dictionary is also a compiler input for the Python package
`radixor-models-standard`. The package contains 20 generated `.rxc` files and
excludes the optional `pl-pl-polimorf` model. A change to any default model
therefore requires a new Python standard-model distribution.

Manually set `python/models-standard-version.txt` to a new, unpublished stable
`2.x.y` distribution version. This version belongs to the complete Python
model distribution; it is independent of the changed Java model version and
the Java catalog version and does not have to use the same number. If the file
already contains the intended unpublished version, do not increment it again.
Published versions are immutable and must never be overwritten.

Do not manually edit
`python/models-standard/radixor_models_standard/__init__.py`, Python runtime
catalog constants, generated manifests, or release validators when the Java
catalog version changes. `models/catalog-version.txt` is the single source of
`catalog_version` in the generated manifest, and the package exposes
`CATALOG_VERSION` by reading that manifest rather than duplicating the value in
Python source. Each model's `model-version.txt` is the single source of that
model's version. Archive verification checks all generated values against the
tracked inputs. The catalog version identifies the aggregate used to build the
distribution but is not a runtime compatibility boundary. Manifest schema,
compiled-trie format, and the model distribution's major version define
runtime compatibility.

Compile and package all Python standard models locally:

```bash
./gradlew --no-daemon pythonBuildStandardModels
./gradlew --no-daemon pythonVerifyStandardModelsDistribution
```

This task reads every default model's canonical `stemmer.gz`,
`model-version.txt`, notice, and Gradle provenance metadata. It compiles each
dictionary twice and rejects non-deterministic output. The generated project,
manifest, notices, `.rxc` files, wheel, and source distribution remain below
`build/python/`; do not add any of them to Git. The verification task inspects
both archives, checks that the manifest uses the tracked aggregate identity,
and checks every manifest model version against that model's own version file.

Before publishing, run **Python Standard Models Release** manually with the
version from `python/models-standard-version.txt`. A manual run validates the
isolated wheel and source distribution without publishing unless the explicit
recovery option for an existing release is selected.

After the source and version changes are merged into `main`, publish the
precompiled model package:

```bash
PYTHON_MODELS_VERSION="$(tools/read-python-models-standard-version.sh)"
./tools/parse-python-release-tag.sh \
  "python-models-standard@${PYTHON_MODELS_VERSION}"
git tag -a "python-models-standard@${PYTHON_MODELS_VERSION}" \
  -m "Python standard models ${PYTHON_MODELS_VERSION}"
git push origin "python-models-standard@${PYTHON_MODELS_VERSION}"
```

Wait for **Python Standard Models Release** to publish its immutable GitHub
Release, PyPI wheel and source distribution, build attestations, and Python
package-index entry. The generated manifest records the Java catalog version
and the individual version and source checksum of every compiled model.

If a tag-triggered workflow fails before creating any GitHub Release, PyPI
artifact, attestation, or package-index entry, delete the failed tag locally and
on the remote before reusing its unpublished version on the corrected commit.
Never move a tag that still exists remotely. If any artifact was published, use
a new version because release artifacts are immutable.

The `radixor` and `radixor-c` runtime distributions depend on
`radixor-models-standard>=2.0,<3.0`; they do not need a new runtime release for
a dictionary-only update. If a coordinated runtime release is planned for
another reason, publish the Python standard-model distribution first because
the runtime release workflows test against the exact version recorded in
`python/models-standard-version.txt`. See `RELEASING-PYTHON.md` for that wider
runtime-release procedure.

## 5. Required publication order

For a default dictionary update, keep this order:

1. Merge the changed model inputs, its single version file, metadata, and the
   regenerated `docs/stemmer-model-catalog.md`.
2. Publish `model/<model-id>@<model-version>` and wait for Maven Central.
3. Increment and merge `models/catalog-version.txt`.
4. Publish `models-catalog@<catalog-version>`.
5. Set and merge the new `python/models-standard-version.txt` value, run
   `pythonBuildStandardModels` and `pythonVerifyStandardModelsDistribution`,
   and publish `python-models-standard@<python-model-distribution-version>`.

Never publish the catalog first: its POMs would reference a model version that
consumers cannot resolve yet. Never publish the Python package from a commit
that does not contain the intended Java model and catalog versions, because
those identities are embedded in its generated manifest.

The migration from the already published model 1.x line to 2.0.0 is a one-time
exception to runtime-independent dictionary releases. Existing runtimes require
`>=1,<2` and hard-code catalog `2026.1`, so 2.0.0 deliberately remains outside
their dependency range. Publish `radixor-models-standard` 2.0.0 first, then
release updated `radixor` and `radixor-c` runtimes that require `>=2,<3`. After
that migration, later 2.x dictionary releases follow the normal five-step
sequence above without a runtime release.
