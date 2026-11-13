#!/usr/bin/env bash
# llama-bench from two llama.cpp commits, built with the app's CPU flags, run on one Android device
# in alternating pairs, so that heat and other load fall on both sides of every pair.
#
#   tools/snapdragon/llama_bench_ab.sh build <old commit> <new commit>
#   tools/snapdragon/llama_bench_ab.sh run <adb serial> <model.gguf on the Mac> [pairs, default 6]
#
# Written for the v0.8.0 to v1.0.0 question (docs/research/llama-cpp-v100-executorch-160.md): on
# the Mac, v1.0.0 decoded 6% to 19% slower with 2,048 to 4,096 tokens in context, because the CPU
# now widens the F16 cache to F32 in attention. Whether a phone loses the same is what this answers.
# The run is decode only, 64 tokens at depths 0, 2,048 and 4,096, flash attention on as the app
# resolves it, 4 threads as the app's engine. Wake, unlock and cool the phone first; on the POCO
# a screen-off run is throttled by the ROM (AGENTS.md).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SRC="$ROOT/core/engine/src/main/cpp/llama.cpp"
WORK="${LLAMA_BENCH_AB_DIR:-$HOME/ow-models/llama-bench-ab}"
NDK="${ANDROID_NDK_HOME:-$(ls -d "$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties")"/ndk/* | sort | tail -1)}"

build() {
  for commit in "$1" "$2"; do
    tree="$WORK/src-$commit" out="$WORK/bin-$commit"
    [ -d "$tree" ] || git -C "$SRC" worktree add --detach "$tree" "$commit" >/dev/null
    # The engine's own flags (core/engine/build.gradle.kts), without OpenCL: this is the CPU path.
    cmake -S "$tree" -B "$tree/build-android" \
      -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_shared \
      -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_FLAGS="-O3" -DBUILD_SHARED_LIBS=ON \
      -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF \
      -DGGML_LLAMAFILE=OFF -DGGML_CPU_KLEIDIAI=ON -DGGML_OPENCL=OFF -DLLAMA_CURL=OFF \
      -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_TOOLS=ON \
      -DLLAMA_BUILD_SERVER=OFF >/dev/null
    cmake --build "$tree/build-android" --target llama-bench -j 8 >/dev/null
    rm -rf "$out" && mkdir -p "$out"
    cp "$tree/build-android/bin/llama-bench" "$tree"/build-android/bin/*.so "$out/"
    cp "$NDK/toolchains/llvm/prebuilt/"*/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so "$out/"
    echo "built $commit: $(ls "$out" | wc -l | tr -d ' ') files in $out"
  done
  echo "$1 $2" > "$WORK/pair"
}

run() {
  serial="$1" model="$2" pairs="${3:-6}"
  read -r old new < "$WORK/pair"
  adb="adb -s $serial" dir=/data/local/tmp/lbab
  $adb shell "mkdir -p $dir/$old $dir/$new"
  $adb push "$WORK/bin-$old/." "$dir/$old/" >/dev/null
  $adb push "$WORK/bin-$new/." "$dir/$new/" >/dev/null
  $adb push "$model" "$dir/model.gguf" >/dev/null
  out="$WORK/run-$(date +%Y%m%d-%H%M%S)-$serial.jsonl"
  for k in $(seq 1 "$pairs"); do
    if [ $((k % 2)) -eq 1 ]; then order="$old $new"; else order="$new $old"; fi
    for v in $order; do
      $adb shell "cd $dir/$v && LD_LIBRARY_PATH=. ./llama-bench -m $dir/model.gguf -t 4 -p 0 -n 64 \
        -d 0,2048,4096 -fa 1 -r 1 -o jsonl 2>/dev/null" |
        sed "s/^{/{\"version\":\"$v\",\"pair\":$k,\"model_name\":\"$(basename "$model" .gguf)\",/" >> "$out"
    done
  done
  python3 - "$out" "$old" "$new" <<'PY'
import json, statistics, sys
from collections import defaultdict
path, old, new = sys.argv[1:4]
by = defaultdict(dict)
for line in open(path):
    if line.startswith("{"):
        r = json.loads(line)
        by[(r["n_depth"], r["pair"])][r["version"]] = r["avg_ts"]
print(f"decode tok/s, {new} over {old}, per pair; {path}")
for depth in sorted({d for d, _ in by}):
    pairs = [v for (d, _), v in by.items() if d == depth and old in v and new in v]
    ratios = sorted(v[new] / v[old] for v in pairs)
    print(f"depth {depth:>5}: {old} {statistics.median(v[old] for v in pairs):6.1f}  {new} "
          f"{statistics.median(v[new] for v in pairs):6.1f}  ratio {statistics.median(ratios):.3f} "
          f"({ratios[0]:.3f} to {ratios[-1]:.3f}), {new} slower in {sum(r < 1 for r in ratios)} of {len(ratios)}")
PY
}

case "${1:-}" in
  build) build "$2" "$3" ;;
  run) run "$2" "$3" "${4:-6}" ;;
  *) sed -n 2,13p "$0"; exit 2 ;;
esac
