package org.egothor.radixor

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.stream.Stream

/** Generates one model's deterministic resource tree without retaining Project state. */
abstract class PrepareModelResourcesTask extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.RELATIVE) abstract RegularFileProperty getDictionaryFile()
    @InputFile @PathSensitive(PathSensitivity.RELATIVE) abstract RegularFileProperty getVersionFile()
    @Optional @InputFile @PathSensitive(PathSensitivity.RELATIVE) abstract RegularFileProperty getLicenseFile()
    @Optional @InputFile @PathSensitive(PathSensitivity.RELATIVE) abstract RegularFileProperty getNoticeFile()
    @Input abstract Property<Boolean> getShareAlike()
    @Input abstract MapProperty<String, String> getDescriptorValues()
    @OutputDirectory abstract DirectoryProperty getGeneratedDirectory()

    /** Copies bounded inputs and writes descriptor and index files. */
    @TaskAction
    void prepareResources() {
        final Path generated = generatedDirectory.get().asFile.toPath()
        deleteTree(generated)
        final Map<String, String> values = descriptorValues.get()
        final String id = values['model.id']
        final String resource = "org/egothor/stemmer/models/${id}/stemmer.gz"
        final Path dictionaryTarget = generated.resolve(resource)
        Files.createDirectories(dictionaryTarget.parent)
        Files.copy(dictionaryFile.get().asFile.toPath(), dictionaryTarget, StandardCopyOption.REPLACE_EXISTING)

        final Path descriptor = generated.resolve("META-INF/radixor/models/${id}.properties")
        Files.createDirectories(descriptor.parent)
        Files.writeString(descriptor, descriptorText(values,
                versionFile.get().asFile.getText('UTF-8').trim(), resource, sha256(dictionaryFile.get().asFile)))
        final Path index = generated.resolve('META-INF/radixor/models.index')
        Files.createDirectories(index.parent)
        Files.writeString(index, "META-INF/radixor/models/${id}.properties\n")

        if (shareAlike.get()) {
            final Path notice = generated.resolve("META-INF/NOTICE/${id}-data.txt")
            Files.createDirectories(notice.parent)
            Files.copy(noticeFile.get().asFile.toPath(), notice, StandardCopyOption.REPLACE_EXISTING)
        } else {
            final Path license = generated.resolve('META-INF/LICENSES/PoliMorf-BSD-2-Clause.txt')
            Files.createDirectories(license.parent)
            Files.copy(licenseFile.get().asFile.toPath(), license, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private static String descriptorText(final Map<String, String> value, final String version,
            final String resource, final String checksum) {
        return """model.id=${value['model.id']}
model.version=${version}
model.language=${value['model.language']}
model.displayName=${value['model.displayName']}
model.resource=${resource}
model.default=${value['model.default']}
model.format=radixor-dictionary-tsv-gzip
model.formatVersion=1
model.sha256=${checksum}
model.rightToLeft=${['FA_IR', 'HE_IL', 'YI'].contains(value['model.language'])}
model.caseProcessing=LOWERCASE_WITH_LOCALE_ROOT
model.diacriticProcessing=AS_IS
model.storeOriginal=true
source.name=${value['source.name']}
source.version=${value['source.version']}
source.project=${value['source.project']}
source.repository=${value['source.repository']}
source.dataset=${value['source.dataset']}
source.revision=${value['source.revision']}
source.revisionStatus=${value['source.revisionStatus']}
source.license=${value['source.license']}
source.licenseUri=${value['source.licenseUri']}
source.attribution=${value['source.attribution']}
source.verificationDate=${value['source.verificationDate']}
transformations.summary=${value['transformations.summary']}
compiler.radixorVersion=3.x
compiler.radixorCommit=unavailable
statistics.groups=unavailable
statistics.forms=unavailable
"""
    }

    private static String sha256(final File file) {
        return MessageDigest.getInstance('SHA-256').digest(file.bytes)
                .collect { byte value -> String.format('%02x', value & 0xff) }.join()
    }

    private static void deleteTree(final Path directory) {
        if (!Files.exists(directory)) return
        Files.walk(directory).withCloseable { Stream<Path> paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}
