package org.egothor.radixor

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.plugins.signing.SigningExtension

import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Configures validation, generation, packaging, and publication for one model artifact. */
final class RadixorModelPlugin implements Plugin<Project> {
    /** Applies the model convention to a project. */
    @Override
    void apply(final Project project) {
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply('maven-publish')
        project.pluginManager.apply('signing')
        project.java {
            withSourcesJar()
            withJavadocJar()
            sourceCompatibility = org.gradle.api.JavaVersion.VERSION_21
            targetCompatibility = org.gradle.api.JavaVersion.VERSION_21
        }
        final RadixorModelExtension model = project.extensions.create('radixorModel', RadixorModelExtension)
        project.group = 'org.egothor'
        project.version = project.providers.gradleProperty('modelReleaseVersion')
                .orElse(project.providers.fileContents(project.layout.projectDirectory.file('model-version.txt')).asText.map(String::trim))
                .get()

        final File input = project.file('src/modelInput/stemmer.gz')
        final File generated = project.layout.buildDirectory.dir('generated/modelResources').get().asFile
        project.sourceSets.main.resources.setSrcDirs([generated])

        final def validate = project.tasks.register('validateModelInput', ValidateModelInputTask) {
            group = 'verification'
            description = 'Validates the immutable source dictionary, metadata, version, and model-specific licensing material.'
            dictionaryFile = project.layout.projectDirectory.file('src/modelInput/stemmer.gz')
            versionFile = project.layout.projectDirectory.file('model-version.txt')
            modelId = model.modelId
            moduleName = project.name
            shareAlike = model.sourceLicense.map { String license -> license == 'CC-BY-SA-3.0' }
            metadata.put('source.project', model.sourceProject)
            metadata.put('source.repository', model.sourceRepository)
            metadata.put('source.dataset', model.sourceDataset)
            metadata.put('source.revision', model.sourceRevision)
            metadata.put('source.revisionStatus', model.sourceRevisionStatus)
            metadata.put('source.license', model.sourceLicense)
            metadata.put('source.licenseUri', model.sourceLicenseUri)
            metadata.put('source.attribution', model.sourceAttribution)
            metadata.put('source.verificationDate', model.sourceVerificationDate)
            metadata.put('transformations.summary', model.transformationsSummary)
        }

        final def prepare = project.tasks.register('prepareModelResources', PrepareModelResourcesTask) {
            group = 'build'
            description = 'Copies validated dictionary bytes and generates the immutable model descriptor and index.'
            dependsOn(validate)
            dictionaryFile = project.layout.projectDirectory.file('src/modelInput/stemmer.gz')
            versionFile = project.layout.projectDirectory.file('model-version.txt')
            shareAlike = model.sourceLicense.map { String license -> license == 'CC-BY-SA-3.0' }
            generatedDirectory = project.layout.buildDirectory.dir('generated/modelResources')
            descriptorValues.put('model.id', model.modelId)
            descriptorValues.put('model.language', model.language)
            descriptorValues.put('model.displayName', model.displayName)
            descriptorValues.put('model.default', model.defaultModel.map(String::valueOf))
            descriptorValues.put('source.name', model.sourceName)
            descriptorValues.put('source.version', model.sourceVersion)
            descriptorValues.put('source.project', model.sourceProject)
            descriptorValues.put('source.repository', model.sourceRepository)
            descriptorValues.put('source.dataset', model.sourceDataset)
            descriptorValues.put('source.revision', model.sourceRevision)
            descriptorValues.put('source.revisionStatus', model.sourceRevisionStatus)
            descriptorValues.put('source.license', model.sourceLicense)
            descriptorValues.put('source.licenseUri', model.sourceLicenseUri)
            descriptorValues.put('source.attribution', model.sourceAttribution)
            descriptorValues.put('source.verificationDate', model.sourceVerificationDate)
            descriptorValues.put('transformations.summary', model.transformationsSummary)
        }
        project.afterEvaluate {
            final boolean shareAlike = model.sourceLicense.get() == 'CC-BY-SA-3.0'
            if (shareAlike) {
                final def notice = project.layout.projectDirectory.file("src/modelInput/${model.noticeFileName.get()}")
                validate.configure { noticeFile = notice }
                prepare.configure { noticeFile = notice }
            } else {
                final def license = project.layout.projectDirectory.file("src/modelInput/${model.licenseFileName.get()}")
                validate.configure { licenseFile = license }
                prepare.configure { licenseFile = license }
            }
        }
        project.tasks.named('processResources', Copy).configure { dependsOn(prepare); duplicatesStrategy = DuplicatesStrategy.FAIL }
        project.tasks.named('sourcesJar', Jar).configure { dependsOn(prepare); exclude('**/stemmer.gz') }
        project.tasks.named('javadocJar', Jar).configure { exclude('**/stemmer.gz') }
        project.tasks.named('jar', Jar).configure {
            archiveBaseName.set("radixor-model-${project.name}")
            preserveFileTimestamps = false
            reproducibleFileOrder = true
        }
        final def verifyDescriptor = project.tasks.register('verifyModelDescriptor') {
            group = 'verification'; description = 'Verifies generated descriptor identity and checksum.'; dependsOn(prepare)
            doLast {
                final Properties properties = new Properties()
                new File(generated, "META-INF/radixor/models/${model.modelId.get()}.properties").withInputStream(properties::load)
                if (properties.getProperty('model.sha256') != sha256(input)) {
                    throw new GradleException('Generated descriptor checksum does not match the immutable source input.')
                }
            }
        }
        final def verifyJar = project.tasks.register('verifyModelJar') {
            group = 'verification'; description = 'Verifies the model JAR checksum, layout, metadata, and dictionary-free documentation artifacts.'
            dependsOn(project.tasks.named('jar'), project.tasks.named('sourcesJar'), project.tasks.named('javadocJar'))
            doLast {
                final File archive = project.tasks.named('jar', Jar).get().archiveFile.get().asFile
                final List<String> names = []
                final String resource = "org/egothor/stemmer/models/${model.modelId.get()}/stemmer.gz"
                final boolean shareAlike = model.sourceLicense.get() == 'CC-BY-SA-3.0'
                final String licenseResource = 'META-INF/LICENSES/PoliMorf-BSD-2-Clause.txt'
                final File sourceLicense = shareAlike ? null : project.file("src/modelInput/${model.licenseFileName.get()}")
                final File sourceNotice = shareAlike
                        ? project.file("src/modelInput/${model.noticeFileName.get()}") : null
                final String noticeResource = "META-INF/NOTICE/${model.modelId.get()}-data.txt"
                String packagedChecksum
                String packagedLicenseChecksum
                String packagedNoticeChecksum
                new java.util.zip.ZipFile(archive).withCloseable { zip ->
                    zip.entries().each { names.add(it.name) }
                    final def entry = zip.getEntry(resource)
                    if (entry != null) {
                        packagedChecksum = sha256(zip.getInputStream(entry).bytes)
                    }
                    final def licenseEntry = zip.getEntry(licenseResource)
                    if (licenseEntry != null) {
                        packagedLicenseChecksum = sha256(zip.getInputStream(licenseEntry).bytes)
                    }
                    final def noticeEntry = zip.getEntry(noticeResource)
                    if (noticeEntry != null) {
                        packagedNoticeChecksum = sha256(zip.getInputStream(noticeEntry).bytes)
                    }
                }
                if (names.count { String name -> name.endsWith('/stemmer.gz') } != 1 || !names.contains(resource)) {
                    throw new GradleException("Model JAR must contain exactly one dictionary at ${resource}.")
                }
                if (packagedChecksum != sha256(input)) {
                    throw new GradleException("Packaged dictionary checksum does not match the immutable source input at ${resource}.")
                }
                if (shareAlike) {
                    requireMatchingChecksum('notice', noticeResource, sha256(sourceNotice), packagedNoticeChecksum)
                    validateUniMorphJarContents(names)
                } else {
                    requireMatchingChecksum('license', licenseResource, sha256(sourceLicense), packagedLicenseChecksum)
                    validatePoliMorfJarContents(names)
                }
                ['META-INF/radixor/models.index', "META-INF/radixor/models/${model.modelId.get()}.properties"].each { String name ->
                    if (!names.contains(name)) throw new GradleException("Model JAR is missing ${name}.")
                }
                [project.tasks.named('sourcesJar', Jar).get(), project.tasks.named('javadocJar', Jar).get()].each { Jar task ->
                    final File documentationArchive = task.archiveFile.get().asFile
                    new java.util.zip.ZipFile(documentationArchive).withCloseable { zip ->
                        if (zip.entries().any { entry -> entry.name.endsWith('/stemmer.gz') || entry.name == 'stemmer.gz' }) {
                            throw new GradleException("Documentation artifact ${documentationArchive.name} must not contain a model dictionary.")
                        }
                    }
                }
            }
        }
        project.tasks.register('validateModelRelease') {
            group = 'verification'; description = 'Validates a tag-supplied model release version.'; dependsOn(verifyDescriptor, verifyJar)
            doLast {
                if (!project.hasProperty('modelReleaseVersion')) throw new GradleException('Model release validation requires -PmodelReleaseVersion=<version>.')
                final String recorded = project.file('model-version.txt').text.trim()
                if (project.property('modelReleaseVersion').toString() != recorded) throw new GradleException("Release version does not match model-version.txt: ${recorded}")
            }
        }
        project.tasks.named('check').configure { dependsOn(verifyDescriptor, verifyJar) }
        project.extensions.configure(PublishingExtension) { PublishingExtension publishing ->
            publishing.publications.create('model', MavenPublication) { MavenPublication publication ->
                publication.from(project.components.java)
                publication.artifactId = "radixor-model-${project.name}"
                publication.pom {
                    name.set("Radixor model ${project.name}")
                    description.set(model.displayName.zip(model.sourceLicense) { String displayName, String licenseId ->
                        final String material = licenseId == 'CC-BY-SA-3.0'
                                ? 'See the packaged model-specific notice.'
                                : 'See the packaged model-data license.'
                        return "${displayName}. This artifact contains Radixor-derived model data licensed under ${licenseId}; "
                                .concat("Radixor software is licensed separately under BSD-3-Clause. ${material}")
                    })
                    url.set('https://github.com/leogalambos/Radixor')
                    licenses {
                        license {
                            name.set(model.sourceLicense)
                            url.set(model.sourceLicenseUri)
                            distribution.set('repo')
                        }
                    }
                    developers {
                        developer {
                            id.set('egothor')
                            name.set('Leo Galambos')
                            email.set('egothor@gmail.com')
                        }
                    }
                    scm {
                        url.set('https://github.com/leogalambos/Radixor')
                        connection.set('scm:git:https://github.com/leogalambos/Radixor.git')
                        developerConnection.set('scm:git:ssh://git@github.com/leogalambos/Radixor.git')
                    }
                }
            }
            publishing.repositories.maven {
                name = 'modelStaging'
                url = project.layout.buildDirectory.dir('model-staging-repository').get().asFile.toURI()
            }
        }

        final String signingKey = project.providers.environmentVariable('SIGNING_KEY').orNull
        final String signingPassword = project.providers.environmentVariable('SIGNING_PASSWORD').orNull
        project.extensions.configure(SigningExtension) { SigningExtension signing ->
            signing.required = {
                project.providers.environmentVariable('GITHUB_REF_TYPE').orNull == 'tag'
            }
            if (signingKey != null && !signingKey.isBlank()) {
                signing.useInMemoryPgpKeys(signingKey, signingPassword)
                signing.sign(project.extensions.getByType(PublishingExtension).publications.getByName('model'))
            }
        }

        final def checksums = project.tasks.register('createModelCentralChecksums') {
            group = 'publishing'
            description = 'Creates Maven Central checksums for this model staging repository.'
            dependsOn(project.tasks.named('publishModelPublicationToModelStagingRepository'))
            doLast {
                final File repository = project.layout.buildDirectory.dir('model-staging-repository').get().asFile
                repository.eachFileRecurse { File artifact ->
                    if (artifact.isFile() && !['.md5', '.sha1', '.sha256', '.sha512'].any {
                        String extension -> artifact.name.endsWith(extension)
                    }) {
                        new File(artifact.absolutePath + '.md5').setText(sha256WithAlgorithm(artifact, 'MD5'), 'US-ASCII')
                        new File(artifact.absolutePath + '.sha1').setText(sha256WithAlgorithm(artifact, 'SHA-1'), 'US-ASCII')
                    }
                }
            }
        }
        project.tasks.register('packageModelReleaseCandidate', Zip) {
            group = 'distribution'
            description = 'Packages only this model publication as a Maven-layout local release candidate.'
            dependsOn(checksums)
            from(project.layout.buildDirectory.dir('model-staging-repository')) {
                exclude('**/maven-metadata*.xml*')
            }
            destinationDirectory.set(project.layout.buildDirectory.dir('model-release-candidate'))
            archiveFileName.set('central-bundle.zip')
            doFirst {
                if (project.providers.environmentVariable('GITHUB_REF_TYPE').orNull == 'tag'
                        && (signingKey == null || signingKey.isBlank()
                        || signingPassword == null || signingPassword.isBlank())) {
                    throw new GradleException('A tagged model release requires SIGNING_KEY and SIGNING_PASSWORD.')
                }
            }
        }
    }

