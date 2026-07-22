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
    }
}
