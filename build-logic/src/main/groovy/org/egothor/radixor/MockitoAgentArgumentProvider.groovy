package org.egothor.radixor

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.process.CommandLineArgumentProvider

import javax.inject.Inject

abstract class MockitoAgentArgumentProvider implements CommandLineArgumentProvider {
    @Classpath
    abstract ConfigurableFileCollection getAgentClasspath()

    @Inject
    MockitoAgentArgumentProvider() {
    }

    @Override
    Iterable<String> asArguments() {
        return ["-javaagent:${agentClasspath.singleFile.absolutePath}"]
    }
}
