# Does thermal-pause-F8 written 1 by hand stay 1 for 90 s under load on this phone? Reads it every 2 s
# with four busy loops running, then clears it.
cd /data/local/tmp/sd
F=""; for c in /sys/class/thermal/cooling_device*; do [ "$(cat $c/type)" = thermal-pause-F8 ] && F=$c; done
trap 'echo 0 > $F/cur_state' EXIT
pids=""; for k in 1 2 3 4; do (end=$(( $(date +%s) + 92 )); while [ $(date +%s) -lt $end ]; do :; done) & pids="$pids $!"; done
echo 1 > $F/cur_state
s=""; for i in $(seq 1 45); do s="$s$(cat $F/cur_state)"; sleep 2; done
echo "$(date +%s) F8 held: $s xo $(cat /sys/class/thermal/thermal_zone99/temp)" > hold50.log
wait
