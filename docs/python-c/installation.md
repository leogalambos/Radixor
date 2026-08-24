# Installing and Building Radixor-C

## Install from PyPI

```bash
python -m pip install --only-binary=:all: radixor-c
```

Published wheels target CPython 3.10–3.14 on Linux, macOS and Windows. Unlike
the stable-ABI `radixor` distribution, Radixor-C produces a wheel for each
supported CPython version and platform because it is compiled directly against
the CPython C API.

The install resolves `radixor-models-standard>=2.0,<3.0`. That pure-Python data
package is shared with `radixor`; installing both runtimes does not duplicate
the model catalog.

## Integrity and provenance

Every Python-C GitHub Release publishes `SHA256SUMS` and GitHub build-provenance
attestations for its wheels and source archive. After downloading a release,
verify both the file inventory and that the artifact came from the intended
tagged release workflow:

```bash
sha256sum --check SHA256SUMS
gh attestation verify "radixor_c-<version>-<wheel-tags>.whl" \
  --repo leogalambos/Radixor \
  --signer-workflow leogalambos/Radixor/.github/workflows/python-c-release.yml \
  --source-ref "refs/tags/python-c@<version>"
```

Checksums detect corruption; workflow- and tag-bound provenance identifies the
repository release path that produced the downloaded artifact. Neither makes
an artifact from an untrusted source safe merely because its filename matches.

## Build from the repository

A C compiler, matching Python development headers and Python build tooling are
required. From the repository root:

```bash
./gradlew pythonCBuild
```

The task builds the wheel and source distribution without installing them.
For direct extension development:

```bash
python -m pip install build setuptools wheel
python -m build python-c
```

No Rust or Java runtime is required to use the resulting package. Java/Gradle
remains the repository orchestration entry point and is also one option for
preparing custom compiled models.

## Verify

```bash
python -c "from radixor_c import Stemmer; print(Stemmer('en').stem('running'))"
```

Expected output is `run`.
