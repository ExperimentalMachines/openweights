# r48 (not for the record): about once a second, the time, the XO and the hottest CPU zone in milli-C, every pause
# device's state, then the CPUs online, the CPUs isolated, and each cluster's current and maximum frequency in MHz.
cd /data/local/tmp/sd
x=""; zs=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in sys-therm-0) x=$z;; cpu-0-*|cpu-1-*) zs="$zs $z";; esac; done
d=""; n=""
for c in /sys/class/thermal/cooling_device*; do t=$(cat $c/type); case $t in thermal-pause-*) d="$d $c"; n="$n $t@${c##*/}";; esac; done
P=$(ls -d /sys/devices/system/cpu/cpufreq/policy*)
echo "# session $(date +%s)" >> trig48.txt
echo "time xo cpu$n online isolated $(for p in $P; do echo -n "${p##*/}:cur/max "; done)" >> trig48.txt
while true; do
  h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done
  l="$(date +%s) $(cat $x/temp) $h"; for c in $d; do l="$l $(cat $c/cur_state)"; done
  l="$l $(cat /sys/devices/system/cpu/online) $(cat /sys/devices/system/cpu/isolated 2>/dev/null || echo -)"
  for p in $P; do l="$l $(( $(cat $p/scaling_cur_freq) / 1000 ))/$(( $(cat $p/scaling_max_freq) / 1000 ))"; done
  echo "$l" >> trig48.txt; sleep 1
done
