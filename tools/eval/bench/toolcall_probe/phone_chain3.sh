#!/bin/zsh
# Seed-7 rows for four artifacts, then the two ExecuTorch benchmark passes. The seed-8 pass
# is left out: the phone is on battery at 60 percent.
S=/private/tmp/claude-501/-Users-alpha-mobile-inference/faac1161-5e3e-47a2-a569-3fb7df4a36f1/scratchpad
SER=192.168.100.171:33917
M=LFM2.5-1.2B-Instruct-attn4-32k.pte,LFM2.5-1.2B-Instruct-8da4w-32k.pte,LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf,LFM2.5-1.2B-Instruct-Q4_K_M.gguf
cd /Users/alpha/mobile-inference
adb -s $SER shell "rm -f /sdcard/Android/data/io.github.alpharomercoma.openweights.debug/files/eval-results/decisions-LFM2.5-1.2B-Instruct-*-driven-search.jsonl"
MODELS=$M ARMS=driven-search PREFIX=prod- tools/eval/bench/run_decisions.sh $SER > $S/phone_decisions7.log 2>&1
echo DECISIONS_DONE >> $S/phone_decisions7.log
BENCH_MODEL=LFM2.5-1.2B-Instruct-attn4-32k CLASSES=ExecuTorchBenchmarkEval PREFIX=prod- tools/eval/bench/run_local.sh $SER > $S/phone_bench_attn4.log 2>&1
BENCH_MODEL=LFM2.5-1.2B-Instruct-8da4w-32k CLASSES=ExecuTorchBenchmarkEval PREFIX=prod- tools/eval/bench/run_local.sh $SER > $S/phone_bench_8da4w.log 2>&1
echo PHONE_DONE > $S/phone_done.txt
