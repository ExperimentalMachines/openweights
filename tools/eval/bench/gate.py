"""Does the model know, before it answers, whether it needs to look something up? The offline
half of the one-token gate (`TurnRunner.honourGate`), measured against the doubt gate on the
same rows, run against a local llama-server so it costs no phone time.

    llama-server -m model.gguf --port 8092 -c 4096 -ngl 99 --jinja
    python3 tools/eval/bench/gate.py bare 8092 <tag> [seed8]        # the bare answers the grades come from
    python3 tools/eval/bench/gate.py gate 8092 <tag> <wording> [seed8]
    python3 tools/eval/bench/gate.py distractors 8092 <tag> <wording>
    python3 tools/eval/bench/gate.py report <tag> <wording> [seed8]

The gate appends the question to the last user message and reads the probability of each
option's first token at the start of the reply, renormalised over the options: the same
reading `Session::judge` makes on the phone. Seed 7 rows choose the wording and the cutoff;
seed 8 rows (decisions-seed8.json, rebuilt from the held-out phone run) only confirm them.
"""
import json, math, sys, time, urllib.request, collections
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parent / "results" / "gate"
sys.path.insert(0, str(HERE))
from confidence import DISTRACTORS, auroc, header, features  # noqa: E402

WORDINGS = {
    # Mirrors the app's gate question (JudgeQuestions in TurnRunner); change both together.
    # Every option must be one token where it follows the prompt, as Session::judge requires.
    "search": ("Before you reply to my last message, decide one thing: can you answer it correctly "
               "from what you already know, or does it need a web search, because it asks about "
               "something obscure, recent, or likely to have changed? Reply with one word, answer "
               "or search.",
               ["answer", "search"]),
    "sure": ("Before you reply: are you sure you know the correct answer to my last message without "
             "looking anything up? Reply with one word, Yes or No.",
             ["Yes", "No"]),
    "need": ("Before you reply: to answer my last message correctly, do you need to look it up on the "
             "web, because it asks about something obscure, recent, or likely to have changed? Reply "
             "with one word, Yes or No.",
             ["Yes", "No"]),
}


def post(port, path, body):
    req = urllib.request.Request(f"http://localhost:{port}{path}", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=300) as resp:
        return json.load(resp)


def rows_for(split):
    name = "decisions-seed8.json" if split == "seed8" else "decisions.json"
    return json.load((HERE / name).open())["rows"]


def conversation(hdr, question):
    d = hdr["date_exchange"]
    return [{"role": "system", "content": hdr["system"]}, {"role": "user", "content": d[0]},
            {"role": "assistant", "content": d[1]}, {"role": "user", "content": d[2]},
            {"role": "assistant", "content": d[3]}, {"role": "user", "content": question}]


def asking(messages, instruction):
    """The question rides on the last user message when there is one, as `Session::judge` does:
    a second user message in a row is refused by templates that require roles to alternate."""
    if messages and messages[-1]["role"] == "user":
        return messages[:-1] + [{"role": "user", "content": messages[-1]["content"] + "\n\n" + instruction}]
    return messages + [{"role": "user", "content": instruction}]


def only_token(port, prompt, option):
    """The option's one token where it follows the prompt, or None: the rule Session::judge
    enforces, so a wording that fails here fails on the phone too. No standalone fallback: an
    option that merges with the prompt's last token has no token of its own there."""
    whole = post(port, "/tokenize", {"content": prompt + option})["tokens"]
    head = post(port, "/tokenize", {"content": prompt})["tokens"]
    if len(whole) == len(head) + 1 and whole[:len(head)] == head:
        return whole[-1]
    return None


def judge(port, messages, wording):
    instruction, options = WORDINGS[wording]
    prompt = post(port, "/apply-template", {"messages": asking(messages, instruction),
                                            "chat_template_kwargs": {"enable_thinking": False}})["prompt"]
    ids = [only_token(port, prompt, o) for o in options]
    assert None not in ids, f"an option is not one token here: {options} -> {ids}"
    assert len(set(ids)) == len(ids), f"options are the same token: {options}"
    t0 = time.time()
    # llama-server cannot be asked for the probability of chosen ids, so the top 400 are read.
    # Their log-probabilities are normalised over the whole vocabulary (the pre-sampling
    # distribution), so an option among them is exact and the mass is a true share. An option
    # outside them is under the 400th's probability: it is counted as that, which bounds the
    # error, and the row is marked so a report can say how many there were.
    res = post(port, "/completion", {"prompt": prompt, "n_predict": 1, "temperature": 0, "n_probs": 400,
                                     "post_sampling_probs": False})
    top = {p["id"]: p["logprob"] for p in res["completion_probabilities"][0]["top_logprobs"]}
    floor = min(top.values())
    missing = sum(1 for i in ids if i not in top)
    shares = [math.exp(top.get(i, floor)) for i in ids]
    mass = sum(shares)
    return dict(zip(options, [x / mass for x in shares])), mass, missing, int((time.time() - t0) * 1000)


