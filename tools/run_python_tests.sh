#!/usr/bin/env bash
# Runs every Python test in the repository: the unittest files under tools/eval/bench
# (found by name, so a new test_*.py is picked up without editing this script) and the
# plain guard_test.py script. Exits nonzero if any of them fail.
set -u

# Resolve the repository root from this script's location so the script works from
# any working directory.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

status=0

# Collect the bench test files by pattern.
bench_tests=()
for f in tools/eval/bench/test_*.py; do
  [ -e "$f" ] || continue
  bench_tests+=("$f")
done

if [ "${#bench_tests[@]}" -gt 0 ]; then
  if ! python3 -m unittest "${bench_tests[@]}"; then
    status=1
  fi
fi

if ! python3 tools/hooks/guard_test.py; then
  status=1
fi

exit "$status"
