#!/usr/bin/env bash
# One cold-versus-warm run on a Test Lab phone, the test APK and models taken from the bucket.
#   ftl.sh <device model id> <tag> <test target> <instrumentation args, ':'-separated> <other files>
# Rows land in ftl-<device>/; only the test's and the runtimes' log lines are kept, never the logcat.
# ACCOUNT, PROJECT, BUCKET (a gs:// folder holding the test APK and the models, in a bucket of
# the same project) and RAW (a folder outside the repository) come from the environment; APK names
# the test APK in the bucket, VERSION the Android API level (36).
set -uo pipefail
DEV=$1; TAG=$2; TARGET=$3; ARGS=$4; FILES=$5
HERE=$(cd "$(dirname "$0")" && pwd)
B=${BUCKET:?}
ACC=${ACCOUNT:?}
PKG=io.github.alpharomercoma.openweights.core.engine.test
OUT="$HERE/$DEV"; mkdir -p "$OUT"
# gcloud's own output names the project and bucket, so it goes to RAW, outside the repo.
LOG="${RAW:?}/$DEV-$TAG.gcloud.txt"
cd /tmp
gcloud firebase test android run --quiet --account "$ACC" --project "${PROJECT:?}" \
  --type instrumentation --app "$B/${APK:?}" --test "$B/${APK:?}" \
  --device "model=$DEV,version=${VERSION:-36},locale=en,orientation=portrait" \
  --test-targets "$TARGET" --timeout 45m \
  --environment-variables "^:^coolBelowC=${COOL_BELOW:-45}:coolLimitS=60:$ARGS" \
  --other-files "$FILES" \
  --directories-to-pull "/sdcard/Android/data/$PKG/files/eval-results" \
  --results-history-name "openweights-coldwarm" > "$LOG" 2>&1
echo "$DEV $TAG exit $?"; grep -E "Passed|Failed|Inconclusive|OUTCOME|rror" "$LOG" | tail -4
RES=$(grep -o 'storage/browser/[^]]*' "$LOG" | head -1 | sed 's|storage/browser/|gs://|')
[ -n "$RES" ] || exit 1
T=$(mktemp -d)
S=$(mktemp -d)
gcloud storage cp --account "$ACC" "${RES}*/artifacts/sdcard/Android/data/$PKG/files/eval-results/*.jsonl" "$S/" >/dev/null 2>&1
for f in "$S"/*.jsonl; do [ -f "$f" ] && cp "$f" "$OUT/$TAG.$(basename "$f" | sed -E 's/.*\.(coldwarm|restore|prefill)\.jsonl$/\1.jsonl/')"; done; rm -rf "$S"
gcloud storage cp --account "$ACC" "${RES}*/logcat" "$T/logcat" >/dev/null 2>&1
cp "$T/logcat" "$RAW/$DEV-$TAG.logcat" 2>/dev/null
grep -E ' (ExecuTorchOnDevice|ExecuTorch|WarmPrefixEval|OpenWeights) *:' "$T/logcat" > "$OUT/$TAG.log" 2>/dev/null
rm -rf "$T"; echo "$DEV $TAG: $(cat "$OUT/$TAG".*.jsonl 2>/dev/null | grep -c .) rows"
