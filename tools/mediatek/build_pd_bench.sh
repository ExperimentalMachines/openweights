#!/bin/sh
# Builds pd_bench_mtk for an arm64 Android phone on a Mac or a Linux x86_64 host: llama.cpp with the CPU and Vulkan
# backends, from the same commit and with the same CPU flags as the Snapdragon matrix
# (b2e5e9b, release b10549, docs/backend/snapdragon/CMakeUserPresets.json), so the CPU cells of
# the two studies differ by chip and not by build. Vulkan replaces OpenCL and Hexagon, which do
# not exist on a Dimensity.
#
#   LLAMA=~/ow-models/mtk-pd/llama-b10549 HEADERS=~/ow-models/mtk-pd \
#   NDK=/opt/homebrew/share/android-commandlinetools/ndk/29.0.14206865 tools/mediatek/build_pd_bench.sh
#
# LLAMA is `git -C core/engine/src/main/cpp/llama.cpp archive b2e5e9b`, unpacked. HEADERS holds
# clones of KhronosGroup/Vulkan-Headers (vulkan.hpp, which the NDK lacks, and a vulkan_core.h
# that matches it) and KhronosGroup/SPIRV-Headers (spirv/unified1); see
# docs/research/gpu-backends.md. b10549 also wants SPIRV-Headers as a CMake package:
# `cmake -S spirv-headers -B spirv-build -DCMAKE_INSTALL_PREFIX=spirv-install && cmake --install
# spirv-build` inside HEADERS. Output: $LLAMA/pkg/{bin,lib}.
set -eu
: "${LLAMA:?llama.cpp b2e5e9b source}" "${HEADERS:?folder with vulkan-headers and spirv-headers}" "${NDK:?Android NDK}"
HERE=$(cd "$(dirname "$0")" && pwd)
case "$(uname -s)" in Darwin) HOSTTAG=darwin-x86_64; JOBS=$(sysctl -n hw.ncpu);; *) HOSTTAG=linux-x86_64; JOBS=$(nproc);; esac
B="$LLAMA/build-mtk"
FLAGS="-march=armv8.7a+fp16+dotprod+i8mm -fvectorize -ffp-model=fast -fno-finite-math-only -flto -D_GNU_SOURCE"
INC="-I$HEADERS/vulkan-headers/include -I$HEADERS/spirv-headers/include"
cmake -S "$LLAMA" -B "$B" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-31 -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DCMAKE_INSTALL_RPATH='$ORIGIN;$ORIGIN/..' \
  -DCMAKE_C_FLAGS="$FLAGS $INC" -DCMAKE_CXX_FLAGS="$FLAGS $INC" \
  -DCMAKE_C_FLAGS_RELEASE="-O3 -DNDEBUG" -DCMAKE_CXX_FLAGS_RELEASE="-O3 -DNDEBUG" \
  -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DGGML_VULKAN=ON \
  -DVulkan_GLSLC_EXECUTABLE="$NDK/shader-tools/$HOSTTAG/glslc" \
  -DSPIRV-Headers_DIR="$HEADERS/spirv-install/share/cmake/SPIRV-Headers" \
  -DLLAMA_OPENSSL=OFF -DLLAMA_CURL=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_SERVER=OFF -DLLAMA_BUILD_TOOLS=ON
cmake --build "$B" -j "$JOBS" --target llama ggml llama-bench
# Only these targets were built, so `cmake --install` would look for the rest; copy instead.
mkdir -p "$LLAMA/pkg/bin" "$LLAMA/pkg/lib" "$LLAMA/pkg/include"
cp "$B"/bin/*.so "$LLAMA/pkg/lib/"
cp "$B/bin/llama-bench" "$LLAMA/pkg/bin/"
cp "$LLAMA"/include/*.h "$LLAMA"/ggml/include/*.h "$LLAMA/pkg/include/"
CXX="$NDK/toolchains/llvm/prebuilt/$HOSTTAG/bin/aarch64-linux-android31-clang++"
"$CXX" -O2 -std=c++20 -I"$LLAMA/pkg/include" "$HERE/pd_bench_mtk.cpp" \
  -L"$LLAMA/pkg/lib" -lllama -lggml -lggml-base -o "$LLAMA/pkg/bin/pd_bench_mtk"
cp "$NDK/toolchains/llvm/prebuilt/$HOSTTAG/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$LLAMA/pkg/lib/"
ls -l "$LLAMA/pkg/bin" "$LLAMA/pkg/lib"
