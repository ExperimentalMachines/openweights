# r24 on QDC sa962335 (unit c581fd31 = phone B): what the hard throttle's CPU pause costs GPU decode,
# with heat held equal. r20 saw GPU decode within 1% of unpaused during natural pauses, on three
# probes, hot. Here the pause is set by hand on a cool phone. The ladder's fixed work (probe.jsonl: one
# conversation, its first 3 turns, up to 128 teacher-forced tokens a turn) on GPU-only, the reference
# binary, at two GPU ceilings set by hand (max_pwrlevel 0 = 1200 MHz; 8 = 578 MHz, the ceiling the
# GPU held through every natural pause in r20), each with the pause off or on (both switches, cores 2
# to 7, for the whole probe). Arms U0 U1 F0 F1 (ceiling U or F, pause 0 or 1).
# The same gate as r23 (hottest CPU and GPU and the XO under 45 C, no cap, no pause, top CPU
# ceilings; not passed in 900 s stops the chain), checked writes, 0 only while the XO is under 70 C,
# a write that does not read back stops the chain, a 1200 s watchdog, guard.sh for anything left set.
# One throwaway U0 probe, then the order fixed
# before any result: U0 U1 F1 F0 | F0 F1 U1 U0 | U1 U0 F0 F1. Labels r24-<i>-<arm>.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain24.log; m=Qwen3-4B-Q4_0.gguf; B=./pkg/bin/pd_bench_ref; G=/sys/class/kgsl/kgsl-3d0
P1=/sys/class/thermal/cooling_device24; P2=/sys/class/thermal/cooling_device30
[ "$(cat $P1/type) $(cat $P2/type)" = "thermal-pause-3C thermal-pause-C0" ] || { echo "STOPPED wrong switches" >> $log; exit 1; }
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
sw() { echo "$(cat $P1/cur_state)$(cat $P2/cur_state)"; }
set_sw() { echo $1 > $P1/cur_state; echo $1 > $P2/cur_state; [ "$(sw)" = "$1$1" ] || { echo "$(date +%s) write $1 read back $(sw)" >> $log; return 1; }; }
clear_sw() { if [ $(cat $xoz/temp) -lt 70000 ]; then set_sw 0; else echo "$(date +%s) left switches $(sw): XO $(cat $xoz/temp)" >> $log; fi; }
set_gpu() { echo $1 > $G/max_pwrlevel; [ "$(cat $G/max_pwrlevel)" = $1 ] || { echo "$(date +%s) max_pwrlevel $1 read back $(cat $G/max_pwrlevel)" >> $log; return 1; }; }
pb=""
cleanup() { [ -n "$pb" ] && kill -9 $pb 2>/dev/null; clear_sw; set_gpu 0; }
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
  g=$(gate)
  case $g in passed*) ;; *) echo "STOPPED at $label gate $g" >> $log; exit 1;; esac
  case $arm in U*) set_gpu 0;; F*) set_gpu 8;; esac || { echo "STOPPED at $label: ceiling not set" >> $log; exit 1; }
  case $arm in *1) set_sw 1 || { echo "STOPPED at $label: pause not set" >> $log; exit 1; };; esac
  sleep 1
  echo "$(date +%s) probe $label arm $arm gate $g hot $(hot) xo $(cat $xoz/temp) switches $(sw) max_pwrlevel $(cat $G/max_pwrlevel) thermal $(cat $G/thermal_pwrlevel)" >> $log
  $B -m gguf/$m -c probe.jsonl -o results-r24.jsonl --label $label --cells gpu:gpu --gen 128 --gen-ref \
    --ctx 8192 --threads 6 --max-turns 3 --max-convs 1 > log-$label.txt 2>&1 &
  pb=$!
  ( sleep 1200; kill $pb 2>/dev/null && { echo "$(date +%s) watchdog killed $label" >> $log; sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null
  clear_sw; set_gpu 0
  echo "$(date +%s) probe $label end rc $rc hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
  case $rc in 0|143|137) ;; *) echo "STOPPED at $label rc $rc" >> $log; exit 1;; esac
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) bin $(sha256sum $B | cut -c1-16) model $(grep "^$m " dl19.log | cut -d' ' -f3) probe $(sha256sum probe.jsonl | cut -c1-16)" >> $log
(sh guard.sh chain24 > /dev/null 2>&1 &)
probe r24warm U0
i=0
for arm in U0 U1 F1 F0 F0 F1 U1 U0 U1 U0 F0 F1; do
  i=$((i+1))
  probe r24-$i-$arm $arm
done
echo R24_DONE >> $log
