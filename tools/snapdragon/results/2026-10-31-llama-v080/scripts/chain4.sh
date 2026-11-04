#!/system/bin/sh
# After the reversed speed pass: one short perplexity run per build and model with the full log
# kept, for the weight buffer sizes (how much the new KleidiAI kernels repack) and peak memory.
cd /data/local/tmp/sd
while ! grep -q AB_DONE ab3.log 2>/dev/null; do sleep 10; done
for m in LFM2.5-1.2B-Instruct-QAD-Q4_0 gemma-3-1b-it-Q4_K_M Qwen3-1.7B-Q4_0 Qwen3.5-2B-Q4_K_M; do
    for p in a105 a080; do
        LD_LIBRARY_PATH=$PWD/$p ./$p/llama-perplexity -m gguf/$m.gguf -f wiki.test.raw -c 512 --chunks 2 -t 6 -dev none -ngl 0 > mem-$p-$m.txt 2>&1
        grep VmHWM /proc/self/status > /dev/null
    done
done
tar czf mem.tgz mem-*.txt
echo MEM_DONE >> ab3.log
