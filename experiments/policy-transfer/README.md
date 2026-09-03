# Radixor Policy-Transfer Experiment

This directory is the repository-level reproduction harness for the study **“The Stemming Policy Is Data: Adapting a Lexicon-Trained Stemmer to Alternative Conflation Objectives.”** It tests whether the fixed Radixor patch-command/trie mechanism changes held-out pairwise conflation behavior when only the reference-defined training policy changes.

## Frozen identities

- Radixor core commit: `0c3b13f485a9ad0b460c0931e4497ea95bed66a1`
- Frozen JAR: `frozen/Radixor-4.2.0-8-g0c3b13f.jar`
- JAR SHA-256: `ca74d617fc939339a76754324bb7be8301f811bf7b40cc76430bcec70729b95d`
- CISTEM commit: `7c19867c2e062c8a7d44b394c19573845ac4bd89`
- GS1 Git blob: `8627bb28b67429f6488f8d017f510327b2c84d1c`
- GS2 Git blob: `2cb401638a67760f5fec47c8379646bf6d6d1b8e`
- Official CISTEM Python blob: `dbc90836bb6361712b52b2e504b85c702294a29f`

The CISTEM gold-standard files are CELEX-derived and are **not** redistributed here. `make fetch-inputs` downloads the exact public upstream gold blobs plus the official pinned `Cistem.py` implementation and verifies their byte sizes and Git identities before evaluation.

## Scientific object

The released CISTEM files contain equivalence classes but no reference stem for each cluster. The experiment therefore distinguishes:

1. the **policy partition**, which specifies which forms are equivalent; and
2. the **canonical target encoding**, which supplies the concrete output string required by Radixor training.

Three deterministic target encodings are evaluated: exact Levenshtein medoid (primary), shortest member, and lexical first. They encode the same in-sample class relation but generate materially different edit-command training evidence.

On the exact common unambiguous vocabulary, GS2 is a strict refinement of GS1. This is verified rather than assumed. The refinement already exists before ambiguity exclusion and is therefore not introduced by the leakage-control filter.

## Requirements

- JDK 21 or newer
- Python 3.11 or newer
- Network access only for `make fetch-inputs`

Java subprocesses are resource-bounded by default with:

`-Xms128m -Xmx1536m -XX:+ExitOnOutOfMemoryError -XX:ActiveProcessorCount=2`

Override this envelope through `RADIXOR_JAVA_OPTIONS` if necessary. Resource options do not change the frozen compiler configuration or the model semantics.

## Reproduction

```text
make smoke
make fetch-inputs
make experiment
make full-information
make baseline
make sensitivities
make verify
make analyze
```

Or run the exact-input workflow with:

```text
make full
```

The primary Radixor intervention compiles 30 persisted models: three target encodings x five folds x two policies. No single model sees the complete partition; for each reported component prediction, every form in that component is absent from fitting. The `full-information` target then compiles six all-data reconstruction models to measure the in-sample-to-component-held-out gap. `baseline` evaluates the pinned fixed-rule CISTEM implementation both on the fold-restricted relation scope used beside cross-fit Radixor and jointly on the full restricted universe; no CISTEM fitting occurs. `sensitivities` emits the relation-specificity and component-size diagnostics added after the first hard review.

## Outputs

Publication-safe aggregate outputs are written to `data/derived/`. The most important files are:

- `preflight.json`
- `policy_transfer_summary.csv`
- `fold_policy_transfer.csv`
- `component_switch_counts.csv`
- `pairwise_summary.csv`
- `cistem_macro_summary.csv`
- `model_artifacts.csv`
- `policy_structure_summary.csv`
- `cistem_baseline_fold.csv`
- `cistem_baseline_summary.csv`
- `cistem_baseline_full_universe.csv`
- `policy_specificity_summary.csv`
- `component_size_sensitivity.csv`
- `full_information_summary.csv`
- `external_full_model_benchmark.csv`

Lexical fold dictionaries, held-out word lists, model binaries produced by a run, and token-level predictions remain under `build/private/` and must not be committed or redistributed.

Persisted model-file SHA-256 is the authoritative model identity. The `trie_fingerprint` field emitted by the frozen Radixor snapshot is retained only as a diagnostic because that snapshot does not provide a process-stable fingerprint for compiled patch-command values.

## Result interpretation

The primary result is a component-macro net policy-aligned switching effect on held-out policy-disagreement pairs. The experiment supports **sparse but directionally robust policy-conditioned relational generalization**: model changes are rare on a pair-micro basis, but when the two learned models differ, the change overwhelmingly follows the requested GS1-versus-GS2 direction, and the positive component-macro effect is stable across folds and all three canonical-target encodings.

Post-execution secondary analyses make the information boundary explicit. The six full-information GS-trained models reconstruct their own restricted partitions exactly; the difference to cross-fit scores is therefore a component-held-out generalization gap rather than a production accuracy loss. Fixed-rule CISTEM reaches fold-restricted cluster-macro F1 0.9213 on GS1 and 0.9329 on GS2; when the one fixed model is scored jointly over the full controlled universe, the corresponding values are 0.9016 and 0.9120. Neither scope is a symmetric fitting comparison because CISTEM is not retrained per fold. The relation-specificity diagnostic additionally shows that the GS2-conditioned Radixor model recovers more shared-positive relations while sharply suppressing GS1-join/GS2-split relations, so the directional effect is not explained by uniform conservatism. Separately, the frozen registered German Radixor (UniMorph-trained) versus CISTEM benchmark is essentially tied on GS1 and favors Radixor on GS2 under the established aggregate scorer. These secondary contexts do not alter the predeclared policy-transfer estimand or falsification rules.

See `EXPERIMENT_SPEC.md` for the frozen design and falsification rules.
