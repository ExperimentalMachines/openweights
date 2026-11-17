# Kills a bench process before the tablet runs out of memory. The NPU's buffers are kernel memory
# (vmalloc and DMA, NOTES.md session 8), which the out-of-memory killer cannot reclaim: session 3's
# two kernel panics came from memory running out with nothing killable. Killing the bench releases
# them. Checks every second; logs the lowest MemAvailable it saw when the process ends.
#   setsid sh memguard.sh <pid> <log file> [floor MB, default 600] > /dev/null 2>&1 < /dev/null &
pid=$1; out=$2; floor=${3:-600}
low=999999
while [ -d /proc/$pid ]; do
  a=$(( $(grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2) / 1024 ))
  [ $a -lt $low ] && low=$a
  if [ $a -lt $floor ]; then
    kill -9 $pid
    echo "$(date +%s) pid $pid KILLED: MemAvailable $a MB under $floor" >> $out
    exit 0
  fi
  sleep 1
done
echo "$(date +%s) pid $pid ended; lowest MemAvailable $low MB" >> $out
