#!/bin/zsh
# usage: NEW=1 MAXWAIT=200 ask.sh <id> <prompt>. Finds controls by label because this phone's
# screen differs from the POCO's. Exits as soon as the phone stops answering.
export ADB_SERVER_SOCKET=tcp:127.0.0.1:5097
S=$QDC_SCRATCH
n="$1"; shift; enc=$(printf '%s' "$*" | sed 's/ /%s/g')
dump() { adb shell uiautomator dump /data/local/tmp/ui.xml 2>/dev/null | grep -q dumped && adb shell cat /data/local/tmp/ui.xml > "$S/u$n.xml"; }
tap() { dump; p=$(python3 -I "$S/ui.py" "$S/u$n.xml" "$1" ${2:-}); [ -n "$p" ] || { echo "no control: $1"; return 1; }; adb shell input tap ${=p}; }
adb shell true 2>/dev/null || { echo "PHONE GONE"; exit 9; }
adb shell input keyevent KEYCODE_WAKEUP
[ "${NEW:-0}" = 1 ] && { tap "New chat"; perl -e 'select(undef,undef,undef,2)'; }
tap "Message, or type / for commands" || tap "Message" sub || exit 3
perl -e 'select(undef,undef,undef,1)'
adb shell "input text \"$enc\""; perl -e 'select(undef,undef,undef,1)'
# A slow phone is still typing after input text returns: send only once the whole question is in the box.
typed=0; for w in {1..40}; do dump; if python3 -I "$S/ui.py" "$S/u$n.xml" --list 400 | grep -qF "$*"; then typed=1; break; fi; perl -e 'select(undef,undef,undef,2)'; done
# A question that never went must not stay in the box and be sent with the next one.
[ $typed = 1 ] || { for i in {1..90}; do adb shell input keyevent KEYCODE_DEL; done; echo "NOT TYPED"; exit 4; }
adb shell input keyevent KEYCODE_BACK; perl -e 'select(undef,undef,undef,1)'
tap "Send message" || exit 3
D=$(date +%s)
while [ $(( $(date +%s)-D )) -lt ${MAXWAIT:-200} ]; do
  perl -e 'select(undef,undef,undef,4)'
  adb shell true 2>/dev/null || { echo "PHONE GONE"; exit 9; }
  dump
  r=$(python3 -I "$S/ui.py" "$S/u$n.xml" "Run")
  if [ -n "$r" ]; then echo "approved a tool"; adb shell input tap ${=r}; continue; fi
  grep -q 'Copy reply' "$S/u$n.xml" && grep -q 'Send message' "$S/u$n.xml" && [ $(( $(date +%s)-D )) -ge 6 ] && break
done
adb exec-out screencap -p > "$S/t$n.png"
echo "=== $n ($(( $(date +%s)-D )) s): $*"
python3 -I "$S/ui.py" "$S/u$n.xml" --list 340 | grep -vE "Attach a file|Dictate|Past chats|New chat|Model settings|Copy reply|Read aloud|More actions|'Send message'|for commands"
