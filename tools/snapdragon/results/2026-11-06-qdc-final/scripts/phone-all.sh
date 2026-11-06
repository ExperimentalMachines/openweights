#!/bin/zsh
# usage: phone-all.sh <reservation>. Tunnel, signed install, verified models, then both runs. Stops at the first sign the phone is gone.
SA=$1; S=$QDC_SCRATCH; cd $S
export ADB_SERVER_SOCKET=tcp:127.0.0.1:5097; D=/data/local/tmp/sd; R=io.github.alpharomercoma.openweights; M=/sdcard/Android/data/$R/files/models
perl -e 'alarm 15; exec @ARGV' adb devices 2>/dev/null | grep -qw device || { echo NO_TUNNEL; exit 8; }
A() { perl -e 'alarm 120; exec @ARGV' adb shell "$@"; }
A "getprop ro.soc.model; getprop ro.build.version.release; mkdir -p $D" | tr '\n' ' '; echo
HAVE=$(A "sha256sum $D/app.apk 2>/dev/null | cut -c1-16" | tr -d '\r'); [ "$HAVE" = 85a75abab964ae86 ] || perl -e 'alarm 400; exec @ARGV' adb push $S/release/app-final-731.apk $D/app.apk 2>&1 | tail -1 | sed 's|.*qdc/||' | cut -c1-80
[ "$(A "sha256sum $D/app.apk | cut -c1-16" | tr -d '\r')" = 85a75abab964ae86 ] || { echo BAD_APK; exit 7; }
A "pm install -r -d --user 0 $D/app.apk 2>&1 | tail -1; dumpsys package $R | grep -m1 versionCode | cut -c1-24"
adb push $S/fetch-731.sh $D/fetch.sh >/dev/null 2>&1; A "chmod 755 $D/fetch.sh; rm -f $D/fetch.log; ls $M 2>/dev/null | grep -c . ; (nohup $D/fetch.sh >/dev/null 2>&1 &); sleep 1; echo fetching"
T0=$(date +%s); until A "grep -q FETCH_DONE $D/fetch.log" || [ $(( $(date +%s)-T0 )) -gt 500 ]; do perl -e 'select(undef,undef,undef,8)'; A true || { echo PHONE GONE; exit 9; }; done; A "cut -c1-60 $D/fetch.log"
A "grep -q BAD $D/fetch.log" && { echo BAD_MODEL; exit 6; }
UN=$(A "stat -c %U /data/data/$R" | tr -d '\r'); UI=$(A "stat -c %u /data/data/$R" | tr -d '\r')
A "svc power stayon true; settings put system screen_off_timeout 1800000; settings put global captive_portal_mode 0; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; input swipe 500 1800 500 300; am start -W -n $R/$R.MainActivity | tail -1; sleep 4; am force-stop $R; mkdir -p $M; mv $D/models/* $M/; chown -R ${UN}:ext_data_rw /sdcard/Android/data/$R/files; chmod 2770 $M; chmod 660 $M/*; logcat -b crash -c; true" 2>&1 | grep -v 'Broken pipe' | cut -c1-60
$S/phone-run.sh working || exit $?
A "iptables -I OUTPUT -m owner --uid-owner $UI -j REJECT; ip6tables -I OUTPUT -m owner --uid-owner $UI -j REJECT; echo app-network-blocked"
$S/phone-run.sh dead; rc=$?
A "iptables -D OUTPUT -m owner --uid-owner $UI -j REJECT; ip6tables -D OUTPUT -m owner --uid-owner $UI -j REJECT; echo restored; logcat -b crash -d | grep -c $R; true"
echo PHONE_ALL_DONE rc=$rc $(date +%T)
