# r47 extra heat: four memory-copy loops (dd from /dev/zero to /dev/null, 32 MB blocks) that load DRAM as decode
# does. Kills them the second any pause device reads non-zero, when chain47 logs "heat off", after 25 minutes, or
# if chain47 is gone.
cd /data/local/tmp/sd
D=""; for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-*) D="$D $c";; esac; done
anyon() { for c in $D; do [ "$(cat $c/cur_state)" != 0 ] && return 0; done; return 1; }
ps=""; for k in 1 2 3 4; do (while :; do dd if=/dev/zero of=/dev/null bs=32M count=64 2>/dev/null; done) & ps="$ps $!"; done
echo "$(date +%s) heat2 on: 4 dd loops" >> chain47.log
t0=$(date +%s); why=""
while [ -z "$why" ]; do
  sleep 1
  if anyon; then why="pause on"; elif grep -q "heat off" chain47.log; then why="chain heat off"; elif ! pgrep -f "sh chain47.sh" > /dev/null; then why="chain gone"; elif [ $(( $(date +%s) - t0 )) -gt 1500 ]; then why="25 minutes"; fi
done
kill -9 $ps 2>/dev/null; pkill -9 -f "dd if=/dev/zero" 2>/dev/null
echo "$(date +%s) heat2 off: $why" >> chain47.log
