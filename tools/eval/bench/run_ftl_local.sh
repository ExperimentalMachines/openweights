#!/bin/sh
# One model's public benchmarks on a Test Lab phone, the files sent from this machine.
#
#   PROJECT=<gcp project> tools/eval/bench/run_ftl_local.sh <file.pte> <tokenizer.json> <name on the phone, no extension> [prefix] [device] [version]
#
# run_matrix_ftl.sh takes its models from a bucket, which needs a billed project and an
# account that can write to it. This one needs neither: gcloud uploads the files to Test
# Lab's own results bucket, so it also runs in a project with no billing (five physical
# runs a day there). The name decides the chat template (PromptTemplate.forModel), so it
# must carry the family: Qwen3-..., Llama-3.2-..., LFM2.5-....
set -eu
PTE=${1:?}; TOK=${2:?}; NAME=${3:?}; PREFIX=${4:-ftl-}; DEV=${5:-pa3q}; VER=${6:-36}
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../../.." && pwd); OUT="$HERE/../results"
PKG=io.github.alpharomercoma.openweights.core.engine.test
EVAL=/data/local/tmp/openweights/eval
# APK=<file> runs another build of the test APK (the Vulkan one); the .pte and tokenizer may
# be gs:// objects in the project's own Test Lab bucket (tools/eval/streaming/pod_to_bucket.sh).
APK=${APK:-"$ROOT/core/engine/build/outputs/apk/androidTest/debug/engine-debug-androidTest.apk"}
LOG="$OUT/$PREFIX$NAME.ftl.log"
mkdir -p "$OUT"
STATUS=0
gcloud firebase test android run --quiet ${PROJECT:+--project "$PROJECT"} ${ACCOUNT:+--account "$ACCOUNT"} --type instrumentation \
  --app "$APK" --test "$APK" \
  --device "model=$DEV,version=$VER,locale=en,orientation=portrait" \
  --test-targets "class io.github.alpharomercoma.openweights.core.engine.eval.ExecuTorchBenchmarkEval" \
  --timeout 45m --environment-variables "^:^budget=40:model=$NAME:context=0${BENCH_SETS:+:sets=$BENCH_SETS}" \
  --other-files "$EVAL/$NAME.pte=$PTE,$EVAL/$NAME.tokenizer.json=$TOK,$EVAL/benchmarks.json=$HERE/benchmarks.json" \
  --directories-to-pull "/sdcard/Android/data/$PKG/files/eval-results" \
  --results-history-name "openweights-quantlab" > "$LOG" 2>&1 || STATUS=$?
grep -E "Test is|OUTCOME|Passed|Failed|rror" "$LOG" | tail -5 || true
RESULTS=$(grep -o 'storage/browser/[^]]*' "$LOG" | head -1 | sed 's|storage/browser/|gs://|')
[ -n "$RESULTS" ] || { echo "No result location in $LOG" >&2; exit 1; }
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
gcloud storage cp ${PROJECT:+--project "$PROJECT"} ${ACCOUNT:+--account "$ACCOUNT"} -r "${RESULTS}*/artifacts/sdcard/Android/data/$PKG/files/eval-results/*.json" "$TMP/" >/dev/null
for f in $(find "$TMP" -name '*.bench*.json'); do cp "$f" "$OUT/$PREFIX$(basename "$f")"; echo "   $PREFIX$(basename "$f")"; done
# Preserve partial reports from a failed run, but never report that run as successful.
exit "$STATUS"
