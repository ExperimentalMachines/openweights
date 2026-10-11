#!/usr/bin/env bash
# Builds ExecuTorch v1.5.1's Android runtime for the app on a Linux x86_64 host:
# libexecutorch.so (XNNPACK, Vulkan, QNN and the LLM JNI layer) and libqnn_executorch_backend.so,
# with patch_qnn.py applied and every library linked for 16 KB pages.
#
#   tools/npu/build_executorch_android_linux.sh <work dir> <output dir>
#
# The recipe is ExecuServe's (tools/executorch/build_libs.py there, run on Modal), which builds
# the native half of scripts/build_android_library.sh; the Java classes do not change and come
# from the published AAR (tools/npu/assemble_runtime.sh). One change from it: NDK r26c links for
# 4 KB pages by default, and Google Play refuses an arm64 library that is not 16 KB aligned for
# an app at this target SDK, so the linker is told the page size. ExecuServe's build was 4 KB
# aligned; ExecuTorch's published AAR, which the app shipped before, is 16 KB.
set -euo pipefail

WORK="${1:?work directory}"
OUT="${2:?output directory}"
HERE="$(cd "$(dirname "$0")" && pwd)"

TAG=v1.5.1
NDK_VERSION=r26c
QAIRT=2.37.0.250724
VULKAN_SDK=1.4.341.1
# XNNPACK's KleidiAI pin at the v1.5.1 tag (backends/xnnpack/third-party/XNNPACK/cmake/DownloadKleidiAI.cmake).
KLEIDIAI=b87ef9c94f45f11c81a6b1fdaed1b2b45ea58c0c
PAGE_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
JOBS="$(nproc)"

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

QNN_SDK="$WORK/qairt-zip/qairt/$QAIRT"
if [ ! -d "$QNN_SDK" ]; then
  curl -sSfL --retry 3 -o qairt.zip \
    "https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/$QAIRT/v$QAIRT.zip"
  mkdir -p qairt-zip && unzip -q qairt.zip -d qairt-zip && rm qairt.zip
fi
ls "$QNN_SDK/include/QNN" >/dev/null

# The Vulkan shaders need LunarG's glslc; the NDK's lacks GL_EXT_integer_dot_product.
VK="$WORK/vulkansdk/$VULKAN_SDK/x86_64/bin"
if [ ! -x "$VK/glslc" ]; then
  curl -sSfL --retry 3 -o vk.tar.xz \
    "https://sdk.lunarg.com/sdk/download/$VULKAN_SDK/linux/vulkansdk-linux-x86_64-$VULKAN_SDK.tar.xz"
  mkdir -p vulkansdk && tar -C vulkansdk -xJf vk.tar.xz && rm vk.tar.xz
fi
export PATH="$VK:$PATH"
glslc --version | head -n1

KAI="$WORK/kleidiai"
if [ ! -d "$KAI" ]; then
  curl -sSfL --retry 3 -o kai.zip "https://github.com/ARM-software/kleidiai/archive/$KLEIDIAI.zip"
  unzip -q kai.zip && mv "kleidiai-$KLEIDIAI" "$KAI" && rm kai.zip
fi

# ExecuTorch's CMake refuses a checkout not named executorch (pytorch/executorch#6475).
SRC="$WORK/android/executorch"
STAMP="$TAG $(sha256sum "$HERE/patch_qnn.py" | cut -d' ' -f1)"
if [ ! -d "$SRC" ]; then
  mkdir -p "$(dirname "$SRC")"
  git clone --depth 1 --branch "$TAG" --recurse-submodules --shallow-submodules \
    https://github.com/pytorch/executorch.git "$SRC"
  python3 "$HERE/patch_qnn.py" "$SRC"
  echo "$STAMP" > "$SRC/.openweights-source"
fi
# A checkout kept from an earlier run builds what it was patched with: refused when it was
# made by another tag or patch script.
[ "$(cat "$SRC/.openweights-source" 2>/dev/null)" = "$STAMP" ] ||
  { echo "$SRC was made from another tag or patch; remove it" >&2; exit 1; }

BUILD=cmake-out-android-arm64-v8a
cd "$SRC"
for attempt in 1 2 3; do
  cmake . -DCMAKE_INSTALL_PREFIX="$BUILD" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DPYTHON_EXECUTABLE=python --preset android-arm64-v8a -DANDROID_PLATFORM=android-26 \
    -DCMAKE_SHARED_LINKER_FLAGS="$PAGE_FLAGS" -DCMAKE_MODULE_LINKER_FLAGS="$PAGE_FLAGS" \
    -DEXECUTORCH_BUILD_EXTENSION_LLM=ON -DEXECUTORCH_BUILD_EXTENSION_LLM_RUNNER=ON \
    -DEXECUTORCH_BUILD_EXTENSION_ASR_RUNNER=ON -DEXECUTORCH_BUILD_EXTENSION_TRAINING=ON \
    -DEXECUTORCH_BUILD_LLAMA_JNI=ON -DEXECUTORCH_BUILD_NEURON=OFF \
    -DEXECUTORCH_BUILD_QNN=ON -DQNN_SDK_ROOT="$QNN_SDK" \
    -DEXECUTORCH_BUILD_VULKAN=ON -DXNNPACK_ENABLE_ARM_SME2=ON -DFLATCC_ALLOW_WERROR=OFF \
    -DSUPPORT_REGEX_LOOKAHEAD=ON -DCMAKE_BUILD_TYPE=Release -DKLEIDIAI_SOURCE_DIR="$KAI" \
    -B"$BUILD" && break
  echo "==> configure failed (attempt $attempt)"
  [ "$attempt" = 3 ] && exit 1
done
cmake --build "$BUILD" -j "$JOBS" --target install --config Release

STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
JNI="$(find "$BUILD/extension/android" -maxdepth 1 -name '*.so' | sort | head -n1)"
[ -n "$JNI" ] || { echo "JNI library not built" >&2; exit 1; }
"$STRIP" --strip-unneeded -o "$OUT/arm64-v8a/libexecutorch.so" "$JNI"
"$STRIP" --strip-unneeded -o "$OUT/arm64-v8a/libqnn_executorch_backend.so" \
  "$BUILD/lib/executorch/backends/qualcomm/libqnn_executorch_backend.so"
echo "pytorch/executorch $TAG + patch_qnn.py; NDK $NDK_VERSION; QAIRT $QAIRT headers; 16 KB pages" \
  > "$OUT/SOURCE-executorch.txt"
ls -la "$OUT/arm64-v8a"
echo "==> done"
