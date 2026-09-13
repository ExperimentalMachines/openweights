#!/bin/zsh
cd ~/ow-models/etexport; source .venv/bin/activate
python -m executorch.extension.llm.export.export_llm \
  --config src/examples/models/qwen3_5/config/qwen3_5_xnnpack_fp32.yaml \
  +base.model_class="qwen3_5_2b" \
  +base.params="src/examples/models/qwen3_5/config/2b_config.json" \
  +base.checkpoint="qwen3_5_2b.pth" \
   \
  +export.output_name="qwen3_5_2b_fp32.pte"
echo "export exit $?"
