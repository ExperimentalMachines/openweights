#!/system/bin/sh
# Downloads the test models one at a time on the phone; logs each file's size when done.
cd /data/local/tmp/sd/gguf
for u in LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf \
         unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_0.gguf \
         unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf \
         unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q8_0.gguf; do
    f=${u##*/}
    curl -sSL --retry 3 -o $f.part https://huggingface.co/$u && mv $f.part $f
    echo "$(date +%T) $f $(stat -c %s $f 2>/dev/null)" >> ../dl.log
done
echo DL_DONE >> ../dl.log