    /** Ensures a required file exists. */
    static void requireFile(final File file, final String diagnostic) {
        if (!file.isFile()) throw new GradleException(diagnostic)
    }

    /** Rejects a missing or byte-different packaged licensing resource. */
    static void requireMatchingChecksum(final String kind, final String resource,
            final String sourceChecksum, final String packagedChecksum) {
        if (packagedChecksum != sourceChecksum) {
            throw new GradleException("Packaged ${kind} does not match the source ${kind} at ${resource}.")
        }
    }

    /** Validates complete source, licensing, attribution, revision-status, and transformation metadata. */
    private static void validateMetadata(final RadixorModelExtension model) {
        final Map<String, String> required = [
                'source.project': model.sourceProject.orNull,
                'source.repository': model.sourceRepository.orNull,
                'source.dataset': model.sourceDataset.orNull,
                'source.revision': model.sourceRevision.orNull,
                'source.revisionStatus': model.sourceRevisionStatus.orNull,
                'source.license': model.sourceLicense.orNull,
                'source.licenseUri': model.sourceLicenseUri.orNull,
                'source.attribution': model.sourceAttribution.orNull,
                'source.verificationDate': model.sourceVerificationDate.orNull,
                'transformations.summary': model.transformationsSummary.orNull]
        required.each { String key, String value ->
            if (value == null || value.isBlank()) {
                throw new GradleException("Required model metadata is missing: ${key}")
            }
        }
        validateRevisionMetadata(model.sourceRevision.get(), model.sourceRevisionStatus.get())
    }

