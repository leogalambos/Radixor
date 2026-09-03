# Frozen Experiment Specification

## Research question

Does changing only the reference-defined conflation policy used to train Radixor cause a corresponding and generalizable change in the stemmer's **pairwise conflation decisions** on lexical components unseen during training?

## Fixed inputs

- Radixor: `0c3b13f485a9ad0b460c0931e4497ea95bed66a1`
- CISTEM: `7c19867c2e062c8a7d44b394c19573845ac4bd89`
- GS1 blob: `8627bb28b67429f6488f8d017f510327b2c84d1c`
- GS2 blob: `2cb401638a67760f5fec47c8379646bf6d6d1b8e`
- Official `Cistem.py` blob: `dbc90836bb6361712b52b2e504b85c702294a29f`

## Normalization

1. Unicode NFC.
2. Lowercase without locale-specific Turkish behavior; the source is German and the implementation preserves German diacritics and `ß`.
3. No diacritic stripping.
4. Duplicate normalized tokens inside one source class are removed.
5. A normalized form assigned to multiple classes within one policy is considered ambiguous and excluded from the primary experiment.
6. The retained universe is the intersection of unambiguous forms from both policies.

## Policy partitions

Each non-empty CISTEM source line is a class. After restriction to the common unambiguous universe, the surviving classes define partitions `G1` and `G2`.

## Leakage-safe unit

The experimental blocking unit is the partition join `G1 v G2`: the connected components obtained by linking any two forms that are in the same class under either policy.

A join component must never be split between train and test data.

## Preflight before fitting

The experiment records:

- normalized form counts and ambiguity exclusions;
- common-universe size;
- restricted class counts;
- join-component count and size quantiles;
- number of disagreement-bearing components;
- number of `G1-same/G2-different` pairs;
- number of `G2-same/G1-different` pairs;
- largest component share of forms;
- largest component share of disagreement evidence;
- fold loads.

If the join structure is so concentrated that five-fold blocked cross-fitting is not credible, components are **not** split to rescue the experiment.

## Fold construction

Five deterministic folds. Informative components are sorted by descending disagreement-pair count, descending form count, then stable component identifier. They are greedily assigned to the fold with the least disagreement evidence, then least forms, then least components. Zero-disagreement components are balanced by forms afterward.

Both policy models use the identical fold assignment.

## Training intervention

For fold `f`:

- `M_GS1^(f)` is trained from `G1` classes in folds other than `f`;
- `M_GS2^(f)` is trained from `G2` classes in folds other than `f`.

No form from any held-out join component occurs in either fold-specific training dictionary. Each component is therefore present in training for four fold models but is evaluated exactly once using the one model pair for which every form in that component was excluded from fitting. No single cross-fit model sees the complete GS1 or GS2 partition.

## Canonical-target encodings

The class relation is held fixed while the nuisance target string is varied:

1. **Medoid (primary)**: exact Levenshtein medoid; ties by shorter string, then lexical order.
2. **Shortest**: shortest member; ties by lexical order.
3. **Lexical**: lexicographically first normalized member.

Representatives use training-class information only.

## Radixor compiler/runtime invariants

- traversal: `BACKWARD`
- case processing: `LOWERCASE_WITH_LOCALE_ROOT`
- diacritic processing: `AS_IS`
- store original: enabled
- reduction: `MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS`
- persisted binary model loaded by `StemmerPatchTrieLoader.loadBinaryCompiled`
- deterministic preferred result from `FrequencyTrie.getNormalizedString`

No policy-specific runtime code path exists.

## Primary disagreement estimand

For policy-disagreement pair `p`:

- aligned switch: `M_GS1` agrees with GS1 and `M_GS2` agrees with GS2;
- reverse switch: `M_GS1` agrees with GS2 and `M_GS2` agrees with GS1.

For disagreement-bearing join component `c`:

`d_c = (aligned_c - reverse_c) / D_c`

Primary:

`Delta_macro = mean_c(d_c)`

Secondary:

`Delta_micro = (sum aligned - sum reverse) / sum D`

Switch diagnostics:

`switch_coverage = (aligned + reverse) / D`

`aligned_share = aligned / (aligned + reverse)` when at least one switch occurs.

Exact identity:

`d = switch_coverage * (2 * aligned_share - 1)`

