"""Real multi-turn conversations for the Snapdragon prefill/decode matrix, by length.

Every conversation is a real exchange from a public dataset, with the dataset's own assistant
replies, because pd_bench teacher-forces history so that all nine processor pairings read the
same tokens. MT-Bench is left out for that reason: it ships user turns only.

WildChat-1M, UltraChat 200k and Daring-Anteater.

Selection is mechanical, so it cannot favour any processor: walk each dataset from the start,
keep English conversations with at least two user turns, put each in a length bucket by total
characters, and take the first N that land in each bucket. Writes conversations.jsonl.
"""
import json, subprocess, time, urllib.parse

API = "https://datasets-server.huggingface.co/rows"
PER_BUCKET = 1
# Characters across the whole conversation; about four to a token for English.
BUCKETS = [("short", 600, 3000), ("medium", 3000, 10000), ("long", 10000, 24000)]
MIN_USER_TURNS = 2


def rows(dataset, offset, split="train", length=100):
    q = urllib.parse.urlencode({"dataset": dataset, "config": "default", "split": split,
                                "offset": offset, "length": length})
    for attempt in range(4):
        out = subprocess.run(["curl", "-sS", "--max-time", "90", f"{API}?{q}"], capture_output=True, text=True)
        try:
            return json.loads(out.stdout).get("rows", [])
        except Exception:
            time.sleep(3 * (attempt + 1))
    return []


def norm(role):
    r = (role or "").lower()
    if r in ("human", "user", "prompter"):
        return "user"
    if r in ("gpt", "assistant", "bot", "chatgpt"):
        return "assistant"
    return None


def from_wildchat(row):
    if row.get("language") != "English":
        return []
    return [{"role": norm(m.get("role")), "content": m["content"]}
            for m in row.get("conversation") or [] if norm(m.get("role")) and m.get("content")]


def from_messages(row):
    return [{"role": norm(m.get("role")), "content": m["content"]}
            for m in row.get("messages") or [] if norm(m.get("role")) and m.get("content")]


def from_conversations(row):
    return [{"role": norm(m.get("from")), "content": m["value"]}
            for m in row.get("conversations") or [] if norm(m.get("from")) and m.get("value")]


SOURCES = [
    # ShareGPT52K was the third source first; the datasets server cannot read it ("The dataset
    # generation failed"), so UltraChat 200k's SFT split took its place.
    ("WildChat-1M", "allenai/WildChat-1M", "train", from_wildchat),
    ("UltraChat-200k", "HuggingFaceH4/ultrachat_200k", "train_sft", from_messages),
    ("Daring-Anteater", "nvidia/Daring-Anteater", "train", from_conversations),
]


def well_formed(msgs):
    # Strictly alternating, starting with the user, so a chat template accepts it as is.
    if not msgs or msgs[0]["role"] != "user":
        return False
    return all(msgs[i]["role"] != msgs[i - 1]["role"] for i in range(1, len(msgs)))


kept = []
for name, ds, split, fn in SOURCES:
    want = {b[0]: PER_BUCKET for b in BUCKETS}
    offset = 0
    while any(want.values()) and offset < 3000:
        batch = rows(ds, offset, split)
        if not batch:
            break
        for r in batch:
            msgs = fn(r["row"])
            if msgs and msgs[-1]["role"] == "user":
                msgs = msgs[:-1]
            users = sum(1 for m in msgs if m["role"] == "user")
            chars = sum(len(m["content"]) for m in msgs)
            if users < MIN_USER_TURNS or not well_formed(msgs):
                continue
            for bname, lo, hi in BUCKETS:
                if lo <= chars < hi and want[bname] > 0:
                    kept.append({"id": f"{name}-{r['row_idx']}", "source": name, "bucket": bname,
                                 "user_turns": users, "chars": chars, "messages": msgs})
                    want[bname] -= 1
        offset += len(batch)
    print(name, "short/medium/long still wanted:", want, "rows read:", offset)

with open("conversations.jsonl", "w") as f:
    for c in kept:
        f.write(json.dumps(c) + "\n")
print("kept", len(kept))
for c in kept:
    print(c["id"], c["bucket"], c["user_turns"], c["chars"])
