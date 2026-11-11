# If the chain dies: stop the test, then clear every pause switch once the XO is under 70 C (the chain's own rule),
# waiting up to 30 minutes for that.
cd /data/local/tmp/sd
while kill -0 $1 2>/dev/null; do sleep 10; done
am force-stop io.github.alpharomercoma.openweights.core.engine.test
xo=""; for z in /sys/class/thermal/thermal_zone*; do [ "$(cat $z/type)" = sys-therm-0 ] && xo=$z; done
n=0; while [ -n "$xo" ] && [ $(cat $xo/temp) -ge 70000 ] && [ $n -lt 180 ]; do sleep 10; n=$((n+1)); done
for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-3C|thermal-pause-C0) echo 0 > $c/cur_state;; esac; done
echo "$(date +%s) guard52: chain gone, switches cleared" >> chain52.log
