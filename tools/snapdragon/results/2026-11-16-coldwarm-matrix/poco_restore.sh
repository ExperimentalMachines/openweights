#!/bin/bash
# llama.cpp's fresh chat read cold and over a restored head, one run per GGUF, on the POCO over
# USB or wireless debugging (WIFI=<ip:port>); see poco_lib.sh:
#   [S=<usb serial>] [WIFI=<ip:port>] poco_restore.sh <model.gguf name without extension> [...]
# The files are read from /data/local/tmp/openweights/eval. Output lands in poco/restore-<model>.*.
set -u
. "$(dirname "$0")/poco_lib.sh"
OUT=$HERE/${OUT_DIR:-poco}; mkdir -p "$OUT"
for G in "$@"; do
  awake || exit 1
  echo "== $G start $(date -u +%FT%TZ) over $S"
  start_log "restore-$G" "WarmPrefixEval:I OpenWeights:I" || exit 1
  detached "restore-$G" "am instrument -w -r -e class $PKG.eval.WarmPrefixEval#aFreshChatReadColdAndRestored -e pairs ${PAIRS:-6} -e coolLimitS ${COOL_LIMIT_S:-60} -e gguf /data/local/tmp/openweights/eval/$G.gguf $RUNNER" "$OUT/restore-$G.instrument.txt" || exit 1
  stop_log "restore-$G" "$OUT/restore-$G.log" || exit 1
  fetch "$R/$G.restore.jsonl" "$OUT/restore-$G.restore.jsonl" jsonl || exit 1
  echo "== $G end $(date -u +%FT%TZ) $(grep -c . "$OUT/restore-$G.restore.jsonl" 2>/dev/null || echo 0) rows; outcome=$(outcome "$OUT/restore-$G.instrument.txt")"
done
on 'svc power stayon false' || true
