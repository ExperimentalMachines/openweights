#!/system/bin/sh
# Runs on the phone, detached. Each file is checked against the hub's hash before it counts.
D=/data/local/tmp/sd; M=$D/models; mkdir -p $M; cd $M
H=https://huggingface.co
get() { curl -sSL --retry 3 -C - -o "$2" "$H/$1" 2>>$D/fetch.err; if [ "$(sha256sum "$2" | cut -c1-16)" = "$3" ]; then echo "$(date +%T) OK $2" >> $D/fetch.log; else echo "$(date +%T) BAD $2" >> $D/fetch.log; fi; }
get experimentalmachines/LFM2.5-1.2B-Instruct-ExecuTorch/resolve/main/xnnpack/LFM2.5-1.2B-Instruct-8da4w-gptq-4k.pte LFM2.5-1.2B-Instruct-8da4w-gptq-4k.pte 99517d410ee813ff &
get LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_K_M.gguf LFM2.5-1.2B-Instruct-Q4_K_M.gguf b1b3de114215d950 &
get LiquidAI/LFM3-1.7B-GGUF/resolve/main/LFM3-1.7B-Q4_K_M.gguf LFM3-1.7B-Q4_K_M.gguf 3793e1e6e2266919 &
curl -sSL --retry 3 -o LFM2.5-1.2B-Instruct-8da4w-gptq-4k.tokenizer.json $H/experimentalmachines/LFM2.5-1.2B-Instruct-ExecuTorch/resolve/main/tokenizer.json 2>>$D/fetch.err
wait
echo FETCH_DONE >> $D/fetch.log
