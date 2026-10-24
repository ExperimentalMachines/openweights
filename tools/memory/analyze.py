# /// script
# requires-python = ">=3.11"
# dependencies = ["gguf"]
# ///
# Tables from MemoryPressureBenchmark reports (tools/memory/results/<run>/*.mem.json).
#
#   uv run tools/memory/analyze.py <results-dir> [--models-dir ~/ow-models]
#
# Peak: what each model held at its highest, during the load and during a turn, against what
# the fit verdict promises for it (FitEstimator: weights + KV cache at the window + 450 MB for a
# GGUF, weights x 2.4 + 450 MB for a .pte). The KV figure is read from the GGUF header on this
# Mac the way GgufMetadata.kvCacheBytes reads it on the phone: every layer's KV heads, times the
# key and value widths, times the window, times two bytes.
#
# Pressure: per balloon level, decode and prefill speed against what came back in from swap
# during the turn (major faults, and the fall in VmSwap), and CPU time over wall time, which
# says whether a slow turn was computing or waiting.
import json
import statistics
import sys
from pathlib import Path

MIB = 1024 * 1024
OVERHEAD = 450 * MIB
COMPILED_FACTOR = 1.4
# The phone's names for files whose originals on the Mac are named by their model.
ALIASES = {'vl.gguf': 'LFM2.5-VL-3B-Q4_0.gguf', 'model.gguf': 'LFM2.5-1.2B-Instruct-QAD-Q4_0.gguf'}


