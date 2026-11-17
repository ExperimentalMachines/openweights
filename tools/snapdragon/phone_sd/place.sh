# Where a process's threads ran, every 2 s: epoch, then tid:last CPU (field 39 of
# /proc/<pid>/task/<tid>/stat) for every thread, so a CPU pause or isolation shows as threads
# crowded onto the cores left. Exits when the process does.
#   setsid sh place.sh <pid> <out file> > /dev/null 2>&1 < /dev/null &
pid=$1; out=$2
while [ -d /proc/$pid ]; do
  l="$(date +%s)"
  for t in /proc/$pid/task/*; do
    s=$(cat $t/stat 2>/dev/null) || continue
    # The comm field can hold spaces; the CPU is the 37th field after the closing parenthesis.
    rest=${s##*) }
    set -- $rest
    shift 36
    l="$l ${t##*/}:$1"
  done
  echo "$l" >> $out
  sleep 2
done
