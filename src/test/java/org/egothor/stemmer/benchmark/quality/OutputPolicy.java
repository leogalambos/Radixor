package org.egothor.stemmer.benchmark.quality;

/** Defines which outputs of a JMH stemmer adapter establish the measured relation. */
enum OutputPolicy {
    /** Uses only the deterministic output selected by the existing JMH comparison. */
    PRIMARY_OUTPUT,
    /** Uses an optimistic pair-specific choice from the complete candidate sets. */
    ANY_CANDIDATE,
    /** Treats all candidates as active and uses the complete intersection relation. */
    ALL_CANDIDATES
}
