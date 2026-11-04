#!/system/bin/sh
# Peak memory and speed with KleidiAI on and off, on both versions (a105, a080 with it; k105,
# k080 without), to see whether v0.8.0's decode gain needs the memory KleidiAI's new repacking
# costs. Peak RSS is the llama-bench process's VmHWM, read until it exits.
cd /data/local/tmp/sd
out=ab8.log
hot() { m=0; for z in /sys/class/thermal/thermal_zone*; do case $(cat $z/type) in cpu*|gpu*|nsp*) t=$(cat $z/temp); [ $t -lt 104000 ] && [ $t -gt $m ] && m=$t;; esac; done; echo $m; }
cool() { s=0; while [ $(hot) -gt 55000 ] && [ $s -lt 120 ]; do sleep 5; s=$((s+5)); done; }
one() { # pkg model
    cool
    export LD_LIBRARY_PATH=$PWD/$1
    ./$1/llama-bench -m gguf/$2 -p 512 -n 64 -r 3 -t 6 -dev none -ngl 0 -o jsonl > b8.out 2>/dev/null &
    pid=$!; hwm=0
    while kill -0 $pid 2>/dev/null; do
        v=$(grep VmHWM /proc/$pid/status 2>/dev/null | tr -s ' ' | cut -d' ' -f2); [ -n "$v" ] && hwm=$v
        sleep 0.3
    done
    wait $pid
    r=$(sed -E 's/.*"n_prompt": *([0-9]+).*"n_gen": *([0-9]+).*"avg_ts": *([0-9.e+-]+).*"stddev_ts": *([0-9.e+-]+).*/pp\1\/tg\2=\3+-\4/' b8.out | tr '\n' ' ')
    echo "$2 $1 peak_rss_kb=$hwm end_hot=$(hot) :: $r" >> $out
}
for m in LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf Qwen3.5-2B-Q4_K_M.gguf Qwen3-1.7B-Q4_0.gguf; do
    one a105 $m; one a080 $m; one k080 $m; one k105 $m
done
echo AB8_DONE >> $out
