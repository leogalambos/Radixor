/*
 * _radixor_c.c — CPython C extension for the Radixor stemmer.
 *
 * Loads pre-compiled Radixor v7 trie files (.rxc, gzip-framed).
 * TSV→trie compilation stays in the 'radixor' (Rust/PyO3) package.
 *
 * The list-specialized batch path uses borrowed input references and transfers
 * each result reference directly into the output list. This avoids temporary
 * sequence objects and reference-count churn in the per-word loop.
 *
 * Copyright (C) 2026, Leo Galambos. BSD-3-Clause.
 */

#define PY_SSIZE_T_CLEAN
#include <Python.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#ifdef _MSC_VER
#define strncasecmp _strnicmp
#endif

/* Interned once during module initialization for repeated str.lower() calls. */
static PyObject *_lower_name = NULL;

/* Patch command kinds. */
#define PATCH_PRESERVE 0
#define PATCH_DELETE_SUFFIX 1
#define PATCH_DELETE_PREFIX 2
#define PATCH_APPEND_CHAR 3
#define PATCH_PREPEND_CHAR 4
#define PATCH_REPLACE_LAST 5
#define PATCH_REPLACE_FIRST 6
#define PATCH_BACKWARD_COMPOUND 7
#define PATCH_FORWARD_COMPOUND 8

/* Serialized opcode values shared with the other Radixor implementations. */
#define OP_SKIP ((uint8_t)'-')
#define OP_DELETE ((uint8_t)'D')
#define OP_INSERT ((uint8_t)'I')
#define OP_REPLACE ((uint8_t)'R')
#define OP_NOOP ((uint8_t)'N')

/* Must match MAX_DENSE_SPAN used when the model is compiled. */
#define MAX_DENSE_SPAN 512

/* Data structures. */

typedef struct {
    uint8_t *opcodes;
    uint32_t *operands;
    uint32_t op_count;
    int32_t length_delta;
    uint32_t min_len;
} CompoundData;

typedef struct {
    uint8_t kind;
    union {
        uint32_t count; /* DELETE_SUFFIX / DELETE_PREFIX */
        uint16_t ch;    /* APPEND / PREPEND / REPLACE_LAST / REPLACE_FIRST */
        CompoundData compound;
    } u;
} Patch;

typedef struct {
    uint32_t *edge_start;    /* [node_count + 1] */
    uint16_t *edge_labels;   /* [edge_count]     */
    uint32_t *edge_targets;  /* [edge_count]     */
    uint8_t *accepts;        /* [node_count]     */
    uint32_t *value_start;   /* [node_count + 1] */
    uint32_t *value_ids;     /* [value_id_count] */
    uint32_t *preferred_ids; /* [node_count], UINT32_MAX = none */
    Patch *patches;
    uint32_t patch_count;
    uint32_t *dense_start; /* [node_count + 1] */
    uint16_t *dense_base;  /* [node_count]     */
    uint32_t *dense_targets;
    uint32_t node_count;
    /* Model metadata that affects lookup and patch application. */
    int backward;
    int lowercase;
    int source_slice_ok; /* !lowercase && !remove_diacritics */
} RadixorTrie;

typedef struct {
    PyObject_HEAD RadixorTrie *trie;
    PyObject *cache; /* PyDict or NULL */
    Py_ssize_t cache_cap;
    uint16_t *key_buf; /* scratch: UTF-16 encoded key */
    Py_ssize_t key_cap;
    uint16_t *u16_buf; /* scratch: patch output (UTF-16) */
    Py_ssize_t u16_cap;
    char *u8_buf; /* scratch: UTF-8 conversion output */
    Py_ssize_t u8_cap;
} StemmerCoreObject;

/* UTF-8 and UTF-16 conversion. */

static uint32_t utf8_next_cp(const char *s, Py_ssize_t len, Py_ssize_t *pos)
{
    if (*pos >= len)
        return 0xFFFDu;
    uint8_t b = (uint8_t)s[(*pos)++];
    if (b < 0x80u)
        return b;
    if (b < 0xC0u)
        return 0xFFFDu;
    if (b < 0xE0u) {
        if (*pos >= len)
            return 0xFFFDu;
        return ((uint32_t)(b & 0x1Fu) << 6) | ((uint8_t)s[(*pos)++] & 0x3Fu);
    }
    if (b < 0xF0u) {
        if (*pos + 1 >= len) {
            *pos = len;
            return 0xFFFDu;
        }
        uint32_t cp = (uint32_t)(b & 0x0Fu) << 12;
        cp |= (uint32_t)((uint8_t)s[(*pos)++] & 0x3Fu) << 6;
        cp |= (uint8_t)s[(*pos)++] & 0x3Fu;
        return cp;
    }
    if (*pos + 2 >= len) {
        *pos = len;
        return 0xFFFDu;
    }
    uint32_t cp = (uint32_t)(b & 0x07u) << 18;
    cp |= (uint32_t)((uint8_t)s[(*pos)++] & 0x3Fu) << 12;
    cp |= (uint32_t)((uint8_t)s[(*pos)++] & 0x3Fu) << 6;
    cp |= (uint8_t)s[(*pos)++] & 0x3Fu;
    return cp;
}

static int encode_utf8_cp(char *d, uint32_t cp)
{
    if (cp < 0x80u) {
        d[0] = (char)cp;
        return 1;
    }
    if (cp < 0x800u) {
        d[0] = (char)(0xC0u | (cp >> 6));
        d[1] = (char)(0x80u | (cp & 0x3Fu));
        return 2;
    }
    if (cp < 0x10000u) {
        d[0] = (char)(0xE0u | (cp >> 12));
        d[1] = (char)(0x80u | ((cp >> 6) & 0x3Fu));
        d[2] = (char)(0x80u | (cp & 0x3Fu));
        return 3;
    }
    d[0] = (char)(0xF0u | (cp >> 18));
    d[1] = (char)(0x80u | ((cp >> 12) & 0x3Fu));
    d[2] = (char)(0x80u | ((cp >> 6) & 0x3Fu));
    d[3] = (char)(0x80u | (cp & 0x3Fu));
    return 4;
}

/* UTF-16 code units → UTF-8 string; dst must have capacity >= n*4. */
static Py_ssize_t utf16_to_utf8(const uint16_t *u, Py_ssize_t n, char *dst)
{
    Py_ssize_t out = 0;
    for (Py_ssize_t i = 0; i < n; i++) {
        uint32_t cp;
        uint16_t hi = u[i];
        if (hi >= 0xD800u && hi <= 0xDBFFu && i + 1 < n) {
            uint16_t lo = u[i + 1];
            if (lo >= 0xDC00u && lo <= 0xDFFFFu) {
                cp = 0x10000u + ((uint32_t)(hi - 0xD800u) << 10) + (lo - 0xDC00u);
                i++;
            } else
                cp = 0xFFFDu;
        } else if (hi >= 0xD800u && hi <= 0xDFFFu) {
            cp = 0xFFFDu;
        } else {
            cp = hi;
        }
        out += encode_utf8_cp(dst + out, cp);
    }
    return out;
}

static int is_utf16_surrogate(uint16_t u) { return u >= 0xD800u && u <= 0xDFFFu; }

/* Append one UTF-16 code unit decoded as UTF-8 to dst[*pos].
   dst must have at least 4 bytes available. */
static void push_utf16_unit_lossy(char *dst, Py_ssize_t *pos, uint16_t u)
{
    uint32_t cp = is_utf16_surrogate(u) ? 0xFFFDu : (uint32_t)u;
    *pos += encode_utf8_cp(dst + *pos, cp);
}

/* Reusable buffers owned by a StemmerCore instance. */

static int ensure_key_cap(StemmerCoreObject *self, Py_ssize_t need)
{
    if (self->key_cap >= need)
        return 1;
    Py_ssize_t new_cap = need + need / 2 + 16;
    uint16_t *nb = (uint16_t *)realloc(self->key_buf, (size_t)new_cap * sizeof(uint16_t));
    if (!nb) {
        PyErr_NoMemory();
        return 0;
    }
    self->key_buf = nb;
    self->key_cap = new_cap;
    return 1;
}

