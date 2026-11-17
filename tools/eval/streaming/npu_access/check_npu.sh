#!/bin/bash
# Can a retail unit's shell reach its NPU? Reserves one Device Streaming unit, records the SoC, any NPU
# device nodes with their owner, mode and SELinux label, whether the shell can open them, the NPU and
# neural-network services, and the vendor NPU libraries, then cancels. For the goal's condition on
# Exynos and Tensor ("only if their NPUs are accessible"), 2026-11-17.
#   check_npu.sh <label> <model id> <android version> <port>
set -u
LAB=$1; MODEL=$2; VER=$3; PORT=$4; S=localhost:$PORT; A="adb -s $S"
HERE=$(cd "$(dirname "$0")" && pwd); ST=$(cd "$HERE/.." && pwd); OUT=$HERE/$LAB; mkdir -p "$OUT"
bridge() { (cd "$ST" && uv run --with google-cloud-devicestreaming python ds_bridge.py "$@"); }
bridge reserve $MODEL $VER --ttl 1800 > "$OUT/reserve.log" 2>&1
grep -q ACTIVE "$OUT/reserve.log" || { echo "reserve failed"; tail -3 "$OUT/reserve.log"; exit 1; }
SESS=$(grep -o "projects/[^ ]*/deviceSessions/session-[a-z0-9]*" "$OUT/reserve.log" | tail -1)
(cd "$ST" && nohup uv run --with google-cloud-devicestreaming python ds_bridge.py serve "$SESS" --port $PORT -v > "$OUT/bridge.log" 2>&1 &); sleep 10
adb connect $S > /dev/null 2>&1; for i in $(seq 1 30); do $A shell echo ok 2>/dev/null | grep -q ok && break; sleep 5; done
$A shell 'echo "== soc"; getprop ro.soc.manufacturer; getprop ro.soc.model; getprop ro.product.model; getprop ro.build.version.release; id
echo "== nodes"; ls -lZ /dev 2>/dev/null | grep -i -E "npu|vertex|edgetpu|tpu|apu|dsp|neuron|enn|gxp|darwinn|abrolhos|janeiro|lassen|rio|vpu"
echo "== open"; for n in $(ls /dev 2>/dev/null | grep -i -E "npu|vertex|edgetpu|tpu|apu|neuron|enn|gxp|darwinn|abrolhos|janeiro|lassen|rio"); do (exec 3</dev/$n) 2>/dev/null && echo "$n: opened" || echo "$n: denied"; done
echo "== services"; service list 2>/dev/null | grep -i -E "npu|neural|edgetpu|tpu|enn|eden|darwinn|aicore|litert|ml" | head -20
echo "== vendor libs"; ls /vendor/lib64 2>/dev/null | grep -i -E "enn|eden|npu|edgetpu|darwinn|litert|tflite|neural|gxp|aicore" | head -30
echo "== vendor lib access"; for f in $(ls /vendor/lib64 2>/dev/null | grep -i -E "enn|npu|edgetpu|darwinn|litert" | head -5); do [ -r /vendor/lib64/$f ] && echo "$f: readable" || echo "$f: not readable"; done
echo "== nnapi"; ls /vendor/bin/hw 2>/dev/null | grep -i -E "neural|npu|edgetpu" ; service list 2>/dev/null | grep -c neuralnetworks' > "$OUT/check.txt" 2>&1
pkill -f "ds_bridge.py serve $SESS " 2>/dev/null; bridge cancel "$SESS" > "$OUT/cancel.log" 2>&1
echo "$LAB done: $(wc -l < "$OUT/check.txt") lines; $(tail -1 "$OUT/cancel.log")"
