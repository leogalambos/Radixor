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
package org.egothor.stemmer.benchmark.quality;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.egothor.stemmer.StemmerModelDescriptor;
import org.egothor.stemmer.StemmerModelRegistry;
import org.egothor.stemmer.StemmerPatchTrieLoader.Language;
import org.egothor.stemmer.benchmark.QualityStemmerMatrix;
import org.egothor.stemmer.benchmark.QualityStemmerMatrix.Candidate;

/** Command-line entry point for JMH-backed pairwise stemming-quality reports. */
public final class StemmingQualityApplication {
    private static final int ARGUMENT_COUNT = 9;
    private static final Logger LOGGER = Logger.getLogger(StemmingQualityApplication.class.getName());

    /** Utility class. */
    private StemmingQualityApplication() {
        throw new AssertionError("No instances.");
    }

    /**
     * Generates a complete report or an explicitly labelled filtered report.
     *
     * @param arguments output directory, language filter, candidate filter, mode
     *                  filter, output-policy filter, audit flag, and audit contributor limit
     * @throws IOException if dictionary, JMH adapter, or report processing fails
     */
    public static void main(final String[] arguments) throws IOException {
        if (arguments.length != ARGUMENT_COUNT) {
            throw new IllegalArgumentException("Expected output directory, resource directory, language filter, stemmer filter, dictionary-mode filter, output-policy filter, ranking metric, audit flag, and audit limit.");
        }
        final Path directory = Path.of(arguments[0]);
        final LanguageUniverse universe = LanguageUniverse.discover(Path.of(arguments[1]));
        final Set<Language> languages = parseLanguages(arguments[2]);
        final Set<ProcessingMode> modes = parseModes(arguments[4]);
        final Set<OutputPolicy> policies = parsePolicies(arguments[5]);
        final String stemmerFilter = arguments[3].strip();
        final String rankMetric = arguments[6].strip();
        final boolean audit = Boolean.parseBoolean(arguments[7]);
        final int auditLimit = parseAuditLimit(arguments[8]);
        final boolean filtered = !arguments[2].isBlank() || !stemmerFilter.isBlank()
                || !arguments[4].isBlank() || !arguments[5].isBlank();
        final List<Candidate> candidates = selectCandidates(languages, stemmerFilter);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("The supplied filters select no JMH stemming-quality candidates.");
        }
        if (!filtered && !languages.equals(universe.dictionaries().keySet())) {
            throw new IllegalStateException("The complete evaluation language selection differs from the reconciled dictionary universe.");
        }

        final Map<Candidate, Boolean> multiOutput = new HashMap<>();
        final Set<ResultKey> expected = new HashSet<>();
        for (Candidate candidate : candidates) {
            final boolean multiple = candidate.createStemmer().supportsMultipleOutputs();
            multiOutput.put(candidate, multiple);
            for (ProcessingMode mode : modes) {
                for (OutputPolicy policy : policies) {
                    if (policy == OutputPolicy.PRIMARY_OUTPUT || multiple) {
                        expected.add(new ResultKey(candidate.name(), candidate.resultLanguage(), mode, policy));
                    }
                }
            }
        }

