# Built-in Languages and Default Models

“Supported language” means that Radixor defines a language enum value and publishes a corresponding default model artifact. It does not mean that a dictionary is embedded in the core JAR. Applications add model artifacts explicitly or use the optional standard pack.

The language enum carries language identity, writing direction, a legacy resource-directory name, and the stable default model ID. A model descriptor carries the independently versioned model identity and resource. See [Model Selection and Loading](model-selection-and-loading.md) for the API and the generated [model catalog](stemmer-model-catalog.md) for versions, provenance, checksums, and sizes.

## Defaults and variants

| Language | Enum | Default model ID | Default artifact | Optional variants |
|---|---|---|---|---|
| Czech | `CS_CZ` | `cs-cz-default` | `org.egothor:radixor-model-cs-cz-default` | — |
| Danish | `DA_DK` | `da-dk-default` | `org.egothor:radixor-model-da-dk-default` | — |
| German | `DE_DE` | `de-de-default` | `org.egothor:radixor-model-de-de-default` | — |
| Spanish | `ES_ES` | `es-es-default` | `org.egothor:radixor-model-es-es-default` | — |
| Persian | `FA_IR` | `fa-ir-default` | `org.egothor:radixor-model-fa-ir-default` | — |
| Finnish | `FI_FI` | `fi-fi-default` | `org.egothor:radixor-model-fi-fi-default` | — |
| French | `FR_FR` | `fr-fr-default` | `org.egothor:radixor-model-fr-fr-default` | — |
| Hebrew | `HE_IL` | `he-il-default` | `org.egothor:radixor-model-he-il-default` | — |
| Hungarian | `HU_HU` | `hu-hu-default` | `org.egothor:radixor-model-hu-hu-default` | — |
| Italian | `IT_IT` | `it-it-default` | `org.egothor:radixor-model-it-it-default` | — |
| Norwegian Bokmål | `NB_NO` | `nb-no-default` | `org.egothor:radixor-model-nb-no-default` | — |
| Dutch | `NL_NL` | `nl-nl-default` | `org.egothor:radixor-model-nl-nl-default` | — |
| Norwegian Nynorsk | `NN_NO` | `nn-no-default` | `org.egothor:radixor-model-nn-no-default` | — |
| Polish | `PL_PL` | `pl-pl-unimorph` | `org.egothor:radixor-model-pl-pl-unimorph` | `pl-pl-polimorf` / `org.egothor:radixor-model-pl-pl-polimorf` |
| Portuguese | `PT_PT` | `pt-pt-default` | `org.egothor:radixor-model-pt-pt-default` | — |
| Russian | `RU_RU` | `ru-ru-default` | `org.egothor:radixor-model-ru-ru-default` | — |
| Swedish | `SV_SE` | `sv-se-default` | `org.egothor:radixor-model-sv-se-default` | — |
| Ukrainian | `UK_UA` | `uk-ua-default` | `org.egothor:radixor-model-uk-ua-default` | — |
| English | `US_UK` | `us-uk-default` | `org.egothor:radixor-model-us-uk-default` | — |
| Yiddish | `YI` | `yi-default` | `org.egothor:radixor-model-yi-default` | — |

The maintained table deliberately avoids duplicating mutable provenance and checksum fields. Those values come from module metadata and are generated into the model catalog.

## The Polish dual-model case

`PL_PL` represents Polish. It is not an alias for either source dictionary.

- `loadCompiled(Language.PL_PL, ...)` resolves `pl-pl-unimorph`.
- `registry.require("pl-pl-polimorf")` resolves the optional PoliMorf model.
- `StemmerPatchTrieLoader.loadCompiled("pl-pl-polimorf", true, reductionMode)` constructs its compiled trie explicitly; complete construction is verified with a dedicated 6 GiB test heap.
- Both artifacts may be present and loaded independently.
- Adding PoliMorf does not change the language default.
- Radixor does not merge their dictionaries or outputs automatically.

UniMorph and PoliMorf have different lexical sources and provenance. Applications should compare outputs with application-specific regression tests before changing an explicit model choice.

## Dependency patterns

Minimal English:

```groovy
dependencies {
    implementation 'org.egothor:radixor:<radixor-version>'
    runtimeOnly 'org.egothor:radixor-model-us-uk-default:1.0.0'
}
```

All documented defaults:

```groovy
dependencies {
    implementation 'org.egothor:radixor:<radixor-version>'
    runtimeOnly 'org.egothor:radixor-models-standard:<catalog-version>'
}
```

The standard pack is metadata-only and excludes optional PoliMorf.

Every individual model artifact carries its own provenance and licensing material. UniMorph
models carry different model-specific CC BY-SA 3.0 notices because their official language
repositories identify different lexical sources and contributors. Each notice preserves upstream
attribution and records the Radixor transformations and Leo Galambos contribution statement.
Legacy imports disclose when an exact historical revision was not recorded; this is a
reproducibility limitation, not a claim that the source or license is unknown.

## Loading a language default

```java
final FrequencyTrie<CompiledPatchCommand> trie =
        StemmerPatchTrieLoader.loadCompiled(
                StemmerPatchTrieLoader.Language.US_UK,
                true,
                ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS);
```

The call discovers the default descriptor from the runtime classpath, verifies its compressed resource, parses the GZip UTF-8 dictionary, and constructs a read-only trie. A missing default throws `StemmerModelNotFoundException`; there is no arbitrary fallback.

## Writing direction

Persian, Hebrew, and Yiddish declare right-to-left language metadata and use forward traversal over stored forms. Other defaults use historical backward Egothor traversal. This setting must remain aligned across dictionary parsing, trie lookup, patch generation, persistence, and application. Model identity remains separate from writing direction.

## Custom and persisted alternatives

Registered model artifacts are a convenient reproducible baseline. Applications may instead load caller-owned textual dictionaries or persist compiled `.radixor.gz` tries. Those paths are distinct from model artifact discovery:

- a model `stemmer.gz` is a compressed textual dictionary plus descriptor/index metadata;
- a `.radixor.gz` created by the binary writer is a persisted compiled trie;
- a source dictionary is upstream input, not automatically a valid model artifact.

See [Dictionary Format](dictionary-format.md), [CLI Compilation](cli-compilation.md), and [Stemmer Models](stemmer-models.md).

## Benchmark interpretation

Benchmark rows must identify the Radixor model ID used. Default rows use the default IDs above. Optional Polish PoliMorf comparisons must be labeled `pl-pl-polimorf`; they are not interchangeable with the historical default Polish row. Continue with [Benchmarking](benchmarking.md) and [Reproducibility](benchmarks/reference/reproducibility.md).
