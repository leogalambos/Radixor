# Compatibility and Guarantees

This document explains what Radixor treats as stable public behavior, what should be regarded as internal implementation detail, and how to think about compatibility across versions.

Its purpose is to make adoption safer. Users should be able to understand which parts of the project are intended as supported API, which parts may evolve more freely, and which kinds of change are expected to remain compatible in future releases.

## Compatibility philosophy

Radixor is designed to be used as a real library, not only as a code drop. That means compatibility matters.

At the same time, the project distinguishes clearly between:

- **public API and behavior** that users are expected to build against,
- **internal implementation layers** that may change more freely when needed for correctness, performance, or maintainability.

The practical goal is straightforward:

- keep the main user-facing API in `org.egothor.stemmer` stable and supportable,
- allow more freedom of evolution in internal trie-focused implementation layers,
- extend the project conservatively without creating unnecessary behavioral ambiguity.

## Public API posture

As a general rule, the `org.egothor.stemmer` package should be treated as the primary supported API surface.

That includes the main user-facing types involved in:

- dictionary loading,
- binary loading and persistence,
- patch-command application,
- compiled trie querying,
- reconstruction workflows,
- reduction configuration,
- CLI use.

This API is expected to remain supportable across future versions. The preferred compatibility model is additive evolution: improving documentation, clarifying behavior, and adding capabilities without unnecessary disruption of existing usage patterns.

Examples of likely additive evolution include:

- additional bundled language resources,
- fuller support for diacritics or native-script language resources,
- expanded documentation and operational tooling,
- new convenience methods that do not break existing code.

## Internal API posture

The `org.egothor.stemmer.trie` package should be treated as internal or at least significantly less stable implementation API.

It represents the structural machinery behind mutable nodes, reduced nodes, compiled nodes, reduction context, signatures, and related internal compilation details. These types may evolve more aggressively when needed to improve implementation quality, correctness, reduction behavior, internal representations, or performance characteristics.

Users should therefore avoid building long-term integrations against `org.egothor.stemmer.trie` unless they are intentionally accepting that tighter coupling.

In practical terms:

- `org.egothor.stemmer` is the supported integration layer,
- `org.egothor.stemmer.trie` is the implementation layer.

## Behavioral guarantees

Several project properties are intended as core behavioral guarantees.

### Deterministic dictionary loading and compilation

Given the same textual dictionary input and the same reduction settings, Radixor is intended to produce the same compiled stemming semantics in a reproducible way.

This includes deterministic local result ordering and deterministic observable lookup behavior.

### Stable meaning of `get()` and `getAll()`

The distinction between preferred-result lookup and multi-result lookup is part of the supported behavior model.

- `get()` returns the locally preferred stored value,
- `getAll()` returns all locally stored values in deterministic ranked order,
- `getEntries()` returns aligned values with counts.

That model is part of how the public API should be understood.

### Stable reduction-mode intent

Each public `ReductionMode` constant carries a semantic contract that should remain meaningful across versions.

In other words, the implementation may evolve, but the intended meaning of modes such as ranked `getAll()` equivalence, unordered `getAll()` equivalence, and dominant `get()` equivalence should not drift casually.

### Stable binary artifact purpose

Compiled `.radixor.gz` artifacts are a first-class project output. Loading and persisting compiled stemmer artifacts is part of the intended usage model, not an incidental implementation side effect.

## What is allowed to evolve

Compatibility does not mean the project is frozen.

The following kinds of change are generally compatible with the project’s direction:

- improved internal data structures,
- changes inside `org.egothor.stemmer.trie`,
- expanded bundled dictionaries,
- additional supported languages,
- improved native-script handling,
- better benchmarks, tests, and reports,
- additive public API growth that does not invalidate existing usage.

The project should be able to improve substantially while keeping the main user-facing integration model intact.

## What may change more cautiously

Some areas should be treated as stable in intent but still approached carefully when changed.

### Bundled dictionary contents

Bundled resources are versioned project data, not immutable language standards. Their contents may improve over time.

That means stemming outcomes can legitimately change when bundled dictionaries are refined or expanded. Such changes are compatible with the project’s direction, but they should still be understood as behavior changes at the lexical-resource level.

### Binary format evolution

Compiled binary artifacts are an intended project output, but binary-format evolution may still be needed in future versions.

If the format changes, that should be handled deliberately and documented clearly. Users should not assume that every historical persisted artifact will remain readable forever without versioning considerations. What should remain stable is the project’s support for compiled artifact workflows, not necessarily perpetual cross-version binary interchange without explicit format evolution rules.

### Performance characteristics

Radixor places strong emphasis on performance, but no benchmark number should be treated as a formal compatibility guarantee.

What is more meaningful than any single raw number is the architectural performance posture: the library is intended to remain a compact compiled stemmer with very strong runtime throughput characteristics.

## What users should rely on

Long-term users should rely primarily on the following:

- the main integration path in `org.egothor.stemmer`,
- the documented meaning of `get()`, `getAll()`, and reduction modes,
- the offline-compilation plus runtime-loading workflow,
- the availability of compiled artifact support,
- the project’s preference for deterministic and auditable behavior.

These are the parts of the project that are intended to remain the most stable and supportable.

## What users should not rely on casually

Users should avoid depending on:

- internal trie package details,
- undocumented internal classes or intermediate representations,
- incidental internal ordering outside documented lookup semantics,
- assumptions that bundled dictionary contents will never evolve,
- assumptions that internal binary-format details are frozen forever.

If a behavior is important to your integration, it should ideally be documented at the public API or project-documentation level rather than inferred from internal implementation details.

## Source compatibility and behavioral compatibility

It is useful to distinguish two different notions of compatibility.

### Source compatibility

Whether existing Java code using the supported public API still compiles and integrates cleanly after an upgrade.

### Behavioral compatibility

Whether the upgraded system still behaves the same way for the same dictionary data, compiled artifacts, and runtime calls.

Radixor aims to preserve both where reasonably possible, but behavioral compatibility can still be influenced by intentional improvements such as dictionary refinement or bug fixes. For that reason, upgrades should be evaluated not only as code upgrades but also as stemming-behavior upgrades.

## Recommended upgrade discipline

When upgrading Radixor in a production environment, it is good practice to:

1. review release notes and documentation changes,
2. rebuild compiled artifacts if the upgrade affects dictionary or artifact handling,
3. rerun representative stemming validation tests,
4. compare benchmark outputs where performance matters,
5. inspect whether bundled-dictionary changes affect expected canonical results.

This is especially important for deployments that treat stemming behavior as part of search relevance or normalization policy.

## Summary

Radixor’s compatibility model is intentionally layered.

- `org.egothor.stemmer` should be treated as the supported public integration API,
- `org.egothor.stemmer.trie` should be treated as an internal implementation layer,
- deterministic public behavior and compiled-artifact workflows are core project commitments,
- internal structure and lexical-resource quality can continue to evolve.

This model gives the project room to improve while still providing a reliable surface for long-term use.
