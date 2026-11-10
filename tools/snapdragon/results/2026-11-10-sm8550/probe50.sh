# r50 probe on QDC sa964839 (unit 57fa7c29, SM8550): does this phone's own hard-throttle pause
# (thermal-pause-F8, CPUs 3 to 7, what xo-therm sets at 78 C) stall the engine's 4 decode threads on
# the three little cores left? ThreadCountBenchmark's built-in 19-token prompt, 32 tokens, guard off.
# First a busy-loop check that F8 holds loops to CPUs 0 to 2. Order fixed: N4 P4 P2.
cd /data/local/tmp/sd
log=probe50.log; PKG=io.github.alpharomercoma.openweights.core.engine.test
F=""; for c in /sys/class/thermal/cooling_device*; do [ "$(cat $c/type)" = thermal-pause-F8 ] && F=$c; done
[ -n "$F" ] || { echo "STOPPED no thermal-pause-F8" >> $log; exit 1; }
trap 'echo 0 > $F/cur_state' EXIT
pcheck() {
  pids=""; for k in 1 2 3 4 5 6; do (end=$(( $(date +%s) + 6 )); while [ $(date +%s) -lt $end ]; do :; done) & pids="$pids $!"; done
  sleep 3; c=""
  for i in 1 2 3 4 5 6 7 8 9 10; do for p in $pids; do s=$(cat /proc/$p/stat 2>/dev/null) && { set -- ${s#*) }; c="$c ${37}"; }; done; sleep 0.2; done
  wait; echo $c | tr ' ' '\n' | sort | uniq -c | tr -s ' ' | tr '\n' ','
}
echo 1 > $F/cur_state; on=$(pcheck); echo 0 > $F/cur_state; off=$(pcheck)
echo "$(date +%s) pause check F8: on {$on} off {$off}" >> $log
logcat -c; logcat -v epoch -s OpenWeightsBench:I OpenWeights:I > logcat-probe50.txt 2>&1 & lc=$!
for arm in N4 P4 P2; do
  t=${arm#?}; case $arm in P*) echo 1 > $F/cur_state;; *) echo 0 > $F/cur_state;; esac
  echo "$(date +%s) run $arm F8 $(cat $F/cur_state)" >> $log
  am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ThreadCountBenchmark \
    -e threads $t -e maxTokens 32 -e stallGuard 0 -e model /data/local/tmp/openweights/model.gguf \
    $PKG/androidx.test.runner.AndroidJUnitRunner > inst-probe50-$arm.txt 2>&1 &
  pb=$!; ( sleep 240; kill -0 $pb 2>/dev/null && { echo "$(date +%s) watchdog $arm" >> $log; am force-stop $PKG; } ) & wd=$!
  wait $pb; kill $wd 2>/dev/null
  echo 0 > $F/cur_state
  echo "$(date +%s) end $arm F8 $(cat $F/cur_state) ok $(grep -c 'OK (1 test)' inst-probe50-$arm.txt)" >> $log
  sleep 20
done
kill $lc
echo PROBE_DONE >> $log
