# Contributing Dictionaries

High-quality dictionaries are one of the most valuable ways to improve **Radixor**.

The project already includes practical bundled dictionaries for common use, but the long-term quality and language reach of the stemmer depend heavily on the quality of its lexical resources. Contributions are therefore welcome not only in the form of code changes, but also in the form of well-prepared dictionary data for existing or additional languages.

This document explains what makes a dictionary contribution useful, how to structure it, and how to prepare it so that it integrates cleanly with the project.

## What a good dictionary contribution looks like

A good dictionary contribution is not defined only by the number of entries.

The most useful contributions are dictionaries that are:

- linguistically consistent,
- operationally clean,
- easy to review,
- easy to reproduce,
- appropriate for actual stemming use rather than raw lexical accumulation.

In practice, dictionary quality matters more than dictionary size. A smaller but coherent and carefully normalized dictionary is often more valuable than a larger resource that mixes conventions, contains noisy forms, or introduces accidental ambiguity.

## Preferred dictionary shape

Radixor uses a simple line-oriented format:

```text
<stem> <variant1> <variant2> <variant3> ...
```

The first token on a line is the canonical stem. All following tokens on that line are known variants that should reduce to that stem.

Example:

```text
run running runs ran
connect connected connecting connection
```

The parser:

- reads UTF-8 text,
- normalizes input to lower case using `Locale.ROOT`,
- ignores empty lines,
- supports remarks introduced by `#` or `//`.

For full format details, see [Dictionary format](dictionary-format.md).

## Contribution priorities

The most useful dictionary contributions generally fall into one of four categories.

### 1. Stronger dictionaries for already bundled languages

Improving lexical quality for already supported languages is often more valuable than merely expanding the language list. Better coverage, cleaner canonicalization, and improved consistency directly improve practical stemming outcomes.

### 2. Additional languages

New language support is welcome when the submitted resource is strong enough to be useful as a maintainable bundled baseline rather than as an incomplete demonstration artifact.

### 3. Native-script language resources

The current bundled resources follow a pragmatic normalization convention and may use transliterated or otherwise normalized forms. This is especially visible for languages such as Russian.

That convention belongs to the supplied dictionaries, not to the underlying algorithm. The parser, trie, and patch-command model are not fundamentally restricted to plain ASCII. Contributions of high-quality native-script dictionaries in full UTF-8 text are therefore particularly valuable, because they would enable more direct language support without transliteration-based workflows.

### 4. Domain-quality refinements

Some contributions may be more appropriate as curated domain extensions than as replacements for a general-purpose bundled dictionary. These are still useful when they are clearly scoped and operationally coherent.

## Normalization guidance

A dictionary should follow one normalization convention consistently.

For current general-purpose bundled resources, the safest convention remains normalized plain-ASCII lexical input where that is already the established project style. For languages where a stronger native-script resource exists, a coherent UTF-8 dictionary may be preferable, provided that the contribution is deliberate, well-structured, and consistently normalized.

The important point is not to mix incompatible conventions casually.

Avoid contributions that combine, without clear design intent:

- native-script and transliterated forms,
- multiple incompatible stem conventions,
- inconsistent use of diacritics,
- ad hoc spelling normalization,
- noisy typo-like forms presented as ordinary lexical variants.

## Choosing canonical stems

A dictionary line should reflect a stable canonical target form.

That means:

- choose one canonical representation and use it consistently,
- avoid mixing alternative stem conventions without a clear lexical reason,
- keep variants grouped under the form that the project should actually return as the canonical result.

For example, the following is coherent:

```text
analyze analyzing analyzed analyzes
```

The following is less useful if the project has not intentionally chosen mixed conventions:

```text
analyse analyzing analyzed analyzes
```

The contribution should make the intended canonical policy easy to understand.

## Ambiguity handling

Ambiguity is allowed, but it should be intentional.

If the same surface form appears under multiple stems, the compiled trie may later expose multiple candidate patch commands. This can be correct and desirable when the lexical reality genuinely requires it. However, accidental ambiguity caused by inconsistent source preparation makes the resource harder to trust and harder to review.

Before contributing a dictionary, check whether repeated surface forms across lines are:

- linguistically intentional,
- consistent with the chosen canonical policy,
- useful for runtime stemming behavior.

## What to avoid

Dictionary contributions are much easier to review and accept when they avoid common quality problems.

Avoid:

- mechanically aggregated word lists without review,
- inconsistent canonical forms,
- mixed orthographic conventions without explanation,
- accidental duplicates caused by source merging,
- noisy or non-lexical tokens,
- comments or formatting that make the source hard to audit.

A dictionary should read like a curated lexical resource, not like an unfiltered export.

## Practical preparation workflow

A disciplined dictionary contribution should typically follow this path:

1. prepare or normalize the lexical source,
2. convert it into Radixor dictionary format,
3. review canonical stem choices,
4. check for accidental duplicates and unintended ambiguity,
5. compile the dictionary,
6. test representative lookups,
7. inspect `get()` and `getAll()` behavior for important edge cases,
8. include a concise explanation of source provenance and normalization choices.

## What to test before submitting

At minimum, a proposed dictionary should be checked for:

- successful parsing,
- successful compilation,
- expected stemming behavior on representative examples,
- acceptable ambiguity behavior,
- stable canonical policy,
- absence of obvious malformed lines or accidental source contamination.

For important resources, it is also useful to test:

- whether representative forms survive reduction as expected,
- whether dominant-result behavior remains sensible if alternate reduction modes are used,
- whether the resulting artifact has a practical size for the intended use case.

## Contribution notes that help maintainers

A dictionary contribution becomes much easier to review when it includes a short maintainer-facing note describing:

- the language or domain covered,
- the provenance of the lexical data,
- the normalization convention used,
- whether the dictionary is ASCII-normalized or native-script UTF-8,
- the intended canonical stem policy,
- any known limitations,
- why the contribution improves the project in practical terms.

This note does not need to be long. It simply needs to make the resource intelligible.

## Bundled-resource expectations

Not every useful dictionary must automatically become a bundled language resource.

To be suitable for bundling, a dictionary should generally be:

- broadly useful,
- maintainable,
- legally safe to include,
- coherent enough to serve as a project baseline,
- strong enough that users can rely on it as more than a demonstration resource.

Some dictionaries are better treated as examples, experiments, or domain-specific artifacts rather than as general built-in resources.

## Native scripts and future language support

One of the most meaningful future directions for the project is stronger support for languages in their native writing systems.

The architecture does not need to change fundamentally for that to happen. What matters is the availability of strong lexical resources and the willingness to define clear conventions for how those resources should be bundled and maintained.

Contributions in this area are therefore especially valuable when they are:

- internally consistent,
- encoded as proper UTF-8 text,
- accompanied by a clear explanation of normalization assumptions,
- strong enough to support practical use rather than only demonstration.

## Related documentation

- [Built-in languages](built-in-languages.md)
- [Dictionary format](dictionary-format.md)
- [CLI compilation](cli-compilation.md)
- [Programmatic usage](programmatic-usage.md)

## Summary

The best dictionary contributions improve Radixor not merely by adding more entries, but by improving the linguistic quality, consistency, and practical usefulness of the lexical resources the project can compile and ship.

A strong contribution is therefore one that is:

- coherent,
- reviewable,
- operationally clean,
- well explained,
- and valuable for real stemming workloads.
