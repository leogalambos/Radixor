# Stemmer Models

This page defines the model artifact and its maintenance lifecycle. Application developers should begin with [Model Selection and Loading](model-selection-and-loading.md); the generated [model catalog](stemmer-model-catalog.md) is the detailed inventory.

## Terminology

| Term | Definition |
|---|---|
| Radixor core | Java parsing, patch-command, trie, registry, descriptor, and loader code in `org.egothor:radixor` |
| Language | Locale-level identity such as `PL_PL`; not a dictionary or model |
| Model ID | Stable identity of one concrete model, such as `pl-pl-unimorph` |
| Model artifact | Independently versioned JAR containing one descriptor, one runtime dictionary, and licensing material |
| Source dictionary | Upstream lexical or morphological source recorded in provenance |
| Runtime dictionary | GZip-compressed UTF-8 Radixor tab-separated data consumed during trie construction |
| Compiled trie | In-memory lookup structure built by the loader; not the `stemmer.gz` resource |
| Default model | Stable ID selected by a language-oriented loader call |
| Optional model | Discoverable only when installed and selected explicitly; PoliMorf is optional for Polish |
| Filtered alternative | Opt-in, non-default model derived from a registered model; individually published but never selected as a language default |
| Prohibited model | Privately retained, checksum-bound source whose selected data lacks file-applicable redistribution evidence |

Core version, model artifact version, catalog version, source dictionary version, and model format version are separate compatibility axes. Updating Java code need not republish unchanged model bytes; updating one model need not release core or every other model.

## Model artifact identity and layout

A module named `models/<model-id>` publishes:

```text
org.egothor:radixor-model-<model-id>:<model-version>
```

The built PoliMorf JAR has this effective tree:

```text
META-INF/
  LICENSES/PoliMorf-BSD-2-Clause.txt
  MANIFEST.MF
  radixor/
    models.index
    models/pl-pl-polimorf.properties
org/egothor/stemmer/models/pl-pl-polimorf/stemmer.gz
```

Each UniMorph-derived model instead contains one model-specific
`META-INF/NOTICE/<model-id>-data.txt`. That notice records the upstream attribution, the Radixor
transformations and contribution statement, the applicable distribution terms, and the canonical
license URI. Most use CC BY-SA 3.0; Hsilimo uses CC BY-SA 4.0, Japanese uses CC BY 4.0, and
Khaling uses LGPLLR with its complete license text and legible dictionary form packaged. The
repository has no root model-data license because Radixor Java software remains BSD-3-Clause.
PoliMorf retains only its BSD-2-Clause data license.

`models.index` contains the descriptor path. The descriptor contains the exact resource path. No Java provider class is required, and model modules do not compile against a core API.

## Discovery and integrity

`StemmerModelRegistry` asks the selected `ClassLoader` for every `META-INF/radixor/models.index`. It sorts index URLs, validates each non-comment entry, loads the named descriptors, sorts descriptors by model ID, and rejects duplicate IDs. It does not scan arbitrary JAR entries.

Descriptor parsing verifies:

- the model-ID syntax;
- required nonblank runtime properties;
- a known `Language` enum name;
- format `radixor-dictionary-tsv-gzip` and format version `1`;
- the exact namespaced resource path;
- presence of the runtime resource;
- a lowercase 64-character SHA-256 value.

Loading then reads the compressed resource bytes through the descriptor's discovering class loader, compares their SHA-256 digest, opens GZip, parses UTF-8 Radixor dictionary rows, and constructs a trie. Duplicate-ID and checksum checks make selection independent of classpath order.

## Descriptor fields

The convention plugin generates these fields:

