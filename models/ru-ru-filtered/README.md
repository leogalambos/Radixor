# Russian filtered candidate

`ru-ru-filtered` is a published opt-in alternative to `ru-ru-default`. It is
available individually and through `radixor-models-filtered`, but is not a
registered default or part of the standard, extended, or Python packages.

The checked-in `ru-ru-default` dictionary is the only lexical input. The audit
removes strongly internally dominated stem assignments without consulting
external vocabulary. It reduces the baseline command vocabulary from 1,840 to
1,821 and removes 28 dominated mappings. The model-data notice records the
primary source, license, and construction.

From the repository root, regenerate all candidates with
`./gradlew --no-daemon dictionaryMeaningAudit`, then validate and build this
model with `./gradlew --no-daemon :models:ru-ru-filtered:check`.
