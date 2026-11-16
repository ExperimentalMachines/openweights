# Sourced by the poco_*.sh scripts. The phone is reached over USB, or over wireless debugging
# (WIFI=<ip:port>) whenever USB is down: on 2026-11-16 the POCO dropped off USB three times, once a
# few seconds into a test. Each test runs on the phone in its own session, so a dropped
# transport no longer ends the test, and its log is written on the phone for the same reason.
# Files come back checked before the phone's copy is deleted. Not handled: an interrupted script
# leaves its log writer and a stay-on setting on the phone (stop_log and `svc power stayon false`
# by hand), and nothing here sees another project computing on the same phone.
# The phone's own serial, kept apart from S: pick() sets S to whichever transport is up, and a
# queue that exported S would otherwise hand the wireless address to the next script as the serial.
USB=${USB_SERIAL:-${S:?set S to the adb serial of the phone}}
export USB_SERIAL=$USB
WIFI=${WIFI:-}
T=io.github.alpharomercoma.openweights.core.engine.test
PKG=io.github.alpharomercoma.openweights.core.engine
RUNNER=$T/androidx.test.runner.AndroidJUnitRunner
R=/sdcard/Android/data/$T/files/eval-results
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

wait_s() { perl -e "select(undef,undef,undef,$1)"; }

# Sets S to a live transport, USB first, waiting up to two minutes; fails when there is none.
# The wireless transport must report the USB serial: another phone on the network is not this one.
pick() {
  local i
  for i in $(seq 1 24); do
    if [ "$(adb -s "$USB" get-state 2>/dev/null)" = device ]; then S=$USB; return 0; fi
    if [ -n "$WIFI" ]; then
      [ "$(adb -s "$WIFI" get-state 2>/dev/null)" = device ] || adb connect "$WIFI" >/dev/null 2>&1
      if [ "$(adb -s "$WIFI" get-state 2>/dev/null)" = device ]; then
        if [ "$(adb -s "$WIFI" shell getprop ro.serialno 2>/dev/null | tr -d '\r')" = "$USB" ]; then
          S=$WIFI
          return 0
        fi
        echo "== $WIFI is not $USB; not using it" >&2
      fi
    fi
    wait_s 5
  done
  echo "== no transport to the phone for two minutes ($(date -u +%FT%TZ))"
  return 1
}

# One shell call on whichever transport is up.
on() { pick && adb -s "$S" shell "$@"; }

# A .hold file beside these scripts parks the next test or push, so a run can be slotted in
# between two steps of a queue that is already going, or the phone handed to someone else.
# HOLD_IGNORE=1 is the slotted run.
wait_hold() { [ -n "${HOLD_IGNORE:-}" ] || while [ -f "$HERE/.hold" ]; do wait_s 10; done; }

# Copies a file to the phone once nothing holds it.
push() { wait_hold; pick && adb -s "$S" push "$1" "$2"; }

# fetch <remote file> <local file> [jsonl]: copies a file off the phone into a temporary file,
# checks the copy (the call succeeded, the byte counts match, and with `jsonl` every line parses),
# moves it into place, and only then deletes the phone's copy. A remote file that does not exist
# leaves no local file and is not an error; a failed check keeps both copies and fails.
fetch() {
  local remote=$1 dest=$2 kind=${3:-} tmp size got
  size=$(on "if [ -f '$remote' ]; then wc -c < '$remote'; else echo none; fi" | tr -d '\r ') || return 1
  [ "$size" = none ] && return 0
  tmp=$(mktemp "$dest.part.XXXX")
  on "cat '$remote'" > "$tmp" || { echo "== fetch $remote failed; kept on the phone" >&2; return 1; }
  got=$(wc -c < "$tmp" | tr -d ' ')
  if [ "$got" != "$size" ]; then
    echo "== fetch $remote: $got of $size bytes; kept on the phone, partial copy at $tmp" >&2
    return 1
  fi
  if [ "$kind" = jsonl ] &&
    ! python3 -c 'import json,sys; [json.loads(l) for l in open(sys.argv[1]) if l.strip()]' "$tmp"; then
    echo "== fetch $remote: not valid JSON lines; kept on the phone, copy at $tmp" >&2
    return 1
  fi
  mv "$tmp" "$dest" && on "rm -f '$remote'"
}

