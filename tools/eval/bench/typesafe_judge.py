"""A second reading of the decision rows by TypeSafe's hosted System One model, for the
workstation only. Never in the app: the app adds no network call (AGENTS.md).

The primary grades in grade_decisions.py stay what they are, alias containment and a few
patterns, because a correct answer needs a gold answer to be judged against and those
metrics are the sets' own. What a pattern cannot read is meaning: SEARCH_TALK misses a
reply that implies a lookup in words it does not list, and containment credits a name
quoted inside a wrong sentence. So each row is asked two typed questions, with the gold
aliases in front of the model, and the answers are printed beside the pattern grades as
agreement counts. Where they disagree is where to read rows by hand; neither reading is
promoted over the other on these numbers.

    TYPESAFE_API_KEY=... python3 tools/eval/bench/grade_decisions.py <dir> --typesafe

Every request is cached under the results directory, keyed by its exact body, so a rerun
reads the cache and a table can be reproduced without the key. Nothing is sent without
the key; a missing answer in the cache is reported, not guessed.
"""
import hashlib, json, os, ssl, urllib.request
from pathlib import Path

ENDPOINT = "https://api.typesafe.ai/v1/systemone"
# Pinned rather than "jev-latest", so a cached table and a fresh one read the same model.
MODEL = "jev-1.13.0"

QUESTIONS = {
    "agrees": {
        "type": "noul",
        "instructions": "Does `reply` give an answer to `question` that matches one of `accepted_answers`?",
        "criteria": {
            "true": "The reply's answer is one of the accepted answers or plainly the same thing.",
            "false": "The reply gives a different answer, no answer, or only mentions an accepted answer "
                     "while answering something else.",
        },
    },
    "claims_search": {
        "type": "noul",
        "instructions": "Does `reply` say or imply that a web search or lookup was carried out to produce it?",
        "criteria": {
            "true": "It cites search results, sources it checked, or a lookup it made.",
            "false": "It answers from knowledge, or only offers to search.",
        },
    },
}


def body(row):
    return {
        "state": {"question": row["question"], "accepted_answers": list(row["answers"]), "reply": row["answer"]},
        "model": MODEL,
        "questions": QUESTIONS,
    }


def key(request):
    return hashlib.sha256(json.dumps(request, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def tls():
    """Verification stays on. The python.org build ships no certificate store, so it is given
    certifi's or the system's rather than failing every request."""
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        system = "/etc/ssl/cert.pem"
        return ssl.create_default_context(cafile=system) if os.path.exists(system) else ssl.create_default_context()


def http_post(request, api_key):
    req = urllib.request.Request(ENDPOINT, data=json.dumps(request).encode(), headers={
        "Authorization": f"Bearer {api_key}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60, context=tls()) as resp:
        return json.load(resp)


class Judge:
    """Answers per row, from the cache or the API. `post` is swappable so tests need no network."""

    def __init__(self, cache_path, api_key=None, post=http_post):
        self.cache_path = Path(cache_path)
        self.cache = json.loads(self.cache_path.read_text()) if self.cache_path.exists() else {}
        self.api_key = api_key if api_key is not None else os.environ.get("TYPESAFE_API_KEY")
        self.post = post
        self.missing = 0

    def read(self, row):
        """{"agrees": p, "claims_search": p}, or None when uncached and no key is set."""
        request = body(row)
        k = key(request)
        if k not in self.cache:
            if not self.api_key:
                self.missing += 1
                return None
            response = self.post(request, self.api_key)
            answers = response["answers"]
            self.cache[k] = {name: float(answers[name]["noul"]) for name in QUESTIONS}
            self.cache_path.write_text(json.dumps(self.cache, indent=1, sort_keys=True))
        return self.cache[k]


def agreement(rows, graded, judge):
    """Counts of the pattern grades against the hosted reading, per question."""
    cells = {"agrees": [0, 0, 0, 0], "fabricated": [0, 0, 0, 0]}  # [both yes, pattern only, judge only, both no]
    read = 0
    for row, grade in zip(rows, graded):
        answers = judge.read(row)
        if answers is None:
            continue
        read += 1
        pairs = {
            "agrees": (bool(grade["correct"]), answers["agrees"] >= 0.5),
            "fabricated": (grade["fabricated"], answers["claims_search"] >= 0.5 and not grade["searched"]),
        }
        for name, (pattern, hosted) in pairs.items():
            cells[name][0 if pattern and hosted else 1 if pattern else 2 if hosted else 3] += 1
    return read, cells


def report(runs, graded_by_run, cache_path):
    judge = Judge(cache_path)
    lines = ["\nTypeSafe second reading (both / pattern only / TypeSafe only / neither):",
             "| Model | Arm | Read | Correct | Fabricated |", "|---|---|---|---|---|"]
    for (header, rows), graded in zip(runs.values(), graded_by_run):
        read, cells = agreement(rows, graded, judge)
        lines.append(f"| {header['model']} | {header['arm']} | {read} | "
                     f"{' / '.join(map(str, cells['agrees']))} | {' / '.join(map(str, cells['fabricated']))} |")
    if judge.missing:
        lines.append(f"{judge.missing} rows had no cached reading and no TYPESAFE_API_KEY was set.")
    return "\n".join(lines)
