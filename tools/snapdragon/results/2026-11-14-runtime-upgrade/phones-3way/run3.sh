#!/usr/bin/env bash
# run3.sh <poco|qdc> <rounds>: v0.8.0, v1.0.0 and v1.0.0 with the F16 key dot, rotating order, decode at depth,
# a cooling gate before each run (hottest CPU zone under 42 C, at most 240 s), temperatures logged per run.
d="$1"; rounds="${2:-4}"; LIMIT=42000; [ "$d" = poco ] && LIMIT=45000; HERE=$(cd "$(dirname "$0")" && pwd); DEV="$HERE/dev.sh $d"
O="$HERE/lb3-$d.jsonl"; L="$HERE/lb3-$d.log"; : > "$O"; : > "$L"
maxtemp() {
  # The POCO's shell cannot read thermal zones (SELinux); its thermal HAL reports the CPU instead.
  if [ "$d" = poco ]; then $DEV shell 'dumpsys thermalservice' | tr -d '\r' | awk -F'[=,]' '/Current temperatures from HAL/{f=1} f && /mName=CPU/ {printf "%d\n", $2*1000; exit}'; return; fi
  $DEV shell 'm=0; for z in /sys/class/thermal/thermal_zone*; do t=$(cat $z/type 2>/dev/null); case "$t" in *cpu*|*CPU*|*soc*|*tscpu*) v=$(cat $z/temp 2>/dev/null); case "$v" in ""|-*) ;; *) [ "$v" -lt 150000 ] && [ "$v" -gt "$m" ] && m=$v;; esac;; esac; done; echo $m' | tr -d '\r'; }
gate() { local w=0; while :; do t=$(maxtemp); [ -z "$t" ] && t=0; if [ "$t" -lt "$LIMIT" ] || [ $w -ge 240 ]; then echo "$t"; return; fi; sleep 10; w=$((w+10)); done; }
bench() { $DEV shell "cd /data/local/tmp/lb3/$1 && LD_LIBRARY_PATH=. ./llama-bench -m ../$2 -t 4 -p 0 -n $3 -d $4 -fa 1 -r 1 -o jsonl 2>/dev/null" | tr -d '\r'; }
echo "$(date +%T) warm-up" >> "$L"; bench v080 lfm-qad.gguf 32 0 > /dev/null
for m in lfm-qad.gguf qwen3-q8.gguf; do
  for k in $(seq 1 "$rounds"); do
    case $((k % 3)) in 1) order="v080 v100 v100p";; 2) order="v100 v100p v080";; 0) order="v100p v080 v100";; esac
    for v in $order; do
      t=$(gate); echo "$(date +%T) $m round $k $v start-temp $t" >> "$L"
      bench "$v" "$m" 64 0,2048,4096 | sed "s/^{/{\"version\":\"$v\",\"round\":$k,\"model_name\":\"$m\",\"start_temp\":$t,/" >> "$O"
    done
  done
done
echo "$(date +%T) done $(wc -l < "$O") rows" >> "$L"
