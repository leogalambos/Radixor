package org.egothor.radixor

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

/** Tests model licensing metadata and packaged-resource validation boundaries. */
final class RadixorModelPluginTest {
    @TempDir
    Path temporaryDirectory

    /** Accepts a known exact source revision. */
    @Test
    void acceptsKnownExactRevision() {
        RadixorModelPlugin.validateRevisionMetadata('6e63b53', 'recorded')
    }

    /** Accepts the explicit legacy-import sentinel without fabricating a revision. */
    @Test
    void acceptsUnknownLegacyRevision() {
        RadixorModelPlugin.validateRevisionMetadata(
                'not-recorded-in-legacy-import', 'not-recorded-in-legacy-import')
    }

    /** Rejects a missing revision-status declaration. */
    @Test
    void rejectsMissingRevisionStatus() {
        assertThrows(GradleException) {
            RadixorModelPlugin.validateRevisionMetadata('6e63b53', '')
        }
    }

    /** Rejects a missing model-specific notice input. */
    @Test
    void rejectsMissingLicensingInputs() {
        File missing = new File('build/nonexistent-model-licensing-input')
        assertThrows(GradleException) {
            RadixorModelPlugin.requireFile(missing, 'Required model notice is missing')
        }
    }

    /** Accepts a complete model-specific UniMorph notice. */
    @Test
    void acceptsCompleteUniMorphNotice() {
        validateNotice(validNotice())
    }

    /** Rejects each independently required notice statement. */
    @Test
    void rejectsIncompleteUniMorphNotices() {
        [
                'Copyright (C) 2026, Leo Galambos.',
                'Attribution:',
                'Creative Commons Attribution-ShareAlike 3.0 Unported',
                'Canonical license URI:',
                "This derived model data, including Radixor's protectable contributions,",
                'Radixor modifications:',
                'Revision status:',
                'Neither UniMorph nor any upstream contributor endorses Radixor.'
        ].each { String required ->
            assertThrows(GradleException) {
                validateNotice(validNotice().replace(required, 'omitted'))
            }
        }
    }

    /** Rejects packaged notice bytes that differ from their model-module source. */
    @Test
    void rejectsIncorrectPackagedNotice() {
        assertThrows(GradleException) {
            RadixorModelPlugin.requireMatchingChecksum(
                    'notice', 'META-INF/NOTICE/test-model-data.txt', 'source', 'different')
        }
    }

    /** Rejects UniMorph CC material in the separately licensed PoliMorf artifact. */
    @Test
    void rejectsUniMorphMaterialInPoliMorf() {
        assertThrows(GradleException) {
            RadixorModelPlugin.validatePoliMorfJarContents(
                    ['META-INF/LICENSES/PoliMorf-BSD-2-Clause.txt', 'META-INF/NOTICE/test-data.txt'])
        }
        assertThrows(GradleException) {
            RadixorModelPlugin.validatePoliMorfJarContents(
                    ['META-INF/LICENSES/PoliMorf-BSD-2-Clause.txt', 'META-INF/LICENSES/CC-BY-SA-3.0.txt'])
        }
    }

    /** Streams a large dictionary while retaining only aggregate counters and the current row. */
    @Test
    void validatesLargeDictionaryWithBoundedState() {
        final int groups = 250_000
        final File dictionary = temporaryDirectory.resolve('large.gz').toFile()
        writeGzip(dictionary) { BufferedWriter writer ->
            for (int index = 0; index < groups; index++) {
                writer.write("stem${index}\tvariant${index}\t\n")
            }
        }

        final RadixorModelPlugin.DictionaryValidationResult result =
                RadixorModelPlugin.validateDictionary(dictionary)

        assertEquals(groups, result.acceptedGroupCount)
        assertEquals(groups * 2L, result.acceptedFormCount)
        assertEquals(groups, result.ignoredEmptyVariantCount)
    }

    /** Rejects a source that is not a GZip stream. */
    @Test
    void rejectsInvalidGzip() {
        final File dictionary = temporaryDirectory.resolve('invalid.gz').toFile()
        Files.writeString(dictionary.toPath(), 'not gzip', StandardCharsets.UTF_8)
        assertThrows(GradleException) { RadixorModelPlugin.validateDictionary(dictionary) }
    }

