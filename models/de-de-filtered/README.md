# German filtered candidate

`de-de-filtered` is a locally buildable alternative to `de-de-default`. It is
not published, registered as a default, or included in the model catalog, BOM,
standard pack, or Python model packages.

The checked-in `de-de-default` dictionary is the only lexical input. The audit
removes strongly internally dominated stem assignments without consulting
external vocabulary. It reduces the baseline command vocabulary from 6,986 to
5,313 and removes 2,055 dominated mappings. The 523 legacy quote-plus-vowel
spellings remain unchanged. External-quality counters for the candidate,
registered model, and CISTEM
are recorded without interpretation on the filtered-candidate documentation
page. The model-data notice records the primary source, license, and construction.

From the repository root, regenerate all candidates with
`./gradlew --no-daemon dictionaryMeaningAudit`, then validate and build this
model with `./gradlew --no-daemon :models:de-de-filtered:check`.
