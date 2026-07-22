# Linguistic Quality Methodology

This evaluation measures agreement between the relation predicted by a stemmer and the gold-standard relation represented by Radixor dictionary groups. It does not require a generated stem to equal one predetermined lemma string. Runtime performance and linguistic quality are separate measurements.

## Scope and fair-comparison rules

The authoritative Radixor language universe is the reconciliation of registered default model descriptors and `StemmerPatchTrieLoader.Language`. Radixor is evaluated for every reconciled language. Optional models are separate comparison rows. A third-party adapter is evaluated only for languages supported by its tested implementation and having a compatible Radixor dictionary; unsupported combinations are absent rather than assigned zero quality.

Model identity is part of the candidate identity. Default Polish means `pl-pl-unimorph`; optional PoliMorf means `pl-pl-polimorf`. Results for those inputs must not be combined or relabeled, and historical snapshots cannot acquire a newer model identity retroactively.

Within one language and dictionary mode, every adapter receives the same original included forms. Exact duplicates are removed only within one dictionary row. Identical surface forms in different rows remain distinct entries. Candidate strings use exact `String.equals`, with no evaluation-only lowercasing, normalization, accent removal, or gold-label-aware selection. Adapter preprocessing and lifecycle match the JMH comparison path.

## Gold-standard pairs

Every usable dictionary row is a gold-standard equivalence group. An unordered pair from the same row is positive; a pair from different rows is negative. For group size `n`, `C2(n) = n * (n - 1) / 2`.

- `TP = underPossiblePairs - underErrorPairs`: same-group pairs correctly related.
- `FN = underErrorPairs`: same-group pairs incorrectly separated.
- `FP = overErrorPairs`: different-group pairs incorrectly related.
- `TN = overPossiblePairs - overErrorPairs`: different-group pairs correctly separated.

Under-stemming is the false-negative relation among same-group pairs. Over-stemming is the false-positive relation among different-group pairs. Their percentages use different denominators and must not be added or averaged without an explicitly defined composite.

## Dictionary-processing modes

- `ALL_WORDS` includes every valid group and preserves every original form.
- `LOWERCASE_GROUPS_ONLY` excludes an entire group if any Unicode code point is uppercase or titlecase. Retained forms are not converted to lowercase. Digits, punctuation, combining marks, and characters without case distinctions do not exclude a group by themselves.

## Output policies

`PRIMARY_OUTPUT` uses the adapter's deterministic primary stem. It defines a strict predicted partition and is the principal direct comparison between implementations.

`ANY_CANDIDATE` is an optimistic oracle-assisted pairwise upper bound. Same-group pairs succeed when candidate sets intersect. Different-group pairs avoid an error whenever a non-colliding candidate selection exists. Selection may differ between pairs, so this policy is not deterministic runtime behaviour and may not correspond to one globally realizable assignment.

`ALL_CANDIDATES` treats every returned candidate as active. Two forms are related when their candidate sets intersect. Alternatives can recover same-group relationships while introducing cross-group collisions. This overlapping relation need not be transitive or form a partition.

Candidate-aware policies are reported as capability analyses. They are not mixed into the principal `PRIMARY_OUTPUT` ranking.

## Relation metrics

Undefined denominators produce `n/a`, never zero, `NaN`, or infinity. Metrics are calculated from unrounded raw counts and displayed with six decimals.

| Metric | Formula | Range and interpretation | Sensitivity and applicability |
| --- | --- | --- | --- |
| Under-stemming rate | `FN / (TP + FN)` | `[0, 1]`; lower is better. False-negative rate over same-group pairs. | Sensitive to splitting large gold groups. All policies. |
| Over-stemming rate | `FP / (TN + FP)` | `[0, 1]`; lower is better. False-positive rate over different-group pairs. | The denominator is usually very large. All policies. |
| Precision | `TP / (TP + FP)` | `[0, 1]`; higher is better. Fraction of predicted relations that are gold-positive. | Penalizes over-stemming. All policies, with oracle-assisted interpretation for `ANY_CANDIDATE`. |
| Recall | `TP / (TP + FN)` | `[0, 1]`; higher is better. Fraction of gold-positive pairs recovered. | Equivalent to one minus the under-stemming rate. All policies. |
| Specificity | `TN / (TN + FP)` | `[0, 1]`; higher is better. Fraction of negative pairs separated. | Sensitive to cross-group collisions. All policies. |
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

## Partition-only metrics

These metrics apply only to `PRIMARY_OUTPUT`. Candidate relations are not forced into artificial partitions.

- Adjusted Rand Index is the Rand agreement corrected for agreement expected from the gold/predicted contingency-table marginals. Its usual range is `[-1, 1]`, with `1` indicating identical partitions.
- Homogeneity is `1 - H(gold | predicted) / H(gold)`, in `[0, 1]`; each predicted cluster ideally contains one gold group.
- Completeness is `1 - H(predicted | gold) / H(predicted)`, in `[0, 1]`; each gold group ideally maps to one predicted cluster.
- V-measure is the harmonic mean of homogeneity and completeness, in `[0, 1]`.
- Normalized mutual information uses arithmetic-mean entropy normalization: `MI / ((H(gold) + H(predicted)) / 2)`, in `[0, 1]` under this implementation.

Entropy zero cases follow the evaluator's explicit perfect/undefined conventions. Language tables render inapplicable candidate-policy values as `n/a`.

## Aggregation and ranking

Macro metrics average defined per-language values, giving each language equal weight. Micro metrics sum TP, FP, FN, and TN before calculating a metric. Cross-stemmer aggregate comparisons require the exact common supported-language intersection; unsupported languages are not zero-filled.

Language tables sort by unrounded balanced accuracy, then MCC, F1, over-stemming rate, over-stemming error count, under-stemming rate, stemmer name, and stable policy order. Display rounding never controls rank.

Multiple metrics and Pearson/Spearman correlation datasets are published because metric suitability and correlation remain analytical questions. Strong correlation does not establish equivalence.

## Limitations

Dictionary groups encode the available annotation, not every linguistic distinction. Homographs may occur in different groups, singleton rows contribute no under-stemming pair, and group size affects pair counts. `ANY_CANDIDATE` is optimistic; `ALL_CANDIDATES` measures an overlapping graph; neither is a deterministic global assignment. Results characterize the tested versions, adapters, dictionaries, and preprocessing, not every deployment or domain.
