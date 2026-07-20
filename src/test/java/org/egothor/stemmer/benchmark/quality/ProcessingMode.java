package org.egothor.stemmer.benchmark.quality;

/** Selects the gold-standard groups included in a stemming-quality scenario. */
public enum ProcessingMode {
    /** Includes every parsed dictionary group. */
    ALL_WORDS,
    /** Includes only groups containing no uppercase or titlecase Unicode code point. */
    LOWERCASE_GROUPS_ONLY;

    /**
     * Tests whether a group is eligible for this mode.
     *
     * @param forms distinct word forms in the group, never {@code null}
     * @return {@code true} when the complete group is eligible
     */
    public boolean includes(final Iterable<String> forms) {
        if (this == ALL_WORDS) {
            return true;
        }
        for (String form : forms) {
            int offset = 0;
            while (offset < form.length()) {
                final int codePoint = form.codePointAt(offset);
                if (Character.isUpperCase(codePoint) || Character.isTitleCase(codePoint)) {
                    return false;
                }
                offset += Character.charCount(codePoint);
            }
        }
        return true;
    }
}