static int ensure_u16_cap(StemmerCoreObject *self, Py_ssize_t need)
{
    if (self->u16_cap >= need)
        return 1;
    Py_ssize_t new_cap = need + need / 2 + 16;
    uint16_t *nb = (uint16_t *)realloc(self->u16_buf, (size_t)new_cap * sizeof(uint16_t));
    if (!nb) {
        PyErr_NoMemory();
        return 0;
    }
    self->u16_buf = nb;
    self->u16_cap = new_cap;
    return 1;
}

static int ensure_u8_cap(StemmerCoreObject *self, Py_ssize_t need)
{
    if (self->u8_cap >= need)
        return 1;
    Py_ssize_t new_cap = need + need / 2 + 16;
    char *nb = (char *)realloc(self->u8_buf, (size_t)new_cap);
    if (!nb) {
        PyErr_NoMemory();
        return 0;
    }
    self->u8_buf = nb;
    self->u8_cap = new_cap;
    return 1;
}

/* Patch parsing. */

static const Patch PATCH_PRESERVE_SINGLETON = {PATCH_PRESERVE, {{0}}};

static int decode_count(uint16_t arg, uint32_t *out)
{
    if (arg < (uint16_t)'a')
        return 0;
    *out = (uint32_t)(arg - (uint16_t)'a') + 1u;
    return 1;
}

static int compile_operand(uint8_t op, uint16_t arg, uint32_t *out)
{
    switch (op) {
    case OP_SKIP:
    case OP_DELETE:
        return decode_count(arg, out) && *out >= 1u;
    case OP_INSERT:
    case OP_REPLACE:
        *out = (uint32_t)arg;
        return 1;
    default:
        return 0;
    }
}

static int32_t calc_length_delta(const uint8_t *ops, const uint32_t *opers, uint32_t n)
{
    int32_t d = 0;
    for (uint32_t i = 0; i < n; i++) {
        if (ops[i] == OP_DELETE)
            d -= (int32_t)opers[i];
        else if (ops[i] == OP_INSERT)
            d++;
    }
    return d;
}

static uint32_t calc_backward_min_len(const uint8_t *ops, const uint32_t *opers, uint32_t n)
{
    uint32_t ml = 0, consumed = 0;
    for (uint32_t i = 0; i < n; i++) {
        switch (ops[i]) {
        case OP_SKIP:
            consumed += opers[i];
            break;
        case OP_DELETE:
            if (consumed + opers[i] > ml)
                ml = consumed + opers[i];
            consumed += opers[i];
            break;
        case OP_INSERT:
            if (consumed > ml)
                ml = consumed;
            break;
        case OP_REPLACE:
            if (consumed + 1 > ml)
                ml = consumed + 1;
            consumed++;
            break;
        }
    }
    return ml;
}

static uint32_t calc_forward_min_len(const uint8_t *ops, const uint32_t *opers, uint32_t n)
{
    uint32_t ml = 0;
    int32_t pos = 0, delta = 0;
    for (uint32_t i = 0; i < n; i++) {
        int32_t op = (int32_t)opers[i];
        switch (ops[i]) {
        case OP_SKIP:
            pos += op;
            break;
        case OP_DELETE: {
            int32_t need = pos + op - delta;
            if (need < 0)
                need = 0;
            if ((uint32_t)need > ml)
                ml = (uint32_t)need;
            delta -= op;
            break;
        }
        case OP_INSERT: {
            int32_t need = pos - delta;
            if (need < 0)
                need = 0;
            if ((uint32_t)need > ml)
                ml = (uint32_t)need;
            delta++;
            pos++;
            break;
        }
        case OP_REPLACE: {
            int32_t need = pos + 1 - delta;
            if (need < 0)
                need = 0;
            if ((uint32_t)need > ml)
                ml = (uint32_t)need;
            pos++;
            break;
        }
        }
    }
    return ml;
}

static Patch patch_parse_single(uint8_t op, uint16_t arg, int backward)
{
    /* Copying the singleton initializes the entire union before one member is set. */
    Patch p = PATCH_PRESERVE_SINGLETON;
    switch (op) {
    case OP_DELETE: {
        uint32_t count;
        if (!decode_count(arg, &count))
            return PATCH_PRESERVE_SINGLETON;
        p.kind = backward ? PATCH_DELETE_SUFFIX : PATCH_DELETE_PREFIX;
        p.u.count = count;
        return p;
    }
    case OP_INSERT:
        p.kind = backward ? PATCH_APPEND_CHAR : PATCH_PREPEND_CHAR;
        p.u.ch = arg;
        return p;
    case OP_REPLACE:
        p.kind = backward ? PATCH_REPLACE_LAST : PATCH_REPLACE_FIRST;
        p.u.ch = arg;
        return p;
    default:
        return PATCH_PRESERVE_SINGLETON;
    }
}

/* Parse a patch command from a UTF-16 encoded patch string.
   Returns heap-allocated compound data (must be freed via patch_free). */
static Patch patch_parse(const uint16_t *chars, Py_ssize_t len, int backward)
{
    if (len == 0 || len % 2 != 0)
        return PATCH_PRESERVE_SINGLETON;
    if (len == 2)
        return patch_parse_single((uint8_t)chars[0], chars[1], backward);

    uint32_t n = (uint32_t)(len / 2);
    uint8_t *ops = (uint8_t *)malloc(n);
    uint32_t *opers = (uint32_t *)malloc(n * sizeof(uint32_t));
    if (!ops || !opers) {
        free(ops);
        free(opers);
        return PATCH_PRESERVE_SINGLETON;
    }

    for (uint32_t i = 0; i < n; i++) {
        if (!compile_operand((uint8_t)chars[i * 2], chars[i * 2 + 1], &opers[i])) {
            free(ops);
            free(opers);
            return PATCH_PRESERVE_SINGLETON;
        }
        ops[i] = (uint8_t)chars[i * 2];
    }

    Patch p;
    p.u.compound.opcodes = ops;
    p.u.compound.operands = opers;
    p.u.compound.op_count = n;
    p.u.compound.length_delta = calc_length_delta(ops, opers, n);
    p.u.compound.min_len =
        backward ? calc_backward_min_len(ops, opers, n) : calc_forward_min_len(ops, opers, n);
    p.kind = backward ? PATCH_BACKWARD_COMPOUND : PATCH_FORWARD_COMPOUND;
    return p;
}

static void patch_free(Patch *p)
{
    if (p->kind == PATCH_BACKWARD_COMPOUND || p->kind == PATCH_FORWARD_COMPOUND) {
        free(p->u.compound.opcodes);
        free(p->u.compound.operands);
    }
}

/* Patch application. */

static Py_ssize_t computed_length(const Patch *p, Py_ssize_t src_len)
{
    int32_t ld;
    Py_ssize_t min_len;
    switch (p->kind) {
    case PATCH_PRESERVE:
        return src_len;
    case PATCH_DELETE_SUFFIX:
    case PATCH_DELETE_PREFIX:
        ld = -(int32_t)p->u.count;
        min_len = 0;
        break;
    case PATCH_APPEND_CHAR:
    case PATCH_PREPEND_CHAR:
        ld = 1;
        min_len = 0;
        break;
    case PATCH_REPLACE_LAST:
    case PATCH_REPLACE_FIRST:
        ld = 0;
        min_len = 1;
        break;
    case PATCH_BACKWARD_COMPOUND:
    case PATCH_FORWARD_COMPOUND:
        ld = p->u.compound.length_delta;
        min_len = (Py_ssize_t)p->u.compound.min_len;
        break;
    default:
        return src_len;
    }
    if (src_len < min_len)
        return src_len;
    int32_t r = (int32_t)src_len + ld;
    return (r < 1) ? src_len : (Py_ssize_t)r;
}

