#!/bin/zsh
# The signed release through its real UI on the phone. usage: phone-run.sh <working|dead>. Stops at the first sign the phone is gone.
export ADB_SERVER_SOCKET=tcp:127.0.0.1:5097
S=$QDC_SCRATCH; cd $S
MODE=$1; O=$S/phone/$MODE; mkdir -p $O
R=io.github.alpharomercoma.openweights; M=/sdcard/Android/data/$R/files/models
A() { perl -e 'alarm 120; exec @ARGV' adb shell "$@"; }
gone() { echo "PHONE GONE $(date +%T)"; exit 9; }
A true || gone
if [ $MODE = working ]; then
  QS=("What is the weather in Tokyo today" "What is the weather in Manila today" "What is the exchange rate for 500 USD to yen now" "What is the price of Bitcoin today" "What is the capital of France" "Who wrote Pride and Prejudice")
else
  QS=("What is the weather in Tokyo today" "What is the exchange rate for 500 USD to yen now" "What is the price of Bitcoin today" "Who won the most recent Formula One race" "What is the capital of France")
fi
for m in LFM2.5-1.2B-Instruct-8da4w-gptq-4k.pte LFM2.5-1.2B-Instruct-Q4_K_M.gguf LFM3-1.7B-Q4_K_M.gguf; do
  echo "##### $m $(date +%T)"
  A "svc power stayon true; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; logcat -c; am force-stop $R; am start -W -n $R/$R.MainActivity --es $R.extra.OPEN_MODEL $M/$m | tail -1" 2>&1 | grep -v 'Broken pipe' | cut -c1-30
  T0=$(date +%s); until A "logcat -d -s OpenWeights:* | grep -q 'warm prefilled'" || [ $(( $(date +%s)-T0 )) -gt 120 ]; do perl -e 'select(undef,undef,undef,3)'; done; echo "warm after $(( $(date +%s)-T0 )) s"
  i=0
  for q in $QS; do
    i=$((i+1)); T0=$(date +%s); tag=${m%.*}-$i
    r=$(NEW=1 MAXWAIT=${WAIT:-300} ./ask-phone.sh p "$q" 2>&1); rc=$?; print -r -- "$r" > $O/$tag.txt; cp $S/up.xml $O/$tag.xml 2>/dev/null
    [ $rc = 9 ] && gone
    perl -e 'alarm 30; exec @ARGV' adb exec-out screencap -p > $O/$tag.png
    if print -r -- "$r" | grep -qE 'Show what it returned|Searched|Opened'; then s=SEARCHED; else s=none; fi
    echo "$s $(( $(date +%s)-T0 ))s | ${q:0:38} | $(python3 -I $S/rows.py $O/$tag.txt | cut -c1-300)"
  done
  A "logcat -d -s OpenWeights:* | grep -E 'search intent|denial repair|generation failed|graph_compute' | cut -c34-110 | tail -6; echo crash-lines \$(logcat -b crash -d | grep -c $R); true" 2>&1 | cut -c1-110
  A "logcat -d -s OpenWeights:*" > $O/${m%.*}-tag.log 2>/dev/null
done
echo PHONE_${MODE}_DONE $(date +%T)
