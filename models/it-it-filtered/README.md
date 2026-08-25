# Italian filtered candidate

`it-it-filtered` is a locally buildable alternative to `it-it-default`. It is
not published, registered as a default, or included in the model catalog, BOM,
standard pack, or Python model packages.

The checked-in `it-it-default` dictionary is the only lexical input. The audit
removes strongly internally dominated stem assignments without consulting
external vocabulary. It reduces the baseline command vocabulary from 750 to
705 and removes 56 dominated mappings. The model-data notice records the
primary source, license, and construction.

From the repository root, regenerate all candidates with
`./gradlew --no-daemon dictionaryMeaningAudit`, then validate and build this
model with `./gradlew --no-daemon :models:it-it-filtered:check`.
