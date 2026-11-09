# r28 on QDC sa962766 (unit 5d3e8f0a, the reservation extended): where decode time goes, by simpleperf's
# CPU-clock sampling (1 kHz, every thread of the process, by shared object and symbol), in pd_bench
# (the reference build, whose libggml-cpu.so keeps ggml_barrier's symbol) decoding with 6 threads, the
# pause off (N6) then on (P6, both switches found by type, as r27). The same fixed work as r23 (probe.jsonl,
# GPU prefill, CPU decode); N6 is sampled for 20 s from 15 s in, P6 for 30 s from 60 s in, then stopped.
# r23's gate before each; the pause is cleared after P6 and on exit.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain28.log; m=Qwen3-4B-Q4_0.gguf; B=./pkg/bin/pd_bench_ref; G=/sys/class/kgsl/kgsl-3d0
P1=""; P2=""
for c in /sys/class/thermal/cooling_device*; do case $(cat $c/type) in thermal-pause-3C) P1=$c;; thermal-pause-C0) P2=$c;; esac; done
[ -n "$P1" ] && [ -n "$P2" ] || { echo "STOPPED pause switches not found" >> $log; exit 1; }
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
sw() { echo "$(cat $P1/cur_state)$(cat $P2/cur_state)"; }
set_sw() { echo $1 > $P1/cur_state; echo $1 > $P2/cur_state; [ "$(sw)" = "$1$1" ] || { echo "$(date +%s) write $1 read back $(sw)" >> $log; return 1; }; }
clear_sw() { if [ $(cat $xoz/temp) -lt 70000 ]; then set_sw 0; else echo "$(date +%s) left switches $(sw)" >> $log; fi; }
pb=""
cleanup() { [ -n "$pb" ] && kill -9 $pb 2>/dev/null; clear_sw; }
trap cleanup EXIT
trap 'exit 1' INT TERM HUP
topped() { for x in /sys/devices/system/cpu/cpufreq/policy*; do [ "$(cat $x/scaling_max_freq)" = "$(cat $x/cpuinfo_max_freq)" ] || return 1; done; return 0; }
gate() { w=0; while [ $w -lt 1800 ]; do [ $(hot) -le 45000 ] && [ $(cat $xoz/temp) -le 45000 ] && [ "$(cat $G/thermal_pwrlevel)" = 0 ] && [ "$(sw)" = 00 ] && topped && { echo "passed $w"; return; }; sleep 5; w=$((w+5)); done; echo "timed_out $w"; }
prof() {
  arm=$1; wait0=$2; dur=$3
  g=$(gate); case $g in passed*) ;; *) echo "STOPPED at $arm gate $g" >> $log; exit 1;; esac
  [ $arm = P6 ] && { set_sw 1 || exit 1; }
  echo "$(date +%s) prof $arm gate $g switches $(sw)" >> $log
  $B -m gguf/$m -c probe.jsonl -o results-r28.jsonl --label r28-$arm --cells gpu:cpu --gen 128 --gen-ref \
    --ctx 8192 --threads 6 --max-turns 3 --max-convs 1 > log-r28-$arm.txt 2>&1 &
  pb=$!
  sleep $wait0
  simpleperf record -e cpu-clock -f 1000 -p $pb --duration $dur -o perf-$arm.data > simpleperf-$arm.out 2>&1
  echo "$(date +%s) prof $arm recorded $(ls -la perf-$arm.data | awk '{print $5}') bytes" >> $log
  [ $arm = P6 ] && kill -9 $pb 2>/dev/null
  wait $pb; pb=""
  clear_sw
  simpleperf report -i perf-$arm.data --sort dso,symbol --percent-limit 0.3 > perf-$arm.txt 2>&1
  simpleperf report -i perf-$arm.data --sort comm,tid --percent-limit 1 > perf-$arm-threads.txt 2>&1
  echo "$(date +%s) prof $arm done switches $(sw)" >> $log
}
echo "$(date +%T) unit $(getprop ro.serialno) bin $(sha256sum $B | cut -c1-16) switches $P1 $P2" >> $log
prof N6 15 20
prof P6 60 30
echo R28_DONE >> $log
