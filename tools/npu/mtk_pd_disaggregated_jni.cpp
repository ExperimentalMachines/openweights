/*
 * Copyright (c) 2026 OpenWeights Authors
 *
 * Licensed under the BSD License (the "License"); you may not use this file
 * except in compliance with the License.
 */

#include <jni.h>
#include <android/log.h>
#include <cerrno>
#include <sched.h>
#include <sys/resource.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <thread>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <memory>
#include <random>
#include <string>
#include <unordered_set>
#include <vector>

#include <executorch/extension/data_loader/file_data_loader.h>
#include <executorch/extension/evalue_util/print_evalue.h>
#include <executorch/extension/module/module.h>
#include <executorch/runtime/executor/method.h>
#include <executorch/runtime/executor/program.h>
#include <executorch/runtime/platform/log.h>
#include <executorch/runtime/platform/runtime.h>

#include <nlohmann/json.hpp>

#include "llama_runner/LlamaConfig.h"
#include "llama_runner/LlamaModelChunk.h"
#include "llama_runner/LlamaRuntime.h"
#include <executorch/backends/mediatek/runtime/include/api/APUWareUtilsLib.h>
#include <executorch/extension/threadpool/cpuinfo_utils.h>
#include <executorch/extension/threadpool/threadpool.h>
#include "llama_runner/ModelChunk.h"
#include "llama_runner/Utils.h"
#include "llama_runner/llm_helper/include/llm_types.h"

#include <executorch/examples/models/llama/tokenizer/llama_tiktoken.h>
#include <pytorch/tokenizers/hf_tokenizer.h>
#include <pytorch/tokenizers/llama2c_tokenizer.h>
#include <pytorch/tokenizers/tiktoken.h>

#define TAG "OpenWeightsPD"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// A BPE tokenizer splits a multi-byte character across tokens, so a single decoded piece
// is often half of one. NewStringUTF aborts the process on such a piece under CheckJNI
// ("input is not valid Modified UTF-8"), which is how a debug build died mid-reply. Only
// whole characters are handed to Java; a trailing partial sequence waits for the next
// token. Measured on LFM2.5, whose tokenizer emits partial UTF-8 for any non-ASCII text.
// The index of the highest-capacity CPU, read from the kernel rather than assumed. The
// capacity file is the scheduler's own normalised measure of core width; where it is
// absent (older kernels) the maximum frequency is the next best proxy. A negative return
// means the topology could not be read, and the caller leaves the affinity mask alone.
// The exporter records, beside the chunks, the runner options the graphs were compiled
// for. Anything it does not name keeps the value MediaTek's own runner defaults to, so an
// older manifest still loads; anything it does name wins, because the graph is fixed and
// the runtime is not.
static example::LlamaModelOptions ParseRunnerOptions(const std::string& json_text) {
    example::LlamaModelOptions options = {
        .prompt_token_batch_size = 128,
        .cache_size = 512,
        .hidden_size = 2048,
        .num_head = 16,
        .num_layer = 16,
        .head_dim = 64,
        .window_size = 0,
        .max_token_length = 2048,
        .partial_rotary_factor = 1.0,
        .rot_emb_base = 1000000.0,
        .model_input_type = example::llm_helper::getLLMTypeFromName("int16"),
        .model_output_type = example::llm_helper::getLLMTypeFromName("int16"),
        .cache_type = example::llm_helper::getLLMTypeFromName("float32"),
        .mask_type = example::llm_helper::getLLMTypeFromName("int16"),
        .rot_emb_type = example::llm_helper::getLLMTypeFromName("int16"),
    };
    if (json_text.empty()) {
        LOGW("DisaggregatedSession: no runner options given, using MediaTek defaults");
        return options;
    }
    nlohmann::json j = nlohmann::json::parse(json_text, nullptr, false);
    if (j.is_discarded() || !j.is_object()) {
        LOGE("DisaggregatedSession: runner options are not an object, using defaults");
        return options;
    }
    auto num = [&](const char* key, auto& field) {
        auto it = j.find(key);
        if (it != j.end() && it->is_number()) {
            field = static_cast<std::decay_t<decltype(field)>>(it->get<double>());
        }
    };
    auto type = [&](const char* key, example::LLMType& field) {
        auto it = j.find(key);
        if (it != j.end() && it->is_string()) {
            field = example::llm_helper::getLLMTypeFromName(it->get<std::string>().c_str());
        }
    };
    num("prompt_token_batch_size", options.prompt_token_batch_size);
    num("cache_size", options.cache_size);
    num("hidden_size", options.hidden_size);
    num("num_head", options.num_head);
    num("num_layer", options.num_layer);
    num("head_dim", options.head_dim);
    num("window_size", options.window_size);
    num("max_token_length", options.max_token_length);
    num("partial_rotary_factor", options.partial_rotary_factor);
    num("rot_emb_base", options.rot_emb_base);
    type("input_type", options.model_input_type);
    type("output_type", options.model_output_type);
    type("cache_type", options.cache_type);
    type("mask_type", options.mask_type);
    type("rot_emb_type", options.rot_emb_type);
    LOGI("DisaggregatedSession: runner options: batch=%zu cache=%zu hidden=%zu heads=%zu layers=%zu dim=%zu maxlen=%zu",
         options.prompt_token_batch_size, options.cache_size, options.hidden_size,
         options.num_head, options.num_layer, options.head_dim, options.max_token_length);
    return options;
}


/**
 * Widens the calling thread to every CPU the process may use, and puts the old mask back
 * when it goes out of scope.
 *
 * Kotlin's coroutine threads are spawned lazily from whichever thread submits work, and a
 * new thread inherits its creator's affinity. On this ROM the UI thread is narrowed to the
 * big cluster while it handles a tap, so a DefaultDispatch thread born then keeps
 * cpus=4-7 for the life of the process, and every pthreadpool worker created from it
 * inherits the same four cores. That was measured here: seven delegate threads over four
 * cores, 75 ms a token against 35 for the same library in a shell process. engine_session.cpp
 * met the same trap on the llama.cpp path and restores the startup mask for it.
 *
 * Asking for every configured CPU names no core: the kernel intersects the request with
 * the process's cpuset, so the result is whatever this phone allows this app, and the
 * scheduler is left to place the threads.
 */
class ProcessWideAffinity {
public:
    ProcessWideAffinity() {
        CPU_ZERO(&previous_);
        saved_ = sched_getaffinity(0, sizeof(previous_), &previous_) == 0;
        cpu_set_t all;
        CPU_ZERO(&all);
        const long configured = sysconf(_SC_NPROCESSORS_CONF);
        for (long cpu = 0; cpu < configured && cpu < CPU_SETSIZE; ++cpu) CPU_SET(cpu, &all);
        if (sched_setaffinity(0, sizeof(all), &all) != 0) {
            LOGW("DisaggregatedSession: could not widen thread affinity: %s", strerror(errno));
        }
    }
    ~ProcessWideAffinity() {
        if (saved_) sched_setaffinity(0, sizeof(previous_), &previous_);
    }
    ProcessWideAffinity(const ProcessWideAffinity&) = delete;
    ProcessWideAffinity& operator=(const ProcessWideAffinity&) = delete;

private:
    cpu_set_t previous_;
    bool saved_ = false;
};

/**
 * Sizes the XNNPACK delegate's threadpool and returns how many threads it ended up with.
 *
 * [requested] below zero sizes it the way the app's own ExecuTorch path does, so the two
 * decode on the same cores on every phone. ExecuTorch's default is one thread per core, and
 * pthreadpool splits each region into equal shares, so on a chip with efficiency cores
 * (A520, A510, A55, A53) every region waits for its share on a little core.
 * get_num_performant_cores() reads each core's microarchitecture from cpuinfo and drops
 * those; minus one leaves a core for the UI thread. This is what jni_layer_llama.cpp does
 * in the AAR. Zero sizes it to every core, which is ExecuTorch's own default, and a positive
 * value forces that count; both exist for the probe, which measures whether the policy
 * matters on a given phone. The pool is one per process, so each call sets it outright
 * rather than leaving whatever the last call chose.
 *
 * Must run before any Module is loaded: XNNPACK captures the pool when it creates its
 * runtime, and resizing afterwards would leave that runtime holding a freed pool.
 */
