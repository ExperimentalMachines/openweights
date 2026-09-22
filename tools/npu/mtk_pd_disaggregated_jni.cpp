/*
 * Copyright (c) 2026 OpenWeights Authors
 *
 * Licensed under the BSD License (the "License"); you may not use this file
 * except in compliance with the License.
 */

#include <jni.h>
#include <android/log.h>
#include <sched.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <memory>
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

static int fastest_cpu() {
    // One metric for the whole comparison: capacities and frequencies are on different
    // scales, so mixing them across cores would rank them by which file happens to exist.
    for (const char* leaf : {"cpu_capacity", "cpufreq/cpuinfo_max_freq"}) {
        int best_cpu = -1;
        long best_score = -1;
        for (int cpu = 0; cpu < CPU_SETSIZE && cpu < 32; ++cpu) {
            char path[128];
            snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/%s", cpu, leaf);
            std::ifstream in(path);
            long score = 0;
            if (in >> score && score > best_score) {
                best_score = score;
                best_cpu = cpu;
            }
        }
        if (best_cpu >= 0) {
            return best_cpu;
        }
    }
    return -1;
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

static std::vector<LayerMapping> build_layer_mappings(size_t num_chunks, Method* cpu_method) {
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
    const std::vector<size_t> attn_layers = attention_layers(num_layers);

    LOGI("DisaggregatedSession: state block has %zu conv, %zu K/V pairs (%zu heads x %zu dim), %zu layers",
         conv_indices.size(), attn_k_indices.size(), kv_heads, head_dim, num_layers);

    if (attn_layers.size() != attn_k_indices.size() ||
        attn_k_indices.size() != attn_v_indices.size()) {
        // A layout this runtime has not been measured against. Handing off a partial state
        // would answer from a prompt the CPU half never saw, which reads as a fluent
        // non-sequitur rather than as a failure, so nothing is handed off at all and the
        // caller falls back to the plain CPU runtime.
        LOGE("DisaggregatedSession: unrecognised state layout (%zu conv, %zu K, %zu V); no handoff",
             conv_indices.size(), attn_k_indices.size(), attn_v_indices.size());
        return mappings;
    }

    mappings.reserve(num_layers);
    const size_t layers_per_chunk = (num_layers + num_chunks - 1) / num_chunks;
    size_t conv_ptr = 0;
    size_t attn_ptr = 0;

    for (size_t l = 0; l < num_layers; ++l) {
        const size_t chunk = l / layers_per_chunk;
        const size_t layer_in_chunk = l % layers_per_chunk;
        const size_t chunk_k = layer_in_chunk;
        const size_t chunk_v = layers_per_chunk + layer_in_chunk;
        const bool is_attn =
            std::find(attn_layers.begin(), attn_layers.end(), l) != attn_layers.end();

        if (is_attn) {
            mappings.push_back({
                LayerMapping::Type::Attention,
                l,
                chunk,
                chunk_k,
                chunk_v,
                attn_k_indices[attn_ptr],
                attn_v_indices[attn_ptr],
                kv_heads,
                head_dim,
            });
            attn_ptr++;
        } else {
            mappings.push_back({
                LayerMapping::Type::ShortConv,
                l,
                chunk,
                chunk_k,
                0,
                conv_indices[conv_ptr],
                0,
                kv_heads,
                head_dim,
            });
            conv_ptr++;
        }
    }
    return mappings;
}

class DisaggregatedSession {
public:
    DisaggregatedSession() : stop_requested_(false), prefilled_(false), prompt_len_(0), first_output_token_(0) {}
    ~DisaggregatedSession() = default;

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
        auto npu_end = std::chrono::high_resolution_clock::now();
        double npu_sec = std::chrono::duration<double>(npu_end - npu_start).count();
        LOGI("DisaggregatedSession: NPU Runtime initialized in %.2f s", npu_sec);

        LOGI("DisaggregatedSession: Initializing CPU Module: %s", cpu_model_path.c_str());
        auto cpu_start = std::chrono::high_resolution_clock::now();
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
        auto cpu_end = std::chrono::high_resolution_clock::now();
        double cpu_sec = std::chrono::duration<double>(cpu_end - cpu_start).count();
        LOGI("DisaggregatedSession: CPU Module initialized in %.2f s", cpu_sec);

        layer_mappings_ = build_layer_mappings(num_chunks_, cpu_method_);
        LOGI("DisaggregatedSession: Built %zu layer mappings for state handoff", layer_mappings_.size());
        if (layer_mappings_.empty()) {
            LOGE("DisaggregatedSession: no state handoff is possible for this export");
            return false;
        }

        prefilled_ = false;
        stop_requested_ = false;
        return true;
    }

    int Prefill(const std::string& prompt_text) {
        if (!tokenizer_) return 0;

        auto encode_res = tokenizer_->encode(prompt_text, 1 /* add_bos */, 0 /* add_eos */);
        if (!encode_res.ok()) {
            LOGE("DisaggregatedSession: Tokenizer encode failed");
            return 0;
        }

        prompt_tokens_ = std::move(encode_res.get());
        prompt_len_ = prompt_tokens_.size();

        // The NPU keeps a fixed window and the older tokens roll out of it, so a prompt
        // longer than the window prefills happily and then hands over a context the CPU
        // half never saw the start of. The reply that comes back reads fluently and
        // answers the wrong question, which is the one failure worth refusing outright.
        if (prompt_len_ > npu_cache_size_) {
            LOGE("DisaggregatedSession: prompt is %zu tokens and the NPU window is %zu; refusing rather than answering from a truncated prompt",
                 prompt_len_, npu_cache_size_);
            prompt_len_ = 0;
            return 0;
        }

        LOGI("DisaggregatedSession: Starting NPU Prefill for %zu prompt tokens...", prompt_len_);

        const size_t batch_size = npu_runtime_.GetTokenBatchSize();
        size_t cur_token_index = 0;
        void* last_logits = nullptr;

        auto prefill_start = std::chrono::high_resolution_clock::now();
        while (cur_token_index < prompt_len_ && !stop_requested_) {
            size_t num_remain = prompt_len_ - cur_token_index;
            size_t remainder = num_remain % batch_size;
            size_t num_new = remainder ? remainder : batch_size;
            std::vector<uint64_t> chunk_tokens(
                prompt_tokens_.begin() + cur_token_index,
                prompt_tokens_.begin() + cur_token_index + num_new
            );
            last_logits = npu_runtime_.Run(chunk_tokens);
            cur_token_index += chunk_tokens.size();
        }
        auto prefill_end = std::chrono::high_resolution_clock::now();
        prefill_ms_ = std::chrono::duration<double, std::milli>(prefill_end - prefill_start).count();

        if (stop_requested_) {
            LOGW("DisaggregatedSession: Prefill cancelled");
            return 0;
        }

        const auto logits_type = npu_runtime_.GetModelOptions().model_output_type;
        first_output_token_ = argmax(logits_type, last_logits, vocab_size_);
        LOGI("DisaggregatedSession: NPU Prefill done in %.2f ms (%.1f tok/s), first_tok=%llu",
             prefill_ms_, (double)prompt_len_ / (prefill_ms_ / 1000.0), (unsigned long long)first_output_token_);

        // Perform State Handoff
        HandoffStates();

        prefilled_ = true;
        return static_cast<int>(prompt_len_);
    }

    void HandoffStates() {
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
                    return;
                }
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

                // The conv state sits in the same fixed-window buffer as a cache, one
                // contiguous window per head. Bounded by the CPU tensor so a model whose
                // hidden size does not happen to equal heads times window cannot overrun.
                const size_t window = npu_cache_size_;
                const size_t stride = window * m.head_dim;
                const size_t capacity = static_cast<size_t>(cpu_conv_tensor.numel());
                size_t written = 0;
                for (size_t h = 0; h < m.kv_heads && written < capacity; ++h) {
                    const size_t n = std::min(window, capacity - written);
                    std::memcpy(cpu_conv_ptr + written, src_conv + h * stride, n * sizeof(float));
                    written += n;
                }
            }
        }
        auto handoff_end = std::chrono::high_resolution_clock::now();
        double handoff_ms = std::chrono::duration<double, std::milli>(handoff_end - handoff_start).count();
        LOGI("DisaggregatedSession: State handoff (NPU -> CPU) completed in %.3f ms", handoff_ms);
    }

    std::vector<int64_t> Generate(
        const std::string& prompt_text,
        int max_new_tokens,
        JNIEnv* env,
        jobject callback_obj,
        jmethodID callback_method
    ) {
        stop_requested_ = false;

        if (!prefilled_) {
            if (Prefill(prompt_text) == 0 && !stop_requested_) {
                // Prefill refused or failed. Decoding now would answer from whatever the
                // caches happen to hold, so the caller is told instead.
                return {};
            }
        }

        if (stop_requested_) {
            return {2 /* CANCELLED */, (int64_t)prompt_len_, 0, (int64_t)prefill_ms_, 0};
        }

        // Decode is one thread chasing one dependency chain, so it belongs on the widest
        // core the chip has. Which core that is comes from the kernel's own capacity
        // table, never from a SoC name: on the Dimensity 9400 this picks cpu7, the
        // Cortex-X925, and on a chip with a different topology it picks whatever that
        // chip calls fastest.
        //
        // This runs on a shared coroutine dispatcher thread, so the previous mask is put
        // back at the end: pinning it permanently would hand the whole app one core.
        cpu_set_t previous_affinity;
        CPU_ZERO(&previous_affinity);
        const bool had_affinity =
            sched_getaffinity(0, sizeof(cpu_set_t), &previous_affinity) == 0;
        const int fastest = fastest_cpu();
        if (fastest >= 0) {
            cpu_set_t cpuset;
            CPU_ZERO(&cpuset);
            CPU_SET(fastest, &cpuset);
            sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
        }
        struct AffinityGuard {
            const cpu_set_t* mask;
            bool restore;
            ~AffinityGuard() {
                if (restore) {
                    sched_setaffinity(0, sizeof(cpu_set_t), mask);
                }
            }
        } affinity_guard{&previous_affinity, had_affinity && fastest >= 0};

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
            jstring jpiece = env->NewStringUTF(whole.c_str());
            jboolean cont = env->CallBooleanMethod(callback_obj, callback_method, jpiece);
            env->DeleteLocalRef(jpiece);
            return cont != JNI_FALSE;
        };

        auto first_dec = tokenizer_->decode(0, first_output_token_);
        if (first_dec.ok()) {
            if (!emit(first_dec.get())) stop_requested_ = true;
        }
        generated_count++;

        while (generated_count < max_new_tokens && !stop_requested_) {
            auto res = cpu_module_->execute("forward", cpu_step_inputs);
            if (!res.ok()) {
                LOGE("DisaggregatedSession: CPU forward execution failed at step %d", generated_count);
                break;
            }

            const float* logits = res.get()[0].toTensor().const_data_ptr<float>();

            // Argmax
            float max_v = logits[0];
            uint64_t best_tok = 0;
            for (size_t v = 1; v < vocab_size_; ++v) {
                if (logits[v] > max_v) {
                    max_v = logits[v];
                    best_tok = v;
                }
            }

            // Token 0 is padding in every vocabulary this runtime loads, and a model that
            // emits it has nothing left to say, so it ends the turn alongside the real
            // stop ids rather than being decoded into the reply.
            if (best_tok == 0 || stop_tokens_.count(best_tok) != 0) {
                LOGI("DisaggregatedSession: Reached stop token %llu", (unsigned long long)best_tok);
                break;
            }

            auto dec = tokenizer_->decode(cur_token, best_tok);
            std::string piece = dec.ok() ? dec.get() : "";

            if (!piece.empty() && !emit(piece)) {
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

        int64_t reason = stop_requested_ ? 2 /* CANCELLED */ : (generated_count >= max_new_tokens ? 1 /* MAX_TOKENS */ : 0 /* END_OF_TURN */);
        return {reason, (int64_t)prompt_len_, (int64_t)generated_count, (int64_t)prefill_ms_, (int64_t)decode_ms};
    }

    void ResetContext() {
        prefilled_ = false;
        stop_requested_ = false;
        prompt_len_ = 0;
        first_output_token_ = 0;
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
    LlamaRuntime npu_runtime_;
    std::unique_ptr<Module> cpu_module_;
    Method* cpu_method_ = nullptr;
    std::unique_ptr<Tokenizer> tokenizer_;
    std::vector<LayerMapping> layer_mappings_;
    size_t num_chunks_ = 4;
    size_t vocab_size_ = 128000;

    size_t npu_cache_size_ = 512;
    // How far into the CPU caches the last turn wrote. Zeroing the whole tensor would be
    // seven hundred megabytes of memset per reset at 32k, for a prompt of a few hundred.
    size_t cache_high_water_ = 0;
    std::unordered_set<uint64_t> stop_tokens_;
    std::vector<uint64_t> prompt_tokens_;
    size_t prompt_len_ = 0;
    uint64_t first_output_token_ = 0;
    double prefill_ms_ = 0.0;
    bool prefilled_ = false;
    std::atomic<bool> stop_requested_{false};
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

    const char* p_opts = env->GetStringUTFChars(runner_options_json, nullptr);
    const char* p_paths = env->GetStringUTFChars(prompt_model_paths, nullptr);
    const char* p_emb = env->GetStringUTFChars(token_embedding_path, nullptr);
    const char* p_cpu = env->GetStringUTFChars(cpu_model_path, nullptr);
    const char* p_tok = env->GetStringUTFChars(tokenizer_path, nullptr);

    auto session = std::make_unique<DisaggregatedSession>();
    bool ok = session->Load(p_opts, p_paths, p_emb, p_cpu, p_tok, (float)temperature);

    env->ReleaseStringUTFChars(runner_options_json, p_opts);
    env->ReleaseStringUTFChars(prompt_model_paths, p_paths);
    env->ReleaseStringUTFChars(token_embedding_path, p_emb);
    env->ReleaseStringUTFChars(cpu_model_path, p_cpu);
    env->ReleaseStringUTFChars(tokenizer_path, p_tok);

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

    const char* p_text = env->GetStringUTFChars(prompt, nullptr);
    int tokens = session->Prefill(p_text);
    env->ReleaseStringUTFChars(prompt, p_text);
    return tokens;
}

JNIEXPORT jlongArray JNICALL
Java_io_github_alpharomercoma_openweights_core_engine_DisaggregatedBridge_nativeGenerate(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jstring prompt,
    jint max_tokens,
    jobject callback
) {
    auto session = reinterpret_cast<DisaggregatedSession*>(handle);
    if (!session) return nullptr;

    const char* p_text = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(p_text);
    env->ReleaseStringUTFChars(prompt, p_text);

    jclass cb_class = callback ? env->GetObjectClass(callback) : nullptr;
    jmethodID cb_method = cb_class ? env->GetMethodID(cb_class, "onToken", "(Ljava/lang/String;)Z") : nullptr;

    auto result = session->Generate(prompt_str, max_tokens, env, callback, cb_method);
    if (result.empty()) {
        // Refused, not finished. Null is what the Kotlin side turns into an exception;
        // an empty array would read as a turn that ended normally with no tokens.
        return nullptr;
    }

    jlongArray res_arr = env->NewLongArray(result.size());
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
