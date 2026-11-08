# After the thread test (chain19.sh) ends, however it ends: let the phone idle until the GPU's thermal
# power level is 0, no CPU pause switch is on and every CPU ceiling is at its maximum, read every 30 s
# for up to 60 min, then run the ladder's probe 4 more times (labels r20-cool2-<i>, alternating which
# processor goes first) for an uncapped baseline. If the phone never gets there, the probes run anyway
# and the logger records the state they ran in.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain20.log
while ps -A -o ARGS | grep -q '^sh chain19.sh'; do sleep 20; done
grep -q 'start r19' chain19.log 2>/dev/null || exit 0
ready() {
  [ "$(cat /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel)" = 0 ] || return 1
  for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-C0|thermal-pause-3C) [ "$(cat $c/cur_state)" = 0 ] || return 1;; esac; done
  for x in /sys/devices/system/cpu/cpufreq/policy*; do [ "$(cat $x/scaling_max_freq)" = "$(cat $x/cpuinfo_max_freq)" ] || return 1; done
  return 0
}
t0=$(date +%s); echo "$t0 cool2 idle start" >> $log
while ! ready && [ $(( $(date +%s) - t0 )) -lt 3600 ]; do sleep 30; done
echo "$(date +%s) cool2 idle end ready $(ready && echo 1 || echo 0)" >> $log
for i in 1 2 3 4; do
  if [ $i = 1 ] || [ $i = 4 ]; then ord=gpu:gpu,cpu:cpu; else ord=cpu:cpu,gpu:gpu; fi
  echo "$(date +%s) cool2 probe $i start order $ord" >> $log
  ./pkg/bin/pd_bench_ref -m gguf/Qwen3-4B-Q4_0.gguf -c probe.jsonl -o results-r20.jsonl --label r20-cool2-$i --cells $ord \
    --gen 128 --gen-ref --ctx 8192 --threads 6 --max-turns 3 --max-convs 1 > log-r20-cool2-$i.txt 2>&1
  echo "$(date +%s) cool2 probe $i end rc $?" >> $log
  sleep 90
done
echo COOL2_DONE >> $log
