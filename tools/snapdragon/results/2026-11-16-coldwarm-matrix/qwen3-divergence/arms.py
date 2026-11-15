"""Step 2: the .pte on the Mac, driven token by token, warm against cold.

The export's forward takes tokens [1, n] and cache positions [n] and returns logits for all
n positions, so every arm yields a logit row per position and two arms can be compared
position by position. Greedy is argmax, first index on a tie, as the runner's sampler.

Arms, all on one loaded program. A reset is position 0 again, which is all the runner's
reset does; the custom SDPA never reads past the current position, so stale cache rows are
never seen.
  free  turn one generated greedily on the Mac, for reference: where it leaves the phone's.
  W   the app's warm turn on the phone's conversation: prompt one in the engine's pieces,
      one forward per piece, then the phone's turn-one reply fed one token per step (its
      canonical ids; the step's greedy choice is checked against each), <|im_end|> not
      fed, then the 20-token tail in one forward, greedy 48.
  C   the app's cold turn: prompt two in the engine's pieces, each piece encoded alone, one
      forward per piece, greedy 48.
  C2  prompt two encoded whole, one forward of 1,307 tokens, greedy 48.
  Cw  W's own ids in C's piece structure; run only if W's ids differ from C's.
  Rb  W with the reply's ids in one forward instead of one per step. Differs from W only in
      how the reply's positions were computed.
  St  W with the 20-token tail fed one per step. Differs from W only in the tail.
  S   all 1,307 ids one per step, greedy 48.
  W2, C_again: W (teacher-forced with W's ids) and C repeated, for determinism.

Usage: python arms.py <model.pte> <tokenizer.json> <out.json> [threads]
"""

import json
import sys
import time

import numpy as np
import torch
from executorch.extension.llm.custom_ops import custom_ops  # noqa: F401
from executorch.extension.pybindings import portable_lib as pl
import executorch.kernels.quantized  # noqa: F401
from tokenizers import Tokenizer

import prompts

pte, tok_path, out_path = sys.argv[1:4]
if len(sys.argv) > 4:
    pl._unsafe_reset_threadpool(int(sys.argv[4]))
THREADS = pl._threadpool_get_thread_count()
IM_END = 151645
TIMED = 48
POSITIONS = 1307

hf = Tokenizer.from_file(tok_path)


def enc(text):
    return hf.encode(text, add_special_tokens=False).ids


def frag(t):
    return hf.decode([t], skip_special_tokens=False)


def text(ids):
    return "".join(frag(t) for t in ids)


module = pl._load_for_executorch(pte)


class Run:
    """One arm: its position, the logit row of every position it fed, and the ids fed."""

    def __init__(self, name):
        self.name = name
        self.pos = 0
        self.rows = []
        self.ids = []
        self.calls = []
        self.ms = 0.0

    def feed(self, ids):
        t = torch.tensor([ids], dtype=torch.long)
        p = torch.arange(self.pos, self.pos + len(ids), dtype=torch.long)
        start = time.time()
        out = module.forward((t, p))[0]
        self.ms += (time.time() - start) * 1000
        logits = out[0].to(torch.float32).numpy().copy()
        assert logits.shape[0] == len(ids), logits.shape
        self.pos += len(ids)
        self.ids.extend(ids)
        if self.pos - len(ids) < POSITIONS:
            self.rows.extend(logits[: max(0, POSITIONS - (self.pos - len(ids)))])
        self.calls.append(len(ids))
        return logits[-1]

    def feed_steps(self, ids):
        last = None
        for t in ids:
            last = self.feed([t])
        return last

    def greedy(self, last, n, stop=None):
        """Greedy from the last fed position's logits: the ids, and the row behind each."""
        out, steps = [], [last]
        tok = int(np.argmax(last))
        while True:
            out.append(tok)
            if (stop is not None and tok == stop) or len(out) >= n:
                break
            last = self.feed([tok])
            steps.append(last)
            tok = int(np.argmax(last))
        return out, steps

    def structure(self):
        """Forward sizes, run-length encoded: [[size, count], ...]."""
        rle = []
        for c in self.calls:
            if rle and rle[-1][0] == c:
                rle[-1][1] += 1
            else:
                rle.append([c, 1])
        return rle


def top2(row):
    i = np.argpartition(-row, 2)[:2]
    i = i[np.argsort(-row[i])]
    return [[int(k), round(float(row[k]), 4), frag(int(k))] for k in i]


def margin(row):
    a, b = top2(row)
    return round(a[1] - b[1], 4)


rows_phone = prompts.phone_rows()
phone_warm = next(r for r in rows_phone if r["arm"] == "warm")
phone_cold = next(r for r in rows_phone if r["arm"] == "cold")
phone_turn_one = phone_warm["turn_one"]

