"""The second-turn prompt exactly as the app sends it, and the engine's piece cutting.

Mirrors three pieces of Kotlin, transcribed rather than approximated:
  ExecuTorchOnDeviceTest.describing(40) and the turn-two question,
  Qwen3Prompt.render(verbatimHistory = true) for user/assistant turns with no tools,
  ExecuTorchEngine.feedText/warmPiece (800-char pieces cut after a line break that ends its
  run, else before a space; the last 1,600 chars go to generate; callChars =
  min(1600, get_max_seq_len - 1) = 1600 for this export).
"""

import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROWS = os.path.join(HERE, "..", "pa3q", "greedy-qwen3-int8.coldwarm.jsonl")

WARM_PIECE_CHARS = 800
CALL_CHARS = 1600  # min(GENERATE_TAIL_CHARS = 1600, get_max_seq_len 2048 - 1)
ASK = "In one word, what did I just describe?"


def long_prompt(sentences=40):
    s = "Here is a description of a system I am building. "
    s += (
        "It runs language models on a phone, entirely on device, with no "
        "network involved at any point in generation. "
    ) * sentences
    return s + "Acknowledge briefly."


def block(role, content):
    return "<|im_start|>" + role + "\n" + content + "<|im_end|>\n"


def prompt_one():
    return block("user", long_prompt()) + "<|im_start|>assistant\n"


def prompt_two(reply):
    return (
        block("user", long_prompt())
        + block("assistant", reply)
        + block("user", ASK)
        + "<|im_start|>assistant\n"
    )


def warm_piece(text, limit=CALL_CHARS):
    most = min(WARM_PIECE_CHARS, limit)
    if len(text) <= most:
        return text
    window = text[:most]
    newline = window.rfind("\n")
    while newline > 0 and newline + 1 < len(text) and text[newline + 1].isspace():
        newline = window.rfind("\n", 0, newline)
    if newline > 0:
        cut = newline + 1
    else:
        sp = window.rfind(" ")
        cut = sp if sp > 0 else most
    return text[:cut]


def pieces(fresh, keep_tail=CALL_CHARS):
    """The prefill pieces and the generate tail, as ExecuTorchEngine.feedText cuts them."""
    out = []
    rest = fresh
    while len(rest) > keep_tail:
        p = warm_piece(rest)
        out.append(p)
        rest = rest[len(p):]
    return out, rest


def phone_rows():
    with open(ROWS) as f:
        return [json.loads(line) for line in f]
