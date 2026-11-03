# What triggers the core pause? The kernel binds thermal-pause-C0, thermal-pause-3C, kgsl and cdsp to one
# shared sensor, sys-therm-0 (trip 78 C, hysteresis 8 C). Every 2 s: the time, sys-therm-0 in milli-C,
# and those four devices' states. Every 30 s once r16 is done (only the thread test runs then, outside
# the matrix): Android's thermal status and its skin reading, the signal an app can read.
cd /data/local/tmp/sd
z=""; for t in /sys/class/thermal/thermal_zone*; do [ "$(cat $t/type)" = sys-therm-0 ] && z=$t; done
d=""; for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-C0|thermal-pause-3C|kgsl|cdsp) d="$d $c";; esac; done
echo "time sys-therm-0 $(for c in $d; do echo -n "$(cat $c/type) "; done)android" > trig17.txt
n=0
while true; do
  l="$(date +%s) $(cat $z/temp)"
  for c in $d; do l="$l $(cat $c/cur_state)"; done
  n=$((n+1))
  if [ $((n % 15)) -eq 0 ] && grep -q CHAIN_DONE chain16.log 2>/dev/null; then
    l="$l $(dumpsys thermalservice | grep -m1 'Thermal Status' | tr -d ' ') $(dumpsys thermalservice | grep -m1 'mName=skin' | sed 's/.*mValue=\([0-9.]*\).*mStatus=\([0-9]\).*/skin=\1,status=\2/')"
  fi
  echo "$l" >> trig17.txt
  sleep 2
done