static size_t size_delegate_pool(int32_t requested) {
    int32_t threads = requested;
    if (requested < 0) {
        threads =
            static_cast<int32_t>(::executorch::extension::cpuinfo::get_num_performant_cores()) - 1;
    } else if (requested == 0) {
        threads = static_cast<int32_t>(cpuinfo_get_processors_count());
    }
    // Workers inherit the mask of the thread that creates them, so this thread is widened
    // first; see ProcessWideAffinity. The workers keep the wide mask after this thread's own
    // is put back.
    ProcessWideAffinity creator_affinity;
    if (threads > 0) {
        ::executorch::extension::threadpool::get_threadpool()->_unsafe_reset_threadpool(
            static_cast<uint32_t>(threads));
    }
    return ::executorch::extension::threadpool::get_threadpool()->get_thread_count();
}

// ExecuServe: a reply piece as a Java string. NewStringUTF takes NUL-terminated *modified*
// UTF-8, which cuts a piece at an embedded NUL and is not the encoding a tokenizer writes for
// characters past U+FFFF (emoji); this converts standard UTF-8 to UTF-16 itself, with U+FFFD
// for any malformed byte, and hands Java the exact length. Null if the JVM is out of memory.
static jstring NewJavaString(JNIEnv* env, const std::string& utf8) {
    std::u16string out;
    out.reserve(utf8.size());
    const auto* p = reinterpret_cast<const unsigned char*>(utf8.data());
    const size_t n = utf8.size();
    for (size_t i = 0; i < n;) {
        const unsigned char c = p[i];
        size_t len = c < 0x80 ? 1 : (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : (c & 0xF8) == 0xF0 ? 4 : 0;
        uint32_t cp = len == 1 ? c : len == 2 ? (c & 0x1F) : len == 3 ? (c & 0x0F) : (c & 0x07);
        bool ok = len > 0 && i + len <= n;
        for (size_t k = 1; ok && k < len; ++k) {
            if ((p[i + k] & 0xC0) != 0x80) ok = false;
            else cp = (cp << 6) | (p[i + k] & 0x3F);
        }
        static constexpr uint32_t kMin[] = {0, 0, 0x80, 0x800, 0x10000};
        if (ok && (cp < kMin[len] || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF))) ok = false;
        if (!ok) {
            out.push_back(u'\uFFFD');
            ++i;
            continue;
        }
        if (cp >= 0x10000) {
            cp -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
        } else {
            out.push_back(static_cast<char16_t>(cp));
        }
        i += len;
    }
    return env->NewString(reinterpret_cast<const jchar*>(out.data()), static_cast<jsize>(out.size()));
}

// ExecuServe: a Java string as standard UTF-8, for the tokenizer. GetStringUTFChars gives
// modified UTF-8, in which an emoji is two 3-byte surrogates and NUL is two bytes, so an emoji
// from one reply would reach the next prompt as different tokens. Unpaired surrogates become
// U+FFFD. False, with an OutOfMemoryError pending for Java, if the characters could not be had.
static bool JavaToUtf8(JNIEnv* env, jstring text, std::string* out) {
    out->clear();
    if (text == nullptr) return true;
    const jsize n = env->GetStringLength(text);
    const jchar* chars = env->GetStringChars(text, nullptr);
    if (chars == nullptr) return false;
    out->reserve(static_cast<size_t>(n));
    for (jsize i = 0; i < n; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < n && chars[i + 1] >= 0xDC00 && chars[i + 1] <= 0xDFFF) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (chars[i + 1] - 0xDC00);
            ++i;
        } else if (cp >= 0xD800 && cp <= 0xDFFF) {
            cp = 0xFFFD;
        }
        if (cp < 0x80) {
            out->push_back(static_cast<char>(cp));
        } else if (cp < 0x800) {
            out->push_back(static_cast<char>(0xC0 | (cp >> 6)));
            out->push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
            out->push_back(static_cast<char>(0xE0 | (cp >> 12)));
            out->push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out->push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            out->push_back(static_cast<char>(0xF0 | (cp >> 18)));
            out->push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            out->push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out->push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    env->ReleaseStringChars(text, chars);
    return true;
}

static size_t utf8_complete_prefix(const std::string& s) {
    size_t i = s.size();
    // A continuation byte is 10xxxxxx. Walk back to the last sequence start.
    size_t start = i;
    while (start > 0 && (static_cast<unsigned char>(s[start - 1]) & 0xC0) == 0x80) {
        --start;
    }
    if (start == 0) {
        // All continuation bytes, or empty: nothing can be judged complete.
        return (i > 0 && (static_cast<unsigned char>(s[0]) & 0xC0) == 0x80) ? 0 : i;
    }
    const unsigned char lead = static_cast<unsigned char>(s[start - 1]);
    size_t need = 1;
    if ((lead & 0x80) == 0x00) {
        need = 1;
    } else if ((lead & 0xE0) == 0xC0) {
        need = 2;
    } else if ((lead & 0xF0) == 0xE0) {
        need = 3;
    } else if ((lead & 0xF8) == 0xF0) {
        need = 4;
    } else {
        // Not a lead byte at all: pass it through rather than stall forever.
        return i;
    }
    const size_t have = i - (start - 1);
    return have >= need ? i : start - 1;
}

using namespace example::llm_helper;
using example::LlamaModelChunk;
using example::LlamaModelOptions;
using example::LlamaModelPaths;
using example::LlamaRuntime;
using example::ModelChunk;
using example::utils::argmax;
using example::utils::split;
using executorch::aten::ScalarType;
using executorch::aten::Tensor;
using executorch::aten::TensorImpl;
using executorch::extension::Module;
using executorch::runtime::Error;
using executorch::runtime::EValue;
using executorch::runtime::Method;
using tokenizers::HFTokenizer;
using tokenizers::Llama2cTokenizer;
using tokenizers::Tokenizer;

struct LayerMapping {
    enum class Type { ShortConv, Attention };
    Type type;
    size_t layer_idx;
    size_t chunk_idx;
    size_t chunk_cache_k;
    size_t chunk_cache_v;
    size_t cpu_val_k;
    size_t cpu_val_v;
    // Read off the CPU export rather than assumed, because the same weights are exported
    // at several context lengths and the cache tensor grows with the window.
    size_t kv_heads;
    size_t head_dim;
    // The conv state is its own [1, dim, 2] tensor on the NPU (the corrected export), rather
    // than the first rows of a window-sized K cache (the first LFM2 exports).
    bool conv_state_own_tensor;
};

namespace {

// The state buffers of an LFM2.5 export are a contiguous run in the method's value list:
// a short-conv state per conv layer and a K/V pair per attention layer, in layer order.
// Tensors of the same shape appear again much later as ordinary intermediates, so the run
// is bounded by the first gap rather than by a count. Measured on the 1.2B 32k export:
// values 45..66, ten [1,2048,2] conv states and twelve [1,32768,8,64] caches.
bool is_conv_state(const Tensor& t) {
    return t.dim() == 3 && t.size(0) == 1 && t.size(2) == 2;
}

bool is_attention_cache(const Tensor& t) {
    // [1, sequence, kv heads, head dim]. The sequence axis is whatever window the model
    // was exported at; pinning it to 2048 is what left every attention layer unmapped on
    // the 32k export, so the prompt reached the CPU with six of sixteen layers blank.
    return t.dim() == 4 && t.size(0) == 1 && t.size(1) > 1 && t.size(2) > 0 && t.size(3) > 0;
}

std::vector<size_t> attention_layers(size_t num_layers) {
    if (num_layers == 16) {
        return {2, 5, 8, 10, 12, 14};
    }
    if (num_layers == 30) {
        return {2, 5, 8, 11, 14, 17, 20, 23, 26, 29};
    }
    return {};
}

} // namespace

