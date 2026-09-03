# Scientific Results and Publications

Radixor is developed as both production software and a reproducible research program. The seven-paper Radixor series separates questions that are often mixed together in stemmer evaluation: generalization beyond training forms, hyperparameter sensitivity, multi-objective error trade-offs, learned conflation policy, runtime structure, lexical-resource consistency, and compiler semantics.

Together, the papers characterize Radixor as a **lexicon-trained transformation system rather than a dictionary lookup engine**: lexical resources provide training evidence, the compiler induces executable patch commands and trie structure, and runtime behavior can extend beyond explicitly listed forms. Each paper establishes a deliberately narrower part of that statement and publishes its own evidence boundary.

!!! note "Publication and evidence status"
    The articles linked below are author-released research manuscripts on ResearchGate. ResearchGate availability is not a claim of journal or conference peer-review status. The project documentation and repository provide the current implementation, benchmark data, protocols, and reproduction material; each manuscript freezes the evidence identity used by its own analyses.

    The newly uploaded ResearchGate records are not yet exposed through stable public publication URLs in web indexing. Until those identifiers become discoverable, each **ResearchGate** link below performs an exact-title search on ResearchGate rather than inventing a publication identifier.

[Leo Galambos on ResearchGate](https://www.researchgate.net/profile/Leo-Galambos){ .md-button }

## The research program at a glance

| Research question | Principal contribution | Paper |
|---|---|---:|
| Does a lexicon-trained stemmer transform genuinely unseen lexical families? | Controlled multilingual evidence of within-resource, out-of-training-family transformation transfer | 1 |
| Are edit costs merely implementation constants? | Multilingual evidence that edit costs act as structural hyperparameters and that nominal settings can collapse to identical induced behavior | 2 |
| How should under- and over-conflation trade-offs be compared? | Recursive Pareto strata and error-regime classification in the pairwise lexical-conflation error space | 3 |
| Can the same architecture learn a different stemming objective from different reference policy? | Controlled German evidence of policy-conditioned relational generalization | 4 |
| Does lexicon size imply dictionary-like online cost? | Runtime evidence relating execution cost more closely to compiled structure than to raw training size | 5 |
| Can internal resource-consistency evidence simplify the learned transformation vocabulary? | A multilingual audit connecting resource-local filtering to distinct patch-command vocabulary | 6 |
| What exactly is preserved or generalized by trie reduction? | Formal semantics for mode-specific canonicalization and accepting-leaf contraction | 7 |

The sequence is intentional. Papers 1--6 establish empirical properties and measurement boundaries from different directions; Paper 7 isolates the formal compiler semantics that explain what the reduced patch-command trie preserves and what accepting leaves are allowed to generalize.

---

## Radixor 1 — Generalization beyond the training lexicon

### *Does a Lexicon-Trained Stemmer Generalize? A 20-Language Study of Held-Out Lexical Families*

[ResearchGate](https://www.researchgate.net/search/publication?q=Does%20a%20Lexicon-Trained%20Stemmer%20Generalize%3F%20A%2020-Language%20Study%20of%20Held-Out%20Lexical%20Families){ .md-button }
[Generalization evidence](benchmarks/generalization.md){ .md-button }
[Protocol](benchmarks/reference/generalization-methodology.md){ .md-button }

**Research question.** Does Radixor merely reproduce mappings seen during training, or does the induced transformation mechanism transfer to lexical families that were withheld from model construction?

**Principal result.** The study evaluates 20 UniMorph-derived language resources under complete lexical-family holdout, ten training-family coverage levels, and five deterministic seeded rankings: **1,000 build/evaluation scenarios**, of which 900 contain a non-empty held-out population. It establishes **within-resource, out-of-training-family transformation transfer with exact parser-normalized surface-overlap control**.

**Interpretation boundary.** The coverage sweep is descriptive, not a fixed-test causal learning curve: each coverage level has a different held-out complement. The paper does not claim arbitrary out-of-domain accuracy, contextual lemmatization, universal derivational competence, or equivalence between exact canonical-target recovery and downstream retrieval effectiveness.

**Why it matters.** This is the empirical foundation for calling Radixor *lexicon-trained but not lexicon-bound*. The learned object is an executable transformation relation, not a closed list of admissible runtime tokens.

---

## Radixor 2 — Edit costs as model-structure controls

### *Edit Costs as Structural Hyperparameters in Dictionary-Induced Stemming: A Multilingual Sensitivity Study*

[ResearchGate](https://www.researchgate.net/search/publication?q=Edit%20Costs%20as%20Structural%20Hyperparameters%20in%20Dictionary-Induced%20Stemming%3A%20A%20Multilingual%20Sensitivity%20Study){ .md-button }
[Edit-cost results](benchmarks/edit-cost-sensitivity.md){ .md-button }
[Protocol](benchmarks/reference/edit-cost-methodology.md){ .md-button }

**Research question.** How strongly do insertion, deletion, replacement, and related edit costs alter the command vocabulary and linguistic behavior learned from the same lexical evidence?

**Principal result.** The frozen campaign contains **16,700 physical model builds** representing **234,000 logical observations** over the sampled 234-point cost grid. Exact equivalence analysis reduces those nominal settings to **334 distinct induced behaviors** across the audited resources.

**Interpretation boundary.** Exact command-sequence equivalence, subset invariance, campaign reduction, and representation counts are deterministic results. Correlations, bootstrap summaries, guarded optima, and same-matrix operating points are descriptive analyses rather than population inference over languages.

**Why it matters.** Radixor's training objective is tunable at the level where lexical mappings become executable transformations.

---

## Radixor 3 — Multi-objective stemmer comparison

### *Classifying Multilingual Stemmers by Pareto Strata: Error Regimes in Pairwise Lexical Conflation*

[ResearchGate](https://www.researchgate.net/search/publication?q=Classifying%20Multilingual%20Stemmers%20by%20Pareto%20Strata%3A%20Error%20Regimes%20in%20Pairwise%20Lexical%20Conflation){ .md-button }
[Benchmark results](benchmarks/index.md){ .md-button }
[Linguistic-quality method](benchmarks/reference/linguistic-quality.md){ .md-button }

**Research question.** A scalar score can hide whether a stemmer fails mainly through under-conflation or over-conflation. What does the comparison look like when those error dimensions are treated explicitly?

**Principal result.** The paper classifies deterministic systems in a two-objective under-/over-conflation error space using candidate-set-relative recursive Pareto strata. It distinguishes aggregate **error-vector equivalence** from output equivalence; identifies recall, specificity, supported-compromise, and unsupported-efficient regimes; derives exact support intervals for the raw pair cost `C_rho = rho * FN + FP`; and follows constrained multi-output trajectories. The frozen analysis maps **190 deterministic implementation rows to 174 aggregate error vectors**.

**Interpretation boundary.** Pareto depth and regime are conditional on the language, reference resource, processing mode, implementation version, output policy, and candidate set.

**Why it matters.** The paper makes the quality comparison auditable without forcing every application into one arbitrary scalar preference between false splits and false merges.

---

## Radixor 4 — The stemming policy is learned from data

### *The Stemming Policy Is Data: Adapting a Lexicon-Trained Stemmer to Alternative Conflation Objectives*

[ResearchGate](https://www.researchgate.net/search/publication?q=The%20Stemming%20Policy%20Is%20Data%3A%20Adapting%20a%20Lexicon-Trained%20Stemmer%20to%20Alternative%20Conflation%20Objectives){ .md-button }
[Reproduction harness](https://github.com/leogalambos/Radixor/tree/main/experiments/policy-transfer){ .md-button }

**Research question.** If two gold resources encode different conflation policies over the same language, can the same Radixor architecture learn the policy difference rather than imposing one hard-coded definition of stemming?

**Principal result.** Using the two public German CISTEM/CELEX-derived policy resources, an independent clean replay regenerated all **30 persisted cross-fit models** with matching model-file SHA-256 identities. The central result is deliberately narrow: **sparse but directionally robust policy-conditioned relational generalization** along one nested aggressive-to-conservative German policy axis. Pair-micro switch coverage is about **0.61%** for the primary medoid encoding.

**Interpretation boundary.** The result is a controlled one-language policy study, not evidence that every conceivable conflation objective is learnable.

**Why it matters.** A material part of the stemming policy can reside in the lexical training relation and be changed without replacing the runtime architecture.

---

## Radixor 5 — Compiled structure and runtime efficiency

### *Runtime Efficiency of Lexicon-Trained Stemming: Compiled Structure and a Speed–Reference-Agreement Envelope in Radixor*

[ResearchGate](https://www.researchgate.net/search/publication?q=Runtime%20Efficiency%20of%20Lexicon-Trained%20Stemming%3A%20Compiled%20Structure%20and%20a%20Speed-Reference-Agreement%20Envelope%20in%20Radixor){ .md-button }
[Benchmarking framework](benchmarking.md){ .md-button }
[Published benchmark results](benchmarks/index.md){ .md-button }

**Research question.** Does training from a large lexicon imply dictionary-like online cost, or is runtime governed more directly by the compact structure compiled from that lexicon?

**Principal result.** On the frozen Java workstation and dictionary-derived timing workload, canonical Radixor execution spans **42.8--150.6 ns per occurrence**, with an unweighted 20-resource median of **67.8 ns**. Across 18 direct Snowball comparisons, the unweighted median Snowball/Radixor ratio is **2.33×** under the primary closest-available same-harness policy and **2.57×** under the canonical policy; a cross-policy invariant yields **17 stable Radixor advantages and one Finnish parity case**. Internal-node count has Spearman `rho = 0.931` with canonical runtime, compared with `rho = 0.723` for training rows.

**Interpretation boundary.** The workload is dictionary-derived, not natural production traffic or an OOV corpus. The paper does not claim universal fastest-stemmer status or causal per-node cost.

**Why it matters.** Lexical training size and runtime state are separate layers; compiled structure is the more informative description of the online execution problem.

---

## Radixor 6 — Lexical-resource consistency and transformation vocabulary

### *Lexical Resource Consistency and Learned Transformation Vocabulary in Multilingual Stemming*

[ResearchGate](https://www.researchgate.net/search/publication?q=Lexical%20Resource%20Consistency%20and%20Learned%20Transformation%20Vocabulary%20in%20Multilingual%20Stemming){ .md-button }
[Filtered candidate models](filtered-models.md){ .md-button }

**Research question.** When internally inspectable resource inconsistencies are removed conservatively, how does that evidence propagate into the transformation vocabulary learned by the compiler?

**Principal result.** Twelve high-command resource configurations are audited. Ten non-destructive candidate resources are materialized; Dutch and Persian are explicit **no-safe-change** outcomes. Among the nine candidates using the homogeneous grouped rule, eight reduce distinct patch-command vocabulary, with the summed vocabulary changing from **20,281 to 19,842 (-2.165%)** and a median per-resource reduction of **1.334%**. German is reported separately: **6,986 to 5,313 commands (-23.948%)**.

**Interpretation boundary.** Command-count reduction is not evidence of trie-node, runtime, memory, or downstream retrieval improvement, and the filtering predicate is not a semantic oracle for linguistic correctness.

**Why it matters.** The paper connects data quality to the learned executable vocabulary while showing precisely where that connection stops.

---

## Radixor 7 — Formal semantics of the compiler

### *Compiling Learned Lexical Transformations: Semantic Canonicalization and Accepting-Leaf Generalization in Patch-Command Tries*

[ResearchGate](https://www.researchgate.net/search/publication?q=Compiling%20Learned%20Lexical%20Transformations%3A%20Semantic%20Canonicalization%20and%20Accepting-Leaf%20Generalization%20in%20Patch-Command%20Tries){ .md-button }
[Architecture and reduction](architecture-and-reduction.md){ .md-button }
[Reduction semantics](reduction-semantics.md){ .md-button }

**Research question.** What does Radixor's trie compiler preserve when it shares subtrees, and what exactly changes when a locally uniform region becomes an accepting leaf?

**Principal result.** The formal analysis separates two mathematically different compiler transformations:

1. **mode-specific semantic subtree canonicalization**, which shares recursively equivalent decision subgraphs while preserving the selected reduction-mode observable; and
2. **uniform accepting-leaf contraction**, which preserves the existing singleton value semantics of a locally uniform region while extending that value decision to additional continuations.

For stemming, accepting leaves generalize **command selection**, not a constant stem. If `C(w)` selects a patch command and `Exec(c, w)` executes it, runtime output is `H(w) = Exec(C(w), w)`.

The paper proves soundness/congruence of the computable reduction signature for all public reduction modes, aggregation stability, completeness for productive ranked and unordered nodes in the contraction-free exact-trie phase, relative state minimality for that canonicalization-only quotient, quotient acyclicity, finite termination/decidability under the stated assumptions, and a value-semantic domain-extension theorem for uniform contraction. It also records that repeated recursive signature hashing admits quadratic work on a degenerate chain.

**Interpretation boundary.** The theorem is about compiler semantics, not the linguistic correctness of newly covered forms. It does not claim global minimality of the production contraction-enabled graph, minimum bytes or runtime, or arbitrary-input exact equivalence after accepting-leaf contraction.

**Why it matters.** Structural sharing and behavioral extension are distinct operations, and suffix-conditioned command selection is not the same claim as discovering linguistic morpheme rules.

---

## How to read and cite the series

The seven papers are cumulative in **scope**, not cumulative in the sense that a later result silently broadens an earlier claim. For scientific use:

- cite the individual paper for the frozen experiment, theorem, or analysis it reports;
- use the current documentation for present software behavior, API contracts, model catalogs, and continuously maintained benchmark pages;
- preserve each paper's stated population, reference-resource, metric, and implementation boundaries when quoting numerical results; and
- treat reproducibility artifacts, source identities, checksums, and explicit negative claims as part of the result rather than as supplementary marketing material.

The resulting research program is intentionally modular: **empirical linguistics, multi-objective evaluation, systems performance, data quality, and compiler correctness are tested separately and connected only where the evidence supports the connection.**
