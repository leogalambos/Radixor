# Radixor

**Radixor** is a high-performance, multi-language stemmer for Java, designed for production-grade search and text-processing systems.

It modernizes the proven Egothor patch-command trie approach while introducing an important practical enhancement: compiled dictionaries are no longer treated as a final, closed artifact. Instead, they can be extended through additional transformation layers, allowing existing lexical assets to be refined and evolved without full recompilation from source dictionaries.

Radixor delivers:

- **Fast runtime stemming** with compact lookup structures
- **Multi-language adaptability** through dictionary-driven compilation
- **Incremental extensibility of compiled dictionaries** through additional transformation layers
- **Deterministic behavior** suitable for reproducible processing pipelines
- **Flexible integration paths**, including CLI-based and programmatic workflows
- **Operational transparency** through continuously published quality and benchmark reports

Radixor is intended for teams that require consistent stemming quality at scale without compromising maintainability, deployment efficiency, or the long-term evolvability of compiled lexical resources.

## Start here

- Read [Quick Start](quick-start.md) for immediate implementation guidance.
- Use [Programmatic Usage](programmatic-usage.md) for application integration patterns.
- Review [Benchmarking](benchmarking.md) for reproducible performance methodology.
- Open [CI Reports](reports.md) to inspect published build artifacts and quality metrics.