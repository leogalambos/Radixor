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

#[derive(Debug, Clone)]
pub enum PatchCommand {
    Preserve,
    DeleteSuffix(usize),
    DeletePrefix(usize),
    AppendChar(u16),
    PrependChar(u16),
    ReplaceLastChar(u16),
    ReplaceFirstChar(u16),
    BackwardCompound {
        opcodes: Vec<u8>,
        operands: Vec<u32>,
        length_delta: i32,
        min_len: usize,
    },
    ForwardCompound {
        opcodes: Vec<u8>,
        operands: Vec<u32>,
        length_delta: i32,
        min_len: usize,
    },
}

const SKIP: u8 = b'-';
const DELETE: u8 = b'D';
const INSERT: u8 = b'I';
const REPLACE: u8 = b'R';
const NOOP: u8 = b'N';

fn decode_count(arg: u16) -> Option<usize> {
    if arg < b'a' as u16 {
        return None;
    }
    Some((arg - b'a' as u16) as usize + 1)
}

fn compile_operand(opcode: u8, arg: u16) -> Option<u32> {
    match opcode {
        SKIP | DELETE => {
            let count = decode_count(arg)?;
            if count < 1 {
                None
            } else {
                Some(count as u32)
            }
        }
        INSERT | REPLACE => Some(arg as u32),
        NOOP => {
            if arg == b'a' as u16 {
                None
            } else {
                panic!("Invalid NOOP arg")
            }
        }
        _ => panic!("Unknown opcode: {}", opcode as char),
    }
}

fn length_delta(opcodes: &[u8], operands: &[u32]) -> i32 {
    let mut delta: i32 = 0;
    for (i, &op) in opcodes.iter().enumerate() {
        match op {
            DELETE => delta -= operands[i] as i32,
            INSERT => delta += 1,
            _ => {}
        }
    }
    delta
}

fn backward_min_len(opcodes: &[u8], operands: &[u32]) -> usize {
    let mut min_len: usize = 0;
    let mut consumed_from_end: usize = 0;
    for (i, &op) in opcodes.iter().enumerate() {
        let operand = operands[i] as usize;
        match op {
            SKIP => consumed_from_end += operand,
            DELETE => {
                min_len = min_len.max(consumed_from_end + operand);
                consumed_from_end += operand;
            }
            INSERT => {
                min_len = min_len.max(consumed_from_end);
            }
            REPLACE => {
                min_len = min_len.max(consumed_from_end + 1);
                consumed_from_end += 1;
            }
            _ => {}
        }
    }
    min_len
}

fn forward_min_len(opcodes: &[u8], operands: &[u32]) -> usize {
    let mut min_len: usize = 0;
    let mut position: i32 = 0;
    let mut len_delta: i32 = 0;
    for (i, &op) in opcodes.iter().enumerate() {
        let operand = operands[i] as i32;
        match op {
            SKIP => position += operand,
            DELETE => {
                let needed = (position + operand - len_delta).max(0) as usize;
                min_len = min_len.max(needed);
                len_delta -= operand;
            }
            INSERT => {
                let needed = (position - len_delta).max(0) as usize;
                min_len = min_len.max(needed);
                len_delta += 1;
                position += 1;
            }
            REPLACE => {
                let needed = (position + 1 - len_delta).max(0) as usize;
                min_len = min_len.max(needed);
                position += 1;
            }
            _ => {}
        }
    }
    min_len
}

/// Locate the UTF-8 byte boundary left after deleting `utf16_units` from
/// the end of `source`. Returns `None` if the requested boundary would split
/// a Unicode scalar value.
#[inline]
fn utf8_suffix_boundary(source: &str, utf16_units: usize) -> Option<usize> {
    if source.is_ascii() {
        return source.len().checked_sub(utf16_units);
    }

    let mut remaining = utf16_units;
    for (index, ch) in source.char_indices().rev() {
        let width = ch.len_utf16();
        if width > remaining {
            return None;
        }
        remaining -= width;
        if remaining == 0 {
            return Some(index);
        }
    }
    None
}