// Where each layer's state sits on the NPU, per chunk: the position of its state (or K) and
// of its V among the chunk's KVCache inputs.
struct NpuSlot {
    size_t chunk;
    size_t k;
    size_t v;
    bool attention;
};

// The corrected LFM2 export takes its states per layer, in layer order: one [1, dim, 2] conv
// state per conv layer, a K then a V per attention layer, and each chunk reports which input
// is which. The first LFM2 exports kept MediaTek's uniform layout, every K of the chunk then
// every V, with the conv state hidden in the first rows of a window-sized K; for those the
// layer types come from the known pattern instead.
static std::vector<NpuSlot> npu_slots(
        const std::vector<std::unique_ptr<example::ModelChunk>>& npu_chunks, size_t num_layers, bool all_attention) {
    std::vector<NpuSlot> slots;
    bool per_layer = false;
    for (const auto& c : npu_chunks) {
        per_layer = per_layer || static_cast<LlamaModelChunk*>(c.get())->HasConvInputs();
    }
    if (per_layer) {
        for (size_t c = 0; c < npu_chunks.size(); ++c) {
            const auto& states = static_cast<LlamaModelChunk*>(npu_chunks[c].get())->GetStateInputs();
            for (size_t i = 0; i < states.size();) {
                if (!states[i].isAttentionCache) {
                    slots.push_back({c, i, 0, false});
                    i += 1;
                } else if (i + 1 < states.size() && states[i + 1].isAttentionCache) {
                    slots.push_back({c, i, i + 1, true});
                    i += 2;
                } else {
                    LOGE("DisaggregatedSession: chunk %zu state %zu is a K without a V", c, i);
                    return {};
                }
            }
        }
        return slots;
    }
    // ExecuServe: a model with no conv state at all (Qwen3, Llama, SmolLM) is attention in every
    // layer, and MediaTek's uniform layout holds each chunk's Ks then its Vs; only LFM2's hybrid
    // layers need the known pattern.
    std::vector<size_t> attn_layers;
    if (all_attention) {
        for (size_t l = 0; l < num_layers; ++l) attn_layers.push_back(l);
    } else {
        attn_layers = attention_layers(num_layers);
    }
    if (attn_layers.empty() || npu_chunks.empty()) return {};
    const size_t layers_per_chunk = (num_layers + npu_chunks.size() - 1) / npu_chunks.size();
    for (size_t l = 0; l < num_layers; ++l) {
        const size_t layer_in_chunk = l % layers_per_chunk;
        const bool is_attn = std::find(attn_layers.begin(), attn_layers.end(), l) != attn_layers.end();
        slots.push_back({l / layers_per_chunk, layer_in_chunk, layers_per_chunk + layer_in_chunk, is_attn});
    }
    return slots;
}

static std::vector<LayerMapping> build_layer_mappings(
        const std::vector<std::unique_ptr<example::ModelChunk>>& npu_chunks, Method* cpu_method) {
    std::vector<LayerMapping> mappings;

    std::vector<size_t> conv_indices;
    std::vector<size_t> attn_k_indices;
    std::vector<size_t> attn_v_indices;
    size_t kv_heads = 0;
    size_t head_dim = 0;

    bool in_run = false;
    for (size_t i = 0; i < cpu_method->values_size(); ++i) {
        auto& val = cpu_method->mutable_value(i);
        bool matched = false;
        if (val.isTensor()) {
            auto t = val.toTensor();
            if (is_conv_state(t)) {
                conv_indices.push_back(i);
                matched = true;
            } else if (is_attention_cache(t)) {
                if (attn_k_indices.size() == attn_v_indices.size()) {
                    attn_k_indices.push_back(i);
                } else {
                    attn_v_indices.push_back(i);
                }
                kv_heads = static_cast<size_t>(t.size(2));
                head_dim = static_cast<size_t>(t.size(3));
                matched = true;
            }
        }
        if (matched) {
            in_run = true;
        } else if (in_run) {
            break; // First gap ends the state block.
        }
    }

    const size_t num_layers = conv_indices.size() + attn_k_indices.size();
    LOGI("DisaggregatedSession: state block has %zu conv, %zu K/V pairs (%zu heads x %zu dim), %zu layers",
         conv_indices.size(), attn_k_indices.size(), kv_heads, head_dim, num_layers);

    const std::vector<NpuSlot> slots = npu_slots(npu_chunks, num_layers, conv_indices.empty());
    size_t npu_attn = 0;
    for (const auto& slot : slots) npu_attn += slot.attention ? 1 : 0;
    if (attn_k_indices.size() != attn_v_indices.size() || slots.size() != num_layers ||
        npu_attn != attn_k_indices.size()) {
        // A layout this runtime has not been measured against. Handing off a partial state
        // would answer from a prompt the CPU half never saw, which reads as a fluent
        // non-sequitur rather than as a failure, so nothing is handed off at all and the
        // caller falls back to the plain CPU runtime.
        LOGE("DisaggregatedSession: unrecognised state layout (CPU %zu conv, %zu K, %zu V; NPU %zu layers, %zu attention); no handoff",
             conv_indices.size(), attn_k_indices.size(), attn_v_indices.size(), slots.size(), npu_attn);
        return mappings;
    }

    const bool own_tensor = !slots.empty() &&
        static_cast<LlamaModelChunk*>(npu_chunks.front().get())->HasConvInputs();
    mappings.reserve(num_layers);
    size_t conv_ptr = 0;
    size_t attn_ptr = 0;
    for (size_t l = 0; l < num_layers; ++l) {
        const NpuSlot& slot = slots[l];
        if (slot.attention) {
            mappings.push_back({
                LayerMapping::Type::Attention, l, slot.chunk, slot.k, slot.v,
                attn_k_indices[attn_ptr], attn_v_indices[attn_ptr], kv_heads, head_dim, own_tensor,
            });
            attn_ptr++;
        } else {
            mappings.push_back({
                LayerMapping::Type::ShortConv, l, slot.chunk, slot.k, 0,
                conv_indices[conv_ptr], 0, kv_heads, head_dim, own_tensor,
            });
            conv_ptr++;
        }
    }
    return mappings;
}

class DisaggregatedSession {
public:
    DisaggregatedSession() : stop_requested_(false), prefilled_(false), prompt_len_(0), first_output_token_(0) {}
    // MediaTek's LlamaRuntime and ModelChunk destructors are empty: only Release() frees the
    // compiled networks and the NPU buffers. Without it every session leaked its NPU half, and
    // the engine reopens a session per conversation, so the fourth question of a test run had
    // the low-memory killer take the process with 1.4 GB swapped (2026-09-24).
    ~DisaggregatedSession() {
        if (npu_initialized_) {
            npu_runtime_.Release();
        }
    }

