#!/bin/bash
# llama.cpp's cold prefill against prompt length on a phone over USB:
#   S=<adb serial> poco_llama_prefill.sh <model.gguf name without extension>
set -u
S=${S:?set S to the adb serial}
G=$1
OUT=$(cd "$(dirname "$0")" && pwd)/poco; mkdir -p "$OUT"
T=io.github.alpharomercoma.openweights.core.engine.test
adb -s $S shell 'svc power stayon usb; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard' || exit 1
adb -s $S logcat -c || exit 1
echo "== prefill $G start $(date -u +%FT%TZ)"
adb -s $S shell "am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.eval.WarmPrefixEval#coldPrefillByLength -e coolLimitS ${COOL_LIMIT_S:-60} -e gguf /data/local/tmp/openweights/eval/$G.gguf $T/androidx.test.runner.AndroidJUnitRunner" > "$OUT/prefill-$G.instrument.txt" 2>&1 || exit 1
adb -s $S logcat -d -s WarmPrefixEval:I OpenWeights:I > "$OUT/prefill-$G.log" || exit 1
adb -s $S shell "cat /sdcard/Android/data/$T/files/eval-results/$G.prefill.jsonl 2>/dev/null" > "$OUT/prefill-$G.prefill.jsonl"
echo "== prefill $G end $(date -u +%FT%TZ) $(grep -c . "$OUT/prefill-$G.prefill.jsonl") rows; $(grep -E 'OK \(|FAILURES|Error in' "$OUT/prefill-$G.instrument.txt" | head -2)"
adb -s $S shell 'svc power stayon false' || true
