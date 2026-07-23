# Linguistic Quality Methodology

This evaluation measures agreement between the relation predicted by a stemmer and the gold-standard relation represented by Radixor dictionary groups. It does not require a generated stem to equal one predetermined lemma string. Runtime performance and linguistic quality are separate measurements.

## Scope and fair-comparison rules

The authoritative Radixor language universe is the reconciliation of registered default model descriptors and `StemmerPatchTrieLoader.Language`. Radixor is evaluated for every reconciled language. Optional models are separate comparison rows. A third-party adapter is evaluated only for languages supported by its tested implementation and having a compatible Radixor dictionary; unsupported combinations are absent rather than assigned zero quality.

Model identity is part of the candidate identity. Default Polish means `pl-pl-unimorph`; optional PoliMorf means `pl-pl-polimorf`. Results for those inputs must not be combined or relabeled, and historical snapshots cannot acquire a newer model identity retroactively.

Within one language and dictionary mode, every adapter receives the same original included forms. A distinct surface string is one evaluated item even when it occurs in several rows; those occurrences become multiple gold-group memberships. Candidate strings use exact `String.equals`, with no evaluation-only lowercasing, normalization, accent removal, or gold-label-aware selection. Adapter preprocessing and lifecycle match the JMH comparison path.

## Gold-standard pairs

Every usable dictionary row contributes one gold-standard group. The groups form an overlapping cover rather than an exclusive partition: one surface form may belong to several groups. For two distinct forms `u` and `v` with membership sets `G(u)` and `G(v)`:

```text
goldRelated(u, v) = (G(u) intersection G(v) is not empty)
```

A pair is counted once even if it shares several groups. Gold-negative pairs have disjoint membership sets. Thus:

- `TP = underPossiblePairs - underErrorPairs`: gold-related pairs correctly related.
- `FN = underErrorPairs`: gold-related pairs incorrectly separated.
- `FP = overErrorPairs`: gold-negative pairs incorrectly related.
- `TN = overPossiblePairs - overErrorPairs`: gold-negative pairs correctly separated.

Under-stemming is Paice's Understemming Index (UI), the false-negative rate among gold-related pairs. Over-stemming is Paice's Overstemming Index (OI), the false-positive rate among gold-negative pairs. The original Paice formulation assumes disjoint lemma groups; this evaluator explicitly generalizes the pair relation to overlapping membership. Their percentages use different denominators and must not be added or averaged without an explicitly defined composite.

## Dictionary-processing modes

- `ALL_WORDS` includes every valid group and preserves every original form.
- `LOWERCASE_GROUPS_ONLY` excludes an entire group if any Unicode code point is uppercase or titlecase. Retained forms are not converted to lowercase. Digits, punctuation, combining marks, and characters without case distinctions do not exclude a group by themselves.

## Output policies

`PRIMARY_OUTPUT` uses the adapter's deterministic primary stem. It defines a strict predicted partition and is the principal direct comparison between implementations.

`ANY_CANDIDATE` is an optimistic oracle-assisted pairwise upper bound. Gold-related pairs succeed when candidate sets intersect. Gold-negative pairs avoid an error whenever a non-colliding candidate selection exists. Selection may differ between pairs, so this policy is not deterministic runtime behaviour and may not correspond to one globally realizable assignment. Because its positive and negative decisions use different oracle conditions, it does not define one confusion matrix; TP/FP/FN/TN and all confusion-derived scores are therefore `n/a`. Its separate under/over error counts and denominators remain defined.

`ALL_CANDIDATES` treats every returned candidate as active. Two forms are related when their candidate sets intersect. Alternatives can recover gold-positive relationships while introducing gold-negative collisions. This overlapping relation need not be transitive or form a partition.

Candidate-aware policies are reported as capability analyses. They are not mixed into the principal `PRIMARY_OUTPUT` ranking.

## Relation metrics

Undefined denominators produce `n/a`, never zero, `NaN`, or infinity. Metrics are calculated from unrounded raw counts and displayed with six decimals.

