/*
 * Copyright (c) 2026 OpenWeights Authors
 *
 * Licensed under the BSD License (the "License"); you may not use this file
 * except in compliance with the License.
 */

#include "executorch/backends/mediatek/runtime/include/NeuronBufferAllocator.h"

#include <chrono>
#include <cmath>
#include <cstring>
#include <iomanip>
#include <iostream>
#include <memory>
#include <random>
#include <sched.h>
#include <string>
#include <vector>

#include <gflags/gflags.h>

#include <executorch/extension/data_loader/file_data_loader.h>
#include <executorch/extension/evalue_util/print_evalue.h>
#include <executorch/extension/module/module.h>
#include <executorch/runtime/executor/method.h>
#include <executorch/runtime/executor/program.h>
#include <executorch/runtime/platform/log.h>
#include <executorch/runtime/platform/runtime.h>

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

// Model Architecture Flags
DEFINE_uint64(prompt_token_batch_size, 128, "Token batch size for NPU prompt model.");
DEFINE_uint64(cache_size, 512, "MediaTek NPU model cache size (e.g. 512).");
DEFINE_uint64(hidden_size, 2048, "Model hidden size (2048 for LFM 1.2B, 4096 for 2.6B).");
DEFINE_uint64(num_head, 16, "Number of attention heads in each layer.");
DEFINE_uint64(num_layer, 16, "Total number of layers in the model.");
DEFINE_uint64(head_dim, 64, "Head dimension of the model.");
DEFINE_uint64(window_size, 0, "Window size of Sliding Window Attention.");
DEFINE_uint64(max_token_length, 2048, "Maximum token length that the model supports.");
DEFINE_double(partial_rotary_factor, 1.0, "Partial rotary factor of the model.");
DEFINE_double(rot_emb_base, 1000000.0, "Rotary embedding base value, aka 'rope_theta'.");

// Model IO Types
DEFINE_string(input_type, "int16", "Model input type. Default to 'int16'");
DEFINE_string(output_type, "int16", "Model output type. Default to 'int16'");
DEFINE_string(cache_type, "float32", "Model cache type. Default to 'float32'");
DEFINE_string(mask_type, "int16", "Model mask type. Default to 'int16'");
DEFINE_string(rot_emb_type, "int16", "Model rotary embedding type. Default to 'int16'");

// Paths
DEFINE_string(token_embedding_path, "embedding.bin", "Input token embedding lookup table path for NPU.");
DEFINE_string(prompt_model_paths, "", "Comma-separated prompt model chunk paths for NPU.");
DEFINE_string(model_package_paths, "", "Comma-separated weight-shared model package paths for NPU.");
DEFINE_string(cpu_model_path, "", "Path to CPU ExecuTorch .pte model (XNNPACK / KleidiAI / portable).");
DEFINE_string(tokenizer_path, "tokenizer.model", "Tokenizer vocab/model path.");
DEFINE_string(tokenizer_type, "tiktoken", "Tokenizer type. One of ['bpe', 'tiktoken', 'hf'].");

// Generation options
DEFINE_uint64(max_response, 50, "Maximum number of tokens to generate.");
DEFINE_string(prompt_file, "", "File containing the prompt text.");
DEFINE_string(prompt, "", "Direct prompt string.");
DEFINE_bool(test_cpu_prefill, false, "Run sequential CPU prefill on the prompt.");
DEFINE_bool(pin_decode, true, "Pin the CPU decode thread to one core. The app's own CPU path is multi threaded, so this is measured, not assumed.");
DEFINE_bool(diag_restore_pos0, false, "Diagnostic: after handoff, restore prompt position 0 from the CPU reference state.");
DEFINE_bool(interleaved_cache_slots, false, "Treat a chunk's cache slots as per-layer pairs rather than all-K then all-V.");
DEFINE_bool(skip_state_handoff, false, "Skip state handoff from NPU to CPU (useful when running CPU prefill).");

static constexpr int8_t kAddBos = 1;
static constexpr int8_t kAddEos = 0;

using namespace example::llm_helper;
using example::LlamaModelChunk;
using example::LlamaModelOptions;
using example::LlamaModelPaths;
using example::LlamaRuntime;
using example::ModelChunk;
using example::utils::argmax;
using example::utils::read_file;
using example::utils::split;
using executorch::aten::ScalarType;
using executorch::aten::Tensor;
using executorch::aten::TensorImpl;
using executorch::extension::Module;
using executorch::runtime::Error;
using executorch::runtime::EValue;
using executorch::runtime::Method;
using executorch::runtime::Result;
using tokenizers::HFTokenizer;
using tokenizers::Llama2cTokenizer;
using tokenizers::Tokenizer;

