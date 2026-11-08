# r19 on QDC sa959107 (unit c581fd31): does CPU decode still stall with 2 threads instead of 6?
# Qwen3 4B gpu:cpu, the cell with the most stalls (r13 on this unit, r16, r18b). Same build, conversations
# and flags as r18b except --threads. Runs after r20's ladder. A throwaway 6-thread cell first, then
# the order fixed before any result: 6 2 2 6 | 2 6 6 2 | 6 2 2 6 | 2 6 6 2 (ABBA, then BAAB).
# Stops at the first cell that fails. Labels r19-<i>-t<threads>.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain19.log
m=Qwen3-4B-Q4_0.gguf
temps() { for z in /sys/class/thermal/thermal_zone*; do t=$(cat $z/type); case $t in cpu*|gpu*|nsp*|ddr*|battery|skin*|sys-therm*) echo -n "$t=$(cat $z/temp) ";; esac; done; }
env_line() { echo "{\"kind\":\"env\",\"label\":\"$1\",\"model\":\"$m\",\"when\":\"$2\",\"time\":$(date +%s),\"unit\":\"$(getprop ro.serialno)\",\"temps\":\"$(temps)\",\"memavail_kb\":$(grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2)}" >> results-r19.jsonl; }
cell() {
  label=$1; t=$2
  echo "$(date +%s) start $label threads $t" >> $log
  env_line $label before
  GGML_HEXAGON_NDEV=2 ./pkg/bin/pd_bench_ref -m gguf/$m -c conversations.jsonl -o results-r19.jsonl --label $label \
    --cells gpu:cpu --npu-devices HTP0,HTP1 --gen 2048 --gen-ref --ctx 8192 --threads $t --max-turns 6 \
    --cool 55 --cool-max 120 > log-$label.txt 2>&1
  rc=$?
  env_line $label after
  echo "$(date +%s) end $label rc $rc" >> $log
  [ $rc = 0 ] || { echo "STOPPED at $label rc $rc" >> $log; exit 1; }
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) bin $(sha256sum pkg/bin/pd_bench_ref | cut -c1-16) model $(sha256sum gguf/$m | cut -c1-16) conv $(sha256sum conversations.jsonl | cut -c1-16)" >> $log
(nohup sh place19.sh > /dev/null 2>&1 &)
cell r19warm 6
i=0
for t in 6 2 2 6 2 6 6 2 6 2 2 6 2 6 6 2; do
  i=$((i+1))
  cell r19-$i-t$t $t
done
echo CHAIN_DONE >> $log
