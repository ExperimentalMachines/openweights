#!/bin/zsh
# AWQ sweep: alpha 0.3 and 0.7 with every fold; alpha 0.5 with the w2 fold off; alpha 0.5 folds on w1/w3 only.
S=/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad
P=/private/tmp/claude-501/-Users-alpha-mobile-inference/4ca3fd3b-8315-4ad1-b15c-5b965ff05bb2/scratchpad
cd ~/ow-models/etexport; source .venv/bin/activate
run() {
  tag=$1; shift
  env "$@" AWQ_DST=~/ow-models/etexport/hf/LFM2.5-1.2B-AWQ-$tag PYTHONPATH=/Users/alpha/mobile-inference/core/engine/src/main/cpp/llama.cpp/gguf-py python $S/awq.py > $S/awq_$tag.log 2>&1 || { echo "awq failed" >> $S/awq_$tag.log; return; }
  python -m executorch.examples.models.lfm2.convert_weights hf/LFM2.5-1.2B-AWQ-$tag lfm2_5_1_2b_awq_$tag.pth > $S/convert_awq_$tag.log 2>&1
  python -m executorch.extension.llm.export.export_llm \
    --config src/examples/models/lfm2/config/lfm2_xnnpack_q8da4w.yaml \
    +base.model_class="lfm2_5_1_2b" \
    +base.params="src/examples/models/lfm2/config/lfm2_5_1_2b_config.json" \
    +base.checkpoint="lfm2_5_1_2b_awq_$tag.pth" \
    +quantization.group_size=32 \
    "+quantization.embedding_quantize='8,0'" \
    +export.max_seq_length=2048 +export.max_context_length=2048 \
    +export.output_name="lfm2_5_1_2b_awq_${tag}_2k.pte" > $S/export_awq_$tag.log 2>&1
  echo "export exit $?" >> $S/export_awq_$tag.log
  (cd $P && ~/ow-models/etexport/.venv/bin/python pte_run.py sample_prompts_trailer_hf.json $S/pte_awq_${tag}_out.json ~/ow-models/etexport/lfm2_5_1_2b_awq_${tag}_2k.pte > $S/pte_awq_$tag.log 2>&1)
  rm -rf ~/ow-models/etexport/hf/LFM2.5-1.2B-AWQ-$tag ~/ow-models/etexport/lfm2_5_1_2b_awq_$tag.pth
}
run a03   AWQ_ALPHA=0.3
run a07   AWQ_ALPHA=0.7
run now2  AWQ_ALPHA=0.5 AWQ_W2=0
run ffn13 AWQ_ALPHA=0.5 AWQ_W2=0 AWQ_CONV=0
echo DONE > $S/awq2_done.txt
