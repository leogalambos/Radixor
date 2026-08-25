# Edit-Cost Sensitivity and Generalization

This experiment evaluates the normalized 234-point delete/insert/replace/match cost grid
against deterministic 10%–100% dictionary-knowledge curves for five frozen splits and all
20 default models. Its primary generalization outcome is exact stemming of changed forms
whose normalized surface was absent from training. Pairwise F0.5, over-stemming, and
under-stemming are independent guards against a superficially good exact-root result.

A cost label uses `D<delete>I<insert>R<replace>M<match>`. `D`, `I`, and `R` are the
relative costs of deleting a source character, inserting a target character, and replacing
a source character. `M` is the cost of keeping an equal source/target character unchanged
(the match or skip step). Thus `D2I5R3M0` means costs 2, 5, 3, and 0 respectively; these
numbers are edit-path costs, not counts of generated or retained commands.

## Campaign identity

- Protocol: `radixor-cost-sensitivity-v4`
- Source identity: `4.2.0-6-g84e57fb`
- Git revision: `84e57fb27ae40913569e826858a8ebb07cf2ea01` (dirty)
- Generator SHA-256: `fc6aec8f08c3596a33e7f167ababfd2e583aed0671869bc4a208d75191ccc1df`
- Analysis script SHA-256: `347af5fd023bd1794dd41e891d8b4a80598d2928839a01a96ae47154102dd21f`
- Raw CSV SHA-256: `13ec2dfe0af799c3cf6a4cf9948903a02497f2e7de540627a507ba2950d7f738`
- Exact classes across languages: 334
- Physically measured scenarios: 16,700
- Expanded logical observations: 234,000

The baseline median unseen changed-form exactness across language, seed, and 10%–90%
knowledge observations is **69.478%**. This macro-style statement gives each
scenario equal weight; it is not a token-weighted production estimate.

## Key findings

- The 4,680 language × normalized-cost combinations collapse to 334 exact dictionary-specific command classes, a **14.01×** reduction. The range is 5 classes for Danish through 44 for German.
- Baseline macro unseen changed-form exactness rises from **62.256%** at 10% knowledge to **70.881%** at 90%, a **+8.625 pp** gain.
- The predeclared language-specific selections reduce median command count for **17/20** dictionaries. Across the macro knowledge curve the reduction is 5.01%–6.11% while the largest absolute exactness change is 0.003 pp. The largest language-level median reduction is 26.64% for French.
- Retained-command ratio has a stable positive association with trie nodes and value
  references, so it is a useful structural-size proxy. No cost or representation predictor
  keeps one association sign across the central 95% of strata for any unseen-form quality
  outcome; quality effects are language- and knowledge-dependent.
- Exact-class count is more associated with baseline command vocabulary (Spearman +0.510) than with raw dictionary rows (+0.260). This supports command-aware, not merely size-based, resource classification.

## Dictionary sensitivity census

Common positive scaling has already been removed from the 234-point normalized grid. The
remaining points collapse only when their generated command is identical for every
full-dictionary pair, verified command by command after fingerprint bucketing. More classes
therefore mean that the dictionary exposes more cost-dependent encoder decisions; this is
sensitivity, not automatically better quality.

