"""Step 3: three questions arms.py left open, on the same program and the same ids.

1. The one place in the phone's reply where the Mac's greedy step preferred another split of
   the same text (reply token 176, " has" over " hasn" in "hasn't"): what that split costs in
   tokens, so whether the phone's 385 generated tokens leave room for it.
2. The phone's parting token (timed reply token 20, " and" warm against " They" cold): how
   close the two are in each Mac arm, on the shared prefix the phone replies have there.
3. The whole distribution at the last prompt position, not only the largest logit gap:
   KL divergence and the top token's probability, warm against cold.

Usage: python followup.py <model.pte> <tokenizer.json> <arms.json> <out.json>
"""

import json
import sys

import numpy as np
import torch
from executorch.extension.llm.custom_ops import custom_ops  # noqa: F401
from executorch.extension.pybindings import portable_lib as pl
import executorch.kernels.quantized  # noqa: F401
from tokenizers import Tokenizer

import prompts

pte, tok_path, arms_path, out_path = sys.argv[1:5]
hf = Tokenizer.from_file(tok_path)
module = pl._load_for_executorch(pte)
AND, THEY = 323, 2379


def enc(text):
    return hf.encode(text, add_special_tokens=False).ids


def frag(t):
    return hf.decode([t], skip_special_tokens=False)


class Run:
    def __init__(self):
        self.pos = 0

    def feed(self, ids):
        t = torch.tensor([ids], dtype=torch.long)
        p = torch.arange(self.pos, self.pos + len(ids), dtype=torch.long)
        out = module.forward((t, p))[0][0].to(torch.float32).numpy().copy()
        self.pos += len(ids)
        return out[-1]

    def steps(self, ids):
        last = None
        for t in ids:
            last = self.feed([t])
        return last


def softmax(x):
    e = np.exp(x - x.max())
    return e / e.sum()


def top(row, k=3):
    i = np.argsort(-row)[:k]
    return [[int(j), round(float(row[j]), 4), frag(int(j))] for j in i]


phone = prompts.phone_rows()
pw = next(r for r in phone if r["arm"] == "warm")
pc = next(r for r in phone if r["arm"] == "cold")
reply_text = pw["turn_one"]
p1 = prompts.prompt_one()
p1_pieces, p1_tail = prompts.pieces(p1)
p1_ids = [enc(p) for p in p1_pieces] + [enc(p1_tail)]
reply_ids = enc(reply_text)
p2 = prompts.prompt_two(reply_text)
tail_ids = enc(p2[len(p1 + reply_text):])
p2_pieces, p2_tail = prompts.pieces(p2)
cold_ids = [enc(p) for p in p2_pieces + [p2_tail]]
# The phone replies' shared first 20 tokens, which both arms are forced along here.
shared = enc(pw["reply"])[:20]
assert shared == enc(pc["reply"])[:20]
out = {}

# 1. The "hasn't" site.
vocab = hf.get_vocab()
site = 176
assert frag(reply_ids[site]) == " hasn" and frag(reply_ids[site + 1]) == "'t"
r = Run()
for ids in p1_ids:
    r.feed(ids)
last = r.steps(reply_ids[:site])
after_has = r.feed([enc(" has")[0]])
out["hasnt_site"] = {
    "reply_token": site,
    "step_top3": top(last),
    "after_feeding_has_top3": top(after_has),
    "nt_is_one_token_in_vocab": "n't" in vocab,
    "tokens_for_has_then_nt": len(enc(" has")) + len(enc("n't")),
    "tokens_for_hasnt_canonical": len(enc(" hasn't")),
}


def warm():
    r = Run()
    for ids in p1_ids:
        r.feed(ids)
    r.steps(reply_ids)
    return r, r.feed(tail_ids)


def cold(pieces):
    r = Run()
    for ids in pieces[:-1]:
        r.feed(ids)
    return r, r.feed(pieces[-1])


arms = {"W": warm, "C": lambda: cold(cold_ids), "C2": lambda: cold([enc(p2)])}
last_rows = {}
out["phone_parting_token_20"] = {}
for name, make in arms.items():
    r, last = make()
    last_rows[name] = last
    # Forced along the phone replies' shared prefix, one token per step as the runner does.
    row = last
    for t in shared:
        row = r.feed([t])
    p = softmax(row)
    order = np.argsort(-row)
    out["phone_parting_token_20"][name] = {
        "top3": top(row),
        "logit_and_minus_they": round(float(row[AND] - row[THEY]), 4),
        "p_and": round(float(p[AND]), 4),
        "p_they": round(float(p[THEY]), 4),
        "rank_and": int(np.where(order == AND)[0][0]) + 1,
        "rank_they": int(np.where(order == THEY)[0][0]) + 1,
    }

# 3. Whole-distribution distance at the last prompt position.
pw_ = softmax(last_rows["W"])
out["last_prompt_position"] = {}
for name in ("C", "C2"):
    q = softmax(last_rows[name])
    out["last_prompt_position"][name + "_vs_W"] = {
        "kl_W_to_arm_nats": float(np.sum(pw_ * (np.log(pw_ + 1e-30) - np.log(q + 1e-30)))),
        "top1_W": top(last_rows["W"], 1)[0] + [round(float(pw_.max()), 4)],
        "top1_arm": top(last_rows[name], 1)[0] + [round(float(q.max()), 4)],
        "max_abs_logit_diff": round(float(np.abs(last_rows[name] - last_rows["W"]).max()), 4),
        "top1_logit_diff": round(float(abs(last_rows[name].max() - last_rows["W"].max())), 4),
    }

json.dump(out, open(out_path, "w"), indent=1)
print(json.dumps(out, indent=1))
