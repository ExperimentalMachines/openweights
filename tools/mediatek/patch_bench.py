"""Adds what pd_bench_mtk needs to an ExecuTorch tree that already carries
tools/npu/patches/executorch-pd-1.6.0.patch:

  * LlamaModelChunk::RollbackTokens(n): drop the newest n positions from every attention cache
    (MediaTek's own RollbackCache, which the runtime only uses for padding) and rebuild the mask;
  * LlamaModelChunk::AdoptTokenIndex(n): take a cache written from outside as holding n positions
    (also used after Reset(), which upstream leaves with a stale mask: Codex review, 2026-11-16);
  * the same two on LlamaRuntime, which keeps its own token index, and TakeSwapFailed(): upstream
    SwapModel logs a chunk that failed to swap graphs and carries on as if it had;
  * an mtk_pd_bench target in examples/mediatek, linking llama.cpp's shared libraries from
    -DLLAMA_PKG=<dir with include/ and lib/> and compiling pd_bench_mtk.cpp with PD_NPU.

Each marker-delimited addition is replaced whole on a rerun, but the plain replacements are not:
the scaling assignments land inside the Dual-LUT replacement's text, so a second run on a patched
tree finds neither and refuses (Codex review, round three). Run it on pristine sources, as
build_npu_bench_linux.sh does: it restores every file this script edits before calling it.
Anchors must exist exactly once, or the script refuses.

  python3 patch_bench.py <executorch root> <dir holding pd_bench_mtk.cpp and npu_engine.h>
"""
import pathlib
import re
import shutil
import sys

root = pathlib.Path(sys.argv[1])
tools = pathlib.Path(sys.argv[2])
runner = root / "examples/mediatek/executor_runner"
llama = runner / "llama_runner"
BEGIN, END = "pd_bench_mtk begin", "pd_bench_mtk end"


def block(path, anchor, text, comment="//"):
    s = path.read_text()
    marked = f"{comment} {BEGIN}\n{text}{comment} {END}\n"
    pattern = re.compile(re.escape(f"{comment} {BEGIN}\n") + r".*?" + re.escape(f"{comment} {END}\n"), re.S)
    if pattern.search(s):
        s = pattern.sub(lambda _: marked, s, count=1)
    elif anchor is None:
        s = s.rstrip("\n") + "\n\n" + marked
    else:
        if s.count(anchor) != 1:
            sys.exit(f"{path}: anchor not found exactly once: {anchor!r}")
        s = s.replace(anchor, anchor + marked)
    path.write_text(s)


def replace_once(path, old, new):
    s = path.read_text()
    if new in s:
        return
    if s.count(old) != 1:
        sys.exit(f"{path}: text to replace not found exactly once: {old!r}")
    path.write_text(s.replace(old, new))


block(llama / "LlamaModelChunk.h", "  size_t GetTokenIndex() const;\n", """
  // Drop the newest n positions from every attention cache, the inverse of reading them, and
  // take a cache written from outside as holding n positions. The mask is rebuilt either way.
  void RollbackTokens(const size_t n) {
    const size_t k = n < mCurrentTokenIndex ? n : mCurrentTokenIndex;
    RollbackCache(k, mCurrentTokenIndex);
    mCurrentTokenIndex -= k;
    mCurrentPadSize = 0;
    mMaskBuilder->markMaskDirty();
  }
  void AdoptTokenIndex(const size_t n) {
    mCurrentTokenIndex = n;
    mCurrentPadSize = 0;
    mMaskBuilder->markMaskDirty();
  }
""")

block(llama / "LlamaRuntime.h", "  const LlamaModelOptions& GetModelOptions() const;\n", """
  void RollbackTokens(const size_t n) {
    for (auto& c : mLlamaModelChunks) {
      static_cast<LlamaModelChunk*>(c.get())->RollbackTokens(n);
    }
    mTokenIndex -= (n < mTokenIndex ? n : mTokenIndex);
  }
  void AdoptTokenIndex(const size_t n) {
    for (auto& c : mLlamaModelChunks) {
      static_cast<LlamaModelChunk*>(c.get())->AdoptTokenIndex(n);
    }
    mTokenIndex = n;
  }
  // True once if any chunk failed its last graph swap.
  bool TakeSwapFailed() {
    return mSwapFailed.exchange(false);
  }
  std::atomic<bool> mSwapFailed{false};
""")
replace_once(llama / "LlamaRuntime.h", "#include <string>\n", "#include <atomic>\n#include <string>\n")
replace_once(
    llama / "LlamaRuntime.cpp",
    '    if (!status)\n      ET_LOG(Error, "Hot swapping failed on chunk %zu", chunkIdx);\n',
    '    if (!status) {\n      ET_LOG(Error, "Hot swapping failed on chunk %zu", chunkIdx);\n'
    "      mSwapFailed = true; // pd_bench_mtk\n    }\n",
)

