# Independent of chain47: once its shell is gone, kill any busy loop it left and stop the test.
cd /data/local/tmp/sd
sleep 20
while kill -0 $1 2>/dev/null; do sleep 10; done
# The busy loops are forked subshells of chain47 and carry its command line.
pkill -9 -f "sh chain47.sh" 2>/dev/null
am force-stop io.github.alpharomercoma.openweights.core.engine.test
svc power stayon false
echo "$(date +%s) guard47: chain gone" >> chain47.log
exit 0
