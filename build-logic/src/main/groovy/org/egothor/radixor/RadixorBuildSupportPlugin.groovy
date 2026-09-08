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
