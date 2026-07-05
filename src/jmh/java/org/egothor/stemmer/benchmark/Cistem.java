/*******************************************************************************
 * MIT License
 *
 * Copyright (c) 2017 Leonie Weißweiler
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Source: CISTEM German stemmer
 *   Authors: Leonie Weissweiler, Alexander Fraser
 *   https://github.com/LeonieWeissweiler/CISTEM
 *   https://www.cis.lmu.de/~weissweiler/cistem/
 ******************************************************************************/
package org.egothor.stemmer.benchmark;

import java.util.regex.Pattern;

public final class Cistem {

    private static final Pattern GE_PATTERN = Pattern.compile("^ge(.{4,})");
    private static final Pattern DOLLAR1_PATTERN = Pattern.compile("(.)\\1");
    private static final Pattern ND_PATTERN = Pattern.compile("nd$");
    private static final Pattern EMR_PATTERN = Pattern.compile("e[mr]$");
    private static final Pattern T_PATTERN = Pattern.compile("t$");
    private static final Pattern ESN_PATTERN = Pattern.compile("[esn]$");
    private static final Pattern STAR_PATTERN = Pattern.compile("(.)\\*");

    private Cistem() {
    }

    public static String stem(final String word) {
        return stem(word, false);
    }

    public static String stem(final String word, final boolean caseInsensitive) {
        if (word.isEmpty()) {
            return word;
        }

        String normalized = word;
        normalized = normalized.replace("Ü", "U");
        normalized = normalized.replace("Ö", "O");
        normalized = normalized.replace("Ä", "A");
        normalized = normalized.replace("ü", "u");
        normalized = normalized.replace("ö", "o");
        normalized = normalized.replace("ä", "a");

        final boolean uppercase = Character.isUpperCase(normalized.charAt(0));

        normalized = normalized.toLowerCase();
        normalized = normalized.replace("ß", "ss");
        normalized = GE_PATTERN.matcher(normalized).replaceAll("$1");
        normalized = normalized.replace("sch", "$");
        normalized = normalized.replace("ei", "%");
        normalized = normalized.replace("ie", "&");
        normalized = DOLLAR1_PATTERN.matcher(normalized).replaceAll("$1*");

        while (normalized.length() > 3) {
            if (normalized.length() > 5) {
                String newWord = EMR_PATTERN.matcher(normalized).replaceAll("");
                if (!normalized.equals(newWord)) {
                    normalized = newWord;
                    continue;
                }

                newWord = ND_PATTERN.matcher(normalized).replaceAll("");
                if (!normalized.equals(newWord)) {
                    normalized = newWord;
                    continue;
                }
            }

            if (!uppercase || caseInsensitive) {
                final String newWord = T_PATTERN.matcher(normalized).replaceAll("");
                if (!normalized.equals(newWord)) {
                    normalized = newWord;
                    continue;
                }
            }

            final String newWord = ESN_PATTERN.matcher(normalized).replaceAll("");
            if (!normalized.equals(newWord)) {
                normalized = newWord;
            } else {
                break;
            }
        }

        normalized = STAR_PATTERN.matcher(normalized).replaceAll("$1$1");
        normalized = normalized.replace("&", "ie");
        normalized = normalized.replace("%", "ei");
        normalized = normalized.replace("$", "sch");

        return normalized;
    }

    public static String[] segment(final String word) {
        return segment(word, false);
    }

    public static String[] segment(final String word, final boolean caseInsensitive) {
        if (word.isEmpty()) {
            return new String[] {"", ""};
        }

        int restLength = 0;
        final boolean uppercase = Character.isUpperCase(word.charAt(0));
        String normalized = word.toLowerCase();
        final String original = new String(normalized);

        normalized = normalized.replace("sch", "$");
        normalized = normalized.replace("ei", "%");
        normalized = normalized.replace("ie", "&");
        normalized = DOLLAR1_PATTERN.matcher(normalized).replaceAll("$1*");

        while (normalized.length() > 3) {
            if (normalized.length() > 5) {
                String newWord = normalized.replaceAll("e[mr]$", "");
                if (!normalized.equals(newWord)) {
                    restLength += 2;
                    normalized = newWord;
                    continue;
                }

                newWord = normalized.replaceAll("nd$", "");
                if (!normalized.equals(newWord)) {
                    restLength += 2;
                    normalized = newWord;
                    continue;
                }
            }

            if (!uppercase || caseInsensitive) {
                final String newWord = normalized.replaceAll("t$", "");
                if (!normalized.equals(newWord)) {
                    restLength += 1;
                    normalized = newWord;
                    continue;
                }
            }

            final String newWord = normalized.replaceAll("[esn]$", "");
            if (!normalized.equals(newWord)) {
                restLength += 1;
                normalized = newWord;
            } else {
                break;
            }
        }

        normalized = normalized.replaceAll("(.)\\*", "$1$1");
        normalized = normalized.replace("&", "ie");
        normalized = normalized.replace("%", "ei");
        normalized = normalized.replace("$", "sch");

        String rest = "";
        if (restLength != 0) {
            rest = original.substring(original.length() - restLength);
        }

        return new String[] {normalized, rest};
    }
}
