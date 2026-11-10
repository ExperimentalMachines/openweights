# Stops the test and clears the pause switch if the chain dies.
cd /data/local/tmp/sd
while kill -0 $1 2>/dev/null; do sleep 10; done
am force-stop io.github.alpharomercoma.openweights.core.engine.test
for c in /sys/class/thermal/cooling_device*; do [ "$(cat $c/type)" = thermal-pause-F8 ] && echo 0 > $c/cur_state; done
echo "$(date +%s) guard50: chain gone" >> chain50.log
