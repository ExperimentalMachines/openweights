# r23 on QDC sa962335 (unit c581fd31 = phone B): the hard throttle's CPU pause set by hand on a cool
# phone, so the pause is the only thing that differs. Does a pause alone make 6 decode threads stall,
# do 2 threads avoid it, and does switching to 2 while paused (pd_bench --paused-threads 2) recover it
# when the pause comes and goes? The ladder's fixed work (probe.jsonl: one conversation, its first 3
# turns, up to 128 teacher-forced tokens a turn), GPU prefill and CPU decode as in the thread cells,
# one binary (pd_bench_sw) for every arm.
# Arms: N6 / N2 no pause, 6 or 2 threads. P6 / P2 both pause switches at 1 (thermal-pause-3C, cores 2 to
# 5, and thermal-pause-C0, cores 6 and 7) for the whole probe, 6 or 2 threads. T6 / TS the switches
# toggled 15 s on, 15 s off from the probe's start, 6 threads, TS with --paused-threads 2 (review: a
# pause held throughout cannot show a switch recovering a decode, or switching back).
# Before every probe: the hottest CPU and GPU zone and the XO under 45 C, no cap, no pause; a
# gate not passed in 900 s stops the chain. A probe running past 1200 s is killed and logged, and the
# chain goes on. Writes are read back; 0 is written only while the XO is under 70 C, so a real pause
# is never cleared; a write that does not read back stops the chain; guard.sh puts back a leftover
# pause if this script dies.
# One throwaway N6 probe, then the order fixed before any result:
# N6 P6 T6 N2 P2 TS | TS P2 N2 T6 P6 N6 | P6 TS N6 P2 T6 N2. Labels r23-<i>-<arm>. run21.sh runs what follows.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain23.log; m=Qwen3-4B-Q4_0.gguf; B=./pkg/bin/pd_bench_sw; G=/sys/class/kgsl/kgsl-3d0
P1=/sys/class/thermal/cooling_device24; P2=/sys/class/thermal/cooling_device30
[ "$(cat $P1/type) $(cat $P2/type)" = "thermal-pause-3C thermal-pause-C0" ] || { echo "STOPPED wrong switches" >> $log; exit 1; }
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
sw() { echo "$(cat $P1/cur_state)$(cat $P2/cur_state)"; }
set_sw() { echo $1 > $P1/cur_state; echo $1 > $P2/cur_state; [ "$(sw)" = "$1$1" ] || { echo "$(date +%s) write $1 read back $(sw)" >> $log; return 1; }; }
clear_sw() { if [ $(cat $xoz/temp) -lt 70000 ]; then set_sw 0; else echo "$(date +%s) left switches $(sw): XO $(cat $xoz/temp)" >> $log; fi; }
tog=""
# kill -9: on this phone a plain kill on a shell waiting on sleep is deferred until the sleep ends.
stop_tog() { [ -n "$tog" ] && { kill -9 $tog 2>/dev/null; wait $tog 2>/dev/null; tog=""; }; }
pb=""
cleanup() { [ -n "$pb" ] && kill -9 $pb 2>/dev/null; stop_tog; clear_sw; }
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
topped() { for x in /sys/devices/system/cpu/cpufreq/policy*; do [ "$(cat $x/scaling_max_freq)" = "$(cat $x/cpuinfo_max_freq)" ] || return 1; done; return 0; }
gate() {
  w=0
  while [ $w -lt 900 ]; do
    [ $(hot) -le 45000 ] && [ $(cat $xoz/temp) -le 45000 ] && [ "$(cat $G/thermal_pwrlevel)" = 0 ] && [ "$(sw)" = 00 ] && topped && { echo "passed $w"; return; }
    sleep 5; w=$((w+5))
  done
  echo "timed_out $w"
}
probe() {
  label=$1; arm=$2
  case $arm in N6|P6|T6) a="--threads 6";; N2|P2) a="--threads 2";; TS) a="--threads 6 --paused-threads 2";; esac
  g=$(gate)
  case $g in passed*) ;; *) echo "STOPPED at $label gate $g" >> $log; exit 1;; esac
  case $arm in
    P*) set_sw 1 || { echo "STOPPED at $label: pause not set" >> $log; exit 1; };;
    T*) (while true; do set_sw 1; sleep 15; clear_sw; sleep 15; done) & tog=$!;;
  esac
  echo "$(date +%s) probe $label arm $arm gate $g hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
  $B -m gguf/$m -c probe.jsonl -o results-r23.jsonl --label $label --cells gpu:cpu --gen 128 --gen-ref \
    --ctx 8192 $a --max-turns 3 --max-convs 1 > log-$label.txt 2>&1 &
  pb=$!
  ( sleep 1200; kill $pb 2>/dev/null && { echo "$(date +%s) watchdog killed $label" >> $log; sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null
  stop_tog; clear_sw
  echo "$(date +%s) probe $label end rc $rc hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
  case $rc in 0|143|137) ;; *) echo "STOPPED at $label rc $rc" >> $log; exit 1;; esac
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) bin $(sha256sum $B | cut -c1-16) model $(grep "^$m " dl19.log | cut -d' ' -f3) probe $(sha256sum probe.jsonl | cut -c1-16)" >> $log
(sh guard.sh chain23 > /dev/null 2>&1 &)
(sh place23.sh > /dev/null 2>&1 &)
probe r23warm N6
i=0
for arm in N6 P6 T6 N2 P2 TS TS P2 N2 T6 P6 N6 P6 TS N6 P2 T6 N2; do
  i=$((i+1))
  probe r23-$i-$arm $arm
done
echo R23_DONE >> $log
