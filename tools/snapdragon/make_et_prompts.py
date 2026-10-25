"""Token files for ExecuTorch's qnn_llama_runner, from the same conversations pd_bench replays.

The runner has no teacher-forced multi-turn mode: it either continues from its own replies or reads
one tokenized prompt. So each turn becomes one file holding the whole conversation up to that turn,
rendered as plain ChatML (the template llama.cpp's built-in renderer gave Qwen3 in pd_bench), with
the dataset's assistant replies, ending in the generation header. The runner reads it fresh, so its
prefill covers the whole history every time, unlike pd_bench, which reads only the new tokens.

  python3 make_et_prompts.py conversations.jsonl tokenizer.json out_dir [max_tokens]
"""
import json, struct, sys
from pathlib import Path
from tokenizers import Tokenizer

convs, tok_path, out = sys.argv[1], sys.argv[2], Path(sys.argv[3])
max_tokens = int(sys.argv[4]) if len(sys.argv) > 4 else 2048 - 64 - 8
MAX_TURNS = 6
tok = Tokenizer.from_file(tok_path)
out.mkdir(parents=True, exist_ok=True)
manifest = []
for line in open(convs):
    c = json.loads(line)
    text, turn = "", 0
    for m in c["messages"]:
        if turn >= MAX_TURNS:
            break
        if m["role"] == "assistant":
            text += f"<|im_start|>assistant\n{m['content']}<|im_end|>\n"
            continue
        text += f"<|im_start|>user\n{m['content']}<|im_end|>\n"
        prompt = text + "<|im_start|>assistant\n"
        ids = tok.encode(prompt, add_special_tokens=False).ids
        if len(ids) > max_tokens:
            break
        name = f"{c['id']}_t{turn}.bin"
        (out / name).write_bytes(struct.pack(f"<{len(ids)}Q", *ids))
        manifest.append({"file": name, "conv": c["id"], "source": c["source"], "bucket": c["bucket"],
                         "turn": turn, "prompt_tokens": len(ids)})
        turn += 1
(out / "manifest.json").write_text(json.dumps(manifest, indent=1))
print(len(manifest), "prompts; tokens", sorted(m["prompt_tokens"] for m in manifest))
