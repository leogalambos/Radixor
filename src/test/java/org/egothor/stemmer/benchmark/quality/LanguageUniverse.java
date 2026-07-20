package org.egothor.stemmer.benchmark.quality;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.egothor.stemmer.StemmerPatchTrieLoader.Language;

/** Reconciles bundled dictionary resources with every production language enumeration value. */
record LanguageUniverse(Map<Language, Path> dictionaries, List<String> resourceDirectories,
        List<String> enumerationValues) {
    /** Discovers and validates a one-to-one resource mapping without silent exclusions. */
    static LanguageUniverse discover(final Path resourcesDirectory) throws IOException {
        final Map<String, Path> resources = new HashMap<>();
        try (java.util.stream.Stream<Path> paths = Files.list(resourcesDirectory)) {
            for (Path directory : paths.filter(Files::isDirectory).toList()) {
                final Path dictionary = directory.resolve("stemmer.gz");
                if (Files.isRegularFile(dictionary)) {
                    final Path previous = resources.put(directory.getFileName().toString(), dictionary);
                    if (previous != null) { throw new IOException("Two dictionary resources map to directory " + directory + "."); }
                }
            }
        }
        final Map<Language, Path> mappings = new EnumMap<>(Language.class);
        final Set<String> mappedDirectories = new TreeSet<>();
        for (Language language : Language.values()) {
            final Path dictionary = resources.get(language.resourceDirectory());
            if (dictionary == null) {
                throw new IOException("Enumeration language " + language + " has no stemmer.gz dictionary under "
                        + resourcesDirectory + ".");
            }
            mappings.put(language, dictionary);
            mappedDirectories.add(language.resourceDirectory());
        }
        final Set<String> unmatched = new TreeSet<>(resources.keySet());
        unmatched.removeAll(mappedDirectories);
        if (!unmatched.isEmpty()) {
            throw new IOException("Dictionary resource directories have no StemmerPatchTrieLoader.Language mapping: "
                    + unmatched + ".");
        }
        final List<String> enumValues = new ArrayList<>();
        for (Language language : Language.values()) { enumValues.add(language.name()); }
        return new LanguageUniverse(Map.copyOf(mappings), List.copyOf(new TreeSet<>(resources.keySet())),
                List.copyOf(enumValues));
    }
}