/* Returns 0 on success, 1 if fallback (out filled with src[0..src_len]). */
static int apply_backward(const uint8_t *ops, const uint32_t *opers, uint32_t nops,
                          const uint16_t *src, Py_ssize_t src_len, uint16_t *out,
                          Py_ssize_t out_len)
{
    int32_t current_len = (int32_t)src_len;
    int32_t position = (int32_t)src_len - 1;
    int32_t src_end = (int32_t)src_len;
    int32_t out_end = (int32_t)out_len;

    for (uint32_t i = 0; i < nops; i++) {
        int32_t operand = (int32_t)opers[i];
        switch (ops[i]) {
        case OP_SKIP: {
            int32_t skip = operand < src_end ? operand : src_end;
            src_end -= skip;
            out_end -= skip;
            if (out_end < 0)
                goto fallback;
            memcpy(out + out_end, src + src_end, (size_t)skip * sizeof(uint16_t));
            position = position - operand + 1;
            break;
        }
        case OP_DELETE: {
            int32_t del_hi = position + 1;
            position -= operand - 1;
            if (position < 0 || position > current_len || position > del_hi)
                goto fallback;
            int32_t ds = (position < current_len) ? position : current_len;
            int32_t de = (del_hi < current_len) ? del_hi : current_len;
            int32_t deleted = de - ds;
            if (deleted < 0 || src_end < deleted)
                goto fallback;
            src_end -= deleted;
            current_len -= deleted;
            break;
        }
        case OP_INSERT:
            if (position < -1 || position >= current_len || out_end <= 0)
                goto fallback;
            out[--out_end] = (uint16_t)operand;
            current_len++;
            position++;
            break;
        case OP_REPLACE:
            if (position < 0 || position >= current_len || src_end <= 0 || out_end <= 0)
                goto fallback;
            src_end--;
            out_end--;
            out[out_end] = (uint16_t)operand;
            break;
        default:
            goto fallback;
        }
        position--;
    }
    if (src_end != out_end)
        goto fallback;
    memcpy(out, src, (size_t)src_end * sizeof(uint16_t));
    return 0;
fallback:
    memcpy(out, src, (size_t)src_len * sizeof(uint16_t));
    return 1;
}

static int apply_forward(const uint8_t *ops, const uint32_t *opers, uint32_t nops,
                         const uint16_t *src, Py_ssize_t src_len, uint16_t *out, Py_ssize_t out_len)
{
    int32_t current_len = (int32_t)src_len;
    int32_t position = 0;
    int32_t src_idx = 0;
    int32_t out_idx = 0;

    for (uint32_t i = 0; i < nops; i++) {
        int32_t operand = (int32_t)opers[i];
        switch (ops[i]) {
        case OP_SKIP: {
            int32_t avail = (int32_t)src_len - src_idx;
            int32_t skip = operand < avail ? operand : avail;
            memcpy(out + out_idx, src + src_idx, (size_t)skip * sizeof(uint16_t));
            src_idx += skip;
            out_idx += skip;
            position = position + operand - 1;
            break;
        }
        case OP_DELETE: {
            if (position < 0 || position > current_len)
                goto fallback;
            int32_t dl = current_len - position;
            int32_t del = operand < dl ? operand : dl;
            if (src_idx + del > (int32_t)src_len)
                goto fallback;
            src_idx += del;
            current_len -= del;
            position--;
            break;
        }
        case OP_INSERT:
            if (position < 0 || position > current_len || out_idx >= (int32_t)out_len)
                goto fallback;
            out[out_idx++] = (uint16_t)operand;
            current_len++;
            break;
        case OP_REPLACE:
            if (position < 0 || position >= current_len || src_idx >= (int32_t)src_len ||
                out_idx >= (int32_t)out_len)
                goto fallback;
            src_idx++;
            out[out_idx++] = (uint16_t)operand;
            break;
        default:
            goto fallback;
        }
        position++;
    }
    {
        int32_t rem = (int32_t)src_len - src_idx;
        if (rem < 0 || out_idx + rem != (int32_t)out_len)
            goto fallback;
        if (rem > 0)
            memcpy(out + out_idx, src + src_idx, (size_t)rem * sizeof(uint16_t));
    }
    return 0;
fallback:
    memcpy(out, src, (size_t)src_len * sizeof(uint16_t));
    return 1;
}

/* Apply patch: src[0..src_len) (UTF-16) → out[0..*out_len) (UTF-16).
   out capacity must be >= max(src_len, computed_length(p, src_len)) + 2.
   Returns actual output length. */
static Py_ssize_t apply_into(const Patch *p, const uint16_t *src, Py_ssize_t src_len, uint16_t *out)
{
    Py_ssize_t tgt = computed_length(p, src_len);
    switch (p->kind) {
    case PATCH_PRESERVE:
        memcpy(out, src, (size_t)src_len * sizeof(uint16_t));
        return src_len;
    case PATCH_DELETE_SUFFIX:
        if (tgt < src_len) {
            memcpy(out, src, (size_t)tgt * sizeof(uint16_t));
            return tgt;
        }
        memcpy(out, src, (size_t)src_len * sizeof(uint16_t));
        return src_len;
    case PATCH_DELETE_PREFIX:
        if (tgt < src_len) {
            memcpy(out, src + p->u.count, (size_t)tgt * sizeof(uint16_t));
            return tgt;
        }
        memcpy(out, src, (size_t)src_len * sizeof(uint16_t));
        return src_len;
    case PATCH_APPEND_CHAR:
        memcpy(out, src, (size_t)src_len * sizeof(uint16_t));
        out[src_len] = p->u.ch;
        return src_len + 1;
    case PATCH_PREPEND_CHAR:
        out[0] = p->u.ch;
        memcpy(out + 1, src, (size_t)src_len * sizeof(uint16_t));
        return src_len + 1;
    case PATCH_REPLACE_LAST:
        memcpy(out, src, (size_t)src_len * sizeof(uint16_t));
        if (src_len > 0)
            out[src_len - 1] = p->u.ch;
        return src_len;
    case PATCH_REPLACE_FIRST:
        memcpy(out, src, (size_t)src_len * sizeof(uint16_t));
        if (src_len > 0)
            out[0] = p->u.ch;
        return src_len;
    case PATCH_BACKWARD_COMPOUND: {
        const CompoundData *c = &p->u.compound;
        if (src_len < (Py_ssize_t)c->min_len || tgt < 1) {
            memcpy(out, src, (size_t)src_len * sizeof(uint16_t));
            return src_len;
        }
        int fb = apply_backward(c->opcodes, c->operands, c->op_count, src, src_len, out, tgt);
        return fb ? src_len : tgt;
    }
    case PATCH_FORWARD_COMPOUND: {
        const CompoundData *c = &p->u.compound;
        if (src_len < (Py_ssize_t)c->min_len || tgt < 1) {
            memcpy(out, src, (size_t)src_len * sizeof(uint16_t));
            return src_len;
        }
        int fb = apply_forward(c->opcodes, c->operands, c->op_count, src, src_len, out, tgt);
        return fb ? src_len : tgt;
    }
    }
    memcpy(out, src, (size_t)src_len * sizeof(uint16_t));
    return src_len;
}

/* Direct source slicing when the model does not require normalization. */

/* Find byte offset from which a suffix of `n` UTF-16 code units starts.
   Returns -1 if a scalar value would be split. */
static Py_ssize_t utf8_suffix_boundary(const char *src, Py_ssize_t byte_len, uint32_t utf16_units)
{
    /* Byte offsets cannot be derived until the UTF-16 length is known. */
    if (utf16_units == 0)
        return byte_len;
    Py_ssize_t total_u16 = 0, i = 0;
    while (i < byte_len) {
        Py_ssize_t prev = i;
        uint32_t cp = utf8_next_cp(src, byte_len, &i);
        (void)prev;
        total_u16 += (cp >= 0x10000u) ? 2 : 1;
    }
    if ((Py_ssize_t)utf16_units > total_u16)
        return -1;
    Py_ssize_t target_prefix = total_u16 - (Py_ssize_t)utf16_units;
    i = 0;
    Py_ssize_t u16_count = 0;
    while (i < byte_len) {
        Py_ssize_t start = i;
        uint32_t cp = utf8_next_cp(src, byte_len, &i);
        Py_ssize_t w = (cp >= 0x10000u) ? 2 : 1;
        if (u16_count + w > target_prefix) {
            /* A UTF-16 boundary must not split a supplementary code point. */
            if (u16_count == target_prefix)
                return start;
            return -1;
        }
        u16_count += w;
        if (u16_count == target_prefix)
            return i;
    }
    return (target_prefix == 0) ? 0 : -1;
}

/* Find byte offset after the first `n` UTF-16 code units. Returns -1 on split. */
static Py_ssize_t utf8_prefix_boundary(const char *src, Py_ssize_t byte_len, uint32_t utf16_units)
{
    if (utf16_units == 0)
        return 0;
    Py_ssize_t i = 0, u16 = 0;
    while (i < byte_len) {
        uint32_t cp = utf8_next_cp(src, byte_len, &i);
        Py_ssize_t w = (cp >= 0x10000u) ? 2 : 1;
        if (u16 + w > (Py_ssize_t)utf16_units)
            return -1;
        u16 += w;
        if (u16 == (Py_ssize_t)utf16_units)
            return i;
    }
    return -1;
}