    bool Load(
        const std::string& runner_options_json,
        const std::string& prompt_model_paths,
        const std::string& token_embedding_path,
        const std::string& cpu_model_path,
        const std::string& tokenizer_path,
        float temperature
    ) {
        LOGI("DisaggregatedSession: Initializing Tokenizer: %s", tokenizer_path.c_str());
        if (tokenizer_path.find(".json") != std::string::npos) {
            tokenizer_ = std::make_unique<HFTokenizer>();
        } else {
            tokenizer_ = example::get_tiktoken_for_llama();
        }
        if (!tokenizer_ || tokenizer_->load(tokenizer_path) != tokenizers::Error::Ok) {
            LOGE("DisaggregatedSession: Failed to load tokenizer from %s", tokenizer_path.c_str());
            return false;
        }

        vocab_size_ = tokenizer_->vocab_size();
        LOGI("DisaggregatedSession: Tokenizer loaded, vocab_size=%zu", vocab_size_);

        // The ids that end a turn are a property of the tokenizer, not of the family. The
        // previous constants (124900, 124894) belong to a 128k vocabulary; this model has
        // 64402, so no id ever matched and every reply ran to the token limit.
        stop_tokens_.clear();
        const uint64_t eos = tokenizer_->eos_tok();
        if (eos != 0) {
            stop_tokens_.insert(eos);
        }
        // ExecuServe: id 0 ends a turn only where it is padding (LFM2's <|pad|>); in Qwen3's
        // vocabulary it is "!", which must not cut a reply short.
        {
            auto zero = tokenizer_->decode(0, 0);
            if (zero.ok() && (zero.get().empty() || zero.get().find("pad") != std::string::npos)) {
                stop_tokens_.insert(0);
            }
        }
        for (const char* marker : {"<|im_end|>", "<|endoftext|>", "</s>"}) {
            auto marker_res = tokenizer_->encode(marker, 0, 0);
            if (marker_res.ok() && marker_res.get().size() == 1) {
                stop_tokens_.insert(marker_res.get()[0]);
            }
        }
        {
            std::string ids;
            for (uint64_t t : stop_tokens_) {
                ids += (ids.empty() ? "" : ",") + std::to_string((unsigned long long)t);
            }
            LOGI("DisaggregatedSession: stop tokens = [%s]", ids.c_str());
        }

        // Every one of these has to match the graphs the exporter compiled, and nothing
        // in a .pte declares them, so they come from the `runner` block the exporter
        // writes beside the chunks. Guessing them is not a small error: with 16 heads
        // instead of 32 and int16 instead of fp32, prefill on a real prompt predicted
        // token 0 and token 2, which is padding and end-of-turn.
        LlamaModelOptions npu_options = ParseRunnerOptions(runner_options_json);

        // Determine number of chunks
        auto pkg_paths_list = split(prompt_model_paths, ',');
        num_chunks_ = pkg_paths_list.size();

        // Use shared-weights mode (model_package_paths), which is how the
        // .pte chunk files are exported.  In this mode LlamaRuntime sets
        // numChunk = model_package_paths.size() and both prompt & gen paths
        // alias to the same package list.  Setting prompt_model_paths here
        // with gen_model_paths empty triggers numChunk = gen_model_paths.size()
        // = 0, which fails ET_CHECK_MSG(numChunk > 0, "No model to initialize").
        LlamaModelPaths npu_paths = {
            .tokenizer_path = tokenizer_path,
            .token_embedding_path = token_embedding_path,
            .prompt_model_paths = {},
            .gen_model_paths = {},
            .model_package_paths = pkg_paths_list,
        };

        npu_cache_size_ = npu_options.cache_size;
        LOGI("DisaggregatedSession: Initializing NPU Runtime with %zu chunks...", num_chunks_);
        auto npu_start = std::chrono::high_resolution_clock::now();
        npu_runtime_.Initialize(npu_options, npu_paths);
        npu_initialized_ = true;
        auto npu_end = std::chrono::high_resolution_clock::now();
        double npu_sec = std::chrono::duration<double>(npu_end - npu_start).count();
        LOGI("DisaggregatedSession: NPU Runtime initialized in %.2f s", npu_sec);

        LOGI("DisaggregatedSession: Initializing CPU Module: %s", cpu_model_path.c_str());
        auto cpu_start = std::chrono::high_resolution_clock::now();
        size_delegate_pool(-1);
        cpu_module_ = std::make_unique<Module>(cpu_model_path, Module::LoadMode::File);
        auto err = cpu_module_->load_method("forward");
        if (err != Error::Ok) {
            LOGE("DisaggregatedSession: Failed to load method 'forward' in CPU module");
            return false;
        }

        auto method_res = cpu_module_->method("forward");
        if (!method_res.ok()) {
            LOGE("DisaggregatedSession: Failed to get Method pointer from CPU module");
            return false;
        }
        cpu_method_ = *method_res;
        // Worth a line because a confined or single-threaded pool is invisible from
        // the throughput alone, and both have happened here.
        LOGI("DisaggregatedSession: CPU delegate threadpool has %zu threads",
             ::executorch::extension::threadpool::get_threadpool()->get_thread_count());
        auto cpu_end = std::chrono::high_resolution_clock::now();
        double cpu_sec = std::chrono::duration<double>(cpu_end - cpu_start).count();
        LOGI("DisaggregatedSession: CPU Module initialized in %.2f s", cpu_sec);

        layer_mappings_ = build_layer_mappings(npu_runtime_.GetModelChunks(), cpu_method_);
        LOGI("DisaggregatedSession: Built %zu layer mappings for state handoff", layer_mappings_.size());
        if (layer_mappings_.empty()) {
            LOGE("DisaggregatedSession: no state handoff is possible for this export");
            return false;
        }


        prefilled_ = false;
        stop_requested_ = false;
        return true;
    }

    /**
     * Feeds [tokens] to the NPU, appending at wherever its position already is.
     *
     * The MediaTek runtime rolls its cache forward across Run calls, so this is genuinely
     * incremental: what a caller fed earlier stays fed. Returns false when the window is
     * full or the caller asked to stop.
     */
    bool FeedTokens(const std::vector<uint64_t>& tokens) {
        if (tokens.empty()) return true;

        // The NPU keeps a fixed window and the older tokens roll out of it, so a prompt
        // longer than the window prefills happily and then hands over a context the CPU
        // half never saw the start of. The reply that comes back reads fluently and
        // answers the wrong question, which is the one failure worth refusing outright.
        if (fed_tokens_.size() + tokens.size() > npu_cache_size_) {
            last_error_ = kErrorOverflow;
            LOGE("DisaggregatedSession: %zu tokens fed plus %zu more exceeds the %zu-token NPU window; refusing rather than answering from a truncated prompt",
                 fed_tokens_.size(), tokens.size(), npu_cache_size_);
            return false;
        }

        const size_t batch_size = npu_runtime_.GetTokenBatchSize();
        size_t cursor = 0;
        auto start_time = std::chrono::high_resolution_clock::now();
        while (cursor < tokens.size() && !stop_requested_) {
            const size_t remaining = tokens.size() - cursor;
            const size_t remainder = remaining % batch_size;
            const size_t take = remainder ? remainder : batch_size;
            std::vector<uint64_t> batch(tokens.begin() + cursor, tokens.begin() + cursor + take);
            last_logits_ = npu_runtime_.Run(batch);
            cursor += take;
        }
        auto end_time = std::chrono::high_resolution_clock::now();
        // This feed's own time for the rate below; the running total is what the turn
        // reports. Dividing a batch by the cumulative total understates every feed after
        // the first and gets worse with each one.
        const double feed_ms = std::chrono::duration<double, std::milli>(end_time - start_time).count();
        prefill_ms_ += feed_ms;

        if (stop_requested_) {
            LOGW("DisaggregatedSession: prefill cancelled");
            return false;
        }

        fed_tokens_.insert(fed_tokens_.end(), tokens.begin(), tokens.end());
        prompt_len_ = fed_tokens_.size();
        const auto logits_type = npu_runtime_.GetModelOptions().model_output_type;
        first_output_token_ = argmax(logits_type, last_logits_, vocab_size_);
        LOGI("DisaggregatedSession: NPU fed %zu tokens in %.2f ms (%.1f tok/s), %zu total in %.2f ms, next_tok=%llu",
             tokens.size(), feed_ms, (double)tokens.size() / (feed_ms / 1000.0),
             fed_tokens_.size(), prefill_ms_, (unsigned long long)first_output_token_);
        return true;
    }

