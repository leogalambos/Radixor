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
package org.egothor.stemmer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies consumer-visible dependency resolution from the isolated local Maven repository.
 */
@Tag("integration")
@DisplayName("Published model dependency topology")
class ModelDependencyResolutionTest {

    /** The temporary directory used for isolated Gradle consumer builds. */
    @TempDir
    Path temporaryDirectory;

    /**
     * Verifies that the standard aggregate resolves all defaults and excludes optional PoliMorf.
     *
     * @throws IOException if the consumer fixture cannot be created or read
     */
    @Test
    @DisplayName("Standard aggregate resolves twenty defaults and excludes PoliMorf")
    void standardAggregateResolvesDefaultsOnly() throws IOException {
        Set<String> artifacts = resolve("""
                implementation 'org.egothor:radixor:%s'
                runtimeOnly 'org.egothor:radixor-models-standard:%s'
                """.formatted(coreVersion(), catalogVersion()));

        assertTrue(artifacts.contains("radixor"));
        assertTrue(artifacts.contains("radixor-model-pl-pl-unimorph"));
        assertFalse(artifacts.contains("radixor-model-pl-pl-polimorf"));
        assertEquals(21, artifacts.size(), "The core and twenty default model JARs must resolve.");
    }

    /**
     * Verifies that importing the model BOM alone introduces no model artifact.
     *
     * @throws IOException if the consumer fixture cannot be created or read
     */
    @Test
    @DisplayName("BOM alone contributes constraints but no model JAR")
    void bomAloneIntroducesNoModels() throws IOException {
        Set<String> artifacts = resolve("implementation platform('org.egothor:radixor-models-bom:"
                + catalogVersion() + "')");

        assertTrue(artifacts.isEmpty(), "A dependency-management BOM must not add runtime artifacts.");
    }

    /**
     * Verifies that the BOM supplies the source-controlled PoliMorf model version.
     *
     * @throws IOException if the consumer fixture cannot be created or read
     */
    @Test
    @DisplayName("BOM manages an explicitly requested PoliMorf model")
    void bomManagesExplicitPolimorf() throws IOException {
        Set<String> artifacts = resolve("""
                implementation platform('org.egothor:radixor-models-bom:%s')
                runtimeOnly 'org.egothor:radixor-model-pl-pl-polimorf'
                """.formatted(catalogVersion()));

        assertEquals(Set.of("radixor-model-pl-pl-polimorf"), artifacts);
    }

    /**
     * Verifies that the root core publication has no transitive model dependency.
     *
     * @throws IOException if the consumer fixture cannot be created or read
     */
    @Test
    @DisplayName("Core publication resolves without model artifacts")
    void coreHasNoTransitiveModels() throws IOException {
        Set<String> artifacts = resolve("implementation 'org.egothor:radixor:" + coreVersion() + "'");

        assertEquals(Set.of("radixor"), artifacts);
    }

    /**
     * Executes an isolated offline Gradle consumer build and returns resolved artifact identifiers.
     *
     * @param dependencyDeclarations Gradle dependency declarations for the fixture
     * @return deterministically ordered resolved Maven artifact identifiers
     * @throws IOException if fixture files cannot be created or read
     */
    private Set<String> resolve(final String dependencyDeclarations) throws IOException {
        Path fixtureDirectory = Files.createTempDirectory(temporaryDirectory, "consumer-");
        Path repository = Path.of(requiredProperty("radixor.consumer.repository"));
        Files.writeString(fixtureDirectory.resolve("settings.gradle"), "rootProject.name = 'consumer'\n",
                StandardCharsets.UTF_8);
        Files.writeString(fixtureDirectory.resolve("build.gradle"), """
                plugins { id 'java' }
                repositories { maven { url = uri('%s') } }
                dependencies {
                %s
                }
                tasks.register('resolveRuntime') {
                    doLast {
                        def names = configurations.runtimeClasspath.resolvedConfiguration.resolvedArtifacts
                                .collect { it.moduleVersion.id.name }.toSorted()
                        file('resolved.txt').text = names.join('\\n') + (names.isEmpty() ? '' : '\\n')
                    }
                }
                """.formatted(repository.toUri(), dependencyDeclarations), StandardCharsets.UTF_8);

        BuildResult result = GradleRunner.create()
                .withProjectDir(fixtureDirectory.toFile())
                .withArguments("--offline", "--stacktrace", "resolveRuntime")
                .build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":resolveRuntime").getOutcome());
        Path resultFile = fixtureDirectory.resolve("resolved.txt");
        List<String> lines = Files.exists(resultFile)
                ? Files.readAllLines(resultFile, StandardCharsets.UTF_8)
                : List.of();
        return new TreeSet<>(lines);
    }

    /**
     * Returns the root core version supplied by the Gradle test task.
     *
     * @return current root core version
     */
    private static String coreVersion() {
        return requiredProperty("radixor.core.version");
    }

    /**
     * Returns the model catalog version supplied by the Gradle test task.
     *
     * @return current model catalog version
     */
    private static String catalogVersion() {
        return requiredProperty("radixor.catalog.version");
    }

    /**
     * Returns a required system property or fails with an actionable diagnostic.
     *
     * @param name system-property name
     * @return nonblank property value
     */
    private static String requiredProperty(final String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required test system property is missing: " + name);
        }
        return value;
    }
}
