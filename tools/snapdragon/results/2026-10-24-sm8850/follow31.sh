# Phone D (sa963265): once the model is on the phone, install the engine test build with the stall guard,
# copy the model to the test's path, then run r32 (the guard at a realistic length) and r31 (the barrier).
cd /data/local/tmp/sd
log=follow31.log
echo "$(date +%s) follow31 waiting for the model" >> $log
while ! grep -q DL_DONE dl19.log 2>/dev/null; do sleep 10; done
echo "$(date +%s) model: $(grep Qwen3 dl19.log)" >> $log
pm install -r -t /data/local/tmp/sd/engine-test2.apk >> $log 2>&1
cp gguf/Qwen3-4B-Q4_0.gguf /data/local/tmp/openweights/model.gguf
chmod 755 /data/local/tmp/openweights; chmod 644 /data/local/tmp/openweights/model.gguf
echo "$(date +%s) installed $(pm path io.github.alpharomercoma.openweights.core.engine.test | cut -d: -f2 | xargs sha256sum | cut -c1-16)" >> $log
sh chain32.sh; echo "$(date +%s) chain32 exit $?" >> $log
sh chain31.sh; echo "$(date +%s) chain31 exit $?" >> $log
echo "$(date +%s) follow31 done" >> $log
