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

// Java-interoperable compiled-trie binary I/O ("v7" stream), matching
// org.egothor.stemmer.StemmerPatchTrieBinaryIO / FrequencyTrie.writeTo/readFrom.
//
// File layout = gzip( big-endian Java DataOutputStream stream ):
//   i32 STREAM_MAGIC=0x45475452 ; i32 STREAM_VERSION=7
//   i32 nodeCount ; i32 rootId(=0)
//   writeUTF(metadata.toTextBlock())                 // Java modified UTF-8
//   i32 valueCount ; valueCount x writeUTF(patch)    // value dictionary
//   per node id 0..nodeCount-1:
//     u8 acceptsRemainingInput
//     i32 edgeCount ; edgeCount x { u16 edgeLabel ; i32 childId }
//     i32 valueCount ; valueCount x { i32 valueId ; i32 count }
//
// The outer gzip framing (headers/mtime) is not byte-identical across Java and
// Rust, but the INNER stream is, and both directions gunzip+parse each other.

use std::collections::HashMap;
use std::io::{self, Read, Write};

use flate2::read::GzDecoder;
use flate2::write::GzEncoder;
use flate2::Compression;

use crate::builder::{FrozenTrie, MAX_DENSE_SPAN};
use crate::patch::PatchCommand;
use crate::trie::{CaseMode, DiacriticMode, FrequencyTrie, TraversalDirection, TrieMetadata};

const STREAM_MAGIC: i32 = 0x4547_5452;
const STREAM_VERSION: i32 = 7;

// Big-endian writer helpers matching Java DataOutputStream.

fn put_i32(out: &mut Vec<u8>, v: i32) {
    out.extend_from_slice(&v.to_be_bytes());
}

fn put_u16(out: &mut Vec<u8>, v: u16) {
    out.extend_from_slice(&v.to_be_bytes());
}

/// Java DataOutputStream.writeUTF: u16 big-endian byte length + modified UTF-8.
fn put_java_utf(out: &mut Vec<u8>, s: &str) -> io::Result<()> {
    let mut bytes: Vec<u8> = Vec::with_capacity(s.len());
    for u in s.encode_utf16() {
        if (0x0001..=0x007F).contains(&u) {
            bytes.push(u as u8);
        } else if u == 0 || (0x0080..=0x07FF).contains(&u) {
            bytes.push(0xC0 | ((u >> 6) as u8 & 0x1F));
            bytes.push(0x80 | (u as u8 & 0x3F));
        } else {
            bytes.push(0xE0 | ((u >> 12) as u8 & 0x0F));
            bytes.push(0x80 | ((u >> 6) as u8 & 0x3F));
            bytes.push(0x80 | (u as u8 & 0x3F));
        }
    }
    if bytes.len() > 0xFFFF {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "string too long for Java modified UTF-8",
        ));
    }
    put_u16(out, bytes.len() as u16);
    out.extend_from_slice(&bytes);
    Ok(())
}

// Metadata text block, byte-identical to TrieMetadata.toTextBlock.

fn text_block(meta: &TrieMetadata) -> String {
    let forward = matches!(meta.traversal, TraversalDirection::Forward);
    let case = match meta.case_mode {
        CaseMode::LowercaseWithLocaleRoot => "LOWERCASE_WITH_LOCALE_ROOT",
        CaseMode::AsIs => "AS_IS",
    };
    let diac = match meta.diacritic_mode {
        DiacriticMode::AsIs => "AS_IS",
        DiacriticMode::Remove => "REMOVE",
    };
    let mut s = String::with_capacity(256);
    s.push_str("radixor.metadata.v1\n");
    s.push_str("formatVersion=7\n");
    s.push_str(if forward {
        "traversalDirection=FORWARD\n"
    } else {
        "traversalDirection=BACKWARD\n"
    });
    s.push_str("reductionMode=MERGE_SUBTREES_WITH_EQUIVALENT_DOMINANT_GET_RESULTS\n");
    s.push_str("dominantWinnerMinPercent=75\n");
    s.push_str("dominantWinnerOverSecondRatio=3\n");
    s.push_str("contractUniformSubtrees=true\n");
    s.push_str(&format!("diacriticProcessingMode={}\n", diac));
    s.push_str(&format!("caseProcessingMode={}\n", case));
    s
}

