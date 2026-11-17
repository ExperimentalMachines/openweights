# Downloads the nine GGUF files of the Snapdragon matrix onto the phone, from the same
# repositories (matched by size; the sha256 prefix is logged and compared with the Snapdragon
# runs' dl12.log). The streamed Galaxy Tab has no curl, so a static aarch64 build is pushed
# beside this script; CA certificates come from the system store. Its musl resolver finds no
# /etc/resolv.conf on Android and --dns-servers did not help, so each host is resolved with ping
# (Android's own resolver) and handed to curl with --resolve, following the redirect by hand.
#   sh dl.sh <model file> ...   (no argument: all nine)
cd /data/local/tmp/mtk
mkdir -p gguf
# One PEM bundle from the system store (the conscrypt APEX copy when present): OpenSSL 4 cannot
# read the old-style hashed names of /system/etc/security/cacerts as a --capath.
d=/apex/com.android.conscrypt/cacerts; [ -d $d ] || d=/system/etc/security/cacerts
[ -s ca.pem ] || cat $d/* > ca.pem
all="LiquidAI/LFM2.5-1.2B-Instruct-GGUF/LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf
bartowski/google_gemma-3-1b-it-GGUF/google_gemma-3-1b-it-Q4_0.gguf
bartowski/Llama-3.2-1B-Instruct-GGUF/Llama-3.2-1B-Instruct-Q4_0.gguf
unsloth/Qwen3-0.6B-GGUF/Qwen3-0.6B-Q4_0.gguf
unsloth/Qwen3-1.7B-GGUF/Qwen3-1.7B-Q4_0.gguf
LiquidAI/LFM2.5-2.6B-GGUF/LFM2.5-2.6B-QAD-Q4_0.gguf
unsloth/Qwen3-1.7B-GGUF/Qwen3-1.7B-Q8_0.gguf
unsloth/Qwen3-4B-GGUF/Qwen3-4B-Q4_0.gguf
bartowski/Llama-3.2-3B-Instruct-GGUF/Llama-3.2-3B-Instruct-Q4_0.gguf"
want="$*"
ip_of() { ping -c1 -W3 "$1" 2>/dev/null | head -1 | cut -d'(' -f2 | cut -d')' -f1; }
for path in $all; do
  f=${path##*/}; r=${path%/*}
  [ -n "$want" ] && ! echo " $want " | grep -q " $f " && continue
  n=0
  until [ -s gguf/$f ] || [ $n -ge 4 ]; do
    url="https://huggingface.co/$r/resolve/main/$f"
    loc=$(./curl -sSI --resolve huggingface.co:443:$(ip_of huggingface.co) --cacert ca.pem "$url" | grep -i '^location:' | tr -d '\r' | cut -d' ' -f2)
    case "$loc" in http*) ;; /*) loc="https://huggingface.co$loc";; *) loc="$url";; esac
    host=$(echo "$loc" | cut -d/ -f3)
    ./curl -sS --retry 3 --resolve $host:443:$(ip_of $host) --cacert ca.pem -o gguf/$f.part "$loc" && mv gguf/$f.part gguf/$f
    n=$((n+1))
  done
  echo "$f $(stat -c %s gguf/$f 2>/dev/null) $(sha256sum gguf/$f | cut -c1-16) $(date +%T)" >> dl.log
done
echo DL_DONE >> dl.log
