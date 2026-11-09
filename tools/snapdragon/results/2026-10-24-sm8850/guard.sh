# Independent of the chain it is given: once that chain's shell is gone, put back what the chains set
# by hand. The CPU pause switches and CPU ceilings only while the XO is under 70 C, so a real thermal
# request is never cleared; while it is hotter, wait. The GPU's hand-set level at once (the thermal
# cap is a separate control). Logs to the chain's log and exits when all is back.
cd /data/local/tmp/sd
c=$1; log=$c.log
a=/sys/class/thermal/cooling_device24; b=/sys/class/thermal/cooling_device30
x=""; for z in /sys/class/thermal/thermal_zone*; do [ "$(cat $z/type)" = sys-therm-0 ] && x=$z/temp; done
sleep 20
while ps -A -o ARGS | grep -q "^sh $c.sh"; do sleep 10; done
echo 0 > /sys/class/kgsl/kgsl-3d0/max_pwrlevel
while [ $(cat $x) -ge 70000 ]; do sleep 10; done
[ "$(cat $a/cur_state)$(cat $b/cur_state)" != 00 ] && { echo 0 > $a/cur_state; echo 0 > $b/cur_state; echo "$(date +%s) guard cleared switches, now $(cat $a/cur_state)$(cat $b/cur_state)" >> $log; }
for p in /sys/devices/system/cpu/cpufreq/policy*; do
  [ "$(cat $p/scaling_max_freq)" != "$(cat $p/cpuinfo_max_freq)" ] && { cat $p/cpuinfo_max_freq > $p/scaling_max_freq; echo "$(date +%s) guard reset ${p##*/} ceiling to $(cat $p/scaling_max_freq)" >> $log; }
done
exit 0
