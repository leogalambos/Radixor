# Releasing the Python distributions

This maintainer-only checklist is intentionally outside the public MkDocs site.

The repository descriptors use `0.0.0` as a non-release placeholder. Release
workflows create isolated staging trees and inject the stable version selected
by the tag. Never commit a release-number rewrite of the descriptors.
Standard `.rxc` resources are also generated in that staging tree from the
canonical model sources. The workflow compiles every model twice and rejects
non-deterministic output; generated payload must never be added to Git.

## Validate without publishing

Run **Python Standard Models Release** manually with version `1.0.0`, then run
**Python Native Release** with version `4.1.0`. The default manual mode validates
artifacts without publishing. The native run must pass Linux x86-64, Linux
ARM64, macOS universal2, and Windows x86-64.

For the Linux paths, maintainers can use `act` with rootless Podman and the
event files under `.github/act/`. Do not pass production secrets to `act`.

## Publish in dependency order

Both tags must point to a commit already contained in `main`.

```bash
git tag -a 'python-models-standard@1.0.0' \
  -m 'Python standard models 1.0.0'
git push origin 'python-models-standard@1.0.0'
```

Wait until the models workflow has published its GitHub Release and Pages
index entry. Then publish both native Python distributions:

```bash
git tag -a 'python@4.1.0' -m 'Python Radixor 4.1.0'
git push origin 'python@4.1.0'
```

The `python@X.Y.Z` tag starts both `python-release.yml` and
`python-c-release.yml`. They publish the same version to the independent PyPI
projects `radixor` and `radixor-c`, respectively. Do not create a separate
`python-c@X.Y.Z` tag.

Do not push both tags together: native publication requires the standard-model
release to exist first.

## Bootstrap the radixor-c PyPI project

The distribution name is `radixor-c`; its import package remains `radixor_c`.
It cannot share the existing `radixor` PyPI project because PyPI project names
identify a single independently versioned distribution.

Before pushing the first `python@X.Y.Z` release tag:

1. In the GitHub repository settings, create an environment named
   `python-c-pypi`. Apply the same deployment protection rules used for
   `python-pypi` if publishing requires maintainer approval.
2. On PyPI, open account publishing settings and add a pending trusted
   publisher with project name `radixor-c`, owner `leogalambos`, repository
   `Radixor`, workflow `python-c-release.yml`, and environment
   `python-c-pypi`.
3. Push the normal `python@X.Y.Z` tag. The first successful trusted publication
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
