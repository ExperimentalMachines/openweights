# r18 on QDC sa943145 (unit 4816891, a fourth phone): Qwen3 4B's GPU prefill with CPU decode, the one
# matrix cell that started cool in r14, run warm. Agreed with the maintainer as an exception to the
# one-run, one-phone rule. A short throwaway CPU-only pass of the first conversation (r18warm) heats
# the phone first. Same build, conversations and flags as r12 to r16.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain18.log
m=Qwen3-4B-Q4_0.gguf
temps() { for z in /sys/class/thermal/thermal_zone*; do t=$(cat $z/type); case $t in cpu*|gpu*|nsp*|ddr*|battery|skin*|sys-therm*) echo -n "$t=$(cat $z/temp) ";; esac; done; }
env_line() { echo "{\"kind\":\"env\",\"label\":\"$1\",\"model\":\"$m\",\"when\":\"$2\",\"time\":$(date +%s),\"unit\":\"$(getprop ro.serialno)\",\"temps\":\"$(temps)\",\"memavail_kb\":$(grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2)}" >> results-$1.jsonl; }
while ! grep -q "^$m " dl18.log 2>/dev/null; do sleep 5; done
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) bin $(sha256sum pkg/bin/pd_bench_ref | cut -c1-16) model $(grep "^$m " dl18.log | cut -d' ' -f3)" >> $log
echo "$(date +%T) start r18warm cpu:cpu one conversation" >> $log
env_line r18warm before
GGML_HEXAGON_NDEV=2 ./pkg/bin/pd_bench_ref -m gguf/$m -c conversations.jsonl -o results-r18warm.jsonl --label r18warm \
  --cells cpu:cpu --npu-devices HTP0,HTP1 --gen 2048 --gen-ref --ctx 8192 --threads 6 --max-turns 6 --max-convs 1 \
  --cool 55 --cool-max 120 > log-r18warm.txt 2>&1
env_line r18warm after
echo "$(date +%T) end r18warm rc $?" >> $log
echo "$(date +%T) start r18 gpu:cpu" >> $log
env_line r18 before
GGML_HEXAGON_NDEV=2 ./pkg/bin/pd_bench_ref -m gguf/$m -c conversations.jsonl -o results-r18.jsonl --label r18 \
  --cells gpu:cpu --npu-devices HTP0,HTP1 --gen 2048 --gen-ref --ctx 8192 --threads 6 --max-turns 6 \
  --cool 55 --cool-max 120 > log-r18.txt 2>&1
rc=$?
env_line r18 after
echo "$(date +%T) end r18 rc $rc" >> $log
echo CHAIN_DONE >> $log
