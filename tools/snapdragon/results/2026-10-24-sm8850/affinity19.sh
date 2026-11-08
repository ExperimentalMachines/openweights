# Can an ordinary process see the phone's paused CPU cores? sched_getaffinity (what taskset and nproc
# read) returns a task's mask intersected with the kernel's active CPUs, and needs no permission. Every
# 5 s: the time, this shell's usable-core mask, nproc, and the two CPU pause cooling devices' states.
cd /data/local/tmp/sd
d=""; for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-C0|thermal-pause-3C) d="$d $c";; esac; done
while true; do
  l="$(date +%s) $(taskset -p $$ | sed 's/.*: //') $(nproc)"
  for c in $d; do l="$l $(cat $c/type)=$(cat $c/cur_state)"; done
  echo "$l" >> affinity19.txt
  sleep 5
done
