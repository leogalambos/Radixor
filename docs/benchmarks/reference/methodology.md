# Benchmark Methodology

The stemmer comparison suite measures Radixor and Java stemmers on the same language and deterministic Radixor dictionary-derived data. Published Radixor rows in this refresh use contracted compiled patch tries, where uniform preferred-command subtrees are collapsed into accepting leaves before the trie is frozen for lookup. For each language, the bundled dictionary resource stores the expected root as the first tab-separated field on a line and its surface forms on the same line. Every single-token field on that line can therefore be paired with the same expected root.

Published stemmer comparison results must come only from benchmark classes matching `.*StemmerComparisonBenchmark.*`. Internal `FrequencyTrie*` microbenchmarks are not part of those results.

## Benchmark Passes

There are two distinct benchmark passes:

- Speed benchmarks process only changed dictionary pairs where `token != expectedRoot`. This removes already-root tokens from timing so a stemmer is measured on words that actually require a transformation. If a language has fewer than 5,000 changed pairs, the complete changed-pair sequence is repeated in stable order until the timing corpus has at least 5,000 tokens. Larger changed-pair corpora are not sampled or truncated.
- Quality benchmarks process the complete dictionary for the language. They report exact agreement over all tokens, exact agreement over changed tokens only, and preservation of tokens that are already roots.

Timing corpora are generated once per JMH JVM and kept in memory as shared `{token, expectedRoot}` arrays. Corpus construction, dictionary loading, trie loading, table loading, and analyzer construction are setup work and are not included in measured benchmark methods.

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

For right-to-left Radixor languages, patch application uses the traversal direction stored in trie metadata. This is required because static backward patch application is not correct for all bundled languages.

## Quality Metric

The quality pass reports exact-root agreement against the expected root from the Radixor dictionary line. It writes to the normal JMH report files:

- `build/reports/jmh/jmh-results.csv`
- `build/reports/jmh/jmh-results.txt`

Accuracy is computed from standard JMH secondary rows:

```text
allExactPercent = correctMatches / evaluatedTokens * 100
changedExactPercent = changedCorrectMatches / changedEvaluatedTokens * 100
rootPreservedPercent = rootPreservedMatches / rootEvaluatedTokens * 100
```

`allExactPercent` uses the complete dictionary. `changedExactPercent` uses only tokens where `token != expectedRoot`. `rootPreservedPercent` measures whether a stemmer leaves already-root dictionary entries unchanged.

Morfologik can emit multiple terms for one input token. The quality benchmark uses the first emitted term for exact-root accounting when no ranking weight is exposed. Throughput benchmarks for Morfologik TokenFilter paths consume all emitted terms.

Quality reports use JMH auxiliary counter rows. Exact-root accounting is deterministic for a fixed corpus and stemmer, so repeated measurement samples duplicate the same counters; documentation uses the counter ratios and does not interpret quality benchmark timing scores.

Pairwise over-stemming, under-stemming, candidate-aware policies, balanced accuracy, and partition comparison are a separate analytical evaluation. See [Linguistic Quality Methodology](linguistic-quality.md); exact-root accuracy must not be interpreted as the complement of pairwise under-stemming.
