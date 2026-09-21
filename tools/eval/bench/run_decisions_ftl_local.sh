#!/bin/sh
# The decision suite on a Test Lab phone, every file sent from this machine (no bucket, so
# it also runs in a project with no billing; five physical runs a day there).
#
#   PROJECT=<gcp project> MODELS_DIR=<dir with the files> MODELS=a.pte,b.gguf ARMS=driven-search \
#     DECISIONS=<question file> tools/eval/bench/run_decisions_ftl_local.sh <prefix> [device] [version]
#
# A .pte needs <stem>.tokenizer.json beside it in MODELS_DIR. Test Lab ends a run at 45
# minutes: two 1.2B models on one arm fit on a Snapdragon 8 Elite, three do not.
set -eu
PREFIX=${1:?results prefix}; DEV=${2:-pa3q}; VER=${3:-36}
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../../.." && pwd); OUT="$HERE/../results/decisions"
PKG=io.github.alpharomercoma.openweights.debug
EVAL=/data/local/tmp/openweights/eval
APP="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
# MODELS_DIR may be a gs:// prefix in the project's own Test Lab bucket
# (tools/eval/streaming/pod_to_bucket.sh), which spares this machine's uplink.
D=${MODELS_DIR:?}; MODELS=${MODELS:?}
FILES="$EVAL/prompt_dump.json=$ROOT/eval/prompt_dump.json,$EVAL/decisions.json=${DECISIONS:-$HERE/decisions.json}"
for m in $(echo "$MODELS" | tr , ' '); do
  FILES="$FILES,$EVAL/$m=$D/$m"
  case "$m" in *.pte) t="${m%.pte}.tokenizer.json"; FILES="$FILES,$EVAL/$t=$D/$t" ;; esac
done
ENV_VARS="^:^models=$MODELS${ARMS:+:arms=$ARMS}${FROM:+:from=$FROM}${ROWS:+:rows=$ROWS}"
QSHA=$(shasum -a 256 "${DECISIONS:-$HERE/decisions.json}" | cut -c1-8)
TAG="$PREFIX$(echo "$MODELS" | tr , + | cut -c1-60)-${ARMS:-all}-q$QSHA"
LOG="$OUT/$TAG.ftl.log"; mkdir -p "$OUT"
STATUS=0
gcloud firebase test android run --quiet ${PROJECT:+--project "$PROJECT"} ${ACCOUNT:+--account "$ACCOUNT"} --type instrumentation \
  --app "$APP" --test "$APK" \
  --device "model=$DEV,version=$VER,locale=en,orientation=portrait" \
  --test-targets "class io.github.alpharomercoma.openweights.ui.chat.DecisionSuiteOnDeviceTest#decisions" \
  --timeout 45m --environment-variables "$ENV_VARS" \
  --other-files "$FILES" \
  --directories-to-pull "/sdcard/Android/data/$PKG/files/eval-results" \
  --results-history-name "openweights-decisions-local" > "$LOG" 2>&1 || STATUS=$?
grep -E "Test is|Test time|OUTCOME|Passed|Failed|rror" "$LOG" | tail -4 || true
RESULTS=$(grep -o 'storage/browser/[^]]*' "$LOG" | head -1 | sed 's|storage/browser/|gs://|')
[ -n "$RESULTS" ] || { echo "No result location in $LOG" >&2; exit 1; }
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
gcloud storage cp ${PROJECT:+--project "$PROJECT"} ${ACCOUNT:+--account "$ACCOUNT"} -r "${RESULTS}*/artifacts/sdcard/Android/data/$PKG/files/eval-results/*.jsonl" "$TMP/" >/dev/null
# The questions' identity goes in front, where the graders read a phone's name, not behind
# the arm, which they parse.
for f in $(find "$TMP" -name '*.jsonl'); do
  n="${PREFIX}q$QSHA-$(basename "$f")"; cp "$f" "$OUT/$n"; echo "   $n"
done
# Preserve partial reports from a failed run, but never report that run as successful.
exit "$STATUS"
