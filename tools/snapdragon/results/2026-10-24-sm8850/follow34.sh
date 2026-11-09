# Phone D (sa963265), in the order of what matters most if the reservation ends early: install the build
# with both device tests and the stall guard (engine-test3.apk), then r34 block 1 (the guard), r33 session A
# (ordinary use), r31 (the barrier), r34 block 2, r33 session B (heavy use).
cd /data/local/tmp/sd
log=follow34.log
echo "$(date +%s) follow34 start" >> $log
pm install -r -t /data/local/tmp/sd/engine-test3.apk >> $log 2>&1
cp conversations.jsonl /data/local/tmp/openweights/ && chmod 644 /data/local/tmp/openweights/conversations.jsonl
echo "$(date +%s) installed $(pm path io.github.alpharomercoma.openweights.core.engine.test | cut -d: -f2 | xargs sha256sum | cut -c1-16)" >> $log
sh chain34.sh 1; echo "$(date +%s) chain34 1 exit $?" >> $log
sh chain33.sh A; echo "$(date +%s) chain33 A exit $?" >> $log
sh chain31.sh; echo "$(date +%s) chain31 exit $?" >> $log
sh chain34.sh 2; echo "$(date +%s) chain34 2 exit $?" >> $log
sh chain33.sh B; echo "$(date +%s) chain33 B exit $?" >> $log
echo "$(date +%s) follow34 done" >> $log
