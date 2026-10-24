#!/bin/sh
# PrefetchMatrixBenchmark on the phone: every model, in every conversation shape, at every
# level of outside memory pressure.
#
#   tools/memory/run_prefetch_matrix.sh <adb-serial>
#     MODELS=a.gguf,b.gguf   files in /data/local/tmp/openweights
#     LEVELS="4096 0"        megabytes the balloon holds (0: none), run in this order
#     CELLS="single:false single:true multi:false multi:true"   shape:tools
#     PAIRS, BLOCKS, DEPTH, GAP, LEAD, SEED, CONTEXT, ANSWER   passed to the test
#     CELL_DEADLINE=5400     seconds before a cell is stopped and recorded as timed out
#     OUT=dir                where the reports land (default: a new directory per run)
#
# Within each level the cells, and within each cell the models, run in an order shuffled from
# SEED, so no cell or model always meets the phone later in the run. A level whose balloon does
# not come up is recorded as such and its cells are not run. Use the mDNS serial: the ip:port
# one can vanish mid-run, and an adb call to a serial that has gone hangs for good, so every
# call here has a time limit. The instrumentation runs detached; a dropped connection loses no
# data. Reports are collected after every cell into a directory named for this run.
set -eu
SERIAL=$1
PKG=io.github.alpharomercoma.openweights.core.engine.test
RUNNER=androidx.test.runner.AndroidJUnitRunner
CLASS=io.github.alpharomercoma.openweights.core.engine.PrefetchMatrixBenchmark
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
APK="$ROOT/core/engine/build/outputs/apk/androidTest/debug/engine-debug-androidTest.apk"
RUN=${RUN:-$(date +%Y%m%d-%H%M)}
OUT=${OUT:-$HERE/results/matrix-$RUN}
LEVELS=${LEVELS:-"4096 0"}
CELLS=${CELLS:-"single:false single:true multi:false multi:true"}
SEED=${SEED:-20261024}
COOL=${COOL:-60}
CELL_DEADLINE=${CELL_DEADLINE:-5400}
mkdir -p "$OUT"
STATUS="$OUT/cells.tsv"
printf 'level\tcell\tmodels\tstatus\tstarted\tended\n' > "$STATUS"

adbt() { limit=$1; shift; perl -e 'alarm shift; exec @ARGV' "$limit" adb -s "$SERIAL" "$@"; }
shuffle() { perl -MList::Util=shuffle -e 'srand(shift); print join(" ", shuffle @ARGV)' "$@"; }