/// Locate the UTF-8 byte boundary right after deleting `utf16_units` from
/// the beginning of `source`. Returns `None` if the requested boundary would
/// split a Unicode scalar value.
#[inline]
fn utf8_prefix_boundary(source: &str, utf16_units: usize) -> Option<usize> {
    if source.is_ascii() {
        return (utf16_units <= source.len()).then_some(utf16_units);
    }

    let mut remaining = utf16_units;
    for (index, ch) in source.char_indices() {
        let width = ch.len_utf16();
        if width > remaining {
            return None;
        }
        remaining -= width;
        if remaining == 0 {
            return Some(index + ch.len_utf8());
        }
    }
    None
}

/// Append one UTF-16 code unit using the same lossy decoding semantics as the
/// generic UTF-16 output path.
#[inline]
fn push_utf16_unit_lossy(out: &mut String, unit: u16) {
    let decoded = char::decode_utf16(std::iter::once(unit))
        .next()
        .expect("single UTF-16 unit iterator must yield one result")
        .unwrap_or('\u{FFFD}');
    out.push(decoded);
}

/// Return whether `unit` is a UTF-16 surrogate code unit.
#[inline]
fn is_utf16_surrogate(unit: u16) -> bool {
    (0xD800..=0xDFFF).contains(&unit)
}

impl PatchCommand {
    pub fn parse(patch: &str, backward: bool) -> Self {
        let chars: Vec<u16> = patch.encode_utf16().collect();
        let len = chars.len();
        if len == 0 || len & 1 != 0 {
            return PatchCommand::Preserve;
        }

        if len == 2 {
            let opcode = chars[0] as u8;
            let arg = chars[1];
            return Self::compile_single(opcode, arg, backward);
        }

        let op_count = len / 2;
        let mut opcodes = Vec::with_capacity(op_count);
        let mut operands = Vec::with_capacity(op_count);

        for i in 0..op_count {
            let opcode = chars[i * 2] as u8;
            let arg = chars[i * 2 + 1];
            match compile_operand(opcode, arg) {
                None => return PatchCommand::Preserve,
                Some(operand) => {
                    opcodes.push(opcode);
                    operands.push(operand);
                }
            }
        }

        let ld = length_delta(&opcodes, &operands);
        if backward {
            let min_len = backward_min_len(&opcodes, &operands);
            PatchCommand::BackwardCompound {
                opcodes,
                operands,
                length_delta: ld,
                min_len,
            }
        } else {
            let min_len = forward_min_len(&opcodes, &operands);
            PatchCommand::ForwardCompound {
                opcodes,
                operands,
                length_delta: ld,
                min_len,
            }
        }
    }

    fn compile_single(opcode: u8, arg: u16, backward: bool) -> Self {
        match opcode {
            DELETE => {
                let count = match decode_count(arg) {
                    Some(c) if c >= 1 => c,
                    _ => return PatchCommand::Preserve,
                };
                if backward {
                    PatchCommand::DeleteSuffix(count)
                } else {
                    PatchCommand::DeletePrefix(count)
                }
            }
            INSERT => {
                if backward {
                    PatchCommand::AppendChar(arg)
                } else {
                    PatchCommand::PrependChar(arg)
                }
            }
            REPLACE => {
                if backward {
                    PatchCommand::ReplaceLastChar(arg)
                } else {
                    PatchCommand::ReplaceFirstChar(arg)
                }
            }
            SKIP | NOOP => PatchCommand::Preserve,
            _ => panic!("Unknown opcode: {}", opcode as char),
        }
    }

    fn computed_length(&self, src_len: usize) -> usize {
        let (ld, min_len) = match self {
            PatchCommand::Preserve => (0i32, 0usize),
            PatchCommand::DeleteSuffix(n) | PatchCommand::DeletePrefix(n) => (-(*n as i32), 0),
            PatchCommand::AppendChar(_) | PatchCommand::PrependChar(_) => (1, 0),
            PatchCommand::ReplaceLastChar(_) | PatchCommand::ReplaceFirstChar(_) => (0, 1),
            PatchCommand::BackwardCompound {
                length_delta,
                min_len,
                ..
            } => (*length_delta, *min_len),
            PatchCommand::ForwardCompound {
                length_delta,
                min_len,
                ..
            } => (*length_delta, *min_len),
        };
        if src_len < min_len {
            return src_len;
        }
        let applied = src_len as i32 + ld;
        if applied < 1 {
            src_len
        } else {
            applied as usize
        }
    }

