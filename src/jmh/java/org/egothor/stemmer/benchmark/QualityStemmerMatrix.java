/*******************************************************************************
 * Copyright (C) 2026, Leo Galambos
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package org.egothor.stemmer.benchmark;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.EnumSet;

import org.egothor.stemmer.StemmerPatchTrieLoader.Language;

/** Authoritative analytical view of the candidate matrix defined by the JMH quality benchmark. */
public final class QualityStemmerMatrix {
    /** Optional Polish PoliMorf model included as an explicit non-default comparison. */
    private static final String POLISH_POLIMORF_MODEL_ID = "pl-pl-polimorf";

    /** Utility class. */
    private QualityStemmerMatrix() {
        throw new AssertionError("No instances.");
    }

    /**
     * Returns every currently registered JMH quality candidate in declaration order.
     * The returned list is immutable and is derived directly from the benchmark enum.
     *
     * @return complete immutable candidate list
     */
    public static List<Candidate> candidates() {
        final List<Candidate> candidates = new ArrayList<>();
        Arrays.stream(StemmerComparisonBenchmarkQuality.QualityCandidate.values())
                .map(candidate -> new Candidate(candidate.name(), candidate.radixorLanguage(),
                        () -> adapt(candidate.createStemmer())))
                .forEach(candidates::add);
        final EnumSet<Language> registeredRadixorLanguages = candidates.stream()
                .filter(candidate -> candidate.name().endsWith("_RADIXOR"))
                .map(Candidate::language).collect(() -> EnumSet.noneOf(Language.class), EnumSet::add, EnumSet::addAll);
        Arrays.stream(Language.values()).filter(language -> !registeredRadixorLanguages.contains(language))
                .map(language -> new Candidate(language.name() + "_RADIXOR", language,
                        () -> adapt(StemmerComparisonBenchmarkQuality.createRadixorQualityStemmer(language))))
                .forEach(candidates::add);
        Arrays.stream(HunspellStemmerComparisonBenchmarkQuality.HunspellLanguageCase.values())
                .map(languageCase -> new Candidate("HUNSPELL_" + languageCase.name() + "_LUCENE_FILTER",
                        languageCase.radixorLanguage(),
                        () -> new BatchStemmer() {
                            /** {@inheritDoc} */
                            @Override public String[] stem(final String[] forms) throws IOException {
                                return HunspellStemmerComparisonBenchmarkQuality.stemForQuality(languageCase, forms);
                            }
                            /** {@inheritDoc} */
                            @Override public List<List<String>> stemCandidates(final String[] forms) throws IOException {
                                return HunspellStemmerComparisonBenchmarkQuality.stemCandidatesForQuality(languageCase, forms);
                            }
                            /** {@inheritDoc} */
                            @Override public boolean supportsMultipleOutputs() { return true; }
                        }))
                .forEach(candidates::add);
        final List<Candidate> defaultPolishCandidates = candidates.stream()
                .filter(candidate -> candidate.language() == Language.PL_PL)
                .toList();
        candidates.add(new Candidate("POLISH_POLIMORF_RADIXOR", Language.PL_PL,
                POLISH_POLIMORF_MODEL_ID, POLISH_POLIMORF_MODEL_ID,
                () -> adapt(StemmerComparisonBenchmarkQuality.createRadixorQualityStemmer(POLISH_POLIMORF_MODEL_ID))));
        defaultPolishCandidates
                .forEach(candidate -> candidates.add(new Candidate(candidate.name(), candidate.language(),
                        POLISH_POLIMORF_MODEL_ID, POLISH_POLIMORF_MODEL_ID, candidate.factory)));
        return List.copyOf(candidates);
    }

    /** Adapts one authoritative general-matrix stemmer without changing capability semantics. */
    private static BatchStemmer adapt(final StemmerComparisonBenchmarkQuality.CandidateStemmer stemmer) {
        return new BatchStemmer() {
            /** {@inheritDoc} */
            @Override public String[] stem(final String[] forms) throws IOException { return stemmer.stem(forms); }
            /** {@inheritDoc} */
            @Override public List<List<String>> stemCandidates(final String[] forms) throws IOException {
                return stemmer.stemCandidates(forms);
            }
            /** {@inheritDoc} */
            @Override public boolean supportsMultipleOutputs() { return stemmer.supportsMultipleOutputs(); }
        };
    }

    /** One JMH candidate and its authoritative dictionary-language mapping. */
    public static final class Candidate {
        private final String name;
        private final Language language;
        private final String resultLanguage;
        private final String dictionaryModelId;
        private final StemmerFactory factory;

        /** Creates an immutable facade over one benchmark candidate. */
        private Candidate(final String name, final Language language,
                final StemmerFactory factory) {
            this(name, language, language.name(), language.defaultModelId(), factory);
        }

        /** Creates an immutable facade over one benchmark candidate and dictionary model. */
        private Candidate(final String name, final Language language, final String resultLanguage,
                final String dictionaryModelId, final StemmerFactory factory) {
            this.name = Objects.requireNonNull(name, "name");
            this.language = Objects.requireNonNull(language, "language");
            this.resultLanguage = Objects.requireNonNull(resultLanguage, "resultLanguage");
            this.dictionaryModelId = Objects.requireNonNull(dictionaryModelId, "dictionaryModelId");
            this.factory = Objects.requireNonNull(factory, "factory");
        }

        /** @return stable JMH candidate name */
        public String name() {
            return this.name;
        }

        /** @return registered Radixor gold-standard dictionary language */
        public Language language() {
            return this.language;
        }

        /** @return stable report language or model label */
        public String resultLanguage() {
            return this.resultLanguage;
        }

        /** @return exact dictionary model used as the gold-standard grouping source */
        public String dictionaryModelId() {
            return this.dictionaryModelId;
        }

        /**
         * Creates a scenario-confined adapter using exactly the JMH factory and preprocessing path.
         *
         * @return sequential batch stemmer
         * @throws IOException if benchmark-only resources cannot be loaded
         */
        public BatchStemmer createStemmer() throws IOException {
            return this.factory.create();
        }
    }

    /** Internal checked factory shared by the JMH quality registries. */
    @FunctionalInterface
    private interface StemmerFactory {
        /** @return a scenario-confined adapter @throws IOException if resources fail */
        BatchStemmer create() throws IOException;
    }

    /** Sequential, scenario-confined batch stemmer contract. */
    @FunctionalInterface
    public interface BatchStemmer {
        /**
         * Stems all supplied forms in order.
         *
         * @param forms input forms, never {@code null}
         * @return one non-null output per form
         * @throws IOException when the JMH adapter fails
         */
        String[] stem(String[] forms) throws IOException;

        /** Returns complete candidate sets; single-output adapters return singleton sets. */
        default List<List<String>> stemCandidates(final String[] forms) throws IOException {
            return Arrays.stream(stem(forms)).map(List::of).toList();
        }

        /** @return whether this adapter exposes genuine alternative outputs */
        default boolean supportsMultipleOutputs() { return false; }
    }
}
