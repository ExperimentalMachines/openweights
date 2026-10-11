#!/usr/bin/env bash
# Builds MediaTek's NPU runtime for the app on a Linux x86_64 host: libexecutorch_pd_jni.so (the
# NPU-prefill, CPU-decode runner), libneuron_backend.so, and libneuron_buffer_allocator.so
# (built from neuron_buffer_allocator.cpp), against ExecuTorch v1.5.1, the release the app links.
# Every library is linked for 16 KB pages, which Google Play requires of an arm64 library.
#
#   tools/npu/build_mediatek_linux.sh <work dir> <output dir>
#
# The recipe is ExecuServe's Modal build (tools/executorch/build_mediatek.py there), which runs
# this repository's runner with its JNI entry points renamed; here they keep this app's names.
# Needs about 16 cores, 32 GB of memory and 60 GB of disk; on a c7i.4xlarge it takes under an hour.
# Only NeuronAdapter.h is taken from the NeuroPilot Express SDK. Its buffer allocator is a binary
# linked for 4 KB pages, so it is replaced by our own; the NeuroPilot adapter itself is never
# shipped, the phone's own copy is used.
set -euo pipefail

WORK="${1:?work directory}"
OUT="${2:?output directory}"
HERE="$(cd "$(dirname "$0")" && pwd)"

TAG=v1.5.1
NDK_VERSION=r26c
# NeuroPilot Express SDK 8.0.8, checked against this digest (ExecuServe's pin).
SDK_URL="https://s3.ap-southeast-1.amazonaws.com/mediatek.neuropilot.com/b1c485de-fca8-4b20-836a-8b33152a3609.gz"
SDK_SHA256=42155c3b25e9f9af1bef55534d0700835f32564a289e8cb1e44f0b2433ddab48
KLEIDIAI=b87ef9c94f45f11c81a6b1fdaed1b2b45ea58c0c
JOBS="$(nproc)"
PAGE_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

mkdir -p "$WORK" "$OUT/arm64-v8a"
cd "$WORK"

if [ ! -d venv ]; then
  python3 -m venv venv
fi
# shellcheck disable=SC1091
. venv/bin/activate
pip install --quiet --upgrade pip
pip install --quiet torch==2.14.0 --index-url https://download.pytorch.org/whl/cpu
pip install --quiet pyyaml ruamel.yaml zstd certifi tomli setuptools wheel requests tqdm 'cmake>=3.29,<4' ninja

NDK="$WORK/android-ndk-$NDK_VERSION"
if [ ! -d "$NDK" ]; then
  curl -sSfL -o ndk.zip "https://dl.google.com/android/repository/android-ndk-$NDK_VERSION-linux.zip"
  unzip -q ndk.zip && rm ndk.zip
fi

SDK="$WORK/neuropilot"
if [ ! -d "$SDK" ]; then
  curl -sSfL --retry 3 -o sdk.tar.gz "$SDK_URL"
  echo "$SDK_SHA256  sdk.tar.gz" | sha256sum -c -
  mkdir -p "$SDK" && tar -C "$SDK" -xzf sdk.tar.gz && rm sdk.tar.gz
fi

KAI="$WORK/kleidiai"
if [ ! -d "$KAI" ]; then
  curl -sSfL --retry 3 -o kai.zip "https://github.com/ARM-software/kleidiai/archive/$KLEIDIAI.zip"
  unzip -q kai.zip && mv "kleidiai-$KLEIDIAI" "$KAI" && rm kai.zip
fi

SRC="$WORK/executorch"
PATCH="$HERE/patches/executorch-pd-1.5.1.patch"
STAMP="$TAG $(sha256sum "$PATCH" | cut -d' ' -f1)"
if [ ! -d "$SRC" ]; then
  git clone --depth 1 --branch "$TAG" --recurse-submodules --shallow-submodules \
    https://github.com/pytorch/executorch.git "$SRC"
  git -C "$SRC" apply "$PATCH"
  echo "$STAMP" > "$SRC/.openweights-source"
fi
# A checkout kept from an earlier run builds what it was patched with, whatever this script
# now says: one made by another tag or patch is refused rather than reused.
[ "$(cat "$SRC/.openweights-source" 2>/dev/null)" = "$STAMP" ] ||
  { echo "$SRC was made from another tag or patch; remove it" >&2; exit 1; }