    /// Return a borrowed UTF-8 source slice when this command can be applied
    /// without constructing an intermediate UTF-16 output buffer.
    ///
    /// The caller must guarantee that the encoded trie key represents the
    /// original source text without case or diacritic normalization. The
    /// supplied `src_utf16_len` is the UTF-16 code-unit length of that key.
    /// Commands whose result is not a contiguous source slice return `None`.
    /// A deletion that would be rejected by the normal patch length rules
    /// returns the complete source, exactly matching [`Self::apply_into`].
    #[inline]
    pub fn source_slice_utf8<'a>(
        &self,
        source: &'a str,
        src_utf16_len: usize,
    ) -> Option<&'a str> {
        match self {
            PatchCommand::Preserve => Some(source),
            PatchCommand::DeleteSuffix(count) => {
                let out_len = self.computed_length(src_utf16_len);
                if out_len >= src_utf16_len {
                    return Some(source);
                }
                utf8_suffix_boundary(source, *count).map(|end| &source[..end])
            }
            PatchCommand::DeletePrefix(count) => {
                let out_len = self.computed_length(src_utf16_len);
                if out_len >= src_utf16_len {
                    return Some(source);
                }
                utf8_prefix_boundary(source, *count).map(|start| &source[start..])
            }
            _ => None,
        }
    }

    /// Apply a simple non-slice patch directly to UTF-8 output.
    ///
    /// The caller must guarantee that the trie key represents the original
    /// source text without case or diacritic normalization. This method mirrors
    /// the UTF-16 patch semantics while avoiding construction and decoding of a
    /// second UTF-16 buffer. It returns `false` when exact UTF-16 replacement
    /// semantics would require manipulating half of a supplementary scalar; the
    /// caller must then use the generic UTF-16 implementation.
    #[inline]
    pub fn apply_simple_utf8_into(
        &self,
        source: &str,
        src_utf16_len: usize,
        out: &mut String,
    ) -> bool {
        match self {
            PatchCommand::AppendChar(unit) => {
                out.clear();
                out.reserve(source.len().saturating_add(3));
                out.push_str(source);
                push_utf16_unit_lossy(out, *unit);
                true
            }
            PatchCommand::PrependChar(unit) => {
                out.clear();
                out.reserve(source.len().saturating_add(3));
                push_utf16_unit_lossy(out, *unit);
                out.push_str(source);
                true
            }
            PatchCommand::ReplaceLastChar(unit) => {
                if src_utf16_len == 0 {
                    out.clear();
                    out.push_str(source);
                    return true;
                }
                if is_utf16_surrogate(*unit) {
                    return false;
                }
                let Some((start, last)) = source.char_indices().next_back() else {
                    return false;
                };
                if last.len_utf16() != 1 {
                    return false;
                }
                out.clear();
                out.reserve(source.len().saturating_sub(last.len_utf8()).saturating_add(3));
                out.push_str(&source[..start]);
                push_utf16_unit_lossy(out, *unit);
                true
            }
            PatchCommand::ReplaceFirstChar(unit) => {
                if src_utf16_len == 0 {
                    out.clear();
                    out.push_str(source);
                    return true;
                }
                if is_utf16_surrogate(*unit) {
                    return false;
                }
                let Some((_, first)) = source.char_indices().next() else {
                    return false;
                };
                if first.len_utf16() != 1 {
                    return false;
                }
                out.clear();
                out.reserve(source.len().saturating_sub(first.len_utf8()).saturating_add(3));
                push_utf16_unit_lossy(out, *unit);
                out.push_str(&source[first.len_utf8()..]);
                true
            }
            _ => false,
        }
    }

    /// Return a stable diagnostic signature for a backward compound patch.
    ///
    /// Delete/skip operands are emitted as UTF-16-unit counts. Insert/replace
    /// operands are emitted as hexadecimal UTF-16 code units. The method is
    /// diagnostic-only and is never called by the normal stemming hot path.
    pub fn backward_compound_signature(&self) -> Option<String> {
        let PatchCommand::BackwardCompound {
            opcodes, operands, ..
        } = self
        else {
            return None;
        };

        let mut signature = String::new();
        for (index, (&opcode, &operand)) in opcodes.iter().zip(operands.iter()).enumerate() {
            if index != 0 {
                signature.push(',');
            }
            signature.push(opcode as char);
            signature.push(':');
            match opcode {
                SKIP | DELETE => signature.push_str(&operand.to_string()),
                INSERT | REPLACE => signature.push_str(&format!("{operand:04X}")),
                NOOP => signature.push('0'),
                _ => signature.push('?'),
            }
        }
        Some(signature)
    }

    pub fn apply(&self, source: &[u16]) -> Vec<u16> {
        let mut out = Vec::new();
        self.apply_into(source, &mut out);
        out
    }

    /// Apply the patch into a caller-owned buffer, avoiding a per-call
    /// allocation on the hot path. `out` is cleared and overwritten.
    pub fn apply_into(&self, source: &[u16], out: &mut Vec<u16>) {
        let src_len = source.len();
        let out_len = self.computed_length(src_len);
        out.clear();
        match self {
            PatchCommand::Preserve => out.extend_from_slice(source),
            PatchCommand::DeleteSuffix(_) => {
                if out_len < src_len {
                    out.extend_from_slice(&source[..out_len]);
                } else {
                    out.extend_from_slice(source);
                }
            }
            PatchCommand::DeletePrefix(n) => {
                if out_len < src_len {
                    out.extend_from_slice(&source[*n..]);
                } else {
                    out.extend_from_slice(source);
                }
            }
            PatchCommand::AppendChar(ch) => {
                out.extend_from_slice(source);
                out.push(*ch);
            }
            PatchCommand::PrependChar(ch) => {
                out.push(*ch);
                out.extend_from_slice(source);
            }
            PatchCommand::ReplaceLastChar(ch) => {
                out.extend_from_slice(source);
                if src_len != 0 {
                    let l = out.len();
                    out[l - 1] = *ch;
                }
            }
            PatchCommand::ReplaceFirstChar(ch) => {
                out.extend_from_slice(source);
                if src_len != 0 {
                    out[0] = *ch;
                }
            }
            PatchCommand::BackwardCompound {
                opcodes, operands, ..
            } => {
                if src_len < self.min_len_for_compound() || out_len < 1 {
                    out.extend_from_slice(source);
                } else {
                    apply_backward_into(opcodes, operands, source, out_len, out);
                }
            }
            PatchCommand::ForwardCompound {
                opcodes, operands, ..
            } => {
                if src_len < self.min_len_for_compound() || out_len < 1 {
                    out.extend_from_slice(source);
                } else {
                    apply_forward_into(opcodes, operands, source, out_len, out);
                }
            }
        }
    }

    fn min_len_for_compound(&self) -> usize {
        match self {
            PatchCommand::BackwardCompound { min_len, .. } => *min_len,
            PatchCommand::ForwardCompound { min_len, .. } => *min_len,
            _ => 0,
        }
    }
}

