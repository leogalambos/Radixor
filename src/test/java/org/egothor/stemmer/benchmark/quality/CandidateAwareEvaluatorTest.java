package org.egothor.stemmer.benchmark.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.egothor.stemmer.benchmark.QualityStemmerMatrix.BatchStemmer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Exact candidate-relation tests, including an independent quadratic oracle. */
@Tag("unit")
@DisplayName("Candidate-aware pairwise stemming quality")
final class CandidateAwareEvaluatorTest {
    /** Verifies intersections repair under-stemming while several shared candidates count once. */
    @Test @DisplayName("Candidate intersections repair primary under-stemming and count each pair once")
    void intersectionsRepairUnderStemming() throws IOException {
        final List<GoldStandardGroup> groups = List.of(new GoldStandardGroup(1, List.of("a", "b", "c")));
        final Map<String, String> primary = Map.of("a", "y", "b", "x", "c", "z");
        final Map<String, List<String>> candidates = Map.of("a", List.of("y", "x", "x"),
                "b", List.of("x", "shared"), "c", List.of("z", "x", "shared"));
        final QualityResult primaryResult = QualityEvaluator.evaluateBatch("Synthetic", "MULTI",
                ProcessingMode.ALL_WORDS, groups, adapter(primary, candidates));
        final QualityResult candidateResult = CandidateAwareEvaluator.evaluate("Synthetic", "MULTI",
                ProcessingMode.ALL_WORDS, OutputPolicy.ALL_CANDIDATES, groups, adapter(primary, candidates));
        assertEquals(3, primaryResult.underErrorPairs());
        assertEquals(0, candidateResult.underErrorPairs());
        assertEquals(7, candidateResult.totalCandidateAssignments(), "Duplicate candidates must be removed per word.");
        assertTrue(candidateResult.underErrorPairs() <= primaryResult.underErrorPairs());
    }

    /** Verifies exact within-row disconnections and cross-row candidate collisions. */
    @Test @DisplayName("Disjoint sets and cross-group intersections produce exact candidate-aware counts")
    void disjointAndCollidingSets() throws IOException {
        final List<GoldStandardGroup> groups = List.of(
                new GoldStandardGroup(1, List.of("a", "b")), new GoldStandardGroup(2, List.of("c", "d")));
        final Map<String, String> primary = Map.of("a", "a", "b", "b", "c", "c", "d", "d");
        final Map<String, List<String>> candidates = Map.of("a", List.of("a", "collision"), "b", List.of("b"),
                "c", List.of("c", "collision", "other"), "d", List.of("d", "other"));
        final QualityResult result = CandidateAwareEvaluator.evaluate("Synthetic", "MULTI",
                ProcessingMode.ALL_WORDS, OutputPolicy.ALL_CANDIDATES, groups, adapter(primary, candidates));
        assertEquals(1, result.underErrorPairs());
        assertEquals(2, result.underPossiblePairs());
        assertEquals(1, result.overErrorPairs(), "Only the cross-group a-c pair shares a candidate.");
        assertEquals(4, result.overPossiblePairs());
    }

    /** Verifies optimistic and all-active cross-group semantics for canonical examples. */
    @Test @DisplayName("ANY_CANDIDATE and ALL_CANDIDATES apply their distinct over-stemming relations")
    void policySpecificOverStemming() throws IOException {
        assertPolicyOver(List.of("x"), List.of("x"), 1, 1);
        assertPolicyOver(List.of("x"), List.of("y"), 0, 0);
        assertPolicyOver(List.of("x"), List.of("x", "y"), 0, 1);
        assertPolicyOver(List.of("x", "y"), List.of("x", "y"), 0, 1);
        assertPolicyOver(List.of("x", "y"), List.of("x", "z"), 0, 1);
    }