| Language | Rows | Mean family size | Changed forms | Baseline commands | Exact classes | Grid reduction | Largest class |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Danish | 4,179 | 7.72 | 74.09% | 138 | 5 | 46.80× | 110 |
| Yiddish | 802 | 5.36 | 64.56% | 202 | 21 | 11.14× | 54 |
| Ukrainian | 1,493 | 10.54 | 81.03% | 286 | 7 | 33.43× | 76 |
| Norwegian Nynorsk | 4,688 | 4.19 | 69.01% | 299 | 11 | 21.27× | 77 |
| Norwegian Bokmål | 17,929 | 5.06 | 63.22% | 325 | 14 | 16.71× | 54 |
| Swedish | 12,371 | 8.93 | 77.61% | 332 | 10 | 23.40× | 59 |
| English | 396,939 | 2.53 | 20.80% | 337 | 15 | 15.60× | 54 |
| Portuguese | 4,001 | 53.86 | 96.29% | 369 | 9 | 26.00× | 77 |
| Hungarian | 19,406 | 48.22 | 95.86% | 462 | 12 | 19.50× | 54 |
| Czech | 5,113 | 11.07 | 82.25% | 537 | 12 | 19.50× | 54 |
| Dutch | 4,992 | 6.30 | 68.28% | 538 | 27 | 8.67× | 54 |
| Persian | 69 | 54.64 | 96.34% | 704 | 18 | 13.00× | 54 |
| Italian | 10,009 | 33.72 | 94.07% | 750 | 19 | 12.32× | 54 |
| Polish | 9,990 | 13.24 | 84.92% | 846 | 19 | 12.32× | 54 |
| Spanish | 65,059 | 14.24 | 87.03% | 1,496 | 18 | 13.00× | 54 |
| Russian | 37,410 | 21.55 | 90.72% | 1,840 | 21 | 11.14× | 54 |
| Finnish | 57,027 | 32.71 | 94.07% | 2,683 | 7 | 33.43× | 110 |
| French | 59,240 | 8.00 | 77.19% | 2,730 | 32 | 7.31× | 54 |
| Hebrew | 2,358 | 25.90 | 92.28% | 4,150 | 13 | 18.00× | 59 |
| German | 54,092 | 6.16 | 72.82% | 6,986 | 44 | 5.32× | 54 |

### Dictionary-level associations

The following across-language coefficients describe whether dictionaries with a
given property expose more distinct cost-dependent command sequences. There are only
20 language observations, so these are exploratory effect descriptions,
not causal estimates or evidence that unlisted properties are irrelevant.

| Dictionary property | Outcome | Spearman ρ | Pearson r | Languages |
| --- | --- | ---: | ---: | ---: |
| `dictionary_rows` | `exact_equivalence_classes` | +0.260 | +0.072 | 20 |
| `dictionary_forms` | `exact_equivalence_classes` | +0.088 | -0.063 | 20 |
| `mean_family_size` | `exact_equivalence_classes` | -0.176 | -0.241 | 20 |
| `changed_form_share` | `exact_equivalence_classes` | -0.231 | -0.132 | 20 |
| `baseline_patch_commands` | `exact_equivalence_classes` | +0.510 | +0.639 | 20 |

## Language-specific observed optima

Here, *optimal* always means optimal for the stated objective inside the measured normalized
234-point grid. It does not mean that the same setting is optimal for an unmeasured corpus,
a different dictionary, or a cost ratio outside the grid.

### Smallest full-dictionary patch-command vocabulary

This table minimizes the number of distinct patch commands retained by the compiled trie at
100% dictionary knowledge. All five seeds compile the same complete dictionary and must produce
the same count. When several cost configurations reach that minimum, the displayed representative
is chosen by higher median unseen F0.5 over the 10%–90% observations, then lower median over- and
under-stemming; `Tied minima` records how many grid configurations reach the same minimum count.
The exactness delta and F0.5 columns describe the displayed representative on partial-knowledge
generalization observations and expose the quality consequence of pursuing structural size alone.