fn fill_with_source(out: &mut Vec<u16>, source: &[u16]) {
    out.clear();
    out.extend_from_slice(source);
}

fn apply_backward_into(
    opcodes: &[u8],
    operands: &[u32],
    source: &[u16],
    produced_len: usize,
    out: &mut Vec<u16>,
) {
    let src_len = source.len();
    out.clear();
    out.resize(produced_len, 0);
    let mut current_len = src_len as i32;
    let mut position = src_len as i32 - 1;
    let mut src_end = src_len as i32;
    let mut out_end = produced_len as i32;

    for (i, &op) in opcodes.iter().enumerate() {
        let operand = operands[i] as i32;
        match op {
            SKIP => {
                let skip = operand.min(src_end);
                src_end -= skip;
                out_end -= skip;
                if out_end < 0 {
                    return fill_with_source(out, source);
                }
                let s = src_end as usize;
                let o = out_end as usize;
                out[o..o + skip as usize].copy_from_slice(&source[s..s + skip as usize]);
                position = position - operand + 1;
            }
            DELETE => {
                let del_end_excl = position + 1;
                position -= operand - 1;
                if position < 0 || position > current_len || position > del_end_excl {
                    return fill_with_source(out, source);
                }
                let deleted = (del_end_excl.min(current_len) - position) as i32;
                if src_end < deleted {
                    return fill_with_source(out, source);
                }
                src_end -= deleted;
                current_len -= deleted;
            }
            INSERT => {
                if position < -1 || position >= current_len || out_end <= 0 {
                    return fill_with_source(out, source);
                }
                out_end -= 1;
                out[out_end as usize] = operand as u16;
                current_len += 1;
                position += 1;
            }
            REPLACE => {
                if position < 0 || position >= current_len || src_end <= 0 || out_end <= 0 {
                    return fill_with_source(out, source);
                }
                src_end -= 1;
                out_end -= 1;
                out[out_end as usize] = operand as u16;
            }
            _ => return fill_with_source(out, source),
        }
        position -= 1;
    }

    if src_end != out_end {
        return fill_with_source(out, source);
    }
    let prefix_len = src_end as usize;
    out[..prefix_len].copy_from_slice(&source[..prefix_len]);
}

