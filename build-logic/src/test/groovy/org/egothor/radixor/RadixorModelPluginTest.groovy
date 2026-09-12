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

package org.egothor.radixor

import org.gradle.api.GradleException
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile

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

    /** Preserves legacy compatibility while forbidding sentinels in manifest-managed models. */
    @Test
    void rejectsUnknownRevisionForManifestManagedModel() {
        assertThrows(GradleException) {
            RadixorModelPlugin.validateManifestRevisionMetadata(
                    'not-recorded-in-legacy-import', 'not-recorded-in-legacy-import', true)
        }
        RadixorModelPlugin.validateManifestRevisionMetadata(
                'not-recorded-in-legacy-import', 'not-recorded-in-legacy-import', false)
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

    /** Packages CC BY-SA 4.0 as ShareAlike notice material and describes that notice in the POM. */
    @Test
    void packagesCcBySaFourNoticeAndDescribesItInPom() {
        final Path project = temporaryDirectory.resolve('cc4-model')
        final Path modelInput = project.resolve('src/modelInput')
        Files.createDirectories(modelInput)
        Files.writeString(project.resolve('settings.gradle'), "rootProject.name = 'hsi-default'\n",
                StandardCharsets.UTF_8)
        Files.writeString(project.resolve('model-version.txt'), '1.0.0\n', StandardCharsets.UTF_8)
        writeGzip(modelInput.resolve('stemmer.gz').toFile()) { BufferedWriter writer ->
            writer.write('root\trooted\n')
        }
        Files.writeString(modelInput.resolve('NOTICE-model-data.txt'), validVersionFourNotice(),
                StandardCharsets.UTF_8)
        Files.writeString(project.resolve('build.gradle'), '''plugins {
    id 'org.egothor.radixor.model'
}
radixorModel {
    modelId = 'hsi-default'
    language = 'HSI'
    displayName = 'Hsilimo — UniMorph'
    defaultModel = true
    rightToLeft = true
    sourceName = 'UniMorph'
    sourceVersion = 'revision'
    sourceRevision = 'revision'
    sourceProject = 'UniMorph'
    sourceRepository = 'https://github.com/unimorph/hsi'
    sourceDataset = 'UniMorph Hsilimo morphological dataset'
    sourceRevisionStatus = 'recorded'
    sourceLicense = 'CC-BY-SA-4.0'
    sourceLicenseUri = 'https://creativecommons.org/licenses/by-sa/4.0/'
    sourceAttribution = 'UniMorph contributors'
    sourceVerificationDate = '2026-09-10'
    transformationsSummary = 'Cleaning and deterministic model packaging'
    noticeFileName = 'NOTICE-model-data.txt'
}
''', StandardCharsets.UTF_8)

        final BuildResult result = GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments('verifyModelJar', 'generatePomFileForModelPublication', '--stacktrace')
                .build()

        assertTrue(result.output.contains('BUILD SUCCESSFUL'))
        final Path archive = project.resolve('build/libs/radixor-model-hsi-default-1.0.0.jar')
        new ZipFile(archive.toFile()).withCloseable { ZipFile zip ->
            assertTrue(zip.getEntry('META-INF/NOTICE/hsi-default-data.txt') != null)
            assertTrue(zip.getEntry('META-INF/LICENSES/PoliMorf-BSD-2-Clause.txt') == null)
        }
        final String pom = Files.readString(
                project.resolve('build/publications/model/pom-default.xml'), StandardCharsets.UTF_8)
        assertTrue(pom.contains('See the packaged model-specific notice.'))
        assertTrue(!pom.contains('See the packaged model-data license.'))
        final String descriptor = Files.readString(project.resolve(
                'build/generated/modelResources/META-INF/radixor/models/hsi-default.properties'),
                StandardCharsets.UTF_8)
        assertTrue(descriptor.contains('model.rightToLeft=true\n'))
    }

    /** Packages the canonical LGPLLR text and legible dictionary form in the Khaling source artifact. */
    @Test
    void packagesLgpllrLicenseAndLegibleDictionarySource() {
        final Path project = temporaryDirectory.resolve('lgpllr-model')
        final Path modelInput = project.resolve('src/modelInput')
        Files.createDirectories(modelInput)
        Files.writeString(project.resolve('settings.gradle'), "rootProject.name = 'klr-default'\n",
                StandardCharsets.UTF_8)
        Files.writeString(project.resolve('model-version.txt'), '1.0.0\n', StandardCharsets.UTF_8)
        writeGzip(modelInput.resolve('stemmer.gz').toFile()) { BufferedWriter writer ->
            writer.write('root\trooted\n')
        }
        Files.writeString(modelInput.resolve('NOTICE-model-data.txt'), validLgpllrNotice(),
                StandardCharsets.UTF_8)
        final List<Path> licenseCandidates = [
                Path.of('models/klr-default/src/modelInput/LGPLLR.txt'),
                Path.of('../models/klr-default/src/modelInput/LGPLLR.txt')]
        final Path license = licenseCandidates.find { Path candidate -> Files.isRegularFile(candidate) }
        assertTrue(license != null, 'The canonical LGPLLR license text must be checked in.')
        Files.copy(license, modelInput.resolve('LGPLLR.txt'))
        Files.writeString(project.resolve('build.gradle'), '''plugins {
    id 'org.egothor.radixor.model'
}
radixorModel {
    modelId = 'klr-default'
    language = 'KLR'
    displayName = 'Khaling — UniMorph'
    defaultModel = true
    sourceName = 'UniMorph'
    sourceVersion = 'revision'
    sourceRevision = 'revision'
    sourceProject = 'UniMorph'
    sourceRepository = 'https://github.com/unimorph/klr'
    sourceDataset = 'UniMorph Khaling morphological dataset'
    sourceRevisionStatus = 'recorded'
    sourceLicense = 'LGPLLR'
    sourceLicenseUri = 'https://spdx.org/licenses/LGPLLR.html'
    sourceAttribution = 'UniMorph contributors'
    sourceVerificationDate = '2026-09-11'
    transformationsSummary = 'Cleaning and deterministic model packaging'
    noticeFileName = 'NOTICE-model-data.txt'
    licenseFileName = 'LGPLLR.txt'
}
''', StandardCharsets.UTF_8)

        final BuildResult result = GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments('verifyModelJar', '--stacktrace')
                .build()

        assertTrue(result.output.contains('BUILD SUCCESSFUL'))
        final Path archive = project.resolve('build/libs/radixor-model-klr-default-1.0.0.jar')
        new ZipFile(archive.toFile()).withCloseable { ZipFile zip ->
            assertTrue(zip.getEntry('META-INF/NOTICE/klr-default-data.txt') != null)
            assertTrue(zip.getEntry('META-INF/LICENSES/LGPLLR.txt') != null)
        }
        final Path sources = project.resolve('build/libs/klr-default-1.0.0-sources.jar')
        new ZipFile(sources.toFile()).withCloseable { ZipFile zip ->
            assertTrue(zip.getEntry('org/egothor/stemmer/models/klr-default/stemmer.gz') != null)
            assertTrue(zip.getEntry('META-INF/LICENSES/LGPLLR.txt') != null)
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
        assertEquals(0L, result.ignoredEmptyVariantCount)
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

    /** Trims a physical row before splitting it exactly as the production parser does. */
    @Test
    void trimsRowsBeforeSplittingFields() {
        final File dictionary = temporaryDirectory.resolve('invalid-row.gz').toFile()
        writeGzip(dictionary) { BufferedWriter writer -> writer.write("\tvariant\nvalid\tvariant\n") }
        final RadixorModelPlugin.DictionaryValidationResult result =
                RadixorModelPlugin.validateDictionary(dictionary)
        assertEquals(2L, result.acceptedGroupCount)
        assertEquals(3L, result.acceptedFormCount)
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

    /** Mirrors production parsing for trimming, comments, NBSP, marks, joiners, and punctuation. */
    @Test
    void matchesProductionDictionaryFieldGrammar() {
        final File dictionary = temporaryDirectory.resolve('parser-conformance.gz').toFile()
        writeGzip(dictionary) { BufferedWriter writer ->
            writer.write("  root  \t  variant  \n")
            writer.write("bad stem\tvariant\n")
            writer.write("nbsp\u00a0stem\taccepted\n")
            writer.write("mark\u0301\tzwnj\u200c\tzwj\u200d\to'neil\tco-op\ta:b\ta/b\n")
            writer.write("comment\tkept#discarded\n")
            writer.write("slash\tkept//discarded\n")
        }

        final RadixorModelPlugin.DictionaryValidationResult result =
                RadixorModelPlugin.validateDictionary(dictionary)

        assertEquals(5L, result.acceptedGroupCount)
        assertEquals(15L, result.acceptedFormCount)
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
                'https://github.com/unimorph/test', 'CC-BY-SA-3.0',
                'https://creativecommons.org/licenses/by-sa/3.0/',
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

    private static String validVersionFourNotice() {
        return '''Model ID: hsi-default
Official repository: https://github.com/unimorph/hsi
Attribution: UniMorph contributors
License:
Creative Commons Attribution-ShareAlike 4.0 International
Canonical license URI: https://creativecommons.org/licenses/by-sa/4.0/
Radixor modifications: Cleaning and deterministic model packaging.
Revision status: recorded
Copyright (C) 2026, Leo Galambos.
Radixor-specific selection, verification, cleaning, normalization,
to the extent protected by applicable law.
The underlying morphological data remains attributed to UniMorph and
This derived model data, including Radixor's protectable contributions,
is distributed under Creative Commons Attribution-ShareAlike 4.0
Neither UniMorph nor any upstream contributor endorses Radixor.
'''
    }

    private static String validLgpllrNotice() {
        return '''Model ID: klr-default
Official repository: https://github.com/unimorph/klr
Attribution: UniMorph contributors
License:
Lesser General Public License For Linguistic Resources
Canonical license URI: https://spdx.org/licenses/LGPLLR.html
Radixor modifications: Cleaning and deterministic model packaging.
Revision status: recorded
Copyright (C) 2026, Leo Galambos.
Radixor-specific selection, verification, cleaning, normalization,
to the extent protected by applicable law.
The underlying morphological data remains attributed to UniMorph and
This derived model data, including Radixor's protectable contributions,
is distributed under the Lesser General Public License For Linguistic Resources
Neither UniMorph nor any upstream contributor endorses Radixor.
'''
    }
}
