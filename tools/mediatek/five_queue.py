"""Queue lines for a model whose NPU export and llama.cpp copy do not fit in memory together: the
five pairings without a handoff, in two processes (NPU alone; CPU and GPU), each process in the
order of its first cell in the model's seeded order (phone/cell-orders.txt). Estimates as in
split_queue.py: each line carries the time left for the whole model from that line on.

  python3 five_queue.py <gguf> <npu dir> <minutes for the llama.cpp process> <minutes for npu:npu> [-- bench args]
"""
import sys

args = sys.argv[1:]
extra = ""
if "--" in args:
    extra = " " + " ".join(args[args.index("--") + 1 :])
    args = args[: args.index("--")]
gguf, npu, llama_min, npu_min = args[0], args[1], int(args[2]), int(args[3])
order = next(l.split()[1] for l in open("phone/cell-orders.txt") if l.split()[0] == gguf).split(",")
llama = [c for c in order if c in ("cpu:cpu", "gpu:gpu", "cpu:gpu", "gpu:cpu")]
lines = [(order.index("npu:npu"), npu, ["npu:npu"], npu_min), (order.index(llama[0]), "-", llama, llama_min)]
lines.sort()
left = llama_min + npu_min
for _, d, cells, m in lines:
    print(f"{gguf} {d} {','.join(cells)} {left}{extra}")
    left -= m
