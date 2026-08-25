# Benchmark Methodology

The stemmer comparison suite measures Radixor and Java stemmers on the same language and deterministic Radixor model dictionary-derived data. Published Radixor rows in this refresh use contracted compiled patch tries, where uniform preferred-command subtrees are collapsed into accepting leaves before the trie is frozen for lookup. For each language, the registered default model resource stores the expected root as the first tab-separated field on a line and its surface forms on the same line. Every single-token field on that line can therefore be paired with the same expected root.

Published speed results come only from the exact method selection retained in `published-speed-benchmarks-2026-08-25.txt`. Internal `FrequencyTrie*` microbenchmarks, quality methods, the CISTEM gold-standard experiment, and the optional `PolishPolimorfStemmerComparisonBenchmark` are not part of those results. The Snowball 3.1.0 matrix includes direct Czech, Persian, and Polish workloads; all published Java comparators were measured in the same refresh.

## Benchmark Passes

There are two distinct benchmark passes:

- Speed benchmarks process only changed dictionary pairs where `token != expectedRoot`. This removes already-root tokens from timing so a stemmer is measured on words that actually require a transformation. If a language has fewer than 5,000 changed pairs, the complete changed-pair sequence is repeated in stable order until the timing corpus has at least 5,000 tokens. Larger changed-pair corpora are not sampled or truncated.
- Quality benchmarks process the complete dictionary for the language. They report exact agreement over all tokens, exact agreement over changed tokens only, and preservation of tokens that are already roots.

Timing corpora are generated once per JMH JVM and kept in memory as shared `{token, expectedRoot}` arrays. Corpus construction, dictionary loading, trie loading, table loading, and analyzer construction are setup work and are not included in measured benchmark methods.

The deterministic and timed workloads are executed separately. Corpus statistics, patch-command counts, exact-root counters, coverage accuracy, and pairwise quality do not use or interpret warmup or runtime scores. Published speed and coverage-speed methods use three independent forks, five one-second warmup iterations and seven one-second measurement iterations per fork, one benchmark thread, and a fixed 6 GiB heap.

Performance is interpreted as average time per input token:

```text
timePerChangedTokenNs = JMH score ns/op / changedTimingTokenCount
```

This is necessary because Radixor dictionaries have different token counts by language.

## Exact-root quality and interpretation

Runtime and exact-root agreement must be interpreted separately. Light, minimal, possessive, and aggressive rule-based implementations deliberately address different scopes and may achieve lower latency by performing fewer transformations. A throughput advantage does not establish higher linguistic quality, and higher dictionary agreement does not establish lower operational cost.

The [English dictionary coverage benchmark](english-coverage.md) shows this operating curve explicitly: contracted tries reduce lookup cost in uniform regions, while reduced dictionary coverage still lowers changed-form precision.

## Normalization Policy

Radixor is measured over dictionary tokens from its own resources: lower-case with `Locale.ROOT`, diacritics preserved. The corpus is normalized during setup, so the Radixor benchmark path uses `FrequencyTrie.getNormalized(CharSequence)` and does not measure redundant lookup-time lowercasing or diacritic normalization.

Lucene TokenFilter paths include required normalization in the measured pipeline. Examples include lower-case normalization for filters requiring lower-case input, German normalization before German light/minimal stemming, and Persian decimal, Arabic, and Persian normalization before Persian stemming. No ASCII folding is applied to Czech or Polish paths, because those Lucene stemmers are diacritic-aware or dictionary/table-backed for those languages. TokenFilter throughput methods materialize each emitted `CharTermAttribute` as a `String` before passing it to the JMH `Blackhole`, so output consumption is easier to inspect and closer to the direct stemmer methods.

Trie metadata records the language writing direction for inspection and interchange. Natural-language suffix models apply their learned patches from the end of the stored Unicode string, independently of whether the script is displayed left-to-right or right-to-left.

## Quality Metric

The quality pass reports exact-root agreement against the expected root from the default-model dictionary line. External-stemmer counters are written locally to:

- `build/reports/jmh/stemmer-accuracy-2026-08-25.csv`
- `build/reports/jmh/stemmer-accuracy-2026-08-25.txt`

The tables in this documentation are verified against the checked-in
[dated accuracy CSV](../data/java-stemmer-accuracy-2026-08-25.csv), not against the mutable local
report directory.

Accuracy is computed from standard JMH secondary rows:

```text
allExactPercent = correctMatches / evaluatedTokens * 100
changedExactPercent = changedCorrectMatches / changedEvaluatedTokens * 100
rootPreservedPercent = rootPreservedMatches / rootEvaluatedTokens * 100
```

`allExactPercent` uses the complete dictionary. `changedExactPercent` uses only tokens where `token != expectedRoot`. `rootPreservedPercent` measures whether a stemmer leaves already-root dictionary entries unchanged.

Morfologik can emit multiple terms for one input token. The quality benchmark uses the first emitted term for exact-root accounting when no ranking weight is exposed. Throughput benchmarks for Morfologik TokenFilter paths consume all emitted terms.

External-stemmer quality reports use JMH auxiliary counter rows from one deterministic evaluation. Radixor exact-root counts are computed directly while the default-model corpus and preferred patch commands are audited, so all 20 default models have the same coverage even where no older JMH quality adapter existed. Documentation uses counter ratios and does not interpret quality benchmark timing scores.

Pairwise over-stemming, under-stemming, candidate-aware policies, and relation metrics are a separate analytical evaluation. See [Linguistic Quality Methodology](linguistic-quality.md); exact-root accuracy must not be interpreted as the complement of pairwise under-stemming.
Default rows use `Language.defaultModelId()`. Optional variants require a separate model field; `pl-pl-unimorph` and `pl-pl-polimorf` must never share an ambiguous Polish label. The benchmark runtime receives each resource exactly once from its individual model JAR through direct JMH runtime dependencies. See [Model Selection and Loading](../../model-selection-and-loading.md).
