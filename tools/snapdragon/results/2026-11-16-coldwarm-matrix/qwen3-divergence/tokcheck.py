"""Step 1, tokenizer only: can the two arms have fed different token ids?

Compares, for the phone's own turn-one reply text:
  the runtime's tokenizer (pytorch_tokenizers C++ HFTokenizer, the code the AAR links) and
  Hugging Face `tokenizers` on the same tokenizer.json;
  the cold arm's ids (prompt two cut into the engine's pieces, each piece encoded alone)
  against the warm arm's ids (turn one's pieces, then the reply re-encoded, then the tail)
  and against one encode of the whole prompt.
The warm arm holds the reply as generated, which no tokenizer can tell us; this only says
whether the canonical encoding of the reply has the reply's length and whether the piece
and tail boundaries are clean. Usage: python tokcheck.py (run from this folder) <tokenizer.json> <out.json>
"""

import json
import sys

import prompts
import pytorch_tokenizers.pytorch_tokenizers_cpp as cpp
from tokenizers import Tokenizer

tok_path, out_path = sys.argv[1], sys.argv[2]
hf = Tokenizer.from_file(tok_path)
rt = cpp.HFTokenizer()
rt.load(tok_path)


def enc_rt(text):
    return list(rt.encode(text, 0, 0))


def enc_hf(text):
    return hf.encode(text, add_special_tokens=False).ids


rows = prompts.phone_rows()
warm_rows = [r for r in rows if r["arm"] == "warm"]
replies = {r["turn_one"] for r in warm_rows}
assert len(replies) == 1, "turn one differed between pairs"
reply = replies.pop()
p1 = prompts.prompt_one()
p2 = prompts.prompt_two(reply)
assert p2.startswith(p1 + reply)
tail_text = p2[len(p1 + reply):]
rec = warm_rows[0]["record"]

out = {
    "reply_chars": len(reply),
    "reply_is_ascii": reply.isascii(),
    "prompt_one_chars": len(p1),
    "prompt_two_chars": len(p2),
    "record_on_phone": rec,
    "record_here": f"extends, record {len(p1) + len(reply)} of {len(p2)} chars",
    "turn_one_tokens_on_phone": warm_rows[0]["turn_one_tokens"],
}

# The runtime's tokenizer against Hugging Face's on each text that matters.
for name, text in [("prompt_one", p1), ("reply", reply), ("tail", tail_text), ("prompt_two", p2)]:
    a, b = enc_rt(text), enc_hf(text)
    out[f"{name}_tokens_runtime"] = len(a)
    out[f"{name}_runtime_equals_hf"] = a == b

# Turn one, warm: pieces of prompt one, then generate's tail, each encoded alone.
p1_pieces, p1_tail = prompts.pieces(p1)
warm_prompt_ids = [t for p in p1_pieces + [p1_tail] for t in enc_rt(p)]
reply_ids = enc_rt(reply)
tail_ids = enc_rt(tail_text)
warm_ids_if_canonical = warm_prompt_ids + reply_ids + tail_ids

# Cold: prompt two cut the same way, each piece encoded alone.
p2_pieces, p2_tail = prompts.pieces(p2)
cold_ids = [t for p in p2_pieces + [p2_tail] for t in enc_rt(p)]
whole_ids = enc_rt(p2)

out["prompt_one_piece_chars"] = [len(p) for p in p1_pieces] + [len(p1_tail)]
out["prompt_two_piece_chars"] = [len(p) for p in p2_pieces] + [len(p2_tail)]
out["prompt_one_pieces_equal_whole"] = warm_prompt_ids == enc_rt(p1)
out["prompt_two_pieces_equal_whole"] = cold_ids == whole_ids
out["head_plus_tail_equal_whole"] = enc_rt(p1 + reply) + tail_ids == whole_ids
out["warm_if_reply_canonical_equals_cold"] = warm_ids_if_canonical == cold_ids
out["positions_warm_if_canonical"] = len(warm_ids_if_canonical)
out["positions_cold"] = len(cold_ids)
out["tail_tokens"] = len(tail_ids)
out["reply_tokens_canonical"] = len(reply_ids)
# The reply re-encoded, then decoded token by token: what the phone's callback fragments
# would have been if generation had produced the canonical ids.
out["reply_canonical_last_fragments"] = [hf.decode([t]) for t in reply_ids[-5:]] + ["<|im_end|>"]
out["turn_one_tail_on_phone"] = warm_rows[0]["turn_one_tail"]

# Where a reply re-encoding could plausibly differ from generation: pieces that are not one
# pre-token, such as multi-newline runs and spaces before newlines.
risky = []
for i, t in enumerate(reply_ids):
    s = hf.decode([t])
    if "\n" in s and s.strip("\n") != "" or s.endswith(" \n") or s == " ":
        risky.append({"index": i, "id": t, "text": s})
out["reply_tokens_with_mixed_whitespace"] = risky

json.dump(
    {
        "summary": out,
        "ids": {
            "prompt_one_pieces": warm_prompt_ids,
            "reply_canonical": reply_ids,
            "tail": tail_ids,
            "cold_pieces": cold_ids,
        },
    },
    open(out_path, "w"),
    indent=1,
)
print(json.dumps(out, indent=1))
