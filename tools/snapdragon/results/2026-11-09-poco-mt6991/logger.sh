# r36 and r37 logger (the file is $2, bat36.txt if none): the battery gauge about once a second (each forced read takes about a second), and every
# tenth sample the thermal service's temperatures and status, the voltage, level and charge counter, the
# plug, the screen, the online cores and the cluster clocks, while the chain whose PID is $1 runs.
cd /data/local/tmp/ow36; out=${2:-bat36.txt}
i=0
while kill -0 $1 2>/dev/null; do
  echo "B $(date +%s.%N | cut -c1-14) $(dumpsys battery get -f current_now)" >> $out
  if [ $((i % 10)) -eq 0 ]; then
    d=$(dumpsys thermalservice)
    th=$(echo "$d" | sed -n '/Current temperatures from HAL/,/Current cooling/p' | grep -E 'mName=(CPU|SKIN|BATTERY),' | sed 's/.*mValue=\([0-9.]*\).*mName=\([A-Z]*\).*/\2=\1/' | tr '\n' ' ')
    st=$(echo "$d" | grep -m1 '^Thermal Status' | tr -dc 0-9)
    fr=""; for p in 0 4 7; do fr="$fr,$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_cur_freq)"; done
    echo "T $(date +%s.%N | cut -c1-14) status=$st ${th}volt=$(dumpsys battery | grep -E '^  voltage:' | tr -dc 0-9) level=$(dumpsys battery get level) ctr=$(dumpsys battery get -f counter) usb=$(dumpsys battery get usb) ac=$(dumpsys battery get ac) screen=$(dumpsys power | grep -m1 mWakefulness= | cut -d= -f2) online=$(cat /sys/devices/system/cpu/online) freq=${fr#,}" >> $out
  fi
  i=$((i+1))
done
