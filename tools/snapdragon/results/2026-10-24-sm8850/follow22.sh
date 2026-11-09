# Waits for r22's fourth cell to end (one balanced block, 6 2 2 6), then stops chain22.sh in its gate
# before the fifth (every shell running chain22.sh, killed by PID; no benchmark runs during a gate), installs
# the engine's test APK and copies the model to the test's path, runs r27, then r22's second block. If
# chain22.sh stops or finishes on its own first, it still runs r27. Logs to follow22.log.
cd /data/local/tmp/sd
log=follow22.log
echo "$(date +%s) follow22 waiting" >> $log
while ! grep -q -E "end r22-4-t6|STOPPED|CHAIN_DONE" chain22.log 2>/dev/null; do sleep 15; done
sleep 2
for p in $(ps -A -o PID,ARGS | awk '$2 == "sh" && $3 == "chain22.sh" {print $1}'); do kill -9 $p; done
pidof pd_bench_ref > /dev/null && echo "$(date +%s) WARNING pd_bench still running" >> $log
echo "$(date +%s) chain22 stopped after: $(tail -1 chain22.log)" >> $log
pm install -r -t /data/local/tmp/sd/engine-test.apk >> $log 2>&1
mkdir -p /data/local/tmp/openweights && cp gguf/Qwen3-4B-Q4_0.gguf /data/local/tmp/openweights/model.gguf
chmod 755 /data/local/tmp/openweights; chmod 644 /data/local/tmp/openweights/model.gguf
echo "$(date +%s) installed $(pm path io.github.alpharomercoma.openweights.core.engine.test) model $(ls -la /data/local/tmp/openweights/model.gguf | awk '{print $5}')" >> $log
sh chain27.sh; echo "$(date +%s) chain27 exit $?" >> $log
grep -q "end r22-4-t6" chain22.log && { sh chain22b.sh; echo "$(date +%s) chain22b exit $?" >> $log; }
echo "$(date +%s) follow22 done" >> $log