find_one() { # <root> <name>: the arm64 copy when there is one
  local hit
  hit="$(find "$1" -name "$2" -path '*arm64*' | sort | head -n1)"
  [ -n "$hit" ] || hit="$(find "$1" -name "$2" | sort | head -n1)"
  [ -n "$hit" ] || { echo "$2 not found under $1" >&2; exit 1; }
  echo "$hit"
}
cp "$(find_one "$SDK" NeuronAdapter.h)" "$SRC/backends/mediatek/runtime/include/api/NeuronAdapter.h"
for source in mtk_pd_disaggregated_jni.cpp mtk_pd_disaggregated_runner.cpp cpu_forward_bench.cpp; do
  cp "$HERE/$source" "$SRC/examples/mediatek/executor_runner/$source"
done

BUILD="$SRC/cmake-android-out"
SUB="$BUILD/examples/mediatek"
COMMON=(-DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a
  -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release -G Ninja
  -DCMAKE_SHARED_LINKER_FLAGS="$PAGE_FLAGS" -DCMAKE_MODULE_LINKER_FLAGS="$PAGE_FLAGS")
for attempt in 1 2 3; do
  cmake -S "$SRC" -B "$BUILD" "${COMMON[@]}" \
    -DEXECUTORCH_BUILD_PRESET_FILE="$SRC/tools/cmake/preset/llm.cmake" \
    -DEXECUTORCH_BUILD_NEURON=ON -DCMAKE_INSTALL_PREFIX="$BUILD" -DKLEIDIAI_SOURCE_DIR="$KAI" && break
  echo "==> configure failed (attempt $attempt)"
  [ "$attempt" = 3 ] && exit 1
done
cmake --build "$BUILD" --target install -j "$JOBS"

# Where the package configs are installed moved between releases; found rather than assumed.
CONFIG="$(find "$BUILD" -iname 'executorch-config.cmake' -not -path '*_deps*' | head -n1)"
[ -n "$CONFIG" ] || CONFIG="$(find "$BUILD" -iname 'executorchconfig.cmake' -not -path '*_deps*' | head -n1)"
GFLAGS="$(find "$BUILD" -iname 'gflags-config.cmake' | head -n1)"
[ -n "$CONFIG" ] && [ -n "$GFLAGS" ] || { echo "package configs not found" >&2; exit 1; }
cmake -S "$SRC/examples/mediatek" -B "$SUB" "${COMMON[@]}" \
  -Dexecutorch_DIR="$(dirname "$CONFIG")" -Dgflags_DIR="$(dirname "$GFLAGS")" \
  -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH
cmake --build "$SUB" --target executorch_pd_jni -j "$JOBS"

# The MediaTek project links the parent's installed archives, not the ones its build writes, so
# they must be identical: a stale unoptimised XNNPACK once decoded at a quarter of the speed.
for archive in backends/xnnpack/third-party/XNNPACK/libXNNPACK.a backends/xnnpack/libxnnpack_backend.a \
  extension/llm/custom_ops/libcustom_ops.a kernels/optimized/libcpublas.a libexecutorch_core.a; do
  cmp -s "$BUILD/$archive" "$BUILD/lib/$(basename "$archive")" ||
    { echo "installed $(basename "$archive") differs from the one just built" >&2; exit 1; }
done

STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
"$STRIP" --strip-unneeded -o "$OUT/arm64-v8a/libexecutorch_pd_jni.so" "$SUB/libexecutorch_pd_jni.so"
"$STRIP" --strip-unneeded -o "$OUT/arm64-v8a/libneuron_backend.so" "$BUILD/backends/mediatek/libneuron_backend.so"
# The allocator, compiled against the header the backend reads so its MemoryUnit layout matches.
CXX="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++"
"$CXX" --target=aarch64-linux-android26 -std=c++17 -O2 -fPIC -shared -frtti -fexceptions \
  -fvisibility=hidden -static-libstdc++ -DC10_USING_CUSTOM_GENERATED_MACROS $PAGE_FLAGS \
  -I"$SRC/backends/mediatek/runtime/include" -I"$(dirname "$SRC")" \
  -I"$SRC/runtime/core/portable_type/c10" \
  -o "$OUT/arm64-v8a/libneuron_buffer_allocator.unstripped.so" "$HERE/neuron_buffer_allocator.cpp" \
  -lnativewindow -llog -ldl
"$STRIP" --strip-unneeded -o "$OUT/arm64-v8a/libneuron_buffer_allocator.so" \
  "$OUT/arm64-v8a/libneuron_buffer_allocator.unstripped.so"
rm "$OUT/arm64-v8a/libneuron_buffer_allocator.unstripped.so"
echo "pytorch/executorch $TAG + patches/executorch-pd-1.5.1.patch; NDK $NDK_VERSION; NeuroPilot SDK ${SDK_SHA256:0:12} headers; 16 KB pages" \
  > "$OUT/SOURCE.txt"
ls -la "$OUT/arm64-v8a"
echo "==> done"
