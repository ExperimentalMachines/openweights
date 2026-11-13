"""ExecuTorch v1.6.0: make Qualcomm's LLM runner servable through the Android LlmModule.

Written against v1.5.1; v1.6.0 leaves the runner and the LLM JNI layer byte for byte as they
were, so every replacement below still matches exactly once.

    python patch_qnn.py <executorch-checkout>

Each replacement must match exactly once, so a tree that has drifted fails here rather than
building something that only looks patched.

1. stop(): the runner's stop() is empty, so a client cannot end a reply at max_tokens or a
   stop string. A flag the decode loops check after each token.
2. reset(): also empty, so every conversation continued the previous one's position until
   the window filled. It now rewinds to position 0; the attention masks are rebuilt from the
   position on every call (token_generator.cpp init_attention_mask), so stale cache entries
   past it are never attended.
3. A prompt that does not fit the window aborted the process (ET_CHECK_MSG). It now returns
   an error whose log line names the overflow, as the XNNPACK runner's does. The bound is
   the prompt processor's, not the window's: it asserts that a prompt ends at most one
   prefill block (ar_len) short of the window, so a guard at the window let a history ending
   in that last block through to the assert, which killed the app (Snapdragon 8 Elite,
   2026-10-11: a reopened 1,966-token conversation on a 2,048 export with 128-token blocks).
4. prefill(): not implemented (IRunner's default returns NotSupported). Text-only prefill at
   the current position, so a long prompt can be fed in pieces.
5. The JNI layer hard-codes decoder_model "llama3", which picks Llama 3's stop tokens for every
   model. It now reads EXECUTORCH_QNN_DECODER_MODEL (qwen3, gemma3, ...), set by the app before
   it constructs the module, and keeps "llama3" when unset.
6. logits_to_token kept its float copy of the logits in a function-static vector sized on first
   use, so after a model switch to a larger vocabulary (Qwen3 151,936 to Gemma 3 262,144) it
   wrote past the allocation. It now grows to the current model's vocabulary; the sampler reads
   only that many.
7. The prompt processor dropped the result of each block's forward and went on to update the
   cache and sample, so a delegate failure mid-prefill became an answer from stale logits. The
   error now returns.
8. The stop request was cleared at the decode loop's entry, so a stop asked for on the first
   token (sampled by the prefill) was forgotten and that token entered the cache, while the app
   records a stopped reply's last token as never fed. It is now cleared when generate() starts.
"""

import pathlib
import sys

root = pathlib.Path(sys.argv[1])
R = root / "examples/qualcomm/oss_scripts/llama/runner"


def edit(path: pathlib.Path, pairs):
    text = path.read_text()
    for old, new in pairs:
        count = text.count(old)
        if count != 1:
            sys.exit(f"{path}: expected one match, found {count}:\n{old[:200]}")
        text = text.replace(old, new)
    path.write_text(text)
    print(f"patched {path.relative_to(root)}")


edit(R / "token_generator.h", [(
    "class TokenGenerator {\n public:\n",
    "class TokenGenerator {\n public:\n"
    "  // Ends the running generate() after the token in progress (execuserve patch).\n"
    "  void request_stop() {\n    stop_requested_.store(true);\n  }\n"
    "  // Cleared when a generation starts, not when its decode loop does (openweights patch).\n"
    "  void clear_stop() {\n    stop_requested_.store(false);\n  }\n",
), (
    " protected:\n",
    " protected:\n  std::atomic<bool> stop_requested_{false};\n",
)])
th = (R / "token_generator.h").read_text()
if "#include <atomic>" not in th:
    (R / "token_generator.h").write_text(th.replace("#pragma once\n", "#pragma once\n\n#include <atomic>\n", 1))

for name in ("token_generator.cpp", "lhd_token_generator.cpp"):
    edit(R / name, [(
        "  while (pos < seq_len - 1) {\n",
        "  while (pos < seq_len - 1) {\n"
        "    if (stop_requested_.load()) {\n      break;\n    }\n",
    )])

edit(R / "runner.h", [(
    "  int32_t context_len_{0};\n",
    "  int32_t context_len_{0};\n"
    "  // The prompt processor's block; a prompt must end this far short of the window.\n"
    "  int32_t prompt_ar_len_{0};\n",
), (
    "  void stop() override {};\n  void reset() override {};\n",
    "  void stop() override;\n  void reset() override;\n"
    "  executorch::runtime::Result<uint64_t> prefill(\n"
    "      const std::vector<executorch::extension::llm::MultimodalInput>& inputs,\n"
    "      int32_t num_bos = 0,\n"
    "      int32_t num_eos = 0) override;\n",
)])

