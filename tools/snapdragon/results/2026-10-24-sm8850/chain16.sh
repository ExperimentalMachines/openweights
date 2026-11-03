# r16 on QDC sa941113 (unit 783ee7e5): Qwen3 1.7B Q4_0 and Qwen3 4B again, all nine cells each,
# because each had cells that ran after a passed gate (r13 and r14 ran them first in their runs).
# One throwaway cpu:cpu pass (label r16warm, its own results file, not part of the matrix) heats the
# phone first; Qwen3 4B follows Qwen3 1.7B directly. Counted cells: the r12 orders and flags exactly.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain16.log
temps() { for z in /sys/class/thermal/thermal_zone*; do t=$(cat $z/type); case $t in cpu*|gpu*|nsp*|ddr*|battery|skin*|sys-therm*) echo -n "$t=$(cat $z/temp) ";; esac; done; }
env_line() { echo "{\"kind\":\"env\",\"label\":\"$1\",\"model\":\"$2\",\"when\":\"$3\",\"time\":$(date +%s),\"unit\":\"$(getprop ro.serialno)\",\"temps\":\"$(temps)\",\"memavail_kb\":$(grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2)}" >> results-$1.jsonl; }
run() { lab=$1; m=$2; devs=$3; cells=$4; nd=$(echo $devs | tr "," "\n" | wc -l)
  while ! grep -q "^$m " dl16.log 2>/dev/null; do sleep 5; done
  echo "$(date +%T) start $lab $m $cells $(grep "^$m " dl16.log | cut -d' ' -f3)" >> $log
  env_line $lab $m before
  GGML_HEXAGON_NDEV=$nd ./pkg/bin/pd_bench_ref -m gguf/$m -c conversations.jsonl -o results-$lab.jsonl --label $lab \
    --cells "$cells" --npu-devices "$devs" --gen 2048 --gen-ref --ctx 8192 --threads 6 \
    --max-turns 6 --cool 55 --cool-max 120 > log-$lab-$m.txt 2>&1
  rc=$?
  env_line $lab $m after
  echo "$(date +%T) end $lab $m rc $rc" >> $log; }
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) bin $(sha256sum pkg/bin/pd_bench_ref | cut -c1-16)" >> $log
run r16warm Qwen3-1.7B-Q4_0.gguf HTP0 cpu:cpu
run r16 Qwen3-1.7B-Q4_0.gguf HTP0 cpu:gpu,cpu:cpu,npu:cpu,gpu:gpu,npu:npu,gpu:cpu,gpu:npu,npu:gpu,cpu:npu
run r16 Qwen3-4B-Q4_0.gguf HTP0,HTP1 gpu:cpu,gpu:gpu,npu:gpu,cpu:npu,npu:npu,cpu:gpu,cpu:cpu,npu:cpu,gpu:npu
echo CHAIN_DONE >> $log
