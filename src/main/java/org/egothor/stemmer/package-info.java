/**
 * Provides the core Egothor-style stemming infrastructure based on compact
 * patch-command tries.
 *
 * <p>
 * The package centers on a read-only {@link org.egothor.stemmer.FrequencyTrie}
 * that maps word forms to one or more values together with their recorded local
 * frequencies. In the stemming use case, these values are compact patch
 * commands that reconstruct a canonical stem from an observed surface form. The
 * trie is built through {@link org.egothor.stemmer.FrequencyTrie.Builder},
 * reduced into a canonical immutable structure, and then queried through
 * deterministic {@code get(String)}, {@code getAll(String)}, and
 * {@code getEntries(String)} operations.
 * </p>
 *
 * <p>
 * Patch commands are produced and interpreted by
 * {@link org.egothor.stemmer.PatchCommandEncoder}. The encoder follows the
 * historical Egothor convention in which edit instructions are serialized for
 * application from the end of the source word toward its beginning. The
 * implementation supports canonical no-operation patches for identity
 * transformations and compact commands for insertion, deletion, replacement,
 * and suffix-preserving transitions.
 * </p>
 *
 * <p>
 * Dictionary loading is provided by
 * {@link org.egothor.stemmer.StemmerPatchTrieLoader}, which reads the
 * traditional line-oriented stemmer resource format in which each non-empty
 * logical line starts with a canonical stem followed by known surface variants.
 * Parsing is delegated to {@link org.egothor.stemmer.StemmerDictionaryParser},
 * which normalizes input to lower case using {@link java.util.Locale#ROOT} and
 * supports whole-line as well as trailing remarks introduced by {@code #} or
 * {@code //}. During loading, each variant is converted into a patch command
 * targeting the canonical stem, and the stem itself may optionally be stored
 * under the canonical no-operation patch.
 * </p>
 *
 * <p>
 * Trie compilation behavior is controlled by
 * {@link org.egothor.stemmer.ReductionMode} and
 * {@link org.egothor.stemmer.ReductionSettings}. These types define how
 * semantically equivalent subtrees may be merged during compilation in order to
 * reduce the size of the final immutable trie while preserving the intended
 * lookup semantics. Depending on the selected mode, reduction may preserve full
 * ranked {@code getAll()} semantics, unordered value equivalence, or dominant
 * {@code get()} semantics subject to configurable dominance thresholds.
 * </p>
 *
 * <p>
 * Persisted compiled tries are supported through
 * {@link org.egothor.stemmer.StemmerPatchTrieBinaryIO} and the corresponding
 * binary loading and saving methods on
 * {@link org.egothor.stemmer.StemmerPatchTrieLoader}. The persisted form wraps
 * the native {@link org.egothor.stemmer.FrequencyTrie} binary format in GZip
 * compression and is intended for efficient deployment and runtime loading.
 * Reconstructing a writable builder from an already compiled trie is supported
 * by {@link org.egothor.stemmer.FrequencyTrieBuilders}.
 * </p>
 *
 * <p>
 * For offline preparation of deployment artifacts, the package also provides
 * the {@link org.egothor.stemmer.Compile} command-line utility, which reads a
 * dictionary source, applies the configured reduction strategy, and writes the
 * resulting compressed binary trie.
 * </p>
 *
 * <p>
 * The package is designed for deterministic behavior, compact persisted
 * representation, and efficient runtime lookup. Public APIs are intentionally
 * focused on immutable compiled structures for read paths, with separate
 * explicit builder-oriented entry points for mutation and reconstruction.
 * </p>
 */
package org.egothor.stemmer;