    /** Accepts an exact recorded revision or the explicit legacy-import sentinel, but never an absent status. */
    static void validateRevisionMetadata(final String revision, final String status) {
        if (revision == null || revision.isBlank()) {
            throw new GradleException('Required model metadata is missing: source.revision')
        }
        if (status == null || status.isBlank()) {
            throw new GradleException('Required model metadata is missing: source.revisionStatus')
        }
        final String sentinel = 'not-recorded-in-legacy-import'
        if (revision == sentinel && status != sentinel) {
            throw new GradleException('The legacy revision sentinel requires source.revisionStatus=not-recorded-in-legacy-import.')
        }
        if (revision != sentinel && status != 'recorded') {
            throw new GradleException('An exact source revision requires source.revisionStatus=recorded.')
        }
    }

    /** Validates the model-specific attribution and ShareAlike notice. */
    static void validateShareAlikeNotice(final File notice, final RadixorModelExtension model) {
        validateShareAlikeNoticeText(notice.getText('UTF-8'), notice.toString(), model.modelId.get(),
                model.sourceRepository.get(), model.sourceLicenseUri.get(), model.sourceRevision.get(),
                model.sourceRevisionStatus.get())
    }

    /** Validates required content in one UniMorph model-data notice. */
    static void validateShareAlikeNoticeText(final String rawText, final String noticeName,
            final String modelId, final String repository, final String licenseUri,
            final String revision, final String revisionStatus) {
        final String text = rawText.replace('\r\n', '\n')
        final List<String> required = [
                "Model ID: ${modelId}",
                "Official repository: ${repository}",
                'Attribution:',
                'License:\nCreative Commons Attribution-ShareAlike 3.0 Unported',
                "Canonical license URI: ${licenseUri}",
                'Radixor modifications:',
                "Revision status: ${revisionStatus}",
                'Copyright (C) 2026, Leo Galambos.',
                'Radixor-specific selection, verification, cleaning, normalization,',
                'to the extent protected by applicable law.',
                'The underlying morphological data remains attributed to UniMorph and',
                "This derived model data, including Radixor's protectable contributions,",
                'is distributed under Creative Commons Attribution-ShareAlike 3.0',
                'Neither UniMorph nor any upstream contributor endorses Radixor.']
        if (revision == 'not-recorded-in-legacy-import') {
            required.add('The exact UniMorph commit used for the original Radixor import was not recorded.')
        }
        final List<String> missing = required.findAll { String value -> !text.contains(value) }
        if (!missing.isEmpty()) {
            throw new GradleException("Model notice ${noticeName} is missing required content: ${missing.join(', ')}")
        }
    }

