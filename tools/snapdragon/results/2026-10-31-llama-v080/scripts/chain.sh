#!/system/bin/sh
# Starts the app-flag A/B once the Snapdragon-tools A/B has finished, so the two never share the phone.
cd /data/local/tmp/sd
while ! grep -q AB_DONE ab.log; do sleep 10; done
sh ab2.sh ab2.log a105 a080 LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf gemma-3-1b-it-Q4_K_M.gguf Qwen3-1.7B-Q4_0.gguf Qwen3.5-2B-Q4_K_M.gguf
