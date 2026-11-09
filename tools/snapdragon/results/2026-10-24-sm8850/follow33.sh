# After follow31 (r32 then r31), installs the build with SessionBenchmark (engine-test3.apk), puts the
# study's conversations where the test reads them, and runs r33.
cd /data/local/tmp/sd
log=follow33.log
echo "$(date +%s) follow33 waiting" >> $log
while ! grep -q "follow31 done" follow31.log 2>/dev/null; do sleep 15; done
pm install -r -t /data/local/tmp/sd/engine-test3.apk >> $log 2>&1
cp conversations.jsonl /data/local/tmp/openweights/ && chmod 644 /data/local/tmp/openweights/conversations.jsonl
echo "$(date +%s) installed $(pm path io.github.alpharomercoma.openweights.core.engine.test | cut -d: -f2 | xargs sha256sum | cut -c1-16)" >> $log
sh chain33.sh; echo "$(date +%s) chain33 exit $?" >> $log
echo "$(date +%s) follow33 done" >> $log
