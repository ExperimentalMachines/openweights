#!/bin/bash
# llama.cpp's cold prefill against prompt length on the POCO over USB or wireless debugging
# (WIFI=<ip:port>); see poco_lib.sh:
#   [S=<usb serial>] [WIFI=<ip:port>] poco_llama_prefill.sh <model.gguf name without extension>
set -u
. "$(dirname "$0")/poco_lib.sh"
G=$1
OUT=$HERE/${OUT_DIR:-poco}; mkdir -p "$OUT"
awake || exit 1
echo "== prefill $G start $(date -u +%FT%TZ) over $S"
start_log "prefill-$G" "WarmPrefixEval:I OpenWeights:I" || exit 1
detached "prefill-$G" "am instrument -w -r -e class $PKG.eval.WarmPrefixEval#coldPrefillByLength -e coolLimitS ${COOL_LIMIT_S:-60} -e gguf /data/local/tmp/openweights/eval/$G.gguf $RUNNER" "$OUT/prefill-$G.instrument.txt" || exit 1
stop_log "prefill-$G" "$OUT/prefill-$G.log" || exit 1
fetch "$R/$G.prefill.jsonl" "$OUT/prefill-$G.prefill.jsonl" jsonl || exit 1
echo "== prefill $G end $(date -u +%FT%TZ) $(grep -c . "$OUT/prefill-$G.prefill.jsonl" 2>/dev/null || echo 0) rows; outcome=$(outcome "$OUT/prefill-$G.instrument.txt")"
on 'svc power stayon false' || true
