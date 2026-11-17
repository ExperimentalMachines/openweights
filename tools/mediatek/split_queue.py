"""Queue lines for phone/chain.sh that never hold all three processors in one process.

The NPU at a 2k window takes 3.8 to 4.5 GB on the 12 GB Galaxy Tab S10+ (Llama 3.2 1B, Qwen3 0.6B),
and with the CPU's copy beside it the GPU's no longer fit (session 5, Qwen3 0.6B: 1,329 MB left
against 1,460 needed), so a model's nine cells run in three processes:
  NPU + CPU: cpu:cpu, cpu:npu, npu:cpu, npu:npu
  NPU + GPU: gpu:npu, npu:gpu, gpu:gpu
  CPU + GPU: cpu:gpu, gpu:cpu
Within a process the cells keep the model's seeded order (phone/cell-orders.txt); the processes
run in the order of their first cell. Each line's estimate (minutes, the chain's deadline check) is
the time left for the whole model from that line on, so the first line checks the full model and a
model never starts unless all its processes fit (a partial model on one unit would be rerun).

  python3 split_queue.py <gguf> <npu dir> <estimate minutes> [cell-orders.txt] [-- bench args...]
"""
import sys

args = sys.argv[1:]
extra = ""
if "--" in args:
    extra = " " + " ".join(args[args.index("--") + 1 :])
    args = args[: args.index("--")]
gguf, npu, est = args[0], args[1], int(args[2])
orders = args[3] if len(args) > 3 else "phone/cell-orders.txt"
order = next(l.split()[1] for l in open(orders) if l.split()[0] == gguf).split(",")
groups = [
    ("npu", {"cpu:cpu", "cpu:npu", "npu:cpu", "npu:npu"}),
    ("npu", {"gpu:npu", "npu:gpu", "gpu:gpu"}),
    ("-", {"cpu:gpu", "gpu:cpu"}),
]
lines = []
for needs_npu, cells in groups:
    mine = [c for c in order if c in cells]
    lines.append((order.index(mine[0]), npu if needs_npu == "npu" else "-", mine))
lines.sort()
left = est
for _, d, mine in lines:
    print(f"{gguf} {d} {','.join(mine)} {max(1, round(left))}{extra}")
    left -= est * len(mine) / 9
