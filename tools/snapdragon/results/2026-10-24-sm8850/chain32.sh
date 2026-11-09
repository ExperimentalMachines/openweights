# r32 on QDC sa963265 (unit 4816891, phone D): r30's design, rerun here after r30 was stopped before it
# started on 5d3e8f0a when the study moved to this longer reservation. The engine's stall guard (stall_guard.h,
# this commit) at a realistic length. The engine's device test as r27, from a build with the guard
# (engine-test2.apk); stallGuard=0 turns it off for a run, so one build measures it against itself. A
# 2,257-token-class prompt (prompt-r30.txt: the first two exchanges of the study's UltraChat-200k-2 and a
# request for a long guide), up to 1,024 tokens, an 8,192-token window, 4 threads unless named. Arms:
# N<t>-<g> no pause; W4-<g> both pause switches on from 40 s to 80 s after the run starts (a pause that
# comes and goes mid-reply); M4-<g> on from 40 s to the run's end (one that comes and stays); g is 0
# (guard off) or G (on). Order fixed before any result: N4-0 W4-G M4-0 N2-0 N4-G M4-G W4-0, then the
# reverse. r27's gate, checks, guard32.sh and a 420 s watchdog. Labels r32-<i>-<arm>.
cd /data/local/tmp/sd
log=chain32.log; G=/sys/class/kgsl/kgsl-3d0; PKG=io.github.alpharomercoma.openweights.core.engine.test
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
  label=$1; arm=$2
  t=${arm%%-*}; t=${t#?}; g=${arm##*-}; [ $g = G ] && gv=1 || gv=0
  w=$(gate)
  case $w in passed*) ;; *) echo "STOPPED at $label gate $w" >> $log; exit 1;; esac
  tog=""
  case $arm in
    W*) (sleep 40; set_sw 1; sleep 40; clear_sw) & tog=$!;;
    M*) (sleep 40; set_sw 1) & tog=$!;;
  esac
  echo "$(date +%s) run $label arm $arm threads $t guard $gv gate $w hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
  am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ThreadCountBenchmark \
    -e threads $t -e maxTokens 1024 -e contextLength 8192 -e stallGuard $gv \
    -e model /data/local/tmp/openweights/model.gguf -e promptFile /data/local/tmp/openweights/prompt-r30.txt \
    $PKG/androidx.test.runner.AndroidJUnitRunner > inst-$label.txt 2>&1 &
  pb=$!
  ( sleep 420; kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog stopped $label" >> $log; (am force-stop $PKG &); sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null; wd=""
  [ -n "$tog" ] && { kill -9 $tog 2>/dev/null; wait $tog 2>/dev/null; }
  clear_sw
  echo "$(date +%s) run $label end rc $rc hot $(hot) xo $(cat $xoz/temp) switches $(sw) result $(grep -c 'OK (1 test)' inst-$label.txt)" >> $log
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
(sh guard32.sh $$ > /dev/null 2>&1 &)
logcat -v epoch -s OpenWeightsBench:I OpenWeights:I >> logcat-r32.txt 2>&1 & lc=$!
i=0
for arm in N4-0 W4-G M4-0 N2-0 N4-G M4-G W4-0 W4-0 M4-G N4-G N2-0 M4-0 W4-G N4-0; do
  i=$((i+1))
  run r32-$i-$arm $arm
done
echo R32_DONE >> $log
