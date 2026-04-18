<h1 class="visually-hidden">Home</h1>
<p align="center">
  <img src="assets/images/banner.jpg" alt="Radixor banner" style="width: 100%; max-width: 1100px;">
</p>

**Radixor** is a high-performance, multi-language stemmer for Java, built for production-grade search and text-processing systems.

It modernizes the proven Egothor patch-command trie approach and extends it for deployment realities that classic stemming pipelines do not handle well.

Traditional Egothor-style stemming workflows usually treat a compiled dictionary as a fixed artifact. Once built, its lexical knowledge is effectively closed unless the original source dictionary is recompiled. Radixor removes that constraint. An already compiled stemming structure can be extended with additional words and transformations, which makes it possible to evolve an existing dictionary for domain-specific, customer-specific, or deployment-specific vocabulary without rebuilding the entire lexical base from scratch.

Radixor also improves how ambiguous reductions can be handled at runtime. Instead of always forcing a single result, it can return multiple plausible stems when the input token cannot be reduced unambiguously. This allows downstream systems to preserve linguistic ambiguity where that is operationally useful, whether for retrieval quality, ranking strategies, diagnostics, or domain-specific normalization policies.

The project also has a clear research lineage. The historical idea behind this stemming family is described in Leo Galambos's paper *Lemmatizer for Document Information Retrieval Systems in JAVA* (SOFSEM 2001), which presents a semi-automatic stemming technique designed for Java-based information retrieval systems. In Radixor documentation, this reference serves as historical and algorithmic background rather than as technical documentation of the current implementation.

> Unlike traditional Egothor-based deployments, Radixor can extend an already compiled stemmer dictionary and can return multiple stems when a word is not reducible to a single unambiguous form.

Radixor delivers:

- **Fast runtime stemming** with compact lookup structures
- **Multi-language adaptability** through dictionary-driven compilation
- **Extension of compiled stemmer structures** without full recompilation from source dictionaries
- **Incremental vocabulary growth** for deployment-specific lexical refinement
- **Support for multiple stemming results** when reduction is ambiguous
- **Deterministic behavior** suitable for reproducible processing pipelines
- **Flexible integration paths**, including CLI-based and programmatic workflows
- **Operational transparency** through continuously published quality and benchmark reports

Radixor is intended for teams that require consistent stemming quality at scale, while retaining the ability to evolve lexical resources after compilation and to handle ambiguous reductions with greater precision than traditional single-stem pipelines allow.

## Start here

- Read [Quick Start](quick-start.md) for immediate implementation guidance.
- Use [Programmatic Usage](programmatic-usage.md) for application integration patterns.
- Review [Benchmarking](benchmarking.md) for reproducible performance methodology.
- Open [CI Reports](reports.md) to inspect published build artifacts and quality metrics.
- See the historical paper: [*Lemmatizer for Document Information Retrieval Systems in JAVA*](https://www.researchgate.net/publication/221512865_Lemmatizer_for_Document_Information_Retrieval_Systems_in_JAVA).
