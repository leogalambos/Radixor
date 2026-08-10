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
**Python Native Release** with version `4.1.0`. `workflow_dispatch` validates
artifacts but cannot publish. The native run must pass Linux x86-64, Linux
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
index entry. Then publish the native distribution:

```bash
git tag -a 'python@4.1.0' -m 'Python Radixor 4.1.0'
git push origin 'python@4.1.0'
```

Do not push both tags together: native publication requires the standard-model
release to exist first.

## Artifact identity

Python releases use `SHA256SUMS` and GitHub keyless build-provenance
attestations. They do not use the Java Maven OpenPGP key. Verify a downloaded
artifact with:

```bash
sha256sum --check SHA256SUMS
gh attestation verify <artifact> --repo leogalambos/Radixor
```
