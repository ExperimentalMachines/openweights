# r22, second block, on QDC sa962766 (unit 5d3e8f0a): chain22.sh's cells 5 to 8 in the order it fixed
# before any result (2 6 6 2), with everything else as chain22.sh. It runs after r27, because follow22.sh
# stopped chain22.sh between its fourth and fifth cells to run r27 while a balanced block was complete.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain22.log; m=Qwen3-4B-Q4_0.gguf; G=/sys/class/kgsl/kgsl-3d0
zs=""; xoz=""
for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*|nsphvx-*|nsphmx-*) zs="$zs $z";; sys-therm-0) xoz=$z;; esac; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
topped() { for x in /sys/devices/system/cpu/cpufreq/policy*; do [ "$(cat $x/scaling_max_freq)" = "$(cat $x/cpuinfo_max_freq)" ] || return 1; done; return 0; }
gate() {
  w=0
  while [ $w -lt 1800 ]; do
    [ $(cat $xoz/temp) -le 45000 ] && [ $(hot) -le 45000 ] && [ "$(cat $G/thermal_pwrlevel)" = 0 ] && topped && { echo "passed $w"; return; }
    sleep 5; w=$((w+5))
  done
  echo "timed_out $w"
}
temps() { for z in /sys/class/thermal/thermal_zone*; do t=$(cat $z/type); case $t in cpu*|gpu*|nsp*|ddr*|battery|skin*|sys-therm*) echo -n "$t=$(cat $z/temp) ";; esac; done; }
env_line() { echo "{\"kind\":\"env\",\"label\":\"$1\",\"model\":\"$m\",\"when\":\"$2\",\"time\":$(date +%s),\"unit\":\"$(getprop ro.serialno)\",\"temps\":\"$(temps)\",\"memavail_kb\":$(grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2)}" >> results-r22.jsonl; }
cell() {
  label=$1; t=$2
  g=$(gate)
  echo "$(date +%s) start $label threads $t gate $g xo $(cat $xoz/temp) hot $(hot) thermal $(cat $G/thermal_pwrlevel)" >> $log
  case $g in passed*) ;; *) echo "STOPPED at $label gate $g" >> $log; exit 1;; esac
  env_line $label before
  GGML_HEXAGON_NDEV=2 ./pkg/bin/pd_bench_ref -m gguf/$m -c conversations.jsonl -o results-r22.jsonl --label $label \
    --cells gpu:cpu --npu-devices HTP0,HTP1 --gen 2048 --gen-ref --ctx 8192 --threads $t --max-turns 6 \
    --cool 55 --cool-max 120 > log-$label.txt 2>&1
  rc=$?
  env_line $label after
  echo "$(date +%s) end $label rc $rc xo $(cat $xoz/temp) hot $(hot)" >> $log
  [ $rc = 0 ] || { echo "STOPPED at $label rc $rc" >> $log; exit 1; }
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) bin $(sha256sum pkg/bin/pd_bench_ref | cut -c1-16) model $(grep "^$m " dl19.log | cut -d' ' -f3) conv $(sha256sum conversations.jsonl | cut -c1-16)" >> $log
i=4
for t in 2 6 6 2; do
  i=$((i+1))
  cell r22-$i-t$t $t
done
echo CHAIN_DONE >> $log