    /**
     * Warms [prompt_text] into the cache without generating.
     *
     * ExecuTorchEngine feeds every piece of the prompt through here except a final tail,
     * which the generate call takes, and it tracks for itself what it has already fed. So
     * this appends rather than replacing.
     */
    int Prefill(const std::string& prompt_text) {
        last_error_ = kErrorNone;
        if (!tokenizer_) {
            last_error_ = kErrorRuntime;
            return 0;
        }
        // No BOS from the tokenizer: the engine writes the family's BOS into the text, as it
        // does for the CPU runtime (DisaggregatedExecuTorchBridge.tokenizerAddsBos), and
        // asking for one here as well started every conversation with two (codex QA).
        auto encoded = tokenizer_->encode(prompt_text, 0 /* add_bos */, 0 /* add_eos */);
        if (!encoded.ok()) {
            LOGE("DisaggregatedSession: tokenizer encode failed");
            last_error_ = kErrorRuntime;
            return 0;
        }
        if (!FeedTokens(encoded.get())) return 0;
        return static_cast<int>(fed_tokens_.size());
    }

    /**
     * Appends the generate call's tail and hands the state to the CPU half.
     *
     * The tail is a continuation, not a whole prompt: the runtime appends it at wherever
     * its position already is, exactly as a prefill does. Matching it against what has been
     * fed, as an earlier version did, throws the conversation away every turn, because a
     * piece tokenised on its own never matches the same text tokenised inside the whole.
     */
    bool AppendAndHandoff(const std::string& tail_text) {
        if (!tokenizer_) return false;
        auto encoded = tokenizer_->encode(tail_text, 0 /* add_bos */, 0 /* add_eos */);
        if (!encoded.ok()) {
            LOGE("DisaggregatedSession: tokenizer encode failed");
            return false;
        }
        if (!encoded.get().empty() && !FeedTokens(encoded.get())) return false;
        if (fed_tokens_.empty()) {
            LOGE("DisaggregatedSession: nothing was fed, refusing to decode from an empty cache");
            return false;
        }
        if (!HandoffStates()) {
            last_error_ = kErrorRuntime;
            return false;
        }
        prefilled_ = true;
        return true;
    }

    // ExecuServe: false, having copied nothing past any buffer, when the two halves' caches do
    // not have the shapes the copy assumes (a CPU build exported for a shorter window, say).
    bool HandoffStates() {
        auto handoff_start = std::chrono::high_resolution_clock::now();
        const size_t T = prompt_len_;
        cache_high_water_ = std::max(cache_high_water_, T);
        const auto& npu_chunks = npu_runtime_.GetModelChunks();

        for (const auto& m : layer_mappings_) {
            if (m.chunk_idx >= npu_chunks.size()) continue;
            LlamaModelChunk* llama_chunk = static_cast<LlamaModelChunk*>(npu_chunks[m.chunk_idx].get());

            if (m.type == LayerMapping::Type::Attention) {
                size_t k_in_idx = llama_chunk->getInputIndex(LlamaModelChunk::IOKind::KVCache, m.chunk_cache_k);
                size_t v_in_idx = llama_chunk->getInputIndex(LlamaModelChunk::IOKind::KVCache, m.chunk_cache_v);
                void* mtk_k_ptr = llama_chunk->GetInputBuffer(k_in_idx).data;
                void* mtk_v_ptr = llama_chunk->GetInputBuffer(v_in_idx).data;

                auto cpu_k_tensor = cpu_method_->mutable_value(m.cpu_val_k).toTensor();
                auto cpu_v_tensor = cpu_method_->mutable_value(m.cpu_val_v).toTensor();
                float* cpu_k_ptr = cpu_k_tensor.mutable_data_ptr<float>();
                float* cpu_v_ptr = cpu_v_tensor.mutable_data_ptr<float>();

                const float* src_k = reinterpret_cast<const float*>(mtk_k_ptr);
                const float* src_v = reinterpret_cast<const float*>(mtk_v_ptr);

                // The NPU holds its cache head-major and right-aligned in a fixed window;
                // the CPU export holds it sequence-major and left-aligned. Transpose the
                // T positions the prompt actually filled, and no more: the CPU cache is
                // the whole exported window, sixty-four megabytes a tensor at 32k.
                const size_t heads = m.kv_heads;
                const size_t dim = m.head_dim;
                const size_t npu_window = npu_cache_size_;
                if (T > npu_window) {
                    LOGE("DisaggregatedSession: handoff reached with %zu tokens for a %zu window", T, npu_window);
                    return false;
                }
                const size_t row = heads * dim;
                const size_t npu_need = row * npu_window * sizeof(float);
                const size_t cpu_floats = static_cast<size_t>(std::min(cpu_k_tensor.numel(), cpu_v_tensor.numel()));
                const size_t cpu_rows = row ? cpu_floats / row : 0;
                if (row == 0 || llama_chunk->GetInputBuffer(k_in_idx).nbytes < npu_need ||
                    llama_chunk->GetInputBuffer(v_in_idx).nbytes < npu_need || cpu_rows < T) {
                    LOGE("DisaggregatedSession: cache shapes do not match (%zu heads x %zu, NPU window %zu, CPU rows %zu, %zu tokens)",
                         heads, dim, npu_window, cpu_rows, T);
                    return false;
                }
                cpu_window_ = cpu_window_ ? std::min(cpu_window_, cpu_rows) : cpu_rows;
                for (size_t s = 0; s < T; ++s) {
                    const size_t mtk_s = npu_window - T + s;
                    for (size_t h = 0; h < heads; ++h) {
                        const float* k_src = src_k + (h * npu_window + mtk_s) * dim;
                        const float* v_src = src_v + (h * npu_window + mtk_s) * dim;
                        float* k_dst = cpu_k_ptr + (s * heads + h) * dim;
                        float* v_dst = cpu_v_ptr + (s * heads + h) * dim;
                        std::memcpy(k_dst, k_src, dim * sizeof(float));
                        std::memcpy(v_dst, v_src, dim * sizeof(float));
                    }
                }
            } else { // ShortConv
                size_t conv_in_idx = llama_chunk->getInputIndex(LlamaModelChunk::IOKind::KVCache, m.chunk_cache_k);
                void* mtk_conv_ptr = llama_chunk->GetInputBuffer(conv_in_idx).data;

                auto cpu_conv_tensor = cpu_method_->mutable_value(m.cpu_val_k).toTensor();
                float* cpu_conv_ptr = cpu_conv_tensor.mutable_data_ptr<float>();
                const float* src_conv = reinterpret_cast<const float*>(mtk_conv_ptr);

                const size_t capacity = static_cast<size_t>(cpu_conv_tensor.numel());
                if (m.conv_state_own_tensor) {
                    // [1, dim, 2] on both sides, the same order: one copy.
                    const size_t npu_bytes = llama_chunk->GetInputBuffer(conv_in_idx).nbytesUsed;
                    std::memcpy(cpu_conv_ptr, src_conv, std::min(npu_bytes, capacity * sizeof(float)));
                } else {
                    // The first LFM2 exports kept the state in the first rows of each head's
                    // window of a K cache: capacity / heads floats per head, at a stride of the
                    // window. Copying a whole window per head, as this did, was only right when
                    // the window happened to be 512 (8 rows of 64).
                    // Checked as the attention branch checks: a CPU state wider than the NPU's
                    // window rows, or a buffer shorter than the last head's slice, refuses the
                    // handoff instead of reading past the NPU buffer.
                    const size_t stride = npu_cache_size_ * m.head_dim;
                    const size_t per_head = m.kv_heads ? capacity / m.kv_heads : 0;
                    const size_t npu_floats = llama_chunk->GetInputBuffer(conv_in_idx).nbytes / sizeof(float);
                    if (m.kv_heads == 0 || per_head > stride ||
                        (m.kv_heads - 1) * stride + per_head > npu_floats) {
                        LOGE("DisaggregatedSession: conv state shapes do not match (%zu heads, %zu per head, stride %zu, NPU %zu floats)",
                             m.kv_heads, per_head, stride, npu_floats);
                        return false;
                    }
                    for (size_t h = 0; h < m.kv_heads; ++h) {
                        std::memcpy(cpu_conv_ptr + h * per_head, src_conv + h * stride, per_head * sizeof(float));
                    }
                }
            }
        }
        auto handoff_end = std::chrono::high_resolution_clock::now();
        double handoff_ms = std::chrono::duration<double, std::milli>(handoff_end - handoff_start).count();
        LOGI("DisaggregatedSession: State handoff (NPU -> CPU) completed in %.3f ms", handoff_ms);
        return true;
    }