def bare(port, tag, split):
    hdr = header(); OUT.mkdir(parents=True, exist_ok=True)
    out = OUT / f"{tag}-{split}-bare.jsonl"
    done = {json.loads(l)["id"] for l in out.open()} if out.exists() else set()
    with out.open("a") as f:
        for r in rows_for(split):
            if r["id"] in done:
                continue
            res = post(port, "/v1/chat/completions", {"messages": conversation(hdr, r["question"]), "temperature": 0,
                                                      "top_k": 1, "seed": 1, "max_tokens": 200, "logprobs": True,
                                                      "top_logprobs": 2, "chat_template_kwargs": {"enable_thinking": False}})
            ch = res["choices"][0]
            toks = [{"t": x["token"], "lp": x["logprob"], "top": [(y["token"], y["logprob"]) for y in x.get("top_logprobs", [])]}
                    for x in (ch.get("logprobs", {}).get("content", []) or [])]
            f.write(json.dumps({"id": r["id"], "need": r["need"], "stratum": r["stratum"], "answers": r["answers"],
                                "answer": ch["message"]["content"], "tokens": toks}) + "\n"); f.flush()


def gate(port, tag, wording, split):
    hdr = header(); OUT.mkdir(parents=True, exist_ok=True)
    out = OUT / f"{tag}-{split}-gate-{wording}.jsonl"
    with out.open("w") as f:
        for r in rows_for(split):
            probs, mass, missing, ms = judge(port, conversation(hdr, r["question"]), wording)
            f.write(json.dumps({"id": r["id"], "probs": probs, "mass": mass, "missing": missing, "ms": ms}) + "\n")
            f.flush()


def distractors(port, tag, wording):
    hdr = header(); OUT.mkdir(parents=True, exist_ok=True)
    with (OUT / f"{tag}-distractors-gate-{wording}.jsonl").open("w") as f:
        for kind, qs in DISTRACTORS.items():
            for q in qs:
                probs, mass, _, _ = judge(port, [{"role": "system", "content": hdr["system"]}, {"role": "user", "content": q}], wording)
                f.write(json.dumps({"kind": kind, "q": q, "probs": probs, "mass": mass}) + "\n")


def lookup(wording, probs):
    """The probability the model gives to needing a search, whichever way the wording asks."""
    if wording == "need":
        return probs["Yes"]
    return probs["search"] if "search" in probs else probs["No"]


def report(tag, wording, split):
    from grade_decisions import correct
    bare_rows = {json.loads(l)["id"]: json.loads(l) for l in (OUT / f"{tag}-{split}-bare.jsonl").open()}
    gates = {json.loads(l)["id"]: json.loads(l) for l in (OUT / f"{tag}-{split}-gate-{wording}.jsonl").open()}
    ids = [i for i in gates if i in bare_rows]
    wrong = [0 if correct(bare_rows[i]["answer"], bare_rows[i]["answers"]) else 1 for i in ids]
    p = [lookup(wording, gates[i]["probs"]) for i in ids]
    doubt = [-features(bare_rows[i]["tokens"])["min_prob"] for i in ids]
    # The set's need label is null at the boundary; those rows are left out of its AUROC
    # rather than counted as not needing a search.
    labelled = [k for k, i in enumerate(ids) if bare_rows[i]["need"] is not None]
    need = [1 if bare_rows[ids[k]]["need"] else 0 for k in labelled]
    print(f"{tag} {split} {wording}: {len(ids)} rows, {sum(wrong)} bare answers wrong, "
          f"{sum(need)} of {len(labelled)} labelled rows need a search")
    print(f"| Signal | AUROC wrong bare answer | AUROC labelled need |\n|---|---|---|")
    print(f"| gate P(search) | {auroc(p, wrong):.2f} | {auroc([p[k] for k in labelled], need):.2f} |")
    print(f"| doubt (least likely of first 20) | {auroc(doubt, wrong):.2f} | {auroc([doubt[k] for k in labelled], need):.2f} |")
    print("\n| Cutoff P(search) > | sent | wrong among sent | right among sent | wrong not sent |\n|---|---|---|---|---|")
    for c in (0.3, 0.5, 0.7, 0.9):
        sent = [k for k, v in enumerate(p) if v > c]
        print(f"| {c} | {len(sent)} | {sum(wrong[k] for k in sent)} | {len(sent) - sum(wrong[k] for k in sent)} | {sum(wrong) - sum(wrong[k] for k in sent)} |")
    sent_doubt = [k for k, v in enumerate(doubt) if -v < 0.2]
    print(f"| doubt min prob < 0.2 | {len(sent_doubt)} | {sum(wrong[k] for k in sent_doubt)} | {len(sent_doubt) - sum(wrong[k] for k in sent_doubt)} | {sum(wrong) - sum(wrong[k] for k in sent_doubt)} |")
    d = OUT / f"{tag}-distractors-gate-{wording}.jsonl"
    if d.exists():
        rs = [json.loads(l) for l in d.open()]
        for c in (0.5, 0.7, 0.9):
            print(f"asks that need no search, P(search) > {c}: {sum(1 for r in rs if lookup(wording, r['probs']) > c)} of {len(rs)}")
    ms = [g["ms"] for g in gates.values()]
    print(f"median judge ms on the workstation: {sorted(ms)[len(ms) // 2]}")
    print(f"rows with an option outside the top 400: {sum(1 for g in gates.values() if g.get('missing'))}")
    masses = sorted(g["mass"] for g in gates.values())
    print(f"option mass: min {masses[0]:.2f}, 10th percentile {masses[len(masses) // 10]:.2f}, median {masses[len(masses) // 2]:.2f}")


if __name__ == "__main__":
    cmd, args = sys.argv[1], sys.argv[2:]
    split = "seed8" if args and args[-1] == "seed8" else "seed7"
    if cmd == "bare":
        bare(args[0], args[1], split)
    elif cmd == "gate":
        gate(args[0], args[1], args[2], split)
    elif cmd == "distractors":
        distractors(args[0], args[1], args[2])
    elif cmd == "report":
        report(args[0], args[1], split)
