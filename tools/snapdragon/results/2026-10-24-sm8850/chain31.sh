# r31 on QDC sa963265 (unit 4816891, phone D): r29 again, with a third spin budget. r29 (5d3e8f0a) found a
# barrier that yields after 10,000 spins (Y10k) ends the pause's stall (no turn in 20 min, against about 3
# tok/s) but slows unpaused decode two to three times. Y1M yields only after 1,000,000 spins, tens of
# milliseconds, longer than a normal barrier wait and far shorter than a paused one. Same patched
# libggml-cpu.so (barrier-yield.patch, 5e5eebcd), binary, work and checks as r29. Order fixed before any
# result: N6-Y0 P6-Y1M N6-Y1M P4-Y1M N6-Y10k P6-Y10k P6-Y0 P4-Y1M N6-Y1M P6-Y1M N6-Y0. Labels r31-<i>-<arm>.
cd /data/local/tmp/sd
export ADSP_LIBRARY_PATH=$PWD/pkg/lib
export LD_LIBRARY_PATH=$PWD/pkgy/lib
log=chain31.log; m=Qwen3-4B-Q4_0.gguf; B=./pkg/bin/pd_bench_ref; G=/sys/class/kgsl/kgsl-3d0
P1=""; P2=""
for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-3C) P1=$c;; thermal-pause-C0) P2=$c;; esac; done
[ -n "$P1" ] && [ -n "$P2" ] || { echo "STOPPED pause switches not found" >> $log; exit 1; }
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
sw() { echo "$(cat $P1/cur_state)$(cat $P2/cur_state)"; }
set_sw() { echo $1 > $P1/cur_state; echo $1 > $P2/cur_state; [ "$(sw)" = "$1$1" ] || { echo "$(date +%s) write $1 read back $(sw)" >> $log; return 1; }; }
clear_sw() { if [ $(cat $xoz/temp) -lt 70000 ]; then set_sw 0; else echo "$(date +%s) left switches $(sw)" >> $log; fi; }
pb=""; wd=""
cleanup() { [ -n "$wd" ] && kill -9 $wd 2>/dev/null; [ -n "$pb" ] && kill -9 $pb 2>/dev/null; clear_sw; }
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
topped() { for x in /sys/devices/system/cpu/cpufreq/policy*; do [ "$(cat $x/scaling_max_freq)" = "$(cat $x/cpuinfo_max_freq)" ] || return 1; done; return 0; }
gate() { w=0; while [ $w -lt 1800 ]; do [ $(hot) -le 45000 ] && [ $(cat $xoz/temp) -le 45000 ] && [ "$(cat $G/thermal_pwrlevel)" = 0 ] && [ "$(sw)" = 00 ] && topped && { echo "passed $w"; return; }; sleep 5; w=$((w+5)); done; echo "timed_out $w"; }
probe() {
  label=$1; arm=$2
  case $arm in *-Y0) y=0;; *-Y10k) y=10000;; *-Y1M) y=1000000;; esac
  t=${arm%%-*}; t=${t#?}
  g=$(gate); case $g in passed*) ;; *) echo "STOPPED at $label gate $g" >> $log; exit 1;; esac
  case $arm in P*) set_sw 1 || { echo "STOPPED at $label: pause not set" >> $log; exit 1; };; esac
  sleep 1
  echo "$(date +%s) probe $label arm $arm threads $t spins $y gate $g hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
  GGML_BARRIER_YIELD_SPINS=$y $B -m gguf/$m -c probe.jsonl -o results-r31.jsonl --label $label --cells gpu:cpu --gen 128 --gen-ref \
    --ctx 8192 --threads $t --max-turns 3 --max-convs 1 > log-$label.txt 2>&1 &
  pb=$!
  ( sleep 300; kill $pb 2>/dev/null && { echo "$(date +%s) watchdog killed $label" >> $log; sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null; wd=""
  clear_sw
  echo "$(date +%s) probe $label end rc $rc hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
}
echo "$(date +%T) unit $(getprop ro.serialno) bin $(sha256sum $B | cut -c1-16) cpu lib $(sha256sum pkgy/lib/libggml-cpu.so | cut -c1-16) ref cpu lib $(sha256sum pkg/lib/libggml-cpu.so | cut -c1-16) switches $P1 $P2" >> $log
i=0
for arm in N6-Y0 P6-Y1M N6-Y1M P4-Y1M N6-Y10k P6-Y10k P6-Y0 P4-Y1M N6-Y1M P6-Y1M N6-Y0; do
  i=$((i+1))
  probe r31-$i-$arm $arm
done
echo R31_DONE >> $log
