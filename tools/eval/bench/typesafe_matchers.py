"""An independent reading of the loop's text matchers, by TypeSafe's hosted model, on the
replies phones actually produced. Workstation only; the app adds no network call.

The intent rule (`TurnRunner.searchIntent`) and the denial repair decide from English
patterns whether a reply announced a search, claimed one, lamented not knowing, or denied a
capability. Each pattern was added for a phrasing a phone found, so their misses are the
phrasings nobody has seen yet. Jev reads every recorded no-call reply against the same
distinctions, asked together in one request per reply, and the app's own decision for that
reply comes from replaying it through the real loop (`MatcherReplayTest`). Disagreements
are the rows to read.

    python3 tools/eval/bench/typesafe_matchers.py extract <replies.json>
    python3 tools/eval/bench/typesafe_matchers.py label <replies.json> <cache.json>
    python3 tools/eval/bench/typesafe_matchers.py report <replies.json> <outcomes.jsonl> <cache.json> [local.jsonl] [rows.jsonl]

The key is read from TYPESAFE_API_KEY and never written anywhere; answers are cached by
request body.
"""
import concurrent.futures, json, os, sys, threading, time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import typesafe_judge  # noqa: E402

QUESTIONS = {
    "announces": {
        "type": "noul",
        "instructions": "Does `reply` say that the assistant is going to search the web, look something up, or check "
                        "sources, instead of giving the answer now?",
    },
    "claims": {
        "type": "noul",
        "instructions": "Does `reply` say or imply that a web search, lookup, or check of sources has already been "
                        "done and that its answer comes from it?",
    },
    "lacks_knowledge": {
        "type": "noul",
        "instructions": "Does `reply` say that the assistant does not know, has no information about, or could not "
                        "find what `question` asks about?",
    },
    "denies_capability": {
        "type": "noul",
        "instructions": "Does `reply` decline what `question` asks by saying the assistant lacks a tool, internet "
                        "access, or the ability to do it?",
    },
    "offers_only": {
        "type": "noul",
        "instructions": "Does `reply` only offer to search or look further if the user wants, without saying it will?",
    },
    "answers": {
        "type": "noul",
        "instructions": "Does `reply` give a direct, substantive answer to `question`?",
    },
}


def extract(out_path):
    """Every unique no-call reply of the bare and driven arms, the corpus the tables were read
    on. Write it to app/build/replay/replies.json for MatcherReplayTest to replay."""
    seen, out = set(), []
    results = Path(__file__).resolve().parent.parent / "results" / "decisions"
    for f in sorted(results.glob("*decisions-*.jsonl")):
        for line in f.open():
            r = json.loads(line)
            calls = r.get("calls")
            if r.get("header") or not isinstance(calls, list) or calls:
                continue
            if r.get("arm") not in ("bare", "driven-search", "driven-full"):
                continue
            reply = r.get("raw") or r.get("answer") or ""
            if reply.startswith("ERROR") or (r["question"], reply) in seen:
                continue
            seen.add((r["question"], reply))
            out.append({"uid": len(out), "file": f.name, "arm": r["arm"], "model": r.get("model"),
                        "question": r["question"], "reply": reply})
    Path(out_path).parent.mkdir(parents=True, exist_ok=True)
    Path(out_path).write_text(json.dumps(out))
    print(f"{len(out)} replies")


def request(row):
    return {"state": {"question": row["question"], "reply": row["reply"]}, "model": typesafe_judge.MODEL,
            "questions": QUESTIONS}


def label(replies, cache_path, workers=8):
    rows = json.load(open(replies))
    judge = typesafe_judge.Judge(cache_path)
    if not judge.api_key:
        sys.exit("TYPESAFE_API_KEY is not set")
    lock = threading.Lock()
    todo = [r for r in rows if typesafe_judge.key(request(r)) not in judge.cache]

    def one(row):
        body = request(row)
        for attempt in range(4):
            try:
                response = judge.post(body, judge.api_key)
                break
            except Exception as failure:  # rate limits and dropped connections: back off and retry
                if attempt == 3:
                    print(f"gave up on {row['uid']}: {failure}")
                    return
                time.sleep(2 ** attempt)
        answers = {name: float(response["answers"][name]["noul"]) for name in QUESTIONS}
        with lock:
            judge.cache[typesafe_judge.key(body)] = answers

    with concurrent.futures.ThreadPoolExecutor(workers) as pool:
        for i, _ in enumerate(pool.map(one, todo)):
            if i % 200 == 199:
                with lock:
                    Path(cache_path).write_text(json.dumps(judge.cache, sort_keys=True))
    Path(cache_path).write_text(json.dumps(judge.cache, sort_keys=True))
    print(f"labelled {len(todo)}, cached {len(judge.cache)}")


FLAGS = ("claims", "announces", "lacks_knowledge", "denies_capability")

# Shapes of reply the app lets through, for reading the misses only; not the app's patterns.
SHAPES = [
    ("instruction echo", r"working search tools|you have (working )?(search )?tools"),
    ("offer ending in a question", None),
    ("knowledge-cutoff lament", r"\b(as of|up to|until|since) my (last |latest |most recent )?(knowledge|training)"
                                r"( cutoff| cut-off| update| refresh| data)?\b|\bmy knowledge cutoff\b|\bas of my last update\b"),
    ("not-documented lament", r"\bis (currently )?(not|isn't) (widely|definitively|publicly|well) "
                              r"(reported|recorded|documented|known)\b|\b(is|are) currently unknown\b|\bno widely reported\b"),
    ("announces another tool", r"\b(i('ll| will)|let me) (run|use|call|check|calculate|compute)\b"),
]


