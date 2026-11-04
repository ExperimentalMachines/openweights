#!/system/bin/sh
# A/B of two llama.cpp builds on one phone: WikiText-2 perplexity (correctness) and llama-bench
# (speed) on CPU, Adreno (OpenCL) and Hexagon, each pair run back to back with the order
# alternating, after a cooling wait, so heat does not favour one build.
# Usage: sh ab.sh <out.log> <pkgA> <pkgB> <model.gguf>...
cd /data/local/tmp/sd
out=$1; A=$2; B=$3; shift 3
hot() { m=0; for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu*|gpu*|nsp*) t=$(cat $z/temp); [ $t -lt 104000 ] && [ $t -gt $m ] && m=$t;; esac; done; echo $m; }
cool() { s=0; while [ $(hot) -gt ${COOL:-55000} ] && [ $s -lt ${COOL_MAX:-120} ]; do sleep 5; s=$((s+5)); done; echo "cool waited=${s}s hot=$(hot)" >> $out; }
run() { # pkg kind dev model
    p=$1; k=$2; d=$3; m=$4
    export LD_LIBRARY_PATH=$PWD/$p/lib ADSP_LIBRARY_PATH=$PWD/$p/lib GGML_HEXAGON_NDEV=1
    ngl=99; [ $d = none ] && ngl=0
    cool
    t0=$(date +%s)
    if [ $k = ppl ]; then
        r=$(timeout 900 ./$p/bin/llama-perplexity -m gguf/$m -f wiki.test.raw -c 512 --chunks ${CHUNKS:-16} -t 6 -dev $d -ngl $ngl 2>&1 | grep -E "Final estimate|error|failed" | tail -1)
    else
        # Columns found by name: the two versions' CSVs need not have the same ones.
        r=$(timeout 900 ./$p/bin/llama-bench -m gguf/$m -p 512 -n 64 -r 3 -t 6 -dev $d -ngl $ngl -o csv 2>/dev/null | tr -d '"' | awk -F, '
            /^build_commit/ { for (i = 1; i <= NF; i++) c[$i] = i; next }
            c["avg_ts"] { printf "pp%s/tg%s=%.2f+-%.2f ", $c["n_prompt"], $c["n_gen"], $c["avg_ts"], $c["stddev_ts"] }')
    fi
    echo "$m $k $d $p secs=$(( $(date +%s) - t0 )) end_hot=$(hot) :: $r" >> $out
}
i=0
for m in "$@"; do
    for k in ppl bench; do
        for d in none GPUOpenCL HTP0; do
            i=$((i+1))
            if [ $((i % 2)) = 1 ]; then run $A $k $d $m; run $B $k $d $m; else run $B $k $d $m; run $A $k $d $m; fi
        done
    done
done
echo AB_DONE >> $out
