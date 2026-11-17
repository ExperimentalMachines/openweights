#!/bin/bash
# Copies a session's results off the streamed unit into the repository every few minutes, because a
# Device Streaming session that ends wipes the unit, and the session scratchpad on the Mac can be
# wiped too (r17's raw data was lost that way). Runs until killed or the device is gone.
#   tools/snapdragon/pull_sd.sh <adb serial> <results dir> [seconds]
S=$1; DEST=$2; EVERY=${3:-180}
mkdir -p "$DEST"
while true; do
  if adb -s "$S" exec-out 'cd /data/local/tmp/sd && tar cf - results-*.jsonl log-*.txt chain-*.log chain-*.out sdlog*.txt place*.txt heat.log hot-*.log pf-*.txt memguard-*.txt dl.log dl-held.txt smoke*.jsonl 2>/dev/null' > "$DEST/.pull.tar" 2>/dev/null \
     && [ -s "$DEST/.pull.tar" ]; then
    tar xf "$DEST/.pull.tar" -C "$DEST" 2>/dev/null && echo "$(date +%T) pulled $(tar tf "$DEST/.pull.tar" | wc -l | tr -d ' ') files"
  else
    echo "$(date +%T) pull failed"
    adb -s "$S" get-state >/dev/null 2>&1 || { echo "$(date +%T) device gone"; exit 1; }
  fi
  sleep "$EVERY"
done