/// Serialize the frozen trie to the inner (uncompressed) v7 stream.
fn write_stream(frozen: &FrozenTrie, meta: &TrieMetadata) -> io::Result<Vec<u8>> {
    let node_count = frozen.accepts.len();
    let mut out = Vec::with_capacity(1024 + frozen.edge_labels.len() * 6);

    put_i32(&mut out, STREAM_MAGIC);
    put_i32(&mut out, STREAM_VERSION);
    put_i32(&mut out, node_count as i32);
    put_i32(&mut out, 0); // rootId
    put_java_utf(&mut out, &text_block(meta))?;

    // Value dictionary: distinct patch strings in first-occurrence order across
    // nodes(id) x values(local) — frozen.value_strings is already in that order.
    let mut value_id: HashMap<&str, i32> = HashMap::new();
    let mut distinct: Vec<&str> = Vec::new();
    for s in &frozen.value_strings {
        if !value_id.contains_key(s.as_str()) {
            value_id.insert(s.as_str(), distinct.len() as i32);
            distinct.push(s.as_str());
        }
    }
    put_i32(&mut out, distinct.len() as i32);
    for s in &distinct {
        put_java_utf(&mut out, s)?;
    }

    for node in 0..node_count {
        out.push(if frozen.accepts[node] { 1 } else { 0 });

        let elo = frozen.edge_start[node] as usize;
        let ehi = frozen.edge_start[node + 1] as usize;
        put_i32(&mut out, (ehi - elo) as i32);
        for k in elo..ehi {
            put_u16(&mut out, frozen.edge_labels[k]);
            put_i32(&mut out, frozen.edge_targets[k] as i32);
        }

        let vlo = frozen.value_start[node] as usize;
        let vhi = frozen.value_start[node + 1] as usize;
        put_i32(&mut out, (vhi - vlo) as i32);
        for k in vlo..vhi {
            let id = value_id[frozen.value_strings[k].as_str()];
            put_i32(&mut out, id);
            put_i32(&mut out, frozen.value_counts[k]);
        }
    }

    Ok(out)
}

/// Serialize the frozen trie to a gzip-compressed v7 file image.
pub(crate) fn write_v7(frozen: &FrozenTrie, meta: &TrieMetadata) -> io::Result<Vec<u8>> {
    let stream = write_stream(frozen, meta)?;
    let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
    encoder.write_all(&stream)?;
    encoder.finish()
}

// Compiled-stream reader.

struct Reader<'a> {
    data: &'a [u8],
    pos: usize,
}

impl<'a> Reader<'a> {
    fn new(data: &'a [u8]) -> Self {
        Reader { data, pos: 0 }
    }

    fn take(&mut self, n: usize) -> io::Result<&'a [u8]> {
        if self.pos + n > self.data.len() {
            return Err(io::Error::new(
                io::ErrorKind::UnexpectedEof,
                "unexpected end of trie stream",
            ));
        }
        let slice = &self.data[self.pos..self.pos + n];
        self.pos += n;
        Ok(slice)
    }

    fn i32(&mut self) -> io::Result<i32> {
        let b = self.take(4)?;
        Ok(i32::from_be_bytes([b[0], b[1], b[2], b[3]]))
    }

    fn u16(&mut self) -> io::Result<u16> {
        let b = self.take(2)?;
        Ok(u16::from_be_bytes([b[0], b[1]]))
    }

    fn u8(&mut self) -> io::Result<u8> {
        Ok(self.take(1)?[0])
    }

    fn java_utf(&mut self) -> io::Result<String> {
        let len = self.u16()? as usize;
        let bytes = self.take(len)?;
        decode_java_utf(bytes)
    }
}

fn decode_java_utf(bytes: &[u8]) -> io::Result<String> {
    let mut units: Vec<u16> = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        let b = bytes[i];
        if b & 0x80 == 0 {
            units.push(b as u16);
            i += 1;
        } else if b & 0xE0 == 0xC0 {
            if i + 1 >= bytes.len() {
                return Err(malformed());
            }
            let b1 = bytes[i + 1];
            units.push((((b as u16 & 0x1F) << 6) | (b1 as u16 & 0x3F)) as u16);
            i += 2;
        } else if b & 0xF0 == 0xE0 {
            if i + 2 >= bytes.len() {
                return Err(malformed());
            }
            let b1 = bytes[i + 1];
            let b2 = bytes[i + 2];
            units.push(((b as u16 & 0x0F) << 12) | ((b1 as u16 & 0x3F) << 6) | (b2 as u16 & 0x3F));
            i += 3;
        } else {
            return Err(malformed());
        }
    }
    Ok(String::from_utf16_lossy(&units))
}

fn malformed() -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, "malformed modified UTF-8")
}

fn parse_metadata(text: &str) -> TrieMetadata {
    let mut traversal = TraversalDirection::Backward;
    let mut case_mode = CaseMode::LowercaseWithLocaleRoot;
    let mut diacritic_mode = DiacriticMode::AsIs;
    for line in text.lines() {
        if let Some((key, value)) = line.split_once('=') {
            match key {
                "traversalDirection" => {
                    traversal = if value == "FORWARD" {
                        TraversalDirection::Forward
                    } else {
                        TraversalDirection::Backward
                    };
                }
                "caseProcessingMode" => {
                    case_mode = if value == "AS_IS" {
                        CaseMode::AsIs
                    } else {
                        CaseMode::LowercaseWithLocaleRoot
                    };
                }
                "diacriticProcessingMode" => {
                    diacritic_mode = if value == "REMOVE" {
                        DiacriticMode::Remove
                    } else {
                        DiacriticMode::AsIs
                    };
                }
                _ => {}
            }
        }
    }
    TrieMetadata {
        traversal,
        case_mode,
        diacritic_mode,
    }
}

