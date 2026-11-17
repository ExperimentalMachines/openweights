// The MediaTek NPU as a third processor for pd_bench_mtk: MediaTek's LlamaRuntime (ExecuTorch's
// examples/mediatek, with tools/npu/patches/executorch-pd-1.6.0.patch and patch_bench.py applied)
// driven turn by turn, and its state converted to and from llama.cpp's per-sequence state so a
// turn can prefill on one processor and decode on another, as pd_bench does on Snapdragon.
//
// What the conversion has to respect, each found in the source rather than assumed:
//   * The NPU's attention caches are fp32 [1, kv_heads, window, head_dim], filled from the right:
//     the newest position is the last column (LlamaModelChunk::RollbackCache). MediaTek's
//     "uniform" layout gives each chunk its layers' Ks, then their Vs; the "per-layer" layout of
//     the LFM2 export gives each layer in order: a [1, dim, 2] conv state for a conv layer, a K
//     then a V for an attention layer.
//   * llama.cpp's state for one sequence starts with a magic (0xaf143cd8) and the source sequence
//     id (llama_context::state_seq_get_data), then the memory's own state (b10549):
//       - a KV cache (llama_kv_cache::state_write): one stream; per cell its position and
//         sequence; then per layer the K rows (one per cell, n_embd_k_gqa wide, head-major), then
//         V the same way or transposed (one row per embedding element across all cells), in the
//         destination's own V layout. Only the layers the cache holds are written.
//       - Gemma 3's sliding-window cache (llama_kv_cache_iswa): the global layers' cache, then the
//         sliding layers' cache, which writes only the cells the window has not masked
//         (is_masked_swa: kept when pos_max - pos < n_swa).
//       - LFM2's hybrid (llama_memory_hybrid): the attention layers' KV cache, then the recurrent
//         memory: a cell count, per cell its position and a zero sequence count, then s_trans,
//         the model's layer count, and per conv layer its state row (r_l: per channel the last
//         two positions, oldest first, models/lfm2.cpp), then the same layers' s rows (empty for
//         LFM2). The NPU export's conv state is the same [channel][2] order (the app's JNI copies
//         it whole into the CPU export, docs/research/npu-pd-disaggregation.md).
//   * llama.cpp's converter permutes Llama-architecture Q and K weights so its interleaved rope
//     matches Hugging Face's rotate-half one; the cached K is therefore in a permuted order:
//     element 2i holds Hugging Face element i and 2i+1 holds i + head_dim/2. Qwen3, Gemma 3 and
//     LFM2 use the NeoX rope in llama.cpp and are not permuted. V is never permuted.
#pragma once

#include "llama_runner/LlamaConfig.h"
#include "llama_runner/LlamaModelChunk.h"
#include "llama_runner/LlamaRuntime.h"
#include "llama_runner/Utils.h"

#include <executorch/runtime/platform/runtime.h>

#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <map>
#include <sstream>
#include <string>
#include <vector>

#include "ggml.h"

