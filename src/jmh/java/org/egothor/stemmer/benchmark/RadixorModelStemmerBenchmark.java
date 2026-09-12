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
import java.util.concurrent.TimeUnit;

import org.egothor.stemmer.ReductionMode;
import org.egothor.stemmer.StemmerPatchTrieLoader;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures the canonical Radixor lookup path for every user-facing model.
 *
 * <p>Each parameter uses the exact stable model ID and its changed-token
 * dictionary corpus. Trial setup performs resource discovery, corpus creation,
 * integrity verification, and trie compilation; measured operations contain
 * only stemming lookups and result consumption. State is confined to a trial
 * and is not retained across model parameters. Timing prefers changed tokens;
 * root-only models use their complete root-preservation corpus instead.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class RadixorModelStemmerBenchmark {

    /** Shared immutable corpus and compiled stemmer for one exact model. */
    @State(Scope.Benchmark)
    public static class ModelState {

        /** Exact user-facing model identifier selected by JMH. */
        @Param({
                "ady-default",
                "af-za-default",
                "afb-default",
                "ail-default",
                "aka-default",
                "am-et-default",
                "ame-default",
                "ang-default",
                "ar-default",
                "arn-default",
                "arz-default",
                "as-in-default",
                "ast-default",
                "aym-default",
                "az-az-default",
                "azg-default",
                "bak-default",
                "be-by-default",
                "bg-bg-default",
                "bn-bd-default",
                "bra-default",
                "bre-default",
                "ca-es-default",
                "ceb-default",
                "chu-default",
                "ckt-default",
                "cly-default",
                "cni-default",
                "cor-default",
                "cpa-default",
                "cre-default",
                "crh-default",
                "cs-cz-default",
                "csb-default",
                "ctp-default",
                "czn-default",
                "da-dk-default",
                "dak-default",
                "de-de-default",
                "dje-default",
                "dsb-default",
                "el-gr-default",
                "es-es-default",
                "et-ee-default",
                "evn-default",
                "fa-ir-default",
                "fi-fi-default",
                "fo-fo-default",
                "fr-fr-default",
                "frm-default",
                "fro-default",
                "frr-default",
                "fur-default",
                "ga-ie-default",
                "gaa-default",
                "gal-default",
                "gmh-default",
                "gml-default",
                "goh-default",
                "got-default",
                "grc-default",
                "gsw-default",
                "gup-default",
                "gv-im-default",
                "hai-default",
                "hbs-default",
                "he-il-default",
                "hil-default",
                "hsi-default",
                "hu-hu-default",
                "hy-am-default",
                "id-id-default",
                "is-is-default",
                "it-it-default",
                "itl-default",
                "izh-default",
                "ja-jp-default",
                "kbd-default",
                "kjh-default",
                "kk-kz-default",
                "kl-gl-default",
                "klr-default",
                "kn-in-default",
                "kod-default",
                "kon-default",
                "krl-default",
                "ky-kg-default",
                "la-default",
                "lg-ug-default",
                "lin-default",
                "liv-default",
                "lld-default",
                "lt-lt-default",
                "lv-lv-default",
                "mag-default",
                "mg-mg-default",
                "mi-nz-default",
                "mk-mk-default",
                "mn-mn-default",
                "mt-mt-default",
                "mwf-default",
                "nap-default",
                "nav-default",
                "nb-no-default",
                "nds-default",
                "nl-nl-default",
                "nn-no-default",
                "non-default",
                "ny-mw-default",
                "ood-default",
                "osx-default",
                "ote-default",
                "pl-pl-polimorf",
                "pl-pl-unimorph",
                "ps-af-default",
                "pt-pt-default",
                "que-default",
                "ro-ro-default",
                "ru-ru-default",
                "sdh-default",
                "see-default",
                "sga-default",
                "shp-default",
                "sjo-default",
                "sme-default",
                "sn-zw-default",
                "sq-al-default",
                "st-za-default",
                "sv-se-default",
                "swc-default",
                "syc-default",
                "tl-ph-default",
                "tr-tr-default",
                "ug-cn-default",
                "uk-ua-default",
                "us-uk-default",
                "uz-uz-default",
                "vro-default",
                "xcl-default",
                "xno-default",
                "xty-default",
                "yi-default",
                "zpv-default",
                "zu-za-default"
        })
        public String modelId;

        /** Canonical timing corpus, repeated only to the timing minimum. */
        private String[] tokens;

        /** Compiled Radixor lookup adapter. */
        private RadixorBenchmarkStemmer stemmer;

        /**
         * Resolves and compiles the selected exact model before measurement.
         *
         * @throws IOException if discovery, integrity verification, corpus parsing,
         *                     or trie compilation fails
         */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            this.tokens = LanguageBenchmarkCorpus.createTokens(this.modelId);
            this.stemmer = new RadixorBenchmarkStemmer(StemmerPatchTrieLoader.loadCompiled(
                    this.modelId, true,
                    ReductionMode.MERGE_SUBTREES_WITH_EQUIVALENT_RANKED_GET_ALL_RESULTS));
        }
    }

    /**
     * Stems the selected model's canonical timing corpus.
     *
     * @param state model-specific trial state
     * @param blackhole result sink
     */
    @Benchmark
    public void radixor(final ModelState state, final Blackhole blackhole) {
        final String[] tokens = state.tokens;
        final RadixorBenchmarkStemmer stemmer = state.stemmer;
        for (String token : tokens) {
            blackhole.consume(stemmer.stem(token));
        }
    }
}