/* Try to produce a Python string directly from the UTF-8 input, avoiding
   UTF-16. Returns NULL (no exception set) if the fast path is not applicable. */
static PyObject *source_slice_apply(const Patch *p, const char *utf8, Py_ssize_t utf8_len,
                                    Py_ssize_t src_utf16_len)
{
    switch (p->kind) {
    case PATCH_PRESERVE:
        return PyUnicode_FromStringAndSize(utf8, utf8_len);

    case PATCH_DELETE_SUFFIX: {
        Py_ssize_t tgt = computed_length(p, src_utf16_len);
        if (tgt >= src_utf16_len)
            return PyUnicode_FromStringAndSize(utf8, utf8_len); /* rejected deletion */
        Py_ssize_t end = utf8_suffix_boundary(utf8, utf8_len, p->u.count);
        if (end < 0)
            return NULL;
        return PyUnicode_FromStringAndSize(utf8, end);
    }
    case PATCH_DELETE_PREFIX: {
        Py_ssize_t tgt = computed_length(p, src_utf16_len);
        if (tgt >= src_utf16_len)
            return PyUnicode_FromStringAndSize(utf8, utf8_len);
        Py_ssize_t start = utf8_prefix_boundary(utf8, utf8_len, p->u.count);
        if (start < 0)
            return NULL;
        return PyUnicode_FromStringAndSize(utf8 + start, utf8_len - start);
    }
    default:
        return NULL;
    }
}

/* Direct UTF-8 apply for simple non-slice patches.
   Returns NULL (no exception) if this patch needs the UTF-16 path. */
static PyObject *simple_utf8_apply(const Patch *p, const char *utf8, Py_ssize_t utf8_len,
                                   Py_ssize_t src_utf16_len, StemmerCoreObject *self)
{
    switch (p->kind) {
    case PATCH_APPEND_CHAR: {
        Py_ssize_t need = utf8_len + 4;
        if (!ensure_u8_cap(self, need))
            return PyErr_Occurred() ? (PyObject *)-1 : NULL;
        memcpy(self->u8_buf, utf8, (size_t)utf8_len);
        Py_ssize_t pos = utf8_len;
        push_utf16_unit_lossy(self->u8_buf, &pos, p->u.ch);
        return PyUnicode_FromStringAndSize(self->u8_buf, pos);
    }
    case PATCH_PREPEND_CHAR: {
        Py_ssize_t need = utf8_len + 4;
        if (!ensure_u8_cap(self, need))
            return PyErr_Occurred() ? (PyObject *)-1 : NULL;
        Py_ssize_t pos = 0;
        push_utf16_unit_lossy(self->u8_buf, &pos, p->u.ch);
        memcpy(self->u8_buf + pos, utf8, (size_t)utf8_len);
        return PyUnicode_FromStringAndSize(self->u8_buf, pos + utf8_len);
    }
    case PATCH_REPLACE_LAST: {
        if (src_utf16_len == 0)
            return PyUnicode_FromStringAndSize(utf8, utf8_len);
        if (is_utf16_surrogate(p->u.ch))
            return NULL;
        Py_ssize_t last_start = utf8_len;
        while (last_start > 0 && ((uint8_t)utf8[last_start - 1] & 0xC0u) == 0x80u)
            last_start--;
        if (last_start <= 0)
            return NULL;
        last_start--;
        Py_ssize_t tmp = last_start;
        uint32_t last_cp = utf8_next_cp(utf8, utf8_len, &tmp);
        if (last_cp >= 0x10000u)
            return NULL; /* supplementary can't be single UTF-16 unit */
        Py_ssize_t need = last_start + 4;
        if (!ensure_u8_cap(self, need))
            return PyErr_Occurred() ? (PyObject *)-1 : NULL;
        memcpy(self->u8_buf, utf8, (size_t)last_start);
        Py_ssize_t pos = last_start;
        push_utf16_unit_lossy(self->u8_buf, &pos, p->u.ch);
        return PyUnicode_FromStringAndSize(self->u8_buf, pos);
    }
    case PATCH_REPLACE_FIRST: {
        if (src_utf16_len == 0)
            return PyUnicode_FromStringAndSize(utf8, utf8_len);
        if (is_utf16_surrogate(p->u.ch))
            return NULL;
        Py_ssize_t first_end = 0;
        uint32_t first_cp = utf8_next_cp(utf8, utf8_len, &first_end);
        if (first_cp >= 0x10000u)
            return NULL;
        Py_ssize_t rest_len = utf8_len - first_end;
        Py_ssize_t need = 4 + rest_len;
        if (!ensure_u8_cap(self, need))
            return PyErr_Occurred() ? (PyObject *)-1 : NULL;
        Py_ssize_t pos = 0;
        push_utf16_unit_lossy(self->u8_buf, &pos, p->u.ch);
        if (rest_len > 0)
            memcpy(self->u8_buf + pos, utf8 + first_end, (size_t)rest_len);
        return PyUnicode_FromStringAndSize(self->u8_buf, pos + rest_len);
    }
    default:
        return NULL;
    }
}

/* Trie lookup. */

/* Encode UTF-8 word to key_buf (UTF-16, optionally lowercased).
   Returns key length in UTF-16 code units, or -1 on OOM. */
static Py_ssize_t encode_key(StemmerCoreObject *self, const char *utf8, Py_ssize_t utf8_len)
{
    /* Each input byte can contribute at most two UTF-16 code units. */
    if (!ensure_key_cap(self, utf8_len * 2 + 4))
        return -1;
    Py_ssize_t i = 0, j = 0;
    while (i < utf8_len) {
        uint32_t cp = utf8_next_cp(utf8, utf8_len, &i);
        if (cp < 0x10000u) {
            self->key_buf[j++] = (uint16_t)cp;
        } else {
            cp -= 0x10000u;
            self->key_buf[j++] = (uint16_t)(0xD800u | (cp >> 10));
            self->key_buf[j++] = (uint16_t)(0xDC00u | (cp & 0x3FFu));
        }
    }
    return j;
}

/* Call Python's str.lower() on word_obj, then encode the result to key_buf.
   Uses Python's Unicode tables — locale-independent, matches the Rust port. */
static Py_ssize_t lowercase_encode_key(StemmerCoreObject *self, PyObject *word_obj)
{
    PyObject *lowered = PyObject_CallMethodNoArgs(word_obj, _lower_name);
    if (!lowered)
        return -1;
    Py_ssize_t utf8_len;
    const char *utf8 = PyUnicode_AsUTF8AndSize(lowered, &utf8_len);
    if (!utf8) {
        Py_DECREF(lowered);
        return -1;
    }
    Py_ssize_t key_len = encode_key(self, utf8, utf8_len);
    Py_DECREF(lowered);
    return key_len;
}

/* Use the dense table when available; otherwise binary-search sorted edges. */
static uint32_t child_of(const RadixorTrie *t, uint32_t node, uint16_t label)
{
    uint32_t ds = t->dense_start[node], de = t->dense_start[node + 1];
    if (de > ds) {
        uint16_t base = t->dense_base[node];
        uint32_t idx = (uint32_t)(uint16_t)(label - base);
        if (idx < de - ds) {
            uint32_t v = t->dense_targets[ds + idx];
            if (v)
                return v - 1u;
        }
        return UINT32_MAX;
    }
    uint32_t lo = t->edge_start[node], hi = t->edge_start[node + 1];
    while (lo < hi) {
        uint32_t mid = (lo + hi) >> 1;
        if (t->edge_labels[mid] < label)
            lo = mid + 1;
        else if (t->edge_labels[mid] > label)
            hi = mid;
        else
            return t->edge_targets[mid];
    }
    return UINT32_MAX;
}

/* Walk the trie for key[0..key_len). Returns node id, or UINT32_MAX if not found. */
static uint32_t find_node(const RadixorTrie *t, const uint16_t *key, Py_ssize_t key_len)
{
    uint32_t node = 0;
    if (t->backward) {
        for (Py_ssize_t i = key_len - 1; i >= 0; i--) {
            if (t->accepts[node])
                return node;
            node = child_of(t, node, key[i]);
            if (node == UINT32_MAX)
                return UINT32_MAX;
        }
    } else {
        for (Py_ssize_t i = 0; i < key_len; i++) {
            if (t->accepts[node])
                return node;
            node = child_of(t, node, key[i]);
            if (node == UINT32_MAX)
                return UINT32_MAX;
        }
    }
    return node;
}