namespace npu {

using example::LlamaModelChunk;

// runner.txt: key=value lines written from the export's config.json runner block (lists are
// comma-separated), so this binary needs no JSON reader.
static std::map<std::string, std::string> read_kv(const std::string & path) {
    std::map<std::string, std::string> m;
    std::ifstream f(path); std::string line;
    while (std::getline(f, line)) {
        auto k = line.find('=');
        if (k != std::string::npos) m[line.substr(0, k)] = line.substr(k + 1);
    }
    return m;
}
static std::vector<std::string> split_list(const std::string & s, const std::string & dir) {
    std::vector<std::string> out; std::stringstream ss(s); std::string x;
    while (std::getline(ss, x, ',')) if (!x.empty()) out.push_back(dir + "/" + x);
    return out;
}

// What memory the llama.cpp side keeps, which decides the state's sections.
enum class MemKind { Kv, Iswa, Hybrid };

struct Reader {
    const uint8_t * p; size_t n, i = 0; bool ok = true;
    template <class T> T get() { T v{}; if (i + sizeof(T) > n) { ok = false; return v; } memcpy(&v, p + i, sizeof(T)); i += sizeof(T); return v; }
    const uint8_t * skip(size_t k) { if (i + k > n) { ok = false; return nullptr; } const uint8_t * q = p + i; i += k; return q; }
};

struct KvSection {
    std::vector<int32_t> pos;                    // per cell, in cache order
    uint32_t v_trans = 0, n_layer = 0;
    std::vector<int32_t> k_type, v_type;
    std::vector<uint64_t> k_row, v_row;          // bytes per cell row (v_row unused when transposed)
    std::vector<uint32_t> v_el, v_embd;          // transposed V: element size and rows
    std::vector<const uint8_t *> k, v;           // each layer's block in the parsed buffer
};
struct RecSection {
    std::vector<int32_t> pos;
    uint32_t s_trans = 0, n_layer_total = 0;
    std::vector<int32_t> r_type, s_type;
    std::vector<uint64_t> r_row, s_row;
    std::vector<const uint8_t *> r, s;
};
// A parsed state; parsed from a destination's own small state it is that destination's layout.
struct ParsedState {
    MemKind kind = MemKind::Kv;
    std::vector<KvSection> kv;
    bool has_rec = false;
    RecSection rec;
};
using KvLayout = ParsedState;

static constexpr uint32_t kStateMagic = 0xaf143cd8;

static bool parse_kv(Reader & r, KvSection & s, std::string & err) {
    if (r.get<uint32_t>() != 1 || !r.ok) { err = "state: not one stream"; return false; }
    uint32_t cells = r.get<uint32_t>();
    s.pos.clear();
    for (uint32_t c = 0; c < cells && r.ok; c++) {
        int32_t p = r.get<int32_t>(); uint32_t ns = r.get<uint32_t>();
        if (ns != 1) { err = "state: a cell with more than one sequence"; return false; }
        r.get<int32_t>();
        s.pos.push_back(p);
    }
    if (cells == 0) return r.ok;
    s.v_trans = r.get<uint32_t>(); s.n_layer = r.get<uint32_t>();
    for (uint32_t l = 0; l < s.n_layer && r.ok; l++) {
        s.k_type.push_back(r.get<int32_t>()); s.k_row.push_back(r.get<uint64_t>());
        s.k.push_back(r.skip(s.k_row.back() * cells));
    }
    for (uint32_t l = 0; l < s.n_layer && r.ok; l++) {
        s.v_type.push_back(r.get<int32_t>());
        if (!s.v_trans) {
            s.v_row.push_back(r.get<uint64_t>()); s.v_el.push_back(0); s.v_embd.push_back(0);
            s.v.push_back(r.skip(s.v_row.back() * cells));
        } else {
            s.v_el.push_back(r.get<uint32_t>()); s.v_embd.push_back(r.get<uint32_t>()); s.v_row.push_back(0);
            s.v.push_back(r.skip((size_t) s.v_el.back() * s.v_embd.back() * cells));
        }
    }
    return r.ok;
}

static bool parse_rec(Reader & r, RecSection & s, uint32_t attn_layers, std::string & err) {
    uint32_t cells = r.get<uint32_t>();
    for (uint32_t c = 0; c < cells && r.ok; c++) {
        s.pos.push_back(r.get<int32_t>());
        uint32_t ns = r.get<uint32_t>();
        for (uint32_t k = 0; k < ns; k++) r.get<int32_t>();
    }
    if (cells != 1) { err = "state: the recurrent memory does not hold one cell"; return false; }
    s.s_trans = r.get<uint32_t>(); s.n_layer_total = r.get<uint32_t>();
    if (s.s_trans || s.n_layer_total < attn_layers) { err = "state: unexpected recurrent layout"; return false; }
    const uint32_t n_rec = s.n_layer_total - attn_layers;
    for (uint32_t l = 0; l < n_rec && r.ok; l++) {
        s.r_type.push_back(r.get<int32_t>()); s.r_row.push_back(r.get<uint64_t>());
        s.r.push_back(r.skip(s.r_row.back() * cells));
    }
    for (uint32_t l = 0; l < n_rec && r.ok; l++) {
        s.s_type.push_back(r.get<int32_t>()); s.s_row.push_back(r.get<uint64_t>());
        s.s.push_back(r.skip(s.s_row.back() * cells));
    }
    return r.ok;
}

static bool parse_state(const uint8_t * data, size_t n, MemKind kind, ParsedState & s, std::string & err) {
    Reader r{data, n};
    s = ParsedState();
    s.kind = kind;
    if (r.get<uint32_t>() != kStateMagic) { err = "state: wrong magic"; return false; }
    r.get<int32_t>();  // the source sequence id
    s.kv.resize(kind == MemKind::Iswa ? 2 : 1);
    for (auto & sec : s.kv) if (!parse_kv(r, sec, err)) return false;
    if (kind == MemKind::Hybrid) {
        s.has_rec = true;
        if (!parse_rec(r, s.rec, s.kv[0].n_layer, err)) return false;
    }
    if (!r.ok || r.i != n) { err = "state: unexpected size for its memory kind"; return false; }
    return true;
}

// Gemma 3's global layers are every p-th (il % p == p - 1, llama.cpp's swa pattern): p is the one
// that gives the global section's layer count, or 0 if none does.
static size_t swa_pattern(size_t n_layer, size_t n_global) {
    size_t found = 0;
    for (size_t p = 2; p <= 64; p++) {
        size_t g = 0;
        for (size_t il = 0; il < n_layer; il++) g += il % p == p - 1;
        if (g == n_global) { if (found) return 0; found = p; }  // ambiguous counts are refused (Codex)
    }
    return found;
}

class Engine {
public:
    std::string error;
    size_t window = 0, batch = 0, n_layer = 0, kv_heads = 0, head_dim = 0, vocab = 0, chunks = 0;
    bool attention_only = true;   // every layer has a KV cache (no conv state)
    bool rope_permute = false;    // the llama.cpp side is a Llama-architecture GGUF
    size_t n_swa = 0;             // the llama.cpp side's sliding window, for Gemma 3

