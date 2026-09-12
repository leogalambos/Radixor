# Polish UniMorph filtered candidate

`pl-pl-unimorph-filtered` is a published opt-in alternative to
`pl-pl-unimorph`. It is available individually and through
`radixor-models-filtered`, but is not a registered default or part of the
standard, extended, or Python packages.

The checked-in `pl-pl-unimorph` dictionary is the only lexical input. The audit
removes strongly internally dominated stem assignments without consulting
external vocabulary. It reduces the baseline command vocabulary from 846 to
843 and removes five dominated mappings. The model-data notice records the
primary source, license, and construction.

From the repository root, regenerate all candidates with
`./gradlew --no-daemon dictionaryMeaningAudit`, then validate and build this
model with `./gradlew --no-daemon :models:pl-pl-unimorph-filtered:check`.
