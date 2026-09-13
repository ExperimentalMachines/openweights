#!/bin/zsh
cd ~/ow-models/etexport; source .venv/bin/activate
python -m executorch.extension.llm.export.export_llm \
  --config src/examples/models/qwen3_5/config/qwen3_5_xnnpack_fp32.yaml \
  +base.model_class="qwen3_5_2b" \
  +base.params="src/examples/models/qwen3_5/config/2b_config.json" \
  +base.checkpoint="qwen3_5_2b.pth" \
  +quantization.qmode=8da4w +quantization.group_size=32 "+quantization.embedding_quantize='8,0'" \
  +export.output_name="qwen3_5_2b_8da4w.pte"
echo "export exit $?"
