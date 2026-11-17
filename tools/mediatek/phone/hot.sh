# The decode-stall probe on MediaTek, the Snapdragon r47 protocol: heat the unit with 8 shell busy
# loops and 8 dd loops (heat.sh), stop them, and decode at once, with every bench thread's CPU every
# 2 s (place.sh) and a 30 s scheduler trace from the first second; the same probe runs cool first,
# behind the matrix's gate, as its baseline. Two thread counts: 6 (the matrix's) and 2 (the
# Snapdragon stall guard's fallback). The phone logger (mtklog.sh) runs throughout, so temperatures,
# thermal status and every CPU ceiling are on record at each probe. Each heat is as long as the
# session allows, at most 20 min (session 3's 12 min reached status 4 and paused no core).
#   DEADLINE=<epoch s> nohup setsid sh hot.sh <label> <gguf> > hot-<label>.out 2>&1 < /dev/null &
cd /data/local/tmp/mtk
export LD_LIBRARY_PATH=$PWD/bench:/system_ext/lib64:/vendor/lib64
lab=$1; m=$2; log=hot-$lab.log
cat > pf-hot.cfg <<EOF
buffers { size_kb: 65536 fill_policy: RING_BUFFER }
data_sources { config { name: "linux.ftrace" ftrace_config {
  ftrace_events: "sched/sched_switch"
  ftrace_events: "power/cpu_frequency"
  ftrace_events: "power/cpu_frequency_limits"
} } }
duration_ms: 30000
EOF
left() { echo $(( DEADLINE - $(date +%s) )); }
# One conversation's first three turns, CPU prefill and decode, as the matrix runs them.
probe() { tag=$1; threads=$2; gate=$3; trace=$4
  ./bench/mtk_pd_bench -m gguf/$m -c conversations.jsonl -o results-$lab.jsonl --label $lab-$tag \
    --cells cpu:cpu --gen 2048 --gen-ref --ctx 8192 --threads $threads --max-convs 1 --max-turns 3 $gate \
    --window 2048 --pad-batch 128 > log-$lab-$tag.txt 2>&1 &
  bp=$!
  nohup setsid sh place.sh $bp place-$lab-$tag.txt > /dev/null 2>&1 < /dev/null &
  # The config goes through stdin: perfetto cannot open a file under /data/local/tmp (SELinux, errno
  # 13), which lost session 6's traces.
  [ "$trace" = trace ] && { cat pf-hot.cfg | perfetto --txt -c - -o /data/misc/perfetto-traces/$lab-$tag.pftrace > pf-$lab-$tag.txt 2>&1 & }
  wait $bp
  echo "$(date +%s) probe $tag threads $threads rc $?" >> $log; }
heat() { echo "$(date +%s) heat on for at most $1 s" >> $log
  sh heat.sh 8 8 $1 > /dev/null 2>&1 < /dev/null
  echo "$(date +%s) heat off" >> $log; }
echo "$(date +%s) start $lab $m deadline $DEADLINE left $(left) s" >> $log
probe cool6 6 "--cool 55 --cool-max 120"
probe cool2 2 "--cool 55 --cool-max 120"
# MAXHEAT (s) caps the first heating, 1,200 by default as in the matrix sessions; the hard-throttle
# attempts of 2026-11-16 set it higher, to look for the core pause 20 minutes did not reach.
h=$(( $(left) - 900 )); [ $h -gt ${MAXHEAT:-1200} ] && h=${MAXHEAT:-1200}
if [ $h -ge 300 ]; then heat $h; probe hot6 6 "--cool 0" trace; else echo "$(date +%s) skip hot6: $h s" >> $log; fi
h=$(( $(left) - 300 )); [ $h -gt 360 ] && h=360
if [ $h -ge 120 ]; then heat $h; probe hot2 2 "--cool 0" trace; else echo "$(date +%s) skip hot2: $h s" >> $log; fi
echo HOT_DONE >> $log
