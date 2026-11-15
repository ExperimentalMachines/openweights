#!/usr/bin/env bash
# One cold-versus-warm run on a Test Lab phone, the test APK and models taken from the bucket.
#   ftl.sh <device model id> <tag> <test target> <instrumentation args, ':'-separated> <other files>
# Rows land in ftl-<device>/; only the test's and the runtimes' log lines are kept, never the logcat.
# ACCOUNT, PROJECT and BUCKET (a gs:// folder holding engine-test-coldwarm.apk and the models,
# in the project's Test Lab bucket) come from the environment.
set -uo pipefail
DEV=$1; TAG=$2; TARGET=$3; ARGS=$4; FILES=$5
HERE=$(cd "$(dirname "$0")" && pwd)
B=${BUCKET:?}
ACC=${ACCOUNT:?}
PKG=io.github.alpharomercoma.openweights.core.engine.test
OUT="$HERE/ftl-$DEV"; mkdir -p "$OUT"
# gcloud's own output names the project and bucket, so it goes to RAW, outside the repo.
LOG="${RAW:?}/$DEV-$TAG.gcloud.txt"
cd /tmp
gcloud firebase test android run --quiet --account "$ACC" --project "${PROJECT:?}" \
  --type instrumentation --app "$B/engine-test-coldwarm.apk" --test "$B/engine-test-coldwarm.apk" \
  --device "model=$DEV,version=36,locale=en,orientation=portrait" \
  --test-targets "$TARGET" --timeout 45m \
  --environment-variables "^:^coolBelowC=45:coolLimitS=60:$ARGS" \
  --other-files "$FILES" \
  --directories-to-pull "/sdcard/Android/data/$PKG/files/eval-results" \
  --results-history-name "openweights-coldwarm" > "$LOG" 2>&1
echo "$DEV $TAG exit $?"; grep -E "Passed|Failed|Inconclusive|OUTCOME|rror" "$LOG" | tail -4
RES=$(grep -o 'storage/browser/[^]]*' "$LOG" | head -1 | sed 's|storage/browser/|gs://|')
[ -n "$RES" ] || exit 1
T=$(mktemp -d)
gcloud storage cp --account "$ACC" "${RES}*/artifacts/sdcard/Android/data/$PKG/files/eval-results/*.jsonl" "$OUT/" >/dev/null 2>&1
gcloud storage cp --account "$ACC" "${RES}*/logcat" "$T/logcat" >/dev/null 2>&1
grep -E ' (ExecuTorchOnDevice|ExecuTorch|WarmPrefixEval|OpenWeights) *:' "$T/logcat" > "$OUT/$TAG.log" 2>/dev/null
rm -rf "$T"; echo "$DEV $TAG: $(ls "$OUT"/*.jsonl 2>/dev/null | wc -l | tr -d ' ') jsonl files"
