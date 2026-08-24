package org.egothor.radixor

import org.gradle.api.GradleException
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import static org.junit.jupiter.api.Assertions.assertArrayEquals
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

/** Exercises catalog publication filtering, isolation, checksums, and semantic verification. */
final class ModelCatalogBundleTaskTest {
    private static final String CATALOG_VERSION = '2026.1'
    private static final List<String> DEFAULTS = ['alpha', 'beta']
    private static final List<String> ALL = ['alpha', 'beta', 'pl-pl-polimorf']
    private static final Map<String, String> MODEL_VERSIONS = [
            alpha: '1.0.0', beta: '1.0.1', 'pl-pl-polimorf': '2.0.0'
    ]

    @TempDir Path temporaryDirectory

    /** Prepares exactly two unsigned POMs and their checksums without changing raw bytes. */
    @Test
    void preparesUnsignedPublicationsWithoutMutatingRawInput() {
        final Path raw = fixture(false)
        final byte[] before = Files.readAllBytes(standardPom(raw))
        final Path prepared = temporaryDirectory.resolve('prepared')
        PrepareModelCatalogBundleInputTask.prepareBundle(raw, prepared, CATALOG_VERSION)
        assertArrayEquals(before, Files.readAllBytes(standardPom(raw)))
        assertEquals(6L, regularFiles(prepared))
        assertTrue(Files.isRegularFile(prepared.resolve(relativeStandardPom() + '.md5')))
        assertTrue(Files.isRegularFile(prepared.resolve(relativeBomPom() + '.sha1')))
    }

    /** Copies test-only signatures and generates checksums for both signatures. */
    @Test
    void preparesSignedPublications() {
        final Path prepared = temporaryDirectory.resolve('prepared')
        PrepareModelCatalogBundleInputTask.prepareBundle(fixture(true), prepared, CATALOG_VERSION)
        assertEquals(12L, regularFiles(prepared))
        assertTrue(Files.isRegularFile(prepared.resolve(relativeStandardPom() + '.asc.md5')))
        assertTrue(Files.isRegularFile(prepared.resolve(relativeBomPom() + '.asc.sha1')))
    }

    /** Deletes stale prepared content before copying current publication files. */
    @Test
    void removesStalePreparedContent() {
        final Path prepared = temporaryDirectory.resolve('prepared')
        Files.createDirectories(prepared)
        Files.writeString(prepared.resolve('stale.jar'), 'stale')
        PrepareModelCatalogBundleInputTask.prepareBundle(fixture(false), prepared, CATALOG_VERSION)
        assertFalse(Files.exists(prepared.resolve('stale.jar')))
    }

    /** Excludes Gradle module metadata, its sidecars, and Maven metadata. */
    @Test
    void excludesModuleAndMavenMetadata() {
        final Path raw = fixture(false)
        final Path module = standardPom(raw).resolveSibling("radixor-models-standard-${CATALOG_VERSION}.module")
        Files.writeString(module, 'module')
        Files.writeString(module.resolveSibling(module.fileName.toString() + '.asc'), 'signature')
        Files.writeString(module.resolveSibling(module.fileName.toString() + '.sha1'), 'checksum')
        Files.writeString(module.parent.resolve('maven-metadata-local.xml'), 'metadata')
        final Path prepared = temporaryDirectory.resolve('prepared')
        PrepareModelCatalogBundleInputTask.prepareBundle(raw, prepared, CATALOG_VERSION)
        assertEquals(6L, regularFiles(prepared))
    }

    /** Rejects a missing standard publication. */
    @Test
    void rejectsMissingStandardPom() {
        final Path raw = fixture(false)
        Files.delete(standardPom(raw))
        assertThrows(GradleException) {
            PrepareModelCatalogBundleInputTask.prepareBundle(raw, temporaryDirectory.resolve('prepared'), CATALOG_VERSION)
        }
    }

    /** Rejects a missing BOM publication. */
    @Test
    void rejectsMissingBomPom() {
        final Path raw = fixture(false)
        Files.delete(bomPom(raw))
        assertThrows(GradleException) {
            PrepareModelCatalogBundleInputTask.prepareBundle(raw, temporaryDirectory.resolve('prepared'), CATALOG_VERSION)
        }
    }

