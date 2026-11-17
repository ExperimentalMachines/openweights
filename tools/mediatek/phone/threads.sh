# Thread counts on MediaTek, the r49/r52 question without a hand-set pause (an unrooted unit cannot
# set one): llama-bench prefill (pp) and decode (tg) at 1, 2, 3, 4, 6 and 8 threads, rounds in a
# shuffled order. Mode "cool": before every run wait until every CPU ceiling is at its top and AP is
# at or under the limit (at most 600 s, logged). Mode "hot": no wait, runs back to back, so the soft
# throttle stays on. Each run's ceilings and HAL temperatures are logged before and after.
#   nohup setsid sh threads.sh <label> <model> <cool|hot> <rounds> [pp] [tg] > /dev/null 2>&1 < /dev/null &
cd /data/local/tmp/mtk
export LD_LIBRARY_PATH=$PWD/bench:/system_ext/lib64:/vendor/lib64
lab=$1; m=$2; mode=$3; rounds=$4; pp=${5:-512}; tg=${6:-128}
out=threads-$lab.txt
hal() { dumpsys thermalservice | sed -n '/Current temperatures from HAL/,/Current cooling/p' | grep -o 'mValue=[0-9.-]*, mType=[0-9]*, mName=\(AP\|SKIN\)' | sed 's/mValue=\([0-9.-]*\).*mName=\(.*\)/\2=\1/' | tr '\n' ' '; dumpsys thermalservice | grep -m1 "Thermal Status" | tr -d ' '; }
caps() { for p in 0 4 7; do echo -n "p$p=$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_max_freq)/$(cat /sys/devices/system/cpu/cpufreq/policy$p/cpuinfo_max_freq) "; done; }
full() { for p in 0 4 7; do [ "$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_max_freq)" = "$(cat /sys/devices/system/cpu/cpufreq/policy$p/cpuinfo_max_freq)" ] || return 1; done; return 0; }
ap() { dumpsys thermalservice | sed -n '/Current temperatures from HAL/,/Current cooling/p' | grep "mName=AP," | sed 's/.*mValue=\([0-9]*\).*/\1/'; }
echo "$(date +%s) start $lab $m $mode rounds=$rounds pp=$pp tg=$tg" >> $out
r=0
while [ $r -lt $rounds ]; do
  # A fixed shuffle per round, the same on every run of this script.
  case $r in 0) order="4 1 6 2 8 3";; 1) order="2 8 3 6 1 4";; 2) order="6 3 1 4 2 8";; *) order="8 6 4 3 2 1";; esac
  for t in $order; do
    w=0
    if [ "$mode" = cool ]; then
      while { ! full || [ "$(ap)" -gt 45 ]; } && [ $w -lt 600 ]; do sleep 5; w=$((w + 5)); done
    fi
    echo "$(date +%s) run round=$r threads=$t waited=$w before: $(caps) $(hal)" >> $out
    # CPU only: no device, and no op offload, or the Vulkan backend can take the prompt
    # (docs/research/gpu-backends.md: -ngl 0 alone does not give a clean CPU reading).
    ./bench/llama-bench -m gguf/$m -t $t -p $pp -n $tg -r 1 -ngl 0 -dev none -nopo 1 -o jsonl >> $out 2>/dev/null
    echo "$(date +%s) done round=$r threads=$t after: $(caps) $(hal)" >> $out
  done
  r=$((r + 1))
done
echo "$(date +%s) THREADS_DONE" >> $out
