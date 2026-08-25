# Edit-Cost Experiment Methodology

## Question and frozen design

The experiment asks how relative patch-command edit costs affect compiled-trie structure,
command vocabulary, exact-root transfer, and pairwise stemming behavior as dictionary knowledge
increases. Delete, insert, and replace costs use `1, 2, 3, 5, 10`; match uses `0, 1`.
Configurations differing only by a common positive scale are deduplicated, leaving 234 points.
`D1I1R1M0` is the production baseline.

Every label uses the fixed form `D<delete>I<insert>R<replace>M<match>`:

- `D` is the dynamic-programming cost of deleting one source character;
- `I` is the cost of inserting one target character;
- `R` is the cost of replacing one source character with one target character;
- `M` is the cost of keeping an equal source/target character unchanged—the match or skip step.

For example, `D2I5R3M0` means delete cost 2, insert cost 5, replace cost 3, and match cost 0.
The numbers are relative edit-path costs. They are not numbers of patch commands, trie nodes, or
dictionary observations.

For each language, the application also fingerprints the complete ordered sequence of commands
generated for every dictionary pair. Grid points with identical sequences are exactly equivalent:
they build identical tries for every nested subset, not merely statistically similar ones. One
representative is evaluated and every member label is retained in `equivalent_cost_labels`; the
validator expands these classes back to the complete logical 234-point matrix for analysis. Because
scale-equivalent points were already removed, this second collapse measures dictionary-specific
insensitivity to genuinely different relative cost settings. SHA-256 is only a candidate-bucketing
mechanism; membership is confirmed by direct command equality over the complete dictionary.

Every dictionary is ranked by the protocol hash under five predeclared seeds. Nested exact-size
prefixes provide 10% through 100% knowledge. Before evaluation, dictionaries are ordered by their
full-dictionary baseline count of distinct patch commands, smallest first. The report is streamed
through a `.partial` file and flushed after each language, so an intentionally stopped late run
retains completed observations.

## Outcomes

The complete dictionary is always evaluated because that is the operating population whose
generalization is being studied. The primary outcome for partial knowledge is `unseen_changed_exact`:
changed forms from withheld dictionary families after removing any normalized surface already seen
in training. The broader whole and withheld scopes remain diagnostic. At 100% knowledge the unseen
denominator is correctly zero.

Pairwise precision, recall, F0.5/F1/F2, balanced accuracy, MCC, over-stemming, and under-stemming
are calculated from raw pair counts. Trie nodes, edges, depths, dense lookup slots, generated
commands, retained commands, and baseline-relative retained-command ratios describe representation
cost. These structural counts are reproducible representation proxies, not measurements of JVM heap
occupancy or serialized artifact bytes. Undefined ratios remain empty; they are never replaced with
zero.

The unseen pairwise scope is a generalization stress test, not a full-model quality report. It keeps
only forms from withheld dictionary families whose normalized surface was absent from training.
Unseen-family under-stemming is `FN / (TP + FN)` over gold-related form pairs, so one incorrectly
separated form can break several relations and the percentage is not a per-form error rate. Published
unseen-family OI/UI summaries are medians over five splits at each 10%–90% knowledge level. At 100%
knowledge the unseen scope is empty; the central comparison table therefore reports a separate
full-model UI calculated over the complete dictionary.

## Predictor and outcome glossary

`Predictor` and `outcome` describe a quantity's role in one reported association; they are not
permanent types. A predictor is the left-hand quantity whose variation is compared with the
right-hand outcome. For example, `patch_command_ratio` is an outcome in a cost-to-representation
association and a predictor in a representation-to-quality association. These analyses measure
association, not causation. The labels below are also the column identifiers used by the derived
machine-readable tables.

### Edit-cost predictors

