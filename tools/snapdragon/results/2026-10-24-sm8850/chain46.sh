# r46 on QDC sa964425 (unit 64a112da), engine test APK built from 62439b56, the guard's final rules. Does chat
# with no pause between turns, for longer than r39-S's 30.6 minutes, reach the hard throttle, and what does the
# final guard do in a natural pause? r40 meant to answer this and its reservation ended 22 minutes in. Three
# SessionBenchmark runs back to back: r46-S1 after a cooling gate, r46-S2 and r46-S3 at once after it, without a
# gate. Each runs the study's nine conversations with no pause between turns, 4 threads, up to 512 tokens a
# reply, the guard on, screen on at a middle brightness. trig46.sh logs the XO, the hottest CPU zone and every
# pause device each second; place() logs where each thread ran. No pause switch is set by hand.
cd /data/local/tmp/sd
log=chain46.log; G=/sys/class/kgsl/kgsl-3d0; PKG=io.github.alpharomercoma.openweights.core.engine.test
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
[ -n "$xoz" ] || { echo "STOPPED no sys-therm-0" >> $log; exit 1; }
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
sw() { s=""; for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-3C|thermal-pause-C0) s="$s$(cat $c/cur_state)";; esac; done; echo $s; }
pb=""; lc=""; wd=""; pl=""; bright=$(settings get system screen_brightness)
cleanup() { pkill -f "sh trig46.sh"; svc power stayon false; settings put system screen_brightness $bright; [ -n "$pl" ] && kill -9 $pl 2>/dev/null; [ -n "$wd" ] && kill -9 $wd 2>/dev/null; [ -n "$pb" ] && kill -9 $pb 2>/dev/null; (am force-stop $PKG &); [ -n "$lc" ] && kill $lc 2>/dev/null; }
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
topped() { for x in /sys/devices/system/cpu/cpufreq/policy*; do [ "$(cat $x/scaling_max_freq)" = "$(cat $x/cpuinfo_max_freq)" ] || return 1; done; return 0; }
gate() {
  w=0
  while [ $w -lt 1800 ]; do
    [ $(hot) -le 45000 ] && [ $(cat $xoz/temp) -le 45000 ] && [ "$(cat $G/thermal_pwrlevel)" = 0 ] && topped && { echo "passed $w"; return; }
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
    echo "$l" >> place-$label.txt; sleep 2
  done
}
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
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) model $(sha256sum /data/local/tmp/openweights/model.gguf | cut -c1-16) conv $(sha256sum /data/local/tmp/openweights/conversations.jsonl | cut -c1-16) apk $(pm path $PKG | cut -d: -f2 | xargs sha256sum | cut -c1-16)" >> $log
(sh guard46.sh $$ > /dev/null 2>&1 &)
logcat -v epoch -s OpenWeightsBench:I OpenWeights:I >> logcat-r46.txt 2>&1 & lc=$!
(sh trig46.sh > /dev/null 2>&1 &)
g=$(gate); case $g in passed*) ;; *) echo "STOPPED at r46-S1 gate $g" >> $log; exit 1;; esac
echo "$(date +%s) gate $g" >> $log
svc power stayon true; input keyevent KEYCODE_WAKEUP; settings put system screen_brightness 128
session r46-S1
session r46-S2
session r46-S3
echo R46_DONE >> $log
