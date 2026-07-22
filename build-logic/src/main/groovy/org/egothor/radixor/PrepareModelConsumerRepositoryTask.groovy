package org.egothor.radixor

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Stream

/** Builds the isolated Maven-layout repository used by consumer resolution tests. */
abstract class PrepareModelConsumerRepositoryTask extends DefaultTask {
    @Input abstract Property<String> getCoreVersion()
    @Input abstract Property<String> getCatalogVersion()
    @Input abstract MapProperty<String, String> getModelVersions()

    @InputFile @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getCorePom()

    @InputFile @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getCoreJar()

    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getModelPoms()

    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getModelJars()

    @InputFile @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getStandardPom()

    @InputFile @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getBomPom()

    @OutputDirectory
    abstract DirectoryProperty getRepositoryDirectory()

    /** Creates the repository using only declared task state and Java file APIs. */
    @TaskAction
    void prepareRepository() {
        final Path repository = repositoryDirectory.get().asFile.toPath()
        deleteTree(repository)
        Files.createDirectories(repository)
        install(repository, 'radixor', coreVersion.get(), corePom.get().asFile.toPath(), coreJar.get().asFile.toPath())

        final Map<String, Path> pomsByModel = indexModelFiles(modelPoms.files)
        final Map<String, Path> jarsByModel = indexModelFiles(modelJars.files)
        modelVersions.get().toSorted().each { String modelId, String modelVersion ->
            final Path pom = pomsByModel.get(modelId)
            final Path jar = jarsByModel.get(modelId)
            if (pom == null || jar == null) {
                throw new GradleException("Missing generated publication input for model ${modelId}.")
            }
            PrepareModelConsumerRepositoryTask.install(
                    repository, "radixor-model-${modelId}", modelVersion, pom, jar)
        }
        install(repository, 'radixor-models-standard', catalogVersion.get(), standardPom.get().asFile.toPath(), null)
        install(repository, 'radixor-models-bom', catalogVersion.get(), bomPom.get().asFile.toPath(), null)
    }

    private static Map<String, Path> indexModelFiles(final Set<File> files) {
        final Map<String, Path> indexed = [:]
        files.each { File file ->
            Path cursor = file.toPath().toAbsolutePath().parent
            while (cursor != null && cursor.fileName.toString() != 'build') cursor = cursor.parent
            if (cursor == null || cursor.parent == null) {
                throw new GradleException("Cannot determine model ID from generated input ${file}.")
            }
            final String modelId = cursor.parent.fileName.toString()
            if (indexed.put(modelId, file.toPath()) != null) {
                throw new GradleException("Duplicate generated publication input for model ${modelId}.")
            }
        }
        return indexed
    }

    private static void install(final Path repository, final String artifactId, final String version,
            final Path pom, final Path jar) {
        final Path module = repository.resolve("org/egothor/${artifactId}/${version}")
        Files.createDirectories(module)
        Files.copy(pom, module.resolve("${artifactId}-${version}.pom"), StandardCopyOption.REPLACE_EXISTING)
        if (jar != null) {
            Files.copy(jar, module.resolve("${artifactId}-${version}.jar"), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private static void deleteTree(final Path directory) {
        if (!Files.exists(directory)) return
        Files.walk(directory).withCloseable { Stream<Path> paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}