| Language | Minimum-command costs | Commands | Baseline | Reduction | Unseen exact Δ | Unseen F0.5 | Tied minima |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Czech | `D1I1R1M1` | 509 | 537 | 5.21% | +0.000 pp | 0.927175 | 25 |
| Danish | `D1I1R2M0` | 123 | 138 | 10.87% | +0.000 pp | 0.878150 | 54 |
| German | `D2I5R3M0` | 6,784 | 6,986 | 2.89% | +0.000 pp | 0.825677 | 2 |
| Spanish | `D1I1R1M1` | 1,283 | 1,496 | 14.24% | +0.000 pp | 0.962789 | 25 |
| Persian | `D1I5R3M1` | 660 | 704 | 6.25% | +0.000 pp | 0.117096 | 15 |
| Finnish | `D1I1R1M1` | 2,033 | 2,683 | 24.23% | +0.005 pp | 0.945461 | 25 |
| French | `D1I1R1M1` | 2,073 | 2,730 | 24.07% | +0.000 pp | 0.915559 | 25 |
| Hebrew | `D2I3R3M0` | 4,023 | 4,150 | 3.06% | +0.022 pp | 0.287004 | 8 |
| Hungarian | `D1I1R2M0` | 411 | 462 | 11.04% | +0.000 pp | 0.970327 | 54 |
| Italian | `D1I1R1M1` | 594 | 750 | 20.80% | +0.000 pp | 0.971928 | 25 |
| Norwegian Bokmål | `D1I1R2M0` | 313 | 325 | 3.69% | -0.004 pp | 0.872959 | 54 |
| Dutch | `D1I2R2M0` | 502 | 538 | 6.69% | +0.005 pp | 0.840309 | 28 |
| Norwegian Nynorsk | `D1I3R3M0` | 299 | 299 | 0.00% | +0.000 pp | 0.874458 | 45 |
| Polish | `D1I1R1M1` | 791 | 846 | 6.50% | +0.000 pp | 0.893496 | 25 |
| Portuguese | `D1I1R1M1` | 305 | 369 | 17.34% | +0.000 pp | 0.975633 | 25 |
| Russian | `D1I1R1M0` | 1,840 | 1,840 | 0.00% | +0.000 pp | 0.946441 | 9 |
| Swedish | `D1I1R1M1` | 317 | 332 | 4.52% | +0.000 pp | 0.906986 | 25 |
| Ukrainian | `D1I1R1M1` | 272 | 286 | 4.90% | +0.000 pp | 0.833220 | 25 |
| English | `D1I1R1M0` | 337 | 337 | 0.00% | +0.000 pp | 0.896140 | 45 |
| Yiddish | `D1I1R2M0` | 201 | 202 | 0.50% | +0.000 pp | 0.771991 | 90 |

Machine-readable values: [minimum-command configurations](data/edit-cost-minimum-commands.csv).

### Best observed pairwise generalization quality on unseen forms

!!! important "This is not full-model production quality"

    `Unseen-family OI` and `Unseen-family UI` are medians over 45 deliberately difficult
    generalization scenarios: five frozen splits at each 10%–90% dictionary-knowledge level.
    Evaluation includes only forms from withheld dictionary families whose normalized surface
    never appeared in training. The values therefore measure how a partial trie connects
    previously unseen members of previously unseen families; they do not describe a model built
    from the complete dictionary.

Under-stemming is pairwise: `UI = FN / (TP + FN)`, where every gold-related pair of forms is
one positive relation. One incorrectly separated form can break several relations inside its
family, so unseen-family UI is neither a per-form error rate nor the complement of exact-root
accuracy. At 100% knowledge no withheld unseen-family population remains. The `Full-model UI`
column therefore supplies a separate reference measured over the complete dictionary with the
displayed cost configuration; it is the relevant column for judging full-model behavior.

Only configurations classified `VIABLE` in every partial-knowledge language × seed observation
are eligible. A candidate must not increase either median unseen-family OI or median unseen-family
UI relative to `D1I1R1M0`. Among those candidates, the table maximizes median unseen F0.5; ties
prefer lower OI, then lower UI, and finally the production baseline. Lower OI/UI values are
better; higher F0.5 is better. Deltas are against the baseline and retain six decimals because
several measured effects are small.

A non-baseline configuration is selected for **7/20** dictionaries. This establishes that edit costs can alter
measured stemming quality for particular dictionaries, but the magnitude and direction remain
dictionary-dependent. Selection and reporting use the same matrix, so these are exploratory
observed optima rather than externally validated production defaults.

