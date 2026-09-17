"""Two readings of the rows where a web search ran, by TypeSafe's hosted model. Workstation
only; the app adds no network call.

1. Sufficiency, asked with no gold answer in sight: do the search results state the answer
   to the question? The public rows carry a free label for the same thing, `findable` in
   grade_decisions.py (a gold alias appears in the results), so this measures how far a
   semantic reading of "is the answer here" moves from string containment, before any
   on-device version of the question (a fetch or a reformulated search when the snippets
   do not answer) is considered.
2. Grounding, asked with the gold aliases: is the reply's answer supported by the results,
   not addressed by them, or contradicted by them, and does it match an accepted answer.
   The citation-check shape; it is a second reading of `correct` and `result_ignored`.

    python3 tools/eval/bench/typesafe_results.py label <cache.json> [--limit N]
    python3 tools/eval/bench/typesafe_results.py report <cache.json>

The key is read from TYPESAFE_API_KEY and never written; answers are cached by body. Result
text in the rows is what the phone recorded, cut at 1,500 characters.
"""
import concurrent.futures, glob, json, sys, threading, time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import typesafe_judge  # noqa: E402
from grade_decisions import correct, grade_row  # noqa: E402

RESULTS = Path(__file__).resolve().parent.parent / "results" / "decisions"

SUFFICIENT = {
    "states_answer": {
        "type": "noul",
        "instructions": "Do `results` state the answer to `question`, clearly enough that someone could answer "
                        "from them alone?",
    },
}
GROUNDED = {
    "support": {
        "type": "choice",
        "instructions": "How does `results` bear on the answer given in `reply`?",
        "criteria": {
            "supported": "The results state the reply's answer.",
            "not_addressed": "The results say nothing either way about the reply's answer.",
            "contradicted": "The results state a different answer.",
        },
    },
    "agrees": {
        "type": "noul",
        "instructions": "Does `reply` give an answer to `question` that matches one of `accepted_answers`?",
    },
}


def rows():
    seen = set()
    for f in sorted(RESULTS.glob("*decisions-*.jsonl")):
        for line in f.open():
            r = json.loads(line)
            if r.get("header") or not isinstance(r.get("calls"), list) or not isinstance(r.get("answers"), list):
                continue
            hits = [c for c in r["calls"] if c.get("name") == "web_search" and c.get("successful") and c.get("result")]
            if not hits:
                continue
            key = (r["question"], r["answer"], hits[0]["result"])
            if key in seen:
                continue
            seen.add(key)
            yield r, "\n\n".join(c["result"] for c in hits)


def requests(row, results):
    return (
        {"state": {"question": row["question"], "results": results}, "model": typesafe_judge.MODEL,
         "questions": SUFFICIENT},
        {"state": {"question": row["question"], "accepted_answers": row["answers"], "reply": row["answer"],
                   "results": results}, "model": typesafe_judge.MODEL, "questions": GROUNDED},
    )


def read(response, questions):
    out = {}
    for name, q in questions.items():
        a = response["answers"][name]
        out[name] = a["noul"] if q["type"] == "noul" else {"choice": a["choice"], "p": a["probabilities"],
                                                          "confidence": a["confidence"]}
    return out


def label(cache_path, limit=None, workers=8):
    judge = typesafe_judge.Judge(cache_path)
    if not judge.api_key:
        sys.exit("TYPESAFE_API_KEY is not set")
    lock = threading.Lock()
    todo = []
    for r, results in rows():
        for body, questions in zip(requests(r, results), (SUFFICIENT, GROUNDED)):
            if typesafe_judge.key(body) not in judge.cache:
                todo.append((body, questions))
    todo = todo[: limit] if limit else todo

    def one(item):
        body, questions = item
        for attempt in range(4):
            try:
                response = judge.post(body, judge.api_key)
                break
            except Exception as failure:  # rate limits and dropped connections: back off and retry
                if attempt == 3:
                    print(f"gave up: {failure}")
                    return
                time.sleep(2 ** attempt)
        with lock:
            judge.cache[typesafe_judge.key(body)] = read(response, questions)

    with concurrent.futures.ThreadPoolExecutor(workers) as pool:
        for i, _ in enumerate(pool.map(one, todo)):
            if i % 300 == 299:
                with lock:
                    Path(cache_path).write_text(json.dumps(judge.cache, sort_keys=True))
    Path(cache_path).write_text(json.dumps(judge.cache, sort_keys=True))
    print(f"labelled {len(todo)}")


def report(cache_path):
    cache = json.loads(Path(cache_path).read_text())
    n = 0
    suff = {"findable": [0, 0], "not_findable": [0, 0]}  # [Jev says states answer, total]
    ground = {}
    ignored_by_support = {}
    grader = [0, 0, 0, 0]  # both right, containment only, Jev only, neither
    right_given_states = {True: [0, 0], False: [0, 0]}
    for r, results in rows():
        s_body, g_body = requests(r, results)
        s, g = cache.get(typesafe_judge.key(s_body)), cache.get(typesafe_judge.key(g_body))
        if s is None or g is None:
            continue
        n += 1
        grade = grade_row(r)
        states = s["states_answer"] >= 0.5
        bucket = "findable" if grade["findable"] else "not_findable"
        suff[bucket][0] += states
        suff[bucket][1] += 1
        ok = bool(grade["correct"])
        right_given_states[states][0] += ok
        right_given_states[states][1] += 1
        support = g["support"]["choice"]
        ground[(support, ok)] = ground.get((support, ok), 0) + 1
        if grade["result_ignored"]:
            ignored_by_support[support] = ignored_by_support.get(support, 0) + 1
        jev_ok = g["agrees"] >= 0.5
        grader[0 if ok and jev_ok else 1 if ok else 2 if jev_ok else 3] += 1
    print(f"{n} searched rows read")
    print("\nSufficiency against the containment label (Jev says the results state the answer):")
    for k, (yes, tot) in suff.items():
        print(f"  {k}: {yes} of {tot}")
    print("\nReply graded right, by whether Jev says the results state the answer:")
    for k, (ok, tot) in right_given_states.items():
        print(f"  results state it = {k}: {ok} of {tot} right")
    print("\nJev's support reading against the grade (support, graded right): count")
    for k, v in sorted(ground.items()):
        print(f"  {k}: {v}")
    print(f"\nrows the grader calls result_ignored, by Jev's support reading: {ignored_by_support}")
    print(f"\ncontainment grade vs Jev agreement (both right / containment only / Jev only / neither): {grader}")


if __name__ == "__main__":
    if sys.argv[1] == "label":
        limit = int(sys.argv[sys.argv.index("--limit") + 1]) if "--limit" in sys.argv else None
        label(sys.argv[2], limit)
    else:
        report(sys.argv[2])