    /** Rejects unexpected binary publication content. */
    @Test
    void rejectsUnexpectedJar() {
        final Path raw = fixture(false)
        Files.writeString(standardPom(raw).resolveSibling('unexpected.jar'), 'binary')
        assertThrows(GradleException) {
            PrepareModelCatalogBundleInputTask.prepareBundle(raw, temporaryDirectory.resolve('prepared'), CATALOG_VERSION)
        }
    }

    /** Rejects dictionary content in the catalog staging repository. */
    @Test
    void rejectsDictionaryContent() {
        final Path raw = fixture(false)
        final Path dictionary = raw.resolve('unrelated/stemmer.gz')
        Files.createDirectories(dictionary.parent)
        Files.writeString(dictionary, 'dictionary')
        assertThrows(GradleException) {
            PrepareModelCatalogBundleInputTask.prepareBundle(raw, temporaryDirectory.resolve('prepared'), CATALOG_VERSION)
        }
    }

    /** Produces and semantically verifies a nonempty ZIP from prepared files. */
    @Test
    void verifiesRealPreparedArchive() {
        final Path prepared = temporaryDirectory.resolve('prepared')
        PrepareModelCatalogBundleInputTask.prepareBundle(fixture(false), prepared, CATALOG_VERSION)
        final File archive = zip(prepared, temporaryDirectory.resolve('catalog.zip'))
        final List<String> entries = VerifyModelCatalogReleaseCandidateTask.verifyBundle(
                archive, CATALOG_VERSION, MODEL_VERSIONS, DEFAULTS, ALL)
        assertEquals(6, entries.size())
    }

    /** Rejects a catalog dependency that does not use its model's recorded version. */
    @Test
    void rejectsIncorrectPerModelVersion() {
        final Path prepared = temporaryDirectory.resolve('prepared')
        PrepareModelCatalogBundleInputTask.prepareBundle(fixture(false), prepared, CATALOG_VERSION)
        final File archive = zip(prepared, temporaryDirectory.resolve('catalog.zip'))
        final Map<String, String> incorrectVersions = new TreeMap<>(MODEL_VERSIONS)
        incorrectVersions.put('beta', '9.9.9')
        assertThrows(GradleException) {
            VerifyModelCatalogReleaseCandidateTask.verifyBundle(
                    archive, CATALOG_VERSION, incorrectVersions, DEFAULTS, ALL)
        }
    }

    /** Rejects an archived checksum that does not match its POM. */
    @Test
    void rejectsIncorrectArchivedChecksum() {
        final Path prepared = temporaryDirectory.resolve('prepared')
        PrepareModelCatalogBundleInputTask.prepareBundle(fixture(false), prepared, CATALOG_VERSION)
        Files.writeString(prepared.resolve(relativeStandardPom() + '.sha1'), 'incorrect')
        final File archive = zip(prepared, temporaryDirectory.resolve('catalog.zip'))
        assertThrows(GradleException) {
            VerifyModelCatalogReleaseCandidateTask.verifyBundle(
                    archive, CATALOG_VERSION, MODEL_VERSIONS, DEFAULTS, ALL)
        }
    }

    /** Repeated preparation replaces restored or stale output deterministically. */
    @Test
    void repeatedPreparationRecreatesValidInput() {
        final Path raw = fixture(false)
        final Path prepared = temporaryDirectory.resolve('prepared')
        PrepareModelCatalogBundleInputTask.prepareBundle(raw, prepared, CATALOG_VERSION)
        final String first = treeDigest(prepared)
        Files.writeString(prepared.resolve('restored-history-stale.txt'), 'stale')
        PrepareModelCatalogBundleInputTask.prepareBundle(raw, prepared, CATALOG_VERSION)
        assertEquals(first, treeDigest(prepared))
    }

