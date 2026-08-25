# Technology and Lineage

Radixor has an unusual position in the stemming ecosystem because its history
predates several implementations with which it is now compared.

The useful way to describe that history is not as a list of project names, but
as a set of **algorithmic lineages and runtime models**.

## The Egothor lineage

The general P-command method used by this lineage was published by Leo Galambos
in [*Lemmatizer for Document Information Retrieval Systems in JAVA*](https://doi.org/10.1007/3-540-45627-9_21)
(SOFSEM 2001). The paper derives compact partial edit commands from a
minimum-cost transformation between a word form and its stem and organizes the
resulting commands in a trie. Egothor implemented this patch-command/trie method
as a compact stemming structure.

Radixor is a modern implementation of that lineage. It is not a binary or source
repackaging of the old project: the current implementation has a new runtime
representation, compiled patch commands, deterministic multi-result semantics,
modern reduction modes, persistence, model packaging, integrity validation, and
current Java/Python integration.

See [Why Radixor Is Different](why-radixor-is-different.md) for the architecture
rather than the chronology.

## Stempel is a historical Egothor branch, not an independent algorithmic lineage

This point is easy to miss when Stempel is encountered through Lucene or
Elasticsearch.

Lucene's own Stempel documentation states that the core stemming algorithm and
implementation were taken **verbatim / virtually unchanged from the Egothor
project**. The Stempel distribution is principally associated with its Polish
stemming tables, even though the underlying algorithm is not inherently
Polish-specific.

That makes Stempel historically important, but it should be interpreted
correctly in Radixor comparisons:

- it demonstrates that the older Egothor technique survived in major search
  infrastructure;
- it is not evidence of a separate later algorithm that Radixor subsequently
  copied;
- benchmarking Stempel against Radixor is effectively a comparison between a
  preserved legacy branch of the technique and its modern re-engineering.

Official Lucene reference:
[StempelStemmer](https://lucene.apache.org/core/9_9_1/analysis/stempel/org/apache/lucene/analysis/stempel/StempelStemmer.html).

In the current Polish benchmark, the direct Stempel path has balanced accuracy
**0.855699** versus **0.991105** for Radixor, and its direct runtime is measured
at **4.229×** the Radixor time. The Lucene StempelFilter path is **4.803×** the
Radixor time. See the [Polish benchmark](benchmarks/languages/polish.md).

## Morfologik is a closed-vocabulary morphological lookup

Morfologik solves a different problem from Radixor. Its runtime is a
dictionary-driven morphological lookup backed by a finite-state automaton (FSA).
The distinction is not merely terminology: it determines what happens when
production text contains a word form that the dictionary does not know.

`DictionaryLookup.lookup(...)` searches the compiled automaton for the supplied
surface form and returns the stored base-form analyses only when that form is
present. If the lookup fails, it returns an empty result. Lucene's
`MorfologikFilter` tries the original token and then its lowercase form; if both
lookups fail, the filter emits the original token unchanged.

There is therefore **no rule-based or learned transformation fallback for an
out-of-vocabulary word**.

Primary-source implementations:

- [Morfologik `DictionaryLookup`](https://github.com/morfologik/morfologik-stemming/blob/master/morfologik-stemming/src/main/java/morfologik/stemming/DictionaryLookup.java)
- [Lucene `MorfologikFilter`](https://github.com/apache/lucene/blob/main/lucene/analysis/morfologik/src/java/org/apache/lucene/analysis/morfologik/MorfologikFilter.java)

This makes dictionary completeness an operational requirement, not just a
quality-tuning parameter. A new domain term, previously unseen inflection,
product name, spelling variant, or other surface form outside the compiled
dictionary receives no morphological reduction from Morfologik. In an
open-vocabulary search system, maintaining coverage therefore requires a
sufficiently comprehensive dictionary and continued dictionary updates.

Radixor uses lexical resources differently. Its source data is **training
evidence for transformations**. Word-to-root relationships are converted into
patch commands, organized in a trie, structurally reduced, and compiled into a
runtime machine. The deployed stemmer selects transformation behaviour and
applies it to the input token; it is not restricted to retrieving a stored
analysis for an exact dictionary member.

This architectural difference matters when interpreting quality numbers.
Morfologik can provide strong analyses for vocabulary covered by its dictionary,
but that strength does not imply generalization to unseen forms. Radixor is
designed to preserve the linguistic evidence of large lexical resources while
turning it into reusable transformation behaviour.

The current Polish benchmark also places the two approaches at very different
points on the measured quality/performance envelope:

- deterministic primary-output balanced accuracy is **0.991105** for Radixor
  and **0.948392** for `MorfologikFilter`;
- when all emitted candidates are considered, balanced accuracy is
  **1.000000** for Radixor and **0.987528** for Morfologik;
- the measured Lucene `MorfologikFilter` runtime is **15.997×** the Radixor
  runtime in the same Java benchmark.

See the [Polish benchmark](benchmarks/languages/polish.md).

The useful conclusion is not that Morfologik is unsophisticated. It is a
morphological dictionary system with a richer analysis objective. The important
engineering distinction is sharper: **it pays the runtime and storage cost of
dictionary/FSA analysis while remaining bounded by dictionary coverage;
Radixor compiles lexical evidence into a substantially smaller hot-path
transformation problem that can also operate beyond explicitly observed word
forms.**

## Snowball and Porter are fixed rule systems

Porter and Snowball form another distinct lineage. Their language algorithms are
explicit rule programs, usually centered on suffix regions and ordered rewrite
rules.

They have a genuine advantage over closed dictionary lookup: their rules
naturally apply to previously unseen words. The trade-off is that the
linguistic behaviour is encoded in the hand-designed rule program itself rather
than learned from lexical evidence.

At `N=100`, the current Python batch benchmark shows that rule-based
generalization does not require accepting a runtime advantage over Radixor:

- each Radixor 4.2.1 Python runtime records lower median processing time than
  PyStemmer 3.1.0 in all **18 / 18** direct language comparisons;
- Python (PyO3) has a **2.19×** geometric-mean speedup and a largest measured
  direct advantage of **4.52×** (Yiddish);
- Python-C has a **2.28×** geometric-mean speedup and a largest measured direct
  advantage of **4.38×** (Yiddish);
- Python (PyO3) spans **6.85–13.87 million words/s**, while Python-C spans
  **6.76–14.63 million words/s**, across all 20 languages at batch size `N=100`.

Those are performance results. The newly integrated official Snowball 3.1.0 Java
quality comparators also make the linguistic trade-off visible for the three
algorithms added in that Snowball generation:

| Language, `ALL_WORDS` | Radixor balanced accuracy | Snowball 3.1.0 balanced accuracy | Radixor OI / UI | Snowball OI / UI |
| --- | ---: | ---: | ---: | ---: |
| Czech | **0.996617** | 0.786366 | **0% / 0.676519%** | 0.000904% / 42.725842% |
| Persian | **0.975610** | 0.535123 | **0% / 4.877973%** | 0.001278% / 92.974054% |
| Polish | **0.991105** | 0.823625 | **0% / 1.779024%** | 0.000967% / 35.273970% |

The lowercase-only evaluation gives the same picture:

- **Czech:** 0.997195 vs 0.784821 balanced accuracy, with UI 0.561033% vs
  43.034822%;
- **Persian:** 0.975610 vs 0.535123, with UI 4.877973% vs 92.974054%;
- **Polish:** 0.991301 vs 0.823465, with UI 1.739895% vs 35.306102%.

The dominant difference is under-stemming rather than excessive conflation.
Snowball's over-stemming remains very low in these measurements, but it leaves
a much larger share of gold-related forms ungrouped. That distinction matters:
a conservative stemmer can look safe when judged only by false conflations
while still sacrificing substantial recall.

Finnish remains another useful illustration. The published `ALL_WORDS`
primary-output quality result is **0.984838** balanced accuracy for Radixor
versus **0.740279** for the Snowball Finnish Lucene path, with under-stemming
**3.032474%** versus **51.944179%**. At `N=100` in the current Python batch run,
Python (PyO3) is **1.30×** faster and Python-C is **1.51×** faster than
PyStemmer's Finnish implementation.

See the [Finnish benchmark](benchmarks/languages/finnish.md), the
[Czech benchmark](benchmarks/languages/czech.md), the
[Persian benchmark](benchmarks/languages/persian.md), and the
[Polish benchmark](benchmarks/languages/polish.md).

The important comparison is therefore not “dictionary versus rules”.
Radixor occupies a third position: **it learns transformations from lexical
evidence, compiles them into a reduced patch-command trie, and retains
algorithmic generalization at runtime without hardcoding a fixed suffix program.**

## Lucene light, minimal, plural, and possessive filters

Several Lucene language filters are intentionally narrow transformations. A
minimal or light stemmer may be extremely fast precisely because it performs
less linguistic conflation.

That is not a defect. It is a different objective.

The important benchmark discipline is therefore:

> do not interpret runtime without the corresponding grouping quality.

A stemmer that removes only a tiny set of endings and a stemmer that attempts
broad morphological conflation are not doing equivalent work merely because
both return a string called a “stem”.

## Hunspell

Hunspell combines dictionaries with affix rules and can produce several
candidate stems. It is another useful comparator because it occupies a middle
ground between direct dictionary analysis and pure suffix stemming.

Its architecture is still different from Radixor's compiled patch-command trie:
Hunspell interprets lexical and affix resources, whereas Radixor has already
compiled observed transformation behaviour into a reduced runtime machine.

## What the current benchmark results justify saying

The project does not need to position Radixor merely as “another stemmer”.

A more accurate statement is:

> **Radixor is a learned transformation stemmer built around reduced
> patch-command tries. Its current public benchmarks show that this architecture
> can move the quality/performance frontier rather than merely trade one for the
> other.**

That is an architectural and empirical claim, not a claim that every alternative
project is poorly designed. Different systems were built for different goals:

| Family | Primary runtime idea | Typical strength | Key distinction from Radixor |
| --- | --- | --- | --- |
| Radixor | Learned patch commands in a reduced compiled trie | High-quality conflation with compact deterministic runtime | Training data compiles into reusable transformations |
| Stempel | Historical Egothor implementation + stemming tables | Proven legacy deployment, especially Polish | Same historical algorithmic lineage; older implementation branch |
| Morfologik | Dictionary/FSA morphological lookup | Rich in-vocabulary lemmatization and multiple analyses | Closed-vocabulary lookup: unknown forms have no stemming fallback; Lucene passes them through unchanged |
| Snowball / Porter | Fixed language rule programs | Portable rule-based stemming with natural OOV coverage | Rules generalize to unseen forms, but are authored rather than learned from lexical evidence |
| Lucene light/minimal | Deliberately narrow handcrafted rules | Very low runtime cost | Intentionally less linguistic work |
| Hunspell | Dictionary + affix rules | Lexical/affix analysis and candidate outputs | Runtime interprets lexicon/affix resources |

The benchmark pages remain the authority for each language and comparator. The
purpose of this page is to make the **technology categories** explicit so readers
do not have to infer them from implementation names.

The current edit-cost experiment also rules out presenting one cost setting or one
generalization slope as universally optimal. Across 20 dictionaries, the normalized grid produces
dictionary-specific exact command classes and the quality associations vary with language,
knowledge level, and split. The [cross-language analysis](benchmarks/edit-cost-sensitivity.md)
therefore links to separate evidence and conclusions on every
[language benchmark page](benchmarks/languages/index.md); selected non-baseline costs remain
external-validation candidates.

## Historical references

- [Leo Galambos, *Lemmatizer for Document Information Retrieval Systems in JAVA*
  (SOFSEM 2001)](https://doi.org/10.1007/3-540-45627-9_21)
- [Lucene StempelStemmer documentation](https://lucene.apache.org/core/9_9_1/analysis/stempel/org/apache/lucene/analysis/stempel/StempelStemmer.html)
- [Lucene Morfologik package documentation](https://lucene.apache.org/core/10_3_2/analysis/morfologik/org/apache/lucene/analysis/morfologik/package-summary.html)
- [Architecture](architecture.md)
- [Benchmark results](benchmarks/index.md)
- [Tested stemmer inventory](benchmarks/reference/tested-stemmers.md)
