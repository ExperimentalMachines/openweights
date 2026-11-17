#!/bin/bash
# One Device Streaming session of the MediaTek matrix, start to finish: reserve a Galaxy Tab S10+,
# bridge adb to it, push the bench, the GGUFs and the NPU exports its queue names, run phone/chain.sh
# under a deadline taken from the session's own expiry, copy results into the repository every three
# minutes, optionally run phone/hot.sh in the time left, then copy everything, cancel the session and
# log it in ledger.tsv. Written after session 5, whose leftover pull loop copied the next session's
# unit into its folder, and session 6, whose deadline was first computed by hand and came out wrong.
#   tools/mediatek/session.sh <label> <queue file> <warm-up gguf|-> <hot-probe gguf|->
# GGUFs come from ~/ow-models/gguf-pd, NPU exports (the queue's npu/<name>) from ~/ow-models/mtk-pd/npu.
# PORT (default 5599) is the local adb port of this session's bridge, so sessions can run side by side
# on different units: each touches only its own port's bridge and pull loop.
set -u
LAB=$1; Q=$2; WARM=$3; HOT=$4
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../.." && pwd)
RES=$HERE/results/2026-11-16-mt6989; R=$RES/$LAB; mkdir -p "$R"
GG=$HOME/ow-models/gguf-pd; NPU=$HOME/ow-models/mtk-pd/npu; BENCH=$HOME/ow-models/mtk-pd/bench3; CURL=$HOME/ow-models/mtk-pd/curl
PORT=${PORT:-5599}; S=localhost:$PORT; A="adb -s $S"; ST=$ROOT/tools/eval/streaming
bridge() { (cd "$ST" && uv run --with google-cloud-devicestreaming python ds_bridge.py "$@"); }
log() { echo "$(date -u +%T) $*" | tee -a "$R/session.log"; }
on_dev() { $A shell "$1" 2>/dev/null | tr -d '\r'; }
# A hard limit on one command (macOS has no timeout(1)): an alarm set before exec survives the exec.
tmo() { perl -e 'alarm shift; exec @ARGV' "$@"; }
# Cancel the session and confirm it, or say loudly that it is still billing. The cancel needs a fresh gcloud
# token: on 2026-11-17 (session 15) a Workspace login lapsed mid-run, the one cancel failed, and the session
# billed on idle until a person signed in again. Every exit after the reservation goes through here, and only a
# confirmed cancel closes the ledger row (Codex review, 2026-11-17: a failed cancel used to log SESSION_END).
# Success is the bridge's exit status 0 and its exact line "cancelled <session>" (Codex review round 2: any
# output containing "cancelled", even an error, used to count). Each call has a time limit and the whole
# cleanup ten minutes by the clock, so a hung gcloud or bridge cannot stall it.
cancel_checked() {
  local by=$(( $(date -u +%s) + 600 )) k=0 out rc
  while [ "$(date -u +%s)" -lt $by ]; do k=$((k + 1))
    if tmo 30 gcloud auth print-access-token >/dev/null 2>&1; then
      out=$(cd "$ST" && tmo 90 uv run --with google-cloud-devicestreaming python ds_bridge.py cancel "$SESS" 2>&1); rc=$?
      echo "$out" | tail -1 >> "$R/session.log"
      [ $rc = 0 ] && [ "$(echo "$out" | tail -1)" = "cancelled $SESS" ] && return 0
    fi
    log "CANCEL FAILED for $SESS (try $k): if gcloud wants a login, run gcloud auth login for the session's account"; sleep 30
  done
  log "SESSION STILL ACTIVE: $SESS was not cancelled; cancel it by hand (tools/eval/streaming/ds_bridge.py cancel $SESS)"
  return 1; }
# (The clock is checked before each attempt, so an attempt that starts near ten minutes can run about two and a
# half minutes past them: bounded, not exact.)
# Every ledger write goes through ledger.py, which locks the file, and a failed write fails the run.
ledger() { python3 "$HERE/ledger.py" "$RES/ledger.tsv" "$@"; }
ledger_ok=1
# A failure after the reservation and before the chain: cancel, close the ledger row only if the cancel is
# confirmed (Codex review round 3: a confirmed startup cancel used to leave the row open), and fail.
startup_fail() {
  if cancel_checked; then
    ledger set "${SESS##*session-}" "end=$(date -u +%FT%TZ)" "mins=$(( ($(date -u +%s) - T0 + 59) / 60 ))" "note=cancelled at startup: $1" \
      || log "LEDGER: could not close the row for $SESS"
  else log "SESSION_OPEN $LAB: not cancelled at startup, ledger row left open"; fi
  pkill -f "ds_bridge.py serve $SESS " 2>/dev/null; exit 1; }
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
npus=$(awk '!/^#/ && NF && $2 != "-" {print $2}' "$Q" | sort -u)
for g in $ggufs; do [ -f "$GG/$g" ] || { echo "missing $GG/$g"; exit 1; }; done
for d in $npus; do [ -f "$NPU/${d#npu/}/runner.txt" ] || { echo "missing $NPU/${d#npu/}/runner.txt"; exit 1; }; done

