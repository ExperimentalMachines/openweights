#!/bin/sh
# The retrieve-or-answer suite on the phone over adb, through the app's own loop.
# Results land in tools/eval/results/decisions/ as one JSON line per row.
#
#   tools/eval/bench/run_decisions.sh [adb-serial]
#     MODELS=a.pte,b.gguf   ARMS=driven-search,driven-full   SETS=retrievalqa,popqa   FROM=40 ROWS=40
#     PREFIX=qdc-           names another phone's files
#     ECHO=1                runs the instruction-echo probe instead of the decisions
#     DECISIONS=<file>      another question file (a held-out seed); it is pushed as decisions.json
#     FRESH=1               archives this phone's earlier results locally before clearing them
#
# The instrumentation runs attached (-w) from a shell this script keeps open: started
# detached with nohup, the am client died with the adb session before the test began
# (measured 2026-09-10). Once running, the test outlives an adb drop: the same day the
# wireless port went away mid-arm and the phone finished two arms on its own. Rows are
# written as they land, so a rerun resumes where the file stops.
#
# An unplugged phone with its screen off suspends its CPU, timeouts included: a search
# froze for two hours that way. The screen is woken at the start and kept awake for the
# length of the run; on the charger neither is needed.
set -eu
# sh reads a script as it runs it. This one runs for hours, and an edit to the file while a
# battery ran (2026-09-18) made the running copy read new bytes at its old offset, die
# with a syntax error at the end and skip its result pull; the next battery then deleted
# the phone's unpulled rows. So the script runs from a copy of itself.
if [ -z "${RUN_DECISIONS_COPY:-}" ]; then
  COPY=$(mktemp "${TMPDIR:-/tmp}/run_decisions.XXXXXX")
  cp "$0" "$COPY"
  RUN_DECISIONS_COPY=1 RUN_DECISIONS_HERE=$(cd "$(dirname "$0")" && pwd) exec sh "$COPY" "$@"
fi
SERIAL=${1:-}
ADB="adb ${SERIAL:+-s $SERIAL}"
PKG=io.github.alpharomercoma.openweights.debug
TEST=$PKG.test
RUNNER=androidx.test.runner.AndroidJUnitRunner
EVAL=/data/local/tmp/openweights/eval
# The copy lives in a temp directory, so the original's directory is passed through.
HERE=${RUN_DECISIONS_HERE:-$(cd "$(dirname "$0")" && pwd)}
ROOT=$(cd "$HERE/../../.." && pwd)
OUT="$HERE/../results/decisions"
mkdir -p "$OUT"
APP="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
METHOD=${ECHO:+instructionEcho}
METHOD=${METHOD:-decisions}

# A dozing phone throttles instrumentation two to five times; awake and unlocked for the run.
$ADB shell "settings put system screen_off_timeout 2147483647; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard" >/dev/null 2>&1 || true
# And kept that way: the ROM relocks after a wake, and an unplugged phone then suspends.
(
  while kill -0 $$ 2>/dev/null; do
    $ADB shell dumpsys power 2>/dev/null | grep -q "mWakefulness=Awake" ||
      $ADB shell "input keyevent KEYCODE_WAKEUP; sleep 1; wm dismiss-keyguard" >/dev/null 2>&1
    sleep 30
  done
) &
AWAKE=$!
trap 'rm -f "$0"; kill $AWAKE 2>/dev/null || true' EXIT   # $0 is the temp copy by now
# Result filenames carry the model and arm, not the questions. Preserve the phone's
# previous captures before resetting its resumable output for another question set.
QUESTIONS=${DECISIONS:-$HERE/decisions.json}
QUESTIONS_SHA=$(shasum -a 256 "$QUESTIONS" | cut -d' ' -f1)
RESULTS=/sdcard/Android/data/$PKG/files/eval-results
MARKER=$RESULTS/decisions.questions.sha256
$ADB shell "mkdir -p $EVAL $RESULTS"
OLD_SHA=$($ADB shell "cat $MARKER 2>/dev/null" | tr -d '\r\n' || true)
$ADB push "$QUESTIONS" "$EVAL/decisions.json" >/dev/null
if [ "$METHOD" = decisions ]; then
  if [ "${FRESH:-}" = 1 ] || [ "$OLD_SHA" != "$QUESTIONS_SHA" ]; then
    mkdir -p "$OUT/archive"
    ARCHIVE=$(mktemp -d "$OUT/archive/${PREFIX:-}previous.XXXXXX")
    # A failed pull stops the script before anything on the phone is deleted.
    $ADB pull "$RESULTS" "$ARCHIVE/" >/dev/null
    $ADB shell "rm -f $RESULTS/decisions-*.jsonl"
  fi
  $ADB shell "echo $QUESTIONS_SHA > $MARKER"
fi
if [ -n "${INSTALL:-}" ]; then
  $ADB push "$APP" /data/local/tmp/app.apk >/dev/null && $ADB shell pm install -r -t --user 0 /data/local/tmp/app.apk
  $ADB push "$APK" /data/local/tmp/owtest.apk >/dev/null && $ADB shell pm install -r -t --user 0 /data/local/tmp/owtest.apk
fi
$ADB shell "logcat -G 8M" >/dev/null 2>&1 || true
echo "== $METHOD $(date +%H:%M)"
$ADB shell "am instrument -w -r ${MODELS:+-e models $MODELS} ${ARMS:+-e arms $ARMS} ${SETS:+-e sets $SETS} ${FROM:+-e from $FROM} ${ROWS:+-e rows $ROWS} -e class io.github.alpharomercoma.openweights.ui.chat.DecisionSuiteOnDeviceTest#$METHOD $TEST/$RUNNER" \
  | grep -E "INSTRUMENTATION_(RESULT|STATUS: stack|CODE)|Time:" || true
for f in $($ADB shell "ls /sdcard/Android/data/$PKG/files/eval-results/decisions-*.jsonl /sdcard/Android/data/$PKG/files/eval-results/echo-*.jsonl" 2>/dev/null); do
  $ADB pull "$f" "$OUT/${PREFIX:-}$(basename "$f")" >/dev/null && echo "   ${PREFIX:-}$(basename "$f")"
done
echo "== done $(date +%H:%M)"
