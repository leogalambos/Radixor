package org.egothor.radixor

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Validates one immutable model input without retaining Project state. */
abstract class ValidateModelInputTask extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.RELATIVE) abstract RegularFileProperty getDictionaryFile()
    @InputFile @PathSensitive(PathSensitivity.RELATIVE) abstract RegularFileProperty getVersionFile()
    @Optional @InputFile @PathSensitive(PathSensitivity.RELATIVE) abstract RegularFileProperty getLicenseFile()
    @Optional @InputFile @PathSensitive(PathSensitivity.RELATIVE) abstract RegularFileProperty getNoticeFile()
    @Input abstract Property<String> getModelId()
    @Input abstract Property<String> getModuleName()
    @Input abstract Property<Boolean> getShareAlike()
    @Input abstract MapProperty<String, String> getMetadata()

    /** Performs deterministic metadata, licensing, and streaming dictionary validation. */
    @TaskAction
    void validateInput() {
        final File dictionary = dictionaryFile.get().asFile
        final String id = modelId.get()
        final String version = versionFile.get().asFile.getText('UTF-8').trim()
        if (id != moduleName.get() || !(id ==~ /[a-z]{2}(?:-[a-z]{2})?-[a-z0-9]+(?:-[a-z0-9]+)*/)) {
            throw new GradleException("Model ID '${id}' must equal module '${moduleName.get()}' and use the safe model-ID syntax.")
        }
        if (!(version ==~ /[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?/)) {
            throw new GradleException("Invalid semantic model version '${version}'.")
        }
        final Map<String, String> values = metadata.get()
        values.each { String key, String value ->
            if (value == null || value.isBlank()) throw new GradleException("Required model metadata is missing: ${key}")
        }
        RadixorModelPlugin.validateRevisionMetadata(values['source.revision'], values['source.revisionStatus'])
        if (shareAlike.get()) {
            final File notice = noticeFile.get().asFile
            RadixorModelPlugin.validateShareAlikeNoticeText(notice.getText('UTF-8'), notice.toString(), id,
                    values['source.repository'], values['source.licenseUri'], values['source.revision'],
                    values['source.revisionStatus'])
        } else {
            final String text = licenseFile.get().asFile.getText('UTF-8')
            if (!text.contains('SPDX-License-Identifier: BSD-2-Clause')
                    || !text.contains('Copyright (c) 2016, Marcin Miłkowski')) {
                throw new GradleException('The PoliMorf license must contain the complete BSD-2-Clause text and upstream attribution.')
            }
        }
        RadixorModelPlugin.validateDictionary(dictionary)
    }
}
