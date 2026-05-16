# Test Tag Taxonomy and Execution Guide

Radixor uses JUnit tags as an explicit execution policy for its test suite.

The project uses three orthogonal axes:

1. **Scope** (how the test is executed in the pipeline)
2. **Domain** (where in the system it belongs)
3. **Intent** (what behavior it verifies)

## Canonical scope tags

| Tag | Description | Typical usage |
| --- | --- | --- |
| `unit` | Fast, deterministic tests that exercise a specific class or behavior without external processes. | Default developer feedback; should stay near-zero flakiness and low run time. |
| `integration` | Tests that span multiple components or end-to-end flows of the public pipeline. | Parser/loader/CLI/IO integration checks and multi-step compile-then-load validations. |
| `property` | Property-based tests with generator-driven coverage for invariants. | Semantics-preserving laws and edge-case exploration beyond curated fixtures. |
| `fuzz` | Randomized stress checks with bounded runtime. | Heavier probabilistic verification of robustness and reduction invariants. |
| `compat` | Backward/forward compatibility and reproducibility checks for persisted artifacts. | Artifact fingerprints, deterministic rebuild, and regression fixtures. |
| `slow` | Long-running or expensive tests that should not execute in every fast gate. | Heavy fuzz/property budgets or high-duration integration checks. |

## Canonical domain tags

| Tag | Description | Typical usage |
| --- | --- | --- |
| `core` | Core algorithm and foundational platform behavior. | Traversal direction, base data structures, low-level helpers. |
| `trie` | All mutable/compiled trie behaviors and traversal internals. | Lookup path selection, node shape, child representation, subtree behavior. |
| `frequency-trie` | Algorithms and corner cases specific to frequency-aware trie logic. | Ranking, weighted reductions, persistence of weighted nodes. |
| `stemmer` | End-user stemming pipeline semantics. | Parse-encode-apply flows and output invariants. |
| `patch` | Patch encoding, decoding, and application semantics. | `PatchCommandEncoder` behavior and related compatibility contracts. |
| `io` | Input/output and resource loading boundaries. | Filesystem readers, streams, and stream lifecycle handling. |
| `serialization` | Binary persistence contract of compiled artifacts. | Versioned format reads/writes and checksum/consistency checks. |
| `parser` | Dictionary and metadata parsing concerns. | Dictionary input parsing and malformed-source rejection. |
| `cli` | Command-line entrypoint and command orchestration behavior. | Compile CLI integration and CLI argument validation. |
| `metadata` | Trie metadata semantics, compatibility fields, and schema expectations. | Version flags, structural properties, and metadata round-trips. |
| `compile` | Compile-time pipeline and build-oriented behavior. | Building, reduction-mode behavior, and compiled artifact generation. |
| `diacritic` | Unicode diacritic normalization and stripping behavior. | Accent-removal correctness and locale-safe normalization checks. |

## Canonical intent tags

| Tag | Description | Typical usage |
| --- | --- | --- |
| `construction` | Tests around construction and assembly of runtime structures. | Builders, loaders, and compile-time object construction contracts. |
| `lookup` | Read behavior and retrieval semantics. | `get()`, `getAll()`, traversal and missing-key behavior. |
| `persistence` | Storage lifecycle semantics. | Serialization/deserialization and round-trip correctness. |
| `reduction` | Reduction algorithm correctness and corner cases. | Dominance threshold, subtree deduplication, rank-preservation invariants. |
| `encoding` | Encoding transformation direction. | `PatchCommandEncoder.encode` and serialized command form generation. |
| `decoding` | Decoding/interpretation of persisted or runtime commands. | Optional consumers that parse and apply encoded command payloads. |
| `apply` | Patch application and transformation behavior. | Verifies that applied patches produce expected derived forms. |
| `normalization` | Canonicalization and cleanup behavior. | String normalization around case/shape and mirrored input paths. |
| `validation` | Input rejection and defensive checks. | Null/empty/invalid contracts and explicit failure conditions. |
| `regression` | Guard tests for behavior changes over time. | Known historical bugs and behavioral drift prevention. |
| `determinism` | Repeatable results under fixed input and settings. | Compile determinism, stable ordering, and artifact reproducibility. |
| `error-handling` | Exception surface and robustness expectations. | Recovery/failure modes and diagnostics quality. |

## Class-level rules

