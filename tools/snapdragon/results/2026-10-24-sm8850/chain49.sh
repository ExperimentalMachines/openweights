# r49 on QDC sa964425 (unit 64a112da, phone A), build 62439b56. Two questions, set before any result.
# 1) The thread table, redone on the app's own engine: 1 to 8 threads, normally (N) and with six cores taken out of
#    use by hand (P: thermal-pause-3C and both thermal-pause-C0 written 1, the three the phone's own trip set in r47
#    and r48), three rounds in a fixed shuffled order, a cooling gate before every run. ThreadCountBenchmark, its
#    built-in 19-token prompt, up to 32 tokens, a 2,048-token window, the guard off. Prefill and decode come from
#    the engine's progress lines, so a run its watchdog stops still gives both. place() logs each thread's last
#    core and CPU time every second, which shows whether a pause held.
# 2) Where six threads run, recorded: one 6-thread run in each state with the kernel's sched_switch and
#    sched_wakeup events traced for 2 s during decode (trace-<label>.txt) and the process's thread list.
cd /data/local/tmp/sd
log=chain49.log; G=/sys/class/kgsl/kgsl-3d0; PKG=io.github.alpharomercoma.openweights.core.engine.test; T=/sys/kernel/tracing
SW=""; for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-3C|thermal-pause-C0) SW="$SW $c";; esac; done
[ $(echo $SW | wc -w) = 3 ] || { echo "STOPPED expected three pause switches, found:$SW" >> $log; exit 1; }
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
sw() { s=""; for c in $SW; do s="$s$(cat $c/cur_state)"; done; echo $s; }
set_sw() { for c in $SW; do echo $1 > $c/cur_state; done; [ "$(sw)" = "$1$1$1" ] || { echo "$(date +%s) write $1 read back $(sw)" >> $log; return 1; }; }
clear_sw() { if [ $(cat $xoz/temp) -lt 70000 ]; then set_sw 0; else echo "$(date +%s) left switches $(sw): XO $(cat $xoz/temp)" >> $log; fi; }
pb=""; lc=""; wd=""; pl=""; bright=$(settings get system screen_brightness)
cleanup() { echo 0 > $T/tracing_on; svc power stayon false; settings put system screen_brightness $bright; [ -n "$pl" ] && kill -9 $pl 2>/dev/null; [ -n "$wd" ] && kill -9 $wd 2>/dev/null; [ -n "$pb" ] && kill -9 $pb 2>/dev/null; (am force-stop $PKG &); clear_sw; [ -n "$lc" ] && kill $lc 2>/dev/null; }
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
topped() { for x in /sys/devices/system/cpu/cpufreq/policy*; do [ "$(cat $x/scaling_max_freq)" = "$(cat $x/cpuinfo_max_freq)" ] || return 1; done; return 0; }
gate() {
  w=0
  while [ $w -lt 900 ]; do
    [ $(hot) -le 45000 ] && [ $(cat $xoz/temp) -le 45000 ] && [ "$(cat $G/thermal_pwrlevel)" = 0 ] && [ "$(sw)" = 000 ] && topped && { echo "passed $w"; return; }
    sleep 5; w=$((w+5))
  done
  echo "timed_out $w"
}
place() {
  p=""; n=0; while [ -z "$p" ] && [ $n -lt 60 ]; do p=$(pidof $PKG); sleep 1; n=$((n+1)); done
  [ -n "$p" ] || return
  while kill -0 $p 2>/dev/null; do
    l="$(date +%s) sw $(sw)"
    for t in /proc/$p/task/*; do s=$(cat $t/stat 2>/dev/null) || continue; set -- ${s#*) }; l="$l ${t##*/}:${37}:$((${12}+${13}))"; done
    echo "$l" >> place-$label.txt; sleep 1
  done
}
# Returns once this run's decode has begun (its first progress line with reading=false since t0), or its run ended.
watch_decode() {
  while kill -0 $pb 2>/dev/null && ! logcat -d -v epoch -s OpenWeightsBench:I -T "$1.000" 2>/dev/null | grep -q "reading=false"; do sleep 1; done
}
trace2s() {
  p=$(pidof $PKG)
  for t in /proc/$p/task/*; do echo "${t##*/} $(cat $t/comm)"; done > tasks-$label.txt
  echo 0 > $T/tracing_on; echo > $T/trace; echo 16384 > $T/buffer_size_kb; echo mono > $T/trace_clock
  echo 1 > $T/events/sched/sched_switch/enable; echo 1 > $T/events/sched/sched_wakeup/enable
  echo "$(date +%s) $label trace on, pid $p, switches $(sw)" >> $log
  echo 1 > $T/tracing_on; sleep 2; echo 0 > $T/tracing_on
  echo 0 > $T/events/sched/sched_switch/enable; echo 0 > $T/events/sched/sched_wakeup/enable
  cat $T/trace > trace-$label.txt; echo > $T/trace
  echo "$(date +%s) $label trace off, $(wc -l < trace-$label.txt) lines" >> $log
}
run() {
  label=$1; t=$2; state=$3; mt=$4; cap=$5; traced=$6
  w=$(gate)
  case $w in passed*) ;; *) echo "STOPPED at $label gate $w" >> $log; exit 1;; esac
  if [ $state = P ]; then set_sw 1 || { echo "STOPPED at $label: switches not set" >> $log; exit 1; }; fi
  t0=$(date +%s)
  echo "$t0 run $label threads $t state $state gate $w hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
  am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ThreadCountBenchmark \
    -e threads $t -e maxTokens $mt -e contextLength 2048 -e stallGuard 0 \
    -e model /data/local/tmp/openweights/model.gguf \
    $PKG/androidx.test.runner.AndroidJUnitRunner > inst-$label.txt 2>&1 &
  pb=$!
  (place) & pl=$!
  ( sleep $cap; kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog stopped $label" >> $log; (am force-stop $PKG &); sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  [ -n "$traced" ] && { watch_decode $t0; sleep 3; kill -0 $pb 2>/dev/null && trace2s; }
  wait $pb; rc=$?; pb=""
  kill -9 $pl 2>/dev/null; wait $pl 2>/dev/null; pl=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null; wd=""
  s=$(sw); clear_sw
  echo "$(date +%s) run $label end rc $rc hot $(hot) xo $(cat $xoz/temp) switches at end $s result $(grep -c 'OK (1 test)' inst-$label.txt)" >> $log
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) model $(sha256sum /data/local/tmp/openweights/model.gguf | cut -c1-16) apk $(pm path $PKG | cut -d: -f2 | xargs sha256sum | cut -c1-16) switches$SW" >> $log
# Before any run: the three switches must hold six busy loops to CPUs 0 and 1, or stop.
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
(sh guard49.sh $$ > /dev/null 2>&1 &)
logcat -v epoch -s OpenWeightsBench:I OpenWeights:I >> logcat-r49.txt 2>&1 & lc=$!
svc power stayon true; input keyevent KEYCODE_WAKEUP; settings put system screen_brightness 128
# The two traced runs: 6 threads, normally (256 tokens, so decode lasts past the trace) and with the cores out of use.
run r49-T-N6 6 N 256 120 traced
run r49-T-P6 6 P 32 300 traced
# Three rounds, fixed order (python random.seed(49), shuffled once per round).
r=0
for order in "N3 P4 P2 N7 P8 P6 P7 P5 N1 N6 N5 N8 P1 N4 P3 N2" "N7 P6 N2 P8 N5 P1 N6 P2 N1 N4 P3 N8 N3 P5 P7 P4" "P2 N5 N2 P1 P4 N8 P7 P6 N1 P5 N4 P8 N3 P3 N7 N6"; do
  r=$((r+1))
  for cell in $order; do
    st=${cell%?}; th=${cell#?}
    if [ $st = P ]; then cap=300; else cap=120; fi
    run r49-$r-$cell $th $st 32 $cap
  done
  echo "$(date +%s) round $r done" >> $log
done
echo R49_DONE >> $log
