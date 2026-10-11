"""ExecuTorch v1.5.1: make Qualcomm's LLM runner servable through the Android LlmModule.

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
   an error whose log line names the overflow, as the XNNPACK runner's does.
4. prefill(): not implemented (IRunner's default returns NotSupported). Text-only prefill at
   the current position, so a long prompt can be fed in pieces.
5. The JNI layer hard-codes decoder_model "llama3", which picks Llama 3's stop tokens for every
   model. It now reads EXECUTORCH_QNN_DECODER_MODEL (qwen3, gemma3, ...), set by the app before
   it constructs the module, and keeps "llama3" when unset.
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
    "  void request_stop() {\n    stop_requested_.store(true);\n  }\n",
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
        "  stop_requested_.store(false);\n  while (pos < seq_len - 1) {\n"
        "    if (stop_requested_.load()) {\n      break;\n    }\n",
    )])

edit(R / "runner.h", [(
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
      end_pos >= static_cast<int64_t>(seq_len)) {
    ET_LOG(
        Error,
        "Prompt exceeds KV cache capacity: %lld held + %d new >= %d",
        static_cast<long long>(cur_pos_),
        num_prompt_tokens,
        seq_len);
    return Error::InvalidArgument;
  }
""",
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
  if (attention_sink_rope_runner_ == nullptr && end_pos >= context_len_) {
    ET_LOG(
        Error,
        "Prompt exceeds KV cache capacity: %lld held + %zu new >= %d",
        static_cast<long long>(cur_pos_),
        tokens.size(),
        context_len_);
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
