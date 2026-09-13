#!/bin/zsh
S=/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad
P=/private/tmp/claude-501/-Users-alpha-mobile-inference/4ca3fd3b-8315-4ad1-b15c-5b965ff05bb2/scratchpad
cd $S
./export_q40.sh q40 > export_q40.log 2>&1
cd $P && ~/ow-models/etexport/.venv/bin/python pte_run.py sample_prompts_trailer_hf.json $S/pte_q40_out.json ~/ow-models/etexport/lfm2_5_1_2b_q40_2k.pte > $S/pte_q40.log 2>&1
cd $S && OW_Q40_ABS_SCALE=1 ./export_q40.sh q40abs > export_q40abs.log 2>&1
cd $P && ~/ow-models/etexport/.venv/bin/python pte_run.py sample_prompts_trailer_hf.json $S/pte_q40abs_out.json ~/ow-models/etexport/lfm2_5_1_2b_q40abs_2k.pte > $S/pte_q40abs.log 2>&1
echo DONE > $S/q40_done.txt