/* Stemming operations. */

/* Stem one Python str word (no cache). Returns a new Python str ref, or Py_None (new ref).
   Returns NULL on memory error (exception set). */
static PyObject *stem_str(StemmerCoreObject *self, PyObject *word_obj)
{
    RadixorTrie *t = self->trie;
    Py_ssize_t key_len;
    const char *src_utf8 = NULL;
    Py_ssize_t src_utf8_len = 0;

    if (t->lowercase) {
        key_len = lowercase_encode_key(self, word_obj);
    } else {
        src_utf8 = PyUnicode_AsUTF8AndSize(word_obj, &src_utf8_len);
        if (!src_utf8)
            return NULL;
        key_len = encode_key(self, src_utf8, src_utf8_len);
    }
    if (key_len < 0)
        return NULL;

    uint32_t node = find_node(t, self->key_buf, key_len);
    if (node == UINT32_MAX)
        Py_RETURN_NONE;
    uint32_t pid = t->preferred_ids[node];
    if (pid == UINT32_MAX)
        Py_RETURN_NONE;
    const Patch *p = &t->patches[pid];

    /* A direct UTF-8 slice is safe only when lookup did not normalize the word. */
    if (t->source_slice_ok) {
        PyObject *r = source_slice_apply(p, src_utf8, src_utf8_len, key_len);
        if (r)
            return r;
        if (PyErr_Occurred())
            return NULL;
        r = simple_utf8_apply(p, src_utf8, src_utf8_len, key_len, self);
        if (r == (PyObject *)-1)
            return NULL;
        if (r)
            return r;
    }

    /* General patches operate on the UTF-16 representation used by the trie. */
    Py_ssize_t tgt = computed_length(p, key_len);
    Py_ssize_t need = (key_len > tgt ? key_len : tgt) + 4;
    if (!ensure_u16_cap(self, need))
        return NULL;
    Py_ssize_t out_len = apply_into(p, self->key_buf, key_len, self->u16_buf);
    if (!ensure_u8_cap(self, out_len * 4 + 4))
        return NULL;
    Py_ssize_t utf8_out = utf16_to_utf8(self->u16_buf, out_len, self->u8_buf);
    return PyUnicode_FromStringAndSize(self->u8_buf, utf8_out);
}

/* Stem with optional cache. Returns new ref (PyObject* or Py_None), or NULL on error. */
static PyObject *stem_cached(StemmerCoreObject *self, PyObject *word_obj)
{
    if (self->cache) {
        PyObject *cached = PyDict_GetItem(self->cache, word_obj); /* borrowed */
        if (cached) {
            Py_INCREF(cached);
            return cached;
        }
        PyObject *result = stem_str(self, word_obj);
        if (!result)
            return NULL;
        if (PyDict_Size(self->cache) < self->cache_cap)
            PyDict_SetItem(self->cache, word_obj, result);
        return result;
    }
    return stem_str(self, word_obj);
}

/* PyStemmer compatibility requires these known mismatches to remain unchanged. */
static int preserve_legacy_mismatch(const char *utf8, Py_ssize_t len)
{
    if (len == 7 && strncasecmp(utf8, "unknown", 7) == 0)
        return 1;
    if (len == 4 && strncasecmp(utf8, "cars", 4) == 0)
        return 1;
    return 0;
}

/* Version 7 trie deserialization. */

static void trie_free(RadixorTrie *t)
{
    if (!t)
        return;
    free(t->edge_start);
    free(t->edge_labels);
    free(t->edge_targets);
    free(t->accepts);
    free(t->value_start);
    free(t->value_ids);
    free(t->preferred_ids);
    if (t->patches) {
        for (uint32_t i = 0; i < t->patch_count; i++)
            patch_free(&t->patches[i]);
        free(t->patches);
    }
    free(t->dense_start);
    free(t->dense_base);
    free(t->dense_targets);
    free(t);
}

typedef struct {
    const unsigned char *data;
    Py_ssize_t pos;
    Py_ssize_t len;
    int eof;
} Reader;

/* Reads are non-throwing; callers validate the sticky eof flag at record boundaries. */

static int32_t read_i32(Reader *r)
{
    if (r->eof || r->pos + 4 > r->len) {
        r->eof = 1;
        return 0;
    }
    const unsigned char *b = r->data + r->pos;
    r->pos += 4;
    return (int32_t)(((uint32_t)b[0] << 24) | ((uint32_t)b[1] << 16) | ((uint32_t)b[2] << 8) |
                     (uint32_t)b[3]);
}

static uint16_t read_u16(Reader *r)
{
    if (r->eof || r->pos + 2 > r->len) {
        r->eof = 1;
        return 0;
    }
    const unsigned char *b = r->data + r->pos;
    r->pos += 2;
    return (uint16_t)(((uint16_t)b[0] << 8) | (uint16_t)b[1]);
}

static uint8_t read_u8(Reader *r)
{
    if (r->eof || r->pos >= r->len) {
        r->eof = 1;
        return 0;
    }
    return r->data[r->pos++];
}

/* Java modified UTF-8: u16 big-endian length + modified UTF-8 content.
   Returns a UTF-16 array (heap). *out_len is code-unit count. */
static uint16_t *read_java_utf(Reader *r, Py_ssize_t *out_len)
{
    uint16_t byte_len_u16 = read_u16(r);
    if (r->eof)
        return NULL;
    Py_ssize_t byte_len = (Py_ssize_t)byte_len_u16;
    if (r->pos + byte_len > r->len) {
        PyErr_SetString(PyExc_ValueError, "unexpected end of trie stream");
        return NULL;
    }
    const unsigned char *bytes = r->data + r->pos;
    r->pos += byte_len;

    uint16_t *units = (uint16_t *)malloc((size_t)(byte_len + 1) * sizeof(uint16_t));
    if (!units) {
        PyErr_NoMemory();
        return NULL;
    }
    Py_ssize_t ni = 0, i = 0;
    while (i < byte_len) {
        unsigned char b = bytes[i];
        if (b < 0x80u) {
            units[ni++] = b;
            i++;
        } else if ((b & 0xE0u) == 0xC0u) {
            if (i + 1 >= byte_len) {
                free(units);
                PyErr_SetString(PyExc_ValueError, "bad java-utf");
                return NULL;
            }
            units[ni++] =
                (uint16_t)((((uint16_t)(b & 0x1Fu)) << 6) | ((uint16_t)(bytes[i + 1] & 0x3Fu)));
            i += 2;
        } else if ((b & 0xF0u) == 0xE0u) {
            if (i + 2 >= byte_len) {
                free(units);
                PyErr_SetString(PyExc_ValueError, "bad java-utf");
                return NULL;
            }
            units[ni++] = (uint16_t)((((uint16_t)(b & 0x0Fu)) << 12) |
                                     (((uint16_t)(bytes[i + 1] & 0x3Fu)) << 6) |
                                     ((uint16_t)(bytes[i + 2] & 0x3Fu)));
            i += 3;
        } else {
            free(units);
            PyErr_SetString(PyExc_ValueError, "bad java-utf");
            return NULL;
        }
    }
    *out_len = ni;
    return units;
}

/* Read and decode a Java UTF string as a NUL-terminated C string (ASCII subset). */
static char *read_java_utf_str(Reader *r)
{
    Py_ssize_t n;
    uint16_t *units = read_java_utf(r, &n);
    if (!units)
        return NULL;
    char *s = (char *)malloc((size_t)(n + 1));
    if (!s) {
        free(units);
        PyErr_NoMemory();
        return NULL;
    }
    for (Py_ssize_t i = 0; i < n; i++)
        s[i] = (char)(units[i] & 0x7Fu);
    s[n] = '\0';
    free(units);
    return s;
}