/// Rebuild dense direct-index tables from the CSR edges (same policy as freeze).
fn build_dense(
    edge_start: &[u32],
    edge_labels: &[u16],
    edge_targets: &[u32],
) -> (Vec<u32>, Vec<u16>, Vec<u32>) {
    let node_count = edge_start.len() - 1;
    let mut dense_start: Vec<u32> = Vec::with_capacity(node_count + 1);
    let mut dense_base: Vec<u16> = Vec::with_capacity(node_count);
    let mut dense_targets: Vec<u32> = Vec::new();
    dense_start.push(0);
    for node in 0..node_count {
        let lo = edge_start[node] as usize;
        let hi = edge_start[node + 1] as usize;
        let count = hi - lo;
        let mut dense = false;
        if count >= 2 {
            let first = edge_labels[lo] as usize;
            let last = edge_labels[hi - 1] as usize;
            let span = last - first + 1;
            if span <= MAX_DENSE_SPAN {
                let base = edge_labels[lo];
                let seg = dense_targets.len();
                dense_targets.resize(seg + span, 0);
                for k in lo..hi {
                    dense_targets[seg + (edge_labels[k] - base) as usize] = edge_targets[k] + 1;
                }
                dense_base.push(base);
                dense_start.push(dense_targets.len() as u32);
                dense = true;
            }
        }
        if !dense {
            dense_base.push(0);
            dense_start.push(dense_targets.len() as u32);
        }
    }
    (dense_start, dense_base, dense_targets)
}

/// Read a gzip-compressed Java v7 compiled-trie image into a runtime trie.
#[allow(dead_code)] // convenience wrapper; lib.rs decompresses then calls read_stream
pub(crate) fn read_v7(gz_bytes: &[u8]) -> io::Result<FrequencyTrie> {
    let mut data = Vec::new();
    GzDecoder::new(gz_bytes).read_to_end(&mut data)?;
    read_stream(&data)
}

/// Whether `decompressed` (an already-gunzipped byte stream) is a v7 trie image.
pub(crate) fn is_v7_stream(decompressed: &[u8]) -> bool {
    decompressed.len() >= 4
        && i32::from_be_bytes([
            decompressed[0],
            decompressed[1],
            decompressed[2],
            decompressed[3],
        ]) == STREAM_MAGIC
}

/// Parse the inner (uncompressed) v7 stream into a runtime trie.
pub(crate) fn read_stream(data: &[u8]) -> io::Result<FrequencyTrie> {
    let mut r = Reader::new(data);
    if r.i32()? != STREAM_MAGIC {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "bad trie stream magic",
        ));
    }
    let version = r.i32()?;
    if version != STREAM_VERSION {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            format!("unsupported trie stream version {version} (expected {STREAM_VERSION})"),
        ));
    }
    let node_count = r.i32()? as usize;
    let root_id = r.i32()?;
    if root_id != 0 {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "unsupported non-zero root node id",
        ));
    }
    let metadata = parse_metadata(&r.java_utf()?);
    let backward = matches!(metadata.traversal, TraversalDirection::Backward);

    let value_table_len = r.i32()? as usize;
    let mut patches: Vec<PatchCommand> = Vec::with_capacity(value_table_len);
    for _ in 0..value_table_len {
        let patch = r.java_utf()?;
        patches.push(PatchCommand::parse(&patch, backward));
    }

    let mut edge_start: Vec<u32> = Vec::with_capacity(node_count + 1);
    let mut edge_labels: Vec<u16> = Vec::new();
    let mut edge_targets: Vec<u32> = Vec::new();
    let mut accepts: Vec<bool> = Vec::with_capacity(node_count);
    let mut value_start: Vec<u32> = Vec::with_capacity(node_count + 1);
    let mut value_ids: Vec<u32> = Vec::new();
    edge_start.push(0);
    value_start.push(0);

    for _ in 0..node_count {
        accepts.push(r.u8()? != 0);
        let edge_count = r.i32()? as usize;
        for _ in 0..edge_count {
            let label = r.u16()?;
            let child = r.i32()? as u32;
            edge_labels.push(label);
            edge_targets.push(child);
        }
        edge_start.push(edge_labels.len() as u32);

        let value_count = r.i32()? as usize;
        for _ in 0..value_count {
            let value_id = r.i32()? as usize;
            let _count = r.i32()?; // frequency: not used at runtime
            if value_id >= patches.len() {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    "value id out of range",
                ));
            }
            value_ids.push(value_id as u32);
        }
        value_start.push(value_ids.len() as u32);
    }

    let (dense_start, dense_base, dense_targets) =
        build_dense(&edge_start, &edge_labels, &edge_targets);

    Ok(FrequencyTrie::new(
        edge_start,
        edge_labels,
        edge_targets,
        accepts,
        value_start,
        value_ids,
        patches,
        dense_start,
        dense_base,
        dense_targets,
        metadata,
    ))
}
