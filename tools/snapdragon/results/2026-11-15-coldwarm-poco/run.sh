#!/bin/bash
# The same second-turn prompt, warm and cold, on the POCO over USB:
# ExecuTorchOnDeviceTest#theSecondTurnReadColdAndWarm per model, screen on and unlocked.
set -u
S=${S:?set S to the adb serial}
OUT=$(cd "$(dirname "$0")" && pwd)
PAIRS=${PAIRS:-8}
T=io.github.alpharomercoma.openweights.core.engine.test
D=/data/local/tmp/openweights
adb -s $S shell 'svc power stayon usb; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard' || exit 1
adb -s $S shell "cmd deviceidle whitelist +$T; am set-standby-bucket $T active; cmd appops set $T RUN_ANY_IN_BACKGROUND allow" >/dev/null
run() { # stem context
  local stem=$1 ctx=$2
  adb -s $S shell 'input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard' || exit 1
  adb -s $S logcat -c
  echo "== $stem start $(date -u +%FT%TZ)"
  adb -s $S shell "am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ExecuTorchOnDeviceTest#theSecondTurnReadColdAndWarm -e pairs $PAIRS ${EXTRA:-} -e pte $D/$stem.pte -e tokenizer $D/$stem.tokenizer.json -e context $ctx $T/androidx.test.runner.AndroidJUnitRunner" > "$OUT/$stem.instrument.txt" 2>&1
  local rc=$?
  adb -s $S logcat -d -s ExecuTorchOnDevice:I ExecuTorch:I > "$OUT/$stem.log" || exit 1
  adb -s $S pull /sdcard/Android/data/$T/files/eval-results/$stem.coldwarm.jsonl "$OUT/" >/dev/null 2>&1 \
    || adb -s $S shell "run-as $T cat files/eval-results/$stem.coldwarm.jsonl" > "$OUT/$stem.coldwarm.jsonl"
  echo "== $stem end $(date -u +%FT%TZ) rc=$rc $(grep -c . "$OUT/$stem.coldwarm.jsonl" 2>/dev/null) rows; $(grep -E 'OK \(|FAILURES|Error' "$OUT/$stem.instrument.txt" | head -2)"
}
# EXTRA="-e temperature 0" builds a greedy engine and alternates the order (README.md).
[ "${ONLY:-}" != lfm ] && PAIRS=${QWEN_PAIRS:-10} run Qwen3-1.7B-INT8-INT4-ExecuTorch-XNNPACK 2048
[ "${ONLY:-}" != qwen ] && run LFM2.5-1.2B-Instruct-8da4w-gptq-4k 4096
adb -s $S shell 'svc power stayon false' || true