p1 = prompts.prompt_one()
p1_pieces, p1_tail = prompts.pieces(p1)
p1_piece_ids = [enc(p) for p in p1_pieces] + [enc(p1_tail)]
p2 = prompts.prompt_two(phone_turn_one)
tail_text = p2[len(p1 + phone_turn_one):]
tail_ids = enc(tail_text)
p2_pieces, p2_tail = prompts.pieces(p2)
cold_piece_ids = [enc(p) for p in p2_pieces + [p2_tail]]

result = {
    "versions": {
        "executorch": "1.4.0 pybindings (pip wheel), XNNPACK",
        "torch": torch.__version__,
        "threads": THREADS,
        "machine": "Apple M5 (4P+6E), macOS",
    }
}
t0 = time.time()

# Turn one generated freely here, for reference only: the Mac's arithmetic is not the
# phone's, so its own greedy turn one need not be the phone's.
F = Run("free")
for ids in p1_piece_ids[:-1]:
    F.feed(ids)
last = F.feed(p1_piece_ids[-1])
prompt_one_len = F.pos
gen, gen_steps = F.greedy(last, n=1500, stop=IM_END)
mac_turn_one = text(gen[:-1] if gen[-1] == IM_END else gen)
canon_reply = enc(phone_turn_one)
tok_at = next((i for i, (a, b) in enumerate(zip(gen, canon_reply)) if a != b), None)
result["turn_one_free_on_mac"] = {
    "ended_on_im_end": gen[-1] == IM_END,
    "generated_tokens_incl_im_end": len(gen),
    "text_equals_phone_text": mac_turn_one == phone_turn_one,
    "first_char_diff_vs_phone": next(
        (i for i, (a, b) in enumerate(zip(mac_turn_one, phone_turn_one)) if a != b), None
    ),
    "first_token_diff_vs_phone_canonical": tok_at,
    "at_that_token": None if tok_at is None else {
        "shared_prefix_tail": text(gen[max(0, tok_at - 8):tok_at]),
        "mac_top2": top2(gen_steps[tok_at]),
        "mac_margin": margin(gen_steps[tok_at]),
        "phone_token": [canon_reply[tok_at], frag(canon_reply[tok_at])],
    },
    "generated_ids_equal_canonical_of_own_text": (gen[:-1] if gen[-1] == IM_END else gen)
    == enc(mac_turn_one),
    "text": mac_turn_one,
}
F.rows = []
del F
print("free", json.dumps(result["turn_one_free_on_mac"])[:800], flush=True)

# W: the phone's conversation. Prompt one in the engine's pieces, then the phone's reply fed
# one token per step as the runtime's decode loop fed it, with the ids the canonical
# encoding gives (the phone's 384 + <|im_end|> = 385 generated). At every step, whether the
# Mac's greedy choice is the next forced id, and how close the call was.
W = Run("W")
for ids in p1_piece_ids[:-1]:
    W.feed(ids)
last = W.feed(p1_piece_ids[-1])
reply_ids = canon_reply
forced = []
for i, nxt in enumerate(reply_ids + [IM_END]):
    a = int(np.argmax(last))
    if a != nxt:
        forced.append({
            "step": i,
            "forced": [nxt, frag(nxt)],
            "mac_top2": top2(last),
            "forced_logit_below_top": round(float(last[a] - last[nxt]), 4),
            "same_text_other_split": text(reply_ids[i:i + 4]).startswith(frag(a)) and frag(a) != frag(nxt),
        })
    if i < len(reply_ids):
        last = W.feed([nxt])
result["turn_one_forced"] = {
    "steps": len(reply_ids) + 1,
    "steps_where_mac_greedy_differs_from_phone_text": len(forced),
    "of_those_a_different_split_of_the_same_text": sum(f["same_text_other_split"] for f in forced),
    "detail": forced,
}
print("forced", json.dumps(result["turn_one_forced"])[:800], flush=True)

# The warm tail, and the timed reply.
last = W.feed(tail_ids)
W_reply, W_steps = W.greedy(last, n=TIMED)
assert W.pos == POSITIONS + TIMED - 1, W.pos
W_rows = np.stack(W.rows)
assert W_rows.shape[0] == POSITIONS
W_ids = W.ids[:POSITIONS]

n_first_five = sum(len(x) for x in p1_piece_ids[:-1])
regions = {
    "prompt_one_shared_pieces": (0, n_first_five),
    "prompt_one_last_piece": (n_first_five, prompt_one_len),
    "reply": (prompt_one_len, prompt_one_len + len(reply_ids)),
    "tail": (prompt_one_len + len(reply_ids), POSITIONS),
}
result["regions"] = {k: list(v) for k, v in regions.items()}
result["arms"] = {
    "W": {
        "reply": text(W_reply),
        "equals_phone_warm": text(W_reply) == phone_warm["reply"],
        "equals_phone_cold": text(W_reply) == phone_cold["reply"],
        "forwards": W.structure() if len(W.calls) < 30 else f"{len(W.calls)} forwards",
        "forward_ms": round(W.ms),
    }
}
replies = {"W": W_reply}
step_rows = {"W": W_steps}


