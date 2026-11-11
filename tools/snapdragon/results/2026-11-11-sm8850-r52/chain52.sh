# r52 on QDC sa966003 (unit 45b9a8ad, SM8850 Snapdragon 8 Elite Gen 5, Android 16): the thread test at a
# realistic length. r27 and r49 timed threads with the pause set by hand on a 19-token prompt and up to 64
# tokens of reply; r34's realistic run (1,751-token prompt) timed only 4 and 2 threads. Question, set before any
# result: at a realistic length, what share of prompt reading and of decode does each thread count keep when the
# hard throttle's pause takes CPUs 2 to 7 out of use?
# Work: ThreadCountBenchmark, Qwen3 4B Q4_0 (the study's file), the 1,751-token prompt-r30 (one user message),
# up to 400 tokens of reply (the study's median reply is 401), an 8,192-token window, the guard off. Threads 1, 2,
# 3, 4, 6 and 8. Three arms per count:
#   N: no pause. Prompt reading and decode normally.
#   D: no pause while the prompt is read; the pause goes on at the first decode step and holds; the run is stopped
#      300 s after the pause went on. Decode under the pause at about 1,750 tokens of context.
#   R: the pause goes on as the run starts its prompt (its START line) and holds; up to 2 tokens; the run is
#      stopped 450 s after the pause went on. Prompt reading under the pause, from the engine's per-chunk progress
#      (512-token chunks); a count that finishes no chunk in 450 s gives only an upper bound.
# Two rounds, each the 18 cells in an order shuffled once before any run (random.Random("r52-<round>")), a cooling
# gate before every run. Pause = all three thermal-pause switches the trip sets (two -3C and one -C0), written by
# hand. The chain also logs "CHAIN pause on|off <label>" into logcat so the pause is timed to the millisecond.
# After Codex's review: R sets and verifies the pause before its run starts (so prompt reading never begins
# unpaused, and model load is paused too); every switch write must read back or the chain stops; rates come from the
# engine's own clocks in its progress lines (prefillMs per completed 512-token chunk, decodeMs); R's cap is 600 s;
# the chain stops launching runs 4 h 30 min after it starts; round 2 runs 1 to 4 threads first, then 6 and 8, so a
# short reservation cuts those first. Scope: a long, uncached first prompt, not a cached follow-up turn.
cd /data/local/tmp/sd
log=chain52.log; G=/sys/class/kgsl/kgsl-3d0; PKG=io.github.alpharomercoma.openweights.core.engine.test; T=/sys/kernel/tracing
SW=""; for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-3C|thermal-pause-C0) SW="$SW $c";; esac; done
[ $(echo $SW | wc -w) = 3 ] || { echo "STOPPED expected three pause switches, found:$SW" >> $log; exit 1; }
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
[ -n "$xoz" ] && [ -n "$zs" ] || { echo "STOPPED thermal zones not found" >> $log; exit 1; }
for c in $SW; do case $(cat $c/type) in thermal-pause-3C|thermal-pause-C0) ;; *) echo "STOPPED unexpected switch $c" >> $log; exit 1;; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
sw() { s=""; for c in $SW; do s="$s$(cat $c/cur_state)"; done; echo $s; }
set_sw() { for c in $SW; do echo $1 > $c/cur_state; done; [ "$(sw)" = "$1$1$1" ] || { echo "$(date +%s) write $1 read back $(sw)" >> $log; return 1; }; }
clear_sw() { if [ $(cat $xoz/temp) -lt 70000 ]; then set_sw 0; else echo "$(date +%s) left switches $(sw): XO $(cat $xoz/temp)" >> $log; fi; }
pb=""; lc=""; wd=""; pl=""; tog=""
reap() { [ -n "$1" ] && { pkill -9 -P $1 2>/dev/null; kill -9 $1 2>/dev/null; wait $1 2>/dev/null; }; }
cleanup() { reap "$pl"; reap "$wd"; reap "$tog"; [ -n "$pb" ] && kill -9 $pb 2>/dev/null; (am force-stop $PKG &); clear_sw; [ -n "$lc" ] && kill $lc 2>/dev/null; }
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
topped() { for x in /sys/devices/system/cpu/cpufreq/policy*; do [ "$(cat $x/scaling_max_freq)" = "$(cat $x/cpuinfo_max_freq)" ] || return 1; done; return 0; }
gate() {
  w=0
  while [ $w -lt 1800 ]; do
    [ $(hot) -le 45000 ] && [ $(cat $xoz/temp) -le 45000 ] && [ "$(cat $G/thermal_pwrlevel)" = 0 ] && [ "$(sw)" = 000 ] && topped && { echo "passed $w"; return; }
    sleep 5; w=$((w+5))
  done
  echo "timed_out $w"
}
# Waits, while the run lives, for its first logcat line after epoch $1 matching $2.
wait_line() { while kill -0 $pb 2>/dev/null && ! logcat -d -v epoch -s OpenWeightsBench:I -T "$1.000" 2>/dev/null | grep -q "$2"; do sleep 1; done; }
# Stops the run: the instrumentation is force-stopped and its waiting `am instrument` killed.
stop_run() { echo "$(date +%s) $label stopped at its cap ($1)" >> $log; (am force-stop $PKG &); sleep 5; kill -9 $pb 2>/dev/null; }
# Every second while the test process lives: the switches, then each thread's id, last CPU and CPU ticks.
place() {
  p=""; n=0; while [ -z "$p" ] && [ $n -lt 60 ]; do p=$(pidof $PKG); sleep 1; n=$((n+1)); done
  [ -n "$p" ] || return
  while kill -0 $p 2>/dev/null; do
    l="$(date +%s) sw $(sw) xo $(cat $xoz/temp) hot $(hot) cap $(cat /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq)/$(cat /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq)"
    for t in /proc/$p/task/*; do s=$(cat $t/stat 2>/dev/null) || continue; set -- ${s#*) }; l="$l ${t##*/}:${37}:$((${12}+${13}))"; done
    echo "$l" >> place-$label.txt; sleep 1
  done
}
run() {
  label=$1; t=$2; arm=$3
  w=$(gate)
  case $w in passed*) ;; *) echo "STOPPED at $label gate $w" >> $log; exit 1;; esac
  case $arm in R) mt=2;; *) mt=400;; esac
  if [ $arm = R ]; then
    set_sw 1 || { echo "STOPPED $label: pause write failed" >> $log; exit 1; }
    log -p i -t OpenWeightsBench "CHAIN pause on $label"; echo "$(date +%s) $label pause on" >> $log
  fi
  t0=$(date +%s); tog=""; rm -f stop52
  am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ThreadCountBenchmark \
    -e threads $t -e maxTokens $mt -e contextLength 8192 -e stallGuard 0 \
    -e model /data/local/tmp/openweights/model.gguf -e promptFile /data/local/tmp/openweights/prompt-r30.txt \
    $PKG/androidx.test.runner.AndroidJUnitRunner > inst-$label.txt 2>&1 &
  pb=$!
  echo "$t0 run $label threads $t arm $arm gate $w hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
  case $arm in
    D) (trap - EXIT INT TERM HUP; wait_line $t0 "reading=false"; kill -0 $pb 2>/dev/null || exit 0
        set_sw 1 || { echo "$(date +%s) $label pause write failed" >> $log; touch stop52; (am force-stop $PKG &); kill -9 $pb 2>/dev/null; exit 0; }
        log -p i -t OpenWeightsBench "CHAIN pause on $label"; echo "$(date +%s) $label pause on" >> $log
        sleep 300; kill -0 $pb 2>/dev/null && stop_run "300 s of decode in the pause") & tog=$!;;
    R) (trap - EXIT INT TERM HUP; sleep 600; kill -0 $pb 2>/dev/null && stop_run "600 s of the run in the pause") & tog=$!;;
  esac
  (trap - EXIT INT TERM HUP; place) & pl=$!
  # A backstop for every arm: no run longer than 25 minutes.
  ( trap - EXIT INT TERM HUP; sleep 1500; kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog stopped $label" >> $log; (am force-stop $PKG &); sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  reap "$pl"; pl=""; reap "$wd"; wd=""; reap "$tog"; tog=""
  am force-stop $PKG
  if [ "$(sw)" != 000 ]; then
    clear_sw
    if [ "$(sw)" = 000 ]; then log -p i -t OpenWeightsBench "CHAIN pause off $label"; echo "$(date +%s) $label pause off" >> $log
    else echo "$(date +%s) $label pause left on: switches $(sw), XO $(cat $xoz/temp)" >> $log; fi
  fi
  [ -f stop52 ] && { echo "STOPPED $label: pause write failed during the run" >> $log; exit 1; }
  echo "$(date +%s) run $label end rc $rc hot $(hot) xo $(cat $xoz/temp) switches $(sw) result $(grep -c 'OK (1 test)' inst-$label.txt)" >> $log
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) model $(sha256sum /data/local/tmp/openweights/model.gguf | cut -c1-16) apk $(pm path $PKG | cut -d: -f2 | xargs sha256sum | cut -c1-16) switches $SW" >> $log
# Before any run, check that the three switches hold six busy loops to CPU 0 and 1, or stop.
pcheck() {
  pids=""; for k in 1 2 3 4 5 6; do (trap - EXIT INT TERM HUP; end=$(( $(date +%s) + 6 )); while [ $(date +%s) -lt $end ]; do :; done) & pids="$pids $!"; done
  sleep 3; c=""
  for i in 1 2 3 4 5 6 7 8 9 10; do for p in $pids; do s=$(cat /proc/$p/stat 2>/dev/null) && { set -- ${s#*) }; c="$c ${37}"; }; done; sleep 0.2; done
  wait; echo $c | tr ' ' '\n' | sort | uniq -c | tr -s ' ' | tr '\n' ','
}
(sh guard52.sh $$ > /dev/null 2>&1 &)
set_sw 1 || { echo "STOPPED pause check: switches not set" >> $log; exit 1; }
on=$(pcheck); set_sw 0 || { echo "STOPPED pause check: switches not cleared" >> $log; exit 1; }; off=$(pcheck)
[ $(echo "$on" | tr ',' '\n' | awk '{s+=$1} END{print s+0}') -ge 20 ] || { echo "STOPPED pause check: too few samples {$on}" >> $log; exit 1; }
echo "$(date +%s) pause check: on {$on} off {$off}" >> $log
for k in 2 3 4 5 6 7; do case ",$on" in *" $k,"*) echo "STOPPED pause check: a loop ran on CPU $k with the switches on" >> $log; exit 1;; esac; done
logcat -v epoch -s OpenWeightsBench:I OpenWeights:I >> logcat-r52.txt 2>&1 & lc=$!
deadline=$(( $(date +%s) + 16200 ))
r=0
for order in 1:D,8:R,3:R,4:D,6:D,8:D,3:N,2:R,2:D,6:R,4:R,8:N,4:N,1:N,3:D,2:N,1:R,6:N 3:R,1:R,2:N,3:D,1:D,2:D,4:D,1:N,4:N,3:N,2:R,4:R,8:N,8:R,6:R,6:N,6:D,8:D; do
  r=$((r+1))
  for cell in $(echo $order | tr ',' ' '); do
    th=${cell%%:*}; arm=${cell##*:}
    [ $(date +%s) -lt $deadline ] || { echo "$(date +%s) deadline: r52-$r-$arm$th and the rest not run" >> $log; break 2; }
    run r52-$r-$arm$th $th $arm
  done
  echo "$(date +%s) round $r done" >> $log
done
echo R52_DONE >> $log
