package org.egothor.radixor

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Verifies the contents and Maven semantics of the model catalog Central bundle. */
abstract class VerifyModelCatalogReleaseCandidateTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getBundleFile()

    @OutputFile
    abstract RegularFileProperty getReportFile()

    @Input abstract Property<String> getCatalogVersion()
    @Input abstract Property<String> getModelVersion()
    @Input abstract ListProperty<String> getDefaultModelIds()
    @Input abstract ListProperty<String> getAllModelIds()

    /** Performs byte-level archive and semantic POM validation. */
    @TaskAction
    void verify() {
        final List<String> entries = verifyBundle(bundleFile.get().asFile, catalogVersion.get(),
                modelVersion.get(), defaultModelIds.get(), allModelIds.get())
        final File report = reportFile.get().asFile
        Files.createDirectories(report.toPath().parent)
        Files.writeString(report.toPath(), "Bundle: ${bundleFile.get().asFile.name}\nBytes: ${bundleFile.get().asFile.length()}\n"
                + entries.join('\n') + '\n', StandardCharsets.UTF_8)
    }

    static List<String> verifyBundle(final File bundle, final String catalogVersion,
            final String modelVersion, final List<String> defaultIds, final List<String> allIds) {
        if (!bundle.isFile() || bundle.length() == 0L) {
            throw new GradleException("The model catalog Central bundle is missing or empty: ${bundle}.")
        }
        final Map<String, byte[]> content = new TreeMap<>()
        new ZipFile(bundle).withCloseable { ZipFile archive ->
            archive.entries().each { ZipEntry entry ->
                if (!entry.directory) {
                    archive.getInputStream(entry).withCloseable { InputStream input ->
                        content.put(entry.name, input.readAllBytes())
                    }
                }
            }
        }
        final List<String> entries = content.keySet().toList()
        final List<String> poms = entries.findAll { String entry -> entry.endsWith('.pom') }
        final List<String> unsupported = entries.findAll { String entry ->
            !(entry ==~ 'org/egothor/radixor-models-(?:standard|bom)/[^/]+/'
                    + 'radixor-models-(?:standard|bom)-[^/]+\\.pom(?:\\.asc)?(?:\\.(?:md5|sha1))?')
        }
        if (!unsupported.isEmpty()) {
            throw new GradleException("The model catalog bundle contains unsupported files: ${unsupported}.")
        }
        if (poms.size() != 2) {
            throw new GradleException("The model catalog bundle must contain exactly two POM files; found ${poms.size()}.")
        }
        if (entries.any { String entry -> entry.endsWith('.jar') || entry.endsWith('/stemmer.gz')
                || entry.endsWith('.module') || entry.contains('maven-metadata') || entry.contains('benchmark-pack') }) {
            throw new GradleException('The model catalog bundle contains forbidden publication content.')
        }
        poms.each { String pom -> verifyChecksums(content, pom) }
        entries.findAll { String entry -> entry.endsWith('.pom.asc') }.each { String signature ->
            verifyChecksums(content, signature)
        }

        final String standardPath = expectedPomPath('standard', catalogVersion)
        final String bomPath = expectedPomPath('bom', catalogVersion)
        if (!content.containsKey(standardPath) || !content.containsKey(bomPath)) {
            throw new GradleException('The bundle does not contain the expected standard and BOM coordinates.')
        }
        final Element standard = parsePom(content.get(standardPath))
        final Element bom = parsePom(content.get(bomPath))
        verifyCoordinates(standard, 'radixor-models-standard', catalogVersion)
        verifyCoordinates(bom, 'radixor-models-bom', catalogVersion)

        final Map<String, String> standardDependencies = dependencies(standard, false)
        final Map<String, String> bomConstraints = dependencies(bom, true)
        final Set<String> expectedDefaults = defaultIds.collect { String id -> "org.egothor:radixor-model-${id}" } as Set<String>
        final Set<String> expectedAll = allIds.collect { String id -> "org.egothor:radixor-model-${id}" } as Set<String>
        if (standardDependencies.keySet() != expectedDefaults
                || standardDependencies.values().any { String version -> version != modelVersion }
                || standardDependencies.containsKey('org.egothor:radixor-model-pl-pl-polimorf')
                || dependencyScopes(standard).any { String scope -> scope != 'runtime' }) {
            throw new GradleException('The standard catalog POM must reference exactly the 20 default model artifacts at the model version.')
        }
        if (!dependencies(bom, false).isEmpty()) {
            throw new GradleException('The model BOM must not introduce runtime dependencies.')
        }
        if (bomConstraints.keySet() != expectedAll
                || bomConstraints.values().any { String version -> version != modelVersion }) {
            throw new GradleException('The model BOM must manage exactly all 21 model artifacts at the model version.')
        }
        return entries
    }

    private static String expectedPomPath(final String kind, final String version) {
        return "org/egothor/radixor-models-${kind}/${version}/radixor-models-${kind}-${version}.pom"
    }

    private static void verifyChecksums(final Map<String, byte[]> content, final String artifact) {
        ['MD5': 'md5', 'SHA-1': 'sha1'].each { String algorithm, String extension ->
            final String checksum = artifact + '.' + extension
            if (!content.containsKey(checksum)) {
                throw new GradleException("The catalog artifact is missing its ${algorithm} checksum: ${artifact}.")
            }
            final String expected = MessageDigest.getInstance(algorithm).digest(content.get(artifact)).encodeHex().toString()
            final String actual = new String(content.get(checksum), StandardCharsets.US_ASCII).trim()
            if (actual != expected) {
                throw new GradleException("The ${algorithm} checksum does not match ${artifact}.")
            }
        }
    }

    private static Element parsePom(final byte[] xml) {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        factory.setNamespaceAware(true)
        factory.setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)
        factory.setFeature('http://xml.org/sax/features/external-general-entities', false)
        factory.setFeature('http://xml.org/sax/features/external-parameter-entities', false)
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, '')
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, '')
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).documentElement
    }

    private static void verifyCoordinates(final Element project, final String artifactId, final String version) {
        if (directText(project, 'groupId') != 'org.egothor'
                || directText(project, 'artifactId') != artifactId
                || directText(project, 'version') != version) {
            throw new GradleException("Unexpected Maven coordinates for ${artifactId}.")
        }
    }

    private static Map<String, String> dependencies(final Element project, final boolean managed) {
        final Map<String, String> result = new TreeMap<>()
        final Element parent = managed ? directChild(project, 'dependencyManagement') : project
        final Element container = parent == null ? null : directChild(parent, 'dependencies')
        if (container == null) return result
        childElements(container, 'dependency').each { Element dependency ->
            final String coordinate = directText(dependency, 'groupId') + ':' + directText(dependency, 'artifactId')
            if (result.put(coordinate, directText(dependency, 'version')) != null) {
                throw new GradleException("The catalog POM contains duplicate dependency ${coordinate}.")
            }
        }
        return result
    }

    private static List<String> dependencyScopes(final Element project) {
        final Element container = directChild(project, 'dependencies')
        if (container == null) return []
        return childElements(container, 'dependency').collect { Element dependency -> directText(dependency, 'scope') }
    }

    private static String directText(final Element parent, final String name) {
        final Element child = directChild(parent, name)
        return child == null ? null : child.textContent.trim()
    }

    private static Element directChild(final Element parent, final String name) {
        if (parent == null) return null
        for (int index = 0; index < parent.childNodes.length; index++) {
            if (parent.childNodes.item(index) instanceof Element
                    && parent.childNodes.item(index).localName == name) return (Element) parent.childNodes.item(index)
        }
        return null
    }

    private static List<Element> childElements(final Element parent, final String name) {
        final List<Element> result = []
        for (int index = 0; index < parent.childNodes.length; index++) {
            if (parent.childNodes.item(index) instanceof Element
                    && parent.childNodes.item(index).localName == name) result.add((Element) parent.childNodes.item(index))
        }
        return result
    }
}
