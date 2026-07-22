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

import java.net.URL;
import java.util.Objects;

/**
 * Immutable validated metadata for one independently versioned Radixor model.
 *
 * <p>The descriptor identifies a GZip-compressed Radixor textual dictionary. It
 * does not contain a precompiled trie. Instances are created during deterministic
 * registry discovery and retain the descriptor URL used in diagnostics.</p>
 */
@SuppressWarnings({ "PMD.DataClass", "PMD.ExcessiveParameterList", "PMD.CommentDefaultAccessModifier" })
public final class StemmerModelDescriptor implements Comparable<StemmerModelDescriptor> {
    private final String id;
    private final String version;
    private final StemmerPatchTrieLoader.Language language;
    private final String displayName;
    private final String resource;
    private final boolean defaultModel;
    private final String format;
    private final int formatVersion;
    private final String sha256;
    private final URL source;
    private final ClassLoader classLoader;

    /** Creates a validated immutable descriptor. */
    StemmerModelDescriptor(final String id, final String version, final StemmerPatchTrieLoader.Language language,
            final String displayName, final String resource, final boolean defaultModel, final String format,
            final int formatVersion, final String sha256, final URL source, final ClassLoader classLoader) {
        this.id = Objects.requireNonNull(id, "id");
        this.version = Objects.requireNonNull(version, "version");
        this.language = Objects.requireNonNull(language, "language");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.resource = Objects.requireNonNull(resource, "resource");
        this.defaultModel = defaultModel;
        this.format = Objects.requireNonNull(format, "format");
        this.formatVersion = formatVersion;
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        this.source = Objects.requireNonNull(source, "source");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    /** Returns the stable model identifier used for explicit deterministic selection. */
    public String id() { return this.id; }
    /** Returns the independently managed model artifact version. */
    public String version() { return this.version; }
    /** Returns the represented language. */
    public StemmerPatchTrieLoader.Language language() { return this.language; }
    /** Returns the human-readable model name. */
    public String displayName() { return this.displayName; }
    /** Returns the namespaced classpath dictionary resource. */
    public String resource() { return this.resource; }
    /**
     * Returns whether model metadata declares this model as a default candidate.
     * Language-oriented runtime resolution uses
     * {@link StemmerPatchTrieLoader.Language#defaultModelId()} as its authoritative
     * mapping.
     */
    public boolean isDefaultModel() { return this.defaultModel; }
    /** Returns the dictionary format identifier. */
    public String format() { return this.format; }
    /** Returns the dictionary format version. */
    public int formatVersion() { return this.formatVersion; }
    /** Returns the lowercase SHA-256 digest of the compressed runtime resource bytes. */
    public String sha256() { return this.sha256; }
    /** Returns the descriptor source URL used for diagnostics. */
    public URL source() { return this.source; }
    /** Returns the discovering class loader. */
    ClassLoader classLoader() { return this.classLoader; }

    /** Compares descriptors by stable model identifier. */
    @Override
    public int compareTo(final StemmerModelDescriptor other) { return this.id.compareTo(other.id); }

    /** Returns a concise descriptor representation. */
    @Override
    public String toString() { return this.id + "@" + this.version + " (" + this.source + ")"; }
}