    std::vector<int64_t> Generate(
        const std::string& prompt_text,
        int max_new_tokens,
        float temperature,
        JNIEnv* env,
        jobject callback_obj,
        jmethodID callback_method
    ) {
        stop_requested_ = false;
        last_error_ = kErrorNone;

        // Always bring the NPU up to this prompt. The old code trusted a prefilled_ flag
        // set by the warm-up calls and then decoded from the last warm segment's
        // prediction, so the app answered a question it had never been asked.
        if (!AppendAndHandoff(prompt_text) && !stop_requested_) {
            return {};
        }

        if (stop_requested_) {
            return {2 /* CANCELLED */, (int64_t)prompt_len_, 0, (int64_t)prefill_ms_, 0};
        }
        // ExecuServe: the request's temperature, from the first token on (greedy at 0, and
        // for an NPU whose logits are quantized integers, whose scale is not known here).
        if (temperature > 0.0f) {
            const auto logits_type = npu_runtime_.GetModelOptions().model_output_type;
            if (logits_type == example::llm_helper::LLMType::FP32) {
                first_output_token_ = Sample(static_cast<const float*>(last_logits_), temperature);
            } else if (logits_type == example::llm_helper::LLMType::FP16) {
                first_output_token_ = Sample(static_cast<const __fp16*>(last_logits_), temperature);
            }
        }
        // The CPU build decodes into its own cache: past either half's window there is no row.
        const size_t window = cpu_window_ ? std::min(npu_cache_size_, cpu_window_) : npu_cache_size_;

        // pthreadpool runs one share of every parallel region on this thread, so its
        // placement matters as much as the workers'. It is widened for the decode and put
        // back afterwards, because it is a shared coroutine thread; see
        // ProcessWideAffinity for why it can arrive narrowed. An earlier version pinned it
        // to the single widest core instead, which confined the pool with it and was
        // measured as eight runnable threads over four cores. Nothing names a core.
        ProcessWideAffinity decode_affinity;

        int64_t cur_token = first_output_token_;
        int64_t cur_pos = prompt_len_;
        int generated_count = 0;

        int32_t t_sizes[] = {1, 1};
        uint8_t t_dim_order[] = {0, 1};
        TensorImpl t_impl(ScalarType::Long, 2, t_sizes, &cur_token, t_dim_order);
        Tensor t_tensor(&t_impl);

        int32_t p_sizes[] = {1};
        uint8_t p_dim_order[] = {0};
        TensorImpl p_impl(ScalarType::Long, 1, p_sizes, &cur_pos, p_dim_order);
        Tensor p_tensor(&p_impl);

        std::vector<EValue> cpu_step_inputs;
        cpu_step_inputs.push_back(t_tensor);
        cpu_step_inputs.push_back(p_tensor);

        double forward_ms = 0.0;
        struct rusage usage_start;
        getrusage(RUSAGE_SELF, &usage_start);
        auto decode_start = std::chrono::high_resolution_clock::now();

        // Emit first token
        std::string pending;
        auto emit = [&](const std::string& piece) -> bool {
            if (!callback_obj || !callback_method) return true;
            pending += piece;
            const size_t cut = utf8_complete_prefix(pending);
            if (cut == 0) return true;
            std::string whole = pending.substr(0, cut);
            pending.erase(0, cut);
            jstring jpiece = NewJavaString(env, whole);
            // ExecuServe: an exception in the callback (or no memory for the string) ends the
            // turn at once, left pending for the Kotlin side to throw; no JNI call follows it.
            if (jpiece == nullptr || env->ExceptionCheck()) {
                callback_failed_ = true;
                return false;
            }
            jboolean cont = env->CallBooleanMethod(callback_obj, callback_method, jpiece);
            env->DeleteLocalRef(jpiece);
            if (env->ExceptionCheck()) {
                callback_failed_ = true;
                return false;
            }
            return cont != JNI_FALSE;
        };
        callback_failed_ = false;

        // ExecuServe: the NPU's own prediction can already be the end of the turn.
        bool ended = stop_tokens_.count(first_output_token_) != 0;
        if (!ended) {
            auto first_dec = tokenizer_->decode(0, first_output_token_);
            if (first_dec.ok() && !emit(first_dec.get())) stop_requested_ = true;
            generated_count++;
        }
        if (callback_failed_) return {};

        bool window_full = false;
        while (!ended && generated_count < max_new_tokens && !stop_requested_) {
            if (static_cast<size_t>(cur_pos) >= window) {
                window_full = true;
                break;
            }
            const auto step_start = std::chrono::high_resolution_clock::now();
            auto res = cpu_module_->execute("forward", cpu_step_inputs);
            forward_ms += std::chrono::duration<double, std::milli>(
                std::chrono::high_resolution_clock::now() - step_start).count();
            if (!res.ok()) {
                // ExecuServe: a failed step is an error, not the end of a reply; returning the
                // stats here made a truncated answer read as a finished one.
                LOGE("DisaggregatedSession: CPU forward execution failed at step %d", generated_count);
                last_error_ = kErrorRuntime;
                return {};
            }

            const float* logits = res.get()[0].toTensor().const_data_ptr<float>();
            const uint64_t best_tok = Sample(logits, temperature);

            // The tokenizer's stop ids, and padding where id 0 is padding (see Load).
            if (stop_tokens_.count(best_tok) != 0) {
                LOGI("DisaggregatedSession: Reached stop token %llu", (unsigned long long)best_tok);
                break;
            }

            auto dec = tokenizer_->decode(cur_token, best_tok);
            std::string piece = dec.ok() ? dec.get() : "";

            if (!piece.empty() && !emit(piece)) {
                if (callback_failed_) return {};
                stop_requested_ = true;
                break;
            }

            cur_token = best_tok;
            cur_pos++;
            generated_count++;
        }

        cache_high_water_ = std::max(cache_high_water_, static_cast<size_t>(cur_pos));

        auto decode_end = std::chrono::high_resolution_clock::now();
        double decode_ms = std::chrono::duration<double, std::milli>(decode_end - decode_start).count();
        double decode_tok_s = (double)generated_count / (decode_ms / 1000.0);
        LOGI("DisaggregatedSession: CPU Decode completed: %d tokens in %.2f ms (%.2f tok/s)",
             generated_count, decode_ms, decode_tok_s);
        // Splitting the loop from the model it drives, because the two have been
        // confused here before: forward() is 99.8% of a token, so a decode that looks
        // slow is the export and its kernels, never the loop around them.
        // How many cores the decode actually got. Throughput alone cannot tell a slow
        // kernel from a pool the scheduler has squeezed onto two cores, and the second has
        // happened here twice: a narrowed affinity mask, then a power-HAL boost that stacked
        // the threads. A healthy decode on an eight-core phone reads five or six.
        {
            struct rusage usage_end;
            getrusage(RUSAGE_SELF, &usage_end);
            const auto seconds = [](const timeval& t) { return t.tv_sec + t.tv_usec / 1e6; };
            const double cpu_s = seconds(usage_end.ru_utime) - seconds(usage_start.ru_utime) +
                                 seconds(usage_end.ru_stime) - seconds(usage_start.ru_stime);
            LOGI("DisaggregatedSession: decode kept %.2f cores busy", cpu_s / (decode_ms / 1000.0));
        }
        LOGI("DisaggregatedSession: forward() was %.2f ms of that (%.1f%%), %.2f ms per token",
             forward_ms, 100.0 * forward_ms / decode_ms,
             forward_ms / std::max(1, generated_count - 1));

        int64_t reason = stop_requested_ ? 2 /* CANCELLED */
            : window_full ? 3 /* WINDOW_FULL */
            : (generated_count >= max_new_tokens ? 1 /* MAX_TOKENS */ : 0 /* END_OF_TURN */);
        return {reason, (int64_t)prompt_len_, (int64_t)generated_count, (int64_t)prefill_ms_, (int64_t)decode_ms};
    }

