#!/bin/zsh
# usage: dead-emu.sh <apk or -> <out dir>. Search cannot reach anything but the network still reads as up: what a captive portal or a rate limit looks like to the app.
unset ADB_SERVER_SOCKET
S=$QDC_SCRATCH; cd $S
APK=$1; O=$S/$2; mkdir -p $O; SER=emulator-5554; A() { adb -s $SER "$@"; }
R=io.github.alpharomercoma.openweights; M=/sdcard/Android/data/$R/files/models
ANDROID=$(sed -n 's/sdk.dir=//p' ~/mobile-inference/local.properties)
A shell "settings put global captive_portal_mode 0" 2>/dev/null
A emu kill >/dev/null 2>&1; perl -e 'select(undef,undef,undef,8)'
nohup $ANDROID/emulator/emulator -avd es-16k -no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect -dns-server 127.0.0.1 > $S/emu-dead.log 2>&1 &
disown; T0=$(date +%s); until [ "$(A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] || [ $(( $(date +%s)-T0 )) -gt 200 ]; do perl -e 'select(undef,undef,undef,4)'; done; perl -e 'select(undef,undef,undef,20)'
[ "$APK" != - ] && A install -r $APK 2>&1 | tail -1 | cut -c1-40
A shell "settings put global captive_portal_mode 0; settings put system screen_off_timeout 1800000; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; logcat -c; logcat -b crash -c; ping -c1 -W3 search.yahoo.com 2>&1 | head -1 | cut -c1-60; dumpsys connectivity | grep -m1 'Active default' | cut -c1-40; dumpsys package $R | grep -m1 versionCode | cut -c1-24"
QS=("What is the weather in Tokyo today" "What is the exchange rate for 500 USD to yen now" "What is the price of Bitcoin today" "What is the capital of France")
for m in LFM2.5-1.2B-Instruct-8da4w-gptq-4k.pte LFM2.5-1.2B-Instruct-Q4_K_M.gguf LFM3-1.7B-Q4_K_M.gguf; do
  echo "##### $m"
  A shell "am force-stop $R; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; am start -W -n $R/$R.MainActivity --es $R.extra.OPEN_MODEL $M/$m | tail -1; sleep 60; true" 2>&1 | grep -v 'Broken pipe' | cut -c1-40
  i=0
  for q in $QS; do
    i=$((i+1)); T0=$(date +%s); tag=${m%.*}-$i
    r=$(NEW=1 MAXWAIT=${WAIT:-300} ./ask-emu.sh d "$q" 2>&1); print -r -- "$r" > $O/$tag.txt; A exec-out screencap -p > $O/$tag.png
    if print -r -- "$r" | grep -qE 'Show what it returned|Searched|Opened'; then s=SEARCHED; else s=none; fi
    echo "$s $(( $(date +%s)-T0 ))s | ${q:0:36} | $(print -r -- "$r" | grep -vE "What is the|ctx|Choose a model|CPU|LFM|Message|This conversation|Show|↑|=== |control: |tok/s" | head -1 | cut -c2-260)"
  done
  A shell "logcat -d -s OpenWeights:* | grep -E 'search intent|denial repair|pass offered|generation failed' | cut -c30-110 | tail -8; echo crash-lines \$(logcat -b crash -d | grep -c $R); true" 2>&1 | cut -c1-110
done
A shell "logcat -d -s OpenWeights:*" > $O/openweights-tag.log 2>/dev/null
echo DEAD_DONE $(date +%T)
