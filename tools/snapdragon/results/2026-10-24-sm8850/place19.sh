# Every 2 s while pd_bench runs: each of its threads' last CPU (field 39 of /proc/<pid>/task/<tid>/stat),
# its utime+stime, and the process's allowed-CPU list, so where the workers ran during a pause is on record.
cd /data/local/tmp/sd
echo "# session $(date +%s) time pid allowed tid:cpu:ticks ..." >> place19.txt
while true; do
  p=$(pidof pd_bench_ref | cut -d' ' -f1)
  if [ -n "$p" ]; then
    l="$(date +%s) $p $(grep Cpus_allowed_list /proc/$p/status | tr -d '\t' | cut -d: -f2)"
    for t in /proc/$p/task/*; do s=$(cat $t/stat 2>/dev/null) || continue; set -- ${s#*) }; l="$l ${t##*/}:${37}:$(( ${12} + ${13} ))"; done
    echo "$l" >> place19.txt
  fi
  sleep 2
done
