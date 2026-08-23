# Generalization Methodology

The dictionary-family generalization experiment asks a deliberately narrower
question than the competitor benchmarks: how accurately does the production
Radixor/Java algorithm apply transformations learned from selected lexical
families to families withheld from the same model dictionary?

It does not compare competitors. Other stemmers were not trained from the
Radixor dictionaries and do not expose a commensurate training-coverage control.
Their quality and runtime remain in the [language benchmark matrix](../languages/index.md).

## Frozen Split Protocol

The publication uses protocol `radixor-generalization-v1`:

1. Parse the exact registered default model with the production dictionary
   parser and keep each complete dictionary row atomic. A root and all variants
   on its row are therefore always trained or withheld together.
2. Rank rows with a stable 64-bit FNV-derived hash of the protocol version,
   model ID, seed, normalized root, and normalized variants. Physical line
   number is only a deterministic tie-breaker and does not affect the primary
   rank.
3. Take exact-size prefixes for 10%, 20%, …, 100% training coverage. Prefixes
   are nested within each seed: every 10% training row is also in the 20% set,
   and so on.
4. Build the same contracted compiled-patch-command trie used by the Java
   coverage benchmark, including backward traversal from the stored sequence end
   for every suffix-oriented language model.
5. Evaluate every normalized form in the complete dictionary and retain raw
   integer numerators and denominators.

Five seeds were declared before results were inspected:

`2654435761`, `2611923443488327891`, `7046029254386353131`,
`11400714819323198485`, and `15111065706836454659`.

The page reports the median and minimum–maximum split range. The range measures
sensitivity to the selected families; it is not timing uncertainty or a
statistical confidence interval. Five splits are especially important for small
resources such as Persian (69 rows) and Yiddish (802 rows).

## Evaluation Scopes

Three raw scopes are retained:

- **Whole dictionary** includes trained and withheld rows. It is an operating
  curve and preserves comparability with the original English coverage table,
  but it is not isolated generalization evidence.
- **Withheld rows** contains every occurrence from dictionary rows outside the
  training prefix.
- **Unseen surface** is the primary generalization scope. It starts with
  withheld rows and excludes an occurrence if the same normalized surface form
  appears in any selected training row. The raw report records the excluded
  overlap count.

At 100% training there is no held-out population, so withheld and unseen rates
are `n/a`, not zero.

## Metrics

For each scope the report stores raw counts for:

- `All exact`: produced output equals the dictionary root for every evaluated
  occurrence.
- `Changed exact`: exact agreement only where the surface form differs from the
  expected root. This is the headline generalization measure because an
  unchanged root cannot inflate it.
- `Root preserved`: exact agreement where the input already equals the expected
  root.

Percentages are derived only during documentation publication. The all-language
summary first takes the median across five splits for each language, then the
arithmetic mean across languages. This language-macro calculation prevents large
English and Finnish dictionaries from dominating small resources.

## Reproducibility and Validation

Run:

```bash
./gradlew dictionaryGeneralization
./gradlew publishDictionaryGeneralizationDocumentation
./gradlew verifyDictionaryGeneralizationDocumentation
```

The first task writes `build/reports/generalization/dictionary-generalization.csv`.
Publication validates the complete 20 × 10 × 5 matrix, exact selected-row
cardinality, count partitions, overlap arithmetic, model provenance, and the
100% boundary before checking in the CSV and its SHA-256 checksum. Verification
re-renders the page from the checked-in counters and fails if either the page or
checksum is stale.

Every raw row identifies the Radixor/Java version and the independently
versioned model ID, model version, and compressed-resource SHA-256.

## Limitations

This is **within-resource dictionary-family generalization**, not a universal
unknown-word test. It does not establish accuracy for another domain, spelling
errors, unseen scripts, arbitrary compounds, new named entities, or a dictionary
whose annotation conventions differ from the training resource. Repeated
surface forms are removed from the primary unseen scope, but related lexical
families may still share productive morphology—as intended by the question.

Runtime is deliberately excluded. Lookup speed does not establish
generalization, and multiplying the published English JMH design across 200
language/coverage configurations would add roughly 2.5 hours of timing while
answering a separate performance question. The existing
[English coverage deep dive](english-coverage.md) retains its measured JMH speed
curve.