| Property | Role | Meaning |
|---|---|---|
| `model.id` | Authoritative runtime identity | Stable model ID |
| `model.version` | Authoritative artifact identity | Independently managed model version |
| `model.language` | Authoritative selection metadata | Existing `Language` enum value |
| `model.displayName` | Display metadata | Human-readable name |
| `model.resource` | Authoritative loading metadata | Namespaced GZip resource |
| `model.default` | Catalog/build declaration | Whether the module declares itself a default; runtime language selection uses `Language.defaultModelId()` |
| `model.format` | Authoritative compatibility metadata | `radixor-dictionary-tsv-gzip` |
| `model.formatVersion` | Authoritative compatibility metadata | Currently `1` |
| `model.sha256` | Authoritative integrity metadata | Digest of the compressed source bytes |
| `model.rightToLeft` | Processing metadata | Language direction recorded by the build |
| `model.caseProcessing` | Processing metadata | `LOWERCASE_WITH_LOCALE_ROOT` |
| `model.diacriticProcessing` | Processing metadata | `AS_IS` |
| `model.storeOriginal` | Processing metadata | Currently `true` |
| `source.name` | Provenance | Source dictionary name |
| `source.version` | Provenance | Upstream version or the legacy-import sentinel |
| `source.project` | Provenance | Upstream project |
| `source.repository` | Provenance | Official language repository |
| `source.dataset` | Provenance | Upstream dataset and lexical-source identity |
| `source.revision` | Provenance | Exact revision or `not-recorded-in-legacy-import` |
| `source.revisionStatus` | Provenance | `recorded` or `not-recorded-in-legacy-import` |
| `source.license` | Provenance | SPDX or project license reference |
| `source.licenseUri` | Provenance | Canonical license URI |
| `source.attribution` | Provenance | Attribution supplied by the official source |
| `source.verificationDate` | Provenance | Date the maintained upstream information was checked |
| `transformations.summary` | Provenance | Material Radixor conversion operations |
| `compiler.radixorVersion` | Provenance | Compiler lineage recorded by the plugin |
| `compiler.radixorCommit` | Provenance | Commit when available; currently `unavailable` |
| `statistics.groups` | Provenance/statistics | Currently `unavailable` |
| `statistics.forms` | Provenance/statistics | Currently `unavailable` |

The current registry consumes the authoritative `model.*` identity, format, resource, and checksum fields. Processing and provenance fields remain packaged for audit and catalog generation but are not all exposed as typed `StemmerModelDescriptor` accessors. The generated catalog is the supported documentation view of source name, version, license, checksum, and size.

## Immutable input to runtime model

The packaging sequence is:

```text
models/<id>/src/modelInput/stemmer.gz
  -> validate GZip, strict UTF-8, rows, metadata, version, and license
  -> copy identical bytes into build/generated/modelResources
  -> generate descriptor, index, and packaged license
  -> package radixor-model-<id>-<version>.jar
  -> discover from the application's runtime classpath
  -> verify checksum, parse dictionary, and build a trie
```

Application runtime never reads `src/modelInput` from a source checkout.

For PoliMorf, the immutable input is exactly:

`models/pl-pl-polimorf/src/modelInput/stemmer.gz`

Its required upstream license is:

`models/pl-pl-polimorf/src/modelInput/LICENSE-BSD-2-Clause.txt`

The final runtime resource is exactly:

`org/egothor/stemmer/models/pl-pl-polimorf/stemmer.gz`

## Aggregate projects

| Project | Published coordinate | Contents and purpose |
|---|---|---|
| `models/standard` | `org.egothor:radixor-models-standard:<catalog-version>` | POM-only aggregate of the 31 IDs in `standard-model-projects.properties` |
| `models/extended` | `org.egothor:radixor-models-extended:<catalog-version>` | POM-only aggregate of the 113 active models outside the 31-model standard set, including PoliMorf |
| `models/filtered` | `org.egothor:radixor-models-filtered:<catalog-version>` | POM-only opt-in aggregate of all 10 filtered, non-default alternatives |
| `models/bom` | `org.egothor:radixor-models-bom:<catalog-version>` | POM-only Maven dependency-management constraints for all 154 distributable model versions |

No aggregate publishes a binary, sources, or Javadoc JAR. The standard,
extended, and filtered POMs resolve model JARs through runtime dependencies;
importing the BOM only manages versions. Aggregate metadata does not relicense
model data: every dependency retains its own packaged notice and license.