    void ResetContext() {
        prefilled_ = false;
        stop_requested_ = false;
        prompt_len_ = 0;
        first_output_token_ = 0;
        prefill_ms_ = 0.0;
        last_logits_ = nullptr;
        // The NPU half has its own rolling cache and its own token index. Clearing only
        // the CPU tensors left the next turn continuing the previous one on the NPU side.
        fed_tokens_.clear();
        npu_runtime_.Reset();
        // Zero what the last turn actually wrote, not the whole exported window.
        if (cpu_method_) {
            for (const auto& m : layer_mappings_) {
                if (m.type == LayerMapping::Type::Attention) {
                    auto tk = cpu_method_->mutable_value(m.cpu_val_k).toTensor();
                    auto tv = cpu_method_->mutable_value(m.cpu_val_v).toTensor();
                    const size_t row = m.kv_heads * m.head_dim * sizeof(float);
                    const size_t bytes =
                        std::min(static_cast<size_t>(tk.nbytes()), cache_high_water_ * row);
                    std::memset(tk.mutable_data_ptr<float>(), 0, bytes);
                    std::memset(tv.mutable_data_ptr<float>(), 0, bytes);
                } else {
                    auto tc = cpu_method_->mutable_value(m.cpu_val_k).toTensor();
                    std::memset(tc.mutable_data_ptr<float>(), 0, tc.nbytes());
                }
            }
        }
        cache_high_water_ = 0;
        LOGI("DisaggregatedSession: ResetContext completed");
    }

    void Stop() {
        stop_requested_ = true;
    }

private:
    // ExecuServe: argmax at temperature 0, otherwise a draw from softmax(logits / temperature).
    template <typename T>
    uint64_t Sample(const T* logits, float temperature) {
        uint64_t best = 0;
        float max_v = static_cast<float>(logits[0]);
        for (size_t v = 1; v < vocab_size_; ++v) {
            const float x = static_cast<float>(logits[v]);
            if (x > max_v) {
                max_v = x;
                best = v;
            }
        }
        if (!(temperature > 0.0f)) return best;
        weights_.resize(vocab_size_);
        double sum = 0.0;
        for (size_t v = 0; v < vocab_size_; ++v) {
            weights_[v] = std::exp((static_cast<double>(logits[v]) - max_v) / temperature);
            sum += weights_[v];
        }
        double r = std::uniform_real_distribution<double>(0.0, sum)(rng_);
        for (size_t v = 0; v < vocab_size_; ++v) {
            r -= weights_[v];
            if (r <= 0.0) return v;
        }
        return best;
    }

    std::mt19937_64 rng_{std::random_device{}()};
    std::vector<double> weights_;
    // The CPU build's cache rows, read at the handoff: its window when it is shorter.
    size_t cpu_window_ = 0;
    bool callback_failed_ = false;

    LlamaRuntime npu_runtime_;
    std::unique_ptr<Module> cpu_module_;
    Method* cpu_method_ = nullptr;
    std::unique_ptr<Tokenizer> tokenizer_;
    std::vector<LayerMapping> layer_mappings_;
    size_t num_chunks_ = 4;
    size_t vocab_size_ = 128000;

    size_t npu_cache_size_ = 512;
    bool npu_initialized_ = false;
    // How far into the CPU caches the last turn wrote. Zeroing the whole tensor would be
    // seven hundred megabytes of memset per reset at 32k, for a prompt of a few hundred.
    size_t cache_high_water_ = 0;
    std::unordered_set<uint64_t> stop_tokens_;
    // Everything fed to the NPU this turn, so an incremental prefill can tell what a new
    // prompt already contains and feed only the rest.
    std::vector<uint64_t> fed_tokens_;
    void* last_logits_ = nullptr;
    std::vector<uint64_t> prompt_tokens_;
    size_t prompt_len_ = 0;
    uint64_t first_output_token_ = 0;
    double prefill_ms_ = 0.0;
    bool prefilled_ = false;
    std::atomic<bool> stop_requested_{false};

public:
    // ExecuServe: why the last call returned nothing, for nativeLastError.
    static constexpr int kErrorNone = 0;
    static constexpr int kErrorOverflow = 1;
    static constexpr int kErrorRuntime = 2;
    int last_error_ = kErrorNone;

    /** Milliseconds the NPU has spent reading prompts since the last reset, as generate reports it. */
    int64_t PrefillMs() const { return static_cast<int64_t>(prefill_ms_); }

private:
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_github_alpharomercoma_openweights_core_engine_DisaggregatedBridge_nativeLoad(
    JNIEnv* env,
    jobject /* thiz */,
    jstring runner_options_json,
    jstring prompt_model_paths,
    jstring token_embedding_path,
    jstring cpu_model_path,
    jstring tokenizer_path,
    jfloat temperature
) {
    executorch::runtime::runtime_init();

    // ExecuServe: each string checked as it is taken; the first that fails returns at once,
    // its OutOfMemoryError left pending for Kotlin and no JNI call made after it.
    std::string opts, paths, emb, cpu, tok;
    if (!JavaToUtf8(env, runner_options_json, &opts) || !JavaToUtf8(env, prompt_model_paths, &paths) ||
        !JavaToUtf8(env, token_embedding_path, &emb) || !JavaToUtf8(env, cpu_model_path, &cpu) ||
        !JavaToUtf8(env, tokenizer_path, &tok)) {
        return 0;
    }

    auto session = std::make_unique<DisaggregatedSession>();
    bool ok = session->Load(opts, paths, emb, cpu, tok, (float)temperature);

    if (!ok) {
        LOGE("DisaggregatedBridge: nativeLoad failed");
        return 0;
    }

    return reinterpret_cast<jlong>(session.release());
}

JNIEXPORT jint JNICALL
Java_io_github_alpharomercoma_openweights_core_engine_DisaggregatedBridge_nativePrefill(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jstring prompt
) {
    auto session = reinterpret_cast<DisaggregatedSession*>(handle);
    if (!session) return 0;

    std::string text;
    if (!JavaToUtf8(env, prompt, &text)) return 0;
    return session->Prefill(text);
}

/**
 * The NPU's prefill milliseconds since the last reset, the same total nativeGenerate returns:
 * the bridge subtracts it from that total, and wall time measured around the JNI call was a
 * different clock, which could leave a short tail's prefill at zero (codex QA).
 */
JNIEXPORT jlong JNICALL
Java_io_github_alpharomercoma_openweights_core_engine_DisaggregatedBridge_nativePrefillMs(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong handle
) {
    auto session = reinterpret_cast<DisaggregatedSession*>(handle);
    return session ? session->PrefillMs() : 0;
}

/** Why the last nativeGenerate or nativePrefill on [handle] failed: 0 none, 1 window, 2 runtime. */
JNIEXPORT jint JNICALL
Java_io_github_alpharomercoma_openweights_core_engine_DisaggregatedBridge_nativeLastError(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong handle
) {
    auto session = reinterpret_cast<DisaggregatedSession*>(handle);
    return session ? session->last_error_ : 2;
}

JNIEXPORT jlongArray JNICALL
Java_io_github_alpharomercoma_openweights_core_engine_DisaggregatedBridge_nativeGenerate(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jstring prompt,
    jint max_tokens,
    jfloat temperature,
    jobject callback
) {
    auto session = reinterpret_cast<DisaggregatedSession*>(handle);
    if (!session) return nullptr;

    std::string prompt_str;
    if (!JavaToUtf8(env, prompt, &prompt_str)) return nullptr;

    // ExecuServe: a callback without onToken(String): Boolean (R8 removed it, say) is a
    // failure here, not a reply generated into nothing; its NoSuchMethodError is thrown.
    jclass cb_class = callback ? env->GetObjectClass(callback) : nullptr;
    jmethodID cb_method = cb_class ? env->GetMethodID(cb_class, "onToken", "(Ljava/lang/String;)Z") : nullptr;
    if (cb_class) env->DeleteLocalRef(cb_class);
    if (callback && !cb_method) {
        LOGE("DisaggregatedBridge: the callback has no onToken(String): Boolean");
        return nullptr;
    }

    auto result = session->Generate(prompt_str, max_tokens, (float)temperature, env, callback, cb_method);
    // The callback threw: Kotlin rethrows it when this returns.
    if (env->ExceptionCheck()) return nullptr;
    if (result.empty()) {
        // Refused, not finished. Null is what the Kotlin side turns into an exception;
        // an empty array would read as a turn that ended normally with no tokens.
        return nullptr;
    }

    jlongArray res_arr = env->NewLongArray(result.size());
    if (!res_arr) return nullptr;
    env->SetLongArrayRegion(res_arr, 0, result.size(), reinterpret_cast<const jlong*>(result.data()));
    return res_arr;
}

JNIEXPORT void JNICALL
Java_io_github_alpharomercoma_openweights_core_engine_DisaggregatedBridge_nativeResetContext(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong handle
) {
    auto session = reinterpret_cast<DisaggregatedSession*>(handle);
    if (session) session->ResetContext();
}

JNIEXPORT void JNICALL
Java_io_github_alpharomercoma_openweights_core_engine_DisaggregatedBridge_nativeStop(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong handle
) {
    auto session = reinterpret_cast<DisaggregatedSession*>(handle);
    if (session) session->Stop();
}

JNIEXPORT void JNICALL
Java_io_github_alpharomercoma_openweights_core_engine_DisaggregatedBridge_nativeClose(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong handle
) {
    auto session = reinterpret_cast<DisaggregatedSession*>(handle);
    delete session;
}

} // extern "C"