fn apply_forward_into(
    opcodes: &[u8],
    operands: &[u32],
    source: &[u16],
    produced_len: usize,
    out: &mut Vec<u16>,
) {
    let src_len = source.len();
    out.clear();
    out.resize(produced_len, 0);
    let mut current_len = src_len as i32;
    let mut position: i32 = 0;
    let mut src_idx: i32 = 0;
    let mut out_idx: i32 = 0;

    for (i, &op) in opcodes.iter().enumerate() {
        let operand = operands[i] as i32;
        match op {
            SKIP => {
                let skip = operand.min(src_len as i32 - src_idx);
                let s = src_idx as usize;
                let o = out_idx as usize;
                out[o..o + skip as usize].copy_from_slice(&source[s..s + skip as usize]);
                src_idx += skip;
                out_idx += skip;
                position = position + operand - 1;
            }
            DELETE => {
                if position < 0 || position > current_len {
                    return fill_with_source(out, source);
                }
                let del_len = operand.min(current_len - position);
                if src_idx + del_len > src_len as i32 {
                    return fill_with_source(out, source);
                }
                src_idx += del_len;
                current_len -= del_len;
                position -= 1;
            }
            INSERT => {
                if position < 0 || position > current_len || out_idx >= produced_len as i32 {
                    return fill_with_source(out, source);
                }
                out[out_idx as usize] = operand as u16;
                out_idx += 1;
                current_len += 1;
            }
            REPLACE => {
                if position < 0
                    || position >= current_len
                    || src_idx >= src_len as i32
                    || out_idx >= produced_len as i32
                {
                    return fill_with_source(out, source);
                }
                src_idx += 1;
                out[out_idx as usize] = operand as u16;
                out_idx += 1;
            }
            _ => return fill_with_source(out, source),
        }
        position += 1;
    }

    let remaining = (src_len as i32 - src_idx) as usize;
    if remaining > produced_len - out_idx as usize {
        return fill_with_source(out, source);
    }
    let o = out_idx as usize;
    let s = src_idx as usize;
    out[o..o + remaining].copy_from_slice(&source[s..s + remaining]);
    if out_idx as usize + remaining != produced_len {
        fill_with_source(out, source);
    }
}


#[cfg(test)]
mod tests {
    use super::PatchCommand;

    fn legacy_result(command: &PatchCommand, source: &str) -> String {
        let source_utf16: Vec<u16> = source.encode_utf16().collect();
        let mut output = Vec::new();
        command.apply_into(&source_utf16, &mut output);
        String::from_utf16_lossy(&output)
    }

    #[test]
    fn source_slice_preserve_matches_legacy_result() {
        let source = "Příliš";
        let command = PatchCommand::Preserve;
        let source_utf16_len = source.encode_utf16().count();
        assert_eq!(command.source_slice_utf8(source, source_utf16_len), Some(source));
        assert_eq!(legacy_result(&command, source), source);
    }

    #[test]
    fn source_slice_delete_suffix_handles_ascii() {
        let source = "running";
        let command = PatchCommand::DeleteSuffix(3);
        let source_utf16_len = source.encode_utf16().count();
        let fast = command.source_slice_utf8(source, source_utf16_len).unwrap();
        assert_eq!(fast, "runn");
        assert_eq!(fast, legacy_result(&command, source));
    }

    #[test]
    fn source_slice_delete_suffix_handles_bmp_unicode() {
        let source = "kočky";
        let command = PatchCommand::DeleteSuffix(1);
        let source_utf16_len = source.encode_utf16().count();
        let fast = command.source_slice_utf8(source, source_utf16_len).unwrap();
        assert_eq!(fast, "kočk");
        assert_eq!(fast, legacy_result(&command, source));
    }

    #[test]
    fn source_slice_delete_prefix_handles_bmp_unicode() {
        let source = "český";
        let command = PatchCommand::DeletePrefix(1);
        let source_utf16_len = source.encode_utf16().count();
        let fast = command.source_slice_utf8(source, source_utf16_len).unwrap();
        assert_eq!(fast, "eský");
        assert_eq!(fast, legacy_result(&command, source));
    }

