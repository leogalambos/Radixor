package org.egothor.stemmer.benchmark.quality;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.egothor.stemmer.benchmark.QualityStemmerMatrix.BatchStemmer;
import org.egothor.stemmer.benchmark.QualityStemmerMatrix.Candidate;

/** Produces deterministic word-level diagnostics for genuinely multi-output adapters. */
final class CandidateQualityAudit {
    /** Utility class. */
    private CandidateQualityAudit() { throw new AssertionError("No instances."); }

    /** Evaluates candidate output and retains the largest candidate sets for reproducible inspection. */
    static Scenario evaluate(final Candidate candidate, final ProcessingMode mode,
            final List<GoldStandardGroup> groups, final QualityResult primary, final QualityResult any,
            final int limit) throws IOException {
        final List<String> forms = new ArrayList<>();
        final List<Integer> groupIndexes = new ArrayList<>();
        final List<Integer> rows = new ArrayList<>();
        for (int group = 0; group < groups.size(); group++) {
            final GoldStandardGroup item = groups.get(group);
            if (!mode.includes(item.forms())) { continue; }
            for (String form : item.forms()) { forms.add(form); groupIndexes.add(group); rows.add(item.rowNumber()); }
        }
        final BatchStemmer stemmer = candidate.createStemmer();
        final String[] primaryOutputs = stemmer.stem(forms.toArray(String[]::new));
        final List<List<String>> rawCandidates = stemmer.stemCandidates(forms.toArray(String[]::new));
        final Map<String, List<Integer>> inverted = new HashMap<>();
        final Map<Integer, Long> candidateCountDistribution = new java.util.TreeMap<>();
        final List<List<String>> candidateSets = new ArrayList<>();
        for (int index = 0; index < forms.size(); index++) {
            final TreeSet<String> canonical = new TreeSet<>(rawCandidates.get(index));
            canonical.add(primaryOutputs[index]);
            final List<String> set = List.copyOf(canonical);
            candidateSets.add(set);
            candidateCountDistribution.merge(set.size(), 1L, Math::addExact);
            for (String value : set) { inverted.computeIfAbsent(value, ignored -> new ArrayList<>()).add(index); }
        }
        final QualityResult candidateResult = CandidateAwareEvaluator.evaluate(candidate.name(), candidate.language().name(),
                mode, OutputPolicy.ALL_CANDIDATES, groups, candidate.createStemmer());
        final List<Integer> selected = new ArrayList<>();
        for (int index = 0; index < forms.size(); index++) { if (candidateSets.get(index).size() > 1) { selected.add(index); } }
        selected.sort(Comparator.<Integer>comparingInt(index -> candidateSets.get(index).size()).reversed()
                .thenComparing(index -> forms.get(index)).thenComparingInt(index -> rows.get(index)));
        final List<Word> words = new ArrayList<>();
        for (int index : selected.subList(0, Math.min(limit, selected.size()))) {
            final Set<Integer> partners = new HashSet<>();
            for (String value : candidateSets.get(index)) { partners.addAll(inverted.get(value)); }
            partners.remove(index);
            long repaired = 0; long introduced = 0;
            for (int partner : partners) {
                final boolean primaryRelated = primaryOutputs[index].equals(primaryOutputs[partner]);
                if (groupIndexes.get(index).equals(groupIndexes.get(partner)) && !primaryRelated) { repaired++; }
                if (!groupIndexes.get(index).equals(groupIndexes.get(partner)) && !primaryRelated) { introduced++; }
            }
            words.add(new Word(rows.get(index), forms.get(index), primaryOutputs[index], candidateSets.get(index),
                    repaired, introduced));
        }
        return new Scenario(primary, any, candidateResult, Map.copyOf(candidateCountDistribution), List.copyOf(words));
    }

    /** Appends candidate diagnostics to the freshly generated audit report. */
    static void append(final Path path, final List<Scenario> scenarios) throws IOException {
        if (scenarios.isEmpty()) { return; }
        final StringBuilder text = new StringBuilder(4096);
        text.append("\n# Candidate-aware audit\n\nWord-level sections below retain original Unicode forms. Per-word repaired and introduced counts describe relations involving that word and are diagnostic, not additive scenario totals.\n\n");
        for (Scenario scenario : scenarios.stream().sorted(Comparator.comparing(Scenario::candidate, QualityResult.ORDER)).toList()) {
            final QualityResult primary = scenario.primary();
            final QualityResult any = scenario.any();
            final QualityResult candidate = scenario.candidate();
            text.append("## ").append(candidate.stemmer()).append(" / ").append(candidate.language()).append(" / ")
                    .append(candidate.processingMode()).append(" / ALL_CANDIDATES\n\n")
                    .append("- Primary under-stemming pairs: ").append(primary.underErrorPairs()).append(" / ").append(primary.underPossiblePairs()).append("\n")
                    .append("- ANY_CANDIDATE under-stemming pairs: ").append(any.underErrorPairs()).append(" / ").append(any.underPossiblePairs()).append("\n")
                    .append("- ALL_CANDIDATES under-stemming pairs: ").append(candidate.underErrorPairs()).append(" / ").append(candidate.underPossiblePairs()).append("\n")
                    .append("- Under-stemming pairs repaired by alternatives: ").append(primary.underErrorPairs() - candidate.underErrorPairs()).append("\n")
                    .append("- Primary over-stemming pairs: ").append(primary.overErrorPairs()).append(" / ").append(primary.overPossiblePairs()).append("\n")
                    .append("- ANY_CANDIDATE over-stemming pairs: ").append(any.overErrorPairs()).append(" / ").append(any.overPossiblePairs()).append("\n")
                    .append("- Best-case over-stemming pairs avoided: ").append(primary.overErrorPairs() - any.overErrorPairs()).append("\n")
                    .append("- ALL_CANDIDATES over-stemming pairs: ").append(candidate.overErrorPairs()).append(" / ").append(candidate.overPossiblePairs()).append("\n")
                    .append("- Additional candidate collision pairs: ").append(candidate.overErrorPairs() - primary.overErrorPairs()).append("\n")
                    .append("- Forms with multiple candidates: ").append(candidate.formsWithMultipleCandidates()).append("\n")
                    .append("- Maximum candidates for one word: ").append(candidate.maximumCandidatesForOneWord()).append("\n\n")
                    .append("- Candidate-count distribution: ").append(new java.util.TreeMap<>(scenario.candidateCountDistribution())).append("\n\n")
                    .append("### Forms with the largest candidate sets\n\n");
            for (Word word : scenario.words()) {
                text.append("- Row ").append(word.row()).append(", form `").append(escape(word.form()))
                        .append("`, primary `").append(escape(word.primary())).append("`, candidates ")
                        .append(word.candidates().stream().map(value -> "`" + escape(value) + "`").toList())
                        .append(", repaired same-group relations ").append(word.repairedUnderRelations())
                        .append(", introduced cross-group relations ").append(word.introducedOverRelations()).append(".\n");
            }
            text.append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    /** Escapes Markdown code-span delimiters without altering linguistic content. */
    private static String escape(final String value) { return value.replace("`", "\\`"); }

    /** Immutable candidate-aware audit scenario. */
    record Scenario(QualityResult primary, QualityResult any, QualityResult candidate,
            Map<Integer, Long> candidateCountDistribution,
            List<Word> words) { }

    /** Immutable word-level candidate diagnostic. */
    record Word(int row, String form, String primary, List<String> candidates,
            long repairedUnderRelations, long introducedOverRelations) { }
}