def kv_bytes(path: Path, context: int) -> int | None:
    from gguf import GGUFReader
    try:
        r = GGUFReader(str(path))
    except Exception:
        return None
    f = {k: v for k, v in r.fields.items()}

    def val(key):
        for k, v in f.items():
            if k.endswith(key):
                data = [v.parts[i] for i in v.data]
                return [int(x[0]) for x in data] if len(data) > 1 else int(data[0][0])
        return None
    blocks, heads, emb = val('.block_count'), val('.attention.head_count'), val('.embedding_length')
    kvh = val('.attention.head_count_kv')
    per_layer = kvh if isinstance(kvh, list) else [kvh or heads] * blocks
    klen, vlen = val('.attention.key_length'), val('.attention.value_length')
    head = klen or (emb // (heads if isinstance(heads, int) else max(heads)))
    return sum(per_layer) * (head + (vlen or head)) * context * 2


def main():
    run = Path(sys.argv[1])
    models_dir = Path(sys.argv[sys.argv.index('--models-dir') + 1] if '--models-dir' in sys.argv else '~/ow-models').expanduser()
    reports = [json.loads(p.read_text()) for p in sorted(run.glob('*.mem.json'))]
    mb = lambda kb: kb / 1024

    print('## Peak memory (MB)\n')
    print('| model | label | file | load peak | settled RSS | turn peak | HWM | promised | peak over promise |')
    print('|---|---|---|---|---|---|---|---|---|')
    for r in reports:
        if 'error' in r:
            print(f"| {r['model']} | {r['label']} | error: {r['error']} |")
            continue
        file_b = r['file_bytes']
        if r['model'].endswith('.pte'):
            promised = file_b + file_b * COMPILED_FACTOR + OVERHEAD
        else:
            local = models_dir / ALIASES.get(r['model'], r['model'])
            kv = kv_bytes(local, r['context']) if local.exists() else None
            promised = file_b + kv + OVERHEAD if kv is not None else None
        turn_peak = max([t['peak']['footprint_kb'] for t in r['turns']] or [0])
        hwm = max([r['loaded']['hwm_kb']] + [t['after']['hwm_kb'] for t in r['turns']])
        peak = max(r['load_peak']['footprint_kb'], turn_peak)
        over = f"{(peak * 1024 / promised - 1) * 100:+.0f}%" if promised else 'n/a'
        print(f"| {r['model']} | {r['label']} | {file_b / MIB:.0f} | {mb(r['load_peak']['footprint_kb']):.0f} | "
              f"{mb(r['loaded']['rss_kb']):.0f} | {mb(turn_peak):.0f} | {mb(hwm):.0f} | "
              f"{promised / MIB:.0f} | {over} |" if promised else
              f"| {r['model']} | {r['label']} | {file_b / MIB:.0f} | {mb(r['load_peak']['footprint_kb']):.0f} | "
              f"{mb(r['loaded']['rss_kb']):.0f} | {mb(turn_peak):.0f} | {mb(hwm):.0f} | n/a | n/a |")

    print('\n## Per turn\n')
    print('| model | label | turn | prefill t/s | decode t/s | wall s | major faults | faults/token | swap in MB | swap after MB | cpu/wall | min avail MB |')
    print('|---|---|---|---|---|---|---|---|---|---|---|---|')
    for r in reports:
        for t in r.get('turns', []):
            tokens = t['prompt_tokens'] + t['generated_tokens']
            swap_in = (t['before']['swap_kb'] - t['after']['swap_kb']) / 1024
            print(f"| {r['model']} | {r['label']} | {t['turn']} | "
                  f"{t['prompt_tokens'] * 1000 / max(t['prefill_ms'], 1):.1f} | "
                  f"{t['generated_tokens'] * 1000 / max(t['decode_ms'], 1):.2f} | {t['wall_ms'] / 1000:.1f} | "
                  f"{t['major_faults']} | {t['major_faults'] / max(tokens, 1):.1f} | {swap_in:.0f} | "
                  f"{t['after']['swap_kb'] / 1024:.0f} | {t['cpu_ms'] / max(t['wall_ms'], 1):.2f} | "
                  f"{t['peak']['min_available_kb'] / 1024:.0f} |")


    # Only runs given a lead: without one every row has the key but no turn prefetched.
    prefetched = [r for r in reports if r.get('lead_s') is not None]
    if not prefetched:
        return
    # Odd turns brought the weights back `lead` seconds before they started and even ones did
    # not (MemoryPressureBenchmark's `lead`), so the two arms are interleaved in one run.
    print('\n## Prefetch, interleaved\n')
    # Send to first token counts the wait for a prefetch step still running when the turn began.
    print('| model | label | turn | arm | looks | brought back MB | prefetch faults | out at send MB | '
          'prefill s | send to first token s | major faults | wall s |')
    print('|---|---|---|---|---|---|---|---|---|---|---|---|')
    for r in prefetched:
        arms = {'prefetch': [], 'none': []}
        for t in r['turns']:
            if t['turn'] == 0:
                continue
            p = t.get('prefetch')
            arm = 'prefetch' if p else 'none'
            arms[arm].append(t)
            print(f"| {r['model']} | {r['label']} | {t['turn']} | {arm} | "
                  f"{(p or {}).get('looks', '')} | {(p or {}).get('brought_back_mb', '')} | "
                  f"{(p or {}).get('major_faults', '')} | {t.get('out_at_send_mb')} | "
                  f"{t['prefill_ms'] / 1000:.1f} | {send_to_first(t) / 1000:.1f} | {t['major_faults']} | "
                  f"{t['wall_ms'] / 1000:.1f} |")
        for arm, rows in arms.items():
            if rows:
                prefill = statistics.median(t['prefill_ms'] for t in rows) / 1000
                first = statistics.median(send_to_first(t) for t in rows) / 1000
                faults = round(statistics.median(t['major_faults'] for t in rows))
                print(f"| {r['model']} | {r['label']} | median of {len(rows)} | {arm} | | | | | "
                      f"{prefill:.1f} | {first:.1f} | {faults} | |")



def send_to_first(t: dict) -> int:
    # Measured from Send to the first token on the test's side: the join, the reset and the
    # whole native turn up to its first piece.
    return t['send_to_first_token_ms']


if __name__ == '__main__':
    main()