| Label | Definition | Unit and interpretation |
| --- | --- | --- |
| `delete_cost` | Dynamic-programming cost of deleting one source character (`D`). | Relative cost; no independently meaningful absolute unit. |
| `insert_cost` | Dynamic-programming cost of inserting one target character (`I`). | Relative cost. |
| `replace_cost` | Dynamic-programming cost of replacing one source character with one target character (`R`). | Relative cost. |
| `match_cost` | Cost of retaining an equal source/target character—the match or skip step (`M`). | Relative cost; zero is valid. |
| `delete_to_insert_ratio` | `delete_cost / insert_cost`. | Dimensionless balance; `1` means equal delete and insert costs. |
| `replace_to_delete_insert` | `replace_cost / (delete_cost + insert_cost)`. | Dimensionless comparison of direct replacement with deletion followed by insertion. |
| `edit_cost_imbalance` | `max(delete_cost, insert_cost, replace_cost) / min(delete_cost, insert_cost, replace_cost)`. Match cost is deliberately excluded because zero is valid. | Dimensionless spread; `1` means equal non-match costs. |

### Dictionary-level quantities

| Label | Definition | Unit and interpretation |
| --- | --- | --- |
| `dictionary_rows` | Number of source dictionary rows; each row defines one stem family. | Rows/families. |
| `dictionary_forms` | Number of evaluated form occurrences over the complete dictionary, including each row's stem and variants. | Form occurrences; not asserted to be globally unique surfaces. |
| `mean_family_size` | `dictionary_forms / dictionary_rows`. | Mean evaluated forms per stem family. |
| `changed_form_share` | Complete-dictionary proportion of form occurrences whose surface differs from the row's stem. | Ratio in `[0, 1]`. |
| `baseline_patch_commands` | Number of distinct patch-command values retained by the compiled full-dictionary trie under `D1I1R1M0`. | Commands. |
| `exact_equivalence_classes` | Number of command-sequence equivalence classes induced by the 234 normalized cost configurations for one complete dictionary. Configurations share a class only when every generated command agrees in order. | Classes; an outcome of the dictionary-sensitivity analysis and a predictor in the selected-cost analysis. More classes mean greater observed cost sensitivity, not better quality. |

### Compiled-trie representation quantities

| Label | Definition | Unit and interpretation |
| --- | --- | --- |
| `patch_command_ratio` | Candidate trie's distinct retained patch-command count divided by the matching `D1I1R1M0` count for the same language, seed, and knowledge level. | Ratio; `1` equals baseline, below `1` uses fewer distinct commands. |
| `trie_nodes` | Unique physical internal nodes plus unique physical leaf nodes in the reduced trie graph. | Nodes; shared reduced subtrees are counted once. |
| `trie_edges` | Outgoing child edges stored by unique physical nodes. | Edges. |
| `longest_path` | Maximum logical root-to-leaf path length. | Edges traversed. |
| `average_path_length` | Arithmetic mean logical root-to-leaf path length, with each distinct logical path weighted once. | Edges traversed; paths that converge on a shared reduced subtree remain distinct. |
| `dense_table_slots` | Total addressable slots allocated by dense child-lookup tables across unique physical nodes. | Slots, including unoccupied positions inside those tables. |
| `value_references` | Patch-value references stored across unique physical nodes. | References; repeated references to the same distinct value are counted separately. |
| `logical_leaf_paths` | Number of distinct logical root-to-leaf paths represented by the reduced trie graph. | Paths; converging paths remain distinct even when they share physical nodes. |

These quantities are outcomes in cost-to-representation analysis. They can also be predictors in
representation-to-quality analysis. They describe the compiled representation and are not direct
measurements of heap occupancy, serialized file size, or runtime latency.

### Unseen-family quality outcomes

All four labels below use only withheld-family forms whose normalized surface did not occur in the
training subset. They are defined only at 10%–90% knowledge; the unseen scope is empty at 100%.
Pairwise metrics treat two forms as a positive pair when their dictionary-family memberships
intersect and as a negative pair when they are disjoint.