    bool open(const std::string & dir) {
        auto kv = read_kv(dir + "/runner.txt");
        auto num = [&](const char * k, double d) { return kv.count(k) ? std::stod(kv[k]) : d; };
        auto type = [&](const char * k) { return example::llm_helper::getLLMTypeFromName(kv.count(k) ? kv[k].c_str() : "fp32"); };
        opt_.prompt_token_batch_size = (size_t) num("prompt_token_batch_size", 128);
        opt_.cache_size = (size_t) num("cache_size", 4096);
        opt_.hidden_size = (size_t) num("hidden_size", 0);
        opt_.num_head = (size_t) num("num_head", 0);
        opt_.num_layer = (size_t) num("num_layer", 0);
        opt_.head_dim = (size_t) num("head_dim", 0);
        opt_.window_size = (size_t) num("window_size", 0);
        opt_.max_token_length = (size_t) num("max_token_length", 40960);
        opt_.partial_rotary_factor = num("partial_rotary_factor", 1.0);
        opt_.rot_emb_base = num("rot_emb_base", 10000.0);
        opt_.rot_emb_local_base = num("rot_emb_local_base", 0.0);  // Gemma 3's sliding layers
        opt_.rope_llama3_factor = num("rope_llama3_factor", 0.0);  // Llama 3.1/3.2's RoPE scaling
        opt_.rope_llama3_low = num("rope_llama3_low", 1.0);
        opt_.rope_llama3_high = num("rope_llama3_high", 4.0);
        opt_.rope_llama3_orig = num("rope_llama3_orig", 8192.0);
        opt_.model_input_type = type("input_type");
        opt_.model_output_type = type("output_type");
        opt_.cache_type = type("cache_type");
        opt_.mask_type = type("mask_type");
        opt_.rot_emb_type = type("rot_emb_type");
        example::LlamaModelPaths paths;
        paths.token_embedding_path = dir + "/" + kv["token_embedding_path"];
        paths.model_package_paths = split_list(kv["model_package_paths"], dir);
        paths.prompt_model_paths = split_list(kv["prompt_model_paths"], dir);
        paths.gen_model_paths = split_list(kv["gen_model_paths"], dir);
        vocab = (size_t) num("vocab_size", 0);
        if (!opt_.hidden_size || !opt_.num_layer || !vocab) { error = "runner.txt incomplete"; return false; }
        const bool per_layer = kv["state_layout"] == "per-layer";
        executorch::runtime::runtime_init();
        rt_.Initialize(opt_, paths);
        window = opt_.cache_size; batch = opt_.prompt_token_batch_size; n_layer = opt_.num_layer;
        // Gemma 3's sliding window, as the export was built with it (the llama.cpp side's own value
        // is used instead when main() can read it from a loaded model).
        n_swa = opt_.window_size;
        head_dim = opt_.head_dim ? opt_.head_dim : opt_.hidden_size / opt_.num_head;
        chunks = rt_.GetModelChunks().size();
        // The layer table every handoff walks, read back from the chunks rather than trusted.
        layers_.clear();
        for (size_t c = 0; c < chunks; c++) {
            auto * ch = chunk(c);
            const auto & st = ch->GetStateInputs();
            if (ch->GetCacheLength() != window) { error = "cache length differs from cache_size"; return false; }
            if (!per_layer) {
                if (st.size() % 2) { error = "uniform layout with an odd state count"; return false; }
                const size_t per = st.size() / 2;
                for (size_t j = 0; j < per; j++) {
                    if (!st[j].isAttentionCache || !st[per + j].isAttentionCache) { error = "uniform layout with a non-attention state"; return false; }
                    layers_.push_back({true, c, st[j].inputIndex, st[per + j].inputIndex, 0, 0});
                }
            } else {
                for (size_t i = 0; i < st.size();) {
                    if (!st[i].isAttentionCache) {
                        layers_.push_back({false, c, 0, 0, st[i].inputIndex, ch->GetInputBuffer(st[i].inputIndex).nbytesUsed});
                        attention_only = false;
                        i += 1;
                    } else if (i + 1 < st.size() && st[i + 1].isAttentionCache) {
                        layers_.push_back({true, c, st[i].inputIndex, st[i + 1].inputIndex, 0, 0});
                        i += 2;
                    } else { error = "a K without its V"; return false; }
                }
            }
        }
        if (layers_.size() != n_layer) { error = "state count does not match the layer count"; return false; }
        for (auto & L : layers_) {
            if (!L.attn) continue;
            const size_t bytes = chunk(L.chunk)->GetInputBuffer(L.k).nbytesUsed, per_head = window * head_dim * sizeof(float);
            if (bytes % per_head) { error = "attention cache is not fp32 [heads, window, head_dim]"; return false; }
            if (kv_heads && kv_heads != bytes / per_head) { error = "attention caches differ in heads"; return false; }
            kv_heads = bytes / per_head;
        }
        cur_batch_ = rt_.GetTokenBatchSize();
        return true;
    }

