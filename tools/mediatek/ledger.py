"""Locked edits of the session ledger (ledger.tsv), which session.sh runs side by side share.

  ledger.py <ledger.tsv> add <row, tab-separated>
  ledger.py <ledger.tsv> set <session id> [unit=U] [end=T] [mins=M] [note=TEXT]

An exclusive flock on <ledger.tsv>.lock is held from the read to the write, so one run's rewrite cannot drop a
row another run appended in between (Codex review, 2026-11-17, round 3: the unlocked rewrite did, in an
interleaving test). A rewrite goes to a temporary file that replaces the ledger only once fully written, so a
failed write (a full disk) leaves the old ledger whole (round 4: truncate-then-write could erase it).
Columns: session id, model, start, end, minutes, note. Exits 1 when the row to set is missing.
"""
import fcntl
import os
import sys
import tempfile

path, mode = sys.argv[1], sys.argv[2]
with open(path + ".lock", "a") as lock:
    fcntl.flock(lock, fcntl.LOCK_EX)
    if mode == "add":
        with open(path, "a") as f:
            f.write(sys.argv[3].rstrip("\n") + "\n")
        sys.exit(0)
    sid = sys.argv[3]
    with open(path) as f:
        text = f.read()
    lines = text.split("\n")
    if text.endswith("\n"):
        lines = lines[:-1]
    rows = [l.split("\t") for l in lines]
    hits = [r for r in rows if r[0] == sid]
    if not hits:
        sys.exit(1)
    for r in hits:
        r += [""] * (6 - len(r))
        for kv in sys.argv[4:]:
            key, value = kv.split("=", 1)
            if key == "unit":
                r[5] = r[5].replace("unit pending", "unit " + value)
            elif key == "end":
                r[3] = value
            elif key == "mins":
                r[4] = value
            elif key == "note":
                r[5] += "; " + value
    fd, tmp = tempfile.mkstemp(dir=os.path.dirname(os.path.abspath(path)), prefix=".ledger-")
    try:
        with os.fdopen(fd, "w") as out:
            out.write("".join("\t".join(r) + "\n" for r in rows))
            out.flush()
            os.fsync(out.fileno())
        os.replace(tmp, path)
    except BaseException:
        os.unlink(tmp)
        raise
