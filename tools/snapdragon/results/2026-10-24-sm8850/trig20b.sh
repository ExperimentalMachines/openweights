# Every 2 s, each device on its own column: time, XO thermistor and the hottest GPU sensor (milli-C),
# the GPU's operating clock and its allowed ceiling (max_gpuclk, Hz) and thermal power level, each CPU
# pause switch, the kgsl and cDSP cooling states, and each CPU policy's allowed ceiling (kHz).
# Appends with a session line, so a restart loses nothing. Replaces trig20.sh, which lacked the GPU's
# temperature and ceilings and the CPU ceilings.
cd /data/local/tmp/sd
xo=""; gz=""
for t in /sys/class/thermal/thermal_zone*; do case $(cat $t/type) in sys-therm-0) xo=$t;; gpuss-*) gz="$gz $t";; esac; done
d=""; n=""
for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-C0|thermal-pause-3C|kgsl|cdsp) d="$d $c"; n="$n $(cat $c/type)@${c##*/}";; esac; done
g=/sys/class/kgsl/kgsl-3d0
p=$(ls -d /sys/devices/system/cpu/cpufreq/policy*)
echo "# session $(date +%s)" >> trig-r20b.txt
echo "time xo gpu_max_c gpuclk max_gpuclk thermal_pwrlevel$n$(for x in $p; do echo -n " max_${x##*/}"; done)" >> trig-r20b.txt
while true; do
  l="$(date +%s) $(cat $xo/temp)"
  m=0; for z in $gz; do t=$(cat $z/temp); [ $t -gt $m ] && m=$t; done
  l="$l $m $(cat $g/gpuclk) $(cat $g/max_gpuclk) $(cat $g/thermal_pwrlevel)"
  for c in $d; do l="$l $(cat $c/cur_state)"; done
  for x in $p; do l="$l $(cat $x/scaling_max_freq)"; done
  echo "$l" >> trig-r20b.txt
  sleep 2
done