    size_t pos() const { return rt_.GetTokenIndex(); }

    // Upstream Reset() zeroes the caches but leaves the mask as it was, so a conversation after a
    // long one would attend to zeroed columns; adopting position 0 marks the mask for a rebuild.
    void clear() { rt_.Reset(); rt_.AdoptTokenIndex(0); filled_ = 0; }

    // Positions a prefill of n tokens occupies at its widest: every piece runs the whole
    // prompt graph, so a remainder piece is padded to `batch` and its pads sit in the window
    // until they are rolled back. A turn must leave room for that, or real history is evicted
    // (Codex review, 2026-11-16).
    size_t prefill_span(size_t n) const { return (n + batch - 1) / batch * batch; }

    // Reads `toks` after what the cache holds, in prompt-sized pieces as MediaTek's runner does
    // (a remainder first, then full batches). Returns the last logits, or null.
    const void * prefill_logits(const std::vector<int32_t> & toks) {
        if (toks.empty() || pos() + prefill_span(toks.size()) > window) { error = "prefill would not fit the window"; return nullptr; }
        if (!swap(batch)) return nullptr;
        size_t i = 0; void * logits = nullptr;
        while (i < toks.size()) {
            size_t left = toks.size() - i, rem = left % batch, take = rem ? rem : batch;
            std::vector<uint64_t> piece(toks.begin() + i, toks.begin() + i + take);
            logits = rt_.Run(piece, true);
            i += take;
        }
        filled_ = std::max(filled_, pos());
        return logits;
    }
    int64_t argmax(const void * logits) const { return (int64_t) example::utils::argmax(opt_.model_output_type, logits, vocab); }
    int64_t prefill(const std::vector<int32_t> & toks) { const void * l = prefill_logits(toks); return l ? argmax(l) : -1; }

