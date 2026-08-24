# Installing and building (Linux, Windows, macOS)

The Python package ships as an **`abi3` wheel** — a single binary per
OS/architecture that works on CPython ≥ 3.9 (including 3.14) through the stable
ABI. Most users just `pip install`; building from source is only needed for
development or unsupported platforms.

## Install from PyPI

PyPI is the primary package index:

```bash
python -m pip install --only-binary=:all: radixor
```

## Install compiled packages from GitHub

Python releases are published as immutable GitHub Release assets. A small
PEP 503 index on GitHub Pages exposes both packages to `pip`:

```bash
python -m pip install --only-binary=:all: \
  --index-url https://leogalambos.github.io/Radixor/python/simple/ radixor
```

The index links directly to checksummed wheel assets in GitHub Releases; Pages
does not duplicate the package files.

Do not configure the GitHub index as an `--extra-index-url`: `pip` does not
prioritize one index over another. Use it as the sole `--index-url`, as shown
above. The binary-only constraint also prevents an accidental source build
with an unprepared toolchain.

Wheels are provided for Linux (`manylinux`), Windows, and macOS
(x86‑64 and Apple Silicon). A source distribution is also published; installing
it triggers a source build, which needs the toolchain described below.

## Install or build from the GitHub source repository

Building requires the **Rust toolchain**, a linker for the target platform, and
**maturin**. The crate and its dependencies contain no project C/C++ sources,
but the selected Rust target still needs its normal platform linker and SDK.

```bash
git clone https://github.com/leogalambos/Radixor
cd Radixor
python -m venv python/.venv
# activate the venv (see per-OS note below)
pip install maturin build setuptools wheel pytest
./gradlew pythonBuildStandardModels
pip install --no-deps build/python/dist/standard/radixor_models_standard-0.0.0-py3-none-any.whl
cd python
maturin develop --release        # compile + install into the venv
pytest -q                        # run the test suite
```

For a reproducible application build, check out a release tag or exact commit
instead of a moving branch. Repository descriptors deliberately use the
non-release placeholder `0.0.0`; release workflows inject the version from the
Git tag into isolated staging trees. Consequently, source-checkout development
installs use `--no-deps` for the generated development model wheel, while
published packages carry normal release versions and dependency resolution
works automatically.

The native distribution requires
`radixor-models-standard>=2.0,<3.0`; an installation of `radixor`
resolves it automatically. The local `--no-deps` command installs the generated
data wheel for development without contacting a package index.

The installed package source (package index, environment, and `sys.path`) is
the model-provider trust boundary. Manifest SHA-256 checks detect accidental
corruption after installation; they do not authenticate a malicious provider.

## Integrity and provenance

Every GitHub Release contains `SHA256SUMS` for its wheel and source archives.
The release workflows also create GitHub artifact attestations for those
archives. After downloading a release, maintainers and users can verify it with:

```bash
sha256sum --check SHA256SUMS
gh attestation verify "radixor-<version>-<wheel-tags>.whl" \
  --repo leogalambos/Radixor \
  --signer-workflow leogalambos/Radixor/.github/workflows/python-release.yml \
  --source-ref "refs/tags/python@<version>"
```

Python packages do **not** reuse the OpenPGP key configured for Java/Maven
Central publications. Java's `SIGNING_KEY` and `SIGNING_PASSWORD` produce Maven
signatures; Python uses release checksums, GitHub identity-bound build
provenance, and PyPI Trusted Publishing with supported digital attestations.

## Build through Gradle

From the repository root, the supported build entry point creates the native
wheel/sdist and pure standard-model wheel/sdist:

```bash
./gradlew pythonBuild
```

`pythonBuild` is the supported one-shot entry point for the full distribution build.
It already runs `pythonBuildNativeWheel` internally first, so running
`./gradlew pythonBuildNativeWheel` manually is only needed for isolated
troubleshooting of the native-wheel task, not for the normal build path.

If you ever hit an intermittent error like this after a clean checkout:

```text
Expected exactly one host Python wheel, found 0 in .../build/python/dist/host
```

run the native wheel task explicitly once and then retry:

```bash
./gradlew clean pythonBuildNativeWheel --no-build-cache
./gradlew pythonBuild --no-build-cache
```

This workaround is now handled automatically by `pythonBuild` when the host wheel is
missing from the expected location, but this sequence remains useful for local
diagnostics when investigating unusual workspace state.

Artifacts are written below `build/python/dist/`; they are not installed into
the invoking interpreter. `./gradlew pythonVerifyDistributions` also validates
archive contents, dependency metadata, checksums, v7 headers, and a fresh
offline wheel-only installation. Platform convenience tasks are also available:

```bash
./gradlew pythonBuildLinux
./gradlew pythonBuildWindows
./gradlew pythonBuildMacos
```

The task matching the current host delegates to `pythonBuild`. A non-host task
uses the default Rust target for that operating system and therefore succeeds
only when its Rust target, linker, and platform SDK are installed. Override a
default with `pythonLinuxTarget`, `pythonWindowsTarget`, or `pythonMacosTarget`.
For example:

```bash
./gradlew pythonBuildWindows -PpythonWindowsTarget=x86_64-pc-windows-gnu
```

Use `-PpythonExecutable=/path/to/python` or
`-PmaturinExecutable=/path/to/maturin` when those tools are not on `PATH`.
These Gradle tasks are the repository integration; direct `maturin` commands
remain useful while developing inside `python/`.

### Prerequisites per platform

=== "Linux"

    ```bash
    # Rust (rustup); most distros already ship Python 3.9+
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
    python3 -m venv .venv && source .venv/bin/activate
    pip install maturin pytest
    ```
    Producing distributable `manylinux` wheels is easiest with
    `maturin build --release` inside the official maturin/`manylinux` container.

=== "macOS"

    ```bash
    brew install rustup-init && rustup-init -y      # or: curl https://sh.rustup.rs | sh
    python3 -m venv .venv && source .venv/bin/activate
    pip install maturin pytest
    maturin develop --release
    ```
    Both Apple Silicon (`aarch64-apple-darwin`) and Intel
    (`x86_64-apple-darwin`) are supported; `maturin build --release --target
    universal2-apple-darwin` produces a universal wheel.

=== "Windows"

    ```powershell
    winget install -e --id Rustlang.Rustup
    py -m venv .venv
    .\.venv\Scripts\Activate.ps1
    pip install maturin pytest
    maturin develop --release
    ```
    The self-contained GNU toolchain avoids needing Visual Studio Build Tools:
    ```powershell
    rustup toolchain install stable-x86_64-pc-windows-gnu
    rustup default stable-x86_64-pc-windows-gnu
    ```
    (The MSVC toolchain also works if you already have the C++ Build Tools.)

### Python 3.14 (and newer than your PyO3 knows about)

Because the extension targets the stable ABI, it links against interpreters
newer than the PyO3 version was released for. If a build against a very new
CPython refuses, set the forward-compatibility flag once in the build shell:

=== "Linux / macOS"

    ```bash
    export PYO3_USE_ABI3_FORWARD_COMPATIBILITY=1
    maturin develop --release
    ```

=== "Windows (PowerShell)"

    ```powershell
    $env:PYO3_USE_ABI3_FORWARD_COMPATIBILITY = "1"
    maturin develop --release
    ```

## Verifying the build

```bash
python -c "from radixor import Stemmer; print(Stemmer('en').stem('running'))"   # -> run
pytest -q
```

## Notes and caveats

- **Model packaging.** Neither runtime distribution contains textual
  dictionaries. `radixor-models-standard` ships 20 compiled gzip v7 resources,
  the checksum/provenance manifest, and CC BY-SA 3.0 notices; optional
  `pl-pl-polimorf` is excluded.
- **Catalog compatibility.** The current supported Python runtime accepts the
  documented compatible model-distribution major (`>=2.0,<3.0`) carrying an
  independent model-catalog identity. Missing,
  incompatible, or corrupt data produces an
  actionable error before native loading.
- **Toolchain PATH.** After installing rustup, open a fresh shell (or ensure
  `~/.cargo/bin` is on `PATH`) so `maturin` can find `cargo`/`rustc`.