The mirrored directional policy-agreement contrasts are exactly equal on `D` and must never be presented as independent tests.

## Uncertainty / stability

Pairs are not treated as IID. The primary unit is the join component.

Report:

- component-macro point estimate;
- 10,000-replicate fixed-seed component-resampling 95% **stability interval**;
- five fold-level component-macro effects;
- leave-one-component-out range;
- pair-micro effect as a weighting sensitivity.

The stability interval is descriptive for this fixed finite resource and is not described as a population confidence interval. No pair-level significance test is used for the primary claim.

## Secondary outcomes

Within each held-out fold only:

- pairwise TP/FP/FN, precision, recall, F1 for each model-policy cell;
- original-CISTEM-style per-gold-class macro precision/recall/F1;
- compiled model SHA-256, trie fingerprint, node count, byte size, persisted metadata.

Cross-fold arbitrary pairs are excluded from full-relation metrics because their outputs come from different fitted fold models.

## Post-execution fixed-rule CISTEM baseline

After the predeclared Radixor intervention completed, a secondary baseline was added to answer a separate absolute-quality question. It does **not** modify the primary estimand, folds, canonical-target encodings, compiler settings, or falsification rules.

- The official CISTEM Python implementation is pinned at the same CISTEM repository commit and Git blob listed above.
- CISTEM receives the exact NFC-lowercase common-universe forms used by the Radixor experiment. Because these inputs are lowercase, CISTEM's capitalization-sensitive branch receives no information unavailable to Radixor.
- CISTEM is a fixed-rule stemmer and is not fitted per fold. The five existing folds are retained only as identical evaluation subsets.
- On each fold and against each gold relation, the baseline reports the same pairwise TP/FP/FN metrics and the same original-CISTEM-style per-class macro precision/recall/F1 used for Radixor secondary evaluation.
- The fold-restricted outputs are `data/derived/cistem_baseline_fold.csv` and `data/derived/cistem_baseline_summary.csv`.
- The same fixed predictions are also scored once over all common-universe forms, producing `data/derived/cistem_baseline_full_universe.csv`. This full restricted-universe scope includes cross-fold false-positive collisions that the fold-restricted scorer necessarily omits.

The fold-restricted baseline supports a relation-scope comparison with cross-fit Radixor; the full-universe row is CISTEM's unrestricted score on the controlled universe. Neither is a leakage-equivalent or policy-intervention control because the fixed CISTEM rules are not retrained under GS1 versus GS2. The original CISTEM study also reports changing the 1,000-class gold-standard samples several times during CISTEM development and evaluating individual rule changes against the gold standards, creating a historical tuning asymmetry relative to Radixor's explicit component holdout.

## Post-execution full-information reconstruction ceiling

A second secondary analysis quantifies the amount of information deliberately removed by component blocking. It does **not** modify the primary experiment.

- For every target encoding and policy, one model is trained on all restricted classes instead of four folds.
- The deterministic canonical assignments are reconstructed from the already generated cross-fit training dictionaries, so this analysis does not introduce a new target-selection rule.
- All common-universe forms are then evaluated with the corresponding all-data model.
- Because every evaluated form occurs in the training dictionary, the policy-matched result is an in-sample reconstruction ceiling rather than a generalization estimate.
- The output is `data/derived/full_information_summary.csv`.
- The manuscript reports `full-information F1 - cross-fit F1` as the **component-held-out generalization gap**. It must not be described as expected production degradation.

The six policy-matched full-information models reconstruct their own partitions exactly under both CISTEM-style macro F1 and pairwise F1.

## Frozen external full-model context

The publication also carries `data/derived/external_full_model_benchmark.csv`, imported from the frozen Radixor snapshot's documented German CISTEM comparison. This table is contextual evidence rather than output of the policy-transfer pipeline.

- Registered `de-de-default` Radixor is trained from German UniMorph, not GS1 or GS2, and its model-data preparation groups inflected forms by lemma.
- The benchmark evaluates the complete public CISTEM gold files with the snapshot's aggregate cluster TP/FP/FN scorer.
- It reports registered Radixor versus CISTEM at F1 0.718140 versus 0.718632 on GS1 and 0.891616 versus 0.842208 on GS2.
- These values must not be numerically subtracted from the component-blocked CISTEM-style macro F1 values because the training source, evaluation universe, and scorer differ.

