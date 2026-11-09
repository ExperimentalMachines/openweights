# r26 on QDC sa962335 (unit c581fd31 = phone B), in the reservation's last half hour: the thread
# counts the app uses (4 by default, 3 at light or moderate thermal status; ThermalPolicy) inside the
# hard throttle's pause, which r23 did not run. As r23 in everything else: the pause set by hand on a
# cool phone for the whole probe, the same fixed work, binary (pd_bench_sw), gate, checked writes and
# guard, except a 600 s watchdog. Arms P4 / P3 paused with 4 or 3 threads, N4 not paused. Order fixed
# before any result, most needed first: P4 P3 N4, then N4 P3 P4 if the reservation lasts. No warm-up
# probe (r25 ran just before on the same model). Labels r26-<i>-<arm>.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain26.log; m=Qwen3-4B-Q4_0.gguf; B=./pkg/bin/pd_bench_sw; G=/sys/class/kgsl/kgsl-3d0
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
  case $arm in N4|P4) a="--threads 4";; P3) a="--threads 3";; esac
  g=$(gate)
  case $g in passed*) ;; *) echo "STOPPED at $label gate $g" >> $log; exit 1;; esac
  case $arm in
    P*) set_sw 1 || { echo "STOPPED at $label: pause not set" >> $log; exit 1; };;
    T*) (while true; do set_sw 1; sleep 15; clear_sw; sleep 15; done) & tog=$!;;
  esac
  echo "$(date +%s) probe $label arm $arm gate $g hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
  $B -m gguf/$m -c probe.jsonl -o results-r26.jsonl --label $label --cells gpu:cpu --gen 128 --gen-ref \
    --ctx 8192 $a --max-turns 3 --max-convs 1 > log-$label.txt 2>&1 &
  pb=$!
  ( sleep 600; kill $pb 2>/dev/null && { echo "$(date +%s) watchdog killed $label" >> $log; sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null
  stop_tog; clear_sw
  echo "$(date +%s) probe $label end rc $rc hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
  case $rc in 0|143|137) ;; *) echo "STOPPED at $label rc $rc" >> $log; exit 1;; esac
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) bin $(sha256sum $B | cut -c1-16) model $(grep "^$m " dl19.log | cut -d' ' -f3) probe $(sha256sum probe.jsonl | cut -c1-16)" >> $log
(sh guard.sh chain26 > /dev/null 2>&1 &)
i=0
for arm in P4 P3 N4 N4 P3 P4; do
  i=$((i+1))
  probe r26-$i-$arm $arm
done
echo R26_DONE >> $log
