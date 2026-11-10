# Independent of chain48: once its shell is gone, kill any busy loop it left and stop the test.
cd /data/local/tmp/sd
sleep 20
while kill -0 $1 2>/dev/null; do sleep 10; done
# The busy loops are forked subshells of chain48 and carry its command line.
pkill -9 -f "sh chain48.sh" 2>/dev/null
pkill -9 -f "dd if=/dev/zero" 2>/dev/null
am force-stop io.github.alpharomercoma.openweights.core.engine.test
svc power stayon false
echo "$(date +%s) guard48: chain gone" >> chain48.log
exit 0
