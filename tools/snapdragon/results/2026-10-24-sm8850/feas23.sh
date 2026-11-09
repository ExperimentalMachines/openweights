# Does a pause set by hand hold, idle and under load? Writes 1 to both CPU pause switches on a cool
# phone and reads them, the isolated-CPU list and the XO every second for 30 s, with a 6-thread busy
# load on for the middle 10 s; then writes 0, checked. Before r23.
cd /data/local/tmp/sd
a=/sys/class/thermal/cooling_device24; b=/sys/class/thermal/cooling_device30
[ "$(cat $a/type) $(cat $b/type)" = "thermal-pause-3C thermal-pause-C0" ] || { echo "wrong switches" > feas23.txt; exit 1; }
x=""; for z in /sys/class/thermal/thermal_zone*; do [ "$(cat $z/type)" = sys-therm-0 ] && x=$z/temp; done
off() { echo 0 > $a/cur_state; echo 0 > $b/cur_state; }
trap off EXIT INT TERM HUP
echo "start $(date +%s) xo $(cat $x)" > feas23.txt
echo 1 > $a/cur_state; echo 1 > $b/cur_state
for i in $(seq 1 30); do
  [ $i = 10 ] && for k in 1 2 3 4 5 6; do (end=$(( $(date +%s) + 10 )); while [ $(date +%s) -lt $end ]; do :; done) & done
  echo "$(date +%s) $(cat $a/cur_state)$(cat $b/cur_state) isolated=$(cat /sys/devices/system/cpu/isolated) xo=$(cat $x) cpus=$(for p in $(pidof sh); do grep -h Cpus_allowed_list /proc/$p/status 2>/dev/null | cut -f2; done | sort -u | tr '\n' ' ')" >> feas23.txt
  sleep 1
done
wait
off
echo "after $(cat $a/cur_state)$(cat $b/cur_state)" >> feas23.txt