    int64_t step(int32_t tok) {
        if (pos() + 1 > window) { error = "decode would not fit the window"; return -1; }
        if (!swap(1)) return -1;
        void * logits = rt_.Run({(uint64_t) tok}, true);
        filled_ = std::max(filled_, pos());
        return argmax(logits);
    }

    // Back to `n` positions: the decoded tokens leave every attention cache. A conv state cannot
    // be rolled back, so the caller saves and restores it around the decode (save_conv/restore_conv).
    bool keep_first(size_t n) {
        if (n > pos()) return false;
        rt_.RollbackTokens(pos() - n);
        return pos() == n;
    }

    // A conv-state snapshot: every conv layer's state, copied whole.
    void save_conv() {
        conv_.clear();
        for (auto & L : layers_) if (!L.attn) { const char * p = (const char *) conv(L); conv_.emplace_back(p, p + L.conv_bytes); }
    }
    void restore_conv() {
        size_t k = 0;
        for (auto & L : layers_) if (!L.attn && k < conv_.size()) { memcpy(conv(L), conv_[k].data(), conv_[k].size()); k++; }
    }

    // Bytes to_llama writes for n positions at most, so the caller can size its buffer before the
    // clock starts.
    size_t state_bytes(size_t n) const {
        size_t b = 64 + 2 * n * 12;
        for (auto & L : layers_) b += L.attn ? 2 * (16 + n * kv_heads * head_dim * 2) : 32 + L.conv_bytes;
        return b;
    }

    // NPU state -> llama.cpp state for positions [0, n), in the destination's layout `lay` (parsed
    // from that context's own small state, see probe_layout in pd_bench_mtk.cpp).
    bool to_llama(const KvLayout & lay, size_t n, std::vector<uint8_t> & out) {
        if (n > pos() || n > window || n == 0) { error = "handoff: length mismatch"; return false; }
        out.clear();
        auto put = [&](const void * p, size_t k) { out.insert(out.end(), (const uint8_t *) p, (const uint8_t *) p + k); };
        uint32_t magic = kStateMagic; int32_t seq = 0;
        put(&magic, 4); put(&seq, 4);
        std::vector<size_t> attn = attention_layers(), global, sliding;
        if (lay.kind == MemKind::Iswa) {
            if (!split_swa(lay.kv[0].n_layer, lay.kv[1].n_layer, global, sliding)) return false;
            if (n_swa == 0) { error = "handoff: sliding window unknown"; return false; }
            const size_t keep = std::min(n, n_swa);
            if (!write_section(out, lay.kv[0], global, 0, n, n)) return false;
            if (!write_section(out, lay.kv[1], sliding, n - keep, n, n)) return false;
        } else {
            if (!write_section(out, lay.kv[0], attn, 0, n, n)) return false;
        }
        if (lay.kind == MemKind::Hybrid) {
            const RecSection & R = lay.rec;
            std::vector<size_t> convs;
            for (size_t l = 0; l < layers_.size(); l++) if (!layers_[l].attn) convs.push_back(l);
            if (convs.size() != R.r_type.size() || R.n_layer_total != n_layer) { error = "handoff: conv layer count differs"; return false; }
            uint32_t one = 1, zero = 0; int32_t last = (int32_t) n - 1;
            put(&one, 4); put(&last, 4); put(&zero, 4);
            put(&R.s_trans, 4); put(&R.n_layer_total, 4);
            for (size_t i = 0; i < convs.size(); i++) {
                const LayerRef & L = layers_[convs[i]];
                if (R.r_type[i] != GGML_TYPE_F32 || R.r_row[i] != L.conv_bytes) { error = "handoff: conv state is not f32 of the NPU's size"; return false; }
                put(&R.r_type[i], 4); put(&R.r_row[i], 8); put(conv(L), L.conv_bytes);
            }
            for (size_t i = 0; i < convs.size(); i++) {
                if (R.s_row[i] != 0) { error = "handoff: a recurrent s state this file does not know"; return false; }
                put(&R.s_type[i], 4); put(&R.s_row[i], 8);
            }
        }
        return true;
    }

