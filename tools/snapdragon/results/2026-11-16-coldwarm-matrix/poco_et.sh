#!/bin/bash
# One ExecuTorch cold-versus-warm run on a phone over USB, screen on and unlocked:
#   S=<adb serial> poco_et.sh <tag> <stem> <context> <pairs> [extra "-e key value" args]
# <stem>.pte and <stem>.tokenizer.json are read from /data/local/tmp/openweights. Rows, the test's
# and the runtime's log lines and the instrumentation output land in poco/<tag>.*.
set -u
S=${S:?set S to the adb serial}
TAG=$1; STEM=$2; CTX=$3; PAIRS=$4; EXTRA=${5:-}
OUT=$(cd "$(dirname "$0")" && pwd)/poco; mkdir -p "$OUT"
T=io.github.alpharomercoma.openweights.core.engine.test
D=/data/local/tmp/openweights
adb -s $S shell 'svc power stayon usb; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard' || exit 1
adb -s $S shell "cmd deviceidle whitelist +$T; am set-standby-bucket $T active; cmd appops set $T RUN_ANY_IN_BACKGROUND allow" >/dev/null || exit 1
adb -s $S logcat -c || exit 1
echo "== $TAG start $(date -u +%FT%TZ)"
adb -s $S shell "am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ExecuTorchOnDeviceTest#${TEST:-theSecondTurnReadColdAndWarm} -e pairs $PAIRS -e coolLimitS ${COOL_LIMIT_S:-60} $EXTRA -e pte $D/$STEM.pte -e tokenizer $D/$STEM.tokenizer.json -e context $CTX $T/androidx.test.runner.AndroidJUnitRunner" > "$OUT/$TAG.instrument.txt" 2>&1 || exit 1
adb -s $S logcat -d -s ExecuTorchOnDevice:I ExecuTorch:I OpenWeights:I > "$OUT/$TAG.log" || exit 1
for kind in coldwarm prefill; do
  adb -s $S shell "cat /sdcard/Android/data/$T/files/eval-results/$STEM.$kind.jsonl 2>/dev/null" > "$OUT/$TAG.$kind.jsonl"
  [ -s "$OUT/$TAG.$kind.jsonl" ] || rm -f "$OUT/$TAG.$kind.jsonl"
done
adb -s $S shell "rm -f /sdcard/Android/data/$T/files/eval-results/$STEM.*.jsonl"
echo "== $TAG end $(date -u +%FT%TZ) $(cat "$OUT/$TAG".*.jsonl 2>/dev/null | grep -c .) rows; $(grep -E 'OK \(|FAILURES|Error in' "$OUT/$TAG.instrument.txt" | head -2)"
adb -s $S shell 'svc power stayon false' || true