## Falsification / claim discipline

Do not claim directional adaptation if the primary medoid `Delta_macro <= 0`.

The frozen specification used the qualitative phrase `non-trivial switch coverage` but did not declare a numerical threshold. No post-result threshold may therefore be invented. Coverage is treated as a magnitude diagnostic, and the observed low pair-micro coverage requires the final claim to be described as **sparse**.

A positive primary effect is considered directionally fragile if it:

- reverses sign under shortest or lexical representative encoding;
- is driven by one giant component or one fold;
- has a component-resampling stability interval spanning materially negative values.

Post-review sensitivity analyses may expose already frozen aggregate evidence but may not redefine the primary estimand. The added component-size analysis therefore asks whether the macro direction remains positive across disagreement-mass strata, while the relation-specificity analysis asks whether the nested result can be explained by a uniform change in aggressiveness.

No post-result change to normalization, fold construction, representatives, compiler parameters, or primary estimand is permitted to rescue the claim.

## Post-general-review sensitivity analyses

The first general hard review requested two secondary diagnostics. Both are derived from already frozen aggregate outputs and do not change any model, prediction, fold, target encoding, or primary statistic.

### Relation-specificity diagnostic

`data/derived/policy_specificity_summary.csv` partitions the nested policy evidence into shared-positive relations, GS1-join/GS2-split disagreement relations, and GS1-negative false-positive joins. For the primary medoid encoding, `M_GS2` joins more shared-positive pairs than `M_GS1` while joining far fewer policy-disagreement pairs. This is inconsistent with a simple uniform conservatism shift.

### Component-size sensitivity

`data/derived/component_size_sensitivity.csv` stratifies each target encoding by disagreement-pair mass per component. For medoid, shortest, and lexical encodings, both component-macro and pair-micro directional effects remain positive in all four bins: 1--10, 11--100, 101--1,000, and >1,000 disagreement pairs per component. The primary effect decreases with pair mass but is not a tiny-component artifact.

## Post-execution structural characterization

The following observations were discovered by deterministic audit after the primary experiment was frozen. They do **not** change the predeclared primary estimand, folds, target encodings, compiler configuration, or model predictions.

- The pinned public CISTEM files are partition-only resources. They do not retain a canonical reference stem for each cluster.
- On the common unambiguous universe, every GS2 class is contained in one GS1 class. GS2 is therefore a strict refinement of GS1 and the join partition equals GS1 for this dataset.
- The refinement is substantial rather than cosmetic: 6,165 of 30,215 restricted GS1 classes are split by GS2, accounting for 6,202,030 GS1-within-class pairs.
- The refinement relation already holds on the normalized raw common vocabulary before ambiguity exclusion; it is not created by the primary filter.
- Canonical-target choice is a genuine nuisance variable. The medoid, shortest-member, and lexical-first rules select the same target for fewer than half of the classes in either policy.
- The frozen Radixor runtime trie fingerprint is not used as a reproducibility identity because it is process-dependent for compiled patch-command values in commit `0c3b13f`. Persisted model-file SHA-256 is authoritative.

These diagnostics are reproduced by `scripts/analyze_policy_structure.py` and exported as aggregate-only `data/derived/policy_structure_summary.csv`.

## Regenerating the frozen external full-model benchmark

The contextual registered-Radixor-versus-CISTEM snapshot can be regenerated from the frozen Radixor source tree without selecting a filtered dictionary:

```bash
./gradlew --no-daemon jmhJar writeJmhRuntimeClasspath

java \
  -Djava.io.tmpdir=build/tmp/jmh \
  -cp "$(cat build/reports/jmh/jmh-runtime-classpath.txt)" \
  org.openjdk.jmh.Main \
  '.*GermanGoldstandardStemmerComparisonBenchmark.cistemStyleQuality.*' \
  -p candidateName=GERMAN_RADIXOR,GERMAN_CISTEM \
  -p goldStandardFileName=goldstandard1.txt,goldstandard2.txt \
  -rf csv \
  -rff build/reports/jmh/german-cistem.csv
```

This command uses registered `de-de-default`; do not set `radixor.benchmark.germanDictionary` unless intentionally testing an alternative dictionary.
