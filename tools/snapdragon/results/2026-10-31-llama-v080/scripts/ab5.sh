#!/system/bin/sh
# b10549 app-flag tools with KleidiAI (a105) against the same build without it (k105), to see
# whether KleidiAI explains the app path's slow prefill on this phone. Order alternates per model.
cd /data/local/tmp/sd
out=ab5.log
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
    one a105 default Qwen3-1.7B-Q4_0.gguf; one k105 default Qwen3-1.7B-Q4_0.gguf
    one k105 default LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf; one a105 default LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf
    one k105 default gemma-3-1b-it-Q4_K_M.gguf; one a105 default gemma-3-1b-it-Q4_K_M.gguf
done
echo AB5_DONE >> $out
