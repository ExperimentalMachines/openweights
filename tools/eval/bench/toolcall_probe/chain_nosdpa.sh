#!/bin/zsh
# fp32 export with the portable SDPA instead of the custom sdpa_with_kv_cache op, to see
# whether the fp32 .pte's gap to the reference is that op. Waits for the mix chain.
S=/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad
P=/private/tmp/claude-501/-Users-alpha-mobile-inference/4ca3fd3b-8315-4ad1-b15c-5b965ff05bb2/scratchpad
until [ -f $S/mix_done.txt ]; do sleep 20; done
cd ~/ow-models/etexport; source .venv/bin/activate
python -m executorch.extension.llm.export.export_llm \
  --config src/examples/models/lfm2/config/lfm2_xnnpack_fp32.yaml \
  +base.model_class="lfm2_5_1_2b" \
  +base.params="src/examples/models/lfm2/config/lfm2_5_1_2b_config.json" \
  +base.checkpoint="lfm2_5_1_2b.pth" \
  model.use_sdpa_with_kv_cache=False \
  +export.max_seq_length=2048 +export.max_context_length=2048 \
  +export.output_name="lfm2_5_1_2b_fp32_nosdpa_2k.pte" > $S/export_nosdpa.log 2>&1
echo "export exit $?" >> $S/export_nosdpa.log
(cd $P && ~/ow-models/etexport/.venv/bin/python pte_run.py sample_prompts_trailer_hf.json $S/pte_nosdpa_out.json ~/ow-models/etexport/lfm2_5_1_2b_fp32_nosdpa_2k.pte > $S/pte_nosdpa.log 2>&1)
echo DONE > $S/nosdpa_done.txt
