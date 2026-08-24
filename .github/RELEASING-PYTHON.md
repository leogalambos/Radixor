# Releasing the Python distributions

This maintainer-only checklist is intentionally outside the public MkDocs site.

The repository descriptors use `0.0.0` as a non-release placeholder. Release
workflows create isolated staging trees and inject the stable version selected
by the tag. Never commit a release-number rewrite of the descriptors.
Standard `.rxc` resources are also generated in that staging tree from the
canonical model sources. The workflow compiles every model twice and rejects
non-deterministic output; generated payload must never be added to Git.
The generated standard-model manifest obtains its aggregate catalog release
identity from `models/catalog-version.txt` and each model version from its own
`model-version.txt`; do not synchronize Python constants manually. The package
exposes `CATALOG_VERSION` directly from that manifest. The catalog identity is
provenance, not a runtime compatibility lock; the manifest schema, compiled
format, and model-distribution major version are the compatibility boundaries.

## Validate without publishing

Read the intended standard-model distribution version from
`python/models-standard-version.txt`. Run **Python Standard Models Release**
manually with that version, then run **Python Native Release** with its intended
release version. Manual validation requires the version to be entered explicitly
and does not publish unless its publish option is selected. **Python C
Distribution Release** installs the exact model distribution in every wheel
test, so its full validation for a new model version runs after that model is
published to PyPI. The native runs must pass Linux x86-64, Linux ARM64, macOS
universal2, and Windows x86-64 where configured.

Before the manual workflow run, validate the standard-model archives locally:

```bash
./gradlew --no-daemon pythonVerifyStandardModelsDistribution
```

For the Linux paths, maintainers can use `act` with rootless Podman and the
event files under `.github/act/`. Do not pass production secrets to `act`.

## Publish in dependency order

All tags must point to a commit already contained in `main`. Set the intended
versions explicitly. When creating a new standard-model release, its version
must match the tracked version file. The recovery-only option that republishes
an already immutable GitHub Release to PyPI may instead name that existing
historical release version.

If a failed workflow produced no release artifact or index entry, delete its
tag locally and remotely before recreating the same unpublished version on the
corrected commit. Never move an existing remote tag, and never reuse a version
for which any artifact was published.

```bash
MODEL_VERSION="$(tools/read-python-models-standard-version.sh)"
PYTHON_VERSION='REPLACE_WITH_PYTHON_VERSION'
PYTHON_C_VERSION='REPLACE_WITH_PYTHON_C_VERSION'

git tag -a "python-models-standard@${MODEL_VERSION}" \
  -m "Python standard models ${MODEL_VERSION}"
git push origin "python-models-standard@${MODEL_VERSION}"
```

Wait until the models workflow has published its non-draft GitHub Release,
PyPI distribution, and Pages index entry. Then publish the native Python
distributions one at a time, waiting for each workflow to finish:

```bash
git tag -a "python@${PYTHON_VERSION}" -m "Python Radixor ${PYTHON_VERSION}"
git push origin "python@${PYTHON_VERSION}"

git tag -a "python-c@${PYTHON_C_VERSION}" \
  -m "Python-C Radixor ${PYTHON_C_VERSION}"
git push origin "python-c@${PYTHON_C_VERSION}"
```

The `python@X.Y.Z` tag starts `python-release.yml` and publishes `radixor`.
The `python-c@X.Y.Z` tag starts `python-c-release.yml` and publishes
`radixor-c`. Each distribution owns its GitHub Release and its entry in the
shared PEP 503 index. Keep their versions aligned for coordinated releases.

Both runtime workflows require the exact tracked standard-model version to be
available as a final GitHub Release and as a non-yanked PyPI artifact. Do not
push either native tag until those checks can pass.

Python runtime and model GitHub Releases intentionally use `make_latest=false`.
The repository-level **Latest release** designation is reserved for the current
Radixor/Java release; use PyPI to resolve the current version of each Python
distribution.

## Bootstrap the radixor-c PyPI project

The distribution name is `radixor-c`; its import package remains `radixor_c`.
It cannot share the existing `radixor` PyPI project because PyPI project names
identify a single independently versioned distribution.

Before pushing the first `python-c@X.Y.Z` release tag:

1. In the GitHub repository settings, create an environment named
   `python-c-pypi`. Apply the same deployment protection rules used for
   `python-pypi` if publishing requires maintainer approval.
2. On PyPI, open account publishing settings and add a pending trusted
   publisher with project name `radixor-c`, owner `leogalambos`, repository
   `Radixor`, workflow `python-c-release.yml`, and environment
   `python-c-pypi`.
3. Push the `python-c@X.Y.Z` tag. The first successful trusted publication
   creates the `radixor-c` project. Do not create or store a PyPI API token.
4. After publication, verify the project owners/maintainers, description,
   release files, and Trusted Publisher entry on PyPI. Enable mandatory 2FA for
   every project owner.

If `radixor-c` is created manually instead of through a pending publisher, add
the same trusted publisher under that project's Publishing settings before the
workflow is run.

## Artifact identity

Python releases use `SHA256SUMS` and GitHub keyless build-provenance
attestations. They do not use the Java Maven OpenPGP key. Verify a downloaded
artifact with:

```bash
sha256sum --check SHA256SUMS
gh attestation verify <artifact> --repo leogalambos/Radixor
```
