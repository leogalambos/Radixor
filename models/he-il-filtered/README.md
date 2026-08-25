# Hebrew filtered candidate

`he-il-filtered` is a locally buildable alternative to `he-il-default`. It is
not published, registered as a default, or included in the model catalog, BOM,
standard pack, or Python model packages.

The checked-in `he-il-default` dictionary is the only lexical input. The audit
removes 41 non-lexical dash placeholders without consulting external
vocabulary. It reduces the baseline command vocabulary from 4,150 to 4,109;
no semantic mapping satisfied the conservative automatic-removal rule. The
model-data notice records the primary source, license, and construction.

From the repository root, regenerate all candidates with
`./gradlew --no-daemon dictionaryMeaningAudit`, then validate and build this
model with `./gradlew --no-daemon :models:he-il-filtered:check`.
