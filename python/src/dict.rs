// Copyright (C) 2026, Leo Galambos
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// 3. Neither the name of the copyright holder nor the names of its contributors
//    may be used to endorse or promote products derived from this software
//    without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

// Port of StemmerDictionaryParser (Java) — line-oriented, tab-separated dictionary.
//
// Layout: first column = canonical stem, following tab-separated columns = variants.
// Remarks: the earliest occurrence of `#` or `//` terminates the logical line.
// Case: LOWERCASE_WITH_LOCALE_ROOT lowercases the line (locale-independent here).
// Items containing any whitespace character are ignored (Java: Character.isWhitespace).

use flate2::read::GzDecoder;
use std::io::{self, Read};

/// One parsed dictionary entry: a canonical stem and its accepted variants,
/// in encounter order.
pub struct DictEntry {
    pub stem: String,
    pub variants: Vec<String>,
}

/// Decompress gzipped UTF-8 dictionary bytes and parse them into entries.
/// `lowercase` mirrors CaseProcessingMode.LOWERCASE_WITH_LOCALE_ROOT.
#[allow(dead_code)] // public helper; the runtime path decompresses then parse_text
pub fn parse_gz_dict(compressed: &[u8], lowercase: bool) -> io::Result<Vec<DictEntry>> {
    let mut decoder = GzDecoder::new(compressed);
    let mut text = String::new();
    decoder.read_to_string(&mut text)?;
    Ok(parse_text(&text, lowercase))
}

/// Parse an already-decompressed dictionary text.
pub fn parse_text(text: &str, lowercase: bool) -> Vec<DictEntry> {
    let mut entries = Vec::new();

    for raw_line in text.lines() {
        // stripRemark(line).trim(), then lowercase.
        let stripped = strip_remark(raw_line).trim();
        if stripped.is_empty() {
            continue;
        }
        let normalized: String = if lowercase {
            stripped.to_lowercase()
        } else {
            stripped.to_string()
        };
        if normalized.is_empty() {
            continue;
        }

        // split on '\t' keeping trailing empties (Java split("\t", -1)).
        let mut columns = normalized.split('\t');

        let stem = match columns.next() {
            Some(c) => c.trim(),
            None => continue,
        };
        if stem.is_empty() || contains_whitespace(stem) {
            continue;
        }

        let mut variants = Vec::new();
        for col in columns {
            let variant = col.trim();
            if variant.is_empty() || contains_whitespace(variant) {
                continue;
            }
            variants.push(variant.to_string());
        }

        entries.push(DictEntry {
            stem: stem.to_string(),
            variants,
        });
    }

    entries
}

/// Removes a trailing remark: the earliest of `#` or `//` terminates the line.
fn strip_remark(line: &str) -> &str {
    let hash = line.find('#');
    let slash = line.find("//");
    let remark = match (hash, slash) {
        (None, None) => return line,
        (Some(h), None) => h,
        (None, Some(s)) => s,
        (Some(h), Some(s)) => h.min(s),
    };
    &line[..remark]
}

/// Matches Java Character.isWhitespace closely enough for dictionary items.
#[inline]
fn contains_whitespace(item: &str) -> bool {
    item.chars().any(|c| c.is_whitespace())
}
