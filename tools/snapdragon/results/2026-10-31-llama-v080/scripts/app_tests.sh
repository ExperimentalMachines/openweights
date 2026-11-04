#!/system/bin/sh
# Runs the same engine test classes on each APK in turn, after a cooling wait, keeping the
# instrumentation output and the engine's log lines for each.
cd /data/local/tmp/sd
P=io.github.alpharomercoma.openweights.core.engine.test
C=io.github.alpharomercoma.openweights.core.engine
CLASSES=$C.LlamaCppEngineTest,$C.ToolCallingTest,$C.PrefixReuseOnDeviceTest,$C.ToolLoopReuseOnDeviceTest,$C.PrefillReuseTest,$C.SlidingWindowOnDeviceTest,$C.GpuOffloadTest
hot() { m=0; for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu*|gpu*|nsp*) t=$(cat $z/temp); [ $t -lt 104000 ] && [ $t -gt $m ] && m=$t;; esac; done; echo $m; }
for v in ${ORDER:-base 080}; do
    s=0; while [ $(hot) -gt 55000 ] && [ $s -lt 180 ]; do sleep 5; s=$((s+5)); done
    echo "$(date +%T) $v install (cooled ${s}s, hot $(hot))" >> app.log
    pm install -r -t engine-$v.apk >> app.log 2>&1
    logcat -c
    t0=$(date +%s)
    am instrument -w -r -e class $CLASSES $P/androidx.test.runner.AndroidJUnitRunner > inst-$v.txt 2>&1
    echo "$(date +%T) $v done in $(( $(date +%s) - t0 ))s" >> app.log
    logcat -d > logcat-$v.txt
done
echo APP_DONE >> app.log
