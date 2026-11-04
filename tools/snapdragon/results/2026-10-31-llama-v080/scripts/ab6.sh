#!/system/bin/sh
# The app build's CPU variant choice: a105 picks armv9.2_2 (SVE, SVE2, SME); v86 is the same
# files with the armv9 variants removed, so the loader falls back to armv8.6_1 (dotprod, fp16, i8mm).
cd /data/local/tmp/sd
rm -rf v86 && cp -r a105 v86 && rm -f v86/libggml-cpu-android_armv9*
out=ab6.log
hot() { m=0; for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu*|gpu*|nsp*) t=$(cat $z/temp); [ $t -lt 104000 ] && [ $t -gt $m ] && m=$t;; esac; done; echo $m; }
cool() { s=0; while [ $(hot) -gt 55000 ] && [ $s -lt 120 ]; do sleep 5; s=$((s+5)); done; }
one() { # pkg sme model
    cool
    export LD_LIBRARY_PATH=$PWD/$1
    if [ $2 = default ]; then unset GGML_KLEIDIAI_SME; else export GGML_KLEIDIAI_SME=0; fi
    r=$(./$1/llama-bench -m gguf/$3 -p 512 -n 64 -r 3 -t 6 -dev none -ngl 0 -o jsonl 2>/dev/null | sed -E 's/.*"n_prompt": *([0-9]+).*"n_gen": *([0-9]+).*"avg_ts": *([0-9.e+-]+).*"stddev_ts": *([0-9.e+-]+).*/pp\1\/tg\2=\3+-\4/' | tr '\n' ' ')
    echo "$3 $1 sme=$2 end_hot=$(hot) :: $r" >> $out
}
for r in 1 2; do
    one a105 default Qwen3-1.7B-Q4_0.gguf; one v86 default Qwen3-1.7B-Q4_0.gguf
    one v86 default LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf; one a105 default LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf
done
LD_LIBRARY_PATH=$PWD/v86 ./v86/llama-bench -m gguf/Qwen3-1.7B-Q4_0.gguf -p 8 -n 1 -r 1 -t 6 -dev none -ngl 0 2>&1 | grep -i "CPU backend\|load_backend\|armv" | head -3 >> $out
echo AB6_DONE >> $out
