# r50 on QDC sa964839 (unit 57fa7c29, SM8550 Snapdragon 8 Gen 2, Android 14), the engine test APK with the
# guard's early try. Question, set before any result: does trying the planned count as soon as the 2-thread
# steps speed up bring the engine back to 4 threads sooner after a pause ends than the 30 s schedule alone,
# and does it add tries inside a pause that stays? Pause: thermal-pause-F8 (CPUs 3 to 7) written 1, what
# this phone's xo-therm sets at 78 C, leaving the three little cores. Work as r34 and r39: ThreadCountBenchmark,
# Qwen3 4B Q4_0, the 1,751-token prompt-r30, up to 512 tokens, 4 threads, an 8,192-token window.
# Arms: W (pause on 10 s after decode starts, off 30 s later) and M (on from 10 s, held to the end), each
# with the guard as built (E, stallGuard 1) or with its scheduled tries only (T, stallGuard timer).
# Order fixed: W-E W-T W-T W-E M-E M-T M-T M-E, then N-E (no pause). Gate before each run; 600 s watchdog.
# The engine logs every fall, try (scheduled or steps sped up), failed try and return. Helper subshells
# drop the exit trap so that none of them can run the cleanup when it ends.
cd /data/local/tmp/sd
log=chain50.log; G=/sys/class/kgsl/kgsl-3d0; PKG=io.github.alpharomercoma.openweights.core.engine.test
F=""; for c in /sys/class/thermal/cooling_device*; do [ "$(cat $c/type)" = thermal-pause-F8 ] && F=$c; done
[ -n "$F" ] || { echo "STOPPED no thermal-pause-F8" >> $log; exit 1; }
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; xo-therm) xoz=$z;; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
sw() { cat $F/cur_state; }
set_sw() { echo $1 > $F/cur_state; [ "$(sw)" = "$1" ] || { echo "$(date +%s) write $1 read back $(sw)" >> $log; return 1; }; }
clear_sw() { if [ $(cat $xoz/temp) -lt 70000 ]; then set_sw 0; else echo "$(date +%s) left switch $(sw): XO $(cat $xoz/temp)" >> $log; fi; }
pb=""; lc=""; wd=""; pl=""
cleanup() { [ -n "$pl" ] && kill -9 $pl 2>/dev/null; [ -n "$wd" ] && kill -9 $wd 2>/dev/null; [ -n "$pb" ] && kill -9 $pb 2>/dev/null; (am force-stop $PKG &); clear_sw; [ -n "$lc" ] && kill $lc 2>/dev/null; }
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
topped() { for x in /sys/devices/system/cpu/cpufreq/policy*; do [ "$(cat $x/scaling_max_freq)" = "$(cat $x/cpuinfo_max_freq)" ] || return 1; done; return 0; }
gate() {
  w=0
  while [ $w -lt 1800 ]; do
    [ $(hot) -le 45000 ] && [ $(cat $xoz/temp) -le 45000 ] && [ "$(cat $G/thermal_pwrlevel)" = 0 ] && [ "$(sw)" = 0 ] && topped && { echo "passed $w"; return; }
    sleep 5; w=$((w+5))
  done
  echo "timed_out $w"
}
watch_decode() {
  while kill -0 $pb 2>/dev/null && ! logcat -d -v epoch -s OpenWeightsBench:I -T "$1.000" 2>/dev/null | grep -q "reading=false"; do sleep 1; done
}
# Every second while the test process lives: the switch, then each thread's id, last CPU and CPU ticks.
place() {
  p=""; n=0; while [ -z "$p" ] && [ $n -lt 60 ]; do p=$(pidof $PKG); sleep 1; n=$((n+1)); done
  [ -n "$p" ] || return
  while kill -0 $p 2>/dev/null; do
    l="$(date +%s) sw $(sw)"
    for t in /proc/$p/task/*; do s=$(cat $t/stat 2>/dev/null) || continue; set -- ${s#*) }; l="$l ${t##*/}:${37}:$((${12}+${13}))"; done
    echo "$l" >> place-$label.txt; sleep 1
  done
}
run() {
  label=$1; arm=$2; mode=$3
  w=$(gate)
  case $w in passed*) ;; *) echo "STOPPED at $label gate $w" >> $log; exit 1;; esac
  tog=""; t0=$(date +%s)
  am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ThreadCountBenchmark \
    -e threads 4 -e maxTokens 512 -e contextLength 8192 -e stallGuard $mode \
    -e model /data/local/tmp/openweights/model.gguf -e promptFile /data/local/tmp/openweights/prompt-r30.txt \
    $PKG/androidx.test.runner.AndroidJUnitRunner > inst-$label.txt 2>&1 &
  pb=$!
  echo "$t0 run $label arm $arm guard $mode gate $w hot $(hot) xo $(cat $xoz/temp) switch $(sw)" >> $log
  case $arm in
    W) (trap - EXIT INT TERM HUP; watch_decode $t0; sleep 10; set_sw 1; echo "$(date +%s) $label pause on" >> $log; sleep 30; clear_sw; echo "$(date +%s) $label pause off" >> $log) & tog=$!;;
    M) (trap - EXIT INT TERM HUP; watch_decode $t0; sleep 10; set_sw 1; echo "$(date +%s) $label pause on" >> $log) & tog=$!;;
  esac
  (trap - EXIT INT TERM HUP; place) & pl=$!
  ( trap - EXIT INT TERM HUP; sleep 600; kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog stopped $label" >> $log; (am force-stop $PKG &); sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $pl 2>/dev/null; wait $pl 2>/dev/null; pl=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null; wd=""
  [ -n "$tog" ] && { kill -9 $tog 2>/dev/null; wait $tog 2>/dev/null; }
  clear_sw
  echo "$(date +%s) run $label end rc $rc hot $(hot) xo $(cat $xoz/temp) switch $(sw) result $(grep -c 'OK (1 test)' inst-$label.txt)" >> $log
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) model $(sha256sum /data/local/tmp/openweights/model.gguf | cut -c1-16) apk $(pm path $PKG | cut -d: -f2 | xargs sha256sum | cut -c1-16) switch $F" >> $log
(sh guard50.sh $$ > /dev/null 2>&1 &)
logcat -v epoch -s OpenWeightsBench:I OpenWeights:I >> logcat-r50.txt 2>&1 & lc=$!
i=0
for cell in W:E W:T W:T W:E M:E M:T M:T M:E N:E; do
  i=$((i+1)); arm=${cell%%:*}; m=${cell##*:}; [ $m = E ] && mode=1 || mode=timer
  run r50-$i-$arm$m $arm $mode
done
echo R50_DONE >> $log
