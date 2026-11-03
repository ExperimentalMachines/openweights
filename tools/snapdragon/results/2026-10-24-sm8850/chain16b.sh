# r16, second half (replaces the rest of chain16.sh, whose shell is stopped while its Qwen3 1.7B
# benchmark keeps running). By the user's choice, Qwen3 4B reruns only gpu:cpu, the one cell that
# ran after a passed gate in r14; its other eight r14 cells ran on this same unit. It follows Qwen3
# 1.7B's last cell directly, so it starts warm. Same flags as every other run.
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
# Wait for the running Qwen3 1.7B benchmark, which chain16.sh started; it is not this script's
# child, so its exit code is not known here (its log and results show whether it finished).
while kill -0 9503 2>/dev/null; do sleep 5; done
env_line r16 Qwen3-1.7B-Q4_0.gguf after
echo "$(date +%T) end r16 Qwen3-1.7B-Q4_0.gguf rc unknown (started by chain16.sh)" >> $log
echo "$(date +%T) queue changed: Qwen3 4B reruns gpu:cpu only" >> $log
run r16 Qwen3-4B-Q4_0.gguf HTP0,HTP1 gpu:cpu
echo CHAIN_DONE >> $log
