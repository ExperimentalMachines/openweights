# Independent of chain49: once its shell is gone, stop the test, turn tracing off and clear the three pause switches
# if the board is under 70 C (a natural pause stays the phone's own), so a dead chain cannot leave cores out of use.
cd /data/local/tmp/sd
sleep 20
while kill -0 $1 2>/dev/null; do sleep 10; done
echo 0 > /sys/kernel/tracing/tracing_on
am force-stop io.github.alpharomercoma.openweights.core.engine.test
svc power stayon false
x=""; for z in /sys/class/thermal/thermal_zone*; do [ "$(cat $z/type)" = sys-therm-0 ] && x=$z; done
if [ $(cat $x/temp) -lt 70000 ]; then for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-3C|thermal-pause-C0) echo 0 > $c/cur_state;; esac; done; fi
echo "$(date +%s) guard49: chain gone" >> chain49.log
exit 0
