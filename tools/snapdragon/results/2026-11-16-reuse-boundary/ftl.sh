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
  --results-history-name "openweights-reuse-boundary" > "$LOG" 2>&1
RC=$?
echo "$DEV $TAG exit $RC"; grep -E "Passed|Failed|Inconclusive|OUTCOME|rror" "$LOG" | tail -4
# Rows from an earlier run under the same tag must not survive into this one's count.
rm -f "$OUT/$TAG".*.jsonl "$OUT/$TAG.log"
RES=$(grep -o 'storage/browser/[^]]*' "$LOG" | head -1 | sed 's|storage/browser/|gs://|')
[ -n "$RES" ] || exit 1
T=$(mktemp -d)
S=$(mktemp -d)
# Both downloads are checked, and nothing is published until the rows and the filtered log exist.
gcloud storage cp --account "$ACC" "${RES}*/artifacts/sdcard/Android/data/$PKG/files/eval-results/*.jsonl" "$S/" >/dev/null 2>&1 || { echo "$DEV $TAG: rows not downloaded"; RC=1; }
gcloud storage cp --account "$ACC" "${RES}*/logcat" "$T/logcat" >/dev/null 2>&1 || { echo "$DEV $TAG: logcat not downloaded"; RC=1; }
grep -E ' (ExecuTorchOnDevice|ExecuTorch|WarmPrefixEval|OpenWeights|ReuseBoundary) *:' "$T/logcat" > "$T/filtered.log" 2>/dev/null
[ -s "$T/filtered.log" ] || { echo "$DEV $TAG: no log lines"; RC=1; }
if ls "$S"/*.jsonl >/dev/null 2>&1 && [ -s "$T/filtered.log" ]; then
  for f in "$S"/*.jsonl; do cp "$f" "$OUT/$TAG.$(basename "$f" | sed -E 's/.*\.(coldwarm|restore|prefill|boundary)\.jsonl$/\1.jsonl/')"; done
  cp "$T/filtered.log" "$OUT/$TAG.log"
fi
cp "$T/logcat" "$RAW/$DEV-$TAG.logcat" 2>/dev/null
rm -rf "$S" "$T"
N=$(cat "$OUT/$TAG".*.jsonl 2>/dev/null | grep -c .)
echo "$DEV $TAG: $N rows"
# The test's own outcome decides, after the diagnostics are collected; no rows is a failure too.
[ "$RC" -eq 0 ] && [ "$N" -gt 0 ] || exit 1
