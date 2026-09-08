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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.stream.Stream

/** Prepares the two POM-only catalog publications for a Maven Central bundle. */
abstract class PrepareModelCatalogBundleInputTask extends DefaultTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getRawRepositoryDirectory()

    @OutputDirectory
    abstract DirectoryProperty getPreparedBundleDirectory()

    @Input
    abstract Property<String> getCatalogVersion()

    /** Copies permitted publication files and creates Central's required legacy checksums. */
    @TaskAction
    void prepare() {
        prepareBundle(rawRepositoryDirectory.get().asFile.toPath(),
                preparedBundleDirectory.get().asFile.toPath(), catalogVersion.get())
    }

    static void prepareBundle(final Path rawRepository, final Path preparedDirectory,
            final String version) {
        if (!Files.isDirectory(rawRepository)) {
            throw new GradleException("The raw model catalog staging repository does not exist: ${rawRepository}.")
        }
        deleteTree(preparedDirectory)
        Files.createDirectories(preparedDirectory)

        final Set<String> expectedPoms = [
                "org/egothor/radixor-models-standard/${version}/radixor-models-standard-${version}.pom",
                "org/egothor/radixor-models-bom/${version}/radixor-models-bom-${version}.pom"
        ] as Set<String>
        final List<Path> copied = []
        Files.walk(rawRepository).withCloseable { Stream<Path> paths ->
            paths.filter(Files::isRegularFile).sorted().forEach { Path source ->
                final String relative = rawRepository.relativize(source).toString().replace(File.separatorChar, '/' as char)
                if (isExcludedPublicationMetadata(relative)) return
                if (relative.endsWith('.jar') || relative.endsWith('/stemmer.gz')
                        || relative.contains('benchmark-pack')) {
                    throw new GradleException("Unsupported model catalog publication file: ${relative}.")
                }
                final String pom = expectedPoms.find { String candidate ->
                    relative == candidate || relative.startsWith(candidate + '.')
                }
                if (pom == null) {
                    throw new GradleException("Unexpected file in the raw model catalog repository: ${relative}.")
                }
                if (relative == pom || relative == pom + '.asc') {
                    final Path target = preparedDirectory.resolve(relative)
                    Files.createDirectories(target.parent)
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                    copied.add(target)
                } else if (!(relative ==~ /.*\.pom(?:\.asc)?\.(?:md5|sha1|sha256|sha512)/)) {
                    throw new GradleException("Unsupported model catalog publication file: ${relative}.")
                }
            }
        }

        final List<Path> poms = copied.findAll { Path path -> path.fileName.toString().endsWith('.pom') }
        if (copied.isEmpty()) {
            throw new GradleException('No model catalog publication files were copied from the raw staging repository.')
        }
        if (poms.size() != 2 || !expectedPoms.every { String expected -> Files.isRegularFile(preparedDirectory.resolve(expected)) }) {
            throw new GradleException("The prepared model catalog must contain exactly the standard and BOM POMs; found ${poms.size()} POM files.")
        }
        copied.each { Path artifact ->
            writeDigest(artifact, 'MD5', artifact.resolveSibling(artifact.fileName.toString() + '.md5'))
            writeDigest(artifact, 'SHA-1', artifact.resolveSibling(artifact.fileName.toString() + '.sha1'))
        }
    }

    private static boolean isExcludedPublicationMetadata(final String relative) {
        final String name = relative.substring(relative.lastIndexOf('/') + 1)
        return name ==~ /maven-metadata.*\.xml(?:\..*)?/ || relative ==~ /.*\.module(?:\..*)?/
    }

    private static void writeDigest(final Path source, final String algorithm, final Path target) {
        final MessageDigest digest = MessageDigest.getInstance(algorithm)
        Files.newInputStream(source).withCloseable { InputStream input ->
            final byte[] buffer = new byte[16 * 1024]
            int count
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        Files.writeString(target, digest.digest().encodeHex().toString(), java.nio.charset.StandardCharsets.US_ASCII)
    }

    private static void deleteTree(final Path directory) {
        if (!Files.exists(directory)) return
        Files.walk(directory).withCloseable { Stream<Path> paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}