struct LayerMapping {
  enum class Type { ShortConv, Attention };
  Type type;
  size_t layer_idx;      // 0..15
  size_t chunk_idx;      // 0..3
  size_t chunk_cache_k;  // cache index in chunk for key/conv
  size_t chunk_cache_v;  // cache index in chunk for val (if attn)
  size_t cpu_val_k;      // value index in CPU Method for key/conv
  size_t cpu_val_v;      // value index in CPU Method for val (if attn)
};

// Build layer mapping for LFM 2.5 1.2B
// 16 layers: 10 ShortConv, 6 Attention
// ShortConv layers: 0, 1, 3, 4, 6, 7, 9, 11, 13, 15
// Attention layers: 2, 5, 8, 10, 12, 14
std::vector<LayerMapping> build_lfm_1_2b_layer_mappings() {
  std::vector<LayerMapping> mappings;
  mappings.reserve(16);

  // In CPU forward method, state tensors are located sequentially:
  // Layer 0: Conv (45)
  // Layer 1: Conv (46)
  // Layer 2: Attn K (47), V (48)
  // Layer 3: Conv (49)
  // Layer 4: Conv (50)
  // Layer 5: Attn K (51), V (52)
  // Layer 6: Conv (53)
  // Layer 7: Conv (54)
  // Layer 8: Attn K (55), V (56)
  // Layer 9: Conv (57)
  // Layer 10: Attn K (58), V (59)
  // Layer 11: Conv (60)
  // Layer 12: Attn K (61), V (62)
  // Layer 13: Conv (63)
  // Layer 14: Attn K (64), V (65)
  // Layer 15: Conv (66)

  size_t cpu_val = 45;
  for (size_t l = 0; l < 16; ++l) {
    size_t chunk = l / 4;
    size_t layer_in_chunk = l % 4;
    size_t chunk_k = FLAGS_interleaved_cache_slots ? 2 * layer_in_chunk : layer_in_chunk;
    size_t chunk_v = FLAGS_interleaved_cache_slots ? 2 * layer_in_chunk + 1 : 4 + layer_in_chunk;

    bool is_attn = (l == 2 || l == 5 || l == 8 || l == 10 || l == 12 || l == 14);
    if (is_attn) {
      mappings.push_back({
          LayerMapping::Type::Attention,
          l,
          chunk,
          chunk_k,
          chunk_v,
          cpu_val,      // K
          cpu_val + 1   // V
      });
      cpu_val += 2;
    } else {
      mappings.push_back({
          LayerMapping::Type::ShortConv,
          l,
          chunk,
          chunk_k,
          0,
          cpu_val,      // Conv state
          0
      });
      cpu_val += 1;
    }
  }
  return mappings;
}

LlamaModelOptions get_model_options() {
  LlamaModelOptions options = {
      .prompt_token_batch_size = FLAGS_prompt_token_batch_size,
      .cache_size = FLAGS_cache_size,
      .hidden_size = FLAGS_hidden_size,
      .num_head = FLAGS_num_head,
      .num_layer = FLAGS_num_layer,
      .head_dim = FLAGS_head_dim,
      .window_size = FLAGS_window_size,
      .max_token_length = FLAGS_max_token_length,
      .partial_rotary_factor = FLAGS_partial_rotary_factor,
      .rot_emb_base = FLAGS_rot_emb_base,
      .model_input_type = getLLMTypeFromName(FLAGS_input_type.c_str()),
      .model_output_type = getLLMTypeFromName(FLAGS_output_type.c_str()),
      .cache_type = getLLMTypeFromName(FLAGS_cache_type.c_str()),
      .mask_type = getLLMTypeFromName(FLAGS_mask_type.c_str()),
      .rot_emb_type = getLLMTypeFromName(FLAGS_rot_emb_type.c_str())};
  return options;
}

std::unique_ptr<Tokenizer> load_tokenizer() {
  std::unique_ptr<Tokenizer> tokenizer;
  if (FLAGS_tokenizer_type == "bpe") {
    tokenizer = std::make_unique<Llama2cTokenizer>();
  } else if (FLAGS_tokenizer_type == "tiktoken") {
    tokenizer = example::get_tiktoken_for_llama();
  } else if (FLAGS_tokenizer_type == "hf") {
    tokenizer = std::make_unique<HFTokenizer>();
  }
  ET_CHECK_MSG(tokenizer, "Invalid tokenizer type: %s", FLAGS_tokenizer_type.c_str());
  tokenizer->load(FLAGS_tokenizer_path);
  return tokenizer;
}