# The bridge needs a fresh gcloud token for the whole session; an expired login (it lapsed on
# 2026-11-16 mid-run) drops the adb link. Refuse to start without one.
gcloud auth print-access-token >/dev/null 2>&1 || { echo "gcloud auth expired: run gcloud auth login, then start again"; exit 1; }

# Nothing of an earlier session may still talk to the port or copy into a folder.
pkill -f "ds_bridge.py serve .* --port $PORT( |$)" 2>/dev/null; pkill -f "mediatek/pull.sh $S " 2>/dev/null; sleep 1

log "reserving for $LAB: $(echo $ggufs | tr '\n' ' ')"
bridge reserve gts10pwifi 36 --ttl 10800 > "$R/reserve.log" 2>&1 &
until grep -q -E "ACTIVE|FAILED|rror" "$R/reserve.log" 2>/dev/null; do sleep 10; done
grep -q ACTIVE "$R/reserve.log" || { log "reserve failed: $(tail -2 "$R/reserve.log")"; exit 1; }
SESS=$(grep -o "projects/[^ ]*/deviceSessions/session-[a-z0-9]*" "$R/reserve.log" | tail -1)
T0=$(date -u +%s)
# The ledger row goes in as soon as the session exists, so a failure before the unit is known still leaves a
# record of something that may be billing (Codex review round 2); the unit is filled in below.
ledger add "$(printf '%s\tgts10pwifi\t%s\t\t\t%s, unit pending: queue %s (session.sh)' "${SESS##*session-}" "$(date -u -r $T0 +%FT%TZ)" "$LAB" "$(basename "$Q")")" \
  || { log "LEDGER: could not add the row for $SESS"; ledger_ok=0; }
(cd "$ST" && nohup uv run --with google-cloud-devicestreaming python ds_bridge.py serve "$SESS" --port $PORT -v > "$R/bridge.log" 2>&1 &)
sleep 8; ready_by=$(( $(date -u +%s) + 300 ))
tmo 10 adb disconnect $S >/dev/null 2>&1; tmo 20 adb connect $S >/dev/null 2>&1
# Wait for a shell that answers, not get-state: on 2026-11-17 (session 16) get-state passed as soon as the
# local bridge accepted, while the unit's first shell still failed, so the push and the chain start failed in
# the same second and the session sat active with nothing running. Reconnect while waiting; give up (and
# cancel, so nothing bills) after five minutes by the clock: every adb call has its own time limit, since one
# hung call would otherwise hold the loop (Codex review, 2026-11-17).
# (An attempt that starts just before the deadline can run about a minute past it: bounded, not exact.)
ready=0
while [ "$(date -u +%s)" -lt $ready_by ]; do
  tmo 20 adb -s $S shell "echo ok" 2>/dev/null | grep -q ok && { ready=1; break; }
  tmo 10 adb disconnect $S >/dev/null 2>&1; tmo 20 adb connect $S >/dev/null 2>&1; sleep 10; done
if [ $ready = 0 ]; then log "no shell answered on $S in five minutes: cancelling $SESS"; startup_fail "no shell answered"; fi
EXP=$(bridge list 2>/dev/null | grep "^$SESS " | awk '{print $4" "$5}' | cut -c1-19)
E=$(date -j -u -f "%Y-%m-%d %H:%M:%S" "$EXP" +%s) || { log "no expiry for $SESS: cancelling"; startup_fail "no expiry"; }
UNIT=$(on_dev 'getprop ro.serialno | sha256sum | cut -c1-8')
log "active $SESS unit $UNIT expires $EXP UTC"
ledger set "${SESS##*session-}" "unit=$UNIT" || { log "LEDGER: could not fill in the unit for $SESS"; ledger_ok=0; }

"$HERE/push.sh" $S "$BENCH" "$CURL" $(for d in $npus; do echo "$NPU/${d#npu/}"; done) > "$R/push.log" 2>&1 || { log "push failed"; tail -5 "$R/push.log"; }
for g in $ggufs; do $A push "$GG/$g" /data/local/tmp/mtk/gguf/ > /dev/null 2>&1 || log "gguf push failed: $g"; done
$A push "$Q" /data/local/tmp/mtk/ > /dev/null
for g in $ggufs; do on_dev "cd /data/local/tmp/mtk; echo \"$g \$(stat -c %s gguf/$g) \$(sha256sum gguf/$g | cut -c1-16) \$(date +%T) pushed\" >> dl.log"; done
log "pushed; dl.log: $(on_dev 'cat /data/local/tmp/mtk/dl.log' | tr '\n' ';')"

# HOLD=1 waits for a file named "go" in the results folder before starting the chain, so a probe can
# run on the idle unit first and the queue on the unit be replaced from what it measured.
if [ -n "${HOLD:-}" ]; then
  log "holding: touch $R/go to start the chain (replace /data/local/tmp/mtk/$(basename "$Q") first if needed)"
  until [ -e "$R/go" ]; do $A get-state >/dev/null 2>&1 || { log "device gone while holding"; break; }; sleep 10; done
fi
# The chain stops admitting models 8 min before the end; hot.sh plans its heats to end 3.5 min before.
CHAIN_DL=$((E - 480)); HOT_DL=$((E - 210))
on_dev "cd /data/local/tmp/mtk; nohup setsid sh mtklog.sh mtklog-$LAB.txt > /dev/null 2>&1 < /dev/null & sleep 1; DEADLINE=$CHAIN_DL nohup setsid sh chain.sh $LAB $WARM $(basename "$Q") > chain-$LAB.out 2>&1 < /dev/null &" > /dev/null
log "chain started, admits until $(date -u -r $CHAIN_DL +%T) UTC"
"$HERE/pull.sh" $S "$R" 180 > "$R/pull.log" 2>&1 &
PULL=$!

gone=0
until on_dev "grep -q CHAIN_DONE /data/local/tmp/mtk/chain-$LAB.log && echo y" | grep -q y; do
  alive || { gone=1; log "device gone after two minutes of reconnecting"; break; }
  [ "$(date -u +%s)" -gt "$E" ] && { gone=1; log "past expiry"; break; }
  sleep 60
done
[ $gone = 0 ] && log "chain done: $(on_dev "grep -c ' end ' /data/local/tmp/mtk/chain-$LAB.log") processes, $(on_dev "grep -c ' skip ' /data/local/tmp/mtk/chain-$LAB.log") skipped"

if [ $gone = 0 ] && [ "$HOT" != "-" ]; then
  on_dev "cd /data/local/tmp/mtk; DEADLINE=$HOT_DL MAXHEAT=${MAXHEAT:-1200} nohup setsid sh hot.sh ${LAB}hot $HOT > hot-${LAB}hot.out 2>&1 < /dev/null &" > /dev/null
  log "hot.sh started with $(( (HOT_DL - $(date -u +%s)) / 60 )) min to its deadline"
  until on_dev "grep -q HOT_DONE /data/local/tmp/mtk/hot-${LAB}hot.log && echo y" | grep -q y; do
    alive || { gone=1; log "device gone after two minutes of reconnecting"; break; }
    [ "$(date -u +%s)" -gt $((HOT_DL + 60)) ] && { log "hot.sh past its deadline"; break; }
    sleep 30
  done
fi

kill $PULL 2>/dev/null
if [ $gone = 0 ]; then
  (cd "$R" && $A exec-out 'cd /data/local/tmp/mtk && tar cf - *.jsonl *.txt *.log *.out 2>/dev/null' | tar xf - 2>/dev/null)
  for f in $(on_dev 'ls /data/misc/perfetto-traces/' | grep "^${LAB}hot"); do $A pull "/data/misc/perfetto-traces/$f" "$R/$f" > /dev/null 2>&1; done
  log "pulled: $(ls "$R" | wc -l | tr -d ' ') files"
fi
closed=0
# Deliberately not cancelled when the link is lost but the server still lists the session as active: the chain
# may still be running on the unit with results not yet pulled. The ledger row stays open and the script fails
# below, so a person pulls and cancels.
if [ $gone = 1 ] && active; then
  log "NOT cancelling: the link was lost but the server still lists the session as active (pull and cancel by hand)"
else
  cancel_checked && closed=1
fi
pkill -f "ds_bridge.py serve $SESS " 2>/dev/null
T1=$(date -u +%s)
# A session that was not cancelled is still billing: leave its ledger row open and fail.
[ $closed = 1 ] || { log "SESSION_OPEN $LAB: not cancelled, ledger row left open"; exit 1; }
ledger set "${SESS##*session-}" "end=$(date -u -r $T1 +%FT%TZ)" "mins=$(( (T1 - T0 + 59) / 60 ))" \
  || { log "LEDGER NOT UPDATED for $SESS: cancelled, but its row is still open"; exit 1; }
log "SESSION_END $LAB $(( (T1 - T0) / 60 )) min"
[ $ledger_ok = 1 ] || { log "LEDGER: an earlier write failed for $SESS; check ledger.tsv"; exit 1; }
