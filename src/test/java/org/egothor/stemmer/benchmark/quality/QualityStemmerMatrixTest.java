package org.egothor.stemmer.benchmark.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.egothor.stemmer.benchmark.QualityStemmerMatrix;
import org.egothor.stemmer.benchmark.QualityStemmerMatrix.Candidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Integration checks binding report coverage to the authoritative JMH candidate registry. */
@Tag("integration")
@DisplayName("JMH stemming-quality candidate matrix")
final class QualityStemmerMatrixTest {
    /** Temporary report location. */
    @TempDir Path temporaryDirectory;

    /** Verifies discovery includes the complete current benchmark enum rather than Radixor alone. */
    @Test @DisplayName("Candidate discovery is derived from every JMH quality candidate")
    void discoversEveryCandidate() {
        final List<Candidate> candidates = QualityStemmerMatrix.candidates();
        assertEquals(92, candidates.size(), "The current adapter-language matrix size changed; report coverage must be reviewed.");
        assertTrue(candidates.stream().anyMatch(candidate -> !candidate.name().endsWith("_RADIXOR")));
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.name().equals("DA_DK_RADIXOR")));
        assertTrue(candidates.stream().anyMatch(candidate -> candidate.name().equals("YI_RADIXOR")));
    }

    /** Verifies a complete report row exists for both modes of every discovered candidate. */
    @Test @DisplayName("Report rendering includes both modes for every discovered candidate")
    void reportContainsCompleteMatrix() throws Exception {
        final List<QualityResult> rows = new ArrayList<>();
        for (Candidate candidate : QualityStemmerMatrix.candidates()) {
            for (ProcessingMode mode : ProcessingMode.values()) {
                rows.add(new QualityResult(candidate.name(), candidate.language().name(), mode,
                        OutputPolicy.PRIMARY_OUTPUT, 1, 1, 1, 0, 1, 0, 1, 1, 1, 0, 0, 0, 0,
                        new PartitionMetrics(1.0, 1.0, 1.0, 1.0, 1.0)));
            }
        }
        final Path report = this.temporaryDirectory.resolve("matrix.csv");
        QualityReportWriter.writeCsv(report, rows);
        final String text = Files.readString(report, StandardCharsets.UTF_8);
        assertEquals(185, text.lines().count());
        for (Candidate candidate : QualityStemmerMatrix.candidates()) {
            assertTrue(text.contains("\"" + candidate.name() + "\",\"" + candidate.language() + "\",\"ALL_WORDS\""));
            assertTrue(text.contains("\"" + candidate.name() + "\",\"" + candidate.language() + "\",\"LOWERCASE_GROUPS_ONLY\""));
        }
    }
}
