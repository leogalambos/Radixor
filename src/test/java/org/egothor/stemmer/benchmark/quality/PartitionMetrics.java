package org.egothor.stemmer.benchmark.quality;

/**
 * Immutable strict-partition comparison metrics. Values use the arithmetic-mean
 * normalization for normalized mutual information and are applicable only to primary output.
 */
record PartitionMetrics(double adjustedRandIndex, double homogeneity, double completeness,
        double vMeasure, double normalizedMutualInformation) { }