int main(int argc, char** argv) {
  executorch::runtime::runtime_init();
  gflags::ParseCommandLineFlags(&argc, &argv, true);

  std::cout << "=========================================================\n"
            << "  OpenWeights Disaggregated Runner (NPU Prefill / CPU Decode)\n"
            << "  Target: MediaTek Dimensity 9400 (MT6991) + Cortex-X925 CPU\n"
            << "=========================================================\n"
            << std::endl;

  ET_CHECK_MSG(
      !FLAGS_model_package_paths.empty() || !FLAGS_prompt_model_paths.empty(),
      "Either model_package_paths or prompt_model_paths must be provided.");
  ET_CHECK_MSG(!FLAGS_cpu_model_path.empty(), "CPU model path must be provided.");

  // 1. Load Tokenizer
  std::cout << "[1/5] Loading Tokenizer: " << FLAGS_tokenizer_path << "..." << std::endl;
  auto tokenizer = load_tokenizer();
  const auto vocab_size = tokenizer->vocab_size();
  std::cout << "      Tokenizer loaded. Vocab size: " << vocab_size << std::endl;

  // 2. Initialize NPU Runtime
  std::cout << "[2/5] Initializing MediaTek NeuroPilot NPU Runtime (4 chunks)..." << std::endl;
  LlamaModelOptions npu_options = get_model_options();
  LlamaModelPaths npu_paths = {
      .tokenizer_path = FLAGS_tokenizer_path,
      .token_embedding_path = FLAGS_token_embedding_path,
      .prompt_model_paths = split(FLAGS_prompt_model_paths, ','),
      .gen_model_paths = {},
      .model_package_paths = split(FLAGS_model_package_paths, ','),
  };
  LlamaRuntime npu_runtime;
  auto npu_load_start = std::chrono::high_resolution_clock::now();
  npu_runtime.Initialize(npu_options, npu_paths);
  auto npu_load_end = std::chrono::high_resolution_clock::now();
  double npu_load_sec = std::chrono::duration<double>(npu_load_end - npu_load_start).count();
  std::cout << "      NPU chunks loaded in " << std::fixed << std::setprecision(2) << npu_load_sec << " s" << std::endl;

  // 3. Initialize CPU Module (XNNPACK / KleidiAI)
  std::cout << "[3/5] Initializing CPU ExecuTorch Module: " << FLAGS_cpu_model_path << "..." << std::endl;
  auto cpu_load_start = std::chrono::high_resolution_clock::now();
  Module cpu_module(FLAGS_cpu_model_path, Module::LoadMode::File);
  auto err = cpu_module.load_method("forward");
  ET_CHECK_MSG(err == Error::Ok, "Failed to load method 'forward' in CPU model");
  auto method_res = cpu_module.method("forward");
  ET_CHECK_MSG(method_res.ok(), "Failed to get Method pointer from CPU module");
  Method* cpu_method = *method_res;
  auto cpu_load_end = std::chrono::high_resolution_clock::now();
  double cpu_load_sec = std::chrono::duration<double>(cpu_load_end - cpu_load_start).count();
  std::cout << "      CPU model loaded in " << std::fixed << std::setprecision(2) << cpu_load_sec << " s" << std::endl;

  // Inspect all tensors in cpu_method to find exact state tensor indices
  std::cout << "      Scanning CPU method values table (" << cpu_method->values_size() << " values)..." << std::endl;
  std::vector<size_t> conv_indices;
  std::vector<size_t> attn_indices;
  for (size_t i = 0; i < cpu_method->values_size(); ++i) {
    auto& val = cpu_method->mutable_value(i);
    if (!val.isTensor()) continue;
    auto t = val.toTensor();
    if (t.dim() == 3 && t.size(0) == 1 && t.size(1) == 2048 && t.size(2) == 2) {
      conv_indices.push_back(i);
    } else if (t.dim() == 4 && t.size(0) == 1 && ((t.size(1) == 2048 && t.size(2) == 8 && t.size(3) == 64) || (t.size(1) == 8 && t.size(2) == 2048 && t.size(3) == 64))) {
      attn_indices.push_back(i);
    }
  }
  std::cout << "      Found " << conv_indices.size() << " Conv state tensors at indices: ";
  for (auto idx : conv_indices) std::cout << idx << " ";
  std::cout << "\n      Found " << attn_indices.size() << " Attn KV tensors at indices: ";
  for (auto idx : attn_indices) std::cout << idx << " ";
  std::cout << std::endl;

  // Verify and bind layer mappings
  auto layer_mappings = build_lfm_1_2b_layer_mappings();
  std::cout << "      Validating CPU state tensors in Method values table..." << std::endl;
  for (const auto& m : layer_mappings) {
    if (m.type == LayerMapping::Type::Attention) {
      ET_CHECK_MSG(m.cpu_val_k < cpu_method->values_size(), "CPU value index out of range for Attn K");
      ET_CHECK_MSG(m.cpu_val_v < cpu_method->values_size(), "CPU value index out of range for Attn V");
      auto& tk = cpu_method->mutable_value(m.cpu_val_k);
      auto& tv = cpu_method->mutable_value(m.cpu_val_v);
      ET_CHECK_MSG(tk.isTensor() && tv.isTensor(), "Attn values must be Tensors");
      auto tk_tensor = tk.toTensor();
      auto tv_tensor = tv.toTensor();
      std::cout << "      Layer " << m.layer_idx << " Attn K (val " << m.cpu_val_k << ") shape: ["
                << tk_tensor.size(0) << "," << tk_tensor.size(1) << "," << tk_tensor.size(2) << "," << tk_tensor.size(3) << "]" << std::endl;
    } else {
      ET_CHECK_MSG(m.cpu_val_k < cpu_method->values_size(), "CPU value index out of range for Conv");
      auto& tc = cpu_method->mutable_value(m.cpu_val_k);
      ET_CHECK_MSG(tc.isTensor(), "Conv value must be a Tensor");
      auto tc_tensor = tc.toTensor();
      std::cout << "      Layer " << m.layer_idx << " Conv (val " << m.cpu_val_k << ") shape: ["
                << tc_tensor.size(0) << "," << tc_tensor.size(1) << "," << tc_tensor.size(2) << "]" << std::endl;
    }
  }
  std::cout << "      Verified all 22 state tensors (10 ShortConv + 6 Attention K/V)!" << std::endl;

  // 4. Prepare Prompt
  std::string prompt_text;
  if (!FLAGS_prompt_file.empty()) {
    prompt_text = read_file(FLAGS_prompt_file);
  } else if (!FLAGS_prompt.empty()) {
    prompt_text = FLAGS_prompt;
  } else {
    prompt_text = "What is the capital of the Philippines?";
  }

  auto encode_res = tokenizer->encode(prompt_text, kAddBos, kAddEos);
  ET_CHECK_MSG(encode_res.ok(), "Tokenizer failed to encode prompt");
  std::vector<uint64_t> input_tokens = std::move(encode_res.get());
  const size_t prompt_len = input_tokens.size();

  std::cout << "\n[Prompt] \"" << prompt_text << "\"" << std::endl;
  std::cout << "[Prompt Tokens] " << prompt_len << " tokens\n" << std::endl;

  // 5. PHASE 1: NPU PREFILL
  std::cout << "[4/5] Executing PHASE 1: NPU Prefill on Dimensity 9400 NPU..." << std::endl;
  const size_t batch_size = npu_runtime.GetTokenBatchSize();
  size_t cur_token_index = 0;
  void* last_logits = nullptr;

  auto prefill_start = std::chrono::high_resolution_clock::now();
  while (cur_token_index < prompt_len) {
    size_t num_remain = prompt_len - cur_token_index;
    size_t remainder = num_remain % batch_size;
    size_t num_new = remainder ? remainder : batch_size;
    std::vector<uint64_t> chunk_tokens(
        input_tokens.begin() + cur_token_index,
        input_tokens.begin() + cur_token_index + num_new);
    last_logits = npu_runtime.Run(chunk_tokens);
    cur_token_index += chunk_tokens.size();
  }
  auto prefill_end = std::chrono::high_resolution_clock::now();
  double prefill_sec = std::chrono::duration<double>(prefill_end - prefill_start).count();
  double prefill_tok_s = (double)prompt_len / prefill_sec;

  const auto logits_type = npu_runtime.GetModelOptions().model_output_type;
  uint64_t first_output_token = argmax(logits_type, last_logits, vocab_size);

  std::cout << "      NPU Prefill completed in " << std::fixed << std::setprecision(3)
            << prefill_sec * 1000.0 << " ms (" << std::fixed << std::setprecision(1)
            << prefill_tok_s << " tok/s)" << std::endl;
  std::cout << "      First output token: " << first_output_token << " (\""
            << tokenizer->decode(0, first_output_token).get() << "\")" << std::endl;

  // 5.5 Optional: Run CPU Prefill as reference
  std::vector<std::vector<float>> reference_states(layer_mappings.size());
  std::vector<std::vector<float>> reference_states_v(layer_mappings.size());
  if (FLAGS_test_cpu_prefill) {
    std::cout << "\n[Reference] Running CPU Prefill for " << prompt_len << " tokens..." << std::endl;
    int64_t step_tok = 0;
    int64_t step_pos = 0;
    int32_t t_sizes[] = {1, 1};
    uint8_t t_dim_order[] = {0, 1};
    TensorImpl t_impl(ScalarType::Long, 2, t_sizes, &step_tok, t_dim_order);
    Tensor t_tensor(&t_impl);

    int32_t p_sizes[] = {1};
    uint8_t p_dim_order[] = {0};
    TensorImpl p_impl(ScalarType::Long, 1, p_sizes, &step_pos, p_dim_order);
    Tensor p_tensor(&p_impl);

    std::vector<EValue> cpu_step_inputs;
    cpu_step_inputs.push_back(t_tensor);
    cpu_step_inputs.push_back(p_tensor);

    const float* cpu_last_logits = nullptr;
    auto cpu_pref_start = std::chrono::high_resolution_clock::now();
    for (size_t s = 0; s < prompt_len; ++s) {
      step_tok = input_tokens[s];
      step_pos = s;
      auto res = cpu_module.execute("forward", cpu_step_inputs);
      ET_CHECK_MSG(res.ok(), "CPU prefill step failed");
      if (s == prompt_len - 1) {
        cpu_last_logits = res.get()[0].toTensor().const_data_ptr<float>();
      }
    }
    auto cpu_pref_end = std::chrono::high_resolution_clock::now();
    double cpu_pref_ms = std::chrono::duration<double, std::milli>(cpu_pref_end - cpu_pref_start).count();
    std::cout << "      CPU Prefill finished in " << std::fixed << std::setprecision(2) << cpu_pref_ms << " ms" << std::endl;

    float max_v = cpu_last_logits[0];
    uint64_t cpu_first_tok = 0;
    for (size_t v = 1; v < vocab_size; ++v) {
      if (cpu_last_logits[v] > max_v) {
        max_v = cpu_last_logits[v];
        cpu_first_tok = v;
      }
    }
    auto dec_p = tokenizer->decode(0, cpu_first_tok);
    std::cout << "      CPU Prefill predicted first token: " << cpu_first_tok
              << " (\"" << (dec_p.ok() ? dec_p.get() : "") << "\")" << std::endl;

    // Save reference states
    for (size_t mi = 0; mi < layer_mappings.size(); ++mi) {
      const auto& m = layer_mappings[mi];
      if (m.type == LayerMapping::Type::Attention) {
        // Only the prompt's own positions are ever compared or restored, and the whole
        // tensor is sixty-four megabytes at 32k, so the copy stops at the prompt.
        const size_t kept = prompt_len * 8 * 64;
        auto tk = cpu_method->mutable_value(m.cpu_val_k).toTensor();
        auto tv = cpu_method->mutable_value(m.cpu_val_v).toTensor();
        const float* k_ptr = tk.const_data_ptr<float>();
        const float* v_ptr = tv.const_data_ptr<float>();
        reference_states[mi].assign(k_ptr, k_ptr + kept);
        reference_states_v[mi].assign(v_ptr, v_ptr + kept);
      } else {
        auto tc = cpu_method->mutable_value(m.cpu_val_k).toTensor();
        const float* c_ptr = tc.const_data_ptr<float>();
        reference_states[mi].assign(c_ptr, c_ptr + tc.numel());
      }
    }
  }

  // 6. PHASE 2: STATE HANDOFF (NPU -> CPU)
  const size_t T = prompt_len;
  ET_CHECK_MSG(T <= FLAGS_cache_size, "Prompt length exceeds the NPU cache size");
  double handoff_ms = 0.0;

  if (!FLAGS_skip_state_handoff) {
    std::cout << "\n[Handoff] Transferring KV cache & Conv state from NPU memory to CPU..." << std::endl;
    auto handoff_start = std::chrono::high_resolution_clock::now();

    const auto& npu_chunks = npu_runtime.GetModelChunks();

    for (size_t ci = 0; ci < npu_chunks.size(); ++ci) {
      auto* c_chunk = static_cast<LlamaModelChunk*>(npu_chunks[ci].get());
      const auto& shape = c_chunk->GetCacheShape();
      std::cout << "      [Geometry] chunk " << ci << " cacheShape [";
      for (size_t d = 0; d < shape.size(); ++d) {
        std::cout << (d ? "," : "") << shape[d];
      }
      const size_t n = c_chunk->getNumInputsFor(LlamaModelChunk::IOKind::KVCache);
      std::cout << "] rows=" << c_chunk->GetCacheRows()
                << " strideBytes=" << c_chunk->GetCacheStrideBytes()
                << " length=" << c_chunk->GetCacheLength()
                << " slots=" << n << " nbytes=";
      for (size_t c = 0; c < n; ++c) {
        const size_t idx = c_chunk->getInputIndex(LlamaModelChunk::IOKind::KVCache, c);
        const auto* p = reinterpret_cast<const float*>(c_chunk->GetInputBuffer(idx).data);
        double sum = 0.0;
        for (size_t k = 0; k < 4096; ++k) sum += std::abs(p[k]);
        std::cout << (c ? "," : "") << c_chunk->GetInputBuffer(idx).nbytes << "/l1=" << sum;
      }
      std::cout << std::endl;
    }
    for (const auto& m : layer_mappings) {
      LlamaModelChunk* llama_chunk = static_cast<LlamaModelChunk*>(npu_chunks[m.chunk_idx].get());

      if (m.type == LayerMapping::Type::Attention) {
        size_t k_in_idx = llama_chunk->getInputIndex(LlamaModelChunk::IOKind::KVCache, m.chunk_cache_k);
        size_t v_in_idx = llama_chunk->getInputIndex(LlamaModelChunk::IOKind::KVCache, m.chunk_cache_v);
        void* mtk_k_ptr = llama_chunk->GetInputBuffer(k_in_idx).data;
        void* mtk_v_ptr = llama_chunk->GetInputBuffer(v_in_idx).data;

        auto cpu_k_tensor = cpu_method->mutable_value(m.cpu_val_k).toTensor();
        auto cpu_v_tensor = cpu_method->mutable_value(m.cpu_val_v).toTensor();
        float* cpu_k_ptr = cpu_k_tensor.mutable_data_ptr<float>();
        float* cpu_v_ptr = cpu_v_tensor.mutable_data_ptr<float>();

        const float* src_k = reinterpret_cast<const float*>(mtk_k_ptr);
        const float* src_v = reinterpret_cast<const float*>(mtk_v_ptr);


        // Transpose (num_heads=8, seq_len=512, head_dim=64) -> (seq_len=2048, num_heads=8, head_dim=64)
        for (size_t s = 0; s < T; ++s) {
          size_t mtk_s = FLAGS_cache_size - T + s;
          for (size_t h = 0; h < 8; ++h) {
            const float* k_src = src_k + (h * FLAGS_cache_size + mtk_s) * 64;
            const float* v_src = src_v + (h * FLAGS_cache_size + mtk_s) * 64;
            float* k_dst = cpu_k_ptr + (s * 8 + h) * 64;
            float* v_dst = cpu_v_ptr + (s * 8 + h) * 64;
            std::memcpy(k_dst, k_src, 64 * sizeof(float));
            std::memcpy(v_dst, v_src, 64 * sizeof(float));
          }
        }
      } else { // ShortConv
        size_t conv_in_idx = llama_chunk->getInputIndex(LlamaModelChunk::IOKind::KVCache, m.chunk_cache_k);
        void* mtk_conv_ptr = llama_chunk->GetInputBuffer(conv_in_idx).data;

        auto cpu_conv_tensor = cpu_method->mutable_value(m.cpu_val_k).toTensor();
        float* cpu_conv_ptr = cpu_conv_tensor.mutable_data_ptr<float>();
        const float* src_conv = reinterpret_cast<const float*>(mtk_conv_ptr);

        // Unstride 8 slices of 512 floats (stride = 512 * 64 floats)
        for (size_t h = 0; h < 8; ++h) {
          std::memcpy(cpu_conv_ptr + h * 512, src_conv + h * (FLAGS_cache_size * 64), 512 * sizeof(float));
        }
      }
    }

    auto handoff_end = std::chrono::high_resolution_clock::now();
    handoff_ms = std::chrono::duration<double, std::milli>(handoff_end - handoff_start).count();
    std::cout << "      State handoff completed in " << std::fixed << std::setprecision(3)
              << handoff_ms << " ms!" << std::endl;
  } else {
    std::cout << "\n[Handoff] SKIPPED state handoff (keeping CPU prefilled states)" << std::endl;
  }

  // Compare states if reference exists
  if (FLAGS_test_cpu_prefill && !FLAGS_skip_state_handoff) {
    std::cout << "\n[Verification] Comparing NPU-transferred states vs CPU reference states:" << std::endl;
    const auto& npu_chunks = npu_runtime.GetModelChunks();
    for (size_t mi = 0; mi < layer_mappings.size(); ++mi) {
      const auto& m = layer_mappings[mi];
      const auto& ref = reference_states[mi];
      auto t = cpu_method->mutable_value(m.cpu_val_k).toTensor();
      const float* actual = t.const_data_ptr<float>();
      double max_diff = 0.0;
      double sum_diff = 0.0;
      size_t numel_to_check = (m.type == LayerMapping::Type::ShortConv) ? 4096 : (prompt_len * 8 * 64);
      for (size_t i = 0; i < numel_to_check; ++i) {
        double d = std::abs(actual[i] - ref[i]);
        if (d > max_diff) max_diff = d;
        sum_diff += d;
      }
      if (m.layer_idx == 2) {
        // Is the NPU K cache the same quantity as the CPU K cache at all? Cosine is
        // scale invariant, so a pure quantisation or output-scale difference still scores
        // near 1.0, a rotary-embedding mismatch scores moderately, and an unrelated
        // tensor scores near zero. Every CPU prompt position is tried against every NPU
        // position so that no alignment convention is assumed.
        auto* scan_chunk = static_cast<LlamaModelChunk*>(npu_chunks[m.chunk_idx].get());
        const size_t k_idx =
            scan_chunk->getInputIndex(LlamaModelChunk::IOKind::KVCache, m.chunk_cache_k);
        const float* npu_k =
            reinterpret_cast<const float*>(scan_chunk->GetInputBuffer(k_idx).data);
        const size_t rows = scan_chunk->GetCacheRows();
        const size_t stride = scan_chunk->GetCacheStrideBytes() / sizeof(float);
        const size_t length = scan_chunk->GetCacheLength();
        for (size_t j : {size_t(0), prompt_len / 2, prompt_len - 1}) {
          const float* ref_j = ref.data() + j * rows * stride;
          double ref_norm = 0.0;
          for (size_t e = 0; e < rows * stride; ++e) ref_norm += double(ref_j[e]) * ref_j[e];
          ref_norm = std::sqrt(ref_norm);
          double best_cos = -2.0;
          size_t best_off = 0;
          for (size_t off = 0; off < length; ++off) {
            double dot = 0.0, nrm = 0.0;
            for (size_t h = 0; h < rows; ++h) {
              for (size_t e = 0; e < stride; ++e) {
                const double a = npu_k[(h * length + off) * stride + e];
                dot += a * ref_j[h * stride + e];
                nrm += a * a;
              }
            }
            if (nrm <= 0.0 || ref_norm <= 0.0) continue;
            const double c = dot / (std::sqrt(nrm) * ref_norm);
            if (c > best_cos) { best_cos = c; best_off = off; }
          }
          std::cout << "      [Match] CPU token " << j << " best cosine " << std::fixed
                    << std::setprecision(4) << best_cos << " at NPU position " << best_off
                    << " (right-aligned guess " << (length - prompt_len + j) << ")"
                    << std::defaultfloat << std::endl;
        }
        std::cout << "      Layer 2 Attn K sample values:\n        REF   : ";
        for (size_t i = 0; i < 8; ++i) std::cout << ref[i] << " ";
        std::cout << "\n        ACTUAL: ";
        for (size_t i = 0; i < 8; ++i) std::cout << actual[i] << " ";
        std::cout << "\n";
      }
      double mae = sum_diff / numel_to_check;
      std::cout << "      Layer " << std::setw(2) << m.layer_idx << " ("
                << (m.type == LayerMapping::Type::ShortConv ? "Conv" : "Attn") << "): "
                << "MAE=" << std::scientific << std::setprecision(3) << mae
                << ", MaxDiff=" << max_diff << std::defaultfloat << std::endl;
    }
  }

  // Diagnostic only: put the first prompt position back to what the CPU itself computed.
  // If the reply recovers, the handoff is sound everywhere except position 0, which is
  // the attention sink every layer leans on.
  if (FLAGS_diag_restore_pos0 && FLAGS_test_cpu_prefill && !FLAGS_skip_state_handoff) {
    for (size_t mi = 0; mi < layer_mappings.size(); ++mi) {
      const auto& m = layer_mappings[mi];
      if (m.type != LayerMapping::Type::Attention) continue;
      auto tk = cpu_method->mutable_value(m.cpu_val_k).toTensor();
      auto tv = cpu_method->mutable_value(m.cpu_val_v).toTensor();
      const size_t row = 8 * 64;
      std::memcpy(tk.mutable_data_ptr<float>(), reference_states[mi].data(), row * sizeof(float));
      std::memcpy(
          tv.mutable_data_ptr<float>(),
          reference_states_v[mi].data(),
          row * sizeof(float));
    }
    std::cout << "      [Diag] restored prompt position 0 from CPU reference" << std::endl;
  }

  // 7. PHASE 3: CPU DECODE (XNNPACK / KleidiAI)
  std::cout << "\n[5/5] Executing PHASE 3: CPU Decode on Cortex-X925 core..." << std::endl;

  // Pin decoding thread to Cortex-X925 Ultra Prime Core (CPU 7 @ 3.73 GHz)
  if (FLAGS_pin_decode) {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(7, &cpuset);
    if (sched_setaffinity(0, sizeof(cpu_set_t), &cpuset) == 0) {
      std::cout << "      [Affinity] Pinned CPU decode thread to core 7" << std::endl;
    }
  } else {
    std::cout << "      [Affinity] Decode left unpinned" << std::endl;
  }

  int64_t cur_token = first_output_token;
  int64_t cur_pos = T;

  int32_t token_sizes[] = {1, 1};
  uint8_t token_dim_order[] = {0, 1};
  TensorImpl token_impl(ScalarType::Long, 2, token_sizes, &cur_token, token_dim_order);
  Tensor token_tensor(&token_impl);

  int32_t pos_sizes[] = {1};
  uint8_t pos_dim_order[] = {0};
  TensorImpl pos_impl(ScalarType::Long, 1, pos_sizes, &cur_pos, pos_dim_order);
  Tensor pos_tensor(&pos_impl);

  std::vector<EValue> cpu_inputs;
  cpu_inputs.push_back(token_tensor);
  cpu_inputs.push_back(pos_tensor);

  // Bind once and drive the Method directly: resolving "forward" by string and allocating
  // a vector of EValues per token was most of a token's cost.
  ET_CHECK_MSG(cpu_method->set_input(EValue(token_tensor), 0) == Error::Ok, "bind token");
  ET_CHECK_MSG(cpu_method->set_input(EValue(pos_tensor), 1) == Error::Ok, "bind pos");

  std::string response_text;
  std::vector<uint64_t> generated_tokens = {first_output_token};
  auto first_decode_piece = tokenizer->decode(0, first_output_token);
  if (first_decode_piece.ok()) {
    response_text += first_decode_piece.get();
  }

  std::cout << "\n[Generated Stream]\n" << response_text << std::flush;

  size_t decode_count = 0;
  double total_decode_time_sec = 0.0;
  uint64_t eos_tok = tokenizer->eos_tok();

  while (decode_count < FLAGS_max_response && cur_pos < FLAGS_max_token_length) {
    auto step_start = std::chrono::high_resolution_clock::now();
    const auto exec_status = cpu_method->execute();
    auto step_end = std::chrono::high_resolution_clock::now();

    total_decode_time_sec += std::chrono::duration<double>(step_end - step_start).count();
    decode_count++;

    ET_CHECK_MSG(exec_status == Error::Ok, "CPU execute failed during decode step");
    auto logits_tensor = cpu_method->get_output(0).toTensor();
    const float* logits = logits_tensor.const_data_ptr<float>();

    // Argmax to pick next token
    float max_val = logits[0];
    uint64_t next_token = 0;
    for (size_t v = 1; v < vocab_size; ++v) {
      if (logits[v] > max_val) {
        max_val = logits[v];
        next_token = v;
      }
    }

    if (next_token == eos_tok || next_token == 7 || next_token == 2) {
      break;
    }

    auto piece = tokenizer->decode(cur_token, next_token);
    std::string str = piece.ok() ? piece.get() : "";
    response_text += str;
    std::cout << str << std::flush;

    generated_tokens.push_back(next_token);
    cur_token = next_token;
    cur_pos++;
  }

  std::cout << "\n" << std::endl;

  double decode_tok_s = (decode_count > 0 && total_decode_time_sec > 0)
                            ? (double)decode_count / total_decode_time_sec
                            : 0.0;

  // 8. FINAL BENCHMARK COMPARISON
  std::cout << "=========================================================\n"
            << "               INFERENCE PERFORMANCE SUMMARY             \n"
            << "=========================================================\n"
            << " Prompt Tokens            : " << prompt_len << " tokens\n"
            << " NPU Prefill Throughput   : " << std::fixed << std::setprecision(1) << prefill_tok_s << " tok/s\n"
            << " NPU Prefill Latency (TTFT: " << std::fixed << std::setprecision(2) << prefill_sec * 1000.0 << " ms\n"
            << " State Handoff Overhead   : " << std::fixed << std::setprecision(3) << handoff_ms << " ms\n"
            << " Tokens Generated         : " << decode_count << " tokens\n"
            << " CPU Decode Throughput    : " << std::fixed << std::setprecision(2) << decode_tok_s << " tok/s\n"
            << " CPU Decode Latency/tok   : " << std::fixed << std::setprecision(2) << (total_decode_time_sec * 1000.0 / decode_count) << " ms/tok\n"
            << " Baseline NPU Decode      : 6.62 tok/s (151.0 ms/tok)\n"
            << " Decode Speedup Factor    : " << std::fixed << std::setprecision(2) << (decode_tok_s / 6.62) << "x faster!\n"
            << "=========================================================" << std::endl;

  npu_runtime.Release();
  return 0;
}