| Metric | Formula | Range and interpretation | Sensitivity and applicability |
| --- | --- | --- | --- |
| Under-stemming rate | `FN / (TP + FN)` | `[0, 1]`; lower is better. False-negative rate over gold-related pairs. | Sensitive to splitting large gold groups. All policies. |
| Over-stemming rate | `FP / (TN + FP)` | `[0, 1]`; lower is better. False-positive rate over gold-negative pairs. | The denominator is usually very large. All policies. |
| Precision | `TP / (TP + FP)` | `[0, 1]`; higher is better. Fraction of predicted relations that are gold-positive. | Penalizes over-stemming. `PRIMARY_OUTPUT` and `ALL_CANDIDATES`. |
| Recall | `TP / (TP + FN)` | `[0, 1]`; higher is better. Fraction of gold-positive pairs recovered. | Equivalent to one minus the under-stemming rate. `PRIMARY_OUTPUT` and `ALL_CANDIDATES`. |
| Specificity | `TN / (TN + FP)` | `[0, 1]`; higher is better. Fraction of negative pairs separated. | Sensitive to false conflations. `PRIMARY_OUTPUT` and `ALL_CANDIDATES`. |
| Balanced accuracy | `(recall + specificity) / 2` | `[0, 1]`; higher is better. Equal weight for positive and negative classes. | Primary navigation metric; less dominated by TN than ordinary accuracy, but not uniquely authoritative. |
| Pairwise accuracy | `(TP + TN) / (TP + TN + FP + FN)` | `[0, 1]`; higher is better. | Can be dominated by the very large TN class and is not the default ranking metric. |
| Pairwise error rate | `(FP + FN) / (TP + TN + FP + FN)` | `[0, 1]`; lower is better. | Also sensitive to the number of negative pairs. |
| F0.5 | `1.25 TP / (1.25 TP + 0.25 FN + FP)` | `[0, 1]`; higher is better. | Gives greater weight to precision and over-stemming avoidance. |
| F1 | `2 TP / (2 TP + FN + FP)` | `[0, 1]`; higher is better. | Equal precision/recall emphasis. |
| F2 | `5 TP / (5 TP + 4 FN + FP)` | `[0, 1]`; higher is better. | Gives greater weight to recall and under-stemming avoidance. |
| Jaccard | `TP / (TP + FP + FN)` | `[0, 1]`; higher is better. | Excludes TN. All policies. |
| Fowlkes–Mallows | `sqrt(precision * recall)` | `[0, 1]`; higher is better. | Geometric balance of precision and recall. All policies. |
| MCC | `(TP TN - FP FN) / sqrt((TP+FP)(TP+FN)(TN+FP)(TN+FN))` | `[-1, 1]`; higher is better. Uses all four counts. | Informative under imbalance; undefined for a zero product denominator. All policies with policy-specific interpretation. |

The general F-beta formula is `((1 + betaSquared) * TP) / (((1 + betaSquared) * TP) + (betaSquared * FN) + FP)`.

## Inapplicable partition metrics

Standard Adjusted Rand Index, homogeneity, completeness, V-measure, and normalized mutual information are not calculated. Their ordinary contingency-table definitions require every item to have one exclusive gold label. Assigning an arbitrary single label or duplicating a multi-membership form would change the scientific question and reintroduce the counting defect this methodology avoids. A future overlapping-clustering index would require a separately specified random model and interpretation; it must not be labelled as ordinary ARI or NMI.

## Aggregation and ranking

Macro metrics average defined per-language values, giving each language equal weight. Micro metrics sum TP, FP, FN, and TN before calculating a metric. Cross-stemmer aggregate comparisons require the exact common supported-language intersection; unsupported languages are not zero-filled.

Language tables sort by unrounded balanced accuracy, then MCC, F1, over-stemming rate, over-stemming error count, under-stemming rate, stemmer name, and stable policy order. Display rounding never controls rank.

Multiple metrics and Pearson/Spearman correlation datasets are published because metric suitability and correlation remain analytical questions. Strong correlation does not establish equivalence.

## Limitations

Dictionary groups encode the available annotation, not every linguistic distinction. Homographs and polyfunctional forms may have several memberships, singleton rows contribute no relation by themselves, and group size affects pair counts. `ANY_CANDIDATE` is optimistic; `ALL_CANDIDATES` measures an overlapping graph; neither is a deterministic global assignment. Results characterize the tested versions, adapters, dictionaries, and preprocessing, not every deployment or domain.