The 31 standard models are CC-BY-SA-3.0. Extended contains 109
CC-BY-SA-3.0 models plus one each under CC-BY-SA-4.0 (`hsi-default`),
CC-BY-4.0 (`ja-jp-default`), LGPLLR (`klr-default`), and BSD-2-Clause
(`pl-pl-polimorf`). Filtered contains nine CC-BY-SA-3.0 alternatives and the
BSD-2-Clause `pl-pl-polimorf-filtered` alternative. JMH, tests, and quality
evaluation depend directly on active individual model projects through
non-production Gradle configurations.

The Maven dependency BOM is not a software bill of materials. The root `cyclonedxDirectBom` task generates the project-wide CycloneDX SBOM under `build/reports/sbom/`; it does not write into `models/bom/build/`.

`models/build/` is an ignored Gradle output directory for the implicit lifecycle parent `:models`, not a source module. CycloneDX direct tasks exposed on subprojects by the root plugin are disabled, so the supported build does not write an SBOM there. Aggregate model reports are owned by the root project under `build/reports/models/`; individual model reports and publication files stay under `models/<model-id>/build/`.

Filtered alternatives under `models/*-filtered` are complete, individually
published opt-in modules. They remain outside the active topology, registry,
JMH language defaults, standard and extended aggregates, and Python packages;
the filtered aggregate and BOM expose their exact IDs. Their construction,
measured results, loading procedure, and promotion boundary are documented on the
[Filtered Candidate Models](filtered-models.md) page.

## Model categories and lifecycle

Topology role and aggregate package membership are separate classifications. A
topology role controls default selection; the standard package list independently
selects 31 active defaults, while the remaining active unfiltered models belong
to the extended aggregate. `extended` is therefore a package membership, never
a topology role. A benchmark result never changes distribution or support status.

| Category | Source location and build role | Registry and installation | Publication and packages | Documentation and benchmarks |
| --- | --- | --- | --- | --- |
| Default | Active `models/<id>` with topology role `default` | Registered language default | Individual artifact and BOM/catalog entry; current members are selected by the independent 31-model standard package list | Supported page and canonical benchmark cohort |
| Standalone default | Active `models/<id>` with topology role `standalone` | Registered language default | Individual artifact and BOM/catalog entry; selected members enter standard and all others enter the Java-only extended aggregate | Supported page and canonical benchmark cohort |
| Optional | Active `models/<id>` with role `optional` | Registered only by explicit model ID; never replaces the language default | Individual artifact, BOM/catalog entry, and Java extended aggregate; excluded from Python | Shown separately on the language page and included where the benchmark identifies that exact model |
| Filtered alternative | `models/*-filtered`, listed only in `alternative-model-projects.properties` | Available only through an exact ID after explicit installation; never a language default | Individual artifact, BOM/catalog entry, and separate filtered aggregate; excluded from standard/extended/Python | Opt-in comparison candidate only |
| Prohibited / quarantine | Ignored `models.prohibited/<id>`; absent from every topology | Not registered, supported, distributed, or installable | No artifact, coordinate, BOM/catalog entry, standard package, signing, or release task | A closed guarded runner may produce a separately labeled benchmark-only page from private bytes |

`models/model-projects.properties` is the complete public topology.
`models/model-quarantine.properties` is a non-build evidence ledger: each entry
records the upstream URI and the reason redistribution could not be established.
Normal Gradle configuration rejects any quarantined directory under `models/`
and does not require the private `models.prohibited/` directory to exist.

The guarded benchmark workflow is deliberately not an installation path.
`tools/run-prohibited-model-benchmarks.sh` takes an exclusive lock, verifies the
closed ID set and every source-file SHA-256, temporarily moves those directories
under `models/`, enables only compilation and measurement tasks through the
internal property, and restores them on exit. Publication, signing, release,
catalog, BOM, standard-package, and distribution tasks remain disabled. Its
separate pages state that the dictionaries are unavailable and do not publish
coordinates or download instructions.

## Create or update a model module

