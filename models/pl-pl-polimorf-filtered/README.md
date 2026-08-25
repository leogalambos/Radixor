# Polish PoliMorf filtered candidate

`pl-pl-polimorf-filtered` is a locally buildable alternative to
`pl-pl-polimorf`. It is not published, registered as a default, or included in
the model catalog, BOM, standard pack, or Python model packages.

The checked-in `pl-pl-polimorf` dictionary is the only lexical input. The audit
removes strongly internally dominated stem assignments without consulting
external vocabulary. It reduces the baseline command vocabulary from 5,249 to
5,179 and removes 104 dominated mappings. The production parser separately
rejects 50 source rows whose canonical field is multiword; those rows do not
contribute to either command count. The license file records the primary source,
license, and construction.

From the repository root, regenerate all candidates with
`./gradlew --no-daemon dictionaryMeaningAudit`, then validate and build this
model with `./gradlew --no-daemon :models:pl-pl-polimorf-filtered:check`.
