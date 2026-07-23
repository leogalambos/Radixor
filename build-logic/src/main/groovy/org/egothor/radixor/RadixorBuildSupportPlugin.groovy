package org.egothor.radixor

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Exposes typed repository build-support tasks to the root build. */
final class RadixorBuildSupportPlugin implements Plugin<Project> {
    /** Registers build-support tasks without inspecting project state during execution. */
    @Override
    void apply(final Project project) {
        project.tasks.register('prepareModelConsumerTestRepository', PrepareModelConsumerRepositoryTask) {
            group = 'verification'
            description = 'Creates an isolated local Maven repository for model dependency-resolution integration tests.'
        }
        project.tasks.register('prepareModelCatalogReleaseCandidate', PrepareModelCatalogBundleInputTask) {
            group = 'publishing'
            description = 'Prepares the isolated POM-only model catalog input for Maven Central.'
        }
        project.tasks.register('verifyModelCatalogReleaseCandidate', VerifyModelCatalogReleaseCandidateTask) {
            group = 'verification'
            description = 'Verifies catalog bundle contents, checksums, coordinates, and dependency semantics.'
        }
    }
}