| Language | Quality costs | Median unseen-family OI (10%–90%) | Δ OI | Median unseen-family UI (10%–90%) | Δ UI | Full-model UI (100%) | Unseen F0.5 | Δ F0.5 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Czech | `D1I1R1M0` | 0.000017% | +0.000000 pp | 28.122302% | +0.000000 pp | 0.566807% | 0.927175 | +0.000000 |
| Danish | `D1I1R1M0` | 0.000061% | +0.000000 pp | 40.837995% | +0.000000 pp | 0.703039% | 0.878150 | +0.000000 |
| German | `D1I5R3M1` | 0.000063% | -0.000000 pp | 49.497381% | -0.001356 pp | 12.031009% | 0.825677 | +0.000019 |
| Spanish | `D1I1R1M0` | 0.000008% | +0.000000 pp | 16.101530% | +0.000000 pp | 2.110205% | 0.962789 | +0.000000 |
| Persian | `D1I1R1M0` | 0.000000% | +0.000000 pp | 97.415054% | +0.000000 pp | 4.877973% | 0.117096 | +0.000000 |
| Finnish | `D1I2R1M0` | 0.000001% | +0.000000 pp | 22.310391% | -0.000007 pp | 2.355085% | 0.945434 | +0.000000 |
| French | `D1I1R1M0` | 0.000011% | +0.000000 pp | 31.351496% | +0.000000 pp | 8.248915% | 0.915559 | +0.000000 |
| Hebrew | `D1I2R2M0` | 0.000079% | -0.000012 pp | 92.512100% | -0.042585 pp | 2.749465% | 0.287004 | +0.001308 |
| Hungarian | `D1I1R1M1` | 0.000010% | +0.000000 pp | 12.955456% | -0.000055 pp | 0.736116% | 0.970327 | +0.000000 |
| Italian | `D1I1R1M0` | 0.000001% | +0.000000 pp | 12.613898% | +0.000000 pp | 0.669175% | 0.971928 | +0.000000 |
| Norwegian Bokmål | `D1I1R1M0` | 0.000024% | +0.000000 pp | 41.628315% | +0.000000 pp | 4.749500% | 0.872959 | +0.000000 |
| Dutch | `D1I1R2M0` | 0.000027% | +0.000000 pp | 48.375349% | -0.015697 pp | 2.129575% | 0.840309 | +0.000084 |
| Norwegian Nynorsk | `D1I1R2M0` | 0.000096% | -0.000001 pp | 41.218142% | +0.000000 pp | 9.675485% | 0.874458 | +0.000000 |
| Polish | `D1I1R1M0` | 0.000010% | +0.000000 pp | 37.062231% | +0.000000 pp | 1.730587% | 0.893496 | +0.000000 |
| Portuguese | `D1I1R1M0` | 0.000008% | +0.000000 pp | 11.069494% | +0.000000 pp | 0.291615% | 0.975633 | +0.000000 |
| Russian | `D1I1R1M0` | 0.000018% | +0.000000 pp | 21.536426% | +0.000000 pp | 1.957425% | 0.946441 | +0.000000 |
| Swedish | `D1I1R1M0` | 0.000034% | +0.000000 pp | 33.331485% | +0.000000 pp | 4.476263% | 0.906986 | +0.000000 |
| Ukrainian | `D1I1R1M0` | 0.000037% | +0.000000 pp | 49.894697% | +0.000000 pp | 0.836852% | 0.833220 | +0.000000 |
| English | `D1I1R1M0` | 0.000001% | +0.000000 pp | 35.620602% | +0.000000 pp | 4.615189% | 0.896140 | +0.000000 |
| Yiddish | `D1I1R2M0` | 0.000045% | -0.000014 pp | 59.609698% | +0.000000 pp | 2.184236% | 0.771991 | +0.000000 |

Machine-readable values: [quality configurations](data/edit-cost-quality-optima.csv).

## Predeclared recommendation rule

For each language, configurations classified `VIABLE` at every partial-knowledge observation
are retained. The rule finds the best median unseen changed-form exactness, admits settings within
0.25 percentage points, then minimizes the median retained-command
ratio; remaining ties prefer unseen F0.5 and exactness. This is an exploratory operating-point
selection, not an external-test estimate.

