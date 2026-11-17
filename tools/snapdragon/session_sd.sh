#!/bin/bash
# One Device Streaming session of the Snapdragon matrix on a retail phone (tools/mediatek/session.sh
# with the Snapdragon package): reserve the phone, bridge adb to it, push pd_bench_sd and its
# llama.cpp libraries (CPU, OpenCL, Hexagon), the GGUFs its queue names, run phone_sd/chain_sd.sh
# under a deadline taken from the session's own expiry, copy results into the repository every three
# minutes, optionally run phone/hot.sh in the time left, then copy everything, cancel the session and
# log it in ledger.tsv. Written after session 5, whose leftover pull loop copied the next session's
# unit into its folder, and session 6, whose deadline was first computed by hand and came out wrong.
#   DEVICE=e3q tools/snapdragon/session_sd.sh <label> <queue file> <warm-up gguf|-> <hot-probe gguf|->
# GGUFs come from ~/ow-models/gguf-pd; the package (pkg/bin/pd_bench_sd, pkg/lib/*.so) from ~/ow-models/sd-pd/pkg.
# PORT (default 5599) is the local adb port of this session's bridge, so sessions can run side by side
# on different units: each touches only its own port's bridge and pull loop.
set -u
LAB=$1; Q=$2; WARM=$3; HOT=$4
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../.." && pwd)
RES=$HERE/results/2026-11-16-s24; R=$RES/$LAB; mkdir -p "$R"
GG=$HOME/ow-models/gguf-pd; PKG=$HOME/ow-models/sd-pd/pkg; DEVICE=${DEVICE:-e3q}; ANDROID=${ANDROID:-36}; D=/data/local/tmp/sd
PORT=${PORT:-5599}; S=localhost:$PORT; A="adb -s $S"; ST=$ROOT/tools/eval/streaming
bridge() { (cd "$ST" && uv run --with google-cloud-devicestreaming python ds_bridge.py "$@"); }
log() { echo "$(date -u +%T) $*" | tee -a "$R/session.log"; }
on_dev() { $A shell "$1" 2>/dev/null | tr -d '\r'; }
# One failed adb call is not a lost device: on 2026-11-16 the S24 session's adb link closed while the
# phone and session were fine, and the driver cancelled at once, losing two models. Reconnect for
# about two minutes before giving up.
# A shell that answers is the test (on 2026-11-16 get-state said "device" while every shell failed with
# "closed"). Half way through, the bridge itself is restarted, since the link it holds can die alone.
active() { bridge list 2>/dev/null | grep -q "^$SESS ACTIVE"; }
restart_bridge() { pkill -f "ds_bridge.py serve $SESS " 2>/dev/null; sleep 2
  (cd "$ST" && nohup uv run --with google-cloud-devicestreaming python ds_bridge.py serve "$SESS" --port $PORT -v >> "$R/bridge.log" 2>&1 &); sleep 8; }
alive() { for k in 1 2 3 4 5 6; do on_dev "echo ok" | grep -q ok && return 0; [ $k = 3 ] && { log "adb link down: restarting the bridge"; restart_bridge; }
  adb disconnect $S >/dev/null 2>&1; adb connect $S >/dev/null 2>&1; sleep 20; done
  # Still down after two minutes: only a session the server no longer lists as active counts as gone.
  if active; then log "adb link down but the session is still active: keep waiting"; restart_bridge; return 0; fi
  return 1; }

# Inputs first, so a missing file fails before any money is spent.
ggufs=$( (awk '!/^#/ && NF {print $1}' "$Q"; echo "$WARM"; echo "$HOT") | grep -v '^-$' | sort -u)
for g in $ggufs; do [ -f "$GG/$g" ] || { echo "missing $GG/$g"; exit 1; }; done
[ -x "$PKG/bin/pd_bench_sd" ] || { echo "missing $PKG/bin/pd_bench_sd"; exit 1; }

# The bridge needs a fresh gcloud token for the whole session; an expired login (it lapsed on
# 2026-11-16 mid-run) drops the adb link. Refuse to start without one.
gcloud auth print-access-token >/dev/null 2>&1 || { echo "gcloud auth expired: run gcloud auth login, then start again"; exit 1; }

# Nothing of an earlier session may still talk to the port or copy into a folder.
pkill -f "ds_bridge.py serve .* --port $PORT( |$)" 2>/dev/null; pkill -f "snapdragon/pull_sd.sh $S " 2>/dev/null; sleep 1

