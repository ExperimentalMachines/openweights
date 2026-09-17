"""Can the phone's own model tell whether search results answer the question? The offline
half of fetching the top page when snippets fall short, scored against TypeSafe's reading
(typesafe_results.py `states_answer`) and the gold-alias label (`findable`).

    llama-server -m LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf --port 8095 -c 8192 -ngl 99 --jinja
    python3 tools/eval/bench/local_sufficiency.py run 8095 <out.jsonl>
    python3 tools/eval/bench/local_sufficiency.py report <out.jsonl>

The conversation is shaped as the app shapes its own search: the question, the assistant's
"Searching the web for: ..." line, the results as the tool's message, and the question put
in the next user turn, scored as `Session::judge` scores (one token each, renormalised).
"""
import json, math, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gate, typesafe_judge, typesafe_results  # noqa: E402
from grade_decisions import grade_row  # noqa: E402
from confidence import auroc  # noqa: E402

QUESTION = ("Do the search results above state the answer to my question, clearly enough to answer from them "
            "alone? Reply with one word, Yes or No.")


def pairs():
    seen = set()
    for row, results in typesafe_results.rows():
        key = (row["question"], results)
        if key in seen:
            continue
        seen.add(key)
        yield row, results


def run(port, out_path):
    done = set()
    if Path(out_path).exists():
        done = {json.loads(l)["key"] for l in open(out_path)}
    with open(out_path, "a") as f:
        for row, results in pairs():
            s_body, _ = typesafe_results.requests(row, results)
            key = typesafe_judge.key(s_body)
            if key in done:
                continue
            messages = [
                {"role": "user", "content": row["question"]},
                {"role": "assistant", "content": "Searching the web for: " + row["question"]},
                {"role": "user", "content": results + "\n\n" + QUESTION},
            ]
            prompt = gate.post(port, "/apply-template", {"messages": messages,
                                                         "chat_template_kwargs": {"enable_thinking": False}})["prompt"]
            ids = [gate.only_token(port, prompt, o) for o in ("Yes", "No")]
            if None in ids:
                f.write(json.dumps({"key": key, "refused": True}) + "\n")
                continue
            res = gate.post(port, "/completion", {"prompt": prompt, "n_predict": 1, "temperature": 0, "n_probs": 400,
                                                  "post_sampling_probs": False})
            top = {p["id"]: p["logprob"] for p in res["completion_probabilities"][0]["top_logprobs"]}
            floor = min(top.values())
            shares = [math.exp(top.get(i, floor)) for i in ids]
            mass = sum(shares)
            f.write(json.dumps({"key": key, "yes": shares[0] / mass, "mass": mass}) + "\n")
            f.flush()


def report(out_path):
    cache = json.loads((Path(__file__).resolve().parent.parent / "results" / "typesafe" / "results-cache.json").read_text())
    local = {json.loads(l)["key"]: json.loads(l) for l in open(out_path)}
    no_answer, jev, findable, right = [], [], [], []
    for row, results in typesafe_results.rows():
        s_body, _ = typesafe_results.requests(row, results)
        k = typesafe_judge.key(s_body)
        x = local.get(k)
        if not x or x.get("refused") or k not in cache:
            continue
        no_answer.append(1 - x["yes"])
        jev.append(1 if cache[k]["states_answer"] < 0.5 else 0)
        g = grade_row(row)
        findable.append(0 if g["findable"] else 1)
        right.append(0 if g["correct"] else 1)
    print(f"{len(no_answer)} searched rows; local P(No) as the score")
    print(f"| label | AUROC |\n|---|---|")
    print(f"| Jev: results do not state the answer | {auroc(no_answer, jev):.2f} |")
    print(f"| gold alias absent from results | {auroc(no_answer, findable):.2f} |")
    print(f"| reply wrong | {auroc(no_answer, right):.2f} |")
    for c in (0.3, 0.5, 0.7):
        sent = [i for i, p in enumerate(no_answer) if p > c]
        print(f"P(No) > {c}: fetch on {len(sent)} of {len(no_answer)}; of those Jev says lacking {sum(jev[i] for i in sent)}, "
              f"reply wrong {sum(right[i] for i in sent)}; recall of Jev-lacking {sum(jev[i] for i in sent) / max(sum(jev), 1):.2f}")


if __name__ == "__main__":
    run(sys.argv[2], sys.argv[3]) if sys.argv[1] == "run" else report(sys.argv[2])
