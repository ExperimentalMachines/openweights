#!/bin/zsh
cd ~/ow-models/etexport; source .venv/bin/activate
python -m executorch.examples.models.lfm2.convert_weights hf/LFM2.5-1.2B-QAD lfm2_5_1_2b_qad.pth
python -m executorch.extension.llm.export.export_llm \
  --config src/examples/models/lfm2/config/lfm2_xnnpack_q8da4w.yaml \
  +base.model_class="lfm2_5_1_2b" \
  +base.params="src/examples/models/lfm2/config/lfm2_5_1_2b_config.json" \
  +base.checkpoint="lfm2_5_1_2b_qad.pth" \
  +quantization.group_size=32 \
  "+quantization.embedding_quantize='8,0'" \
  +export.max_seq_length=2048 +export.max_context_length=2048 \
  +export.output_name="lfm2_5_1_2b_qad_8da4w_2k.pte"
echo "export exit $?"
