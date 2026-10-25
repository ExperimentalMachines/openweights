#!/system/bin/sh
# On the phone: ExecuTorch's QNN runner (the app's other runtime) on the NPU, one tokenized prompt
# per turn from make_et_prompts.py, 64 tokens after each (or fewer at an end of turn). Twice per
# prompt, so a first-load effect shows. The runner prints its own timings; they are kept whole.
#
#   sh run_et.sh <label>      expects et/ with the runner, libraries, model, tokenizer, prompts/
cd /data/local/tmp/sd/et || exit 1
label=$1
export LD_LIBRARY_PATH=$PWD ADSP_LIBRARY_PATH=$PWD
out=../et-$label.txt
keep=../et-$label
mkdir -p $keep
fails=0
# The zones pd_bench reads: CPU cores (cpu-0-*, cpu-1-*, not the cpu-hw-trip-* zones, which hold
# a fixed 105 C trip point), GPU (gpuss-*) and NPU (nsphvx-*, nsphmx-*), as the hottest of each,
# with the GPU's thermal cap and whether any CPU cluster's ceiling is lowered.
hot() { m=0; for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in $1) t=$(cat $z/temp); [ $t -gt $m ] && m=$t;; esac; done; echo $m; }
cpu_capped() { for p in /sys/devices/system/cpu/cpufreq/policy*; do [ $(cat $p/scaling_max_freq) -lt $(cat $p/cpuinfo_max_freq) ] && { echo 1; return; }; done; echo 0; }
temps() { echo -n "cpu=$(hot 'cpu-[01]-*') gpu=$(hot 'gpuss-*') npu=$(hot 'nsph[vm]x-*') gpu_cap=$(cat /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel) cpu_capped=$(cpu_capped)"; }
hottest() { a=$(hot 'cpu-[01]-*'); b=$(hot 'gpuss-*'); c=$(hot 'nsph[vm]x-*'); m=$a; [ $b -gt $m ] && m=$b; [ $c -gt $m ] && m=$c; echo $m; }
# The same gate pd_bench applies before a cell, here before each pass over the prompts: hottest
# CPU, GPU and NPU zone at or below COOL degrees, no GPU cap and no CPU ceiling lowered, for at
# most COOL_MAX seconds.
cool() {
  w=0
  while [ $(hottest) -gt $(( ${COOL:-55} * 1000 )) ] || [ "$(cat /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel)" != 0 ] || [ $(cpu_capped) = 1 ]; do
    [ $w -ge ${COOL_MAX:-300} ] && { echo "=== cool timed_out after ${w}s hottest $(hottest)" >> $out; return; }
    sleep 5; w=$((w + 5))
  done
  echo "=== cool passed after ${w}s hottest $(hottest)" >> $out
}
# The manifest, not a glob, says which prompts run, so a stale file cannot join the cohort.
files=$(grep -o '"file": "[^"]*"' prompts/manifest.json | cut -d'"' -f4)
[ $(echo $files | wc -w) -gt 0 ] || { echo "=== manifest empty or unreadable" >> $out; echo "ET_DONE fails 1" >> $out; exit 1; }
echo "=== manifest $(echo $files | wc -w) prompts pte_sha $(sha256sum Qwen3-0.6B-qnn-hybrid-2k.pte | cut -c1-16) runner_sha $(sha256sum qnn_llama_runner | cut -c1-16)" >> $out
for rep in 1 2; do
  cool
  for name in $files; do
    f=prompts/$name
    sz=$(stat -c %s $f 2>/dev/null || echo 0)
    if [ $sz -eq 0 ] || [ $((sz % 8)) -ne 0 ]; then echo "=== bad prompt file $f size $sz" >> $out; fails=$((fails + 1)); continue; fi
    n=$((sz / 8))
    tag=r${rep}-${name%.bin}
    rm -f perf.txt gen.txt
    echo "=== rep $rep file $f prompt_tokens $n time $(date +%s) temps $(temps)" >> $out
    ./qnn_llama_runner --decoder_model_version qwen3 --model_path Qwen3-0.6B-qnn-hybrid-2k.pte \
      --tokenizer_path tokenizer.json --tokenized_prompt $f --seq_len $((n + 64)) --eval_mode 1 \
      --temperature 0 --output_path gen.txt --performance_output_path perf.txt >> $out 2>&1
    rc=$?
    [ $rc -ne 0 ] || ! [ -s perf.txt ] && fails=$((fails + 1))
    cp gen.txt $keep/$tag.gen.txt 2>/dev/null; cp perf.txt $keep/$tag.perf.txt 2>/dev/null
    echo "=== exit $rc time $(date +%s) temps_after $(temps) perf $(cat perf.txt 2>/dev/null | tr '\n' ' ')" >> $out
  done
done
echo "ET_DONE fails $fails" >> $out
[ $fails -eq 0 ]