| Language | Recommended costs | Unseen changed exact | Δ vs baseline | Unseen F0.5 | Command ratio |
| --- | --- | ---: | ---: | ---: | ---: |
| Czech | `D10I10R1M1` | 70.455% | +0.000 pp | 0.9272 | 0.942× |
| Danish | `D1I1R10M0` | 64.067% | +0.000 pp | 0.8781 | 0.895× |
| German | `D2I5R3M0` | 55.374% | +0.000 pp | 0.8257 | 0.971× |
| Spanish | `D10I10R1M1` | 87.056% | +0.000 pp | 0.9628 | 0.820× |
| Persian | `D10I10R1M1` | 14.906% | -0.031 pp | 0.1165 | 0.917× |
| Finnish | `D10I10R1M1` | 84.143% | +0.005 pp | 0.9455 | 0.736× |
| French | `D10I10R1M1` | 76.846% | +0.000 pp | 0.9156 | 0.734× |
| Hebrew | `D10I5R10M1` | 14.245% | +0.022 pp | 0.2870 | 0.975× |
| Hungarian | `D1I1R10M0` | 90.699% | +0.000 pp | 0.9703 | 0.888× |
| Italian | `D10I10R1M1` | 91.567% | +0.000 pp | 0.9719 | 0.806× |
| Norwegian Bokmål | `D1I1R10M0` | 67.144% | -0.004 pp | 0.8730 | 0.965× |
| Dutch | `D10I1R10M0` | 63.882% | +0.005 pp | 0.8403 | 0.961× |
| Norwegian Nynorsk | `D1I1R1M0` | 63.758% | +0.000 pp | 0.8745 | 1.000× |
| Polish | `D10I10R1M1` | 67.725% | +0.000 pp | 0.8935 | 0.941× |
| Portuguese | `D10I10R1M1` | 93.092% | +0.000 pp | 0.9756 | 0.812× |
| Russian | `D10I10R1M1` | 79.655% | -0.004 pp | 0.9464 | 0.970× |
| Swedish | `D10I10R1M1` | 75.990% | +0.000 pp | 0.9070 | 0.953× |
| Ukrainian | `D10I10R1M1` | 52.642% | +0.000 pp | 0.8332 | 0.943× |
| English | `D1I1R1M0` | 77.779% | +0.000 pp | 0.8961 | 1.000× |
| Yiddish | `D1I1R1M0` | 45.515% | +0.000 pp | 0.7720 | 1.000× |

## Per-language conclusions

The macro result is not substituted for language evidence. Each language page now
publishes its five-split knowledge curve, dictionary-specific exact cost classes,
within-language association coverage, and a bounded conclusion. The compact index
below exposes the main measured differences; follow the link for the supporting rows
and interpretation limits.

