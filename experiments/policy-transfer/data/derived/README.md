# Publication-Safe Derived Results

This directory contains aggregate, non-lexical outputs used by the paper. CELEX-derived lexical strings, fold dictionaries, token predictions, and compiled experiment models remain private build products.

Primary and predeclared cross-fit outputs:

- `preflight.json`
- `fold_preflight.csv`
- `model_artifacts.csv`
- `component_switch_counts.csv`
- `fold_pairwise_counts.csv`
- `fold_cistem_macro.csv`
- `policy_transfer_summary.csv`
- `fold_policy_transfer.csv`
- `pairwise_summary.csv`
- `cistem_macro_summary.csv`

Post-execution secondary analyses:

- `policy_structure_summary.csv` - deterministic structural characterization of the two public partitions and target-encoding sensitivity.
- `cistem_baseline_fold.csv` / `cistem_baseline_summary.csv` - fixed official CISTEM outputs under the fold-restricted relation scope used beside cross-fit Radixor.
- `cistem_baseline_full_universe.csv` - the same fixed CISTEM model scored jointly on the full restricted universe, including cross-fold false-positive collisions.
- `policy_specificity_summary.csv` - relation-stratified evidence separating shared-positive recovery from GS1-join/GS2-split decisions.
- `component_size_sensitivity.csv` - directional effects stratified by disagreement-pair mass per component.
- `full_information_summary.csv` - six all-data GS-trained Radixor models. Policy-matched F1 is an in-sample reconstruction ceiling used to quantify the component-held-out generalization gap.
- `external_full_model_benchmark.csv` - frozen contextual benchmark imported from the Radixor snapshot. Registered `de-de-default` is UniMorph-trained and lemma-grouped; this table uses the complete public CISTEM resources and the snapshot aggregate scorer, so its F1 values are not numerically interchangeable with the component-blocked macro F1 values.
