#!/bin/sh
# Builds libexecutorch_pd_jni.so, the disaggregated runner and cpu_forward_bench from an
# ExecuTorch release/1.4 checkout, and strips the two libraries the app loads into
# core/engine's debug jniLibs.
#
#   EXECUTORCH=~/abliteration/vendor/executorch \
#   ANDROID_NDK=~/chips/ndk \
#   NEUROPILOT_SDK=~/neuropilot_sdk/neuropilot-express-sdk-8.0.8-build20250925 \
#   tools/npu/build_pd_libs.sh
#
# It is a script because each of these was paid for once, and each presents as a slow model
# rather than as a build error:
#
#   1. examples/mediatek is its own CMake project. A build type set on the parent does not
#      reach it, and an empty CMAKE_BUILD_TYPE means CMake passes no -O flag at all.
#   2. That project links the parent's installed archives in cmake-android-out/lib, not the
#      ones the parent's build writes. Building the parent without installing leaves every
#      binary linked against whatever was installed last: here, an unoptimised XNNPACK that
#      ran a decode step in 116 ms against 28 for the Release one.
#   3. So the last step checks that the installed archives are byte-identical to the ones
#      just built, and refuses to strip anything into the app if they are not.
#
# The tree it expects is release/1.4 at e4d02f4, which is what patches/ was cut against.
set -eu

: "${EXECUTORCH:?path to an ExecuTorch release/1.4 checkout}"
: "${ANDROID_NDK:?path to the Android NDK}"
: "${NEUROPILOT_SDK:?path to the NeuroPilot Express SDK, for NeuronAdapter.h}"

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
OUT="$EXECUTORCH/cmake-android-out"
SUB="$OUT/examples/mediatek"
JNI_LIBS="$ROOT/core/engine/src/debug/jniLibs/arm64-v8a"
PATCH="$HERE/patches/executorch-release-1.4-pd.patch"
JOBS=${JOBS:-$(sysctl -n hw.ncpu 2>/dev/null || nproc)}
TOOLCHAIN="$ANDROID_NDK/build/cmake/android.toolchain.cmake"
STRIP=$(ls "$ANDROID_NDK"/toolchains/llvm/prebuilt/*/bin/llvm-strip | head -1)

echo "== patching $EXECUTORCH"
if git -C "$EXECUTORCH" apply --reverse --check "$PATCH" 2>/dev/null; then
  echo "   already applied"
else
  git -C "$EXECUTORCH" apply "$PATCH"
fi
# MediaTek's header is proprietary, so it comes from the SDK rather than from this repo.
cp "$NEUROPILOT_SDK/api/NeuronAdapter.h" "$EXECUTORCH/backends/mediatek/runtime/include/api/"
for source in mtk_pd_disaggregated_jni.cpp mtk_pd_disaggregated_runner.cpp cpu_forward_bench.cpp; do
  cp "$HERE/$source" "$EXECUTORCH/examples/mediatek/executor_runner/$source"
done

echo "== configuring and installing the runtime (Release)"
cmake -S "$EXECUTORCH" -B "$OUT" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
  -DEXECUTORCH_BUILD_PRESET_FILE="$EXECUTORCH/tools/cmake/preset/llm.cmake" \
  -DEXECUTORCH_BUILD_NEURON=ON \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$OUT" >/dev/null
cmake --build "$OUT" --target install -j "$JOBS" >/dev/null

echo "== configuring and building the MediaTek targets (Release)"
cmake -S "$EXECUTORCH/examples/mediatek" -B "$SUB" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
  -DCMAKE_PREFIX_PATH="$OUT/lib/cmake/ExecuTorch;$OUT/third-party/gflags" \
  -DCMAKE_BUILD_TYPE=Release >/dev/null
cmake --build "$SUB" --target executorch_pd_jni mtk_pd_disaggregated_runner cpu_forward_bench -j "$JOBS" >/dev/null

echo "== checking the binaries were linked against what was just built"
for build_type in "$(grep '^CMAKE_BUILD_TYPE:' "$OUT/CMakeCache.txt")" \
                  "$(grep '^CMAKE_BUILD_TYPE:' "$SUB/CMakeCache.txt")"; do
  case "$build_type" in
    *=Release) ;;
    *) echo "a tree is not a Release build: $build_type" >&2; exit 1 ;;
  esac
done
for archive in backends/xnnpack/third-party/XNNPACK/libXNNPACK.a \
               backends/xnnpack/libxnnpack_backend.a \
               extension/llm/custom_ops/libcustom_ops.a \
               kernels/optimized/libcpublas.a \
               libexecutorch_core.a; do
  if ! cmp -s "$OUT/$archive" "$OUT/lib/$(basename "$archive")"; then
    echo "installed $(basename "$archive") differs from the one just built" >&2
    exit 1
  fi
done
echo "   Release in both trees; installed archives match the build"

echo "== stripping into $JNI_LIBS"
mkdir -p "$JNI_LIBS"
"$STRIP" --strip-unneeded -o "$JNI_LIBS/libexecutorch_pd_jni.so" "$SUB/libexecutorch_pd_jni.so"
"$STRIP" --strip-unneeded -o "$JNI_LIBS/libneuron_backend.so" "$OUT/backends/mediatek/libneuron_backend.so"
ls -la "$JNI_LIBS"
echo "done. The runner and the bench are in $SUB, for adb push."
