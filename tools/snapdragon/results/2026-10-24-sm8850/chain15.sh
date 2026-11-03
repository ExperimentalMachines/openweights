# r15 on QDC sa941113 (unit 783ee7e5): LFM2.5 1.2B rerun. r12's best LFM2.5 1.2B cell (npu:cpu) ran
# first on a cool phone, CPU uncapped for 19 of 33 turns, so it was not comparable with cells 2 to 9.
# One throwaway cpu:cpu pass (label r15warm, its own results file, not part of the matrix) heats the
# phone first, so the first counted cell starts as warm as the rest. Counted cells: r12's order and
# flags exactly.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain15.log
temps() { for z in /sys/class/thermal/thermal_zone*; do t=$(cat $z/type); case $t in cpu*|gpu*|nsp*|ddr*|battery|skin*|sys-therm*) echo -n "$t=$(cat $z/temp) ";; esac; done; }
env_line() { echo "{\"kind\":\"env\",\"label\":\"$1\",\"model\":\"$2\",\"when\":\"$3\",\"time\":$(date +%s),\"unit\":\"$(getprop ro.serialno)\",\"temps\":\"$(temps)\",\"memavail_kb\":$(grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2)}" >> results-$1.jsonl; }
run() { lab=$1; m=$2; devs=$3; cells=$4; nd=$(echo $devs | tr "," "\n" | wc -l)
  while ! grep -q "^$m " dl15.log 2>/dev/null; do sleep 5; done
  echo "$(date +%T) start $lab $m $cells $(grep "^$m " dl15.log | cut -d' ' -f3)" >> $log
  env_line $lab $m before
  GGML_HEXAGON_NDEV=$nd ./pkg/bin/pd_bench_ref -m gguf/$m -c conversations.jsonl -o results-$lab.jsonl --label $lab \
    --cells "$cells" --npu-devices "$devs" --gen 2048 --gen-ref --ctx 8192 --threads 6 \
    --max-turns 6 --cool 55 --cool-max 120 > log-$lab-$m.txt 2>&1
  rc=$?
  env_line $lab $m after
  echo "$(date +%T) end $lab $m rc $rc" >> $log; }
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) bin $(sha256sum pkg/bin/pd_bench_ref | cut -c1-16)" >> $log
run r15warm LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf HTP0 cpu:cpu
run r15 LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf HTP0 npu:cpu,gpu:gpu,cpu:cpu,npu:npu,cpu:gpu,cpu:npu,gpu:cpu,gpu:npu,npu:gpu
echo CHAIN_DONE >> $log
