#!/bin/zsh
# The signed release through its real UI: everyday questions on the three models. Emulator only, by serial.
unset ADB_SERVER_SOCKET
S=$QDC_SCRATCH; cd $S
R=io.github.alpharomercoma.openweights; M=/sdcard/Android/data/$R/files/models; O=$S/${OUT:-emu-regress}; mkdir -p $O
SER=emulator-5554; A() { adb -s $SER "$@"; }
ST=$S/hub-pte; PTE=LFM2.5-1.2B-Instruct-8da4w-gptq-4k.pte; TOK=LFM2.5-1.2B-Instruct-8da4w-gptq-4k.tokenizer.json; L3=LFM3-1.7B-Q4_K_M.gguf; G=LFM2.5-1.2B-Instruct-Q4_K_M.gguf
A shell "settings put global captive_portal_mode 0; settings put system screen_off_timeout 1800000; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; logcat -c; logcat -b crash -c" 2>&1 | cut -c1-70
A push $ST/$PTE $M/ 2>&1 | tail -1 | cut -c1-90
A push $ST/$TOK $M/ 2>&1 | tail -1 | cut -c1-90
A push $S/../lfm3/$L3 $M/ 2>&1 | tail -1 | cut -c1-90
A shell "chmod 666 $M/*; ls $M | cut -c1-60"
QS=("What is the weather in Tokyo today" "What is the weather in Manila today" "What is the exchange rate for 500 USD to yen now" "What is the price of Bitcoin today" "What is the capital of France" "Who wrote Pride and Prejudice")
for m in $PTE $G $L3; do
  echo "##### $m"
  A shell "am force-stop $R; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; am start -W -n $R/$R.MainActivity --es $R.extra.OPEN_MODEL $M/$m | tail -1; sleep 60; true" 2>&1 | grep -v 'Broken pipe' | cut -c1-60
  i=0
  for q in $QS; do
    i=$((i+1)); T0=$(date +%s); tag=${m%.*}-$i
    r=$(NEW=1 MAXWAIT=${WAIT:-300} ./ask-emu.sh r "$q" 2>&1); print -r -- "$r" > $O/$tag.txt
    if print -r -- "$r" | grep -qE 'Show what it returned|Searched|Opened'; then s=SEARCHED; else s=none; fi
    cp $S/ur.xml $O/$tag.xml 2>/dev/null
    A exec-out screencap -p > $O/$tag.png
    # Open the search result as well, so a reply that says it could not read a figure can be checked against what it was given.
    if [ $s = SEARCHED ]; then
      p=$(python3 -I $S/ui.py $S/ur.xml "Show what it returned" sub)
      if [ -n "$p" ]; then A shell input tap ${=p}; perl -e 'select(undef,undef,undef,2)'; A shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1; A shell cat /data/local/tmp/ui.xml > $O/$tag-result.xml; fi
    fi
    echo "$s $(( $(date +%s)-T0 ))s | ${q:0:40} | $(print -r -- "$r" | grep -vE "What is the|Who wrote|ctx|Choose a model|CPU|LFM|Message|This conversation|Show|↑|=== |control: |tok/s" | head -1 | cut -c2-170)"
  done
  A shell "logcat -d -s OpenWeights:* | grep -E 'search intent|generation failed|refus|lament|abort' | cut -c30-130 | tail -6; echo crash-lines \$(logcat -b crash -d | grep -c $R); true" 2>&1 | cut -c1-120
done
A shell "logcat -d -s OpenWeights:*" > $O/openweights-tag.log 2>/dev/null
echo EVERYDAY_DONE $(date +%T)
