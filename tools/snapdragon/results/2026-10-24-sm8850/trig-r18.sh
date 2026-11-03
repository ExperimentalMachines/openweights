# Every 2 s: the time, the XO thermistor (sys-therm-0) in milli-C, and the four hard-throttle switches.
cd /data/local/tmp/sd
z=""; for t in /sys/class/thermal/thermal_zone*; do [ "$(cat $t/type)" = sys-therm-0 ] && z=$t; done
d=""; for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-C0|thermal-pause-3C|kgsl|cdsp) d="$d $c";; esac; done
echo "time sys-therm-0 $(for c in $d; do echo -n "$(cat $c/type) "; done)" > trig-r18.txt
while true; do
  l="$(date +%s) $(cat $z/temp)"
  for c in $d; do l="$l $(cat $c/cur_state)"; done
  echo "$l" >> trig-r18.txt
  sleep 2
done