1. Choose a stable lowercase model ID matching the module directory.
2. Add `models/<id>/model-version.txt`; do not derive it from core.
3. Apply `org.egothor.radixor.model` in the module build script.
4. Declare `modelId`, `language`, `displayName`, `defaultModel`, repository, dataset, revision and status, license URI, attribution, verification date, and transformations.
5. Put immutable `stemmer.gz` and a model-specific `NOTICE-model-data.txt` under `src/modelInput/`. The notice must identify the applicable data license and canonical URI, upstream attribution, transformations, and derived-data contributions without implying that the core software uses that license.
6. After file-applicable redistribution terms are established, add the module ID and its `default`, `standalone`, or `optional`
   build-topology role to `models/model-projects.properties`. All roles feed
   settings, verification, BOM constraints, the catalog, individual
   publication, tests, and JMH. Standard membership is independently defined by
   `models/standard-model-projects.properties`; remaining active models feed the
   Java extended aggregate. Descriptor metadata remains authoritative for model
   identity and language properties.
7. Run:

```bash
./gradlew --no-daemon ":models:<model-id>:validateModelInput"
./gradlew --no-daemon ":models:<model-id>:prepareModelResources"
./gradlew --no-daemon ":models:<model-id>:verifyModelDescriptor"
./gradlew --no-daemon ":models:<model-id>:verifyModelJar"
./gradlew --no-daemon ":models:<model-id>:check"
./gradlew --no-daemon runtimeModelIntegrationTest "-PmodelId=<model-id>"
```

Validation fails for missing inputs, notices, attribution, repository, revision status, Radixor contribution and transformation disclosures, ShareAlike and no-endorsement statements, notice byte identity, unsafe or mismatched ID, invalid semantic version, invalid GZip/UTF-8, invalid dictionary rows, checksum mismatch, wrong packaged path, duplicate dictionaries, or dictionaries in sources/Javadoc artifacts. The explicit legacy revision sentinel is valid; an absent revision or status is not. The PoliMorf module separately validates its complete BSD-2-Clause license and attribution.

Copying an arbitrary `stemmer.gz` into an application is insufficient: registry discovery requires an index, a valid descriptor, namespaced resource, checksum, version, language, format declaration, and licensing material.

## Release boundaries

| Tag | Publishes | Does not publish |
|---|---|---|
| `release@<core-version>` | Root `org.egothor:radixor` software artifacts | Model JARs, standard pack, or BOM |
| `model/<model-id>@<model-version>` | Exactly the matching independently versioned model | Core, other models, standard pack, BOM, JMH, or full quality suite |
| `models-catalog@<catalog-version>` | Standard, extended, and filtered aggregates plus models BOM | Individual model JARs or core |

Local validation for a PoliMorf model release is:

```bash
./tools/parse-model-release-tag.sh "model/pl-pl-polimorf@<model-version>" .
./gradlew --no-daemon :models:pl-pl-polimorf:check
./gradlew --no-daemon runtimeModelIntegrationTest -PmodelId=pl-pl-polimorf
./gradlew --no-daemon :models:pl-pl-polimorf:validateModelRelease \
  "-PmodelReleaseVersion=<model-version>"
./gradlew --no-daemon :models:pl-pl-polimorf:packageModelReleaseCandidate \
  "-PmodelReleaseVersion=<model-version>"
```

`runtimeModelIntegrationTest` uses an isolated JVM, defaults to a 6 GiB maximum heap, and can be overridden with `-PradixorLargeModelMaxHeap=10g`. For PoliMorf, `validateModelRelease` depends on this complete runtime construction and real stemming smoke verification in addition to descriptor, checksum, license, and package validation. The generic release workflow still selects and publishes only the requested model. The commands above are local validation only; repository owners control tags and publication.

## Documentation and troubleshooting

`publishModelCatalogDocumentation` updates the checked-in catalog used by a direct local `mkdocs serve`.
`prepareMkDocsSource` independently regenerates the same catalog under `build/mkdocs-source/` for the
publication workflow, and `verifyModelCatalogDocumentation` fails when the two copies differ. Rendered
site content remains untracked. For runtime failures, dependency inspection, ClassLoader isolation, and
fat-JAR guidance, see [Model Selection and Loading](model-selection-and-loading.md#troubleshooting).
