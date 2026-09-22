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
        // Worth a line because a confined or single-threaded pool is invisible from
        // the throughput alone, and both have happened here.
        LOGI("DisaggregatedSession: CPU delegate threadpool has %zu threads",
             ::executorch::extension::threadpool::get_threadpool()->get_thread_count());
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
     * this appends rather than replacing, and BOS goes on the first piece only.
     */
    int Prefill(const std::string& prompt_text) {
        if (!tokenizer_) return 0;
        const int8_t add_bos = fed_tokens_.empty() ? 1 : 0;
        auto encoded = tokenizer_->encode(prompt_text, add_bos, 0 /* add_eos */);
        if (!encoded.ok()) {
            LOGE("DisaggregatedSession: tokenizer encode failed");
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
        const int8_t add_bos = fed_tokens_.empty() ? 1 : 0;
        auto encoded = tokenizer_->encode(tail_text, add_bos, 0 /* add_eos */);
        if (!encoded.ok()) {
            LOGE("DisaggregatedSession: tokenizer encode failed");
            return false;
        }
        if (!encoded.get().empty() && !FeedTokens(encoded.get())) return false;
        if (fed_tokens_.empty()) {
            LOGE("DisaggregatedSession: nothing was fed, refusing to decode from an empty cache");
            return false;
        }
        HandoffStates();
        prefilled_ = true;
        return true;
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

        // Always bring the NPU up to this prompt. The old code trusted a prefilled_ flag
        // set by the warm-up calls and then decoded from the last warm segment's
        // prediction, so the app answered a question it had never been asked.
        if (!AppendAndHandoff(prompt_text) && !stop_requested_) {
            return {};
        }

        if (stop_requested_) {
            return {2 /* CANCELLED */, (int64_t)prompt_len_, 0, (int64_t)prefill_ms_, 0};
        }

        // Nothing is pinned here, and that is a measured decision rather than an
        // omission. Decode looks like one thread chasing one dependency chain, so an
        // earlier version pinned it to the widest core in the kernel's capacity table,
        // which on this chip is cpu7. It is not one thread: the XNNPACK delegate fans
        // every matmul across a pthreadpool, and pthreadpool runs one share of each
        // parallel region on the calling thread. Pinning the caller confined the pool
        // with it. On the Poco X8 Pro Max the decode threads then read cpus=7 for the
        // caller and cpus=4-7 for its seven workers: eight runnable threads over four
        // cores, so every parallel region waited on a doubled-up share.
        //
        // Measured against the app's own ExecuTorch path, same .pte, same prompt, same
        // thermal state: pinned gave 3.2 busy cores and 6 tok/s, unpinned gives all
        // threads cpus=0-7, 6.12 busy cores and 31 tok/s. The earlier reading that put
        // pinning ahead (13.02 against 10.58) came from the standalone shell runner,
        // where the pool is single-threaded anyway and the only question is which core
        // the one thread lands on. See docs/research/npu-pd-disaggregation.md.

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
            const auto step_start = std::chrono::high_resolution_clock::now();
            auto res = cpu_module_->execute("forward", cpu_step_inputs);
            forward_ms += std::chrono::duration<double, std::milli>(
                std::chrono::high_resolution_clock::now() - step_start).count();
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
        // Splitting the loop from the model it drives, because the two have been
        // confused here before: forward() is 99.8% of a token, so a decode that looks
        // slow is the export and its kernels, never the loop around them.
        LOGI("DisaggregatedSession: forward() was %.2f ms of that (%.1f%%), %.2f ms per token",
             forward_ms, 100.0 * forward_ms / decode_ms,
             forward_ms / std::max(1, generated_count - 1));

        int64_t reason = stop_requested_ ? 2 /* CANCELLED */ : (generated_count >= max_new_tokens ? 1 /* MAX_TOKENS */ : 0 /* END_OF_TURN */);
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