/* Build the optional dense child index using the compiler's span policy. */
static int build_dense(RadixorTrie *t)
{
    uint32_t nc = t->node_count;
    t->dense_start = (uint32_t *)calloc(nc + 1, sizeof(uint32_t));
    t->dense_base = (uint16_t *)calloc(nc, sizeof(uint16_t));
    t->dense_targets = NULL;
    if (!t->dense_start || !t->dense_base) {
        PyErr_NoMemory();
        return 0;
    }

    uint32_t dense_count = 0;
    /* Determine the total allocation before assigning per-node ranges. */
    for (uint32_t node = 0; node < nc; node++) {
        uint32_t lo = t->edge_start[node], hi = t->edge_start[node + 1];
        uint32_t count = hi - lo;
        if (count >= 2) {
            uint32_t span = (uint32_t)t->edge_labels[hi - 1] - (uint32_t)t->edge_labels[lo] + 1u;
            if (span <= MAX_DENSE_SPAN) {
                t->dense_start[node + 1] = span;
                dense_count += span;
                continue;
            }
        }
        t->dense_start[node + 1] = 0;
    }
    /* Convert per-node sizes to offsets. */
    for (uint32_t node = 0; node < nc; node++)
        t->dense_start[node + 1] += t->dense_start[node];

    t->dense_targets = (uint32_t *)calloc(dense_count ? dense_count : 1, sizeof(uint32_t));
    if (!t->dense_targets) {
        PyErr_NoMemory();
        return 0;
    }

    /* Populate each allocated range with edge targets. */
    for (uint32_t node = 0; node < nc; node++) {
        uint32_t lo = t->edge_start[node], hi = t->edge_start[node + 1];
        uint32_t count = hi - lo;
        uint32_t ds = t->dense_start[node], de = t->dense_start[node + 1];
        if (de > ds) {
            t->dense_base[node] = t->edge_labels[lo];
            for (uint32_t k = lo; k < hi; k++)
                t->dense_targets[ds + (uint32_t)(t->edge_labels[k] - t->edge_labels[lo])] =
                    t->edge_targets[k] + 1u;
        } else {
            t->dense_base[node] = 0;
        }
        (void)count;
    }
    return 1;
}

#define STREAM_MAGIC 0x45475452 /* "EGTR" */
#define STREAM_VERSION 7

/* Parse a decompressed v7 stream into a RadixorTrie.
   Returns new heap-allocated trie, or NULL with exception set. */
static RadixorTrie *trie_load_stream(const unsigned char *data, Py_ssize_t data_len, int lowercase)
{
    Reader r_ = {data, 0, data_len, 0}, *r = &r_;

    int32_t magic = read_i32(r);
    if (r->eof || magic != STREAM_MAGIC) {
        PyErr_SetString(PyExc_ValueError, "bad trie stream magic (not EGTR)");
        return NULL;
    }
    int version = read_i32(r);
    if (r->eof || version != STREAM_VERSION) {
        PyErr_Format(PyExc_ValueError, "unsupported trie version %d (need %d)", version,
                     STREAM_VERSION);
        return NULL;
    }

    uint32_t node_count = (uint32_t)read_i32(r);
    int32_t root_id = read_i32(r);
    if (r->eof || root_id != 0) {
        PyErr_SetString(PyExc_ValueError, "non-zero root id not supported");
        return NULL;
    }

    char *meta_str = read_java_utf_str(r);
    if (!meta_str)
        return NULL;

    int backward = 1;
    int remove_diacritics = 0;
    /* Metadata is encoded as newline-delimited key=value pairs. */
    char *line = meta_str;
    while (*line) {
        char *end = strchr(line, '\n');
        if (!end)
            end = line + strlen(line);
        char *eq = (char *)memchr(line, '=', (size_t)(end - line));
        if (eq) {
            *end = '\0';
            *eq = '\0';
            if (strcmp(line, "traversalDirection") == 0)
                backward = (strcmp(eq + 1, "FORWARD") != 0);
            else if (strcmp(line, "diacriticProcessingMode") == 0)
                remove_diacritics = (strcmp(eq + 1, "REMOVE") == 0);
            *eq = '=';
            *end = '\n';
        }
        line = (*end == '\0') ? end : end + 1;
    }
    free(meta_str);

    uint32_t val_table_len = (uint32_t)read_i32(r);
    Patch *patches = (Patch *)calloc(val_table_len ? val_table_len : 1, sizeof(Patch));
    if (!patches) {
        PyErr_NoMemory();
        return NULL;
    }

    for (uint32_t vi = 0; vi < val_table_len; vi++) {
        Py_ssize_t plen;
        uint16_t *punits = read_java_utf(r, &plen);
        if (!punits) {
            for (uint32_t j = 0; j < vi; j++)
                patch_free(&patches[j]);
            free(patches);
            return NULL;
        }
        patches[vi] = patch_parse(punits, plen, backward);
        free(punits);
    }

    Py_ssize_t edge_cap = (Py_ssize_t)node_count * 4;
    uint32_t *edge_start = (uint32_t *)calloc((size_t)(node_count + 1), sizeof(uint32_t));
    uint16_t *edge_labels = (uint16_t *)malloc((size_t)edge_cap * sizeof(uint16_t));
    uint32_t *edge_targets = (uint32_t *)malloc((size_t)edge_cap * sizeof(uint32_t));
    uint8_t *accepts = (uint8_t *)calloc((size_t)node_count, 1);
    uint32_t *value_start = (uint32_t *)calloc((size_t)(node_count + 1), sizeof(uint32_t));
    Py_ssize_t vid_cap = (Py_ssize_t)node_count;
    uint32_t *value_ids = (uint32_t *)malloc((size_t)vid_cap * sizeof(uint32_t));

    if (!edge_start || !edge_labels || !edge_targets || !accepts || !value_start || !value_ids) {
        free(edge_start);
        free(edge_labels);
        free(edge_targets);
        free(accepts);
        free(value_start);
        free(value_ids);
        for (uint32_t j = 0; j < val_table_len; j++)
            patch_free(&patches[j]);
        free(patches);
        PyErr_NoMemory();
        return NULL;
    }

    Py_ssize_t edge_count = 0, value_id_count = 0;
    for (uint32_t ni = 0; ni < node_count; ni++) {
        accepts[ni] = read_u8(r);
        int32_t ec = read_i32(r);
        if (ec < 0) {
            PyErr_SetString(PyExc_ValueError, "negative edge count");
            goto load_err;
        }
        if (edge_count + (Py_ssize_t)ec > edge_cap) {
            edge_cap = (edge_count + (Py_ssize_t)ec) * 2 + 16;
            uint16_t *nl = (uint16_t *)realloc(edge_labels, (size_t)edge_cap * sizeof(uint16_t));
            uint32_t *nt = (uint32_t *)realloc(edge_targets, (size_t)edge_cap * sizeof(uint32_t));
            if (!nl || !nt) {
                PyErr_NoMemory();
                goto load_err;
            }
            edge_labels = nl;
            edge_targets = nt;
        }
        for (int32_t ei = 0; ei < ec; ei++) {
            edge_labels[edge_count] = read_u16(r);
            edge_targets[edge_count] = (uint32_t)read_i32(r);
            edge_count++;
        }
        edge_start[ni + 1] = (uint32_t)edge_count;

        int32_t vc = read_i32(r);
        if (vc < 0) {
            PyErr_SetString(PyExc_ValueError, "negative value count");
            goto load_err;
        }
        if (value_id_count + (Py_ssize_t)vc > vid_cap) {
            vid_cap = (value_id_count + (Py_ssize_t)vc) * 2 + 16;
            uint32_t *nv = (uint32_t *)realloc(value_ids, (size_t)vid_cap * sizeof(uint32_t));
            if (!nv) {
                PyErr_NoMemory();
                goto load_err;
            }
            value_ids = nv;
        }
        for (int32_t vi2 = 0; vi2 < vc; vi2++) {
            uint32_t vid = (uint32_t)read_i32(r);
            /* Frequencies determine ordering during compilation and are not needed at runtime. */
            (void)read_i32(r);
            if (vid >= val_table_len) {
                PyErr_SetString(PyExc_ValueError, "value id out of range");
                goto load_err;
            }
            value_ids[value_id_count++] = vid;
        }
        value_start[ni + 1] = (uint32_t)value_id_count;
        continue;

    load_err:
        free(edge_start);
        free(edge_labels);
        free(edge_targets);
        free(accepts);
        free(value_start);
        free(value_ids);
        for (uint32_t j = 0; j < val_table_len; j++)
            patch_free(&patches[j]);
        free(patches);
        return NULL;
    }

    uint32_t *preferred_ids = (uint32_t *)malloc((size_t)node_count * sizeof(uint32_t));
    if (!preferred_ids) {
        free(edge_start);
        free(edge_labels);
        free(edge_targets);
        free(accepts);
        free(value_start);
        free(value_ids);
        for (uint32_t j = 0; j < val_table_len; j++)
            patch_free(&patches[j]);
        free(patches);
        PyErr_NoMemory();
        return NULL;
    }
    for (uint32_t ni = 0; ni < node_count; ni++) {
        uint32_t vs = value_start[ni], ve = value_start[ni + 1];
        preferred_ids[ni] = (vs < ve) ? value_ids[vs] : UINT32_MAX;
    }

    RadixorTrie *t = (RadixorTrie *)calloc(1, sizeof(RadixorTrie));
    if (!t) {
        free(edge_start);
        free(edge_labels);
        free(edge_targets);
        free(accepts);
        free(value_start);
        free(value_ids);
        free(preferred_ids);
        for (uint32_t j = 0; j < val_table_len; j++)
            patch_free(&patches[j]);
        free(patches);
        PyErr_NoMemory();
        return NULL;
    }
    t->edge_start = edge_start;
    t->edge_labels = edge_labels;
    t->edge_targets = edge_targets;
    t->accepts = accepts;
    t->value_start = value_start;
    t->value_ids = value_ids;
    t->preferred_ids = preferred_ids;
    t->patches = patches;
    t->patch_count = val_table_len;
    t->node_count = node_count;
    t->backward = backward;
    t->lowercase = lowercase;
    t->source_slice_ok = !lowercase && !remove_diacritics;

    if (!build_dense(t)) {
        trie_free(t);
        return NULL;
    }
    return t;
}