    /** Rejects generic license files and foreign notices in a UniMorph model artifact. */
    static void validateUniMorphJarContents(final List<String> names) {
        if (names.any { String name -> name.startsWith('META-INF/LICENSES/') }) {
            throw new GradleException('A UniMorph model artifact must use only its model-specific notice for data licensing.')
        }
        if (names.count { String name -> name.startsWith('META-INF/NOTICE/') && !name.endsWith('/') } != 1) {
            throw new GradleException('A UniMorph model artifact must contain exactly one model-specific notice.')
        }
    }

    /** Rejects UniMorph licensing material in the separately licensed PoliMorf artifact. */
    static void validatePoliMorfJarContents(final List<String> names) {
        if (names.any { String name -> name.startsWith('META-INF/NOTICE/')
                || name.contains('CC-BY-SA') }) {
            throw new GradleException('The PoliMorf artifact must not contain UniMorph CC BY-SA material.')
        }
    }

    /** Memory-bounded validation statistics for one dictionary input. */
    static final class DictionaryValidationResult {
        final long acceptedGroupCount
        final long acceptedFormCount
        final long ignoredEmptyVariantCount

        DictionaryValidationResult(final long acceptedGroupCount, final long acceptedFormCount,
                final long ignoredEmptyVariantCount) {
            this.acceptedGroupCount = acceptedGroupCount
            this.acceptedFormCount = acceptedFormCount
            this.ignoredEmptyVariantCount = ignoredEmptyVariantCount
        }
    }