# Gemma 3 exports take two combined rotary inputs, the global layers' table and the sliding
# layers' table at rot_emb_local_base (execupack pipeline/export_mtk.py, runner block), while the
# runtime builds one table and, given two inputs, treats them as cos and sin. A subclass fills
# input 0 from the global table and input 1 from a local one, only when the export names a local base.
replace_once(
    llama / "LlamaRuntime.cpp",
    "namespace example {\n\nvoid LlamaRuntime::Initialize(",
    """namespace example {

// pd_bench_mtk (patch_bench.py): two combined rotary inputs, global then local.
class DualRotaryEmbeddingLut : public llm_helper::RotaryEmbeddingMasterLut {
 public:
  DualRotaryEmbeddingLut(const llm_helper::LLMType type, const size_t length, const size_t dim,
                         const float base, const float localBase)
      : RotaryEmbeddingMasterLut(type, length, dim, base), mLocal(type, length, dim, localBase) {
    mLocal.generate();
  }
  void setEmbed(std::vector<void*> buffers, const size_t tokenIndex, const size_t tokenBatchSize,
                const size_t leftPadLength, const size_t rightPadLength) const override {
    if (buffers.size() != 2) {
      RotaryEmbeddingMasterLut::setEmbed(buffers, tokenIndex, tokenBatchSize, leftPadLength, rightPadLength);
      return;
    }
    RotaryEmbeddingMasterLut::setEmbed(buffers[0], tokenIndex, tokenBatchSize, leftPadLength, rightPadLength);
    mLocal.setEmbed(buffers[1], tokenIndex, tokenBatchSize, leftPadLength, rightPadLength);
  }

 private:
  llm_helper::RotaryEmbeddingMasterLut mLocal;
};

void LlamaRuntime::Initialize(""",
)
replace_once(
    llama / "LlamaRuntime.cpp",
    """  mRotEmbMasterLut = std::make_unique<llm_helper::RotaryEmbeddingMasterLut>(
      modelOptions.rot_emb_type,
      modelOptions.max_token_length,
      rotEmbDim,
      modelOptions.rot_emb_base);
  mRotEmbMasterLut->generate();""",
    """  if (modelOptions.rot_emb_local_base > 0) { // pd_bench_mtk
    mRotEmbMasterLut = std::make_unique<DualRotaryEmbeddingLut>(
        modelOptions.rot_emb_type, modelOptions.max_token_length, rotEmbDim,
        modelOptions.rot_emb_base, modelOptions.rot_emb_local_base);
  } else {
    mRotEmbMasterLut = std::make_unique<llm_helper::RotaryEmbeddingMasterLut>(
        modelOptions.rot_emb_type,
        modelOptions.max_token_length,
        rotEmbDim,
        modelOptions.rot_emb_base);
  }
  mRotEmbMasterLut->generate();""",
)
replace_once(
    llama / "LlamaRuntime.cpp",
    "  constexpr size_t numRotEmbInputs = 1;",
    "  const size_t numRotEmbInputs = modelOptions.rot_emb_local_base > 0 ? 2 : 1; // pd_bench_mtk",
)

