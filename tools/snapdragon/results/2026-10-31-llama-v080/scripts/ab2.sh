#!/system/bin/sh
# A/B of llama-perplexity and llama-bench built with the app's own flags (KleidiAI, CPU variants
# picked at runtime), CPU unless DEVS says otherwise. Each pair runs back to back with the order
# alternating, after a cooling wait, and the weight buffer sizes are kept to show repacking.
# Usage: sh ab2.sh <out.log> <dirA> <dirB> <model.gguf>...
cd /data/local/tmp/sd
out=$1; A=$2; B=$3; shift 3
hot() { m=0; for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu*|gpu*|nsp*) t=$(cat $z/temp); [ $t -lt 104000 ] && [ $t -gt $m ] && m=$t;; esac; done; echo $m; }
cool() { s=0; while [ $(hot) -gt ${COOL:-55000} ] && [ $s -lt ${COOL_MAX:-120} ]; do sleep 5; s=$((s+5)); done; echo "cool waited=${s}s hot=$(hot)" >> $out; }
run() { # pkg kind dev model
    p=$1; k=$2; d=$3; m=$4
    export LD_LIBRARY_PATH=$PWD/$p ADSP_LIBRARY_PATH=$PWD/$p GGML_HEXAGON_NDEV=1
    ngl=99; [ $d = none ] && ngl=0
    cool
    t0=$(date +%s)
    if [ $k = ppl ]; then
        r=$(timeout 900 ./$p/llama-perplexity -m gguf/$m -f wiki.test.raw -c 512 --chunks ${CHUNKS:-16} -t 6 -dev $d -ngl $ngl 2>&1 | grep -E "Final estimate|error|failed|model buffer size" | sed "s/.*load_tensors: *//" | tr "\n" " ")
    else
        # JSON lines, because a CSV field ("Adreno 840, Hexagon") can itself hold a comma.
        r=$(timeout 900 ./$p/llama-bench -m gguf/$m -p 512 -n 64 -r 3 -t 6 -dev $d -ngl $ngl -o jsonl 2>/dev/null | sed -E 's/.*"n_prompt": *([0-9]+).*"n_gen": *([0-9]+).*"avg_ts": *([0-9.e+-]+).*"stddev_ts": *([0-9.e+-]+).*/pp\1\/tg\2=\3+-\4/' | tr '\n' ' ')
    fi
    echo "$m $k $d $p secs=$(( $(date +%s) - t0 )) end_hot=$(hot) :: $r" >> $out
}
i=0
for m in "$@"; do
    for k in ppl bench; do
        for d in ${DEVS:-none}; do
            i=$((i+1))
            if [ $((i % 2)) = 1 ]; then run $A $k $d $m; run $B $k $d $m; else run $B $k $d $m; run $A $k $d $m; fi
        done
    done
done
echo AB_DONE >> $out