    /** Validates GZip, strict UTF-8, and dictionary rows without retaining decompressed input. */
    static DictionaryValidationResult validateDictionary(final File file) {
        long acceptedGroups = 0L
        long acceptedForms = 0L
        long ignoredEmptyVariants = 0L
        try {
            final def decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            Files.newInputStream(file.toPath()).withCloseable { InputStream source ->
                new BufferedInputStream(source).withCloseable { BufferedInputStream bufferedInput ->
                    new GZIPInputStream(bufferedInput).withCloseable { GZIPInputStream gzipInput ->
                        new BufferedReader(new InputStreamReader(gzipInput, decoder)).withCloseable { BufferedReader reader ->
                            String line
                            long lineNumber = 0L
                            while ((line = reader.readLine()) != null) {
                lineNumber++
                final String trimmed = line.trim()
                if (trimmed && !trimmed.startsWith('#') && !trimmed.startsWith('//')) {
                    final String[] columns = line.split('\\t', -1)
                    if (columns[0].isEmpty()) {
                        throw new GradleException("Invalid Radixor dictionary row ${lineNumber} in ${file}.")
                    }
                    if (containsUnicodeWhitespace(columns[0])) continue
                    long acceptedRowForms = 1L
                    for (int index = 1; index < columns.length; index++) {
                        final String variant = columns[index]
                        if (variant.isEmpty()) {
                            ignoredEmptyVariants++
                        } else if (!containsUnicodeWhitespace(variant)) {
                            acceptedRowForms++
                        }
                    }
                    acceptedGroups++
                    acceptedForms += acceptedRowForms
                }
                            }
                        }
                    }
                }
            }
        } catch (GradleException exception) {
            throw exception
        } catch (Exception exception) {
            throw new GradleException("Invalid GZip or UTF-8 model input: ${file}", exception)
        }
        if (acceptedGroups == 0L) throw new GradleException("Model dictionary contains no valid rows: ${file}")
        if (ignoredEmptyVariants > 0L) {
            println("Model validation warning: " + file + " contains " + ignoredEmptyVariants
                    + " empty variant columns; the production parser intentionally ignores empty variants.")
        }
        return new DictionaryValidationResult(acceptedGroups, acceptedForms, ignoredEmptyVariants)
    }

