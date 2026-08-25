# Russian filtered candidate

`ru-ru-filtered` is a locally buildable alternative to `ru-ru-default`. It is
not published, registered as a default, or included in the model catalog, BOM,
standard pack, or Python model packages.

The checked-in `ru-ru-default` dictionary is the only lexical input. The audit
removes strongly internally dominated stem assignments without consulting
external vocabulary. It reduces the baseline command vocabulary from 1,840 to
1,821 and removes 28 dominated mappings. The model-data notice records the
primary source, license, and construction.

From the repository root, regenerate all candidates with
`./gradlew --no-daemon dictionaryMeaningAudit`, then validate and build this
model with `./gradlew --no-daemon :models:ru-ru-filtered:check`.
