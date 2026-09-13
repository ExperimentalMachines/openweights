#!/bin/zsh
# Second split: attention stays int4; within feed-forward and conv, one projection group
# at int4 at a time, the rest int8 per channel. Tags: w13_4, w2_4, convin4, convout4.
S=/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad
P=/private/tmp/claude-501/-Users-alpha-mobile-inference/4ca3fd3b-8315-4ad1-b15c-5b965ff05bb2/scratchpad
cd ~/ow-models/etexport; source .venv/bin/activate
run() {
  tag=$1; regex=$2
  OW_INT8_REGEX="$regex" python -m executorch.extension.llm.export.export_llm \
    --config src/examples/models/lfm2/config/lfm2_xnnpack_q8da4w.yaml \
    +base.model_class="lfm2_5_1_2b" \
    +base.params="src/examples/models/lfm2/config/lfm2_5_1_2b_config.json" \
    +base.checkpoint="lfm2_5_1_2b.pth" \
    +quantization.group_size=32 \
    "+quantization.embedding_quantize='8,0'" \
    +export.max_seq_length=2048 +export.max_context_length=2048 \
    +export.output_name="lfm2_5_1_2b_${tag}_2k.pte" > $S/export_${tag}.log 2>&1
  echo "export exit $?" >> $S/export_${tag}.log
  (cd $P && ~/ow-models/etexport/.venv/bin/python pte_run.py sample_prompts_trailer_hf.json $S/pte_${tag}_out.json ~/ow-models/etexport/lfm2_5_1_2b_${tag}_2k.pte > $S/pte_${tag}.log 2>&1)
}
# int8 on everything that is NOT attention and NOT the named group
run w13_4    '^(?!.*attention\.w)(?!.*feed_forward\.w[13]).*'
run w2_4     '^(?!.*attention\.w)(?!.*feed_forward\.w2).*'
run convin4  '^(?!.*attention\.w)(?!.*conv\.[BCx]_proj).*'
run convout4 '^(?!.*attention\.w)(?!.*conv\.out_proj).*'
echo DONE > $S/mix2_done.txt