    // llama.cpp state -> NPU state; the NPU then holds exactly the state's positions.
    bool from_llama(const uint8_t * data, size_t len, MemKind kind) {
        ParsedState s;
        if (!parse_state(data, len, kind, s, error)) return false;
        const auto & base = s.kv[0];
        const size_t n = base.pos.size();
        if (n == 0 || n > window) { error = "handoff: state does not fit the NPU cache"; return false; }
        for (int32_t p : base.pos) if (p < 0 || (size_t) p >= n) { error = "handoff: positions are not 0..n-1"; return false; }
        std::vector<size_t> attn = attention_layers(), global, sliding;
        // Positions the last turn filled and this state does not: zero them, as InitCache would,
        // so the left of the window holds nothing the mask could let through.
        if (filled_ > n) {
            for (size_t l : attn)
                for (int isv = 0; isv < 2; isv++) {
                    float * C = cache(l, isv);
                    for (size_t hh = 0; hh < kv_heads; hh++)
                        memset(C + (hh * window + (window - filled_)) * head_dim, 0, (filled_ - n) * head_dim * sizeof(float));
                }
        }
        if (kind == MemKind::Iswa) {
            if (!split_swa(s.kv[0].n_layer, s.kv[1].n_layer, global, sliding)) return false;
            if (!read_section(s.kv[0], global, n) || !read_section(s.kv[1], sliding, n)) return false;
        } else if (!read_section(base, attn, n)) return false;
        if (kind == MemKind::Hybrid) {
            std::vector<size_t> convs;
            for (size_t l = 0; l < layers_.size(); l++) if (!layers_[l].attn) convs.push_back(l);
            if (convs.size() != s.rec.r.size() || s.rec.pos.size() != 1 || (size_t) s.rec.pos[0] != n - 1) { error = "handoff: recurrent state does not match"; return false; }
            for (size_t i = 0; i < convs.size(); i++) {
                const LayerRef & L = layers_[convs[i]];
                if (s.rec.r_type[i] != GGML_TYPE_F32 || s.rec.r_row[i] != L.conv_bytes) { error = "handoff: conv state is not f32 of the NPU's size"; return false; }
                memcpy(conv(L), s.rec.r[i], L.conv_bytes);
            }
        }
        rt_.AdoptTokenIndex(n);
        filled_ = n;
        return true;
    }

    std::vector<size_t> attention_layers() const {
        std::vector<size_t> a;
        for (size_t l = 0; l < layers_.size(); l++) if (layers_[l].attn) a.push_back(l);
        return a;
    }

private:
    struct LayerRef { bool attn; size_t chunk, k, v, conv, conv_bytes; };
    example::LlamaRuntime rt_;
    example::LlamaModelOptions opt_;
    std::vector<LayerRef> layers_;
    size_t cur_batch_ = 0, filled_ = 0;
    std::vector<std::vector<char>> conv_;

    LlamaModelChunk * chunk(size_t c) { return static_cast<LlamaModelChunk *>(rt_.GetModelChunks()[c].get()); }
    float * cache(size_t l, bool isv) { const auto & L = layers_[l]; return (float *) chunk(L.chunk)->GetInputBuffer(isv ? L.v : L.k).data; }
    void * conv(const LayerRef & L) { return chunk(L.chunk)->GetInputBuffer(L.conv).data; }

    bool swap(size_t b) {
        if (cur_batch_ == b) return true;
        rt_.SwapModel(b);
        if (rt_.TakeSwapFailed()) { error = "graph swap failed"; cur_batch_ = 0; return false; }
        cur_batch_ = b;
        return true;
    }

