# r47 (not for the record): every 1 s, the time, the XO thermistor (sys-therm-0) and the hottest CPU zone in milli-C, then the state of
# every CPU pause device on this unit, named in the header. One logger for the whole chain, so a natural hard
# throttle shows in the same file as the sensor that trips it.
cd /data/local/tmp/sd
x=""; zs=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in sys-therm-0) x=$z;; cpu-0-*|cpu-1-*) zs="$zs $z";; esac; done
d=""; n=""
for c in /sys/class/thermal/cooling_device*; do t=$(cat $c/type); case $t in thermal-pause-*) d="$d $c"; n="$n $t@${c##*/}";; esac; done
echo "# session $(date +%s)" >> trig47.txt
echo "time xo cpu$n" >> trig47.txt
while true; do
  h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done
  l="$(date +%s) $(cat $x/temp) $h"; for c in $d; do l="$l $(cat $c/cur_state)"; done
  echo "$l" >> trig47.txt; sleep 1
done
