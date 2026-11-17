# Heats the unit the way r47 did on Snapdragon: shell busy loops plus dd loops that load DRAM as
# decode does. Runs until the file "heat.stop" appears or the time limit passes, then removes its
# loops. No root needed. Each loop's PID is written to heat.pids so a stop can kill them by PID.
#   setsid sh heat.sh <busy loops> <dd loops> <max seconds> > /dev/null 2>&1 < /dev/null &
cd /data/local/tmp/mtk
busy=${1:-8}; dd_loops=${2:-8}; max=${3:-900}
rm -f heat.stop heat.pids
i=0; while [ $i -lt $busy ]; do (while :; do :; done) & echo $! >> heat.pids; i=$((i + 1)); done
i=0; while [ $i -lt $dd_loops ]; do (while :; do dd if=/dev/zero of=/dev/null bs=32M count=64 2>/dev/null; done) & echo $! >> heat.pids; i=$((i + 1)); done
echo "$(date +%s) heat on busy=$busy dd=$dd_loops" >> heat.log
t=0
while [ $t -lt $max ] && [ ! -e heat.stop ]; do sleep 2; t=$((t + 2)); done
for p in $(cat heat.pids); do kill -9 $p 2>/dev/null; done
echo "$(date +%s) heat off after ${t}s" >> heat.log