# Screen on and unlocked, the test package exempt from idle. Over wireless the screen is kept
# on regardless of power, because a screen-off run on this phone is throttled two to five times.
awake() {
  wait_hold
  pick || return 1
  local mode=usb
  [ "$S" = "$USB" ] || mode=true
  on "svc power stayon $mode; input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard" || return 1
  on "cmd deviceidle whitelist +$T; am set-standby-bucket $T active; cmd appops set $T RUN_ANY_IN_BACKGROUND allow" >/dev/null
}

# start_log <name> <tag:level ...>: clears the log and writes the filtered lines on the phone.
start_log() {
  on "logcat -c; rm -f /data/local/tmp/ow-log-$1.txt; setsid nohup logcat -v threadtime -s $2 > /data/local/tmp/ow-log-$1.txt 2>/dev/null < /dev/null & echo \$! > /data/local/tmp/ow-log-$1.pid"
}

# stop_log <name> <local file>: ends that log writer and fetches what it wrote.
stop_log() {
  on "kill \$(cat /data/local/tmp/ow-log-$1.pid) 2>/dev/null; rm -f /data/local/tmp/ow-log-$1.pid" || return 1
  fetch "/data/local/tmp/ow-log-$1.txt" "$2"
}

# outcome <instrumentation output file>: passed, failed or crashed, as the runner reported it.
# A failed test is a result too (a negative run is recorded), so the scripts label it rather
# than stop on it.
outcome() {
  if grep -q "Process crashed" "$1" 2>/dev/null; then echo crashed
  elif grep -q "FAILURES!!!" "$1" 2>/dev/null; then echo failed
  elif grep -q "^OK (" "$1" 2>/dev/null; then echo passed
  else echo unknown; fi
}

# detached <name> <command> <local file>: runs <command> on the phone in its own session, waits
# for it through whichever transport is up, and fetches its output to <local file>. Refuses to
# start while an instrumentation of this package is already running. Returns the command's exit
# code; fails when the phone cannot be reached for two minutes, and returns 124 after stopping a
# command that outlived MAX_MIN minutes (default 120) of wall-clock time.
detached() {
  local name=$1 cmd=$2 out=$3 f=/data/local/tmp/ow-run-$1.txt deadline via code
  # The bracket keeps the pattern from matching the shell that runs pgrep, whose own command
  # line contains it.
  if on "pgrep -f 'am [i]nstrument.*$T' > /dev/null"; then
    echo "== $name: an instrumentation of $T is already running; not starting another" >&2
    return 1
  fi
  on "rm -f $f; setsid nohup sh -c '($cmd) > $f 2>&1; echo OW_EXIT=\$? >> $f' < /dev/null > /dev/null 2>&1 &" || return 1
  via=$S
  deadline=$(($(date +%s) + ${MAX_MIN:-120} * 60))
  while :; do
    wait_s 15
    pick || return 1
    if [ "$S" != "$via" ]; then
      echo "== $name now over $S ($(date -u +%FT%TZ))" >&2
      [ "$S" = "$USB" ] || on "svc power stayon true" >/dev/null
      via=$S
    fi
    on "grep -q '^OW_EXIT=' $f" && break
    if [ "$(date +%s)" -ge "$deadline" ]; then
      echo "== $name still running after ${MAX_MIN:-120} min; stopping it" >&2
      on "am force-stop $T" >/dev/null
      fetch "$f" "$out"
      return 124
    fi
  done
  fetch "$f" "$out" || return 1
  code=$(sed -n 's/^OW_EXIT=//p' "$out" | tail -1 | tr -d '\r')
  return "${code:-1}"
}
