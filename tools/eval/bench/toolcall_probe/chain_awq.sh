#!/bin/zsh
S=/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad
P=/private/tmp/claude-501/-Users-alpha-mobile-inference/4ca3fd3b-8315-4ad1-b15c-5b965ff05bb2/scratchpad
cd ~/ow-models/etexport; source .venv/bin/activate
PYTHONPATH=/Users/alpha/mobile-inference/core/engine/src/main/cpp/llama.cpp/gguf-py python $S/awq.py > $S/awq.log 2>&1 || { echo "awq failed" >> $S/awq.log; exit 1; }
python -m executorch.examples.models.lfm2.convert_weights hf/LFM2.5-1.2B-AWQ lfm2_5_1_2b_awq.pth > $S/convert_awq.log 2>&1
python -m executorch.extension.llm.export.export_llm \
  --config src/examples/models/lfm2/config/lfm2_xnnpack_q8da4w.yaml \
  +base.model_class="lfm2_5_1_2b" \
  +base.params="src/examples/models/lfm2/config/lfm2_5_1_2b_config.json" \
  +base.checkpoint="lfm2_5_1_2b_awq.pth" \
  +quantization.group_size=32 \
  "+quantization.embedding_quantize='8,0'" \
  +export.max_seq_length=2048 +export.max_context_length=2048 \
  +export.output_name="lfm2_5_1_2b_awq_2k.pte" > $S/export_awq.log 2>&1
echo "export exit $?" >> $S/export_awq.log
cd $P && ~/ow-models/etexport/.venv/bin/python pte_run.py sample_prompts_trailer_hf.json $S/pte_awq_out.json ~/ow-models/etexport/lfm2_5_1_2b_awq_2k.pte > $S/pte_awq.log 2>&1
echo DONE > $S/awq_done.txt