cleanup() { adbt 30 shell "kill \$(pidof balloon) 2>/dev/null; am force-stop $PKG" >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

SDK=$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties")
CC=$(ls "$SDK"/ndk/*/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android31-clang | tail -1)
BUILD=$(mktemp -d)
"$CC" -O2 -o "$BUILD/balloon" "$HERE/balloon.c"

adbt 30 shell "settings put system screen_off_timeout 2147483647; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard" >/dev/null 2>&1 || true
adbt 30 shell "cmd deviceidle whitelist +$PKG; am set-standby-bucket $PKG active; cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow" >/dev/null 2>&1 || true
adbt 60 push "$BUILD/balloon" /data/local/tmp/balloon >/dev/null
adbt 30 shell chmod 755 /data/local/tmp/balloon
adbt 900 push "$APK" /data/local/tmp/owtest.apk >/dev/null
adbt 120 shell pm install -r -t --user 0 /data/local/tmp/owtest.apk >/dev/null

collect() {
  TMP=$(mktemp -d)
  for f in $(adbt 60 shell "ls /sdcard/Android/data/$PKG/files/eval-results/ | grep '\.$RUN\..*\.prefetch\.json\$'" 2>/dev/null); do
    adbt 120 pull "/sdcard/Android/data/$PKG/files/eval-results/$f" "$TMP/" >/dev/null 2>&1 && cp "$TMP/$f" "$OUT/"
  done
}

for level in $LEVELS; do
  label="b$level"
  adbt 30 shell "kill \$(pidof balloon) 2>/dev/null; rm -f /data/local/tmp/balloon.log" || true
  if [ "$level" -gt 0 ]; then
    adbt 30 shell "nohup /data/local/tmp/balloon $level 2 > /data/local/tmp/balloon.log 2>&1 &" || true
    n=0; ready=no
    while [ $n -lt 60 ]; do
      if adbt 20 shell "grep -q ready /data/local/tmp/balloon.log" 2>/dev/null; then ready=yes; break; fi
      sleep 5; n=$((n + 1))
    done
    if [ $ready = no ]; then
      for cell in $CELLS; do printf '%s\t%s\t-\tballoon not ready\t%s\t%s\n' "$label" "$cell" "$(date +%s)" "$(date +%s)" >> "$STATUS"; done
      echo "== $label balloon not ready; level skipped"
      continue
    fi
  fi
  for cell in $(shuffle "$((SEED + level))" $CELLS); do
    shape=${cell%%:*}; tools=${cell##*:}
    models=$(shuffle "$((SEED + level + ${#cell}))" $(echo "$MODELS" | tr ',' ' ') | tr ' ' ',')
    tag="$label-$shape-$tools"
    started=$(date +%s)
    echo "== $tag $(date +%H:%M) models $models $(adbt 20 shell "grep MemAvailable /proc/meminfo | tr -s ' '" 2>/dev/null)"
    adbt 20 logcat -c || true
    # Streamed to a file on the phone for the whole cell: the ring buffer read at the end
    # wrapped under the balloon and lost the first 13 to 30 minutes of every 4 GB cell.
    adbt 20 shell "nohup logcat -v epoch -s OpenWeightsPrefetch:I OpenWeights:I lowmemorykiller:I > /data/local/tmp/matrix-$tag.logcat 2>&1 & echo \$! > /data/local/tmp/matrix-$tag.logpid" || true
    adbt 30 shell "nohup am instrument -r -e class $CLASS -e run $RUN -e label $label -e shape $shape -e tools $tools -e models $models -e seed $SEED ${PAIRS:+-e pairs $PAIRS} ${BLOCKS:+-e blocks $BLOCKS} ${DEPTH:+-e depth $DEPTH} ${GAP:+-e gap $GAP} ${LEAD:+-e lead $LEAD} ${CONTEXT:+-e context $CONTEXT} ${ANSWER:+-e answer $ANSWER} $PKG/$RUNNER > /data/local/tmp/matrix-$tag.log 2>&1 &" || true
    n=0; until adbt 20 shell pidof $PKG >/dev/null 2>&1 || [ $n -ge 12 ]; do sleep 5; n=$((n + 1)); done
    # Two answers in a row saying the process is gone, or the deadline; a failed check
    # (connection gone) is not taken as the run having ended.
    gone=0; status=done
    while [ $gone -lt 2 ]; do
      sleep 20
      if out=$(adbt 20 shell "pidof $PKG || echo none" 2>/dev/null); then
        case "$out" in *none*) gone=$((gone + 1));; *) gone=0;; esac
      fi
      if [ $(( $(date +%s) - started )) -gt "$CELL_DEADLINE" ]; then
        status="timed out"; adbt 30 shell "am force-stop $PKG" || true; break
      fi
    done
    alive=$(adbt 20 shell "pidof balloon >/dev/null && echo alive || echo gone" 2>/dev/null || echo unknown)
    [ "$level" -gt 0 ] && [ "$alive" != alive ] && status="$status; balloon $alive"
    result=$(adbt 20 shell "grep -E 'INSTRUMENTATION_CODE|shortMsg' /data/local/tmp/matrix-$tag.log | tr '\n' ' '" 2>/dev/null || echo unread)
    printf '%s\t%s\t%s\t%s (%s)\t%s\t%s\n' "$label" "$cell" "$models" "$status" "$result" "$started" "$(date +%s)" >> "$STATUS"
    adbt 20 shell "kill \$(cat /data/local/tmp/matrix-$tag.logpid)" 2>/dev/null || true
    adbt 120 shell cat /data/local/tmp/matrix-$tag.logcat > "$OUT/$tag.logcat.txt" 2>/dev/null || true
    collect
    echo "   $status $result"
  done
  adbt 30 shell "kill \$(pidof balloon) 2>/dev/null" || true
  sleep "$COOL"
done
collect
echo "== done $(date +%H:%M) run $RUN in $OUT"