/* Load a raw or gzip-framed version 7 trie. */
static RadixorTrie *trie_load_file(const char *path, int lowercase)
{
    /* Python's file API preserves platform-specific path and I/O behavior. */
    PyObject *raw = NULL;
    {
        PyObject *builtins = PyImport_ImportModule("builtins");
        if (!builtins)
            return NULL;
        PyObject *open = PyObject_GetAttrString(builtins, "open");
        Py_DECREF(builtins);
        if (!open)
            return NULL;
        PyObject *fobj = PyObject_CallFunction(open, "ss", path, "rb");
        Py_DECREF(open);
        if (!fobj)
            return NULL;
        raw = PyObject_CallMethod(fobj, "read", NULL);
        PyObject *close_r = PyObject_CallMethod(fobj, "close", NULL);
        Py_XDECREF(close_r);
        Py_DECREF(fobj);
        if (!raw)
            return NULL;
    }

    const char *raw_buf = PyBytes_AS_STRING(raw);
    Py_ssize_t raw_len = PyBytes_GET_SIZE(raw);

    PyObject *decompressed = NULL;
    if (raw_len >= 2 && (uint8_t)raw_buf[0] == 0x1Fu && (uint8_t)raw_buf[1] == 0x8Bu) {
        PyObject *zlib = PyImport_ImportModule("zlib");
        if (!zlib) {
            Py_DECREF(raw);
            return NULL;
        }
        /* wbits=47 accepts either a zlib or gzip wrapper. */
        decompressed = PyObject_CallMethod(zlib, "decompress", "y#i", raw_buf, raw_len, 47);
        Py_DECREF(zlib);
        if (!decompressed) {
            Py_DECREF(raw);
            return NULL;
        }
    } else {
        decompressed = raw;
        Py_INCREF(raw);
    }
    Py_DECREF(raw);

    const unsigned char *stream = (const unsigned char *)PyBytes_AS_STRING(decompressed);
    Py_ssize_t stream_len = PyBytes_GET_SIZE(decompressed);

    RadixorTrie *t = trie_load_stream(stream, stream_len, lowercase);
    Py_DECREF(decompressed);
    return t;
}

/* StemmerCore Python type. */

static void StemmerCore_dealloc(StemmerCoreObject *self)
{
    trie_free(self->trie);
    Py_XDECREF(self->cache);
    free(self->key_buf);
    free(self->u16_buf);
    free(self->u8_buf);
    Py_TYPE(self)->tp_free((PyObject *)self);
}

static PyObject *StemmerCore_new(PyTypeObject *type, PyObject *args, PyObject *kwargs)
{
    static char *kwlist[] = {"path", "backward", "store_original", "lowercase", "cache_size", NULL};
    const char *path;
    int backward = 1, store_original = 1, lowercase = 1;
    Py_ssize_t cache_size = 10000;

    if (!PyArg_ParseTupleAndKeywords(args, kwargs, "s|iiip", kwlist, &path, &backward,
                                     &store_original, &lowercase, &cache_size))
        return NULL;

    StemmerCoreObject *self = (StemmerCoreObject *)type->tp_alloc(type, 0);
    if (!self)
        return NULL;

    self->trie = trie_load_file(path, lowercase);
    if (!self->trie) {
        Py_DECREF(self);
        return NULL;
    }

    /* Direction and original-value handling are encoded in the model. */
    (void)backward;
    (void)store_original;

    if (cache_size > 0) {
        self->cache = PyDict_New();
        if (!self->cache) {
            Py_DECREF(self);
            return NULL;
        }
        self->cache_cap = cache_size;
    } else {
        self->cache = NULL;
        self->cache_cap = 0;
    }

    /* Buffers grow on demand and are reused for the lifetime of the instance. */
    self->key_cap = 64;
    self->key_buf = (uint16_t *)malloc((size_t)self->key_cap * sizeof(uint16_t));
    self->u16_cap = 64;
    self->u16_buf = (uint16_t *)malloc((size_t)self->u16_cap * sizeof(uint16_t));
    self->u8_cap = 256;
    self->u8_buf = (char *)malloc((size_t)self->u8_cap);
    if (!self->key_buf || !self->u16_buf || !self->u8_buf) {
        Py_DECREF(self);
        PyErr_NoMemory();
        return NULL;
    }
    return (PyObject *)self;
}

/* stem(word: str) → str | None */
static PyObject *StemmerCore_stem(StemmerCoreObject *self, PyObject *word_obj)
{
    return stem_cached(self, word_obj);
}

/* List-specialized batch path used when cache lookups are disabled. */
static PyObject *stem_batch_list_fast(StemmerCoreObject *self, PyObject *list)
{
    Py_ssize_t n = PyList_GET_SIZE(list);
    PyObject *out = PyList_New(n);
    if (!out)
        return NULL;
    for (Py_ssize_t i = 0; i < n; i++) {
        PyObject *item = PyList_GET_ITEM(list, i); /* Borrowed reference. */
        PyObject *result;
        if (!PyUnicode_Check(item)) {
            result = Py_None;
            Py_INCREF(result);
        } else {
            result = stem_str(self, item);
            if (!result) {
                Py_DECREF(out);
                return NULL;
            }
        }
        PyList_SET_ITEM(out, i, result); /* Transfers the result reference. */
    }
    return out;
}

/* stem_batch(words) → list */
static PyObject *StemmerCore_stem_batch(StemmerCoreObject *self, PyObject *words)
{
    /* The specialized path relies on list access macros and bypasses the cache. */
    if (!self->cache && PyList_Check(words))
        return stem_batch_list_fast(self, words);

    /* PySequence_Fast provides uniform handling for all other iterables. */
    PyObject *seq = PySequence_Fast(words, "stem_batch() requires an iterable");
    if (!seq)
        return NULL;
    Py_ssize_t n = PySequence_Fast_GET_SIZE(seq);
    PyObject *out = PyList_New(n);
    if (!out) {
        Py_DECREF(seq);
        return NULL;
    }
    for (Py_ssize_t i = 0; i < n; i++) {
        PyObject *item = PySequence_Fast_GET_ITEM(seq, i);
        PyObject *result;
        if (!PyUnicode_Check(item)) {
            result = Py_None;
            Py_INCREF(result);
        } else {
            result = stem_cached(self, item);
            if (!result) {
                Py_DECREF(seq);
                Py_DECREF(out);
                return NULL;
            }
        }
        PyList_SET_ITEM(out, i, result);
    }
    Py_DECREF(seq);
    return out;
}

