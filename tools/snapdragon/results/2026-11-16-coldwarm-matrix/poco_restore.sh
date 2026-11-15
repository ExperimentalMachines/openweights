#!/bin/bash
# llama.cpp's fresh chat read cold and over a restored head, one run per GGUF, on a phone over USB:
#   S=<adb serial> poco_restore.sh <model.gguf name without extension> [...]
# The files are read from /data/local/tmp/openweights/eval. Output lands in poco/restore-<model>.*.
set -u
S=${S:?set S to the adb serial}
OUT=$(cd "$(dirname "$0")" && pwd)/poco; mkdir -p "$OUT"
T=io.github.alpharomercoma.openweights.core.engine.test
adb -s $S shell "cmd deviceidle whitelist +$T; am set-standby-bucket $T active; cmd appops set $T RUN_ANY_IN_BACKGROUND allow" >/dev/null || exit 1
for G in "$@"; do
  adb -s $S shell 'svc power stayon usb; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard' || exit 1
  adb -s $S logcat -c || exit 1
  echo "== $G start $(date -u +%FT%TZ)"
  adb -s $S shell "am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.eval.WarmPrefixEval#aFreshChatReadColdAndRestored -e pairs ${PAIRS:-6} -e coolLimitS ${COOL_LIMIT_S:-60} -e gguf /data/local/tmp/openweights/eval/$G.gguf $T/androidx.test.runner.AndroidJUnitRunner" > "$OUT/restore-$G.instrument.txt" 2>&1 || exit 1
  adb -s $S logcat -d -s WarmPrefixEval:I OpenWeights:I > "$OUT/restore-$G.log" || exit 1
  adb -s $S shell "cat /sdcard/Android/data/$T/files/eval-results/$G.restore.jsonl 2>/dev/null" > "$OUT/restore-$G.restore.jsonl"
  echo "== $G end $(date -u +%FT%TZ) $(grep -c . "$OUT/restore-$G.restore.jsonl") rows; $(grep -E 'OK \(|FAILURES|Error in' "$OUT/restore-$G.instrument.txt" | head -2)"
done
adb -s $S shell 'svc power stayon false' || true
