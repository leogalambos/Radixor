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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Immutable deterministic registry of models discovered from classpath indexes.
 *
 * <p>Discovery enumerates every {@value #INDEX_RESOURCE} visible to the selected
 * class loader, validates the referenced descriptors, sorts them by stable model
 * identifier, and rejects duplicate identifiers. Selection never depends on
 * classpath order. Registry creation validates metadata and resource presence;
 * {@link StemmerPatchTrieLoader} verifies resource bytes when loading a model.</p>
 *
 * <p>Registry creation is not globally cached. Applications should normally
 * discover once for a class-loader scope and retain the immutable result.</p>
 */
@SuppressWarnings({ "PMD.UseProperClassLoader", "PMD.ControlStatementBraces" })
public final class StemmerModelRegistry {
    /** Fixed classpath index name used by every model artifact. */
    public static final String INDEX_RESOURCE = "META-INF/radixor/models.index";
    private static final String FORMAT = "radixor-dictionary-tsv-gzip";
    private static final int FORMAT_VERSION = 1;
    private final Map<String, StemmerModelDescriptor> descriptors;

    /** Creates an immutable registry from already validated descriptors. */
    private StemmerModelRegistry(final Map<String, StemmerModelDescriptor> descriptors) {
        this.descriptors = Collections.unmodifiableMap(new LinkedHashMap<>(descriptors));
    }

    /**
     * Discovers models through the current thread context class loader.
     *
     * <p>If the context loader is {@code null}, the defining class loader of this
     * registry is used.</p>
     *
     * @return immutable registry in stable model-ID order
     * @throws IOException if index or descriptor resources cannot be enumerated or read
     * @throws DuplicateStemmerModelException if two descriptors declare one model ID
     * @throws StemmerModelIntegrityException if an index, descriptor, or declared resource is invalid
     * @throws UnsupportedStemmerModelFormatException if a descriptor uses an unsupported format
     */
    public static StemmerModelRegistry fromContextClassLoader() throws IOException {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return fromClassLoader(classLoader == null ? StemmerModelRegistry.class.getClassLoader() : classLoader);
    }

    /**
     * Discovers and validates every indexed descriptor visible to an explicit class loader.
     *
     * @param classLoader class loader whose indexed model resources are visible
     * @return immutable registry in stable model-ID order
     * @throws NullPointerException if {@code classLoader} is {@code null}
     * @throws IOException if index or descriptor resources cannot be enumerated or read
     * @throws DuplicateStemmerModelException if two descriptors declare one model ID
     * @throws StemmerModelIntegrityException if an index, descriptor, or declared resource is invalid
     * @throws UnsupportedStemmerModelFormatException if a descriptor uses an unsupported format
     */
    public static StemmerModelRegistry fromClassLoader(final ClassLoader classLoader) throws IOException {
        Objects.requireNonNull(classLoader, "classLoader");
        final List<URL> indexes = Collections.list(classLoader.getResources(INDEX_RESOURCE));
        indexes.sort((left, right) -> left.toExternalForm().compareTo(right.toExternalForm()));
        final List<StemmerModelDescriptor> discovered = new ArrayList<>();
        for (URL index : indexes) {
            readIndex(index, classLoader, discovered);
        }
        Collections.sort(discovered);
        final Map<String, StemmerModelDescriptor> byId = new LinkedHashMap<>();
        for (StemmerModelDescriptor descriptor : discovered) {
            final StemmerModelDescriptor previous = byId.putIfAbsent(descriptor.id(), descriptor);
            if (previous != null) {
                throw new DuplicateStemmerModelException("Duplicate model ID '" + descriptor.id() + "' at "
                        + previous.source() + " and " + descriptor.source() + ".");
            }
        }
        return new StemmerModelRegistry(byId);
    }

    /** Returns all descriptors in stable model-identifier order. */
    public List<StemmerModelDescriptor> models() { return List.copyOf(this.descriptors.values()); }

    /**
     * Returns the model with an exact stable identifier.
     *
     * @param modelId exact stable model identifier
     * @return matching descriptor
     * @throws NullPointerException if {@code modelId} is {@code null}
     * @throws StemmerModelNotFoundException if the selected class loader exposes no matching descriptor
     */
    public StemmerModelDescriptor require(final String modelId) {
        Objects.requireNonNull(modelId, "modelId");
        final StemmerModelDescriptor descriptor = this.descriptors.get(modelId);
        if (descriptor == null) {
            throw new StemmerModelNotFoundException("No model '" + modelId + "' is available. Add org.egothor:radixor-model-"
                    + modelId + ":<version> to the runtime classpath.");
        }
        return descriptor;
    }

    /**
     * Returns all models for a language in stable model-identifier order.
     *
     * @param language language to filter
     * @return immutable list, possibly empty
     * @throws NullPointerException if {@code language} is {@code null}
     */
    public List<StemmerModelDescriptor> findByLanguage(final StemmerPatchTrieLoader.Language language) {
        Objects.requireNonNull(language, "language");
        return this.descriptors.values().stream().filter(value -> value.language() == language).toList();
    }

    /**
     * Resolves the language's documented default model without classpath-order fallback.
     *
     * @param language language whose {@link StemmerPatchTrieLoader.Language#defaultModelId()} is required
     * @return exact default-model descriptor
     * @throws NullPointerException if {@code language} is {@code null}
     * @throws StemmerModelNotFoundException if the default model is not visible
     * @throws StemmerModelIntegrityException if the default descriptor declares another language
     */
    public StemmerModelDescriptor requireDefault(final StemmerPatchTrieLoader.Language language) {
        Objects.requireNonNull(language, "language");
        final StemmerModelDescriptor descriptor = this.descriptors.get(language.defaultModelId());
        if (descriptor == null) {
            throw new StemmerModelNotFoundException("No default model '" + language.defaultModelId()
                    + "' is available for language " + language + ". Add org.egothor:radixor-model-"
                    + language.defaultModelId() + ":<version> to the runtime classpath.");
        }
        if (descriptor.language() != language) {
            throw new StemmerModelIntegrityException("Default model '" + descriptor.id() + "' declares language "
                    + descriptor.language() + " instead of " + language + ".");
        }
        return descriptor;
    }

    /** Reads one deterministic index and appends its descriptors. */
    private static void readIndex(final URL index, final ClassLoader classLoader,
            final List<StemmerModelDescriptor> descriptors) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(index.openStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                final String path = line.trim();
                if (path.isEmpty() || path.startsWith("#")) continue;
                if (!path.matches("META-INF/radixor/models/[a-z0-9-]+\\.properties")) {
                    throw new StemmerModelIntegrityException("Malformed model index entry at " + index + ":" + lineNumber + ": " + path);
                }
                final Enumeration<URL> resources = classLoader.getResources(path);
                if (!resources.hasMoreElements()) throw new StemmerModelIntegrityException("Indexed descriptor is missing: " + path + " from " + index);
                while (resources.hasMoreElements()) descriptors.add(readDescriptor(resources.nextElement(), classLoader));
            }
        }
    }

    /** Parses and validates one immutable descriptor. */
    private static StemmerModelDescriptor readDescriptor(final URL source, final ClassLoader classLoader) throws IOException {
        final Properties properties = new Properties();
        try (InputStream input = source.openStream()) { properties.load(input); }
        final String id = required(properties, "model.id", source);
        if (!id.matches("[a-z]{2}(?:-[a-z]{2})?-[a-z0-9]+(?:-[a-z0-9]+)*")) throw new StemmerModelIntegrityException("Invalid model.id '" + id + "' at " + source);
        final String format = required(properties, "model.format", source);
        final int version;
        try { version = Integer.parseInt(required(properties, "model.formatVersion", source)); }
        catch (NumberFormatException exception) { throw new StemmerModelIntegrityException("Invalid model.formatVersion at " + source, exception); }
        if (!FORMAT.equals(format) || version != FORMAT_VERSION) throw new UnsupportedStemmerModelFormatException("Unsupported model format " + format + " version " + version + " at " + source + ".");
        final StemmerPatchTrieLoader.Language language;
        try { language = StemmerPatchTrieLoader.Language.valueOf(required(properties, "model.language", source)); }
        catch (IllegalArgumentException exception) { throw new StemmerModelIntegrityException("Invalid model.language at " + source, exception); }
        final String resource = required(properties, "model.resource", source);
        if (!resource.equals("org/egothor/stemmer/models/" + id + "/stemmer.gz")) throw new StemmerModelIntegrityException("Invalid model.resource for '" + id + "' at " + source);
        if (classLoader.getResource(resource) == null) throw new StemmerModelIntegrityException("Model resource is missing: " + resource + " declared at " + source);
        final String checksum = required(properties, "model.sha256", source);
        if (!checksum.matches("[0-9a-f]{64}")) throw new StemmerModelIntegrityException("Invalid model.sha256 at " + source);
        return new StemmerModelDescriptor(id, required(properties, "model.version", source), language,
                required(properties, "model.displayName", source), resource,
                Boolean.parseBoolean(required(properties, "model.default", source)), format, version, checksum, source, classLoader);
    }

    /** Returns a required nonblank property. */
    private static String required(final Properties properties, final String key, final URL source) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new StemmerModelIntegrityException("Required property '" + key + "' is missing at " + source);
        return value.trim();
    }
}
