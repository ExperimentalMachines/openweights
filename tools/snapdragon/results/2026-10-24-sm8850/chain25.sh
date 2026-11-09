# r25 on QDC sa962335 (unit c581fd31 = phone B): is the soft throttle's CPU cost the ceiling alone?
# r20 put CPU decode at 0.77 to 0.82 and prefill at 0.63 to 0.67 of the cool probes, hot. Here the
# ceilings are set by hand on a cool phone: U leaves both CPU policies at their tops (3628.8 and 4608
# MHz); C writes the ceilings the soft throttle set in r20 (policy0 1900.8 MHz, policy6 2227.2 MHz) to
# scaling_max_freq. The ladder's fixed work on CPU-only (cpu:cpu, 6 threads), the reference binary.
# The same gate as r23 (not passed in 900 s stops the chain); the tops are written back after every
# probe and on exit, checked; a write that does not read back stops the chain; guard.sh puts them back if
# this script dies. One throwaway U probe, then U C C U C U U C (cut from 12 before it started, to
# finish inside the reservation). Labels r25-<i>-<arm>.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain25.log; m=Qwen3-4B-Q4_0.gguf; B=./pkg/bin/pd_bench_ref; G=/sys/class/kgsl/kgsl-3d0
C0=/sys/devices/system/cpu/cpufreq/policy0; C6=/sys/devices/system/cpu/cpufreq/policy6
P1=/sys/class/thermal/cooling_device24; P2=/sys/class/thermal/cooling_device30
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
set_cpu() { echo $1 > $C0/scaling_max_freq; echo $2 > $C6/scaling_max_freq
  [ "$(cat $C0/scaling_max_freq) $(cat $C6/scaling_max_freq)" = "$1 $2" ] || { echo "$(date +%s) ceilings $1 $2 read back $(cat $C0/scaling_max_freq) $(cat $C6/scaling_max_freq)" >> $log; return 1; }; }
tops() { set_cpu $(cat $C0/cpuinfo_max_freq) $(cat $C6/cpuinfo_max_freq); }
pb=""
cleanup() { [ -n "$pb" ] && kill -9 $pb 2>/dev/null; tops; }
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
topped() { for x in /sys/devices/system/cpu/cpufreq/policy*; do [ "$(cat $x/scaling_max_freq)" = "$(cat $x/cpuinfo_max_freq)" ] || return 1; done; return 0; }
gate() {
  w=0
  while [ $w -lt 900 ]; do
    [ $(hot) -le 45000 ] && [ $(cat $xoz/temp) -le 45000 ] && [ "$(cat $G/thermal_pwrlevel)" = 0 ] && [ "$(cat $P1/cur_state)$(cat $P2/cur_state)" = 00 ] && topped && { echo "passed $w"; return; }
    sleep 5; w=$((w+5))
  done
  echo "timed_out $w"
}
probe() {
  label=$1; arm=$2
  g=$(gate)
  case $g in passed*) ;; *) echo "STOPPED at $label gate $g" >> $log; exit 1;; esac
  if [ $arm = C ]; then set_cpu 1900800 2227200 || { echo "STOPPED at $label: ceilings not set" >> $log; exit 1; }; fi
  sleep 1
  echo "$(date +%s) probe $label arm $arm gate $g hot $(hot) xo $(cat $xoz/temp) ceilings $(cat $C0/scaling_max_freq) $(cat $C6/scaling_max_freq)" >> $log
  $B -m gguf/$m -c probe.jsonl -o results-r25.jsonl --label $label --cells cpu:cpu --gen 128 --gen-ref \
    --ctx 8192 --threads 6 --max-turns 3 --max-convs 1 > log-$label.txt 2>&1 &
  pb=$!
  ( sleep 1200; kill $pb 2>/dev/null && { echo "$(date +%s) watchdog killed $label" >> $log; sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null
  tops
  echo "$(date +%s) probe $label end rc $rc hot $(hot) xo $(cat $xoz/temp) ceilings $(cat $C0/scaling_max_freq) $(cat $C6/scaling_max_freq)" >> $log
  case $rc in 0|143|137) ;; *) echo "STOPPED at $label rc $rc" >> $log; exit 1;; esac
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) bin $(sha256sum $B | cut -c1-16) model $(grep "^$m " dl19.log | cut -d' ' -f3) probe $(sha256sum probe.jsonl | cut -c1-16)" >> $log
(sh guard.sh chain25 > /dev/null 2>&1 &)
probe r25warm U
i=0
for arm in U C C U C U U C; do
  i=$((i+1))
  probe r25-$i-$arm $arm
done
echo R25_DONE >> $log
