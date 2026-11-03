# r18b on QDC sa943145 (unit 4816891): Qwen3 4B gpu:cpu again. r18's counted cell passed its gate (40 s, 54.4 C,
# uncapped) because the one-conversation warm-up was too short; r18 was stopped at 22 turns, with the
# phone hot, and this starts at once so the gate times out. Same build, conversations and flags.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain18b.log
m=Qwen3-4B-Q4_0.gguf
temps() { for z in /sys/class/thermal/thermal_zone*; do t=$(cat $z/type); case $t in cpu*|gpu*|nsp*|ddr*|battery|skin*|sys-therm*) echo -n "$t=$(cat $z/temp) ";; esac; done; }
env_line() { echo "{\"kind\":\"env\",\"label\":\"$1\",\"model\":\"$m\",\"when\":\"$2\",\"time\":$(date +%s),\"unit\":\"$(getprop ro.serialno)\",\"temps\":\"$(temps)\",\"memavail_kb\":$(grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2)}" >> results-$1.jsonl; }
while pgrep -f "[p]d_bench_ref" >/dev/null; do sleep 1; done
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) bin $(sha256sum pkg/bin/pd_bench_ref | cut -c1-16) model $(grep "^$m " dl18.log | cut -d' ' -f3)" >> $log
echo "$(date +%T) start r18b gpu:cpu" >> $log
env_line r18b before
GGML_HEXAGON_NDEV=2 ./pkg/bin/pd_bench_ref -m gguf/$m -c conversations.jsonl -o results-r18b.jsonl --label r18b \
  --cells gpu:cpu --npu-devices HTP0,HTP1 --gen 2048 --gen-ref --ctx 8192 --threads 6 --max-turns 6 \
  --cool 55 --cool-max 120 > log-r18b.txt 2>&1
rc=$?
env_line r18b after
echo "$(date +%T) end r18b rc $rc" >> $log
echo CHAIN_DONE >> $log
