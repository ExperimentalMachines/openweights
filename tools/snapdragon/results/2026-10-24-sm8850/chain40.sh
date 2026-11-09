# r40 on QDC sa963265 (unit 4816891, phone D), after r39, engine-test5 (416d8c86). Does an hour of chat with
# no pause between turns reach the hard throttle, and what does the guard do in a natural pause? r39-S ran
# out of the conversations' 34 user turns at 30.6 minutes with the XO at 76.4 C against the trip at 78.
# So two such sessions back to back: r40-S1 after r34's gate, r40-S2 at once after it, without a gate (the
# phone as hot as one left it), each SessionBenchmark with no pause between turns, 4 threads, up to 512
# tokens a reply, the guard on, screen on at a middle brightness. trig39.sh logs all eleven pause devices
# every second; place() logs where each thread ran. No pause is set by hand. Labels r40-S1, r40-S2.
cd /data/local/tmp/sd
log=chain40.log; G=/sys/class/kgsl/kgsl-3d0; PKG=io.github.alpharomercoma.openweights.core.engine.test
P1=""; P2=""
for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-3C) P1=$c;; thermal-pause-C0) P2=$c;; esac; done
[ -n "$P1" ] && [ -n "$P2" ] || { echo "STOPPED pause switches not found" >> $log; exit 1; }
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
sw() { echo "$(cat $P1/cur_state)$(cat $P2/cur_state)"; }
set_sw() { echo $1 > $P1/cur_state; echo $1 > $P2/cur_state; [ "$(sw)" = "$1$1" ] || { echo "$(date +%s) write $1 read back $(sw)" >> $log; return 1; }; }
clear_sw() { if [ $(cat $xoz/temp) -lt 70000 ]; then set_sw 0; else echo "$(date +%s) left switches $(sw): XO $(cat $xoz/temp)" >> $log; fi; }
pb=""; lc=""; wd=""; pl=""; tg=""; bright=$(settings get system screen_brightness)
cleanup() { [ -n "$tg" ] && kill $tg 2>/dev/null; svc power stayon false; settings put system screen_brightness $bright; [ -n "$pl" ] && kill -9 $pl 2>/dev/null; [ -n "$wd" ] && kill -9 $wd 2>/dev/null; [ -n "$pb" ] && kill -9 $pb 2>/dev/null; (am force-stop $PKG &); clear_sw; [ -n "$lc" ] && kill $lc 2>/dev/null; }
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
# Returns once this run's decode has begun: its first progress line with reading=false since t0.
watch_decode() {
  while ! logcat -d -v epoch -s OpenWeightsBench:I -T "$1.000" 2>/dev/null | grep -q "reading=false"; do sleep 1; done
}
# Every 2 s while the test process lives: each thread's id, the CPU it last ran on, its user and system ticks.
place() {
  p=""; n=0; while [ -z "$p" ] && [ $n -lt 60 ]; do p=$(pidof $PKG); sleep 1; n=$((n+1)); done
  [ -n "$p" ] || return
  while kill -0 $p 2>/dev/null; do
    l="$(date +%s) sw $(sw)"
    for t in /proc/$p/task/*; do s=$(cat $t/stat 2>/dev/null) || continue; set -- ${s#*) }; l="$l ${t##*/}:${37}:$((${12}+${13}))"; done
    echo "$l" >> place-$label.txt; sleep 2
  done
}
run() {
  label=$1; arm=$2
  t=${arm%%-*}; t=${t#?}; g=${arm##*-}; [ $g = G ] && gv=1 || gv=0
  w=$(gate)
  case $w in passed*) ;; *) echo "STOPPED at $label gate $w" >> $log; exit 1;; esac
  tog=""; t0=$(date +%s)
  case $arm in
    W*) (watch_decode $t0; sleep 10; set_sw 1; echo "$(date +%s) $label pause on" >> $log; sleep 30; clear_sw; echo "$(date +%s) $label pause off" >> $log) & tog=$!;;
    M*) (watch_decode $t0; sleep 10; set_sw 1; echo "$(date +%s) $label pause on" >> $log) & tog=$!;;
  esac
  echo "$(date +%s) run $label arm $arm threads $t guard $gv gate $w hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
  am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ThreadCountBenchmark \
    -e threads $t -e maxTokens 1024 -e contextLength 8192 -e stallGuard $gv \
    -e model /data/local/tmp/openweights/model.gguf -e promptFile /data/local/tmp/openweights/prompt-r30.txt \
    $PKG/androidx.test.runner.AndroidJUnitRunner > inst-$label.txt 2>&1 &
  pb=$!
  (place) & pl=$!
  ( sleep 420; kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog stopped $label" >> $log; (am force-stop $PKG &); sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $pl 2>/dev/null; wait $pl 2>/dev/null; pl=""
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
(sh guard40.sh $$ > /dev/null 2>&1 &)
logcat -v epoch -s OpenWeightsBench:I OpenWeights:I >> logcat-r40.txt 2>&1 & lc=$!
(sh trig39.sh > /dev/null 2>&1 &) ; tg=$(sleep 1; pgrep -f "sh trig39.sh" | head -1)
session() {
  label=$1
  echo "$(date +%s) session $label think 0 minutes 45 hot $(hot) xo $(cat $xoz/temp) switches $(sw) screen $(dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*')" >> $log
  am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.SessionBenchmark \
    -e conversations /data/local/tmp/openweights/conversations.jsonl -e model /data/local/tmp/openweights/model.gguf \
    -e threads 4 -e maxTokens 512 -e thinkSeconds 0 -e minutes 45 -e contextLength 8192 \
    $PKG/androidx.test.runner.AndroidJUnitRunner > inst-$label.txt 2>&1 &
  pb=$!
  (place) & pl=$!
  ( sleep 3600; kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog stopped $label" >> $log; (am force-stop $PKG &); sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $pl $wd 2>/dev/null; wait $pl $wd 2>/dev/null; pl=""; wd=""
  echo "$(date +%s) session $label end rc $rc hot $(hot) xo $(cat $xoz/temp) switches $(sw) result $(grep -c 'OK (1 test)' inst-$label.txt)" >> $log
}
(sh trig39.sh > /dev/null 2>&1 &)
g=$(gate); case $g in passed*) ;; *) echo "STOPPED at r40-S1 gate $g" >> $log; exit 1;; esac
echo "$(date +%s) gate $g" >> $log
svc power stayon true; input keyevent KEYCODE_WAKEUP; settings put system screen_brightness 128
label=r40-S1; session r40-S1
label=r40-S2; session r40-S2
svc power stayon false; settings put system screen_brightness $bright
echo R40_DONE >> $log
