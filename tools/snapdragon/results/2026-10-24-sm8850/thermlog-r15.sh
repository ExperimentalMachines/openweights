# Every 10 s: the GPU, NPU, DDR and core-pause cooling states, the hottest NPU sensor and the
# cell/turn running, so a slowdown or stall in pd_bench's turns can be matched to its cause.
cd /data/local/tmp/sd
devs=""
for c in /sys/class/thermal/cooling_device*; do
  case $(cat $c/type) in kgsl|gpu|cdsp*|thermal-pause-*|ddr-cdev) devs="$devs $c";; esac
done
for c in $devs; do echo -n "$(basename $c)=$(cat $c/type) "; done > thermlog15.txt; echo >> thermlog15.txt
while true; do
  l="$(date +%s)"
  for c in $devs; do l="$l $(cat $c/cur_state)"; done
  m=0; for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in nsp*) t=$(cat $z/temp); [ $t -gt $m ] && m=$t;; esac; done
  f=$(ls -t log-r15*.txt 2>/dev/null | head -1)
  echo "$l nspmax=$m $(tail -n 4 $f | grep -E '^(cpu|gpu|npu):' | tail -1 | cut -d' ' -f1-3)" >> thermlog15.txt
  sleep 10
done
