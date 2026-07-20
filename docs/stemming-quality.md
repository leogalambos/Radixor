# Stemming quality evaluation

The explicit `stemmingQuality` analysis measures agreement between stemmer outputs and gold-standard equivalence classes represented by bundled multilingual dictionary rows. Dictionary text remains unchanged; reports and diagnostics use English.

JMH adapters, registries, third-party versions, language mappings, and preparation remain in `src/jmh`. The evaluator, reports, audits, and tests reside in the standard `src/test` source set. The former `src/stemmingQualityTest` source set was removed, and neither analytical nor JMH classes enter the production JAR.

## Language and adapter coverage

The authoritative Radixor universe is the validated one-to-one reconciliation of `src/main/resources/*/stemmer.gz` and every `StemmerPatchTrieLoader.Language` value. All 20 current values have exactly one resource; no sentinel or alias is excluded. Radixor is evaluated for all 20 languages, independently of third-party support. Third-party combinations come only from explicit JMH adapter metadata. Unsupported combinations are documented and never fabricated as zero-valued rows.

The expected matrix is constructed before evaluation from stemmer, language, dictionary mode, and supported output policy. Generation fails on missing, duplicate, unexpected, or stale keys.

## Dictionary groups and modes

Every usable parsed row is one gold-standard group. Exact duplicate strings are removed only within that row; identical forms in different rows remain distinct. `ALL_WORDS` preserves every valid form. `LOWERCASE_GROUPS_ONLY` excludes a complete group containing an uppercase or titlecase Unicode code point. Retained words are not lowercased or normalized by the evaluator.

## Output policies

`PRIMARY_OUTPUT` uses the deterministic JMH output and defines a strict partition.

For multi-output adapters, `C(w)` is the immutable, sorted, exactly deduplicated candidate set. It is non-null, non-empty, contains no null, and contains the primary output. Radixor obtains alternatives through `getAll`. The repository's Morphologik lookups can return distinct lemma strings and are multi-output. Configured Hunspell filters can emit several stems at one token position. Other adapters emit only primary rows.

`ANY_CANDIDATE` is an optimistic oracle-assisted pairwise upper bound. A same-group pair succeeds when its sets intersect. A cross-group pair is an error only when both sets are the same singleton; otherwise unequal candidates can be selected for that pair. Choices may vary between pairs and need not form one realizable global assignment.

`ALL_CANDIDATES` activates every candidate. Two forms are related when their sets intersect, for both same-group and cross-group pairs. This relation can overlap and need not be transitive. A pair sharing several candidates is counted once.

The evaluator verifies:

```text
ANY under <= PRIMARY under
ALL under <= PRIMARY under
ANY under = ALL under
ANY over <= PRIMARY over
ALL over >= PRIMARY over
```

## Pair definitions and efficient counting

For `C2(n) = n(n-1)/2`:

```text
underPossible = sum_g C2(n_g)
overPossible = C2(N) - sum_g C2(n_g)
```

Under-stemming counts unrelated same-group pairs. Over-stemming counts related cross-group pairs. Primary output uses global and per-group stem frequencies. Candidate sets are canonical signatures counted globally and per group. An inverted candidate-to-signature index discovers intersections, and signature pairs shared through several candidates are deduplicated. `ANY_CANDIDATE` over-stemming uses only equal singleton signatures. All pair arithmetic uses checked `long` operations; complete production word pairs are never enumerated.

## Confusion and aggregate metrics

```text
TP = underPossible - underError
FN = underError
FP = overError
TN = overPossible - overError
```

Under-stemming is `FN/(TP+FN)` and over-stemming is `FP/(TN+FP)`; their denominators differ. The CSV also publishes precision, recall, specificity, accuracy, balanced accuracy, F0.5, F1, F2, Jaccard, Fowlkes-Mallows, Matthews correlation coefficient, and pairwise error rate. F0.5 emphasizes precision and over-stemming, F1 balances precision and recall, and F2 emphasizes recall and under-stemming. Accuracy and error rate can be dominated by the large cross-group true-negative population. Metrics use raw counts, not rounded rates. Zero denominators produce `n/a` in Markdown and empty CSV fields.

Only `PRIMARY_OUTPUT` receives partition metrics: Adjusted Rand Index, homogeneity, completeness, V-measure, and normalized mutual information with arithmetic-mean entropy normalization. Candidate policies remain inapplicable rather than being forced into artificial partitions.

Micro summaries sum confusion counts before calculation. Macro summaries average defined language values and retain coverage counts. Common-language comparisons use the exact language intersection and never score unsupported languages as zero. Rankings are separated by policy and metric; the default F0.5 choice is navigation, not a universal scientific preference.

Pearson and average-tie-rank Spearman reports use unrounded values and separate dictionary-mode and output-policy cohorts. Fewer than three observations, undefined inputs, and zero variance produce documented missing values. The reports provide reproducible data and make no automatic scientific conclusion.

## Exact accuracy and pairwise under-stemming

Exact textual accuracy and pairwise grouping use different denominators. One erroneous form in a 12-form group creates 11 erroneous pairs: with 88 singleton groups, exact accuracy can be 99% while pairwise under-stemming is `11/C2(12) = 16.666667%`. Singleton groups affect word accuracy but add no within-group pairs.

## Running the analysis

```bash
./gradlew stemmingQuality
./gradlew stemmingQuality -PstemmingQualityStemmer=Radixor -PstemmingQualityLanguage=DE_DE -PstemmingQualityMode=ALL_WORDS -PstemmingQualityAudit=true
```

Optional properties are `stemmingQualityLanguage`, `stemmingQualityStemmer`, `stemmingQualityMode`, `stemmingQualityOutputPolicy`, `stemmingQualityRankMetric`, `stemmingQualityAudit`, and `stemmingQualityAuditLimit`. Policies are `PRIMARY_OUTPUT`, `ANY_CANDIDATE`, and `ALL_CANDIDATES`. Filtered reports carry `-filtered` and cannot overwrite complete output.

Generated files under `build/reports/stemming-quality/` include `stemming-quality.md`, `stemming-quality.csv`, `metric-correlations-pearson.csv`, `metric-correlations-spearman.csv`, and optional audit Markdown.

## Limitations

These measurements evaluate agreement with the available dictionary grouping. They do not capture every semantic, morphological, downstream, or dataset-specific property. `ANY_CANDIDATE` is optimistic and may not be globally realizable. `ALL_CANDIDATES` measures an overlap graph rather than a partition. Language coverage must remain visible in cross-stemmer comparisons. No single published metric establishes universal superiority; multiple metrics and their correlations are provided for transparent scientific assessment.
