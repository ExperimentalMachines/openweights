# r21 on QDC sa962335 (unit c581fd31 = phone B), Qwen3 4B Q4_0, the same build, model and probe as r20.
# Is the soft throttle's GPU cost the clock alone? The same fixed work as r20's probes (probe.jsonl: one
# conversation, its first 3 turns, 128 teacher-forced tokens a turn) on GPU-only, with the ceiling set by
# hand: max_pwrlevel 0 (1200 MHz) or 8 (578 MHz, the level the thermal throttle held in r20). The phone is
# brought back under 42 C (hottest CPU and GPU zone) with the thermal cap off before every probe, so heat
# does not differ between the arms. One throwaway uncapped probe, then the order fixed before any result:
# U F F U F U U F U F F U (ABBA, BAAB, ABBA). Labels r21-<i>-<U|F>. The ceiling is reset to 0 after each
# probe and on exit. Then chain22.sh, if it has been pushed by then.
cd /data/local/tmp/sd
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
log=chain21.log; m=Qwen3-4B-Q4_0.gguf; B=./pkg/bin/pd_bench_ref; G=/sys/class/kgsl/kgsl-3d0
trap 'echo 0 > $G/max_pwrlevel' EXIT
zs=""; for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu-0-*|cpu-1-*|gpuss-*) zs="$zs $z";; esac; done
xoz=""; for z in /sys/class/thermal/thermal_zone*; do [ "$(cat $z/type)" = sys-therm-0 ] && xoz=$z; done
hot() { h=0; for z in $zs; do t=$(cat $z/temp); [ $t -gt $h ] && h=$t; done; echo $h; }
gate() { w=0; while [ $w -lt 300 ]; do [ $(hot) -le 42000 ] && [ "$(cat $G/thermal_pwrlevel)" = 0 ] && break; sleep 5; w=$((w+5)); done; echo $w; }
probe() {
  w=$(gate); echo $2 > $G/max_pwrlevel; sleep 1
  echo "$(date +%s) probe $1 level $2 waited $w hot $(hot) xo $(cat $xoz/temp) thermal $(cat $G/thermal_pwrlevel) max_gpuclk $(cat $G/max_gpuclk)" >> $log
  $B -m gguf/$m -c probe.jsonl -o results-r21.jsonl --label $1 --cells gpu:gpu --gen 128 --gen-ref \
    --ctx 8192 --threads 6 --max-turns 3 --max-convs 1 > log-$1.txt 2>&1
  rc=$?
  echo "$(date +%s) probe $1 end rc $rc hot $(hot) xo $(cat $xoz/temp) thermal $(cat $G/thermal_pwrlevel) max_gpuclk $(cat $G/max_gpuclk)" >> $log
  echo 0 > $G/max_pwrlevel
  [ $rc = 0 ] || { echo "STOPPED at $1 rc $rc" >> $log; exit 1; }
}
echo "$(date +%T) unit $(getprop ro.serialno) soc $(getprop ro.soc.model) bin $(sha256sum $B | cut -c1-16) model $(grep "^$m " dl19.log | cut -d' ' -f3) probe $(sha256sum probe.jsonl | cut -c1-16)" >> $log
probe r21warm 0
i=0
for a in U F F U F U U F U F F U; do
  i=$((i+1)); [ $a = U ] && l=0 || l=8
  probe r21-$i-$a $l
done
echo R21_DONE >> $log
[ -f chain22.sh ] && sh chain22.sh