    bool split_swa(uint32_t n_global, uint32_t n_sliding, std::vector<size_t> & global, std::vector<size_t> & sliding) {
        if (!attention_only || n_global + n_sliding != n_layer) { error = "handoff: sliding-window layer counts differ"; return false; }
        const size_t p = swa_pattern(n_layer, n_global);
        if (!p) { error = "handoff: no sliding-window pattern fits"; return false; }
        for (size_t il = 0; il < n_layer; il++) (il % p == p - 1 ? global : sliding).push_back(il);
        return true;
    }

    // One KV section for `layers` holding positions [from, to) of n held, in the layout `T`.
    bool write_section(std::vector<uint8_t> & out, const KvSection & T, const std::vector<size_t> & layers, size_t from, size_t to, size_t n) {
        auto put = [&](const void * p, size_t k) { out.insert(out.end(), (const uint8_t *) p, (const uint8_t *) p + k); };
        if (T.n_layer != layers.size()) { error = "handoff: KV layer count differs"; return false; }
        const size_t row = kv_heads * head_dim, cells = to - from;
        uint32_t one = 1, c32 = (uint32_t) cells; put(&one, 4); put(&c32, 4);
        for (size_t p = from; p < to; p++) { int32_t q = (int32_t) p; uint32_t ns = 1; int32_t sq = 0; put(&q, 4); put(&ns, 4); put(&sq, 4); }
        if (cells == 0) return true;
        put(&T.v_trans, 4); put(&T.n_layer, 4);
        std::vector<float> tmp(row); std::vector<ggml_fp16_t> h(row);
        for (size_t i = 0; i < layers.size(); i++) {
            if (T.k_type[i] != GGML_TYPE_F16 || T.k_row[i] != row * 2) { error = "handoff: llama.cpp K is not f16 of the NPU's width"; return false; }
            put(&T.k_type[i], 4); put(&T.k_row[i], 8);
            const float * K = cache(layers[i], false);
            for (size_t p = from; p < to; p++) {
                gather(K, n, p, tmp.data());
                if (rope_permute) permute_to_llama(tmp.data());
                ggml_fp32_to_fp16_row(tmp.data(), h.data(), row);
                put(h.data(), row * 2);
            }
        }
        for (size_t i = 0; i < layers.size(); i++) {
            if (T.v_type[i] != GGML_TYPE_F16) { error = "handoff: llama.cpp V is not f16"; return false; }
            const float * V = cache(layers[i], true);
            put(&T.v_type[i], 4);
            if (!T.v_trans) {
                if (T.v_row[i] != row * 2) { error = "handoff: V width differs"; return false; }
                put(&T.v_row[i], 8);
                for (size_t p = from; p < to; p++) {
                    gather(V, n, p, tmp.data());
                    ggml_fp32_to_fp16_row(tmp.data(), h.data(), row);
                    put(h.data(), row * 2);
                }
            } else {
                if (T.v_el[i] != 2 || T.v_embd[i] != row) { error = "handoff: transposed V shape differs"; return false; }
                put(&T.v_el[i], 4); put(&T.v_embd[i], 4);
                std::vector<float> line(cells); std::vector<ggml_fp16_t> lh(cells);
                for (size_t hh = 0; hh < kv_heads; hh++)
                    for (size_t d = 0; d < head_dim; d++) {
                        const float * src = V + (hh * window + (window - n) + from) * head_dim + d;
                        for (size_t t = 0; t < cells; t++) line[t] = src[t * head_dim];
                        ggml_fp32_to_fp16_row(line.data(), lh.data(), cells);
                        put(lh.data(), cells * 2);
                    }
            }
        }
        return true;
    }

