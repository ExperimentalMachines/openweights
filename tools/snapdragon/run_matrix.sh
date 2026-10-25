#!/system/bin/sh
# On the phone: every prefill/decode pairing for each model, in an order shuffled per model so
# no pairing always runs first (cold) or last (hot). Thermal zones and memory are logged before
# and after each model.
#
#   sh run_matrix.sh <label> <seed> <model.gguf>[:HTP0,HTP1] ...
# Expects pkg/ (bin, lib) and conversations.jsonl beside it, models under gguf/.
cd /data/local/tmp/sd
label=$1; seed=$2; shift 2
status=0
export LD_LIBRARY_PATH=$PWD/pkg/lib ADSP_LIBRARY_PATH=$PWD/pkg/lib
out=results-$label.jsonl
CELLS="cpu:cpu cpu:gpu cpu:npu gpu:cpu gpu:gpu gpu:npu npu:cpu npu:gpu npu:npu"
temps() { for z in /sys/class/thermal/thermal_zone*; do t=$(cat $z/type); case $t in cpu*|gpu*|nsp*|cdsp*|ddr*|battery|skin*|sys-therm*) echo -n "$t=$(cat $z/temp) ";; esac; done; }
for spec in "$@"; do
  m=${spec%%:*}; devs=HTP0
  case $spec in *:*) devs=${spec#*:};; esac
  ndev=$(echo $devs | tr ',' '\n' | wc -l)
  # The same seed and model name always give the same order.
  order=$(echo $CELLS | tr ' ' '\n' | awk -v s="$(echo "$seed$m" | cksum | cut -d' ' -f1)" 'BEGIN{srand(s)} {print rand()"\t"$0}' | sort | cut -f2 | tr '\n' ',' | sed 's/,$//')
  # Exactly the nine legal cells, each once, or nothing runs.
  if [ "$(echo $order | tr ',' '\n' | sort -u | wc -l)" != 9 ] || [ "$(echo $order | tr ',' '\n' | grep -cE '^(cpu|gpu|npu):(cpu|gpu|npu)$')" != 9 ]; then
    echo "{\"kind\":\"env\",\"label\":\"$label\",\"model\":\"$m\",\"when\":\"refused\",\"order\":\"$order\"}" >> $out; status=1; continue
  fi
  echo "{\"kind\":\"env\",\"label\":\"$label\",\"model\":\"$m\",\"when\":\"before\",\"time\":$(date +%s),\"order\":\"$order\",\"temps\":\"$(temps)\",\"memavail_kb\":$(grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2)}" >> $out
  GGML_HEXAGON_NDEV=$ndev ./pkg/bin/${BIN:-pd_bench} -m gguf/$m -c conversations.jsonl -o $out --label $label \
    --cells "$order" --npu-devices "$devs" --gen ${GEN:-64} --ctx ${CTX:-8192} --threads ${THREADS:-6} \
    --max-turns ${TURNS:-6} --cool ${COOL:-0} --cool-max ${COOL_MAX:-600} > log-$label-$m.txt 2>&1
  rc=$?
  [ $rc -ne 0 ] && status=1
  echo "{\"kind\":\"env\",\"label\":\"$label\",\"model\":\"$m\",\"when\":\"after\",\"time\":$(date +%s),\"exit\":$rc,\"temps\":\"$(temps)\",\"memavail_kb\":$(grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2)}" >> $out
done
echo "{\"kind\":\"run\",\"label\":\"$label\",\"seed\":\"$seed\",\"bin\":\"${BIN:-pd_bench}\",\"gen\":${GEN:-64},\"ctx\":${CTX:-8192},\"threads\":${THREADS:-6},\"turns\":${TURNS:-6},\"cool\":${COOL:-0},\"hexagon_env\":\"$(env | grep GGML_HEXAGON | tr '\n' ' ')\",\"status\":$status}" >> $out
echo "MATRIX_DONE status $status" >> log-$label-done.txt
exit $status