    #[test]
    fn source_slice_rejected_deletion_preserves_source() {
        let source = "a";
        let command = PatchCommand::DeleteSuffix(1);
        let source_utf16_len = source.encode_utf16().count();
        assert_eq!(command.source_slice_utf8(source, source_utf16_len), Some(source));
        assert_eq!(legacy_result(&command, source), source);
    }

    #[test]
    fn source_slice_falls_back_when_utf16_boundary_splits_scalar() {
        let source = "a😀b";
        let command = PatchCommand::DeleteSuffix(2);
        let source_utf16_len = source.encode_utf16().count();
        assert_eq!(command.source_slice_utf8(source, source_utf16_len), None);
    }

    #[test]
    fn source_slice_handles_complete_supplementary_scalar_deletion() {
        let source = "a😀b";
        let command = PatchCommand::DeleteSuffix(3);
        let source_utf16_len = source.encode_utf16().count();
        let fast = command.source_slice_utf8(source, source_utf16_len).unwrap();
        assert_eq!(fast, "a");
        assert_eq!(fast, legacy_result(&command, source));
    }

    #[test]
    fn source_slice_returns_none_for_non_slice_patch() {
        let source = "cars";
        let command = PatchCommand::AppendChar(b'x' as u16);
        let source_utf16_len = source.encode_utf16().count();
        assert_eq!(command.source_slice_utf8(source, source_utf16_len), None);
    }

    #[test]
    fn direct_utf8_append_matches_legacy_result() {
        let source = "kočk";
        let command = PatchCommand::AppendChar('a' as u16);
        let mut fast = String::new();
        assert!(command.apply_simple_utf8_into(source, source.encode_utf16().count(), &mut fast));
        assert_eq!(fast, legacy_result(&command, source));
    }

    #[test]
    fn direct_utf8_prepend_matches_legacy_result() {
        let source = "eský";
        let command = PatchCommand::PrependChar('č' as u16);
        let mut fast = String::new();
        assert!(command.apply_simple_utf8_into(source, source.encode_utf16().count(), &mut fast));
        assert_eq!(fast, legacy_result(&command, source));
    }

    #[test]
    fn direct_utf8_replace_last_matches_legacy_result() {
        let source = "koty";
        let command = PatchCommand::ReplaceLastChar('a' as u16);
        let mut fast = String::new();
        assert!(command.apply_simple_utf8_into(source, source.encode_utf16().count(), &mut fast));
        assert_eq!(fast, legacy_result(&command, source));
    }

    #[test]
    fn direct_utf8_replace_first_matches_legacy_result() {
        let source = "ceský";
        let command = PatchCommand::ReplaceFirstChar('č' as u16);
        let mut fast = String::new();
        assert!(command.apply_simple_utf8_into(source, source.encode_utf16().count(), &mut fast));
        assert_eq!(fast, legacy_result(&command, source));
    }

    #[test]
    fn direct_utf8_replace_last_falls_back_for_supplementary_scalar() {
        let source = "a😀";
        let command = PatchCommand::ReplaceLastChar('x' as u16);
        let mut fast = String::new();
        assert!(!command.apply_simple_utf8_into(source, source.encode_utf16().count(), &mut fast));
    }

    #[test]
    fn direct_utf8_replace_first_falls_back_for_supplementary_scalar() {
        let source = "😀a";
        let command = PatchCommand::ReplaceFirstChar('x' as u16);
        let mut fast = String::new();
        assert!(!command.apply_simple_utf8_into(source, source.encode_utf16().count(), &mut fast));
    }

    #[test]
    fn direct_utf8_append_surrogate_matches_lossy_legacy_result() {
        let source = "a";
        let command = PatchCommand::AppendChar(0xD800);
        let mut fast = String::new();
        assert!(command.apply_simple_utf8_into(source, source.encode_utf16().count(), &mut fast));
        assert_eq!(fast, legacy_result(&command, source));
    }

    #[test]
    fn backward_compound_signature_is_stable() {
        let command = PatchCommand::BackwardCompound {
            opcodes: vec![b'D', b'R', b'I'],
            operands: vec![2, 'a' as u32, 'x' as u32],
            length_delta: 0,
            min_len: 2,
        };
        assert_eq!(command.backward_compound_signature().as_deref(), Some("D:2,R:0061,I:0078"));
    }
}
