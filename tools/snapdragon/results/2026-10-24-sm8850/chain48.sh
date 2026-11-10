# r48, exploratory and not for the record (r47 again, guard-on first in the pause). Does reaching the XO trip (78 C) switch the pause on by itself, and does
# decode throughput fall in that natural pause? 1) Baseline: ThreadCountBenchmark, 4 threads, the built-in short
# prompt, up to 256 tokens, guard off (B0) then on (B1). 2) Heat: eight busy loops and the screen at full
# brightness until any of the eleven pause devices reads non-zero by itself, or 40 minutes, or the XO passes 82 C
# with none on. 3) The moment one is on: stop the loops and run the same work, guard off (P0) then on (P1).
# trig48.sh logs the XO, the hottest CPU zone and every pause device about once a second throughout.
cd /data/local/tmp/sd
log=chain48.log; PKG=io.github.alpharomercoma.openweights.core.engine.test
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
[ -n "$xoz" ] || { echo "STOPPED no sys-therm-0" >> $log; exit 1; }
D=""; for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-*) D="$D $c";; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
sw() { s=""; for c in $D; do s="$s$(cat $c/cur_state)"; done; echo $s; }
anyon() { for c in $D; do [ "$(cat $c/cur_state)" != 0 ] && return 0; done; return 1; }
loops=""; pb=""; wd=""; lc=""; bright=$(settings get system screen_brightness)
stoploops() { [ -n "$loops" ] && kill -9 $loops 2>/dev/null; pkill -9 -f "dd if=/dev/zero" 2>/dev/null; loops=""; }
cleanup() { stoploops; pkill -f "sh trig48.sh"; svc power stayon false; settings put system screen_brightness $bright; [ -n "$wd" ] && kill -9 $wd 2>/dev/null; [ -n "$pb" ] && kill -9 $pb 2>/dev/null; (am force-stop $PKG &); [ -n "$lc" ] && kill $lc 2>/dev/null; }
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
run() {
  label=$1; g=$2; cap=$3
  echo "$(date +%s) run $label guard $g hot $(hot) xo $(cat $xoz/temp) switches $(sw)" >> $log
  am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ThreadCountBenchmark \
    -e threads 4 -e maxTokens 256 -e contextLength 2048 -e stallGuard $g \
    -e model /data/local/tmp/openweights/model.gguf \
    $PKG/androidx.test.runner.AndroidJUnitRunner > inst-$label.txt 2>&1 &
  pb=$!
  ( sleep $cap; kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog stopped $label" >> $log; (am force-stop $PKG &); sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null; wd=""
  echo "$(date +%s) run $label end rc $rc hot $(hot) xo $(cat $xoz/temp) switches $(sw) result $(grep -c 'OK (1 test)' inst-$label.txt)" >> $log
}
(sh guard48.sh $$ > /dev/null 2>&1 &)
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) devices$D" >> $log
logcat -v epoch -s OpenWeightsBench:I OpenWeights:I >> logcat-r48.txt 2>&1 & lc=$!
(sh trig48.sh > /dev/null 2>&1 &)
svc power stayon true; input keyevent KEYCODE_WAKEUP
settings put system screen_brightness 255
for k in 1 2 3 4 5 6 7 8; do (while :; do :; done) & loops="$loops $!"; done
for k in 1 2 3 4 5 6 7 8; do (while :; do dd if=/dev/zero of=/dev/null bs=32M count=64 2>/dev/null; done) & loops="$loops $!"; done
echo "$(date +%s) heat on: 8 loops, 8 dd loops, brightness 255, xo $(cat $xoz/temp) hot $(hot)" >> $log
t0=$(date +%s); why=""
while [ -z "$why" ]; do
  sleep 1
  now=$(date +%s); x=$(cat $xoz/temp)
  if anyon; then why="pause on"; elif [ $x -gt 82000 ]; then why="xo over 82 with no pause"; elif [ $((now - t0)) -gt 2400 ]; then why="40 minutes"; fi
  [ $(( (now - t0) % 60 )) -lt 1 ] && echo "$now heating xo $x hot $(hot) switches $(sw)" >> $log
done
stoploops; settings put system screen_brightness 128
echo "$(date +%s) heat off: $why, xo $(cat $xoz/temp) hot $(hot) switches $(sw)" >> $log
run r48-P1 1 300
anyon && run r48-P0 0 150
echo R47_DONE >> $log
