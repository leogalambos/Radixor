package org.egothor.stemmer.benchmark.quality;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.egothor.stemmer.benchmark.QualityStemmerMatrix.BatchStemmer;

/**
 * Calculates exact candidate-intersection pair metrics from canonical candidate-set signatures.
 * Candidate-aware output defines an overlap relation rather than a partition. The algorithm
 * aggregates signature frequencies and uses an inverted candidate index; it never enumerates
 * complete dictionary word pairs. All pair arithmetic is checked.
 */
final class CandidateAwareEvaluator {
    /** Utility class. */
    private CandidateAwareEvaluator() { throw new AssertionError("No instances."); }

    /** Evaluates one genuinely multi-output scenario through its authoritative JMH adapter. */
    static QualityResult evaluate(final String stemmerName, final String language, final ProcessingMode mode,
            final OutputPolicy policy, final List<GoldStandardGroup> groups, final BatchStemmer stemmer) throws IOException {
        if (policy == OutputPolicy.PRIMARY_OUTPUT) {
            throw new IllegalArgumentException("Candidate-aware evaluation requires ANY_CANDIDATE or ALL_CANDIDATES.");
        }
        final List<GoldStandardGroup> includedGroups = groups.stream().filter(group -> mode.includes(group.forms())).toList();
        final List<String> forms = new ArrayList<>();
        final List<Integer> groupIndexes = new ArrayList<>();
        long singletonRows = 0;
        long pairRows = 0;
        long underPossible = 0;
        for (int groupIndex = 0; groupIndex < includedGroups.size(); groupIndex++) {
            final GoldStandardGroup group = includedGroups.get(groupIndex);
            if (group.forms().size() == 1) { singletonRows = add(singletonRows, 1, "singleton rows"); }
            else { pairRows = add(pairRows, 1, "rows contributing under-stemming pairs"); }
            underPossible = add(underPossible, QualityEvaluator.chooseTwo(group.forms().size()), "under denominator");
            for (String form : group.forms()) { forms.add(form); groupIndexes.add(groupIndex); }
        }
        final String[] input = forms.toArray(String[]::new);
        final String[] primary = stemmer.stem(input);
        final List<List<String>> rawCandidates = stemmer.stemCandidates(input);
        if (primary == null || primary.length != input.length || rawCandidates == null || rawCandidates.size() != input.length) {
            throw failure(stemmerName, language, mode, policy, "the adapter returned an invalid output batch");
        }

        final Map<Signature, SignatureCount> counts = new HashMap<>();
        final Set<String> distinctCandidates = new HashSet<>();
        long oneCandidate = 0;
        long multipleCandidates = 0;
        long maximumCandidates = 0;
        long assignments = 0;
        for (int index = 0; index < input.length; index++) {
            final Signature signature = signature(rawCandidates.get(index), primary[index], stemmerName, language,
                    mode, policy, includedGroups.get(groupIndexes.get(index)).rowNumber(), input[index]);
            final int size = signature.candidates().size();
            if (size == 1) { oneCandidate = add(oneCandidate, 1, "single-candidate forms"); }
            else { multipleCandidates = add(multipleCandidates, 1, "multi-candidate forms"); }
            maximumCandidates = Math.max(maximumCandidates, size);
            assignments = add(assignments, size, "candidate assignments");
            distinctCandidates.addAll(signature.candidates());
            counts.computeIfAbsent(signature, ignored -> new SignatureCount()).increment(groupIndexes.get(index));
        }

        final List<Map.Entry<Signature, SignatureCount>> signatures = new ArrayList<>(counts.entrySet());
        signatures.sort(Map.Entry.comparingByKey());
        long sameGroupRelated = 0;
        long crossGroupRelated = 0;
        final Map<String, List<Integer>> inverted = new HashMap<>();
        for (int index = 0; index < signatures.size(); index++) {
            final Map.Entry<Signature, SignatureCount> entry = signatures.get(index);
            long sameWithin = 0;
            for (long groupCount : entry.getValue().byGroup().values()) {
                sameWithin = add(sameWithin, QualityEvaluator.chooseTwo(groupCount), "same-signature group pairs");
            }
            sameGroupRelated = add(sameGroupRelated, sameWithin, "same-group related pairs");
            if (policy == OutputPolicy.ALL_CANDIDATES || entry.getKey().candidates().size() == 1) {
                crossGroupRelated = add(crossGroupRelated,
                        subtract(QualityEvaluator.chooseTwo(entry.getValue().total()), sameWithin, "same-signature cross pairs"),
                        "cross-group related pairs");
            }
            for (String candidate : entry.getKey().candidates()) {
                inverted.computeIfAbsent(candidate, ignored -> new ArrayList<>()).add(index);
            }
        }
        final Set<SignaturePair> relatedSignaturePairs = new HashSet<>();
        for (List<Integer> indexes : inverted.values()) {
            for (int left = 0; left < indexes.size(); left++) {
                for (int right = left + 1; right < indexes.size(); right++) {
                    relatedSignaturePairs.add(new SignaturePair(indexes.get(left), indexes.get(right)));
                }
            }
        }
        for (SignaturePair pair : relatedSignaturePairs) {
            final SignatureCount left = signatures.get(pair.left()).getValue();
            final SignatureCount right = signatures.get(pair.right()).getValue();
            long same = 0;
            for (Map.Entry<Integer, Long> group : left.byGroup().entrySet()) {
                same = add(same, multiply(group.getValue(), right.byGroup().getOrDefault(group.getKey(), 0L),
                        "different-signature same-group pairs"), "same-group related pairs");
            }
            final long total = multiply(left.total(), right.total(), "different-signature pairs");
            sameGroupRelated = add(sameGroupRelated, same, "same-group related pairs");
            if (policy == OutputPolicy.ALL_CANDIDATES) {
                crossGroupRelated = add(crossGroupRelated, subtract(total, same, "different-signature cross pairs"),
                        "cross-group related pairs");
            }
        }
        final long wordCount = input.length;
        final long overPossible = subtract(QualityEvaluator.chooseTwo(wordCount), underPossible, "over denominator");
        final long underError = subtract(underPossible, sameGroupRelated, "candidate under errors");
        return new QualityResult(stemmerName, language, mode, policy,
                includedGroups.size(), wordCount, singletonRows, pairRows, oneCandidate, multipleCandidates,
                maximumCandidates, assignments, distinctCandidates.size(), crossGroupRelated, overPossible,
                underError, underPossible, null);
    }

