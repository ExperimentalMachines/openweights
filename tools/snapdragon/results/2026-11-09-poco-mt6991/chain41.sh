# r41 on the owner's POCO X8 Pro Max (MediaTek MT6991, HyperOS, Android 16), on USB power, the morning after
# r37: what the stall guard's fallback costs on this chip, and whether chat with no pause between turns
# throttles it. Part 1: ThreadCountBenchmark with the study's Qwen3 4B Q4_0 and prompt-r30.txt, up to
# 1,024 tokens, an 8,192-token window, the guard on, at 4 threads (the app's default) and 2 (the guard's
# fallback) in the order 4 2 2 4, fixed before any result, each after a gate (the thermal service's CPU
# temperature at or under 42 C, up to 300 s) and 60 s idle. Part 2, r41-S: SessionBenchmark as r39-S
# ran it on the SM8850, no pause between turns, 45 minutes or the conversations' 34 user turns. Screen held
# on while plugged in at a fixed brightness; logger.sh samples the gauge and temperatures into bat41.txt.
# The phone is the owner's: the moment it is unplugged the chain stops and puts the settings back.
cd /data/local/tmp/ow36
log=chain41.log; PKG=io.github.alpharomercoma.openweights.core.engine.test
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
(sh logger.sh $$ bat41.txt > /dev/null 2>&1 &)
logcat -v epoch -s OpenWeightsBench:I OpenWeights:I >> logcat-r41.txt 2>&1 & lc=$!
plugged() { [ "$(dumpsys battery get usb)" = true ] || [ "$(dumpsys battery get ac)" = true ]; }
# Stops the chain when the owner unplugs the phone to take it.
( while kill -0 $$ 2>/dev/null; do plugged || { echo "$(date +%s) STOPPED unplugged by the owner" >> $log; am force-stop $PKG; kill $$; break; }; sleep 5; done ) &
gate() { w=0; while [ $w -lt 300 ] && [ "$(cpu_t)" -gt 42 ]; do sleep 5; w=$((w+5)); done; echo $w; }
watchrun() { # $1 label, $2 watchdog seconds
  pb=$!
  ( sleep $2; kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog stopped $1" >> $log; (am force-stop $PKG &); sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null; wd=""
  echo "$(date +%s) run $1 end rc $rc cpu $(cpu_t) result $(grep -c 'OK (1 test)' inst-$1.txt)" >> $log
}
i=0
for t in 4 2 2 4; do
  i=$((i+1)); label=r41-$i-N$t
  w=$(gate); echo "$(date +%s) idle $label gate $w cpu $(cpu_t)" >> $log; sleep 60
  echo "$(date +%s) run $label threads $t cpu $(cpu_t)" >> $log
  am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ThreadCountBenchmark \
    -e threads $t -e maxTokens 1024 -e contextLength 8192 -e model $M -e promptFile $D/prompt-r30.txt \
    $PKG/androidx.test.runner.AndroidJUnitRunner > inst-$label.txt 2>&1 &
  watchrun $label 900
done
w=$(gate); echo "$(date +%s) idle r41-S gate $w cpu $(cpu_t)" >> $log; sleep 60
echo "$(date +%s) run r41-S session threads 4 think 0 minutes 45 cpu $(cpu_t)" >> $log
am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.SessionBenchmark \
  -e conversations $D/conversations.jsonl -e model $M -e threads 4 -e maxTokens 512 -e thinkSeconds 0 \
  -e minutes 45 -e contextLength 8192 $PKG/androidx.test.runner.AndroidJUnitRunner > inst-r41-S.txt 2>&1 &
watchrun r41-S 3600
sleep 60
echo R41_DONE >> $log
