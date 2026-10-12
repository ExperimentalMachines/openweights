#!/bin/sh
# MemoryPressureBenchmark on the phone, once per level of outside memory pressure.
#
#   tools/memory/run_pressure.sh [adb-serial]
#     MODELS=a.gguf,b.pte   files in /data/local/tmp/openweights (default model.gguf)
#     LEVELS="0 4096 8192"  megabytes the balloon holds for each run (default 0: peak only)
#     CONTEXT, FILL, TURNS, GAP, ANSWER   passed to the test; see its defaults
#     OUT=dir               where the reports land (default tools/memory/results/<date>)
#
# The balloon (balloon.c, built here with the NDK) runs under the shell, fills its pages with
# random bytes so zram cannot compress them away, and touches every page every two seconds.
# Each level waits for it to be fully resident before the test starts, and cools the phone
# for COOL seconds after, because heat slows decode as surely as swap does and the two must
# not be confused. The instrumentation is started detached, as run_local.sh does, because a
# wireless-debugging drop takes an attached run with it.
set -eu
SERIAL=${1:-}
ADB="adb ${SERIAL:+-s $SERIAL}"
PKG=io.github.alpharomercoma.openweights.core.engine.test
RUNNER=androidx.test.runner.AndroidJUnitRunner
CLASS=io.github.alpharomercoma.openweights.core.engine.MemoryPressureBenchmark
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
APK="$ROOT/core/engine/build/outputs/apk/androidTest/debug/engine-debug-androidTest.apk"
OUT=${OUT:-$HERE/results/$(date +%Y-%m-%d)}
LEVELS=${LEVELS:-0}
COOL=${COOL:-90}
mkdir -p "$OUT"

SDK=$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties")
CC=$(ls "$SDK"/ndk/*/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android31-clang | tail -1)
BUILD=$(mktemp -d)
"$CC" -O2 -o "$BUILD/balloon" "$HERE/balloon.c"

# Awake, unlocked and exempt from the ROM's freezer for the whole run: a dozing phone is
# throttled two to five times, and a frozen one simply stops.
$ADB shell "settings put system screen_off_timeout 2147483647; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard" >/dev/null 2>&1 || true
$ADB shell "cmd deviceidle whitelist +$PKG; am set-standby-bucket $PKG active; cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow" >/dev/null 2>&1 || true
$ADB push "$BUILD/balloon" /data/local/tmp/balloon >/dev/null
$ADB shell chmod 755 /data/local/tmp/balloon
$ADB push "$APK" /data/local/tmp/owtest.apk >/dev/null
$ADB shell pm install -r -t --user 0 /data/local/tmp/owtest.apk >/dev/null
$ADB shell "touch /data/local/tmp/mem-start"

for level in $LEVELS; do
  label="b$level"
  $ADB shell "pkill -f /data/local/tmp/balloon; rm -f /data/local/tmp/balloon.log" || true
  if [ "$level" -gt 0 ]; then
    $ADB shell "nohup /data/local/tmp/balloon $level 2 > /data/local/tmp/balloon.log 2>&1 &"
    n=0; until $ADB shell "grep -q ready /data/local/tmp/balloon.log" 2>/dev/null || [ $n -ge 60 ]; do sleep 5; n=$((n + 1)); done
  fi
  echo "== $label $(date +%H:%M) $($ADB shell "grep -E 'MemAvailable|SwapFree' /proc/meminfo | tr -s ' ' | tr '\n' ' '")"
  $ADB logcat -c
  $ADB shell "nohup am instrument -r -e class $CLASS -e label $label ${MODELS:+-e models $MODELS} ${CONTEXT:+-e context $CONTEXT} ${FILL:+-e fill $FILL} ${TURNS:+-e turns $TURNS} ${GAP:+-e gap $GAP} ${ANSWER:+-e answer $ANSWER} $PKG/$RUNNER > /data/local/tmp/mem-$label.log 2>&1 &"
  n=0; until $ADB shell pidof $PKG >/dev/null 2>&1 || [ $n -ge 12 ]; do sleep 5; n=$((n + 1)); done
  while $ADB shell pidof $PKG >/dev/null 2>&1; do sleep 15; done
  $ADB logcat -d -s OpenWeightsMemory:* OpenWeights:I lowmemorykiller:* > "$OUT/$label.logcat.txt" 2>/dev/null || true
  $ADB shell "grep -E 'INSTRUMENTATION_(RESULT|CODE)|shortMsg' /data/local/tmp/mem-$label.log | head -3" || true
  $ADB shell "tail -2 /data/local/tmp/balloon.log 2>/dev/null; pidof balloon >/dev/null && echo balloon alive || echo balloon gone" | sed 's/^/   /' || true
  $ADB shell "pkill -f /data/local/tmp/balloon" || true
  sleep "$COOL"
done

TMP=$(mktemp -d)
for f in $($ADB shell "find /sdcard/Android/data/$PKG/files/eval-results -name '*.mem.json' -newer /data/local/tmp/mem-start"); do
  $ADB pull "$f" "$TMP/" >/dev/null && cp "$TMP/$(basename "$f")" "$OUT/" && echo "   $(basename "$f")"
done
echo "== done $(date +%H:%M)"