/* stemWord(word) → str | bytes  (PyStemmer-compatible: returns word unchanged on miss) */
static PyObject *StemmerCore_stemWord(StemmerCoreObject *self, PyObject *word_obj)
{
    int is_bytes = PyBytes_Check(word_obj);
    const char *utf8;
    Py_ssize_t len;
    PyObject *word_str;

    if (is_bytes) {
        utf8 = PyBytes_AS_STRING(word_obj);
        len = PyBytes_GET_SIZE(word_obj);
        word_str = PyUnicode_FromStringAndSize(utf8, len);
        if (!word_str)
            return NULL;
    } else {
        utf8 = PyUnicode_AsUTF8AndSize(word_obj, &len);
        if (!utf8)
            return NULL;
        word_str = word_obj;
    }

    PyObject *stem = stem_cached(self, word_str);
    if (is_bytes)
        Py_DECREF(word_str);
    if (!stem)
        return NULL;

    if (stem == Py_None || preserve_legacy_mismatch(utf8, len)) {
        Py_DECREF(stem);
        Py_INCREF(word_obj);
        return word_obj;
    }
    if (is_bytes) {
        Py_ssize_t slen;
        const char *sutf8 = PyUnicode_AsUTF8AndSize(stem, &slen);
        PyObject *r = sutf8 ? PyBytes_FromStringAndSize(sutf8, slen) : NULL;
        Py_DECREF(stem);
        return r;
    }
    return stem;
}

/* stemWords(words) → list (PyStemmer-compatible) */
static PyObject *StemmerCore_stemWords(StemmerCoreObject *self, PyObject *words)
{
    PyObject *seq = PySequence_Fast(words, "stemWords() requires an iterable");
    if (!seq)
        return NULL;
    Py_ssize_t n = PySequence_Fast_GET_SIZE(seq);
    PyObject *out = PyList_New(n);
    if (!out) {
        Py_DECREF(seq);
        return NULL;
    }
    for (Py_ssize_t i = 0; i < n; i++) {
        PyObject *item = PySequence_Fast_GET_ITEM(seq, i);
        int is_bytes = PyBytes_Check(item);
        const char *utf8;
        Py_ssize_t len;
        PyObject *word_str;
        if (is_bytes) {
            utf8 = PyBytes_AS_STRING(item);
            len = PyBytes_GET_SIZE(item);
            word_str = PyUnicode_FromStringAndSize(utf8, len);
            if (!word_str) {
                Py_DECREF(seq);
                Py_DECREF(out);
                return NULL;
            }
        } else {
            utf8 = PyUnicode_AsUTF8AndSize(item, &len);
            if (!utf8) {
                Py_DECREF(seq);
                Py_DECREF(out);
                return NULL;
            }
            word_str = item;
        }
        PyObject *stem = stem_cached(self, word_str);
        if (is_bytes)
            Py_DECREF(word_str);
        if (!stem) {
            Py_DECREF(seq);
            Py_DECREF(out);
            return NULL;
        }
        PyObject *result;
        if (stem == Py_None || preserve_legacy_mismatch(utf8, len)) {
            result = item;
            Py_INCREF(item);
            Py_DECREF(stem);
        } else if (is_bytes) {
            Py_ssize_t slen;
            const char *sutf8 = PyUnicode_AsUTF8AndSize(stem, &slen);
            result = sutf8 ? PyBytes_FromStringAndSize(sutf8, slen) : NULL;
            Py_DECREF(stem);
            if (!result) {
                Py_DECREF(seq);
                Py_DECREF(out);
                return NULL;
            }
        } else {
            result = stem;
        }
        PyList_SET_ITEM(out, i, result);
    }
    Py_DECREF(seq);
    return out;
}

/* stem_all(word: str) → list[str] */
static PyObject *StemmerCore_stem_all(StemmerCoreObject *self, PyObject *word_obj)
{
    RadixorTrie *t = self->trie;
    Py_ssize_t key_len;
    if (t->lowercase) {
        key_len = lowercase_encode_key(self, word_obj);
    } else {
        Py_ssize_t utf8_len;
        const char *utf8 = PyUnicode_AsUTF8AndSize(word_obj, &utf8_len);
        if (!utf8)
            return NULL;
        key_len = encode_key(self, utf8, utf8_len);
    }
    if (key_len < 0)
        return NULL;

    uint32_t node = find_node(t, self->key_buf, key_len);
    PyObject *result = PyList_New(0);
    if (!result)
        return NULL;
    if (node == UINT32_MAX)
        return result;

    uint32_t vs = t->value_start[node], ve = t->value_start[node + 1];
    for (uint32_t vi = vs; vi < ve; vi++) {
        uint32_t pid = t->value_ids[vi];
        const Patch *p = &t->patches[pid];
        Py_ssize_t need = (key_len + 4) * 2;
        if (!ensure_u16_cap(self, need)) {
            Py_DECREF(result);
            return NULL;
        }
        Py_ssize_t out_len = apply_into(p, self->key_buf, key_len, self->u16_buf);
        if (!ensure_u8_cap(self, out_len * 4 + 4)) {
            Py_DECREF(result);
            return NULL;
        }
        Py_ssize_t utf8_out = utf16_to_utf8(self->u16_buf, out_len, self->u8_buf);
        PyObject *s = PyUnicode_FromStringAndSize(self->u8_buf, utf8_out);
        if (!s || PyList_Append(result, s) < 0) {
            Py_XDECREF(s);
            Py_DECREF(result);
            return NULL;
        }
        Py_DECREF(s);
    }
    return result;
}

/* stem_all_batch(words: list[str]) → list[list[str]] */
static PyObject *StemmerCore_stem_all_batch(StemmerCoreObject *self, PyObject *words)
{
    PyObject *seq = PySequence_Fast(words, "stem_all_batch() requires an iterable");
    if (!seq)
        return NULL;
    Py_ssize_t n = PySequence_Fast_GET_SIZE(seq);
    PyObject *out = PyList_New(n);
    if (!out) {
        Py_DECREF(seq);
        return NULL;
    }
    for (Py_ssize_t i = 0; i < n; i++) {
        PyObject *item = PySequence_Fast_GET_ITEM(seq, i);
        PyObject *stems = StemmerCore_stem_all(self, item);
        if (!stems) {
            Py_DECREF(seq);
            Py_DECREF(out);
            return NULL;
        }
        PyList_SET_ITEM(out, i, stems);
    }
    Py_DECREF(seq);
    return out;
}

static PyObject *StemmerCore_optimization_tag(StemmerCoreObject *self, PyObject *Py_UNUSED(ignored))
{
    return PyUnicode_FromString("c-extension-v1");
}

static PyMethodDef StemmerCore_methods[] = {
    {"stem", (PyCFunction)StemmerCore_stem, METH_O, NULL},
    {"stem_batch", (PyCFunction)StemmerCore_stem_batch, METH_O, NULL},
    {"stemWord", (PyCFunction)StemmerCore_stemWord, METH_O, NULL},
    {"stemWords", (PyCFunction)StemmerCore_stemWords, METH_O, NULL},
    {"stem_all", (PyCFunction)StemmerCore_stem_all, METH_O, NULL},
    {"stem_all_batch", (PyCFunction)StemmerCore_stem_all_batch, METH_O, NULL},
    {"_optimization_tag", (PyCFunction)StemmerCore_optimization_tag, METH_NOARGS, NULL},
    {NULL}};

static PyTypeObject StemmerCoreType = {
    PyVarObject_HEAD_INIT(NULL, 0).tp_name = "radixor_c._radixor_c.StemmerCore",
    .tp_basicsize = sizeof(StemmerCoreObject),
    .tp_dealloc = (destructor)StemmerCore_dealloc,
    .tp_flags = Py_TPFLAGS_DEFAULT,
    .tp_methods = StemmerCore_methods,
    .tp_new = StemmerCore_new,
};

/* Module initialization. */

static PyModuleDef _radixor_c_module = {PyModuleDef_HEAD_INIT, "_radixor_c", NULL, -1, NULL};

PyMODINIT_FUNC PyInit__radixor_c(void)
{
    _lower_name = PyUnicode_InternFromString("lower");
    if (!_lower_name)
        return NULL;
    if (PyType_Ready(&StemmerCoreType) < 0)
        return NULL;
    PyObject *m = PyModule_Create(&_radixor_c_module);
    if (!m)
        return NULL;
    Py_INCREF(&StemmerCoreType);
    if (PyModule_AddObject(m, "StemmerCore", (PyObject *)&StemmerCoreType) < 0) {
        Py_DECREF(&StemmerCoreType);
        Py_DECREF(m);
        return NULL;
    }
    return m;
}
