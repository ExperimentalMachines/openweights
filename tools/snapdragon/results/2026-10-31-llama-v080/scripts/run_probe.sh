#!/bin/bash
# Runs both probes on each model and reports which cases render differently.
cd "$(dirname "$0")"
M=/Users/alpha/ow-models
mkdir -p out
for f in LFM2.5-1.2B-Instruct-QAD-Q4_0 LFM2.5-2.6B-Q4_K_M LFM2.5-VL-3B-Q4_0 Qwen3-1.7B-Q8_0 Qwen3.5-2B-Q4_K_M vlm/gemma-3-4b-it-Q4_K_M vlm/SmolVLM2-2.2B-Instruct-Q4_K_M; do
    n=$(basename $f)
    ./probe105 $M/$f.gguf > out/$n.105.txt 2>&1
    ./probe080 $M/$f.gguf > out/$n.080.txt 2>&1
    if cmp -s out/$n.105.txt out/$n.080.txt; then
        echo "$n: identical ($(grep -c '^### ' out/$n.105.txt) cases)"
    else
        echo "$n: DIFFERENT in: $(diff <(grep -E '^###|hash|THREW|LOAD' out/$n.105.txt) <(grep -E '^###|hash|THREW|LOAD' out/$n.080.txt) | head -3 | tr '\n' ' ')"
    fi
done
