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

// Port of PatchCommandEncoder (Java) — DP-based minimum-cost edit script.
// Costs: insert=1, delete=1, replace=1, match=0, mismatch_penalty=100.
// Produces compact opcode strings: D(elete), I(nsert), R(eplace), -(skip), N(oop).
// Count argument: 'a' + count - 1  (i.e., COUNT_SENTINEL = 'a' - 1 = 96).

const MISMATCH_PENALTY: i32 = 100;
const COUNT_SENTINEL: u16 = b'a' as u16 - 1; // 96 = 0x60

#[derive(Clone, Copy, PartialEq)]
enum Trace {
    Delete,
    Insert,
    Replace,
    Match,
}

/// Encode the patch command that transforms `source` (UTF-16 slice) into `target`.
/// Returns "Na" when source == target.
pub fn encode_patch(source: &[u16], target: &[u16], backward: bool) -> String {
    if source == target {
        return "Na".to_string();
    }
    if backward {
        encode_backward(source, target)
    } else {
        encode_forward(source, target)
    }
}

// Backward traversal encoding.

fn encode_backward(source: &[u16], target: &[u16]) -> String {
    let src_len = source.len();
    let tgt_len = target.len();
    let cols = tgt_len + 1;

    let mut cost = vec![0i32; (src_len + 1) * cols];
    let mut trace = vec![Trace::Match; (src_len + 1) * cols];

    let idx = |r: usize, c: usize| r * cols + c;

    // Boundary conditions (Egothor backward: rows=source, cols=target)
    for i in 1..=src_len {
        cost[idx(i, 0)] = i as i32;
        trace[idx(i, 0)] = Trace::Delete;
    }
    for j in 1..=tgt_len {
        cost[idx(0, j)] = j as i32;
        trace[idx(0, j)] = Trace::Insert;
    }

    // Fill left-to-right, top-to-bottom (sourceIndex 1..=srcLen, targetIndex 1..=tgtLen)
    for si in 1..=src_len {
        let src_ch = source[si - 1]; // sourceCharacters[sourceIndex + sourceCharacterOffset=-1]
        for ti in 1..=tgt_len {
            let tgt_ch = target[ti - 1];

            // sourceNeighbor = sourceIndex - 1, targetNeighbor = targetIndex - 1
            let del = cost[idx(si - 1, ti)] + 1; // DELETE from [si-1][ti]
            let ins = cost[idx(si, ti - 1)] + 1; // INSERT from [si][ti-1]
            let diag = cost[idx(si - 1, ti - 1)];
            let rep = diag + 1;
            let mat = diag
                + if src_ch == tgt_ch {
                    0
                } else {
                    MISMATCH_PENALTY
                };

            // Priority: MATCH (baseline), then DELETE (<=), INSERT (<), REPLACE (<)
            let mut best = mat;
            let mut bt = Trace::Match;
            if del <= best {
                best = del;
                bt = Trace::Delete;
            }
            if ins < best {
                best = ins;
                bt = Trace::Insert;
            }
            if rep < best {
                bt = Trace::Replace;
            }
            let _ = best;

            cost[idx(si, ti)] = if bt == Trace::Replace {
                rep
            } else if bt == Trace::Insert {
                ins
            } else if bt == Trace::Delete {
                del
            } else {
                mat
            };
            trace[idx(si, ti)] = bt;
        }
    }

    build_patch_backward(&trace, target, cols, src_len, tgt_len)
}

fn build_patch_backward(
    trace: &[Trace],
    target: &[u16],
    cols: usize,
    src_len: usize,
    tgt_len: usize,
) -> String {
    let idx = |r: usize, c: usize| r * cols + c;

    let mut patch = String::new();
    let mut pending_deletes: u16 = COUNT_SENTINEL;
    let mut pending_skips: u16 = COUNT_SENTINEL;

    let mut si = src_len;
    let mut ti = tgt_len;

    while si != 0 || ti != 0 {
        match trace[idx(si, ti)] {
            Trace::Delete => {
                if pending_skips != COUNT_SENTINEL {
                    append_instruction(&mut patch, '-', pending_skips);
                    pending_skips = COUNT_SENTINEL;
                }
                pending_deletes = pending_deletes.wrapping_add(1);
                si -= 1;
            }
            Trace::Insert => {
                if pending_deletes != COUNT_SENTINEL {
                    append_instruction(&mut patch, 'D', pending_deletes);
                    pending_deletes = COUNT_SENTINEL;
                }
                if pending_skips != COUNT_SENTINEL {
                    append_instruction(&mut patch, '-', pending_skips);
                    pending_skips = COUNT_SENTINEL;
                }
                ti -= 1;
                append_instruction(&mut patch, 'I', target[ti]);
            }
            Trace::Replace => {
                if pending_deletes != COUNT_SENTINEL {
                    append_instruction(&mut patch, 'D', pending_deletes);
                    pending_deletes = COUNT_SENTINEL;
                }
                if pending_skips != COUNT_SENTINEL {
                    append_instruction(&mut patch, '-', pending_skips);
                    pending_skips = COUNT_SENTINEL;
                }
                ti -= 1;
                si -= 1;
                append_instruction(&mut patch, 'R', target[ti]);
            }
            Trace::Match => {
                if pending_deletes != COUNT_SENTINEL {
                    append_instruction(&mut patch, 'D', pending_deletes);
                    pending_deletes = COUNT_SENTINEL;
                }
                pending_skips = pending_skips.wrapping_add(1);
                si -= 1;
                ti -= 1;
            }
        }
    }

    if pending_deletes != COUNT_SENTINEL {
        append_instruction(&mut patch, 'D', pending_deletes);
    }

    patch
}

