#!/system/bin/sh
# After the memory probe: (1) ToolCallingTest with Qwen3.5 2B as model.gguf on both APKs, since
# Qwen3.5's tool grammar is the one template output that changed; model.gguf is put back after.
# (2) GPU start-up time alone: a one-token llama-bench on the Adreno, three times per build.
cd /data/local/tmp/sd
while ! grep -q MEM_DONE ab3.log 2>/dev/null; do sleep 10; done
P=io.github.alpharomercoma.openweights.core.engine.test
ln -f gguf/Qwen3.5-2B-Q4_K_M.gguf /data/local/tmp/openweights/model.gguf
for v in 080 base; do
    pm install -r -t engine-$v.apk > /dev/null 2>&1
    logcat -c
    am instrument -w -r -e class io.github.alpharomercoma.openweights.core.engine.ToolCallingTest $P/androidx.test.runner.AndroidJUnitRunner > q35-inst-$v.txt 2>&1
    logcat -d > q35-logcat-$v.txt
done
ln -f gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf /data/local/tmp/openweights/model.gguf
for i in 1 2 3; do
    for p in q105 q080; do
        export LD_LIBRARY_PATH=$PWD/$p/lib ADSP_LIBRARY_PATH=$PWD/$p/lib
        t0=$(date +%s%N)
        ./$p/bin/llama-bench -m gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf -p 0 -n 1 -r 1 -t 6 -dev GPUOpenCL -ngl 99 > /dev/null 2>&1
        echo "gpu-startup $p run$i ms=$(( ($(date +%s%N) - t0) / 1000000 ))" >> ab3.log
    done
done
echo Q35_DONE >> ab3.log
