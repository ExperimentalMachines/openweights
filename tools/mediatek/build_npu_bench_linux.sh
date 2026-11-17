#!/usr/bin/env bash
# Builds mtk_pd_bench (pd_bench_mtk.cpp with the NPU) and MediaTek's stock mtk_llama_executor_runner
# against ExecuTorch v1.6.0 + tools/npu/patches/executorch-pd-1.6.0.patch (the runtime the app
# ships), on a Linux x86_64 host, with llama.cpp b10549 (CPU + Vulkan) built by the same NDK.
#
#   tools/mediatek/build_npu_bench_linux.sh <work dir> <output dir> <llama.cpp b2e5e9b source>
#
# Step one is tools/npu/build_mediatek_linux.sh unchanged (ExecuTorch, the Neuron backend, our
# buffer allocator); then patch_bench.py, llama.cpp, and the two runner targets.
set -euo pipefail
WORK="${1:?work directory}"; OUT="${2:?output directory}"; LLAMA="${3:?llama.cpp source}"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
[ "${SKIP_RUNTIME:-0}" = 1 ] || bash "$REPO/tools/npu/build_mediatek_linux.sh" "$WORK" "$OUT"

NDK="$WORK/android-ndk-r26c"
SRC="$WORK/executorch"
BUILD="$SRC/cmake-android-out"
SUB="$BUILD/examples/mediatek"
# shellcheck disable=SC1091
. "$WORK/venv/bin/activate"
# The files patch_bench.py edits go back to the app patch's state first, so a rerun never stacks
# an older version of the edits under the current one.
LR=examples/mediatek/executor_runner/llama_runner
git -C "$SRC" checkout -- examples/mediatek/CMakeLists.txt $LR/LlamaModelChunk.h $LR/LlamaRuntime.h $LR/LlamaRuntime.cpp $LR/LlamaConfig.h $LR/llm_helper/rotary_embedding.cpp $LR/llm_helper/include/rotary_embedding.h
git -C "$SRC" apply --include=examples/mediatek/CMakeLists.txt --include="$LR/LlamaModelChunk.h" \
  --include="$LR/LlamaRuntime.h" --include="$LR/LlamaRuntime.cpp" "$REPO/tools/npu/patches/executorch-pd-1.6.0.patch"
python3 "$HERE/patch_bench.py" "$SRC" "$HERE"
# The llama.cpp tree must be the Snapdragon matrix's commit (written by the archive step).
[ "$(cat "$LLAMA/.source-commit" 2>/dev/null)" = b2e5e9b28b2484fbf94b543432ece638996a8b97 ] ||
  { echo "$LLAMA is not llama.cpp b2e5e9b (no matching .source-commit)" >&2; exit 1; }

H="$WORK/headers"
mkdir -p "$H"
[ -d "$H/vulkan-headers" ] || git clone -q --depth 1 https://github.com/KhronosGroup/Vulkan-Headers "$H/vulkan-headers"
[ -d "$H/spirv-headers" ] || git clone -q --depth 1 https://github.com/KhronosGroup/SPIRV-Headers "$H/spirv-headers"
[ -d "$H/spirv-install" ] || { cmake -S "$H/spirv-headers" -B "$H/spirv-build" -G Ninja -DCMAKE_INSTALL_PREFIX="$H/spirv-install" -DSPIRV_HEADERS_ENABLE_TESTS=OFF >/dev/null && cmake --install "$H/spirv-build" >/dev/null; }
LLAMA="$LLAMA" HEADERS="$H" NDK="$NDK" sh "$HERE/build_pd_bench.sh"

# c++_shared, as llama.cpp's libraries are built: one C++ runtime in the process (Codex review).
COMMON=(-DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a
  -DANDROID_PLATFORM=android-31 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release -G Ninja)
CONFIG="$(find "$BUILD" -iname 'executorch-config.cmake' -not -path '*_deps*' | head -n1)"
[ -n "$CONFIG" ] || CONFIG="$(find "$BUILD" -iname 'executorchconfig.cmake' -not -path '*_deps*' | head -n1)"
GFLAGS="$(find "$BUILD" -iname 'gflags-config.cmake' | head -n1)"
cmake -S "$SRC/examples/mediatek" -B "$SUB" "${COMMON[@]}" \
  -Dexecutorch_DIR="$(dirname "$CONFIG")" -Dgflags_DIR="$(dirname "$GFLAGS")" \
  -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH -DLLAMA_PKG="$LLAMA/pkg"
cmake --build "$SUB" --target mtk_pd_bench mtk_llama_executor_runner -j "$(nproc)"

STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
mkdir -p "$OUT/bench"
"$STRIP" --strip-unneeded -o "$OUT/bench/mtk_pd_bench" "$SUB/mtk_pd_bench"
"$STRIP" --strip-unneeded -o "$OUT/bench/mtk_llama_executor_runner" "$SUB/mtk_llama_executor_runner"
"$STRIP" --strip-unneeded -o "$OUT/bench/pd_bench_mtk" "$LLAMA/pkg/bin/pd_bench_mtk"
for so in "$LLAMA"/pkg/lib/*.so; do "$STRIP" --strip-unneeded -o "$OUT/bench/$(basename "$so")" "$so"; done
cp "$OUT/arm64-v8a/libneuron_backend.so" "$OUT/arm64-v8a/libneuron_buffer_allocator.so" "$OUT/bench/"
( cd "$OUT/bench" && sha256sum * > SHA256SUMS )
echo "pd_bench_mtk.cpp $(sha256sum "$HERE/pd_bench_mtk.cpp" | cut -c1-16), npu_engine.h $(sha256sum "$HERE/npu_engine.h" | cut -c1-16), patch_bench.py $(sha256sum "$HERE/patch_bench.py" | cut -c1-16), llama.cpp b2e5e9b" > "$OUT/bench/SOURCE.txt"
ls -la "$OUT/bench"
echo "==> bench done"
