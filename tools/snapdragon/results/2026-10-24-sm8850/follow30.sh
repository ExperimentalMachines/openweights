# Waits for r29 to end, then installs the engine test build with the stall guard (engine-test2.apk) and
# r30's prompt, and runs r30. Installing compiles the APK, so it waits rather than load the phone mid-probe.
cd /data/local/tmp/sd
log=follow30.log
echo "$(date +%s) follow30 waiting" >> $log
while ! grep -q -E "R29_DONE|STOPPED" chain29.log 2>/dev/null; do sleep 15; done
echo "$(date +%s) r29 ended: $(tail -1 chain29.log)" >> $log
pm install -r -t /data/local/tmp/sd/engine-test2.apk >> $log 2>&1
cp prompt-r30.txt /data/local/tmp/openweights/prompt-r30.txt && chmod 644 /data/local/tmp/openweights/prompt-r30.txt
echo "$(date +%s) installed $(pm path io.github.alpharomercoma.openweights.core.engine.test | cut -d: -f2 | xargs sha256sum | cut -c1-16)" >> $log
sh chain30.sh; echo "$(date +%s) chain30 exit $?" >> $log
