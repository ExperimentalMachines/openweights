# The MediaTek prefill/decode matrix on the streamed unit, the Snapdragon chain's protocol
# (results-r12..r16): reply-length turns of conversations.jsonl, --gen 2048 --gen-ref, 6 turns per
# conversation, 6 threads, a 55 C gate with 120 s at most, one throwaway warm-up pass first.
#   nohup setsid sh chain.sh <label> <warmup model|-> <queue file> > chain-<label>.out 2>&1 < /dev/null &
# (nohup as well as setsid: a setsid-only logger died about 24 s after its adb shell ended.)
# Every process of a model gets the same --window/--pad-batch (the NPU exports: 2048 and 128), so
# all nine cells read the same turns whichever processors a process holds.
# Each queue line: <gguf> <npu dir|-> <cells> [estimate]; one bench process per line, so a line holds only
# the processors its cells need (memory: see the run notes).
cd /data/local/tmp/mtk
export LD_LIBRARY_PATH=$PWD/bench:/system_ext/lib64:/vendor/lib64
lab=$1; warm=$2; queue=$3
log=chain-$lab.log
env_line() { echo "{\"kind\":\"env\",\"label\":\"$1\",\"model\":\"$2\",\"when\":\"$3\",\"time\":$(date +%s),\"soc\":\"$(getprop ro.soc.model)\",\"temps\":\"$(dumpsys thermalservice | sed -n '/Current temperatures from HAL/,/Current cooling/p' | grep -o 'mValue=[0-9.-]*, mType=[0-9]*, mName=[A-Z]*' | sed 's/mValue=\([0-9.-]*\).*mName=\(.*\)/\2=\1/' | tr '\n' ' ')\",\"memavail_kb\":$(grep MemAvailable /proc/meminfo | tr -s ' ' | cut -d' ' -f2)}" >> results-$1.jsonl; }
wait_dl() { while ! grep -q "^$1 " dl.log 2>/dev/null; do sleep 5; done; }
run() { l=$1; m=$2; npu=$3; cells=$4; extra=$5
  wait_dl $m
  # Every run names what it ran: the binary and libraries, the GGUF's checksum prefix, and the NPU
  # export's config and runner (Codex review, round two: one hash at the top proves nothing later).
  ex="-"; [ "$npu" != "-" ] && ex="$(cat $npu/config.json $npu/runner.txt | sha256sum | cut -c1-16)"
  echo "$(date +%T) start $l $m $npu $cells gguf=$(grep "^$m " dl.log | tail -1 | cut -d' ' -f3) bin=$(sha256sum bench/mtk_pd_bench | cut -c1-16) libs=$(cat bench/*.so | sha256sum | cut -c1-16) npu=$ex window=${WINDOW:-2048}" >> $log
  env_line $l $m before
  nd=""; [ "$npu" != "-" ] && nd="--npu-dir $npu"
  tag=$l-$m-$(echo $cells | tr ':,' '-_')
  ./bench/mtk_pd_bench -m gguf/$m -c conversations.jsonl -o results-$l.jsonl --label $l $nd \
    --cells "$cells" --gen 2048 --gen-ref --ctx 8192 --threads 6 --max-turns 6 --cool 55 --cool-max 120 --window ${WINDOW:-2048} --pad-batch 128 $extra \
    > log-$tag.txt 2>&1 &
  bp=$!
  # Where each of the bench's threads last ran, every 2 s: a CPU decode step that stalls can then
  # be matched with its threads crowded onto fewer cores (a pause the shell cannot read otherwise;
  # Qwen3 0.6B's cpu:cpu had six steps of 0.7 to 1.7 s in session 5 with all cores "online").
  nohup setsid sh place.sh $bp place-$tag.txt > /dev/null 2>&1 < /dev/null &
  # And a floor under available memory (memguard.sh), so a growing NPU buffer cannot panic the kernel.
  nohup setsid sh memguard.sh $bp memguard-$l.txt > /dev/null 2>&1 < /dev/null &
  wait $bp
  rc=$?
  env_line $l $m after
  echo "$(date +%T) end $l $m rc $rc" >> $log; }
echo "$(date +%T) soc $(getprop ro.soc.model) model $(getprop ro.product.model) bin $(sha256sum bench/mtk_pd_bench | cut -c1-16)" >> $log
# A file named nowarm skips the warm-up, for a unit a probe has already run on (session 9 on).
[ "$warm" != "-" ] && [ ! -e nowarm ] && run ${lab}warm $warm - cpu:cpu
[ -e nowarm ] && echo "$(date +%T) warm-up skipped: nowarm (a probe ran first)" >> $log
# A queue line may end with an estimate in minutes (split_queue.py: the time left for the whole
# model from that line on). With DEADLINE set (epoch seconds, the session's end less a margin) a
# model is admitted or refused once, at its first line, after its GGUF has arrived: an admitted
# model runs all its lines, a refused one none, because a model's cells must all run on one unit
# and a partial set would be rerun anyway (Codex review, round three: a per-line check let a
# refused model's later lines run, and could cut an admitted model short). A line without a
# number for an estimate is refused while DEADLINE is set. Anything after the estimate (up to a #) is
# passed to the bench: `-c <file>` for a model run on a subset of the conversations.
admitted=" "; refused=" "
while read -r m npu cells est rest || [ -n "$m" ]; do
  [ -z "$m" ] && continue
  case "$m" in \#*) continue;; esac
  case "$refused" in *" $m "*) echo "$(date +%T) skip $lab $m $cells: model refused earlier" >> $log; continue;; esac
  case "$admitted" in *" $m "*) ;; *)
    if [ -n "$DEADLINE" ]; then
      case "$est" in ''|*[!0-9]*) echo "$(date +%T) skip $lab $m: no estimate with a deadline set" >> $log; refused="$refused$m "; continue;; esac
      wait_dl $m
      if [ $(( $(date +%s) + est * 60 )) -gt "$DEADLINE" ]; then
        echo "$(date +%T) skip $lab $m: ${est} min would pass the session end" >> $log; refused="$refused$m "; continue
      fi
    fi
    admitted="$admitted$m ";;
  esac
  run $lab $m $npu $cells "${rest%%#*}"
done < $queue
echo CHAIN_DONE >> $log
