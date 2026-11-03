# Every 10 s: each throttle-related cooling device's state, the hottest NSP zone, the last finished turn.
cd /data/local/tmp/sd
devs=""
for c in /sys/class/thermal/cooling_device*; do
  case $(cat $c/type) in kgsl|gpu|cdsp*|thermal-pause-*|ddr-cdev|cpu-cluster*) devs="$devs $c";; esac
done
for c in $devs; do echo -n "$(basename $c)=$(cat $c/type) "; done > thermlog-r18.txt; echo >> thermlog-r18.txt
while true; do
  l="$(date +%s)"
  for c in $devs; do l="$l $(cat $c/cur_state)"; done
  m=0; for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in nsp*) t=$(cat $z/temp); [ $t -gt $m ] && m=$t;; esac; done
  f=$(ls -t log-r18*.txt 2>/dev/null | head -1)
  echo "$l nspmax=$m $(tail -n 4 $f 2>/dev/null | grep -E '^(cpu|gpu|npu):' | tail -1 | cut -d' ' -f1-3)" >> thermlog-r18.txt
  sleep 10
done
