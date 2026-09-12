# Finnish filtered candidate

`fi-fi-filtered` is a published opt-in alternative to `fi-fi-default`. It is
available individually and through `radixor-models-filtered`, but is not a
registered default or part of the standard, extended, or Python packages.

The checked-in `fi-fi-default` dictionary is the only lexical input. The audit
removes three strongly internally dominated stem assignments without consulting
external vocabulary. The distinct baseline command count remains 2,683 because
the affected commands are still supported by other mappings. The model-data
notice records the primary source, license, and construction.

From the repository root, regenerate all candidates with
`./gradlew --no-daemon dictionaryMeaningAudit`, then validate and build this
model with `./gradlew --no-daemon :models:fi-fi-filtered:check`.