| Language | Baseline exact at 10% | Baseline exact at 90% | Knowledge effect | Selected costs | Median command change | Conclusion |
| --- | ---: | ---: | ---: | --- | ---: | --- |
| Czech | 64.331% | 70.970% | +6.639 pp | `D10I10R1M1` | -5.83% | [Evidence and conclusion](languages/czech.md#edit-cost-conclusion) |
| Danish | 51.619% | 68.000% | +16.381 pp | `D1I1R10M0` | -10.47% | [Evidence and conclusion](languages/danish.md#edit-cost-conclusion) |
| German | 48.338% | 58.463% | +10.125 pp | `D2I5R3M0` | -2.94% | [Evidence and conclusion](languages/german.md#edit-cost-conclusion) |
| Spanish | 80.556% | 89.280% | +8.725 pp | `D10I10R1M1` | -18.02% | [Evidence and conclusion](languages/spanish.md#edit-cost-conclusion) |
| Persian | 14.864% | 16.975% | +2.112 pp | `D10I10R1M1` | -8.31% | [Evidence and conclusion](languages/persian.md#edit-cost-conclusion) |
| Finnish | 78.695% | 85.707% | +7.012 pp | `D10I10R1M1` | -26.37% | [Evidence and conclusion](languages/finnish.md#edit-cost-conclusion) |
| French | 69.937% | 80.058% | +10.121 pp | `D10I10R1M1` | -26.64% | [Evidence and conclusion](languages/french.md#edit-cost-conclusion) |
| Hebrew | 10.977% | 15.910% | +4.933 pp | `D10I5R10M1` | -2.48% | [Evidence and conclusion](languages/hebrew.md#edit-cost-conclusion) |
| Hungarian | 86.780% | 91.867% | +5.087 pp | `D1I1R10M0` | -11.17% | [Evidence and conclusion](languages/hungarian.md#edit-cost-conclusion) |
| Italian | 85.934% | 93.330% | +7.397 pp | `D10I10R1M1` | -19.44% | [Evidence and conclusion](languages/italian.md#edit-cost-conclusion) |
| Norwegian Bokmål | 58.507% | 70.574% | +12.068 pp | `D1I1R10M0` | -3.54% | [Evidence and conclusion](languages/norwegian-bokmal.md#edit-cost-conclusion) |
| Dutch | 55.658% | 68.281% | +12.623 pp | `D10I1R10M0` | -3.86% | [Evidence and conclusion](languages/dutch.md#edit-cost-conclusion) |
| Norwegian Nynorsk | 50.756% | 69.473% | +18.717 pp | `D1I1R1M0` | +0.00% | [Evidence and conclusion](languages/norwegian-nynorsk.md#edit-cost-conclusion) |
| Polish | 61.850% | 70.138% | +8.288 pp | `D10I10R1M1` | -5.88% | [Evidence and conclusion](languages/polish.md#edit-cost-conclusion) |
| Portuguese | 89.204% | 94.378% | +5.174 pp | `D10I10R1M1` | -18.75% | [Evidence and conclusion](languages/portuguese.md#edit-cost-conclusion) |
| Russian | 74.976% | 81.082% | +6.106 pp | `D10I10R1M1` | -3.04% | [Evidence and conclusion](languages/russian.md#edit-cost-conclusion) |
| Swedish | 67.345% | 79.922% | +12.577 pp | `D10I10R1M1` | -4.72% | [Evidence and conclusion](languages/swedish.md#edit-cost-conclusion) |
| Ukrainian | 43.396% | 56.474% | +13.078 pp | `D10I10R1M1` | -5.67% | [Evidence and conclusion](languages/ukrainian.md#edit-cost-conclusion) |
| English | 75.824% | 79.131% | +3.308 pp | `D1I1R1M0` | +0.00% | [Evidence and conclusion](languages/english.md#edit-cost-conclusion) |
| Yiddish | 38.579% | 50.000% | +11.421 pp | `D1I1R1M0` | +0.00% | [Evidence and conclusion](languages/yiddish.md#edit-cost-conclusion) |

## Generalization across knowledge levels

Each cell is the median across 20 languages × five frozen splits at the stated
knowledge level. The recommended curve applies each language's independently selected
setting; it is not a single global configuration. Percentages remain macro summaries
rather than token-weighted production estimates.

| Knowledge | Baseline unseen changed exact | Recommended | Δ | Baseline F0.5 | Recommended F0.5 | Baseline commands | Recommended commands |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10% | 62.256% | 62.256% | +0.000 pp | 0.8638 | 0.8638 | 1.000× | 0.939× |
| 20% | 65.946% | 65.946% | +0.000 pp | 0.8833 | 0.8833 | 1.000× | 0.939× |
| 30% | 67.259% | 67.259% | +0.000 pp | 0.8881 | 0.8881 | 1.000× | 0.946× |
| 40% | 68.376% | 68.376% | +0.000 pp | 0.8927 | 0.8927 | 1.000× | 0.948× |
| 50% | 69.309% | 69.309% | +0.000 pp | 0.8961 | 0.8961 | 1.000× | 0.947× |
| 60% | 70.014% | 70.014% | +0.000 pp | 0.8984 | 0.8984 | 1.000× | 0.941× |
| 70% | 70.186% | 70.183% | -0.003 pp | 0.9021 | 0.9021 | 1.000× | 0.942× |
| 80% | 70.691% | 70.691% | +0.000 pp | 0.9058 | 0.9058 | 1.000× | 0.948× |
| 90% | 70.881% | 70.881% | +0.000 pp | 0.9066 | 0.9066 | 1.000× | 0.950× |

## Dictionary properties and selected costs

These across-language coefficients connect dictionary descriptors to the exploratory
language-specific selections above. With 20 dictionaries and selection on the same
data, they generate hypotheses about dictionary types; they do not establish a portable
cost-selection rule for an unseen resource. Predictor and selected-outcome labels are
defined in the [methodology glossary](reference/edit-cost-methodology.md#predictor-and-outcome-glossary).

| Dictionary property | Selected outcome | Spearman ρ | Pearson r | Languages |
| --- | --- | ---: | ---: | ---: |
| `mean_family_size` | `recommended_match_cost` | +0.673 | +0.393 | 20 |
| `changed_form_share` | `recommended_match_cost` | +0.673 | +0.620 | 20 |
| `mean_family_size` | `recommended_command_ratio` | -0.636 | -0.434 | 20 |
| `changed_form_share` | `recommended_insert_cost` | +0.623 | +0.590 | 20 |
| `changed_form_share` | `recommended_command_ratio` | -0.616 | -0.487 | 20 |
| `mean_family_size` | `recommended_insert_cost` | +0.615 | +0.352 | 20 |
| `mean_family_size` | `recommended_delete_cost` | +0.598 | +0.321 | 20 |
| `changed_form_share` | `recommended_delete_cost` | +0.566 | +0.575 | 20 |

## Associations across cost configurations

Correlations are calculated separately inside every language × seed × knowledge stratum;
the table reports the median and central 95% empirical interval of those within-stratum
coefficients. This avoids allowing language size or knowledge level to manufacture a pooled
association. The interval is descriptive, not an independence-adjusted confidence interval.
Every predictor and outcome label is defined in the [methodology glossary](reference/edit-cost-methodology.md#predictor-and-outcome-glossary).
Only the following pairs keep the same sign throughout that central interval.

| Predictor | Outcome | Median Spearman ρ | Central 95% | Median Pearson r | Strata |
| --- | --- | ---: | ---: | ---: | ---: |
| `patch_command_ratio` | `value_references` | +0.988 | +0.263…+1.000 | +0.995 | 900 |
| `patch_command_ratio` | `trie_nodes` | +0.953 | +0.187…+1.000 | +0.970 | 900 |
| `edit_cost_imbalance` | `logical_leaf_paths` | +0.177 | +0.079…+0.293 | +0.137 | 540 |

### Quality associations are heterogeneous

No cost or representation predictor keeps one Spearman sign across the central 95%
of strata for any unseen-form quality outcome. The largest absolute median for each
outcome is shown below to make that heterogeneity visible; it must not be interpreted
as a global effect that transfers between languages or knowledge levels.

| Predictor | Outcome | Median Spearman ρ | Central 95% | Median Pearson r | Strata |
| --- | --- | ---: | ---: | ---: | ---: |
| `logical_leaf_paths` | `unseen_changed_exact` | -1.000 | -1.000…+0.126 | -1.000 | 502 |
| `logical_leaf_paths` | `unseen_f05` | -0.956 | -1.000…+0.331 | -1.000 | 517 |
| `replace_to_delete_insert` | `unseen_over_percent` | -0.766 | -0.947…+0.712 | -0.570 | 325 |
| `logical_leaf_paths` | `unseen_under_percent` | +1.000 | -0.158…+1.000 | +1.000 | 517 |

### Smallest observed association envelopes

These pairs have the smallest central-interval envelope in the measured matrix.
They are candidates for practical insensitivity, not proof of independence; an interval
that spans substantial positive and negative values instead indicates heterogeneity.

| Predictor | Outcome | Median Spearman ρ | Central 95% | Median Pearson r | Strata |
| --- | --- | ---: | ---: | ---: | ---: |
| `delete_to_insert_ratio` | `unseen_changed_exact` | +0.000 | +0.000…+0.000 | +0.121 | 535 |
| `delete_to_insert_ratio` | `unseen_f05` | +0.000 | +0.000…+0.000 | +0.118 | 625 |
| `delete_to_insert_ratio` | `unseen_over_percent` | +0.000 | +0.000…+0.000 | +0.051 | 325 |
| `delete_to_insert_ratio` | `unseen_under_percent` | +0.000 | +0.000…+0.000 | -0.121 | 602 |
| `delete_to_insert_ratio` | `patch_command_ratio` | +0.000 | +0.000…+0.000 | +0.006 | 900 |
| `delete_to_insert_ratio` | `trie_nodes` | +0.000 | +0.000…+0.000 | +0.007 | 900 |
| `delete_to_insert_ratio` | `trie_edges` | +0.000 | +0.000…+0.000 | -0.001 | 797 |
| `delete_to_insert_ratio` | `average_path_length` | +0.000 | +0.000…+0.000 | -0.051 | 540 |

## Interpretation boundaries

The experiment establishes sensitivity and within-resource generalization, not causality
or performance on unrelated domains. A weak median correlation is not described as proof
of no relationship: heterogeneous signs, nonlinear effects, and repeated splits remain
possible. Recommendations therefore remain language-specific until validated on an external
dictionary or corpus. See the [full protocol](reference/edit-cost-methodology.md) and
[raw-data instructions](reference/reproducibility.md).
