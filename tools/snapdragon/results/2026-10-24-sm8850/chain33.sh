# r33 on QDC sa963265 (unit 4816891, phone D): does ordinary use reach the hard throttle? The engine's
# SessionBenchmark (engine-test5.apk, installed below: the guard as fixed after the second review, one loaded
# engine for the whole session) chats through the study's nine conversations turn by turn, each reply
# the model's own and kept in the history (up to 512 tokens a reply), with a reading pause before each next
# message: session A 30 s for 40 minutes, then session B 5 s for 30 minutes. 4 threads (the app's default),
# the stall guard on, as the app would ship it. The screen is kept on at a middle brightness for both, as a
# user's would be; it is restored after. Each session starts after r23's gate (XO and hottest CPU and GPU
# zone at or under 45 C); trig31.sh logs the XO thermistor, the pause switches and the caps every second.
# Logcat for the test's and the engine's tags only. No pause is set by hand. Labels r33-A, r33-B.
cd /data/local/tmp/sd
log=chain33.log; G=/sys/class/kgsl/kgsl-3d0; PKG=io.github.alpharomercoma.openweights.core.engine.test
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
gate() { w=0; while [ $w -lt 1800 ]; do [ $(hot) -le 45000 ] && [ $(cat $xoz/temp) -le 45000 ] && [ "$(cat $G/thermal_pwrlevel)" = 0 ] && { echo "passed $w"; return; }; sleep 5; w=$((w+5)); done; echo "timed_out $w"; }
bright=$(settings get system screen_brightness)
pb=""; lc=""
cleanup() { [ -n "$pb" ] && kill -9 $pb 2>/dev/null; (am force-stop $PKG &); svc power stayon false; settings put system screen_brightness $bright; [ -n "$lc" ] && kill $lc 2>/dev/null; }
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
session() {
  label=$1; think=$2; minutes=$3
  g=$(gate); case $g in passed*) ;; *) echo "STOPPED at $label gate $g" >> $log; exit 1;; esac
  svc power stayon true; input keyevent KEYCODE_WAKEUP; settings put system screen_brightness 128
  echo "$(date +%s) session $label think $think minutes $minutes gate $g hot $(hot) xo $(cat $xoz/temp) screen $(dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*')" >> $log
  am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.SessionBenchmark \
    -e conversations /data/local/tmp/openweights/conversations.jsonl -e model /data/local/tmp/openweights/model.gguf \
    -e threads 4 -e maxTokens 512 -e thinkSeconds $think -e minutes $minutes -e contextLength 8192 \
    $PKG/androidx.test.runner.AndroidJUnitRunner > inst-$label.txt 2>&1 &
  pb=$!
  ( sleep $(( minutes * 60 + 900 )); kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog stopped $label" >> $log; (am force-stop $PKG &); sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null
  svc power stayon false
  echo "$(date +%s) session $label end rc $rc hot $(hot) xo $(cat $xoz/temp) result $(grep -c 'OK (1 test)' inst-$label.txt)" >> $log
}
# The guard's second review came after r34 block 1 had started on engine-test4; from here on, engine-test5.
[ "$(pm path $PKG | cut -d: -f2 | xargs sha256sum | cut -c1-16)" = "$(sha256sum engine-test5.apk | cut -c1-16)" ] || pm install -r -t /data/local/tmp/sd/engine-test5.apk >> $log 2>&1
echo "$(date +%T) unit $(getprop ro.serialno) apk $(pm path $PKG | cut -d: -f2 | xargs sha256sum | cut -c1-16) brightness was $bright" >> $log
logcat -v epoch -s OpenWeightsBench:I OpenWeights:I >> logcat-r33.txt 2>&1 & lc=$!
# `chain33.sh A` or `B` runs one session, so the two can sit apart in the queue.
case $1 in
  A) session r33-A 30 40;;
  B) session r33-B 5 30;;
  *) echo "STOPPED: session A or B" >> $log; exit 1;;
esac
echo R33_${1}_DONE >> $log
