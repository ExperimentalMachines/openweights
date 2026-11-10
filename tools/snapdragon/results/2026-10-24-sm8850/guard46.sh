# Independent of chain46: once the chain46 shell whose PID it is given is gone, stop the test package and put the
# screen back, so a session left running does not keep the phone busy. r46 sets no pause switch by hand, so there
# is nothing else to restore. Logs to chain46.log.
cd /data/local/tmp/sd
sleep 20
while kill -0 $1 2>/dev/null; do sleep 10; done
am force-stop io.github.alpharomercoma.openweights.core.engine.test
svc power stayon false
echo "$(date +%s) guard46: chain gone, test stopped" >> chain46.log
exit 0