def first_diff(a, b):
    return next((i for i, (x, y) in enumerate(zip(a, b)) if x != y), None)


def summarize(r, reply, steps):
    """The arm against W: its reply, logit distance by region, and where the replies part."""
    R = np.stack(r.rows)
    d = np.abs(R - W_rows).max(axis=1)
    agree = R.argmax(axis=1) == W_rows.argmax(axis=1)
    k = first_diff(W_reply, reply)
    entry = {
        "reply": text(reply),
        "equals_phone_warm": text(reply) == phone_warm["reply"],
        "equals_phone_cold": text(reply) == phone_cold["reply"],
        "forwards": r.structure() if len(r.calls) < 30 else f"{len(r.calls)} forwards",
        "forward_ms": round(r.ms),
        "ids_equal_W": r.ids[:POSITIONS] == W_ids,
        "max_abs_logit_diff_vs_W_by_region": {
            name: round(float(d[a:b].max()), 5) for name, (a, b) in regions.items()
        },
        "positions_bitwise_equal_to_W": int((d == 0).sum()),
        "first_position_differing_from_W": int(np.argmax(d > 0)) if (d > 0).any() else None,
        "argmax_disagreements_vs_W_in_1307": int((~agree).sum()),
        "last_prompt_position_max_abs_diff_vs_W": round(float(d[POSITIONS - 1]), 5),
        "reply_parts_from_W_at_token": k,
    }
    if k is not None:
        entry["at_parting"] = {
            "shared_prefix": text(reply[:k]),
            "W_top2": top2(W_steps[k]),
            "W_margin": margin(W_steps[k]),
            "arm_top2": top2(steps[k]),
            "arm_margin": margin(steps[k]),
            "max_abs_logit_diff_vs_W_at_this_step": round(float(np.abs(steps[k] - W_steps[k]).max()), 5),
        }
    result["arms"][r.name] = entry
    replies[r.name] = reply
    step_rows[r.name] = steps
    r.rows = []
    print(r.name, json.dumps(entry)[:600], flush=True)


def run_cold(name, pieces_ids):
    r = Run(name)
    for ids in pieces_ids[:-1]:
        r.feed(ids)
    last = r.feed(pieces_ids[-1])
    assert r.pos == POSITIONS, (name, r.pos)
    reply, steps = r.greedy(last, n=TIMED)
    summarize(r, reply, steps)
    return r


C = run_cold("C", cold_piece_ids)
result["ids"] = {
    "W_equals_C": C.ids[:POSITIONS] == W_ids,
    "C_pieces_equal_whole_encoding": C.ids[:POSITIONS] == enc(p2),
}
run_cold("C2", [enc(p2)])
if C.ids[:POSITIONS] != W_ids:
    sizes = [len(x) for x in cold_piece_ids]
    cuts, at = [], 0
    for s in sizes:
        cuts.append(W_ids[at:at + s])
        at += s
    run_cold("Cw", cuts)

# Rb: the reply in one forward.
Rb = Run("Rb")
for ids in p1_piece_ids:
    Rb.feed(ids)
Rb.feed(reply_ids)
last = Rb.feed(tail_ids)
summarize(Rb, *Rb.greedy(last, n=TIMED))

# St: the tail one per step.
St = Run("St")
for ids in p1_piece_ids:
    St.feed(ids)
St.feed_steps(reply_ids)
last = St.feed_steps(tail_ids)
summarize(St, *St.greedy(last, n=TIMED))

# S: everything one per step.
S = Run("S")
last = S.feed_steps(W_ids)
summarize(S, *S.greedy(last, n=TIMED))

# Repeats.
W2 = Run("W2")
for ids in p1_piece_ids:
    W2.feed(ids)
W2.feed_steps(reply_ids)
last = W2.feed(tail_ids)
summarize(W2, *W2.greedy(last, n=TIMED))
run_cold("C_again", cold_piece_ids)

for name in ("W", "C"):
    sm = sorted(((i, margin(s)) for i, s in enumerate(step_rows[name])), key=lambda x: x[1])[:5]
    result["arms"][name]["smallest_margins_in_timed_reply"] = [
        {"step": i, "margin": m, "top2": top2(step_rows[name][i])} for i, m in sm
    ]
result["reply_ids"] = {k: v for k, v in replies.items()}
result["turn_one_free_ids"] = gen
result["wall_s"] = round(time.time() - t0, 1)
json.dump(result, open(out_path, "w"), indent=1)
print(json.dumps({k: v for k, v in result.items() if k not in ("reply_ids", "turn_one_free_ids")}, indent=1))
