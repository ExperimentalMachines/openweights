# r39: every 1 s, the time and the state of each CPU pause device, all eleven on this unit (trig31.sh reads three).
cd /data/local/tmp/sd
d=""; n=""
for c in /sys/class/thermal/cooling_device*; do t=$(cat $c/type); case $t in thermal-pause-*) d="$d $c"; n="$n $t@${c##*/}";; esac; done
echo "# session $(date +%s)" >> pause39.txt
echo "time$n" >> pause39.txt
while true; do l="$(date +%s)"; for c in $d; do l="$l $(cat $c/cur_state)"; done; echo "$l" >> pause39.txt; sleep 1; done
