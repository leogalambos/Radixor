package org.egothor.stemmer.benchmark.quality;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable gold-standard equivalence class originating from one dictionary row. */
public record GoldStandardGroup(int rowNumber, List<String> forms) {
    private static final int FIRST_ROW_NUMBER = 1;
    /**
     * Creates a group while removing exact duplicates within this row.
     *
     * @param rowNumber positive physical dictionary row number
     * @param forms supplied forms; encounter order has no metric significance
     * @throws IllegalArgumentException if the row or forms are invalid
     */
    public GoldStandardGroup {
        if (rowNumber < FIRST_ROW_NUMBER) {
            throw new IllegalArgumentException("Dictionary row number must be positive.");
        }
        Objects.requireNonNull(forms, "forms");
        final Set<String> distinct = new LinkedHashSet<>();
        for (String form : forms) {
            if (form == null || form.isEmpty()) {
                throw new IllegalArgumentException("Dictionary group forms must be non-empty strings.");
            }
            distinct.add(form);
        }
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException("A dictionary group must contain at least one usable form.");
        }
        forms = List.copyOf(distinct);
    }
}