    /** Verifies both candidate policies have identical same-group under-stemming. */
    @Test @DisplayName("Candidate policies share the exact same within-group intersection rule")
    void candidatePoliciesShareUnderStemming() throws IOException {
        final List<GoldStandardGroup> groups = List.of(new GoldStandardGroup(1, List.of("a", "b", "c")));
        final Map<String, String> primary = Map.of("a", "x", "b", "y", "c", "z");
        final Map<String, List<String>> candidates = Map.of("a", List.of("x", "shared"),
                "b", List.of("y", "shared"), "c", List.of("z"));
        final QualityResult any = CandidateAwareEvaluator.evaluate("Synthetic", "MULTI", ProcessingMode.ALL_WORDS,
                OutputPolicy.ANY_CANDIDATE, groups, adapter(primary, candidates));
        final QualityResult all = CandidateAwareEvaluator.evaluate("Synthetic", "MULTI", ProcessingMode.ALL_WORDS,
                OutputPolicy.ALL_CANDIDATES, groups, adapter(primary, candidates));
        assertEquals(2, any.underErrorPairs()); assertEquals(any.underErrorPairs(), all.underErrorPairs());
    }

    /** Compares the optimized signature algorithm with an independent fixed-seed oracle. */
    @Test @DisplayName("Optimized candidate metrics equal a deterministic randomized brute-force oracle")
    void randomizedOracleAgreement() throws IOException {
        final Random random = new Random(0x5EEDC0DEL);
        for (int trial = 0; trial < 150; trial++) {
            final int groupCount = 1 + random.nextInt(5);
            final List<GoldStandardGroup> groups = new ArrayList<>();
            final Map<String, String> primary = new HashMap<>();
            final Map<String, List<String>> candidates = new HashMap<>();
            int word = 0;
            for (int group = 0; group < groupCount; group++) {
                final List<String> forms = new ArrayList<>();
                for (int member = 0; member < 1 + random.nextInt(5); member++) {
                    final String form = "w" + word++;
                    forms.add(form);
                    final String primaryStem = "s" + random.nextInt(7);
                    primary.put(form, primaryStem);
                    final List<String> raw = new ArrayList<>();
                    raw.add(primaryStem);
                    for (int candidate = 0; candidate < random.nextInt(4); candidate++) {
                        raw.add("s" + random.nextInt(7));
                    }
                    candidates.put(form, raw);
                }
                groups.add(new GoldStandardGroup(group + 1, forms));
            }
            final QualityResult optimized = CandidateAwareEvaluator.evaluate("Random", "MULTI",
                    ProcessingMode.ALL_WORDS, OutputPolicy.ALL_CANDIDATES, groups, adapter(primary, candidates));
            final QualityResult any = CandidateAwareEvaluator.evaluate("Random", "MULTI",
                    ProcessingMode.ALL_WORDS, OutputPolicy.ANY_CANDIDATE, groups, adapter(primary, candidates));
            final QualityResult primaryResult = QualityEvaluator.evaluateBatch("Random", "MULTI",
                    ProcessingMode.ALL_WORDS, groups, adapter(primary, candidates));
            final long[] oracle = oracle(groups, candidates);
            assertEquals(oracle[0], optimized.underErrorPairs(), "Under errors differ in trial " + trial);
            assertEquals(oracle[1], optimized.underPossiblePairs(), "Under denominator differs in trial " + trial);
            assertEquals(oracle[2], optimized.overErrorPairs(), "Over errors differ in trial " + trial);
            assertEquals(oracle[3], optimized.overPossiblePairs(), "Over denominator differs in trial " + trial);
            assertEquals(oracle[4], any.overErrorPairs(), "Optimistic over errors differ in trial " + trial);
            assertEquals(any.underErrorPairs(), optimized.underErrorPairs());
            assertTrue(any.underErrorPairs() <= primaryResult.underErrorPairs());
            assertTrue(any.overErrorPairs() <= primaryResult.overErrorPairs());
            assertTrue(optimized.overErrorPairs() >= primaryResult.overErrorPairs());
        }
    }

