# Czech filtered candidate

`cs-cz-filtered` is a locally buildable alternative to `cs-cz-default`. It is
not published, registered as a default, or included in the model catalog, BOM,
standard pack, or Python model packages.

The checked-in `cs-cz-default` dictionary is the only lexical input. The audit
sanitizes objective source artifacts and removes strongly internally dominated
stem assignments without consulting external vocabulary. It reduces the
baseline command vocabulary from 537 to 468 and removes 26 dominated mappings.
The model-data notice records the primary source, license, and construction.

From the repository root, regenerate all candidates with
`./gradlew --no-daemon dictionaryMeaningAudit`, then validate and build this
model with `./gradlew --no-daemon :models:cs-cz-filtered:check`.
