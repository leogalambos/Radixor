package org.egothor.radixor

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

import javax.inject.Inject

/** Declarative configuration for one independently published Radixor model. */
abstract class RadixorModelExtension {
    /** Stable model identifier. */
    abstract Property<String> getModelId()

    /** Radixor language enum constant. */
    abstract Property<String> getLanguage()

    /** Human-readable model name. */
    abstract Property<String> getDisplayName()

    /** Whether this is the documented default for its language. */
    abstract Property<Boolean> getDefaultModel()

    /** Source dictionary name. */
    abstract Property<String> getSourceName()

    /** Source dictionary version or explicit unavailable marker. */
    abstract Property<String> getSourceVersion()

    /** Exact upstream revision or the explicit legacy-import sentinel. */
    abstract Property<String> getSourceRevision()

    /** Upstream source project. */
    abstract Property<String> getSourceProject()

    /** Official upstream repository URL. */
    abstract Property<String> getSourceRepository()

    /** Upstream dataset identity. */
    abstract Property<String> getSourceDataset()

    /** Whether the source revision is recorded or was not recorded by a legacy import. */
    abstract Property<String> getSourceRevisionStatus()

    /** SPDX license identifier. */
    abstract Property<String> getSourceLicense()

    /** Canonical URI for the source-data license. */
    abstract Property<String> getSourceLicenseUri()

    /** Upstream attribution supplied with the source data. */
    abstract Property<String> getSourceAttribution()

    /** Date on which the upstream metadata was verified. */
    abstract Property<String> getSourceVerificationDate()

    /** Material transformations applied by Radixor. */
    abstract Property<String> getTransformationsSummary()

    /** Model-specific data notice input file name, when required. */
    abstract Property<String> getNoticeFileName()

    /** License input file name. */
    abstract Property<String> getLicenseFileName()

    /** Creates the extension. */
    @Inject
    RadixorModelExtension(final ObjectFactory objects) {
        defaultModel.convention(false)
        sourceVersion.convention('unavailable')
        sourceLicense.convention('LicenseRef-Radixor-Stemmer-Data')
        licenseFileName.convention('LICENSE-stemmer-data.txt')
        noticeFileName.convention('NOTICE-model-data.txt')
    }
}
