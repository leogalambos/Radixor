# Benchmark Results

This section contains the published Radixor benchmark reference set. It is intentionally split into
two layers:

- **benchmark reference pages**, which explain methodology, corpora, environment, candidate
  selection, and the English dictionary coverage experiment;
- **language result pages**, which contain the actual same-language accuracy and throughput tables.

This structure keeps methodology separate from per-language result pages, while preserving all
measured data and the command-class analysis for each Radixor language resource.

## Read This First

Start with [Benchmarking](../benchmarking.md) for the high-level interpretation model. The most
important rule is that speed and exact-root quality must be read together. Many competing stemmers
are intentionally light, minimal, or aggressive; they can be fast because they are not trying to
match dictionary roots with the same precision.

Radixor rows in the refreshed tables use contracted compiled patch tries. Contraction collapses
uniform preferred-command subtrees into accepting leaves, reducing hot lookup depth while preserving
the preferred result measured by the accuracy pass.

## Reference Pages

| Page | Purpose |
| --- | --- |
| [Methodology](reference/methodology.md) | Workload design, normalization, speed metrics, quality metrics, and interpretation rules. |
| [Corpora](reference/corpora.md) | Dictionary row counts, complete quality tokens, already-root tokens, changed speed tokens, and timing token counts. |
| [Environment and reports](reference/environment.md) | Hardware, JVM, JMH settings, report files, and badge/report policy. |
| [English dictionary coverage](reference/english-coverage.md) | Quality/speed operating curve for contracted Radixor tries built from 100% down to 10% of English dictionary rows. |
| [Candidate evaluation](reference/candidates.md) | Included benchmark families and evaluated candidates that were skipped. |

## Language Results

Each language page contains:

- the dictionary corpus size,
- the Radixor patch-command distribution,
- exact-root quality metrics,
- throughput metrics,
- interpretation notes for the compared stemmers.

Open [Language Benchmark Pages](languages/index.md) for the complete language list.

## Key Published Result

The English dictionary coverage benchmark shows the current contracted-trie operating curve. With
the full English dictionary, Radixor reaches `97.478%` all-token exactness and `97.197%`
changed-token exactness at `109.8 ns/token`. Even with a deterministic 10% dictionary slice, it
keeps `92.868%` all-token exactness and `76.516%` changed-token exactness at `90.9 ns/token`.

Those figures should not be reduced to a single speed badge. The professional interpretation is a
quality/speed envelope: the amount and quality of dictionary knowledge affect stemming precision,
while contracted tries reduce lookup cost in uniform regions of the compiled graph.
