# What an unrooted shell can see of the throttle on the streamed MediaTek unit, every 2 s:
# epoch, online and isolated CPU lists, each CPU policy's current clock and ceiling, the GPU's
# clock, ceiling and load; every 10 s also Android's thermal status and HAL temperatures (AP,
# SKIN), which cost about a second to read. Columns are named in the first line.
#   setsid sh mtklog.sh <out file> > /dev/null 2>&1 < /dev/null &
cd /data/local/tmp/mtk
out=${1:-mtklog.txt}
echo "epoch online isolated p0_cur p0_max p4_cur p4_max p7_cur p7_max gpu_cur gpu_max gpu_busy | status AP SKIN" > $out
i=0
while true; do
  l="$(date +%s) $(cat /sys/devices/system/cpu/online) $(cat /sys/devices/system/cpu/isolated)-"
  for p in 0 4 7; do
    b=/sys/devices/system/cpu/cpufreq/policy$p
    l="$l $(cat $b/scaling_cur_freq) $(cat $b/scaling_max_freq)"
  done
  l="$l $(cat /sys/kernel/gpu/gpu_clock) $(cat /sys/kernel/gpu/gpu_max_clock) $(cat /sys/kernel/gpu/gpu_busy | tr -d ' %')"
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