/**
 * Times the CPU half's decode step on any arm64 phone, NPU or not.
 *
 * The rest of this library needs a MediaTek NPU; this does not, so the decode half's
 * choices (the Release build of these libraries, the pool sizing, the affinity guard) can be
 * checked on phones this repository has no NPU export for. It runs the same code the session
 * runs: size_delegate_pool, a Module loaded the same way, and a widened caller. With
 * [hold_lifetime_lock] it also holds MediaTek's FAST_SINGLE_ANSWER lock for the whole
 * timing, the way the Neuron backend used to for a loaded model's lifetime, so the effect
 * of that lock on a CPU decode can be measured on another MediaTek phone and ROM.
 *
 * Returns {median ms per step, pool threads, performant cores, cores busy, whether the lock
 * library was reachable, the caller's effective uclamp.min while timing}, or null on failure.
 * Called by core/engine's PdDecodeProbe instrumented test.
 */
static int effective_uclamp_min_of_this_thread() {
    char path[64];
    snprintf(path, sizeof(path), "/proc/self/task/%d/sched", static_cast<int>(gettid()));
    FILE* file = fopen(path, "r");
    if (file == nullptr) return -1;
    char line[256];
    int value = -1;
    while (fgets(line, sizeof(line), file) != nullptr) {
        if (strncmp(line, "effective uclamp.min", 20) == 0) {
            const char* colon = strchr(line, ':');
            if (colon != nullptr) value = atoi(colon + 1);
            break;
        }
    }
    fclose(file);
    return value;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_io_github_alpharomercoma_openweights_core_engine_eval_PdDecodeProbeNative_nativeProbeCpuDecode(
    JNIEnv* env,
    jobject /* thiz */,
    jstring cpu_model_path,
    jint start_pos,
    jint steps,
    jint threads,
    jboolean hold_lifetime_lock
) {
    std::string path;
    if (!JavaToUtf8(env, cpu_model_path, &path)) return nullptr;

    const size_t pool = size_delegate_pool(threads);
    const auto performant = ::executorch::extension::cpuinfo::get_num_performant_cores();
    Module module(path, Module::LoadMode::File);
    if (module.load_method("forward") != Error::Ok) {
        LOGE("probe: could not load forward from %s", path.c_str());
        return nullptr;
    }

    int64_t token = 1;
    int64_t pos = start_pos;
    int32_t token_sizes[] = {1, 1};
    uint8_t token_dims[] = {0, 1};
    TensorImpl token_impl(ScalarType::Long, 2, token_sizes, &token, token_dims);
    int32_t pos_sizes[] = {1};
    uint8_t pos_dims[] = {0};
    TensorImpl pos_impl(ScalarType::Long, 1, pos_sizes, &pos, pos_dims);
    std::vector<EValue> inputs{EValue(Tensor(&token_impl)), EValue(Tensor(&pos_impl))};

    // One untimed step, so lazily built runtime state is not charged to the first sample.
    if (!module.execute("forward", inputs).ok()) {
        LOGE("probe: forward failed at position %d", start_pos);
        return nullptr;
    }
    pos = start_pos;

    std::unique_ptr<ScopePerformancer> lifetime_lock;
    const bool lock_available = ApuWareUtilsLib::GetInstance().mEnable;
    if (hold_lifetime_lock && lock_available) {
        lifetime_lock = std::make_unique<ScopePerformancer>();
        // The HAL applies the scenario asynchronously; let it settle before timing.
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
    }

    ProcessWideAffinity caller_affinity;
    std::vector<double> step_ms;
    struct rusage usage_start;
    getrusage(RUSAGE_SELF, &usage_start);
    const auto window_start = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < steps; ++i) {
        const auto step_start = std::chrono::high_resolution_clock::now();
        if (!module.execute("forward", inputs).ok()) {
            LOGE("probe: forward failed at position %lld", (long long)pos);
            return nullptr;
        }
        step_ms.push_back(std::chrono::duration<double, std::milli>(
            std::chrono::high_resolution_clock::now() - step_start).count());
        pos++;
    }
    const double window_s = std::chrono::duration<double>(
        std::chrono::high_resolution_clock::now() - window_start).count();
    const int uclamp_min = effective_uclamp_min_of_this_thread();
    struct rusage usage_end;
    getrusage(RUSAGE_SELF, &usage_end);
    lifetime_lock.reset();

    const auto seconds = [](const timeval& t) { return t.tv_sec + t.tv_usec / 1e6; };
    const double cpu_s = seconds(usage_end.ru_utime) - seconds(usage_start.ru_utime) +
                         seconds(usage_end.ru_stime) - seconds(usage_start.ru_stime);
    std::sort(step_ms.begin(), step_ms.end());
    const double result[] = {
        step_ms.empty() ? 0.0 : step_ms[step_ms.size() / 2],
        static_cast<double>(pool),
        static_cast<double>(performant),
        cpu_s / window_s,
        lock_available ? 1.0 : 0.0,
        static_cast<double>(uclamp_min),
    };
    jdoubleArray out = env->NewDoubleArray(6);
    if (!out) return nullptr;
    env->SetDoubleArrayRegion(out, 0, 6, result);
    return out;
}