def shape(reply):
    import re
    from grade_decisions import plain
    text = plain(reply).lower()
    for name, pattern in SHAPES:
        if pattern is None:
            if reply.rstrip().endswith("?") and re.search(r"look (it|this|that) up|search (the web|for|online)|web search", text):
                return name
        elif re.search(pattern, text):
            return name
    return "other"


def report(replies, outcomes, cache_path, local=None, out_rows=None):
    """Every table of typesafe-experiments.md section 1 from one reading of the rows.

    Jev's answers are kept as independent flags, each yes at 0.5: no precedence between them
    and no veto by `offers_only`, which misfired on replies that claimed a search and then
    offered more. Correctness is grade_decisions.correct, word-bounded since 2026-09-17."""
    import glob
    from grade_decisions import correct
    rows = {r["uid"]: r for r in json.load(open(replies))}
    done = {json.loads(l)["uid"]: json.loads(l)["outcome"] for l in open(outcomes)}
    cache = json.loads(Path(cache_path).read_text())
    gold = {}
    for f in glob.glob(str(Path(__file__).resolve().parent.parent / "results" / "decisions" / "*decisions-*.jsonl")):
        for line in open(f):
            r = json.loads(line)
            if not r.get("header") and isinstance(r.get("answers"), list) and r["answers"]:
                gold[r["question"]] = r["answers"]
    readings = {}
    if local:
        readings = {json.loads(l)["uid"]: json.loads(l) for l in open(local)}
    table, clusters, shapes_all = {}, {}, {}
    acts = {"patterns": [0, 0], "local": [0, 0]}  # [acted, acted and Jev flags a search decision, claim or lament]
    jev_search = 0
    written = []
    for uid, outcome in done.items():
        row = rows[uid]
        j = cache[typesafe_judge.key(request(row))]
        flagged = any(j[f] >= 0.5 for f in FLAGS)
        search_like = any(j[f] >= 0.5 for f in ("claims", "announces", "lacks_knowledge"))
        g = gold.get(row["question"])
        ok = correct(row["reply"], g) if g else None
        app = {"app_search": "searched", "denial_prose": "denial repair", "denial_tool": "denial repair",
               "push": "push"}.get(outcome, "nothing")
        cell = table.setdefault((app, flagged), [0, 0, 0])
        cell[0] += 1
        cell[1] += ok is False
        cell[2] += ok is True
        name = shape(row["reply"])
        if app == "nothing" and flagged:
            c = clusters.setdefault(name, [0, 0, 0])
            c[0] += 1
            c[1] += ok is False
            c[2] += ok is True
        if name in ("knowledge-cutoff lament", "not-documented lament"):
            k = shapes_all.setdefault((name, row["model"].endswith(".gguf"), app), [0, 0, 0])
            k[0] += 1
            k[1] += ok is False
            k[2] += ok is True
        jev_search += search_like
        acts["patterns"][0] += outcome == "app_search"
        acts["patterns"][1] += outcome == "app_search" and search_like
        x = readings.get(uid)
        local_acts = bool(x and not x.get("refused") and x["mass"] >= 0.9 and max(x["p"], key=x["p"].get) != "D")
        acts["local"][0] += local_acts
        acts["local"][1] += local_acts and search_like
        written.append({"uid": uid, "model": row["model"], "arm": row["arm"], "question": row["question"],
                        "reply": row["reply"], "app": outcome, "jev": j, "correct": ok, "shape": name,
                        "local": x["p"] if x and not x.get("refused") else None})
    print(f"{len(done)} replies\n\n| The app | Jev flags | replies | wrong | right |\n|---|---|---|---|---|")
    for (app, flagged), (n, wrong, right) in sorted(table.items()):
        print(f"| {app} | {'yes' if flagged else 'no'} | {n} | {wrong} | {right} |")
    print("\n| Let through, by shape | replies | wrong | right |\n|---|---|---|---|")
    for name, (n, wrong, right) in sorted(clusters.items(), key=lambda kv: -kv[1][0]):
        print(f"| {name} | {n} | {wrong} | {right} |")
    print("\n| Lament shape over every reply | runtime | the app | matched | wrong | right |\n|---|---|---|---|---|---|")
    for (name, gguf, app), (n, wrong, right) in sorted(shapes_all.items()):
        print(f"| {name} | {'GGUF' if gguf else 'compiled'} | {app} | {n} | {wrong} | {right} |")
    print(f"\nJev flags a search decision, claim or lament on {jev_search} replies")
    for who, (acted, agreed) in acts.items():
        if acted:
            print(f"{who}: acts on {acted}, Jev agrees on {agreed} (precision {agreed / acted:.2f}, "
                  f"recall {agreed / jev_search:.2f})")
    if out_rows:
        Path(out_rows).write_text("\n".join(json.dumps(w, ensure_ascii=False) for w in written) + "\n")


if __name__ == "__main__":
    if sys.argv[1] == "extract":
        extract(sys.argv[2])
    elif sys.argv[1] == "label":
        label(sys.argv[2], sys.argv[3])
    else:
        # report <replies.json> <outcomes.jsonl> <cache.json> [local-reading.jsonl] [rows-out.jsonl]
        report(*sys.argv[2:7])
