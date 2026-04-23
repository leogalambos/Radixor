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

import java.text.Normalizer;
import java.text.Normalizer.Form;

/**
 * Utility that strips diacritics from text for diacritic-insensitive trie
 * storage and lookup.
 */
final class DiacriticStripper {

    /**
     * Direct single-character replacement table.
     */
    private static final char[] DIRECT_REPLACEMENTS = new char[Character.MAX_VALUE + 1];

    static {
        registerSingle("áàâäãåāăąǎȁȃạảấầẩẫậắằẳẵặ", 'a');
        registerSingle("ÁÀÂÄÃÅĀĂĄǍȀȂẠẢẤẦẨẪẬẮẰẲẴẶ", 'A');
        registerSingle("çćĉċč", 'c');
        registerSingle("ÇĆĈĊČ", 'C');
        registerSingle("ďđḍ", 'd');
        registerSingle("ĎĐḌ", 'D');
        registerSingle("éèêëēĕėęěȅȇẹẻẽếềểễệ", 'e');
        registerSingle("ÉÈÊËĒĔĖĘĚȄȆẸẺẼẾỀỂỄỆ", 'E');
        registerSingle("ğĝġģǧ", 'g');
        registerSingle("ĞĜĠĢǦ", 'G');
        registerSingle("ĥħ", 'h');
        registerSingle("ĤĦ", 'H');
        registerSingle("íìîïĩīĭįıǐȉȋịỉ", 'i');
        registerSingle("ÍÌÎÏĨĪĬĮİǏȈȊỊỈ", 'I');
        registerSingle("ĵ", 'j');
        registerSingle("Ĵ", 'J');
        registerSingle("ķǩ", 'k');
        registerSingle("ĶǨ", 'K');
        registerSingle("ĺļľŀł", 'l');
        registerSingle("ĹĻĽĿŁ", 'L');
        registerSingle("ñńņňŉŋ", 'n');
        registerSingle("ÑŃŅŇŊ", 'N');
        registerSingle("óòôöõōŏőǒȍȏọỏốồổỗộớờởỡợø", 'o');
        registerSingle("ÓÒÔÖÕŌŎŐǑȌȎỌỎỐỒỔỖỘỚỜỞỠỢØ", 'O');
        registerSingle("ŕŗř", 'r');
        registerSingle("ŔŖŘ", 'R');
        registerSingle("śŝşšș", 's');
        registerSingle("ŚŜŞŠȘ", 'S');
        registerSingle("ťţŧț", 't');
        registerSingle("ŤŢŦȚ", 'T');
        registerSingle("úùûüũūŭůűųǔȕȗụủứừửữự", 'u');
        registerSingle("ÚÙÛÜŨŪŬŮŰŲǓȔȖỤỦỨỪỬỮỰ", 'U');
        registerSingle("ýÿŷỳỵỷỹ", 'y');
        registerSingle("ÝŶŸỲỴỶỸ", 'Y');
        registerSingle("źżž", 'z');
        registerSingle("ŹŻŽ", 'Z');
        registerSingle("þ", 't');
        registerSingle("Þ", 'T');
    }

    private DiacriticStripper() {
        throw new AssertionError("No instances.");
    }

    /* default */ static String strip(final String input) {
        StringBuilder normalized = null;

        for (int index = 0; index < input.length(); index++) {
            final char source = input.charAt(index);
            final String replacement = replacementFor(source);

            if (replacement == null) {
                if (normalized != null) {
                    normalized.append(source);
                }
                continue;
            }

            if (normalized == null) {
                normalized = new StringBuilder(input.length()); // NOPMD - invariant: only once
                normalized.append(input, 0, index);
            }
            normalized.append(replacement);
        }

        if (normalized == null) {
            return input;
        }
        return normalized.toString();
    }

    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    private static String replacementFor(final char source) {
        if (source <= 0x007F) {
            return null;
        }

        final char mapped = DIRECT_REPLACEMENTS[source];
        if (mapped != '\0') {
            return String.valueOf(mapped);
        }

        if (source == 'ß') {
            return "ss";
        }
        if (source == 'Æ') {
            return "AE";
        }
        if (source == 'æ') {
            return "ae";
        }
        if (source == 'Œ') {
            return "OE";
        }
        if (source == 'œ') {
            return "oe";
        }

        final String decomposed = Normalizer.normalize(String.valueOf(source), Form.NFD);
        final StringBuilder ascii = new StringBuilder(decomposed.length());
        for (int index = 0; index < decomposed.length(); index++) {
            final char part = decomposed.charAt(index);
            if (Character.getType(part) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (part <= 0x007F) {
                ascii.append(part);
            }
        }

        if (ascii.length() == 0) {
            return null;
        }
        return ascii.toString();
    }

    private static void registerSingle(final String sourceCharacters, final char replacement) {
        for (int index = 0; index < sourceCharacters.length(); index++) {
            DIRECT_REPLACEMENTS[sourceCharacters.charAt(index)] = replacement;
        }
    }
}