        LOGGER.log(Level.INFO, filtered ? "Starting a filtered stemming-quality report."
                : "Starting the complete stemming-quality report.");
        final Map<String, List<GoldStandardGroup>> dictionaries = new HashMap<>();
        final List<QualityResult> results = new ArrayList<>();
        final List<QualityAudit.Scenario> audits = new ArrayList<>();
        final List<CandidateQualityAudit.Scenario> candidateAudits = new ArrayList<>();
        final StemmerModelRegistry modelRegistry = StemmerModelRegistry.fromContextClassLoader();
        for (Candidate candidate : candidates) {
            final StemmerModelDescriptor model = modelRegistry.require(candidate.dictionaryModelId());
            List<GoldStandardGroup> groups = dictionaries.get(candidate.dictionaryModelId());
            if (groups == null) {
                groups = BundledGoldStandardLoader.loadModel(candidate.dictionaryModelId());
                dictionaries.put(candidate.dictionaryModelId(), groups);
            }
            for (ProcessingMode mode : modes) {
                final QualityStemmerMatrix.BatchStemmer primaryStemmer = candidate.createStemmer();
                final QualityResult primary;
                if (audit && policies.contains(OutputPolicy.PRIMARY_OUTPUT)) {
                    final QualityAudit.Scenario scenario = QualityAudit.evaluate(candidate, mode, groups, auditLimit);
                    audits.add(scenario);
                    primary = withModelProvenance(scenario.result(), model);
                } else {
                    primary = withModelProvenance(
                            QualityEvaluator.evaluateBatch(candidate.name(), candidate.resultLanguage(),
                                    mode, groups, primaryStemmer),
                            model);
                }
                if (policies.contains(OutputPolicy.PRIMARY_OUTPUT)) {
                    results.add(primary);
                    logScenario(candidate, mode, OutputPolicy.PRIMARY_OUTPUT);
                }
                if (multiOutput.get(candidate)) {
                    final QualityResult anyCandidate = withModelProvenance(
                            CandidateAwareEvaluator.evaluate(candidate.name(),
                                    candidate.resultLanguage(), mode, OutputPolicy.ANY_CANDIDATE, groups,
                                    candidate.createStemmer()),
                            model);
                    final QualityResult allCandidates;
                    if (audit) {
                        final CandidateQualityAudit.Scenario scenario = CandidateQualityAudit.evaluate(
                                candidate, mode, groups, primary, anyCandidate, auditLimit);
                        candidateAudits.add(scenario);
                        allCandidates = withModelProvenance(scenario.candidate(), model);
                    } else {
                        allCandidates = withModelProvenance(
                                CandidateAwareEvaluator.evaluate(candidate.name(), candidate.resultLanguage(),
                                        mode, OutputPolicy.ALL_CANDIDATES, groups, candidate.createStemmer()),
                                model);
                    }
                    verifyPolicyInvariants(primary, anyCandidate, allCandidates);
                    if (policies.contains(OutputPolicy.ANY_CANDIDATE)) {
                        results.add(anyCandidate); logScenario(candidate, mode, OutputPolicy.ANY_CANDIDATE);
                    }
                    if (policies.contains(OutputPolicy.ALL_CANDIDATES)) {
                        results.add(allCandidates); logScenario(candidate, mode, OutputPolicy.ALL_CANDIDATES);
                    }
                }
            }
        }
        validateMatrix(expected, results);

