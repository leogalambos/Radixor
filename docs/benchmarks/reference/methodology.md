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

## Quality And Search Interpretation

Radixor speed must be interpreted together with exact-root quality. A slower Radixor row must not be read as a simple performance weakness when Radixor is also the row with accuracy close to 100% and competing stemmers are much lower.

Many fast light, minimal, possessive, or aggressive rule-based stemmers are fast because they do much less linguistic work. The measured Radixor cost buys dictionary-trained precision, and that precision is what improves search quality when queries and indexed text are reduced to the same intended roots.

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

Quality reports intentionally use one deterministic measurement iteration without warmup, because exact-root agreement is not a timing metric and repeated precision passes would only duplicate the same counters.
