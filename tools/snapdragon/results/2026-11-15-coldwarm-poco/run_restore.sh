#!/bin/bash
# llama.cpp's fresh chat read cold and over a restored snapshot, on the POCO over USB:
# WarmPrefixEval#aFreshChatReadColdAndRestored, screen on and unlocked.
set -u
S=${S:?set S to the adb serial}
OUT=$(cd "$(dirname "$0")" && pwd)
T=io.github.alpharomercoma.openweights.core.engine.test
G=${G:-LFM2.5-1.2B-Instruct-QAD-Q4_0}
adb -s $S shell 'svc power stayon usb; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard' || exit 1
adb -s $S logcat -c
echo "== $G start $(date -u +%FT%TZ)"
adb -s $S shell "am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.eval.WarmPrefixEval#aFreshChatReadColdAndRestored -e pairs ${PAIRS:-6} -e coolLimitS ${COOL_LIMIT_S:-240} -e gguf /data/local/tmp/openweights/eval/$G.gguf $T/androidx.test.runner.AndroidJUnitRunner" > "$OUT/$G.restore.instrument.txt" 2>&1
adb -s $S logcat -d -s WarmPrefixEval:I > "$OUT/$G.restore.log" || exit 1
adb -s $S pull /sdcard/Android/data/$T/files/eval-results/$G.restore.jsonl "$OUT/" >/dev/null 2>&1
echo "== $G end $(date -u +%FT%TZ) $(grep -c . "$OUT/$G.restore.jsonl" 2>/dev/null) rows; $(grep -E 'OK \(|FAILURES' "$OUT/$G.restore.instrument.txt" | head -1)"
adb -s $S shell 'svc power stayon false' || true
