# r20 then r19 on QDC sa959107 (unit c581fd31), Qwen3 4B Q4_0, same build as the study.
# r20, the throttle ladder: the same fixed work (one conversation, its first 3 turns, 128 teacher-forced
# tokens a turn) on GPU-only and CPU-only, alternating which goes first, at every thermal state on the
# way up and back down: 4 probes cool, then a CPU heater between probes until both CPU pause switches
# have been seen on in 6 probes, then probes 45 s apart, idle, until the pauses are off and the XO
# thermistor is under 50 C (at least 4 such probes, 40 at most). Only the heat varies.
# r19, the thread test, follows: a throwaway 6-thread cell, then 6 and 2 threads in ABBA order.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain20.log
m=Qwen3-4B-Q4_0.gguf
B=./pkg/bin/pd_bench_ref
xo() { for t in /sys/class/thermal/thermal_zone*; do [ "$(cat $t/type)" = sys-therm-0 ] && cat $t/temp; done; }
paused() { for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-C0|thermal-pause-3C) [ "$(cat $c/cur_state)" != 0 ] && return 0;; esac; done; return 1; }
n=0
probe() {
  n=$((n+1)); ph=$1
  if [ $((n % 4)) = 1 ] || [ $((n % 4)) = 0 ]; then ord=gpu:gpu,cpu:cpu; else ord=cpu:cpu,gpu:gpu; fi
  echo "$(date +%s) probe $n $ph start xo $(xo) paused $(paused && echo 1 || echo 0) order $ord" >> $log
  $B -m gguf/$m -c probe.jsonl -o results-r20.jsonl --label r20-$n-$ph --cells $ord --gen 128 --gen-ref \
    --ctx 8192 --threads 6 --max-turns 3 --max-convs 1 > log-r20-$n.txt 2>&1
  echo "$(date +%s) probe $n $ph end rc $? xo $(xo) paused $(paused && echo 1 || echo 0)" >> $log
}
heat() {
  echo "$(date +%s) heat start xo $(xo)" >> $log
  $B -m gguf/$m -c conversations.jsonl -o heat-r20.jsonl --label heat --cells cpu:cpu --gen 2048 --gen-ref \
    --ctx 8192 --threads 6 --max-turns 2 --max-convs 1 > /dev/null 2>&1
  echo "$(date +%s) heat end xo $(xo) paused $(paused && echo 1 || echo 0)" >> $log
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) bin $(sha256sum $B | cut -c1-16) model $(grep "^$m " dl19.log | cut -d' ' -f3) probe $(sha256sum probe.jsonl | cut -c1-16)" >> $log
for i in 1 2 3 4; do probe cool; done
hot=0; k=0
while [ $hot -lt 6 ] && [ $k -lt 25 ]; do
  k=$((k+1)); heat
  was=0; paused && was=1
  probe heat
  if [ $was = 1 ] || paused; then hot=$((hot+1)); fi
done
c=0; k=0
while [ $k -lt 40 ]; do
  k=$((k+1)); sleep 45
  probe down
  if ! paused && [ "$(xo)" -lt 50000 ]; then c=$((c+1)); [ $c -ge 4 ] && break; fi
done
echo LADDER_DONE >> $log
sh chain19.sh
