# r20 continued, after review. Takes over from chain20.sh once its running probe ends (probes 1 to 4
# ran under it), with the same probe, straight into the heat phase. Changes: a probe counts as hot only if the 2 s logger saw a CPU
# pause switch on during it, read from trig-r20b.txt between its start and end; a failed probe or
# heater stops the ladder; the cool-down needs 4 consecutive probes started with no pause, the GPU
# uncapped (kgsl 0, thermal power level 0), every CPU ceiling at its maximum and XO under 50 C.
# Ends LADDER_DONE, or LADDER_HEAT_LIMIT / LADDER_COOL_LIMIT if a target was not reached; r19 follows
# only after LADDER_DONE.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain20.log
m=Qwen3-4B-Q4_0.gguf
B=./pkg/bin/pd_bench_ref
while pidof pd_bench_ref > /dev/null; do sleep 2; done
col() { head -50 trig-r20b.txt | grep '^time ' | tail -1 | tr ' ' '\n' | grep -n "^$1" | cut -d: -f1 | tr '\n' ' '; }
PCOLS=$(col thermal-pause); KG=$(col kgsl@); TP=$(col thermal_pwrlevel); XO=$(col xo)
pausedin() { awk -v a=$1 -v b=$2 -v c="$PCOLS" 'BEGIN{n=split(c,k," ")} $1~/^[0-9]+$/ && $1>=a && $1<=b {for(i=1;i<=n;i++) if($k[i]!="0") f=1} END{exit f?0:1}' trig-r20b.txt; }
coolnow() { tail -1 trig-r20b.txt | awk -v c="$PCOLS" -v kg=$KG -v tp=$TP -v xo=$XO 'BEGIN{n=split(c,k," ")} {ok=($kg=="0" && $tp=="0" && $xo<50000); for(i=1;i<=n;i++) if($k[i]!="0") ok=0; for(j=NF-1;j<=NF;j++) mx[j]=$j} END{exit ok?0:1}'; }
ceilfull() { for x in /sys/devices/system/cpu/cpufreq/policy*; do [ "$(cat $x/scaling_max_freq)" = "$(cat $x/cpuinfo_max_freq)" ] || return 1; done; return 0; }
n=4
probe() {
  n=$((n+1)); ph=$1
  if [ $((n % 4)) = 1 ] || [ $((n % 4)) = 0 ]; then ord=gpu:gpu,cpu:cpu; else ord=cpu:cpu,gpu:gpu; fi
  t0=$(date +%s)
  echo "$t0 probe $n $ph start order $ord" >> $log
  $B -m gguf/$m -c probe.jsonl -o results-r20.jsonl --label r20-$n-$ph --cells $ord --gen 128 --gen-ref \
    --ctx 8192 --threads 6 --max-turns 3 --max-convs 1 > log-r20-$n.txt 2>&1
  rc=$?
  t1=$(date +%s); hotp=0; pausedin $t0 $t1 && hotp=1
  echo "$t1 probe $n $ph end rc $rc paused_during $hotp" >> $log
  [ $rc = 0 ] || { echo "STOPPED probe $n rc $rc" >> $log; exit 1; }
  return $((1 - hotp))
}
heat() {
  echo "$(date +%s) heat start" >> $log
  $B -m gguf/$m -c conversations.jsonl -o heat-r20.jsonl --label heat --cells cpu:cpu --gen 2048 --gen-ref \
    --ctx 8192 --threads 6 --max-turns 2 --max-convs 1 > log-r20-heat.txt 2>&1
  rc=$?
  echo "$(date +%s) heat end rc $rc" >> $log
  [ $rc = 0 ] || { echo "STOPPED heat rc $rc" >> $log; exit 1; }
}
echo "$(date +%s) chain20b takes over after probe 4; pause columns $PCOLS kgsl $KG pwrlevel $TP" >> $log
hot=0; k=0
while [ $hot -lt 6 ] && [ $k -lt 25 ]; do
  k=$((k+1)); heat
  probe heat && hot=$((hot+1))
done
[ $hot -ge 6 ] || { echo LADDER_HEAT_LIMIT >> $log; exit 1; }
c=0; k=0
while [ $k -lt 40 ]; do
  k=$((k+1)); sleep 45
  ok=0; coolnow && ceilfull && ok=1
  probe down
  if [ $ok = 1 ]; then c=$((c+1)); else c=0; fi
  [ $c -ge 4 ] && break
done
[ $c -ge 4 ] || { echo LADDER_COOL_LIMIT >> $log; exit 1; }
echo LADDER_DONE >> $log
sh chain19.sh
