"""Can the phone's own model read its reply the way the intent matchers try to? The offline
half of a judged intent rule, scored against TypeSafe's reading (typesafe_matchers.py).

    llama-server -m LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf --port 8092 -c 4096 -ngl 99 --jinja
    python3 tools/eval/bench/local_reading.py run 8092 <replies.json> <out.jsonl>
    python3 tools/eval/bench/local_reading.py report <replies.json> <out.jsonl> <matchers-cache.json> [outcomes.jsonl]

The reply is put back as the assistant's turn and one Choice is asked in the next user turn,
exactly the shape `Session::judge` uses after a pass: each letter is scored as the next
token, renormalised over the four, with the options' mass kept.
"""
import json, math, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gate  # noqa: E402

QUESTION = ("Look at the reply you just gave. Which of these does it do? A: says it is going to search or look "
            "something up. B: says it already searched or checked sources. C: says it does not know or has no "
            "information. D: none of these, it answers or does something else. Reply with one letter.")
OPTIONS = ["A", "B", "C", "D"]


def run(port, replies, out_path):
    rows = json.load(open(replies))
    done = set()
    if Path(out_path).exists():
        done = {json.loads(l)["uid"] for l in open(out_path)}
    with open(out_path, "a") as f:
        for r in rows:
            if r["uid"] in done:
                continue
            messages = [{"role": "user", "content": r["question"]}, {"role": "assistant", "content": r["reply"]}]
            prompt = gate.post(port, "/apply-template", {"messages": messages + [{"role": "user", "content": QUESTION}],
                                                          "chat_template_kwargs": {"enable_thinking": False}})["prompt"]
            ids = [gate.only_token(port, prompt, o) for o in OPTIONS]
            if None in ids:
                f.write(json.dumps({"uid": r["uid"], "refused": True}) + "\n")
                continue
            res = gate.post(port, "/completion", {"prompt": prompt, "n_predict": 1, "temperature": 0, "n_probs": 400,
                                                  "post_sampling_probs": False})
            top = {p["id"]: p["logprob"] for p in res["completion_probabilities"][0]["top_logprobs"]}
            floor = min(top.values())
            shares = [math.exp(top.get(i, floor)) for i in ids]
            mass = sum(shares)
            f.write(json.dumps({"uid": r["uid"], "p": dict(zip(OPTIONS, [s / mass for s in shares])), "mass": mass}) + "\n")
            f.flush()


def jev_label(j):
    """TypeSafe's reading as one of the four, strongest first."""
    if j["claims"] >= 0.5:
        return "B"
    if j["announces"] >= 0.5 and j["offers_only"] < 0.5:
        return "A"
    if j["lacks_knowledge"] >= 0.5:
        return "C"
    return "D"


def report(replies, out_path, cache_path, outcomes=None):
    import typesafe_matchers as m, typesafe_judge as t
    rows = {r["uid"]: r for r in json.load(open(replies))}
    cache = json.loads(Path(cache_path).read_text())
    app = {json.loads(l)["uid"]: json.loads(l)["outcome"] for l in open(outcomes)} if outcomes else {}
    conf = {}
    acts = {"local": [0, 0, 0], "app": [0, 0, 0]}  # [acted and Jev acts, acted, Jev acts]
    refused = low = n = 0
    for line in open(out_path):
        x = json.loads(line)
        if x.get("refused"):
            refused += 1
            continue
        j = cache.get(t.key(m.request(rows[x["uid"]])))
        if j is None:
            continue
        n += 1
        low += x["mass"] < 0.9
        local = max(x["p"], key=x["p"].get) if x["mass"] >= 0.9 else "D"
        jl = jev_label(j)
        conf[(jl, local)] = conf.get((jl, local), 0) + 1
        jev_acts = jl != "D"
        for who, acted in (("local", local != "D"), ("app", app.get(x["uid"]) == "app_search")):
            if who == "app" and not app:
                continue
            acts[who][0] += acted and jev_acts
            acts[who][1] += acted
            acts[who][2] += jev_acts
    print(f"{n} replies read, {refused} refused (an option not one token), {low} under the 0.9 mass floor")
    print("| Jev \\\\ local | " + " | ".join(OPTIONS) + " |\n|---|---|---|---|---|")
    for a in OPTIONS:
        print(f"| {a} | " + " | ".join(str(conf.get((a, b), 0)) for b in OPTIONS) + " |")
    for who, (both, acted, jev) in acts.items():
        if acted or jev:
            print(f"{who}: acts on {acted}, of which Jev agrees {both} (precision {both / max(acted, 1):.2f}); "
                  f"recall against Jev {both / max(jev, 1):.2f}")


if __name__ == "__main__":
    if sys.argv[1] == "run":
        run(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        report(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5] if len(sys.argv) > 5 else None)