if [ -n "${RESUME:-}" ]; then
  # Take over a session whose driver stopped after its push (RESUME=<session name>): restart the
  # bridge, read the expiry and unit again, and go straight to the chain.
  SESS=$RESUME
  T0=$(date -j -u -f "%Y-%m-%dT%H:%M:%S" "$(bridge list --all 2>/dev/null | grep "^$SESS " >/dev/null; awk -F'\t' -v s="${SESS##*session-}" '$1 == s {print substr($3, 1, 19)}' "$RES/ledger.tsv")" +%s 2>/dev/null || date -u +%s)
  (cd "$ST" && nohup uv run --with google-cloud-devicestreaming python ds_bridge.py serve "$SESS" --port $PORT -v > "$R/bridge-resume.log" 2>&1 &)
  sleep 8; adb disconnect $S >/dev/null 2>&1; adb connect $S >/dev/null
  for i in $(seq 1 30); do $A get-state >/dev/null 2>&1 && break; sleep 5; done
  EXP=$(bridge list 2>/dev/null | grep "^$SESS " | awk '{print $4" "$5}' | cut -c1-19)
  E=$(date -j -u -f "%Y-%m-%d %H:%M:%S" "$EXP" +%s) || { log "no expiry for $SESS"; exit 1; }
  UNIT=$(on_dev 'getprop ro.serialno | sha256sum | cut -c1-8')
  log "resumed $SESS unit $UNIT expires $EXP UTC"
else
log "reserving for $LAB: $(echo $ggufs | tr '\n' ' ')"
bridge reserve $DEVICE $ANDROID --ttl 10800 > "$R/reserve.log" 2>&1 &
until grep -q -E "ACTIVE|FAILED|rror" "$R/reserve.log" 2>/dev/null; do sleep 10; done
grep -q ACTIVE "$R/reserve.log" || { log "reserve failed: $(tail -2 "$R/reserve.log")"; exit 1; }
SESS=$(grep -o "projects/[^ ]*/deviceSessions/session-[a-z0-9]*" "$R/reserve.log" | tail -1)
T0=$(date -u +%s)
(cd "$ST" && nohup uv run --with google-cloud-devicestreaming python ds_bridge.py serve "$SESS" --port $PORT -v > "$R/bridge.log" 2>&1 &)
sleep 8; adb disconnect $S >/dev/null 2>&1; adb connect $S >/dev/null
for i in $(seq 1 30); do $A get-state >/dev/null 2>&1 && break; sleep 5; done
EXP=$(bridge list 2>/dev/null | grep "^$SESS " | awk '{print $4" "$5}' | cut -c1-19)
E=$(date -j -u -f "%Y-%m-%d %H:%M:%S" "$EXP" +%s) || { log "no expiry for $SESS"; exit 1; }
UNIT=$(on_dev 'getprop ro.serialno | sha256sum | cut -c1-8')
log "active $SESS unit $UNIT expires $EXP UTC"
printf '%s\t'"$DEVICE"'\t%s\t\t\t%s, unit %s: queue %s (session.sh)\n' "${SESS##*session-}" "$(date -u -r $T0 +%FT%TZ)" "$LAB" "$UNIT" "$(basename "$Q")" >> "$RES/ledger.tsv"

$A shell "mkdir -p $D/pkg/bin $D/pkg/lib $D/gguf" > /dev/null
$A push "$PKG/bin/." $D/pkg/bin/ > "$R/push.log" 2>&1 && $A push "$PKG/lib/." $D/pkg/lib/ >> "$R/push.log" 2>&1 && $A push "$HERE/phone_sd/." $D/ >> "$R/push.log" 2>&1 || { log "push failed"; tail -5 "$R/push.log"; }
on_dev "chmod 755 $D/pkg/bin/*" > /dev/null
$A push "$Q" $D/ > /dev/null
# A model is marked ready (dl.log, which the chain waits on) only when its copy on the phone has the
# Mac's size and checksum prefix (Codex review: a failed push used to be marked ready anyway).
for g in $ggufs; do
  $A push "$GG/$g" $D/gguf/ > /dev/null 2>&1 || log "gguf push failed: $g"
  want="$(stat -f %z "$GG/$g") $(shasum -a 256 "$GG/$g" | cut -c1-16)"
  got=$(on_dev "cd $D; echo \$(stat -c %s gguf/$g) \$(sha256sum gguf/$g | cut -c1-16)")
  if [ "$got" = "$want" ]; then on_dev "cd $D; echo \"$g $got \$(date +%T) pushed\" >> dl.log" > /dev/null; else log "gguf on the phone differs: $g ($got, want $want)"; fi
done
log "pushed; dl.log: $(on_dev "cat $D/dl.log" | tr '\n' ';')"

# HOLD=1 waits for a file named "go" in the results folder before starting the chain, so a probe can
# run on the idle unit first and the queue on the unit be replaced from what it measured.
if [ -n "${HOLD:-}" ]; then
  log "holding: touch $R/go to start the chain (replace $D/$(basename "$Q") first if needed)"
  until [ -e "$R/go" ]; do $A get-state >/dev/null 2>&1 || { log "device gone while holding"; break; }; sleep 10; done
fi
fi
# The chain stops admitting models 8 min before the end; hot.sh plans its heats to end 3.5 min before.
CHAIN_DL=$((E - 480)); HOT_DL=$((E - 210))
on_dev "cd $D; nohup setsid sh sdlog.sh sdlog-$LAB.txt > /dev/null 2>&1 < /dev/null & sleep 1; DEADLINE=$CHAIN_DL nohup setsid sh chain_sd.sh $LAB $WARM $(basename "$Q") > chain-$LAB.out 2>&1 < /dev/null &" > /dev/null
log "chain started, admits until $(date -u -r $CHAIN_DL +%T) UTC"
"$HERE/pull_sd.sh" $S "$R" 180 > "$R/pull.log" 2>&1 &
PULL=$!

gone=0
until on_dev "grep -q CHAIN_DONE $D/chain-$LAB.log && echo y" | grep -q y; do
  alive || { gone=1; log "device gone after two minutes of reconnecting"; break; }
  [ "$(date -u +%s)" -gt "$E" ] && { gone=1; log "past expiry"; break; }
  sleep 60
done
[ $gone = 0 ] && log "chain done: $(on_dev "grep -c ' end ' $D/chain-$LAB.log") processes, $(on_dev "grep -c ' skip ' $D/chain-$LAB.log") skipped"

if [ $gone = 0 ] && [ "$HOT" != "-" ]; then
  on_dev "cd $D; DEADLINE=$HOT_DL MAXHEAT=${MAXHEAT:-1200} nohup setsid sh hot_sd.sh ${LAB}hot $HOT > hot-${LAB}hot.out 2>&1 < /dev/null &" > /dev/null
  log "hot.sh started with $(( (HOT_DL - $(date -u +%s)) / 60 )) min to its deadline"
  until on_dev "grep -q HOT_DONE $D/hot-${LAB}hot.log && echo y" | grep -q y; do
    alive || { gone=1; log "device gone after two minutes of reconnecting"; break; }
    [ "$(date -u +%s)" -gt $((HOT_DL + 60)) ] && { log "hot.sh past its deadline"; break; }
    sleep 30
  done
fi

kill $PULL 2>/dev/null
if [ $gone = 0 ]; then
  (cd "$R" && $A exec-out "cd $D && tar cf - *.jsonl *.txt *.log *.out 2>/dev/null" | tar xf - 2>/dev/null)
  for f in $(on_dev 'ls /data/misc/perfetto-traces/' | grep "^${LAB}hot"); do $A pull "/data/misc/perfetto-traces/$f" "$R/$f" > /dev/null 2>&1; done
  log "pulled: $(ls "$R" | wc -l | tr -d ' ') files"
fi
if [ $gone = 1 ] && active; then
  log "NOT cancelling: the link was lost but the server still lists the session as active (pull and cancel by hand)"
else
  bridge cancel "$SESS" 2>&1 | tail -1 | tee -a "$R/session.log"
fi
pkill -f "ds_bridge.py serve $SESS " 2>/dev/null
T1=$(date -u +%s)
python3 - "$RES/ledger.tsv" "${SESS##*session-}" "$(date -u -r $T1 +%FT%TZ)" $(( (T1 - T0 + 59) / 60 )) <<'EOF'
import sys
p, sid, end, mins = sys.argv[1:]
rows = [l.rstrip("\n").split("\t") for l in open(p)]
for r in rows:
    if r[0] == sid:
        r += [""] * (6 - len(r))
        r[3], r[4] = end, mins
open(p, "w").write("".join("\t".join(r) + "\n" for r in rows))
EOF
log "SESSION_END $LAB $(( (T1 - T0) / 60 )) min"