    /** Canonicalizes and validates one adapter candidate collection. */
    private static Signature signature(final List<String> raw, final String primary, final String stemmer,
            final String language, final ProcessingMode mode, final OutputPolicy policy,
            final int row, final String form) throws IOException {
        if (primary == null) { throw failure(stemmer, language, mode, policy, "null primary output at row " + row + " for '" + form + "'"); }
        if (raw == null || raw.isEmpty()) { throw failure(stemmer, language, mode, policy, "null or empty candidate collection at row " + row + " for '" + form + "'"); }
        final TreeSet<String> candidates = new TreeSet<>();
        for (String candidate : raw) {
            if (candidate == null) { throw failure(stemmer, language, mode, policy, "null candidate at row " + row + " for '" + form + "'"); }
            candidates.add(candidate);
        }
        if (!candidates.contains(primary)) { throw failure(stemmer, language, mode, policy, "candidate set omits primary output '" + primary + "' at row " + row + " for '" + form + "'"); }
        return new Signature(List.copyOf(candidates));
    }

    /** Checked addition with diagnostic context. */
    private static long add(final long left, final long right, final String context) { try { return Math.addExact(left, right); } catch (ArithmeticException exception) { throw new IllegalStateException("Arithmetic overflow in " + context + ".", exception); } }
    /** Checked subtraction with diagnostic context. */
    private static long subtract(final long left, final long right, final String context) { try { return Math.subtractExact(left, right); } catch (ArithmeticException exception) { throw new IllegalStateException("Arithmetic overflow in " + context + ".", exception); } }
    /** Checked multiplication with diagnostic context. */
    private static long multiply(final long left, final long right, final String context) { try { return Math.multiplyExact(left, right); } catch (ArithmeticException exception) { throw new IllegalStateException("Arithmetic overflow in " + context + ".", exception); } }
    /** Creates one scenario-qualified adapter failure. */
    private static IOException failure(final String stemmer, final String language, final ProcessingMode mode,
            final OutputPolicy policy, final String reason) { return new IOException("Candidate-aware evaluation failed for stemmer " + stemmer + ", language " + language + ", dictionary mode " + mode + ", and output policy " + policy + ": " + reason + "."); }

    /** Deterministic immutable candidate-set signature. */
    private record Signature(List<String> candidates) implements Comparable<Signature> {
        /** Orders signatures lexicographically without depending on map iteration. */
        @Override public int compareTo(final Signature other) {
            final int common = Math.min(candidates.size(), other.candidates.size());
            for (int index = 0; index < common; index++) { final int compared = candidates.get(index).compareTo(other.candidates.get(index)); if (compared != 0) { return compared; } }
            return Integer.compare(candidates.size(), other.candidates.size());
        }
    }
    /** Aggregated global and per-group frequency of one signature. */
    private static final class SignatureCount {
        private long total;
        private final Map<Integer, Long> byGroup = new HashMap<>();
        /** Adds one word occurrence. */ private void increment(final int group) { total = add(total, 1, "signature frequency"); byGroup.merge(group, 1L, (left, right) -> add(left, right, "signature group frequency")); }
        /** @return global signature frequency */ private long total() { return total; }
        /** @return mutable internally owned per-group frequencies */ private Map<Integer, Long> byGroup() { return byGroup; }
    }
    /** Unordered pair of distinct canonical signature indexes. */
    private record SignaturePair(int left, int right) { }
}
