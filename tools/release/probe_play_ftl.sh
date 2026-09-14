#!/bin/sh
# Installs the production app from Google Play on Test Lab phones and says "hi" to a model.
#
#   tools/release/probe_play_ftl.sh [model file in the eval bucket] [device ...]
#     tools/release/probe_play_ftl.sh LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf mustang:36 gts10pwifi:36
#     PROMPT="Explain how a bicycle gear works" (no commas or colons) sends that instead of hi
#
# The question a local bundle cannot answer is what Play actually serves. PlayProductionProbe
# installs through the Play Store app on a signed-in lab phone, logs the version code, copies
# the installed APKs out, and fails if "hi" kills the process. This script prints what each
# phone got: the version code and the build ID of libopenweights_llama.so, which is how a
# crash report's native frame is matched to a build (every release is versionName 2.0.0).
set -eu
MODEL=${1:-LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf}
shift || true
DEVICES=${*:-mustang:36}
BUCKET=${BUCKET:-gs://openweights-eval-models}
PKG=io.github.alpharomercoma.openweights
EVAL=/data/local/tmp/openweights/eval
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
APP="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
OUT=${OUT:-$ROOT/build/play-probe/$(date +%Y%m%d-%H%M%S)}
mkdir -p "$OUT"

DEVICE_FLAGS=""
for d in $DEVICES; do
  DEVICE_FLAGS="$DEVICE_FLAGS --device model=${d%%:*},version=${d##*:},locale=en,orientation=portrait"
done

# The debug app is only the host the instrumentation runs in; the package under test is
# the one Play installs.
# shellcheck disable=SC2086
gcloud firebase test android run --quiet --type instrumentation \
  --app "$APP" --test "$APK" $DEVICE_FLAGS \
  --test-targets "class io.github.alpharomercoma.openweights.release.PlayProductionProbe" \
  --timeout 20m --environment-variables "^:^model=$EVAL/$MODEL${PROMPT:+:prompt=$PROMPT}" \
  --other-files "$EVAL/$MODEL=$BUCKET/$MODEL" \
  --results-bucket "$BUCKET" --results-dir "play-probe/$(basename "$OUT")" \
  --directories-to-pull /sdcard/probe \
  --results-history-name openweights-play-probe 2>&1 | tee "$OUT/ftl.log" \
  | grep -E "Test is|OUTCOME|Passed|Failed|error|ERROR" || true

gcloud storage cp -r "$BUCKET/play-probe/$(basename "$OUT")/*-portrait" "$OUT/" >/dev/null 2>&1 || true
for dir in "$OUT"/*/; do
  [ -f "$dir/logcat" ] || continue
  echo "== $(basename "$dir")"
  grep -o "OpenWeightsPlayProbe.*" "$dir/logcat" | sed 's/^OpenWeightsPlayProbe: //' | grep -v "^APK"
  for split in $(find "$dir" -name 'split_config.arm64_v8a.apk'); do
    unzip -p "$split" lib/arm64-v8a/libopenweights_llama.so > "$OUT/lib.so"
    echo "libopenweights_llama.so $(file "$OUT/lib.so" | grep -o 'BuildID\[sha1\]=[0-9a-f]*')"
  done
done
