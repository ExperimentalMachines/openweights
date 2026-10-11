#!/usr/bin/env bash
# Packages the ExecuTorch runtime the app links: ExecuTorch 1.5.1's Android library with QNN
# (Qualcomm NPU) beside XNNPACK and Vulkan, and MediaTek's NPU runtime built for this app.
#
#   tools/npu/assemble_runtime.sh <dir holding arm64-v8a/libexecutorch.so, libqnn_executorch_backend.so>
#       <dir holding arm64-v8a/libexecutorch_pd_jni.so, libneuron_backend.so, libneuron_buffer_allocator.so>
#
# The first directory is build_executorch_android_linux.sh's output, the second
# build_mediatek_linux.sh's. The Java and Kotlin API is unchanged by the patches, so the classes,
# the x86_64 libraries (the emulator: CPU and GPU only) and the POM's dependencies come from
# ExecuTorch's published executorch-android-vulkan AAR at the same release; arm64-v8a's
# libexecutorch.so is replaced and the QNN and MediaTek libraries are added.
#
# Every arm64 library must be linked for 16 KB pages: Google Play refuses one that is not, and
# a 4 KB build once passed every check here and on the phone (2026-10-11), so this refuses it.
#
# The result is a Maven artifact in core/engine/maven, which settings.gradle.kts reads.
set -euo pipefail
LIBS="${1:?directory with the ExecuTorch arm64-v8a libraries}"
MTK="${2:?directory with the MediaTek arm64-v8a libraries}"
RELEASE=1.5.1
# A rebuilt binary is a new version: Gradle and anyone holding the old file must not mistake one for the other.
VERSION="$RELEASE-openweights.3"
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DEST="$ROOT/core/engine/maven/org/experimentalmachines/executorch/executorch-android/$VERSION"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

base="https://repo1.maven.org/maven2/org/pytorch/executorch-android-vulkan/$RELEASE/executorch-android-vulkan-$RELEASE"
curl -fsSL "$base.aar" -o "$WORK/upstream.aar"
curl -fsSL "$base.pom" -o "$WORK/upstream.pom"
mkdir -p "$WORK/aar" && (cd "$WORK/aar" && unzip -q ../upstream.aar)
for lib in libexecutorch.so libqnn_executorch_backend.so; do
  [ -f "$LIBS/arm64-v8a/$lib" ] || { echo "missing $LIBS/arm64-v8a/$lib" >&2; exit 1; }
  cp "$LIBS/arm64-v8a/$lib" "$WORK/aar/jni/arm64-v8a/$lib"
done
for lib in libexecutorch_pd_jni.so libneuron_backend.so libneuron_buffer_allocator.so; do
  [ -f "$MTK/arm64-v8a/$lib" ] || { echo "missing $MTK/arm64-v8a/$lib" >&2; exit 1; }
  cp "$MTK/arm64-v8a/$lib" "$WORK/aar/jni/arm64-v8a/$lib"
done

# Every library is a shared object for its directory's architecture, and every LOAD segment
# of every 64-bit one is aligned to at least 16 KB. A file that is not one is refused, not
# skipped: an x86_64 library under arm64-v8a passes an alignment check and loads nowhere.
python3 - "$WORK/aar/jni" <<'PY'
import os, struct, sys
MACHINES = {"arm64-v8a": (2, 0xB7), "x86_64": (2, 0x3E), "armeabi-v7a": (1, 0x28), "x86": (1, 0x03)}
bad = []
for abi in sorted(os.listdir(sys.argv[1])):
    want = MACHINES.get(abi)
    for name in sorted(os.listdir(os.path.join(sys.argv[1], abi))):
        data = open(os.path.join(sys.argv[1], abi, name), "rb").read()
        if want is None or data[:4] != b"\x7fELF" or data[5] != 1:
            bad.append(f"{abi}/{name}: not a little-endian ELF for a known ABI")
            continue
        cls, kind, machine = data[4], *struct.unpack_from("<HH", data, 0x10)
        if (cls, machine) != want or kind != 3:
            bad.append(f"{abi}/{name}: class {cls}, type {kind}, machine {machine:#x}")
            continue
        if cls != 2:
            continue
        phoff = struct.unpack_from("<Q", data, 0x20)[0]
        size, count = struct.unpack_from("<HH", data, 0x36)
        aligns = {struct.unpack_from("<Q", data, phoff + i * size + 48)[0]
                  for i in range(count) if struct.unpack_from("<I", data, phoff + i * size)[0] == 1}
        if not aligns or min(aligns) < 16384:
            bad.append(f"{abi}/{name}: LOAD alignment {sorted(aligns)}")
if bad:
    sys.exit("refused:\n  " + "\n  ".join(bad))
PY

mkdir -p "$DEST"
# A fresh archive, moved into place: zip into an existing one would keep entries this build no longer has.
(cd "$WORK/aar" && zip -q -r -X "$WORK/out.aar" .)
mv "$WORK/out.aar" "$DEST/executorch-android-$VERSION.aar"
# Our coordinates, upstream's dependencies. The Gradle-metadata marker goes: this artifact
# publishes no .module file, and Gradle would otherwise look for one.
python3 - "$WORK/upstream.pom" "$DEST/executorch-android-$VERSION.pom" "$RELEASE" "$VERSION" <<'PY'
import sys
src, dst, release, version = sys.argv[1:5]
pom = open(src).read()
pom = "\n".join(l for l in pom.splitlines() if "published-with-gradle-metadata" not in l) + "\n"
for old, new in (("<groupId>org.pytorch</groupId>", "<groupId>org.experimentalmachines.executorch</groupId>"),
                 ("<artifactId>executorch-android-vulkan</artifactId>", "<artifactId>executorch-android</artifactId>"),
                 (f"<version>{release}</version>", f"<version>{version}</version>")):
    assert old in pom, old
    pom = pom.replace(old, new, 1)
open(dst, "w").write(pom)
PY
grep -q "<version>$VERSION</version>" "$DEST/executorch-android-$VERSION.pom"
(cd "$DEST" && shasum -a 256 "executorch-android-$VERSION.aar")
