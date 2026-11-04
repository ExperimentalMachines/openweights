#!/system/bin/sh
# KleidiAI's SME kernels on or off (GGML_KLEIDIAI_SME=0) on the app-flag builds, to see whether
# they explain the app path's slow prefill on this phone. Order alternates per model.
cd /data/local/tmp/sd
out=ab4.log
hot() { m=0; for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu*|gpu*|nsp*) t=$(cat $z/temp); [ $t -lt 104000 ] && [ $t -gt $m ] && m=$t;; esac; done; echo $m; }
cool() { s=0; while [ $(hot) -gt 55000 ] && [ $s -lt 120 ]; do sleep 5; s=$((s+5)); done; }
one() { # pkg sme model
    cool
    export LD_LIBRARY_PATH=$PWD/$1
    if [ $2 = default ]; then unset GGML_KLEIDIAI_SME; else export GGML_KLEIDIAI_SME=0; fi
    r=$(./$1/llama-bench -m gguf/$3 -p 512 -n 64 -r 3 -t 6 -dev none -ngl 0 -o jsonl 2>/dev/null | sed -E 's/.*"n_prompt": *([0-9]+).*"n_gen": *([0-9]+).*"avg_ts": *([0-9.e+-]+).*"stddev_ts": *([0-9.e+-]+).*/pp\1\/tg\2=\3+-\4/' | tr '\n' ' ')
    echo "$3 $1 sme=$2 end_hot=$(hot) :: $r" >> $out
}
for p in a105 a080; do
    one $p default Qwen3-1.7B-Q4_0.gguf; one $p 0 Qwen3-1.7B-Q4_0.gguf
    one $p 0 LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf; one $p default LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf
done
LD_LIBRARY_PATH=$PWD/a080 ./a080/llama-bench -m gguf/Qwen3-1.7B-Q4_0.gguf -p 8 -n 1 -r 1 -t 6 -dev none -ngl 0 2>&1 | grep -i "kleidiai" | head -3 >> $out
echo AB4_DONE >> $out