| Label | Definition | Unit and preferred direction |
| --- | --- | --- |
| `unseen_changed_exact` | `100 × unseen_changed_correct / unseen_changed_total`, restricted to surface forms that differ from their expected stem. | Percent of form occurrences; higher is better. |
| `unseen_f05` | Pairwise F0.5 computed from precision and recall in the unseen-family scope. The `β = 0.5` weighting favors precision and therefore penalizes over-stemming more strongly than F1. | Score in `[0, 1]`; higher is better. |
| `unseen_over_percent` | `100 × FP / (TN + FP)` for pairwise relations in the unseen-family scope. | Percent of gold-unrelated pairs incorrectly joined; lower is better. |
| `unseen_under_percent` | `100 × FN / (TP + FN)` for pairwise relations in the unseen-family scope. | Percent of gold-related pairs incorrectly separated; lower is better. This is not a per-form error rate. |

### Selected-cost outcomes

These labels occur in the across-dictionary analysis of the exploratory recommendation selected
for each language from all 45 partial-knowledge observations.

| Label | Definition | Unit and interpretation |
| --- | --- | --- |
| `recommended_delete_cost` | Delete component of the selected cost configuration. | Relative cost. |
| `recommended_insert_cost` | Insert component of the selected cost configuration. | Relative cost. |
| `recommended_replace_cost` | Replace component of the selected cost configuration. | Relative cost. |
| `recommended_match_cost` | Match component of the selected cost configuration. | Relative cost. |
| `recommended_command_ratio` | Median `patch_command_ratio` of the selected configuration across five seeds and nine partial-knowledge levels. | Ratio to the matching baseline. |
| `recommended_exact_delta_pp` | Selected configuration's median `unseen_changed_exact` minus the baseline median over the same 45 observations. | Percentage points; positive means higher exactness than baseline. |

## Analysis protocol

Spearman correlation is primary for monotonic association and Pearson correlation is secondary.
Cost predictors include individual normalized costs, delete/insert balance, replacement cost versus
delete-plus-insert, and edit-cost imbalance. Outcomes include command ratio, trie nodes, edges, path
lengths, dense-table slots, value references, logical leaf paths, and the four unseen-form quality
measures; representation-to-quality associations are also reported. All coefficients are calculated
within language × seed × knowledge strata before their
distribution is summarized, avoiding a pooled language-size or knowledge-level confound. No weak
result is called proof of no effect, and pairs without within-stratum variance are omitted.
Recommendation uses only configurations that remain `VIABLE`
(at most five times the matching baseline command count), a frozen 0.25 percentage-point exactness
tolerance, then command ratio, unseen F0.5, and exactness in that order.

Two additional descriptive selectors expose the objective trade-off without replacing that
recommendation. The structural selector minimizes the distinct patch-command count at 100%
dictionary knowledge; seed invariance is required, and partial-knowledge F0.5, over-stemming, and
under-stemming break equal-count ties. The quality selector considers only configurations that are
`VIABLE` throughout all 45 partial-knowledge observations and do not worsen either median unseen
over-stemming or median unseen under-stemming relative to `D1I1R1M0`. It then maximizes median
unseen F0.5, with lower over-stemming and under-stemming as tie-breakers. These selectors are
post-experiment descriptive optima inside the measured grid, not predeclared production choices.

The central report summarizes all 900 language × seed × partial-knowledge strata. Each language
page also reports a separate distribution across its 45 seed × knowledge strata. A language-level
association is called stable only when the coefficient is defined in all 45 strata and the central
95% empirical interval retains one sign. Partial coverage remains visible but cannot support that
claim. When a quality outcome is constant across cost configurations, the absence of a coefficient
is reported as observed cost insensitivity in this matrix rather than as missing measurement.

The cost grid and recommendation rule are exploratory because the same resources support selection
and reporting. A claim about a new domain requires a separate external dictionary or corpus that
was not used for configuration selection.

## Reproduction

Run the experiment with an explicit stable campaign identity:

```bash
./gradlew --no-daemon \
  -PdictionaryGeneralizationReleaseVersion=<source-identity> \
  editCostSensitivity

python3 tools/update-edit-cost-documentation.py \
  build/reports/generalization/edit-cost-sensitivity.csv docs update
```

On interruption, preserve `edit-cost-sensitivity.csv.partial`; it contains every buffer flushed
through the last completed language plus any subsequently written complete rows. Publication rejects
partial matrices unless `--allow-partial` is explicitly used for a non-publishing pilot check.
