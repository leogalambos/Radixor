# Spanish filtered candidate

`es-es-filtered` is a published opt-in alternative to `es-es-default`. It is
available individually and through `radixor-models-filtered`, but is not a
registered default or part of the standard, extended, or Python packages.

The checked-in `es-es-default` dictionary is the only lexical input. The audit
sanitizes objective source artifacts and removes strongly internally dominated
stem assignments without consulting external vocabulary. It reduces the
baseline command vocabulary from 1,496 to 1,458, removes one invalid row, 34
artifact mappings, and eight dominated mappings. The model-data notice records
the primary source, license, and construction.

From the repository root, regenerate all candidates with
`./gradlew --no-daemon dictionaryMeaningAudit`, then validate and build this
model with `./gradlew --no-daemon :models:es-es-filtered:check`.
