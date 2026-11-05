#!/bin/zsh
# The compiled model's Tokyo row again, with the replaced reply's cost on the next turn measured in the same chat.
unset ADB_SERVER_SOCKET
S=$QDC_SCRATCH; cd $S; SER=emulator-5554; A() { adb -s $SER "$@"; }
R=io.github.alpharomercoma.openweights; M=/sdcard/Android/data/$R/files/models; O=$S/emu-dead-after; ANDROID=$(sed -n 's/sdk.dir=//p' ~/mobile-inference/local.properties)
A emu kill >/dev/null 2>&1; perl -e 'select(undef,undef,undef,8)'
nohup $ANDROID/emulator/emulator -avd es-16k -no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect -dns-server 127.0.0.1 > $S/emu-dead.log 2>&1 &
disown; T0=$(date +%s); until [ "$(A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] || [ $(( $(date +%s)-T0 )) -gt 200 ]; do perl -e 'select(undef,undef,undef,4)'; done; perl -e 'select(undef,undef,undef,20)'
A shell "settings put system screen_off_timeout 1800000; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; logcat -c; am force-stop $R; am start -W -n $R/$R.MainActivity --es $R.extra.OPEN_MODEL $M/LFM2.5-1.2B-Instruct-8da4w-gptq-4k.pte | tail -1; sleep 60; true" 2>&1 | grep -v 'Broken pipe' | cut -c1-30
for i in 1 2; do r=$(NEW=1 MAXWAIT=400 ./ask-emu.sh d "What is the weather in Tokyo today" 2>&1); print -r -- "$r" > $O/tail-tokyo-$i.txt; print -r -- "$r" | grep -E "^'[A-Z]" | grep -vE 'What is|tok/s|Message|LFM|CPU' | cut -c1-200 | head -2; done
r=$(NEW=1 MAXWAIT=400 ./ask-emu.sh d "What is the exchange rate for 500 USD to yen now" 2>&1); print -r -- "$r" > $O/tail-rate.txt; print -r -- "$r" | grep -E "No search engine" | cut -c1-110
A shell "logcat -c"
r=$(NEW=0 MAXWAIT=400 ./ask-emu.sh d "Say hello in one word" 2>&1); print -r -- "$r" > $O/tail-next-turn.txt; print -r -- "$r" | grep -E '=== |tok/s|conversation|No search|[Hh]ello' | cut -c1-170
A shell "logcat -d -s OpenWeights:* | grep -E 'kv:|et:|prompt|fed|reset|pass offered' | tail -8 | cut -c30-150; echo crash-lines \$(logcat -b crash -d | grep -c $R); true" 2>&1 | cut -c1-130
A exec-out screencap -p > $O/tail-next-turn.png
echo TAIL_DONE $(date +%T)
