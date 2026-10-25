#!/system/bin/sh
# On the phone: ExecuTorch's QNN runner (the app's other runtime) on the NPU, one tokenized prompt
# per turn from make_et_prompts.py, 64 tokens after each (or fewer at an end of turn). Twice per
# prompt, so a first-load effect shows. The runner prints its own timings; they are kept whole.
#
#   sh run_et.sh <label>      expects et/ with the runner, libraries, model, tokenizer, prompts/
cd /data/local/tmp/sd/et
label=$1
export LD_LIBRARY_PATH=$PWD ADSP_LIBRARY_PATH=$PWD
out=../et-$label.txt
for rep in 1 2; do
  for f in prompts/*.bin; do
    n=$(( $(stat -c %s $f) / 8 ))
    echo "=== rep $rep file $f prompt_tokens $n time $(date +%s)" >> $out
    ./qnn_llama_runner --decoder_model_version qwen3 --model_path Qwen3-0.6B-qnn-hybrid-2k.pte \
      --tokenizer_path tokenizer.json --tokenized_prompt $f --seq_len $((n + 64)) --eval_mode 1 \
      --temperature 0 --output_path out-$label.txt --performance_output_path perf-$label.txt >> $out 2>&1
    echo "=== exit $? perf $(cat perf-$label.txt 2>/dev/null | tr '\n' ' ')" >> $out
  done
done
echo ET_DONE >> $out
