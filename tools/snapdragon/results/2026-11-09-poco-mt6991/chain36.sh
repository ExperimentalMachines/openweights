# r36 on the owner's POCO X8 Pro Max (MediaTek MT6991, HyperOS, Android 16), on battery: what a reply
# costs in energy at the app's 4 decode threads and at the stall guard's 2, then what half an hour of
# paced chat drains and heats on a phone in a room, not a rack. The QDC phones have no working gauge (their
# battery is bypassed and the power rails read zero); this one has. The chain is started on USB power and
# nothing runs until the gauge says the phone was unplugged by hand.
# Energy arms: ThreadCountBenchmark with the study's Qwen3 4B Q4_0 (unsloth/Qwen3-4B-GGUF, 92f4a83c),
# prompt-r30.txt, up to 512 tokens, an 8,192-token window, the guard as shipped (on), in the order
# N4 N2 N2 N4 N4 N2 N2 N4, fixed before any result. Each waits for the thermal service's CPU temperature
# to read 42 C or less (up to 300 s), then idles 60 s, the arm's own baseline. Then r36-S: SessionBenchmark,
# 4 threads, up to 512 tokens a reply, 30 s between turns, 30 minutes, after the same gate. Screen on at a
# fixed brightness throughout; logger36.sh samples the gauge. A run with the phone plugged back in stops.
cd /data/local/tmp/ow36
log=chain36.log; PKG=io.github.alpharomercoma.openweights.core.engine.test
M=/data/local/tmp/openweights/Qwen3-4B-Q4_0.gguf; D=/data/local/tmp/openweights
old_to=$(settings get system screen_off_timeout); old_mode=$(settings get system screen_brightness_mode); old_b=$(settings get system screen_brightness)
cpu_t() { dumpsys thermalservice | sed -n '/Current temperatures from HAL/,/Current cooling/p' | grep -m1 'mName=CPU,' | sed 's/.*mValue=\([0-9]*\).*/\1/'; }
plugged() { [ "$(dumpsys battery get usb)" = true ] || [ "$(dumpsys battery get ac)" = true ]; }
pb=""; lc=""; wd=""
cleanup() {
  [ -n "$wd" ] && kill -9 $wd 2>/dev/null; [ -n "$pb" ] && kill -9 $pb 2>/dev/null; (am force-stop $PKG &)
  [ -n "$lc" ] && kill $lc 2>/dev/null
  settings put system screen_off_timeout $old_to; settings put system screen_brightness_mode $old_mode; settings put system screen_brightness $old_b
  cmd deviceidle whitelist -$PKG > /dev/null 2>&1
  echo "$(date +%s) restored timeout $old_to brightness mode $old_mode value $old_b" >> $log
}
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
gate() { w=0; while [ $w -lt 300 ] && [ "$(cpu_t)" -gt 42 ]; do sleep 5; w=$((w+5)); done; echo $w; }
watch() { # $1 label, $2 seconds before the watchdog stops the run
  pb=$!
  ( sleep $2; kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog stopped $1" >> $log; (am force-stop $PKG &); sleep 10; kill -9 $pb 2>/dev/null; } ) & wd=$!
  wait $pb; rc=$?; pb=""
  kill -9 $wd 2>/dev/null; wait $wd 2>/dev/null; wd=""
  echo "$(date +%s) run $1 end rc $rc cpu $(cpu_t) result $(grep -c 'OK (1 test)' inst-$1.txt)" >> $log
}
run() {
  label=$1; t=$2
  w=$(gate); echo "$(date +%s) idle $label gate $w cpu $(cpu_t)" >> $log
  sleep 60
  plugged && { echo "STOPPED plugged in before $label" >> $log; exit 1; }
  echo "$(date +%s) run $label threads $t cpu $(cpu_t)" >> $log
  am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ThreadCountBenchmark \
    -e threads $t -e maxTokens 512 -e contextLength 8192 -e model $M -e promptFile $D/prompt-r30.txt \
    $PKG/androidx.test.runner.AndroidJUnitRunner > inst-$label.txt 2>&1 &
  watch $label 600
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) build $(getprop ro.build.fingerprint | cut -d/ -f5) model $(sha256sum $M | cut -c1-16) apk $(pm path $PKG | cut -d: -f2 | xargs sha256sum | cut -c1-16) timeout $old_to brightness $old_mode/$old_b" >> $log
cmd deviceidle whitelist +$PKG > /dev/null; am set-standby-bucket $PKG active; cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow
settings put system screen_off_timeout 7200000; settings put system screen_brightness_mode 0; settings put system screen_brightness $old_b
(sh logger36.sh $$ > /dev/null 2>&1 &)
logcat -v epoch -s OpenWeightsBench:I OpenWeights:I >> logcat-r36.txt 2>&1 & lc=$!
# Unplugged means ten reads in a row, 2 s apart: the first start took one stray read (a cable out for
# about 4 s and back) for the unplug and began its first idle on the charger.
w=0; steady=0
while [ $steady -lt 10 ] && [ $w -lt 1800 ]; do if plugged; then steady=0; else steady=$((steady+1)); fi; sleep 2; w=$((w+2)); done
[ $steady -ge 10 ] || { echo "STOPPED not unplugged for 20 s in a row" >> $log; exit 1; }
echo "$(date +%s) unplugged for 20 s, after $w s" >> $log
i=0
for t in 4 2 2 4 4 2 2 4; do i=$((i+1)); run r36-$i-N$t $t; done
w=$(gate); echo "$(date +%s) idle r36-S gate $w cpu $(cpu_t)" >> $log
sleep 60
plugged && { echo "STOPPED plugged in before r36-S" >> $log; exit 1; }
echo "$(date +%s) run r36-S session threads 4 think 30 minutes 30 cpu $(cpu_t)" >> $log
am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.SessionBenchmark \
  -e conversations $D/conversations.jsonl -e model $M -e threads 4 -e maxTokens 512 -e thinkSeconds 30 \
  -e minutes 30 -e contextLength 8192 $PKG/androidx.test.runner.AndroidJUnitRunner > inst-r36-S.txt 2>&1 &
watch r36-S 2700
sleep 60
echo R36_DONE >> $log
