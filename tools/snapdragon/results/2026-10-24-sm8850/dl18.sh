cd /data/local/tmp/sd
mkdir -p gguf
for u in unsloth/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_0.gguf; do
  f=${u##*/}
  n=0
  until [ -s gguf/$f ] || [ $n -ge 4 ]; do
    curl -sSL --retry 3 -o gguf/$f.part "https://huggingface.co/$u" && mv gguf/$f.part gguf/$f
    n=$((n+1))
  done
  echo "$f $(stat -c %s gguf/$f 2>/dev/null) $(sha256sum gguf/$f | cut -c1-16) $(date +%T)" >> dl18.log
done
echo DL_DONE >> dl18.log
