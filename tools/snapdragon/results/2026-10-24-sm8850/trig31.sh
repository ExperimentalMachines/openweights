# Every 1 s: time, XO thermistor, hottest GPU and CPU zone (milli-C), the GPU's operating clock, its
# ceiling (max_gpuclk), its thermal and hand-set power levels, how busy it is, each CPU pause switch,
# the kgsl and cDSP cooling states, and each CPU policy's ceiling (kHz). Appends with a session line.
cd /data/local/tmp/sd
xo=""; gz=""; cz=""
for t in /sys/class/thermal/thermal_zone*; do case $(cat $t/type) in sys-therm-0) xo=$t;; gpuss-*) gz="$gz $t";; cpu-0-*|cpu-1-*) cz="$cz $t";; esac; done
d=""; n=""
for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-C0|thermal-pause-3C|kgsl|cdsp) d="$d $c"; n="$n $(cat $c/type)@${c##*/}";; esac; done
g=/sys/class/kgsl/kgsl-3d0
p=$(ls -d /sys/devices/system/cpu/cpufreq/policy*)
echo "# session $(date +%s)" >> trig-r31.txt
echo "time xo gpu_max_c cpu_max_c gpuclk max_gpuclk thermal_pwrlevel max_pwrlevel gpu_busy$n$(for x in $p; do echo -n " max_${x##*/}"; done)" >> trig-r31.txt
while true; do
  l="$(date +%s) $(cat $xo/temp)"
  m=0; for z in $gz; do t=$(cat $z/temp); [ $t -gt $m ] && m=$t; done; l="$l $m"
  m=0; for z in $cz; do t=$(cat $z/temp); [ $t -gt $m ] && m=$t; done; l="$l $m"
  l="$l $(cat $g/gpuclk) $(cat $g/max_gpuclk) $(cat $g/thermal_pwrlevel) $(cat $g/max_pwrlevel) $(cat $g/gpu_busy_percentage | tr -d ' %')"
  for c in $d; do l="$l $(cat $c/cur_state)"; done
  for x in $p; do l="$l $(cat $x/scaling_max_freq)"; done
  echo "$l" >> trig-r31.txt
  sleep 1
done
