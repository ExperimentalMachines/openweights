#!/bin/sh
# Puts everything a session needs on the streamed unit (session expiry wipes it): the static curl,
# the bench binaries and libraries, the phone scripts, the conversations, and the NPU exports given.
#   tools/mediatek/push.sh <adb serial> <bench dir> <curl binary> [npu export dir ...]
# An NPU export dir holds the chunk .pte files, the embedding .bin and runner.txt; it lands as
# /data/local/tmp/mtk/npu/<its basename>.
set -eu
S=$1; BENCH=$2; CURL=$3; shift 3
HERE=$(cd "$(dirname "$0")" && pwd)
A="adb -s $S"
$A shell mkdir -p /data/local/tmp/mtk/bench /data/local/tmp/mtk/npu /data/local/tmp/mtk/gguf
$A push "$CURL" /data/local/tmp/mtk/curl >/dev/null
$A push "$BENCH"/. /data/local/tmp/mtk/bench/ >/dev/null
$A push "$HERE"/phone/. /data/local/tmp/mtk/ >/dev/null
$A shell chmod 755 /data/local/tmp/mtk/curl /data/local/tmp/mtk/bench/mtk_pd_bench /data/local/tmp/mtk/bench/mtk_llama_executor_runner /data/local/tmp/mtk/bench/pd_bench_mtk
for d in "$@"; do
  $A push "$d" /data/local/tmp/mtk/npu/ >/dev/null
done
$A shell "cd /data/local/tmp/mtk/bench && sha256sum -c SHA256SUMS 2>&1 | grep -v ': OK' ; ls -la /data/local/tmp/mtk /data/local/tmp/mtk/npu"
