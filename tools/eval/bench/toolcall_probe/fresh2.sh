#!/bin/zsh
S=/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad
P=/private/tmp/claude-501/-Users-alpha-mobile-inference/4ca3fd3b-8315-4ad1-b15c-5b965ff05bb2/scratchpad
cd $P
~/ow-models/etexport/.venv/bin/python pte_run.py sample_prompts_fresh.json $S/pte_attn4_fresh_out.json ~/ow-models/etexport/lfm2_5_1_2b_attn4_2k.pte > $S/pte_attn4_fresh.log 2>&1
~/ow-models/etexport/.venv/bin/python pte_run.py sample_prompts_fresh.json $S/pte_all8_fresh_out.json ~/ow-models/etexport/lfm2_5_1_2b_all8_2k.pte > $S/pte_all8_fresh.log 2>&1
echo DONE > $S/fresh2_done.txt
