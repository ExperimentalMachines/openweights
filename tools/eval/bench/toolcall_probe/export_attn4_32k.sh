#!/bin/zsh
cd ~/ow-models/etexport; source .venv/bin/activate
OW_INT8_REGEX='^(?!.*attention\.w).*' python -m executorch.extension.llm.export.export_llm \
  --config src/examples/models/lfm2/config/lfm2_xnnpack_q8da4w.yaml \
  +base.model_class="lfm2_5_1_2b" \
  +base.params="src/examples/models/lfm2/config/lfm2_5_1_2b_config.json" \
  +base.checkpoint="lfm2_5_1_2b.pth" \
  +quantization.group_size=32 \
  "+quantization.embedding_quantize='8,0'" \
  +export.max_seq_length=2048 +export.max_context_length=32768 \
  +export.output_name="LFM2.5-1.2B-Instruct-attn4-32k.pte"
echo "export exit $?"
