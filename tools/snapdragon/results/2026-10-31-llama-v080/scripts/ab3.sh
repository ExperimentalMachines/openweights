#!/system/bin/sh
# The speed pairs again, each in the opposite order to its first run (ab.sh and ab2.sh happened
# to run most speed pairs v0.8.0 first), so the two runs together cancel the order effect.
# Usage: sh ab3.sh <out.log>; each spec below is "first second device model".
cd /data/local/tmp/sd
out=$1
hot() { m=0; for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu*|gpu*|nsp*) t=$(cat $z/temp); [ $t -lt 104000 ] && [ $t -gt $m ] && m=$t;; esac; done; echo $m; }
cool() { s=0; while [ $(hot) -gt 55000 ] && [ $s -lt 120 ]; do sleep 5; s=$((s+5)); done; echo "cool waited=${s}s hot=$(hot)" >> $out; }
bench() { # pkg dev model
    p=$1; d=$2; m=$3
    case $p in q*) dir=$p/lib; bin=$p/bin;; *) dir=$p; bin=$p;; esac
    export LD_LIBRARY_PATH=$PWD/$dir ADSP_LIBRARY_PATH=$PWD/$dir GGML_HEXAGON_NDEV=1
    ngl=99; [ $d = none ] && ngl=0
    cool
    t0=$(date +%s)
    r=$(timeout 900 ./$bin/llama-bench -m gguf/$m -p 512 -n 64 -r 3 -t 6 -dev $d -ngl $ngl -o jsonl 2>/dev/null | sed -E 's/.*"n_prompt": *([0-9]+).*"n_gen": *([0-9]+).*"avg_ts": *([0-9.e+-]+).*"stddev_ts": *([0-9.e+-]+).*/pp\1\/tg\2=\3+-\4/' | tr '\n' ' ')
    echo "$m bench $d $p secs=$(( $(date +%s) - t0 )) end_hot=$(hot) :: $r" >> $out
}
while read first second d m; do
    [ -z "$m" ] && continue
    bench $first $d $m
    bench $second $d $m
done <<EOF
a105 a080 none LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf
a105 a080 none gemma-3-1b-it-Q4_K_M.gguf
a105 a080 none Qwen3-1.7B-Q4_0.gguf
a105 a080 none Qwen3.5-2B-Q4_K_M.gguf
q105 q080 HTP0 LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf
q105 q080 HTP0 Qwen3-1.7B-Q4_0.gguf
q080 q105 GPUOpenCL LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf
q080 q105 GPUOpenCL Qwen3-1.7B-Q4_0.gguf
q105 q080 none LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf
q105 q080 none Qwen3-1.7B-Q4_0.gguf
EOF
echo AB_DONE >> $out