// Forward traversal encoding.

fn encode_forward(source: &[u16], target: &[u16]) -> String {
    let src_len = source.len();
    let tgt_len = target.len();
    let cols = tgt_len + 1;

    let mut cost = vec![0i32; (src_len + 1) * cols];
    let mut trace = vec![Trace::Match; (src_len + 1) * cols];

    let idx = |r: usize, c: usize| r * cols + c;

    // Boundary conditions (fill from bottom-right corner)
    // cost[srcLen][tgtLen] = 0, trace = MATCH
    for si in (0..src_len).rev() {
        cost[idx(si, tgt_len)] = cost[idx(si + 1, tgt_len)] + 1;
        trace[idx(si, tgt_len)] = Trace::Delete;
    }
    for ti in (0..tgt_len).rev() {
        cost[idx(src_len, ti)] = cost[idx(src_len, ti + 1)] + 1;
        trace[idx(src_len, ti)] = Trace::Insert;
    }

    // Fill right-to-left, bottom-to-top
    for si in (0..src_len).rev() {
        let src_ch = source[si]; // sourceCharacters[sourceIndex + sourceCharacterOffset=0]
        for ti in (0..tgt_len).rev() {
            let tgt_ch = target[ti];

            // sourceNeighbor = sourceIndex + 1, targetNeighbor = targetIndex + 1
            let del = cost[idx(si + 1, ti)] + 1;
            let ins = cost[idx(si, ti + 1)] + 1;
            let diag = cost[idx(si + 1, ti + 1)];
            let rep = diag + 1;
            let mat = diag
                + if src_ch == tgt_ch {
                    0
                } else {
                    MISMATCH_PENALTY
                };

            let mut best = mat;
            let mut bt = Trace::Match;
            if del <= best {
                best = del;
                bt = Trace::Delete;
            }
            if ins < best {
                best = ins;
                bt = Trace::Insert;
            }
            if rep < best {
                bt = Trace::Replace;
            }
            let _ = best;

            cost[idx(si, ti)] = if bt == Trace::Replace {
                rep
            } else if bt == Trace::Insert {
                ins
            } else if bt == Trace::Delete {
                del
            } else {
                mat
            };
            trace[idx(si, ti)] = bt;
        }
    }

    build_patch_forward(&trace, target, cols, src_len, tgt_len)
}

fn build_patch_forward(
    trace: &[Trace],
    target: &[u16],
    cols: usize,
    src_len: usize,
    tgt_len: usize,
) -> String {
    let idx = |r: usize, c: usize| r * cols + c;

    let mut patch = String::new();
    let mut pending_deletes: u16 = COUNT_SENTINEL;
    let mut pending_skips: u16 = COUNT_SENTINEL;

    let mut si = 0usize;
    let mut ti = 0usize;

    while si != src_len || ti != tgt_len {
        match trace[idx(si, ti)] {
            Trace::Delete => {
                if pending_skips != COUNT_SENTINEL {
                    append_instruction(&mut patch, '-', pending_skips);
                    pending_skips = COUNT_SENTINEL;
                }
                pending_deletes = pending_deletes.wrapping_add(1);
                si += 1;
            }
            Trace::Insert => {
                if pending_deletes != COUNT_SENTINEL {
                    append_instruction(&mut patch, 'D', pending_deletes);
                    pending_deletes = COUNT_SENTINEL;
                }
                if pending_skips != COUNT_SENTINEL {
                    append_instruction(&mut patch, '-', pending_skips);
                    pending_skips = COUNT_SENTINEL;
                }
                append_instruction(&mut patch, 'I', target[ti]);
                ti += 1;
            }
            Trace::Replace => {
                if pending_deletes != COUNT_SENTINEL {
                    append_instruction(&mut patch, 'D', pending_deletes);
                    pending_deletes = COUNT_SENTINEL;
                }
                if pending_skips != COUNT_SENTINEL {
                    append_instruction(&mut patch, '-', pending_skips);
                    pending_skips = COUNT_SENTINEL;
                }
                append_instruction(&mut patch, 'R', target[ti]);
                si += 1;
                ti += 1;
            }
            Trace::Match => {
                if pending_deletes != COUNT_SENTINEL {
                    append_instruction(&mut patch, 'D', pending_deletes);
                    pending_deletes = COUNT_SENTINEL;
                }
                pending_skips = pending_skips.wrapping_add(1);
                si += 1;
                ti += 1;
            }
        }
    }

    if pending_deletes != COUNT_SENTINEL {
        append_instruction(&mut patch, 'D', pending_deletes);
    }

    patch
}

// Instruction encoding helpers.

#[inline]
fn append_instruction(patch: &mut String, opcode: char, argument: u16) {
    patch.push(opcode);
    patch.push(char::from_u32(argument as u32).unwrap_or('\u{FFFD}'));
}