1. Every test class has **exactly one** scope tag.
2. Every test class has at least one domain tag.
3. Additional tags describe intent and may be used on classes or nested tests.
4. For each test class, intent tags should reflect the primary behavior under test, not historical naming conventions.

## Governance and execution policy

The following rules are used to keep the suite auditable and stable:

| Rule | Required state | Why |
| --- | --- | --- |
| Scope discipline | Exactly one scope tag per class. | Prevents accidental promotion of integration-only behavior into fast unit runs. |
| Coverage breadth | At least one domain tag per class. | Ensures tests can be grouped by subsystem for targeted review. |
| Intent specificity | Use at least one intent tag when behavior is non-trivial. | Makes failure triage faster and profile composition explicit. |
| Runtime policy | Never run `slow` tests in the default `unit` profile unless explicitly required. | Preserves turnaround for PR feedback while preserving deep checks. |
| Change risk | Any persistence or compatibility-affecting change must include `compat` in validation. | Protects long-lived binary artifact contracts. |
| Mutation resistance | `fuzz`/`property` sets should be gated to dedicated profiles. | Limits flakiness exposure and controls CI resource cost. |

## Suggested CI profiles

These are recommended launch profiles for local and CI usage and are also exposed as Gradle tasks:

- **Profile: `ci-smoke` (fast feedback):**

```
./gradlew test -DincludeTags=unit -DexcludeTags=slow
./gradlew ciSmoke
```

`ciSmoke` also excludes `org.egothor.stemmer.CompileIntegrationTest*` at test-name filter level as a
defensive fallback in case of future tag drift.
`ciRelease` also excludes
`org.egothor.stemmer.StemmerPatchTrieLoaderTest$BundledDictionaryTests*` at filter level.

- **Profile: `ci-core` (core behavioral coverage):**

```
./gradlew test -DincludeTags=unit,trie,frequency-trie,property
./gradlew ciCore
```

- **Profile: `ci-integration` (pipeline correctness):**

```
./gradlew test -DincludeTags=integration
./gradlew ciIntegration
```

- **Profile: `ci-slow` (explicit heavy validation):**

```
./gradlew ciSlow
```

- **Profile: `ci-compat` (artifact stability):**

```
./gradlew test -DincludeTags=compat,regression
./gradlew ciCompat
```

- **Profile: `ci-release` (strong confidence before release):**

```
./gradlew test -DexcludeTags=slow
./gradlew ciRelease
```
`ciRelease` is non-slow by policy and uses the same defensive name-based exclusion for
`org.egothor.stemmer.CompileIntegrationTest*` and
`org.egothor.stemmer.StemmerPatchTrieLoaderTest$BundledDictionaryTests*` in addition to tag filtering.

- **Profile: `ci-nightly` (extended hardening):**

```
./gradlew test -DincludeTags=fuzz
./gradlew ciNightly
```

- **Profile: `ci` (enterprise umbrella):**

```
./gradlew ci
```

`ci` and `ciRelease` intentionally do **not** include `slow` paths. Run `ciSlow` explicitly for production-dictionary stress and long-running corpus checks.

## Practical examples

All examples use Gradle with JUnit Platform integration:

- Only unit tests:

```
./gradlew test -DincludeTags=unit
```

- Integration tests only:

```
./gradlew test -DincludeTags=integration
```

- Only trie subsystem tests:

```
./gradlew test -DincludeTags=trie
```

- Deterministic fuzz checks:

```
./gradlew test -DincludeTags=fuzz
```

- Property tests:

```
./gradlew test -DincludeTags=property
```

- Stemmer + patch command behavior:

```
./gradlew test -DincludeTags=stemmer,patch
```

- Compatibility artifacts and regression checks:

```
./gradlew test -DincludeTags=compat
```

- Keep regression suite and remove long-running cases:

```
./gradlew test -DincludeTags=regression -DexcludeTags=slow
```

- Core + patch behavior:

```
./gradlew test -DincludeTags=trie,patch
```

- Deterministic compatibility and persistence checks:

```
./gradlew test -DincludeTags=compat,determinism,serialization
```

## Notes

- `-DincludeTags` and `-DexcludeTags` are interpreted by Gradle task filtering and forwarded into
  JUnit tag filtering.
- Class-name filtering is also available via Gradle test selectors where needed
  (for example, `--tests *CompileTest`), but tag filtering remains the default
  execution strategy.
- `-DincludeTags` supports comma-separated literal tags. When you need a single exact tag with special
  characters, quote the argument for the shell.
