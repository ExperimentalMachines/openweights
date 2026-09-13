#!/bin/zsh
# $1 = output tag (q40 or q40abs); OW_Q40_ABS_SCALE=1 makes every group scale positive.
cd ~/ow-models/etexport; source .venv/bin/activate
export PYTHONPATH=/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad:$PYTHONPATH
export OW_Q40_GGUF=~/ow-models/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf
python -m executorch.extension.llm.export.export_llm \
  --config src/examples/models/lfm2/config/lfm2_xnnpack_q8da4w.yaml \
  +base.model_class="lfm2_5_1_2b" \
  +base.params="src/examples/models/lfm2/config/lfm2_5_1_2b_config.json" \
  +base.checkpoint="lfm2_5_1_2b_qad.pth" \
  +quantization.group_size=32 \
  "+quantization.embedding_quantize='8,0'" \
  +export.max_seq_length=2048 +export.max_context_length=2048 \
  +export.output_name="lfm2_5_1_2b_${1}_2k.pte"
echo "export exit $?"