edit(R / "runner.cpp", [(
    """  int64_t end_pos = 0;
  ET_CHECK_MSG(
      !c10::add_overflows(
          cur_pos_, static_cast<int64_t>(num_prompt_tokens), &end_pos) &&
          end_pos < static_cast<int64_t>(seq_len),
      "sequence length exceeded - please increase the seq_len value");
""",
    """  int64_t end_pos = 0;
  if (c10::add_overflows(
          cur_pos_, static_cast<int64_t>(num_prompt_tokens), &end_pos) ||
      end_pos >= static_cast<int64_t>(seq_len) ||
      (attention_sink_rope_runner_ == nullptr &&
       end_pos > static_cast<int64_t>(context_len_) - prompt_ar_len_)) {
    ET_LOG(
        Error,
        "Prompt exceeds KV cache capacity: %lld held + %d new, past %d",
        static_cast<long long>(cur_pos_),
        num_prompt_tokens,
        std::min(seq_len - 1, context_len_ - prompt_ar_len_));
    return Error::InvalidArgument;
  }
""",
), (
    "  max_ar_len = std::max(token_generator_ar_len, prompt_processor_ar_len);\n",
    "  max_ar_len = std::max(token_generator_ar_len, prompt_processor_ar_len);\n"
    "  prompt_ar_len_ = prompt_processor_ar_len;\n",
), (
    "  stats_.inference_start_ms = time_in_ms();\n",
    "  stats_.inference_start_ms = time_in_ms();\n"
    "  if (token_generator_) {\n    token_generator_->clear_stop();\n  }\n",
), (
    "Error Runner::generate(\n",
    """void Runner::stop() {
  if (token_generator_) {
    token_generator_->request_stop();
  }
}

void Runner::reset() {
  cur_pos_ = 0;
}

Result<uint64_t> Runner::prefill(
    const std::vector<llm::MultimodalInput>& inputs,
    int32_t /*num_bos*/,
    int32_t /*num_eos*/) {
  if (!is_loaded()) {
    ET_CHECK_OK_OR_RETURN_ERROR(load());
  }
  std::vector<uint64_t> tokens;
  for (const auto& input : inputs) {
    if (!input.is_text()) {
      return Error::NotSupported;
    }
    // BOS only at the very start, as generate() does.
    int32_t n_bos = (cur_pos_ == 0 && tokens.empty()) ? 1 : 0;
    tokenizers::Result<std::vector<uint64_t>> encoded =
        tokenizer_->encode(input.get_text(), n_bos, 0);
    ET_CHECK_TK_OK_OR_RETURN_ERROR(encoded.error(), "failed to encode prefill text");
    tokens.insert(tokens.end(), encoded.get().begin(), encoded.get().end());
  }
  if (tokens.empty()) {
    return Error::InvalidArgument;
  }
  int64_t end_pos = cur_pos_ + static_cast<int64_t>(tokens.size());
  if (attention_sink_rope_runner_ == nullptr &&
      end_pos > static_cast<int64_t>(context_len_) - prompt_ar_len_) {
    ET_LOG(
        Error,
        "Prompt exceeds KV cache capacity: %lld held + %zu new, past %d",
        static_cast<long long>(cur_pos_),
        tokens.size(),
        context_len_ - prompt_ar_len_);
    return Error::InvalidArgument;
  }
  auto result = prompt_processor_->prefill(
      tokens, cur_pos_, false, attention_sink_rope_runner_.get());
  ET_CHECK_OK_OR_RETURN_ERROR(result.error());
  cur_pos_ = end_pos;
  return result.get();
}

Error Runner::generate(
""",
)])

edit(R / "decoder_runner.h", [(
    "    static std::vector<float> logits_f(vocab_size);\n",
    "    // Grown to this model's vocabulary on every call: sized once, it overran after a switch\n"
    "    // to a model with a larger one (openweights patch).\n"
    "    static thread_local std::vector<float> logits_f;\n"
    "    if (logits_f.size() < static_cast<size_t>(vocab_size)) {\n"
    "      logits_f.resize(vocab_size);\n"
    "    }\n",
)])

edit(R / "prompt_processor.cpp", [(
    "    // Run inference\n    decoder_runner_->step(method_name_, inputs_);\n",
    "    // Run inference; a failed block must not go on to update the cache (openweights patch).\n"
    "    ET_CHECK_OK_OR_RETURN_ERROR(decoder_runner_->step(method_name_, inputs_).error());\n",
)])

edit(root / "extension/android/jni/jni_layer_llama.cpp", [(
    '        std::string decoder_model = "llama3"; // use llama3 for now\n',
    '        // The family picks the runner\'s stop tokens and prompt handling; the app\n'
    '        // names it before constructing the module (execuserve patch).\n'
    '        const char* family = std::getenv("EXECUTORCH_QNN_DECODER_MODEL");\n'
    '        std::string decoder_model = (family && *family) ? family : "llama3";\n',
)])
# prefill() reads MultimodalInput, which irunner.h only declares.
rc = R / "runner.cpp"
if "multimodal_input.h" not in rc.read_text():
    rc.write_text("#include <executorch/extension/llm/runner/multimodal_input.h>\n" + rc.read_text())

jni = root / "extension/android/jni/jni_layer_llama.cpp"
if "#include <cstdlib>" not in jni.read_text():
    jni.write_text("#include <cstdlib>\n" + jni.read_text())
print("ok")