    /** Detects Unicode whitespace in one bounded dictionary field. */
    private static boolean containsUnicodeWhitespace(final String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) return true
        }
        return false
    }

    /** Builds deterministic descriptor text. */
    private static String descriptorText(final RadixorModelExtension model, final String version,
            final String resource, final String checksum) {
        return """model.id=${model.modelId.get()}
model.version=${version}
model.language=${model.language.get()}
model.displayName=${model.displayName.get()}
model.resource=${resource}
model.default=${model.defaultModel.get()}
model.format=radixor-dictionary-tsv-gzip
model.formatVersion=1
model.sha256=${checksum}
model.rightToLeft=${['FA_IR', 'HE_IL', 'YI'].contains(model.language.get())}
model.caseProcessing=LOWERCASE_WITH_LOCALE_ROOT
model.diacriticProcessing=AS_IS
model.storeOriginal=true
source.name=${model.sourceName.get()}
source.version=${model.sourceVersion.get()}
source.project=${model.sourceProject.get()}
source.repository=${model.sourceRepository.get()}
source.dataset=${model.sourceDataset.get()}
source.revision=${model.sourceRevision.get()}
source.revisionStatus=${model.sourceRevisionStatus.get()}
source.license=${model.sourceLicense.get()}
source.licenseUri=${model.sourceLicenseUri.get()}
source.attribution=${model.sourceAttribution.get()}
source.verificationDate=${model.sourceVerificationDate.get()}
transformations.summary=${model.transformationsSummary.get()}
compiler.radixorVersion=3.x
compiler.radixorCommit=unavailable
statistics.groups=unavailable
statistics.forms=unavailable
"""
    }

    /** Calculates the lowercase hexadecimal SHA-256 digest. */
    private static String sha256(final File file) {
        return sha256(file.bytes)
    }

    /** Calculates the lowercase hexadecimal SHA-256 digest of bytes. */
    private static String sha256(final byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).collect { byte value -> String.format('%02x', value & 0xff) }.join()
    }

    /** Calculates a lowercase hexadecimal digest using the requested algorithm. */
    private static String sha256WithAlgorithm(final File file, final String algorithm) {
        return MessageDigest.getInstance(algorithm).digest(file.bytes)
                .collect { byte value -> String.format('%02x', value & 0xff) }.join()
    }

}