    // One parsed KV section into `layers`, by each cell's position, of n held.
    bool read_section(const KvSection & S, const std::vector<size_t> & layers, size_t n) {
        const size_t cells = S.pos.size(), row = kv_heads * head_dim;
        if (cells == 0) return true;
        if (S.n_layer != layers.size()) { error = "handoff: KV layer count differs"; return false; }
        for (int32_t p : S.pos) if (p < 0 || (size_t) p >= n) { error = "handoff: a position outside the held range"; return false; }
        std::vector<float> tmp(row);
        for (size_t i = 0; i < layers.size(); i++) {
            if (S.k_type[i] != GGML_TYPE_F16 || S.k_row[i] != row * 2) { error = "handoff: K is not f16 of the NPU's width"; return false; }
            float * K = cache(layers[i], false);
            for (size_t c = 0; c < cells; c++) {
                ggml_fp16_to_fp32_row((const ggml_fp16_t *) (S.k[i] + c * row * 2), tmp.data(), row);
                if (rope_permute) permute_from_llama(tmp.data());
                scatter(K, n, (size_t) S.pos[c], tmp.data());
            }
            float * V = cache(layers[i], true);
            if (S.v_type[i] != GGML_TYPE_F16) { error = "handoff: V is not f16"; return false; }
            if (!S.v_trans) {
                for (size_t c = 0; c < cells; c++) {
                    ggml_fp16_to_fp32_row((const ggml_fp16_t *) (S.v[i] + c * row * 2), tmp.data(), row);
                    scatter(V, n, (size_t) S.pos[c], tmp.data());
                }
            } else {
                if (S.v_embd[i] != row || S.v_el[i] != 2) { error = "handoff: transposed V shape differs"; return false; }
                std::vector<float> line(cells);
                for (size_t j = 0; j < row; j++) {
                    ggml_fp16_to_fp32_row((const ggml_fp16_t *) (S.v[i] + j * cells * 2), line.data(), cells);
                    const size_t hh = j / head_dim, d = j % head_dim;
                    for (size_t c = 0; c < cells; c++)
                        V[(hh * window + (window - n) + (size_t) S.pos[c]) * head_dim + d] = line[c];
                }
            }
        }
        return true;
    }

    // Position t of n held positions, right-aligned in the window, all heads -> one llama row.
    void gather(const float * C, size_t n, size_t t, float * out) const {
        for (size_t hh = 0; hh < kv_heads; hh++)
            memcpy(out + hh * head_dim, C + (hh * window + (window - n) + t) * head_dim, head_dim * sizeof(float));
    }
    void scatter(float * C, size_t n, size_t t, const float * in) const {
        for (size_t hh = 0; hh < kv_heads; hh++)
            memcpy(C + (hh * window + (window - n) + t) * head_dim, in + hh * head_dim, head_dim * sizeof(float));
    }
    void permute_to_llama(float * r) const {
        std::vector<float> t(head_dim); const size_t half = head_dim / 2;
        for (size_t hh = 0; hh < kv_heads; hh++) {
            float * x = r + hh * head_dim;
            for (size_t i = 0; i < half; i++) { t[2 * i] = x[i]; t[2 * i + 1] = x[i + half]; }
            memcpy(x, t.data(), head_dim * sizeof(float));
        }
    }
    void permute_from_llama(float * r) const {
        std::vector<float> t(head_dim); const size_t half = head_dim / 2;
        for (size_t hh = 0; hh < kv_heads; hh++) {
            float * x = r + hh * head_dim;
            for (size_t i = 0; i < half; i++) { t[i] = x[2 * i]; t[i + half] = x[2 * i + 1]; }
            memcpy(x, t.data(), head_dim * sizeof(float));
        }
    }
};

// Cosine of two f16 rows.
static double cos16(const uint8_t * a, const uint8_t * b, size_t count) {
    std::vector<float> x(count), y(count);
    ggml_fp16_to_fp32_row((const ggml_fp16_t *) a, x.data(), count);
    ggml_fp16_to_fp32_row((const ggml_fp16_t *) b, y.data(), count);
    double xy = 0, xx = 0, yy = 0;
    for (size_t i = 0; i < count; i++) { xy += x[i] * y[i]; xx += x[i] * x[i]; yy += y[i] * y[i]; }
    return xy / (std::sqrt(xx * yy) + 1e-30);
}
static double cos32(const uint8_t * a, const uint8_t * b, size_t count) {
    const float * x = (const float *) a; const float * y = (const float *) b;
    double xy = 0, xx = 0, yy = 0;
    for (size_t i = 0; i < count; i++) { xy += x[i] * y[i]; xx += x[i] * x[i]; yy += y[i] * y[i]; }
    return xy / (std::sqrt(xx * yy) + 1e-30);
}

} // namespace npu