    /** Verifies candidate contract violations fail with scenario and word context. */
    @Test @DisplayName("Invalid candidate collections fail with precise contextual diagnostics")
    void invalidCandidateOutput() {
        final List<GoldStandardGroup> groups = List.of(new GoldStandardGroup(7, List.of("žluťoučký")));
        final BatchStemmer invalid = adapter(Map.of("žluťoučký", "stem"), Map.of("žluťoučký", List.of("other")));
        final IOException exception = assertThrows(IOException.class, () -> CandidateAwareEvaluator.evaluate(
                "Invalid", "CS_CZ", ProcessingMode.ALL_WORDS, OutputPolicy.ALL_CANDIDATES, groups, invalid));
        assertTrue(exception.getMessage().contains("row 7"));
        assertTrue(exception.getMessage().contains("žluťoučký"));
        assertTrue(exception.getMessage().contains("omits primary output"));
    }

    /** Creates a deterministic multi-output adapter from per-form fixtures. */
    private static BatchStemmer adapter(final Map<String, String> primary,
            final Map<String, List<String>> candidates) {
        return new BatchStemmer() {
            /** {@inheritDoc} */
            @Override public String[] stem(final String[] forms) {
                final String[] outputs = new String[forms.length];
                for (int index = 0; index < forms.length; index++) { outputs[index] = primary.get(forms[index]); }
                return outputs;
            }
            /** {@inheritDoc} */
            @Override public List<List<String>> stemCandidates(final String[] forms) {
                final List<List<String>> outputs = new ArrayList<>();
                for (String form : forms) { outputs.add(candidates.get(form)); }
                return outputs;
            }
            /** {@inheritDoc} */
            @Override public boolean supportsMultipleOutputs() { return true; }
        };
    }

    /** Evaluates one two-row example and checks both policy numerators. */
    private static void assertPolicyOver(final List<String> left, final List<String> right,
            final long expectedAny, final long expectedAll) throws IOException {
        final List<GoldStandardGroup> groups = List.of(new GoldStandardGroup(1, List.of("a")),
                new GoldStandardGroup(2, List.of("b")));
        final Map<String, String> primary = Map.of("a", left.get(0), "b", right.get(0));
        final Map<String, List<String>> candidates = Map.of("a", left, "b", right);
        final QualityResult any = CandidateAwareEvaluator.evaluate("Synthetic", "MULTI", ProcessingMode.ALL_WORDS,
                OutputPolicy.ANY_CANDIDATE, groups, adapter(primary, candidates));
        final QualityResult all = CandidateAwareEvaluator.evaluate("Synthetic", "MULTI", ProcessingMode.ALL_WORDS,
                OutputPolicy.ALL_CANDIDATES, groups, adapter(primary, candidates));
        assertEquals(expectedAny, any.overErrorPairs()); assertEquals(expectedAll, all.overErrorPairs());
    }

    /** Enumerates small word pairs independently and returns under error/possible and over error/possible counts. */
    private static long[] oracle(final List<GoldStandardGroup> groups,
            final Map<String, List<String>> candidates) {
        final List<String> forms = new ArrayList<>();
        final List<Integer> labels = new ArrayList<>();
        for (int group = 0; group < groups.size(); group++) {
            for (String form : groups.get(group).forms()) { forms.add(form); labels.add(group); }
        }
        long underError = 0; long underPossible = 0; long overError = 0; long overPossible = 0; long anyOverError = 0;
        for (int left = 0; left < forms.size(); left++) {
            for (int right = left + 1; right < forms.size(); right++) {
                final Set<String> intersection = new LinkedHashSet<>(candidates.get(forms.get(left)));
                intersection.retainAll(new LinkedHashSet<>(candidates.get(forms.get(right))));
                if (labels.get(left).equals(labels.get(right))) {
                    underPossible++; if (intersection.isEmpty()) { underError++; }
                } else {
                    overPossible++; if (!intersection.isEmpty()) { overError++; }
                    final Set<String> leftSet = new LinkedHashSet<>(candidates.get(forms.get(left)));
                    final Set<String> rightSet = new LinkedHashSet<>(candidates.get(forms.get(right)));
                    if (leftSet.size() == 1 && leftSet.equals(rightSet)) { anyOverError++; }
                }
            }
        }
        return new long[] {underError, underPossible, overError, overPossible, anyOverError};
    }
}
