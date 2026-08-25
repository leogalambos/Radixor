# Filtered Candidate Models

Radixor keeps a set of locally buildable candidate models for dictionaries in
which the baseline compiler found more than 500 distinct patch commands. These
models test whether internally inconsistent stem assignments or objective source
artifacts explain part of the unusually large command vocabulary.

Candidate models are complete model modules under `models/<model-id>`: each has
an independent version, immutable `stemmer.gz`, descriptor metadata, provenance,
and licensing material. They are deliberately not published and do not belong
to the published model catalog, model BOM, standard model pack, Java benchmark
classpath, or Python model packages. Their Gradle publication and release tasks
are disabled. Language convenience methods therefore continue to load the
registered default model. A candidate must be selected by its exact model ID.

## Current candidates

The audit uses the backward `D1I1R1M0` baseline. “Artifacts” counts non-lexical
fields or rows removed during objective sanitation; “mappings” counts semantic
assignments removed by internal-evidence filtering.

| Language | Registered input | Candidate model | Commands before | Commands after | Change | Artifacts | Mappings | Review |
|---|---|---|---:|---:|---:|---:|---:|---:|
| Czech | `cs-cz-default` | `cs-cz-filtered` | 537 | 468 | -12.85% | 22 | 26 | 98 |
| Dutch | `nl-nl-default` | none: no safe change | 538 | 538 | 0.00% | 0 | 0 | 176 |
| Persian | `fa-ir-default` | none: no safe change | 704 | 704 | 0.00% | 0 | 0 | 385 |
| Italian | `it-it-default` | `it-it-filtered` | 750 | 705 | -6.00% | 0 | 56 | 193 |
| Polish | `pl-pl-unimorph` | `pl-pl-unimorph-filtered` | 846 | 843 | -0.35% | 0 | 5 | 224 |
| Spanish | `es-es-default` | `es-es-filtered` | 1,496 | 1,458 | -2.54% | 34 plus one row | 8 | 183 |
| Russian | `ru-ru-default` | `ru-ru-filtered` | 1,840 | 1,821 | -1.03% | 0 | 28 | 286 |
| Finnish | `fi-fi-default` | `fi-fi-filtered` | 2,683 | 2,683 | 0.00% | 0 | 3 | 604 |
| French | `fr-fr-default` | `fr-fr-filtered` | 2,730 | 2,576 | -5.64% | 97 | 75 | 1,115 |
| Hebrew | `he-il-default` | `he-il-filtered` | 4,150 | 4,109 | -0.99% | 41 | 0 | 2,247 |
| Polish | `pl-pl-polimorf` | `pl-pl-polimorf-filtered` | 5,249 | 5,179 | -1.33% | 0 | 104 | 1,686 |
| German | `de-de-default` | `de-de-filtered` | 6,986 | 5,313 | -23.95% | 0 | 2,055 | 960 |

A zero change in distinct commands does not mean that no mapping changed.
Finnish removes three dominated assignments, but their commands remain supported
by other forms. Conversely, the Dutch and Persian audits found no automatic
change that met the conservative rule, so no redundant candidate modules were
created. A large command vocabulary alone is not evidence that a dictionary is
defective.

## Construction and evidence boundary

The first field of a Radixor dictionary row is the canonical stem; subsequent
tab-separated fields must be forms belonging to that stem. Candidate generation
uses only the corresponding dictionary already checked into this repository.
It does not download, copy, or consult a current UniMorph checkout or any other
external vocabulary. The primary source and license of each registered input
remain applicable and are recorded inside the candidate module.

Objective sanitation handles only observations supported directly by the input:

- Unicode format controls with no lexical content;
- isolated export markers before a row's repeated stem;
- an embedded `Citations:` source label;
- non-lexical dash placeholders;
- known metadata fields embedded in a Czech row; and
- a Czech form ending in an exclamation mark.

A semantic mapping is dominated only when all of these conditions hold:

1. the same surface form occurs elsewhere in the same dictionary under a
   different stem;
2. the current patch command occurs at most twice;
3. the current form-to-stem Levenshtein distance is at least four;
4. the alternative stem is at least two edits closer and at most half as far;
   and
5. the alternative command is at least as frequent.

Except for German, at least three dominated forms from one source row must also
agree on the same alternative stem. This protects isolated homonyms, irregular
forms, and suppletive forms from automatic deletion. Retained rare and isolated
cases are written to `audit-evidence.tsv` for human review. The German source
uses the strong individual-mapping rule without the three-form group condition;
its quality measurements are therefore reported separately below.

