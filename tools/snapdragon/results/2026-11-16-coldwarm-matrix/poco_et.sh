#!/bin/bash
# One ExecuTorch cold-versus-warm run on the POCO, over USB or, when USB is down, over wireless
# debugging (WIFI=<ip:port>); see poco_lib.sh:
#   [S=<usb serial>] [WIFI=<ip:port>] poco_et.sh <tag> <stem> <context> <pairs> [extra "-e key value" args]
# <stem>.pte and <stem>.tokenizer.json are read from /data/local/tmp/openweights$SUB. Rows, the
# test's, the runtime's and the NPU bridge's log lines and the instrumentation output land in
# poco/<tag>.*.
set -u
. "$(dirname "$0")/poco_lib.sh"
TAG=$1; STEM=$2; CTX=$3; PAIRS=$4; EXTRA=${5:-}
OUT=$HERE/${OUT_DIR:-poco}; mkdir -p "$OUT"
D=/data/local/tmp/openweights${SUB:-}
awake || exit 1
echo "== $TAG start $(date -u +%FT%TZ) over $S"
start_log "$TAG" "ExecuTorchOnDevice:I ExecuTorch:I OpenWeights:I OpenWeightsPD:I" || exit 1
detached "$TAG" "am instrument -w -r -e class $PKG.ExecuTorchOnDeviceTest#${TEST:-theSecondTurnReadColdAndWarm} -e pairs $PAIRS -e coolLimitS ${COOL_LIMIT_S:-60} $EXTRA -e pte $D/$STEM.pte -e tokenizer $D/$STEM.tokenizer.json -e context $CTX $RUNNER" "$OUT/$TAG.instrument.txt" || exit 1
stop_log "$TAG" "$OUT/$TAG.log" || exit 1
for kind in coldwarm prefill; do
  fetch "$R/$STEM.$kind.jsonl" "$OUT/$TAG.$kind.jsonl" jsonl || exit 1
done
echo "== $TAG end $(date -u +%FT%TZ) $(cat "$OUT/$TAG".*.jsonl 2>/dev/null | grep -c .) rows; outcome=$(outcome "$OUT/$TAG.instrument.txt")"
on 'svc power stayon false' || true