        final String suffix = filtered ? "-filtered" : "";
        final Path markdown = directory.resolve("stemming-quality" + suffix + ".md");
        final Path csv = directory.resolve("stemming-quality" + suffix + ".csv");
        QualityReportWriter.writeMarkdown(markdown, results, filtered, universe, candidates, expected.size(), rankMetric);
        QualityReportWriter.writeCsv(csv, results);
        final Path pearson = directory.resolve("metric-correlations-pearson" + suffix + ".csv");
        final Path spearman = directory.resolve("metric-correlations-spearman" + suffix + ".csv");
        MetricCorrelationWriter.write(pearson, spearman, results);
        System.out.println("Stemming-quality Markdown report: " + markdown.toAbsolutePath());
        System.out.println("Stemming-quality CSV report: " + csv.toAbsolutePath());
        System.out.println("Pearson metric-correlation report: " + pearson.toAbsolutePath());
        System.out.println("Spearman metric-correlation report: " + spearman.toAbsolutePath());
        if (audit) {
            final Path auditPath = directory.resolve("stemming-quality-audit" + suffix + ".md");
            QualityAudit.write(auditPath, audits);
            CandidateQualityAudit.append(auditPath, candidateAudits);
            System.out.println("Stemming-quality audit report: " + auditPath.toAbsolutePath());
        }
        LOGGER.log(Level.INFO, "Completed the stemming-quality report with {0} evaluated scenarios.", results.size());
    }

    /** Attaches the exact independently versioned model used by one scenario. */
    private static QualityResult withModelProvenance(final QualityResult result,
            final StemmerModelDescriptor model) {
        return result.withModelProvenance(model.id(), model.version(), model.sha256());
    }

    /**
     * Selects candidates directly from the authoritative JMH matrix.
     *
     * <p>
     * An unfiltered publication run evaluates only each language's registered
     * default model. Optional model variants remain available only through an
     * explicit stemmer or model-ID filter and therefore cannot enter the complete
     * documentation snapshot accidentally.
     * </p>
     */
    static List<Candidate> selectCandidates(final Set<Language> languages, final String filter) {
        return QualityStemmerMatrix.candidates().stream()
                .filter(candidate -> languages.contains(candidate.language()))
                .filter(candidate -> !filter.isBlank()
                        || candidate.dictionaryModelId().equals(candidate.language().defaultModelId()))
                .filter(candidate -> matchesFilter(candidate, filter))
                .toList();
    }

    /** Tests one candidate against the exact, suffix, and model-ID filters. */
    private static boolean matchesFilter(final Candidate candidate, final String filter) {
        if (filter.isBlank()) {
            return true;
        }
        final String normalizedFilter = filter.toUpperCase(Locale.ROOT);
        final String name = candidate.name().toUpperCase(Locale.ROOT);
        final String model = candidate.dictionaryModelId().toUpperCase(Locale.ROOT);
        return name.equals(normalizedFilter) || name.endsWith("_" + normalizedFilter)
                || model.equals(normalizedFilter) || model.endsWith("-" + normalizedFilter);
    }

    /** Parses a comma-separated language filter or selects every language. */
    private static Set<Language> parseLanguages(final String filter) {
        if (filter.isBlank()) {
            return EnumSet.allOf(Language.class);
        }
        final Set<Language> selected = EnumSet.noneOf(Language.class);
        for (String item : filter.split(",")) {
            selected.add(Language.valueOf(item.strip().toUpperCase(Locale.ROOT)));
        }
        return selected;
    }

    /** Parses a comma-separated mode filter or selects both processing modes. */
    private static Set<ProcessingMode> parseModes(final String filter) {
        if (filter.isBlank()) {
            return EnumSet.allOf(ProcessingMode.class);
        }
        final Set<ProcessingMode> selected = EnumSet.noneOf(ProcessingMode.class);
        for (String item : filter.split(",")) {
            selected.add(ProcessingMode.valueOf(item.strip().toUpperCase(Locale.ROOT)));
        }
        return selected;
    }

    /** Parses a comma-separated output-policy filter or selects both policies. */
    private static Set<OutputPolicy> parsePolicies(final String filter) {
        if (filter.isBlank()) { return EnumSet.allOf(OutputPolicy.class); }
        final Set<OutputPolicy> selected = EnumSet.noneOf(OutputPolicy.class);
        for (String item : filter.split(",")) {
            selected.add(OutputPolicy.valueOf(item.strip().toUpperCase(Locale.ROOT)));
        }
        return selected;
    }

    /** Enforces the mathematical monotonicity guaranteed by primary-output inclusion. */
    private static void verifyPolicyInvariants(final QualityResult primary, final QualityResult any,
            final QualityResult all) {
        if (any.underErrorPairs() > primary.underErrorPairs() || all.underErrorPairs() > primary.underErrorPairs()
                || any.underErrorPairs() != all.underErrorPairs() || any.overErrorPairs() > primary.overErrorPairs()
                || all.overErrorPairs() < primary.overErrorPairs()) {
            throw new IllegalStateException("Output-policy invariants failed for stemmer " + primary.stemmer()
                    + ", language " + primary.language() + ", dictionary mode " + primary.processingMode()
                    + ": PRIMARY_OUTPUT under/over=" + primary.underErrorPairs() + "/" + primary.overErrorPairs()
                    + ", ANY_CANDIDATE under/over=" + any.underErrorPairs() + "/" + any.overErrorPairs()
                    + ", ALL_CANDIDATES under/over=" + all.underErrorPairs() + "/" + all.overErrorPairs() + ".");
        }
    }

    /** Validates exact expected and actual result keys, including duplicates. */
    private static void validateMatrix(final Set<ResultKey> expected, final List<QualityResult> results) {
        final Set<ResultKey> actual = new HashSet<>();
        for (QualityResult result : results) {
            final ResultKey key = ResultKey.from(result);
            if (!actual.add(key)) { throw new IllegalStateException("Duplicate stemming-quality result key: " + key + "."); }
        }
        if (!expected.equals(actual)) {
            final Set<ResultKey> missing = new HashSet<>(expected); missing.removeAll(actual);
            final Set<ResultKey> unexpected = new HashSet<>(actual); unexpected.removeAll(expected);
            throw new IllegalStateException("Stemming-quality result matrix mismatch. Missing rows: " + missing
                    + "; unexpected rows: " + unexpected + ".");
        }
    }

    /** Immutable expected-matrix key. */
    private record ResultKey(String stemmer, String language, ProcessingMode mode, OutputPolicy policy) {
        /** Creates a key from one immutable result. */
        private static ResultKey from(final QualityResult result) {
            return new ResultKey(result.stemmer(), result.language(), result.processingMode(), result.outputPolicy());
        }
    }

    /** Parses and validates the deterministic audit contributor limit. */
    private static int parseAuditLimit(final String value) {
        final int limit = Integer.parseInt(value);
        if (limit < 1) {
            throw new IllegalArgumentException("The audit contributor limit must be positive.");
        }
        return limit;
    }

    /** Logs one completed scenario without per-word noise. */
    private static void logScenario(final Candidate candidate, final ProcessingMode mode, final OutputPolicy policy) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "Completed stemming-quality evaluation for stemmer {0}, language {1}, dictionary mode {2}, and output policy {3}.",
                    new Object[] {candidate.name(), candidate.resultLanguage(), mode, policy});
        }
    }

}