    /** Rejects malformed UTF-8 through the strict incremental decoder. */
    @Test
    void rejectsMalformedUtf8() {
        final File dictionary = temporaryDirectory.resolve('malformed-utf8.gz').toFile()
        new GZIPOutputStream(Files.newOutputStream(dictionary.toPath())).withCloseable { OutputStream output ->
            output.write([0x73, 0x74, 0x65, 0x6d, 0x09, 0xc3, 0x28, 0x0a] as byte[])
        }
        assertThrows(GradleException) { RadixorModelPlugin.validateDictionary(dictionary) }
    }

    /** Rejects structurally invalid rows with an empty stem. */
    @Test
    void rejectsInvalidRows() {
        final File dictionary = temporaryDirectory.resolve('invalid-row.gz').toFile()
        writeGzip(dictionary) { BufferedWriter writer -> writer.write("\tvariant\n") }
        assertThrows(GradleException) { RadixorModelPlugin.validateDictionary(dictionary) }
    }

    /** Preserves the production parser policy for Unicode-whitespace items. */
    @Test
    void rejectsUnicodeWhitespaceItemsWithoutRejectingTheSource() {
        final File dictionary = temporaryDirectory.resolve('whitespace-items.gz').toFile()
        writeGzip(dictionary) { BufferedWriter writer ->
            writer.write("invalid stem\tvariant\n")
            writer.write("valid\taccepted\tinvalid variant\n")
        }
        final RadixorModelPlugin.DictionaryValidationResult result =
                RadixorModelPlugin.validateDictionary(dictionary)
        assertEquals(1L, result.acceptedGroupCount)
        assertEquals(2L, result.acceptedFormCount)
    }

    /** Streams the complete maintained PoliMorf model input successfully. */
    @Test
    void validatesFullPoliMorfInput() {
        final List<File> candidates = [
                new File('models/pl-pl-polimorf/src/modelInput/stemmer.gz'),
                new File('../models/pl-pl-polimorf/src/modelInput/stemmer.gz')]
        final File dictionary = candidates.find { File candidate -> candidate.isFile() }
        assertTrue(dictionary != null, 'The complete PoliMorf model input must be available to build-logic tests.')

        final RadixorModelPlugin.DictionaryValidationResult result =
                RadixorModelPlugin.validateDictionary(dictionary)
        assertTrue(result.acceptedGroupCount > 0L)
        assertTrue(result.acceptedFormCount > result.acceptedGroupCount)
    }

    private static void writeGzip(final File target, final Closure<Void> content) {
        new GZIPOutputStream(Files.newOutputStream(target.toPath())).withCloseable { OutputStream gzip ->
            new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8)).withCloseable {
                BufferedWriter writer -> content.call(writer)
            }
        }
    }

    private static void validateNotice(final String text) {
        RadixorModelPlugin.validateShareAlikeNoticeText(text, 'test notice', 'test-model',
                'https://github.com/unimorph/test', 'https://creativecommons.org/licenses/by-sa/3.0/',
                'not-recorded-in-legacy-import', 'not-recorded-in-legacy-import')
    }

    private static String validNotice() {
        return '''Model ID: test-model
Official repository: https://github.com/unimorph/test
Attribution: UniMorph and upstream contributors
License:
Creative Commons Attribution-ShareAlike 3.0 Unported
Canonical license URI: https://creativecommons.org/licenses/by-sa/3.0/
Radixor modifications: Cleaning and packaging.
Revision status: not-recorded-in-legacy-import
The exact UniMorph commit used for the original Radixor import was not recorded.
Copyright (C) 2026, Leo Galambos.
Radixor-specific selection, verification, cleaning, normalization,
to the extent protected by applicable law.
The underlying morphological data remains attributed to UniMorph and
This derived model data, including Radixor's protectable contributions,
is distributed under Creative Commons Attribution-ShareAlike 3.0
Neither UniMorph nor any upstream contributor endorses Radixor.
'''
    }
}
