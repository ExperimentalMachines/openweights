# What an unrooted shell can see of the throttle on a streamed Snapdragon phone, every 2 s: epoch,
# online and isolated CPU lists, each CPU policy's current clock and ceiling (whatever clusters this
# chip has: the header names them), the Adreno GPU's clock, ceiling and load where kgsl is readable
# (-1 where not); every 10 s also Android's thermal status and HAL temperatures (AP, SKIN).
#   setsid sh sdlog.sh <out file> > /dev/null 2>&1 < /dev/null &
cd /data/local/tmp/sd
out=${1:-sdlog.txt}
pols=$(ls -d /sys/devices/system/cpu/cpufreq/policy* | sed 's|.*/policy||' | sort -n | tr '\n' ' ')
h="epoch online isolated"; for p in $pols; do h="$h p${p}_cur p${p}_max"; done
echo "$h gpu_cur gpu_max gpu_busy | status AP SKIN" > $out
g=/sys/class/kgsl/kgsl-3d0
rd() { v=$(cat $1 2>/dev/null) && [ -n "$v" ] && echo $v || echo -1; }
i=0
while true; do
  l="$(date +%s) $(cat /sys/devices/system/cpu/online) $(cat /sys/devices/system/cpu/isolated)-"
  for p in $pols; do b=/sys/devices/system/cpu/cpufreq/policy$p; l="$l $(rd $b/scaling_cur_freq) $(rd $b/scaling_max_freq)"; done
  l="$l $(rd $g/gpuclk) $(rd $g/max_gpuclk) $(rd $g/gpu_busy_percentage | tr -d ' %')"
  if [ $((i % 5)) -eq 0 ]; then
    full=$(dumpsys thermalservice)
    d=$(echo "$full" | sed -n '/Current temperatures from HAL/,/Current cooling devices/p')
    s=$(echo "$full" | grep -m1 "Thermal Status" | tr -dc '0-9')
    ap=$(echo "$d" | grep "mName=AP," | sed 's/.*mValue=\([0-9.-]*\).*/\1/')
    sk=$(echo "$d" | grep "mName=SKIN," | sed 's/.*mValue=\([0-9.-]*\).*/\1/')
    l="$l | $s $ap $sk"
  fi
  echo "$l" >> $out
  i=$((i + 1))
  sleep 2
done
