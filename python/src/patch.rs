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