    /** Creates a real Gradle ZIP, rebuilds a missing output, and reuses Configuration Cache. */
    @Test
    void gradleZipRebuildsWithConfigurationCacheReuse() {
        final Path project = temporaryDirectory.resolve('testkit-project')
        Files.createDirectories(project)
        Files.writeString(project.resolve('settings.gradle'), "rootProject.name = 'catalog-fixture'\n")
        Files.writeString(project.resolve('build.gradle'), '''plugins {
    id 'org.egothor.radixor.build-support'
}
tasks.named('prepareModelCatalogReleaseCandidate') {
    rawRepositoryDirectory = layout.projectDirectory.dir('raw')
    preparedBundleDirectory = layout.buildDirectory.dir('prepared')
    catalogVersion = '2026.1'
}
tasks.register('bundle', Zip) {
    dependsOn(tasks.named('prepareModelCatalogReleaseCandidate'))
    from(layout.buildDirectory.dir('prepared'))
    destinationDirectory = layout.buildDirectory.dir('candidate')
    archiveFileName = 'catalog.zip'
}
''')
        final Path raw = project.resolve('raw')
        write(standardPom(raw), pom('radixor-models-standard', false))
        write(bomPom(raw), pom('radixor-models-bom', true))

        final List<String> arguments = ['bundle', '--configuration-cache',
                '--configuration-cache-problems=fail', '--warning-mode=fail']
        final String first = GradleRunner.create().withProjectDir(project.toFile())
                .withPluginClasspath().withArguments(arguments).build().output
        final Path archive = project.resolve('build/candidate/catalog.zip')
        assertTrue(Files.size(archive) > 0L)
        Files.delete(archive)
        final String second = GradleRunner.create().withProjectDir(project.toFile())
                .withPluginClasspath().withArguments(arguments).build().output
        assertTrue(Files.size(archive) > 0L)
        assertTrue(first.contains('Configuration cache entry stored.'))
        assertTrue(second.contains('Configuration cache entry reused.'))
    }

    private Path fixture(final boolean signed) {
        final Path raw = temporaryDirectory.resolve('raw')
        write(standardPom(raw), pom('radixor-models-standard', false))
        write(bomPom(raw), pom('radixor-models-bom', true))
        if (signed) {
            Files.writeString(standardPom(raw).resolveSibling(standardPom(raw).fileName.toString() + '.asc'), 'test signature')
            Files.writeString(bomPom(raw).resolveSibling(bomPom(raw).fileName.toString() + '.asc'), 'test signature')
        }
        return raw
    }

    private static String pom(final String artifact, final boolean managed) {
        final List<String> ids = managed ? ALL : DEFAULTS
        final String dependencies = ids.collect { String id ->
            "<dependency><groupId>org.egothor</groupId><artifactId>radixor-model-${id}</artifactId>" +
                    "<version>${MODEL_VERSIONS.get(id)}</version>${managed ? '' : '<scope>runtime</scope>'}</dependency>"
        }.join()
        final String body = managed ? "<dependencyManagement><dependencies>${dependencies}</dependencies></dependencyManagement>"
                : "<dependencies>${dependencies}</dependencies>"
        return "<?xml version=\"1.0\"?><project xmlns=\"http://maven.apache.org/POM/4.0.0\">" +
                "<modelVersion>4.0.0</modelVersion><groupId>org.egothor</groupId>" +
                "<artifactId>${artifact}</artifactId><version>${CATALOG_VERSION}</version>${body}</project>"
    }

    private static Path standardPom(final Path raw) { raw.resolve(relativeStandardPom()) }
    private static Path bomPom(final Path raw) { raw.resolve(relativeBomPom()) }
    private static String relativeStandardPom() {
        "org/egothor/radixor-models-standard/${CATALOG_VERSION}/radixor-models-standard-${CATALOG_VERSION}.pom"
    }
    private static String relativeBomPom() {
        "org/egothor/radixor-models-bom/${CATALOG_VERSION}/radixor-models-bom-${CATALOG_VERSION}.pom"
    }

    private static void write(final Path path, final String value) {
        Files.createDirectories(path.parent)
        Files.writeString(path, value, StandardCharsets.UTF_8)
    }

    private static long regularFiles(final Path root) {
        Files.walk(root).withCloseable { paths -> paths.filter(Files::isRegularFile).count() }
    }

    private static File zip(final Path root, final Path target) {
        new ZipOutputStream(Files.newOutputStream(target)).withCloseable { ZipOutputStream output ->
            Files.walk(root).withCloseable { paths ->
                paths.filter(Files::isRegularFile).sorted().forEach { Path file ->
                    output.putNextEntry(new ZipEntry(root.relativize(file).toString().replace(File.separatorChar, '/' as char)))
                    Files.copy(file, output)
                    output.closeEntry()
                }
            }
        }
        return target.toFile()
    }

    private static String treeDigest(final Path root) {
        final MessageDigest digest = MessageDigest.getInstance('SHA-256')
        Files.walk(root).withCloseable { paths ->
            paths.filter(Files::isRegularFile).sorted().forEach { Path path ->
                digest.update(root.relativize(path).toString().getBytes(StandardCharsets.UTF_8))
                digest.update(Files.readAllBytes(path))
            }
        }
        return digest.digest().encodeHex().toString()
    }
}
