#!/bin/zsh
cd ~/ow-models/etexport; source .venv/bin/activate
python -m executorch.extension.llm.export.export_llm \
  --config src/examples/models/lfm2/config/lfm2_xnnpack_q8da4w.yaml \
  +base.model_class="lfm2_5_1_2b" \
  +base.params="src/examples/models/lfm2/config/lfm2_5_1_2b_config.json" \
  +base.checkpoint="lfm2_5_1_2b.pth" \
  "quantization.qmode=8da4w-gptq" \
  +quantization.group_size=32 \
  "+quantization.embedding_quantize='8,0'" \
  +quantization.calibration_limit=32 +quantization.calibration_seq_length=512 \
  +export.max_seq_length=2048 +export.max_context_length=2048 \
  +export.output_name="lfm2_5_1_2b_gptq_2k.pte"
echo "export exit $?"
