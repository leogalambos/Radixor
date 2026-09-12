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

    /** Whether source identity and output bytes are governed by the pinned UniMorph manifest. */
    abstract Property<Boolean> getManifestManaged()

    /** Whether the model language is conventionally written right-to-left. */
    abstract Property<Boolean> getRightToLeft()

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
        manifestManaged.convention(false)
        rightToLeft.convention(false)
        sourceVersion.convention('unavailable')
        sourceLicense.convention('LicenseRef-Radixor-Stemmer-Data')
        licenseFileName.convention('LICENSE-stemmer-data.txt')
        noticeFileName.convention('NOTICE-model-data.txt')
    }
}
