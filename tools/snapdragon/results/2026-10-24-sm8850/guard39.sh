# Independent of chain39: once the chain39 shell whose PID it is given is gone, put back what it set by hand: the two CPU pause
# switches, found by type (the device numbers differ between units), only while the XO is under 70 C so a
# real thermal request is never cleared; while it is hotter, wait. Stops the test package too, so an
# instrumentation left running does not keep the phone busy. Logs to chain39.log.
cd /data/local/tmp/sd
sw=""; for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-3C|thermal-pause-C0) sw="$sw $c/cur_state";; esac; done
x=""; for z in /sys/class/thermal/thermal_zone*; do [ "$(cat $z/type)" = sys-therm-0 ] && x=$z/temp; done
sleep 20
while kill -0 $1 2>/dev/null; do sleep 10; done
am force-stop io.github.alpharomercoma.openweights.core.engine.test
while [ $(cat $x) -ge 70000 ]; do sleep 10; done
for f in $sw; do [ "$(cat $f)" != 0 ] && { echo 0 > $f; echo "$(date +%s) guard cleared $f, now $(cat $f)" >> chain39.log; }; done
exit 0
