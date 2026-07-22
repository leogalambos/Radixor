# Tested Stemmer Inventory

The JMH adapter registry is authoritative for evaluated implementations and language mappings. Names below describe the implementation actually invoked, not an abstract algorithm in every possible implementation. Unsupported language combinations are omitted rather than scored as failures.

| Family or implementation | Upstream / attribution | Tested version or revision | Evaluated scope | Output capability and adapter behaviour | Interpretation notes |
| --- | --- | --- | --- | --- | --- |
| Radixor | Egothor / Radixor project | Current repository revision; exact revision was not embedded in the quality CSV | All 20 reconciled default model languages; 19 have benchmark pages | Deterministic preferred patch via `get`; ranked distinct alternatives via `getAll`; primary is always included | Model-dictionary-derived compiled patch trie. Default rows use each language's stable default model ID. |
| Apache Lucene language stem filters | Apache Lucene project | 10.5.0 | Adapter-declared language-specific subsets | TokenFilter lifecycle and language normalization match JMH; normally single-output | Light, minimal, possessive, and language stem filters deliberately implement different scopes. Narrow scope is not a defect. |
| Apache Lucene SnowballFilter | Apache Lucene project using Snowball algorithms | Lucene 10.5.0 | Snowball-supported subset of Radixor languages | Single primary token emitted through the Lucene TokenFilter path | Includes TokenStream overhead and required normalization. |
| Official Snowball Java | Snowball project | Repository preparation downloads the configured upstream Java distribution; an immutable revision was not recorded in the quality CSV | Same-language adapter subset | Direct generated Java API; single output | Rule-based suffix algorithms provide broad baselines rather than dictionary-root guarantees. |
| Lucene Stempel | Apache Lucene / Polish stemming tables | Lucene 10.5.0 | Polish | Direct and TokenFilter paths where registered; single primary output | Table-driven Polish implementation. |
| Morfologik | Morfologik project; Lucene integration by Apache Lucene | Morfologik 2.1.9, Lucene integration 10.5.0; Ukrainian dictionary artifact 4.9.1 | Registered Polish and Ukrainian paths | Deterministic first lemma for primary comparison; all distinct lemma strings for candidate policies | Several analyses may share a lemma and are deduplicated by exact string equality. |
| Hunspell via Lucene | Hunspell dictionaries from the `wooorm/dictionaries` repository; adapter by Apache Lucene | Lucene 10.5.0; dictionary repository revision was not recorded | Configured German, English, Spanish, French, Dutch, Polish, and Ukrainian dictionaries | First emitted stem is primary; all distinct stems at the token position are candidates | Dictionary content and affix rules differ by language. |
| CISTEM | Leonie Weissweiler, CISTEM project | Upstream `master` source path used by preparation; immutable commit not recorded | German | Single output | German stemming algorithm; benchmark-only implementation and gold-standard preparation remain under JMH infrastructure. |
| OpenNLP Porter | Apache OpenNLP project | Version resolved by `gradle/opennlp-benchmarks.gradle` and `gradle.lockfile` | English | Direct single output | Porter-family English baseline. |
| Lucene Porter source copy | Apache Lucene project | 10.5.0 source artifact | English | Package-isolated benchmark-only generated source; single output | Generated into the JMH build tree, never production code. |
| Paice/Husk Lancaster | Upstream Java implementation from `Hopper262/paice-husk-stemmer` | Configured upstream branch/revision in `gradle/paicehusk-benchmarks.gradle`; immutable commit not recorded | English | Direct single output | Aggressive rule-based English baseline; benchmark-only generated source. |

## Preprocessing and lifecycle

The quality evaluator calls the same adapter matrix used by JMH. Each language mapping is explicit. Retained dictionary forms are not evaluation-lowercased or normalized. Where an implementation requires preprocessing, such as Lucene German or Persian normalization, that operation is part of its documented adapter path. Stateful TokenFilters are reset through the same sequential lifecycle used by the benchmark and are not invoked concurrently.

Candidate sets are non-null, non-empty, contain the deterministic primary output, contain no null strings, and are deduplicated using exact Java string equality. Gold-standard group identity never selects, removes, or ranks a candidate.

## Coverage fairness

Radixor coverage is derived from registered default descriptors reconciled with language enumeration. Third-party coverage is the intersection of that universe with actual adapter support. Absence therefore means “not supported or not configured for this language,” not “zero quality.” Optional `pl-pl-polimorf` is a separate model row and does not replace default `pl-pl-unimorph`. Consult each language page for the exact evaluated rows.

Project authors and organizations are named only where repository configuration or source notices establish attribution. No broader authorship or license claim is inferred when metadata was not captured.
The JMH runtime configuration directly includes optional models needed for controlled comparisons; ordinary users do not receive these benchmark-only dependencies transitively. Historical rows retain their original model inputs. See [Model Selection and Loading](../../model-selection-and-loading.md).
