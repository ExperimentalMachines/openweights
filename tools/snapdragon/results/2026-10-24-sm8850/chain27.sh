# r27 on QDC sa962766 (unit 5d3e8f0a): the app's own engine with the CPU pause set by hand, which r23
# and r26 ran only in the benchmark. The engine's device test ThreadCountBenchmark (LlamaCppEngine, the
# persistent thread pool the app attaches, prompt and reply threads equal as ThermalPolicy plans them),
# one thread count per run (-e threads), 64 tokens, Qwen3 4B Q4_0 (the study's file, copied to the test's
# path). Arms N<t> / P<t>: pause off, or both pause switches (found by type) at 1 for the whole run;
# t in 2 3 4 6. The gate of r23 (hottest CPU and GPU zone and the XO under 45 C, no GPU cap, no pause,
# top CPU ceilings; not passed in 1800 s stops the chain: the first start, with 900 s, timed out after
# r22's last cell and was restarted with 1800), checked writes, 0 only while the XO is under
# 70 C, guard27.sh (given this shell's PID), and a 420 s watchdog (cut from 600 before it started, to
# fit the reservation) that force-stops the test package; a
# run counts only with its BENCH line in the log, never on the instrumentation's OK alone. Logcat is kept for the
# test's and the engine's own tags only (OpenWeightsBench, OpenWeights). Order fixed before any result:
# N4 P4 P2 N2 P3 N3 N6 P6, then P6 N6 N3 P3 N2 P2 P4 N4. Labels r27-<i>-<arm>.
cd /data/local/tmp/sd
log=chain27.log; G=/sys/class/kgsl/kgsl-3d0; PKG=io.github.alpharomercoma.openweights.core.engine.test
P1=""; P2=""
for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-3C) P1=$c;; thermal-pause-C0) P2=$c;; esac; done
[ -n "$P1" ] && [ -n "$P2" ] || { echo "STOPPED pause switches not found" >> $log; exit 1; }
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
sw() { echo "$(cat $P1/cur_state)$(cat $P2/cur_state)"; }
set_sw() { echo $1 > $P1/cur_state; echo $1 > $P2/cur_state; [ "$(sw)" = "$1$1" ] || { echo "$(date +%s) write $1 read back $(sw)" >> $log; return 1; }; }
clear_sw() { if [ $(cat $xoz/temp) -lt 70000 ]; then set_sw 0; else echo "$(date +%s) left switches $(sw): XO $(cat $xoz/temp)" >> $log; fi; }
pb=""; lc=""; wd=""
cleanup() { [ -n "$wd" ] && kill -9 $wd 2>/dev/null; [ -n "$pb" ] && kill -9 $pb 2>/dev/null; (am force-stop $PKG &); clear_sw; [ -n "$lc" ] && kill $lc 2>/dev/null; }
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
topped() { for x in /sys/devices/system/cpu/cpufreq/policy*; do [ "$(cat $x/scaling_max_freq)" = "$(cat $x/cpuinfo_max_freq)" ] || return 1; done; return 0; }
gate() {
  w=0
  while [ $w -lt 1800 ]; do
    [ $(hot) -le 45000 ] && [ $(cat $xoz/temp) -le 45000 ] && [ "$(cat $G/thermal_pwrlevel)" = 0 ] && [ "$(sw)" = 00 ] && topped && { echo "passed $w"; return; }
    sleep 5; w=$((w+5))
  done
  echo "timed_out $w"
}
run() {
  label=$1; arm=$2; t=${arm#?}
  g=$(gate)
  case $g in passed*) ;; *) echo "STOPPED at $label gate $g" >> $log; exit 1;; esac
  case $arm in P*) set_sw 1 || { echo "STOPPED at $label: pause not set" >> $log; exit 1; };; esac
  sleep 1
  echo "$(date +%s) run $label arm $arm threads $t gate $g hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
  am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ThreadCountBenchmark \
    -e threads $t -e maxTokens 64 -e model /data/local/tmp/openweights/model.gguf \
    $PKG/androidx.test.runner.AndroidJUnitRunner > inst-$label.txt 2>&1 &
  pb=$!
  ( sleep 420; kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog stopped $label" >> $log; (am force-stop $PKG &); sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null; wd=""
  clear_sw
  echo "$(date +%s) run $label end rc $rc hot $(hot) xo $(cat $xoz/temp) switches $(sw) result $(grep -c 'OK (1 test)' inst-$label.txt), bench lines so far $(grep -c "BENCH threads=" logcat-r27.txt)" >> $log
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) model $(sha256sum /data/local/tmp/openweights/model.gguf | cut -c1-16) apk $(pm path $PKG | cut -d: -f2 | xargs sha256sum | cut -c1-16) switches $P1 $P2" >> $log
# This unit has two thermal-pause-3C devices (phone B had one): before any run, check that the two
# written here hold six busy loops to CPU 0 and 1, as pausecheck23.txt did on phone B, or stop.
pcheck() {
  pids=""; for k in 1 2 3 4 5 6; do (end=$(( $(date +%s) + 6 )); while [ $(date +%s) -lt $end ]; do :; done) & pids="$pids $!"; done
  sleep 3; c=""
  for i in 1 2 3 4 5 6 7 8 9 10; do for p in $pids; do s=$(cat /proc/$p/stat 2>/dev/null) && { set -- ${s#*) }; c="$c ${37}"; }; done; sleep 0.2; done
  wait; echo $c | tr ' ' '\n' | sort | uniq -c | tr -s ' ' | tr '\n' ','
}
set_sw 1 || { echo "STOPPED pause check: switches not set" >> $log; exit 1; }
on=$(pcheck); clear_sw; off=$(pcheck)
echo "$(date +%s) pause check: on {$on} off {$off}" >> $log
for k in 2 3 4 5 6 7; do case ",$on" in *" $k,"*) echo "STOPPED pause check: a loop ran on CPU $k with the switches on" >> $log; exit 1;; esac; done
(sh guard27.sh $$ > /dev/null 2>&1 &)
logcat -v epoch -s OpenWeightsBench:I OpenWeights:I >> logcat-r27.txt 2>&1 & lc=$!
i=0
for arm in N4 P4 P2 N2 P3 N3 N6 P6 P6 N6 N3 P3 N2 P2 P4 N4; do
  i=$((i+1))
  run r27-$i-$arm $arm
done
echo R27_DONE >> $log