# Llama 3.1/3.2 train with "llama3" RoPE scaling (factor, low/high frequency factors, original
# context), which llama.cpp applies from the GGUF; MediaTek's table generator uses the plain
# frequencies, so an exported Llama's keys drift from the reference as position grows (measured
# 2026-11-16: K cosine NPU vs CPU 0.999 at positions 0-255 down to 0.91 past 1,280, V flat). The
# table takes the scaling when the runner names it (LlamaModelOptions.rope_llama3_*).
helper = llama / "llm_helper"
block(helper / "include/rotary_embedding.h", "  bool mIsReady = false;\n", """
 public:
  // llama3 RoPE scaling; factor 0 leaves the plain frequencies.
  double mL3Factor = 0, mL3Low = 1, mL3High = 4, mL3Orig = 8192;
  double invFreq(double base, int dim, double rotDim) const {
    double inv = 1.0 / std::pow(base, double(dim * 2) / rotDim);
    if (mL3Factor <= 0) return inv;
    const double pi = 3.14159265358979323846, wavelen = 2 * pi / inv;
    const double lowWavelen = mL3Orig / mL3Low, highWavelen = mL3Orig / mL3High;
    if (wavelen < highWavelen) return inv;
    if (wavelen > lowWavelen) return inv / mL3Factor;
    const double smooth = (mL3Orig / wavelen - mL3Low) / (mL3High - mL3Low);
    return (1 - smooth) * inv / mL3Factor + smooth * inv;
  }
 private:
""")
src = (helper / "rotary_embedding.cpp").read_text()
old = "float(pos) / std::powf(base, float(dim * 2) / rotDimFp)"
new = "float(double(pos) * invFreq(base, dim, rotDimFp)) /* pd_bench_mtk */"
if new not in src:
    if src.count(old) != 2:
        sys.exit("rotary_embedding.cpp: expected two frequency lines")
    (helper / "rotary_embedding.cpp").write_text(src.replace(old, new))
if "#include <cmath>" not in (helper / "include/rotary_embedding.h").read_text():
    replace_once(helper / "include/rotary_embedding.h", "#pragma once\n", "#pragma once\n#include <cmath>\n")
block(llama / "LlamaConfig.h", "  double rot_emb_base = 10000.0;\n", """  double rot_emb_local_base = 0.0;
  double rope_llama3_factor = 0.0, rope_llama3_low = 1.0, rope_llama3_high = 4.0, rope_llama3_orig = 8192.0;
""")
replace_once(
    llama / "LlamaRuntime.cpp",
    "  mRotEmbMasterLut->generate();\n\n  const bool useSharedWeights",
    """  mRotEmbMasterLut->mL3Factor = modelOptions.rope_llama3_factor; // pd_bench_mtk
  mRotEmbMasterLut->mL3Low = modelOptions.rope_llama3_low;
  mRotEmbMasterLut->mL3High = modelOptions.rope_llama3_high;
  mRotEmbMasterLut->mL3Orig = modelOptions.rope_llama3_orig;
  mRotEmbMasterLut->generate();

  const bool useSharedWeights""",
)

for name in ("pd_bench_mtk.cpp", "npu_engine.h"):
    shutil.copy(tools / name, runner / name)

# At the end of the file, after the if(ANDROID) block that defines the runner sources.
block(root / "examples/mediatek/CMakeLists.txt", None, """
# llama.cpp's CPU and Vulkan beside this runtime's NPU in one process, so a turn can prefill on
# one and decode on another (OpenWeights tools/mediatek/patch_bench.py).
if(${ANDROID} AND DEFINED LLAMA_PKG)
  set(_pd_bench_srcs ${_mtk_executor_runner__srcs})
  list(FILTER _pd_bench_srcs EXCLUDE REGEX ".*executor_runner.*\\\\.cpp$")
  list(PREPEND _pd_bench_srcs ${CMAKE_CURRENT_LIST_DIR}/executor_runner/pd_bench_mtk.cpp)
  add_executable(mtk_pd_bench ${_pd_bench_srcs})
  set_target_properties(mtk_pd_bench PROPERTIES CXX_STANDARD 20)
  target_compile_definitions(mtk_pd_bench PRIVATE PD_NPU)
  target_include_directories(
    mtk_pd_bench
    PUBLIC ${_common_include_directories}
           ${EXECUTORCH_ROOT}/cmake-android-out/third-party/gflags/include
           ${CMAKE_CURRENT_LIST_DIR}/executor_runner
           ${LLAMA_PKG}/include
  )
  target_sources(
    mtk_pd_bench
    PRIVATE
      ${EXECUTORCH_ROOT}/kernels/prim_ops/register_prim_ops.cpp
      ${EXECUTORCH_ROOT}/kernels/prim_ops/et_view.cpp
      ${EXECUTORCH_ROOT}/kernels/prim_ops/et_copy_index.cpp
  )
  target_link_libraries(
    mtk_pd_bench
    ${_executor_runner_libs}
    neuron_backend
    mtk_llama_executor_lib
    tokenizers
    extension_module
    ${LLAMA_PKG}/lib/libllama.so
    ${LLAMA_PKG}/lib/libggml.so
    ${LLAMA_PKG}/lib/libggml-base.so
    log
  )
  target_compile_options(mtk_pd_bench PUBLIC ${_common_compile_options})
endif()
""", comment="#")
print("patched", root)