The 523 German quote-plus-vowel spellings are reported but retained. Earlier
normalization or deduplication experiments changed command frequencies and
ranking, so that orthographic issue remains a separate controlled experiment.

### Multiword fields

The raw PoliMorf input contains 50 rows with whitespace in the canonical field.
The production parser rejects each complete row before patch-command encoding;
they therefore contribute neither to the original 5,249 commands nor to the
filtered 5,179. No audited dictionary contains a whitespace-bearing accepted
variant. The sanitation report records these parser decisions explicitly, but
they are not the cause of PoliMorf's large command vocabulary.

## German external quality comparison

The German candidate was evaluated against both existing CISTEM gold standards.
F1 is derived from raw TP, FP, and FN counters; benchmark timing is not treated
as a quality result.

| Gold standard | Candidate | TP | FP | FN | Precision | Recall | F1 |
|---|---|---:|---:|---:|---:|---:|---:|
| 1 | Registered Radixor | 179,879 | 3,638 | 137,562 | 0.980176 | 0.566653 | 0.718140 |
| 1 | `de-de-filtered` | 180,641 | 3,620 | 136,800 | 0.980354 | 0.569054 | 0.720113 |
| 1 | CISTEM | 192,893 | 26,500 | 124,548 | 0.879212 | 0.607650 | 0.718632 |
| 2 | Registered Radixor | 266,098 | 20,724 | 43,969 | 0.927746 | 0.858195 | 0.891616 |
| 2 | `de-de-filtered` | 267,576 | 20,703 | 42,491 | 0.928184 | 0.862962 | 0.894386 |
| 2 | CISTEM | 286,002 | 83,103 | 24,065 | 0.774853 | 0.922388 | 0.842208 |

The table preserves the complete comparison outcome for both gold standards;
the raw counters, rather than a qualitative label, are authoritative.

## Regenerate and validate

Candidate generation writes raw reports under
`build/reports/generalization/dictionary-meaning-audit/` and regenerates only
the ten candidate `stemmer.gz` inputs. It never overwrites a registered model.

```bash
./gradlew --no-daemon dictionaryMeaningAudit
./gradlew --no-daemon checkFilteredModels
```

The report files are:

- `audit-summary.csv`: command counts, trie structure, and internal quality;
- `audit-evidence.tsv`: removed and retained semantic candidates; and
- `audit-sanitation.tsv`: objective sanitation and parser rejections.

Build one candidate independently:

```bash
./gradlew --no-daemon :models:de-de-filtered:check
./gradlew --no-daemon :models:de-de-filtered:jar
```

The resulting local JAR is
`models/de-de-filtered/build/libs/radixor-model-de-de-filtered-1.0.0.jar`.
Add that file to an application's runtime classpath; do not use nonexistent
Maven coordinates for an unpublished candidate. Select it explicitly:

```java
final FrequencyTrie<CompiledPatchCommand> german =
        StemmerPatchTrieLoader.loadCompiled(
                "de-de-filtered",
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
```

Using `loadCompiled(Language.DE_DE, ...)` still selects `de-de-default`, which
is intentional while the candidate remains unapproved.

Repeat the German external comparison after regeneration:

```bash
./gradlew --no-daemon jmhJar writeJmhRuntimeClasspath

java \
  -Djava.io.tmpdir=build/tmp/jmh \
  -Dradixor.benchmark.germanDictionary=models/de-de-filtered/src/modelInput/stemmer.gz \
  -cp "$(cat build/reports/jmh/jmh-runtime-classpath.txt)" \
  org.openjdk.jmh.Main \
  '.*GermanGoldstandardStemmerComparisonBenchmark.cistemStyleQuality.*' \
  -p candidateName=GERMAN_RADIXOR,GERMAN_CISTEM \
  -p goldStandardFileName=goldstandard1.txt,goldstandard2.txt \
  -rf csv \
  -rff build/reports/jmh/german-cistem-filtered.csv
```

## Promotion to a default

A candidate is not promoted merely because it uses fewer commands. Promotion
requires review of retained evidence, language-specific quality validation,
representative application tests, and a check that runtime and trie-size effects
are acceptable. The approved candidate dictionary can then replace the input of
the registered default module in a deliberate model release. That release must
update the default model's own version and provenance, regenerate the checked-in
catalog documentation manually, and follow the normal model and metapackage
release procedure. The candidate module remains outside every published package
until that decision is made.
