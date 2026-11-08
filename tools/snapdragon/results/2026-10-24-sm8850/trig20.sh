# Every 2 s: time, XO thermistor (sys-therm-0, milli-C), the GPU's clock and thermal power level, and the
# CPU pause, GPU cap (kgsl) and cDSP cooling states. One file for the ladder and the thread test after it.
cd /data/local/tmp/sd
z=""; for t in /sys/class/thermal/thermal_zone*; do [ "$(cat $t/type)" = sys-therm-0 ] && z=$t; done
d=""; for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-C0|thermal-pause-3C|kgsl|cdsp) d="$d $c";; esac; done
g=/sys/class/kgsl/kgsl-3d0
echo "time sys-therm-0 gpuclk thermal_pwrlevel $(for c in $d; do echo -n "$(cat $c/type) "; done)" > trig-r20.txt
while true; do
  l="$(date +%s) $(cat $z/temp) $(cat $g/gpuclk) $(cat $g/thermal_pwrlevel)"
  for c in $d; do l="$l $(cat $c/cur_state)"; done
  echo "$l" >> trig-r20.txt
  sleep 2
done
