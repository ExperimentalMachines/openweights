"""Step 5: every way the phone's turn-one reply could be written in as many ids as the
canonical encoding, and how far the warm arm's decode was from writing any of them.

The phone generated 385 tokens: 384 plus <|im_end|>, exactly the canonical encoding's count.
Between added tokens (<think>, </think>), the reply is split into stretches; for each, a
dynamic programme over the byte-level text finds the fewest vocabulary ids that write it and
every segmentation that reaches that number, crossing pre-token boundaries or not. If the
canonical encoding is already the fewest for every stretch, an encoding with the phone's count
must also be the fewest for every stretch, so these segmentations are the only ones H1 can
mean. Each place where one differs from the canonical ids is scored on the .pte,
teacher-forced along the canonical reply one token per step, by branching into the
alternative at its position (the cache is indexed by position, so the canonical id fed again
at that position restores it): the alternative's summed log-probability against the
canonical path's over the differing ids, and the logit gap at the first of them.

Usage: python h1exact.py <model.pte> <tokenizer.json> <out.json>
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

pte, tok_path, out_path = sys.argv[1:4]
hf = Tokenizer.from_file(tok_path)
vocab = hf.get_vocab()  # byte-level strings, the alphabet pre_tokenize_str returns
longest = max(len(k) for k in vocab)
specials = [t.content for t in hf.get_added_tokens_decoder().values()]


def enc(text):
    return hf.encode(text, add_special_tokens=False).ids


reply = next(r for r in prompts.phone_rows() if r["arm"] == "warm")["turn_one"]
reply_ids = enc(reply)


def fewest(b):
    """Fewest ids for byte-level text b, and every segmentation reaching it (as strings)."""
    n = len(b)
    best, back = [None] * (n + 1), [[] for _ in range(n + 1)]
    best[0] = 0
    for i in range(n):
        if best[i] is None:
            continue
        for j in range(i + 1, min(n, i + longest) + 1):
            if b[i:j] in vocab:
                c = best[i] + 1
                if best[j] is None or c < best[j]:
                    best[j], back[j] = c, [i]
                elif c == best[j]:
                    back[j].append(i)

    def segs(j):
        if j == 0:
            return [[]]
        return [s + [b[i:j]] for i in back[j] for s in segs(i)]

    return best[n], segs(n)


stretches = []
windows = {}
index, rest = 0, reply
while rest:
    hits = sorted((rest.find(s), s) for s in specials if rest.find(s) >= 0)
    end = hits[0][0] if hits else len(rest)
    text = rest[:end]
    if text:
        b = "".join(p for p, _ in hf.pre_tokenizer.pre_tokenize_str(text))
        canon = [hf.id_to_token(t) for t in enc(text)]
        count, all_segs = fewest(b)
        stretches.append({"chars": len(text), "canonical_ids": len(canon), "fewest_ids": count,
                          "segmentations_with_fewest": len(all_segs)})
        for seg in all_segs:
            # Each stretch of disagreement, from where the two part to where their byte
            # offsets meet again, is one deviation the decode would have had to make.
            i = j = oi = oj = 0
            while i < len(canon):
                if canon[i] == seg[j]:
                    oi += len(canon[i])
                    oj += len(seg[j])
                    i += 1
                    j += 1
                    continue
                a0, b0 = i, j
                while True:
                    if oi <= oj:
                        oi += len(canon[i])
                        i += 1
                    else:
                        oj += len(seg[j])
                        j += 1
                    if oi == oj:
                        break
                key = (index + a0, tuple(canon[a0:i]), tuple(seg[b0:j]))
                windows[key] = True
        index += len(canon)
    if not hits:
        break
    index += 1  # the added token
    rest = rest[end + len(hits[0][1]):]
assert index == len(reply_ids), (index, len(reply_ids))

out = {"stretches": stretches, "differing_windows": len(windows)}
print(json.dumps(out, indent=1), flush=True)

module = pl._load_for_executorch(pte)
pos = 0


def feed(ids, at=None):
    global pos
    start = pos if at is None else at
    t = torch.tensor([ids], dtype=torch.long)
    p = torch.arange(start, start + len(ids), dtype=torch.long)
    row = module.forward((t, p))[0][0, -1].to(torch.float32).numpy().copy()
    if at is None:
        pos += len(ids)
    return row


def logsoftmax(x):
    m = x.max()
    return x - m - np.log(np.exp(x - m).sum())


def path_logprob(row, ids, base):
    total = 0.0
    for k, t in enumerate(ids):
        total += float(logsoftmax(row)[t])
        if k + 1 < len(ids):
            row = feed([t], at=base + k)
    return total


p1 = prompts.prompt_one()
p1_pieces, p1_tail = prompts.pieces(p1)
for piece in p1_pieces:
    feed(enc(piece))
row = feed(enc(p1_tail))
by_index = {}
for (at, can, alt) in windows:
    by_index.setdefault(at, []).append((can, alt))
scored = []
for i, target in enumerate(reply_ids):
    for can, alt in by_index.get(i, []):
        can_ids = [vocab[t] for t in can]
        alt_ids = [vocab[t] for t in alt]
        assert can_ids == reply_ids[i:i + len(can_ids)]
        lp_can = path_logprob(row, can_ids, pos)
        lp_alt = path_logprob(row, alt_ids, pos)
        scored.append({
            "reply_token": i,
            "canonical": [hf.decode([t]) for t in can_ids],
            "alternative": [hf.decode([t]) for t in alt_ids],
            "first_id_logit_gap": round(float(row[can_ids[0]] - row[alt_ids[0]]), 4),
            "logprob_canonical_minus_alternative": round(lp_can - lp_alt, 4),
        })
    row = feed([target])  # also overwrites any branch written at this position
scored.sort(key=lambda s: s["logprob_canonical_minus_alternative"])
out["scored"] = scored
json.dump(out, open(out_path, "w"), indent=1)
print(json.dumps(out, indent=1))
