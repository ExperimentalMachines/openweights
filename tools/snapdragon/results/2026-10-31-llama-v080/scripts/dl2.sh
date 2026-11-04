#!/system/bin/sh
# After the first downloads: fetch Gemma 3 1B, then lay the files out where the engine's
# on-device tests look (/data/local/tmp/openweights), as hard links to save the copy.
cd /data/local/tmp/sd
while ! grep -q DL_DONE dl.log; do sleep 5; done
curl -sSL --retry 3 -o gguf/gemma-3-1b-it-Q4_K_M.gguf.part https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf && mv gguf/gemma-3-1b-it-Q4_K_M.gguf.part gguf/gemma-3-1b-it-Q4_K_M.gguf
echo "$(date +%T) gemma $(stat -c %s gguf/gemma-3-1b-it-Q4_K_M.gguf)" >> dl.log
mkdir -p /data/local/tmp/openweights/bench
ln -f gguf/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf /data/local/tmp/openweights/model.gguf
ln -f gguf/Qwen3-1.7B-Q8_0.gguf /data/local/tmp/openweights/bench/qwen.gguf
ln -f gguf/gemma-3-1b-it-Q4_K_M.gguf /data/local/tmp/openweights/bench/gemma.gguf
chmod 755 /data/local/tmp/openweights /data/local/tmp/openweights/bench
chmod 644 gguf/*.gguf
echo LAYOUT_DONE >> dl.log
