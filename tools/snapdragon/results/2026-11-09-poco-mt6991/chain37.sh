# r37 on the owner's POCO X8 Pro Max (MediaTek MT6991, HyperOS, Android 16), on USB power: the stall
# guard on a phone with no Snapdragon core pause, through half an hour of paced chat. Any fallback here
# is a false one (or a stall of another kind), which matters because the guard ships to every phone.
# Charging adds heat, so this is the warmer case, not a user's typical one; r36, unplugged, waits for the
# owner. SessionBenchmark as r33 and r36-S ran it: the study's Qwen3 4B Q4_0, 4 threads, up to 512 tokens
# a reply, 30 s between turns, 30 minutes, an 8,192-token window, the guard as shipped. After a gate (the
# thermal service's CPU temperature at or under 42 C, up to 300 s) and 60 s idle. Screen held on while
# plugged in, at a fixed brightness; logger.sh samples the gauge and temperatures into bat37.txt.
cd /data/local/tmp/ow36
log=chain37.log; PKG=io.github.alpharomercoma.openweights.core.engine.test
M=/data/local/tmp/openweights/Qwen3-4B-Q4_0.gguf; D=/data/local/tmp/openweights
old_to=$(settings get system screen_off_timeout); old_mode=$(settings get system screen_brightness_mode); old_b=$(settings get system screen_brightness); old_on=$(settings get global stay_on_while_plugged_in)
cpu_t() { dumpsys thermalservice | sed -n '/Current temperatures from HAL/,/Current cooling/p' | grep -m1 'mName=CPU,' | sed 's/.*mValue=\([0-9]*\).*/\1/'; }
pb=""; lc=""; wd=""
cleanup() {
  [ -n "$wd" ] && kill -9 $wd 2>/dev/null; [ -n "$pb" ] && kill -9 $pb 2>/dev/null; (am force-stop $PKG &)
  [ -n "$lc" ] && kill $lc 2>/dev/null
  settings put system screen_off_timeout $old_to; settings put system screen_brightness_mode $old_mode; settings put system screen_brightness $old_b
  settings put global stay_on_while_plugged_in $old_on
  cmd deviceidle whitelist -$PKG > /dev/null 2>&1
  echo "$(date +%s) restored timeout $old_to brightness mode $old_mode value $old_b stay-on $old_on" >> $log
}
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) build $(getprop ro.build.fingerprint | cut -d/ -f5) model $(sha256sum $M | cut -c1-16) apk $(pm path $PKG | cut -d: -f2 | xargs sha256sum | cut -c1-16) timeout $old_to brightness $old_mode/$old_b stay-on $old_on" >> $log
cmd deviceidle whitelist +$PKG > /dev/null; am set-standby-bucket $PKG active; cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow
settings put global stay_on_while_plugged_in 7; settings put system screen_off_timeout 7200000
settings put system screen_brightness_mode 0; settings put system screen_brightness $old_b; input keyevent KEYCODE_WAKEUP
(sh logger.sh $$ bat37.txt > /dev/null 2>&1 &)
logcat -v epoch -s OpenWeightsBench:I OpenWeights:I >> logcat-r37.txt 2>&1 & lc=$!
w=0; while [ $w -lt 300 ] && [ "$(cpu_t)" -gt 42 ]; do sleep 5; w=$((w+5)); done
echo "$(date +%s) idle r37-S gate $w cpu $(cpu_t)" >> $log
sleep 60
echo "$(date +%s) run r37-S session threads 4 think 30 minutes 30 cpu $(cpu_t) usb $(dumpsys battery get usb)" >> $log
am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.SessionBenchmark \
  -e conversations $D/conversations.jsonl -e model $M -e threads 4 -e maxTokens 512 -e thinkSeconds 30 \
  -e minutes 30 -e contextLength 8192 $PKG/androidx.test.runner.AndroidJUnitRunner > inst-r37-S.txt 2>&1 &
pb=$!
( sleep 2700; kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog stopped r37-S" >> $log; (am force-stop $PKG &); sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
wait $pb; rc=$?; pb=""
kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null; wd=""
echo "$(date +%s) run r37-S end rc $rc cpu $(cpu_t) usb $(dumpsys battery get usb) result $(grep -c 'OK (1 test)' inst-r37-S.txt)" >> $log
sleep 60
echo R37_DONE >> $log
