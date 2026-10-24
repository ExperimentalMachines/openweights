# Tables from PrefetchMatrixBenchmark reports (tools/memory/results/matrix-<run>/).
#
#   python3 tools/memory/analyze_matrix.py <results-dir> [--expect-models a.gguf,b.gguf]
#       [--expect-levels 4096,0] [--expect-cells single:false,single:true,multi:false,multi:true]
#
# Fixed before the reports it reads existed:
#
# - Unit: a pair. In single-turn cells, one question asked twice from a reset context, once with
#   the prefetch and once without, in a balanced, seeded order. In multi-turn cells, the k-th
#   follow-up of two conversations with the same questions, one with the prefetch in every gap
#   and one without; the pairs of one block share that block's two conversations.
# - Primary outcome: time from Send to the first streamed token. Paired difference = without
#   minus with, so a positive number means the prefetch turn began answering sooner.
# - Per cell, primary: every paired difference, their median and mean, and the count of pairs
#   the prefetch won. In single-turn cells each pair is its own randomization, so an exact
#   two-sided sign test is given (ties dropped, reported). In multi-turn cells the randomized
#   unit is the block (two conversations), so follow-ups are not independent: no test is
#   given, only each block's mean difference. A percentile bootstrap of the median is shown
#   as a description: pairs in single-turn cells, whole blocks in multi-turn ones.
# - A pair counts only when its two turns match the schedule: the same question, block and
#   depth, one turn in each position, no duplicate. Anything else is listed, not estimated.
# - Secondary, per cell: process major faults in the turn and in window plus turn; prefill
#   summed over the turn's rounds; process CPU in the window; failures by arm; pairs whose two
#   turns did different work (different rounds or generated tokens), which are counted, never
#   dropped.
# - Kills: low memory kills in the log, split by whether the victim was this benchmark, and by
#   phase (window, turn) and arm. A cell with no log is reported as unknown, not as zero.
# - Exploratory only: pairs whose control turn found 256 MB or more of the weights out at Send.
#   That is measured after assignment, so it is a description of where the effect lives, not an
#   unbiased subgroup.
# - Pooled across cells: counts only, no interval; the cells differ too much to weight.
# - Completeness: the expected cells, models and pairs are checked, and anything missing is
#   listed at the top; the script exits non-zero when the matrix is incomplete.
import json
import math
import random
import re
import statistics
import sys
from pathlib import Path

PRESSURED_MB = 256
RESAMPLES = 10_000


def arg(name: str, default: str) -> str:
    return sys.argv[sys.argv.index(name) + 1] if name in sys.argv else default


def sign_test(wins: int, losses: int) -> float:
    n = wins + losses
    if n == 0:
        return 1.0
    k = min(wins, losses)
    tail = sum(math.comb(n, i) for i in range(k + 1)) / 2 ** n
    return min(1.0, 2 * tail)


def bootstrap(groups: list[list[float]]) -> tuple[float, float]:
    rng = random.Random(7)
    meds = []
    for _ in range(RESAMPLES):
        sample = [d for g in rng.choices(groups, k=len(groups)) for d in g]
        meds.append(statistics.median(sample))
    meds.sort()
    return meds[int(0.025 * RESAMPLES)], meds[int(0.975 * RESAMPLES) - 1]


def stft(t: dict) -> float | None:
    v = t.get('send_to_first_token_ms')
    return None if v is None or 'error' in t else v / 1000


def pairs_of(r: dict) -> tuple[list[tuple[dict, dict]], list[str]]:
    by_pair: dict[int, dict[str, list[dict]]] = {}
    problems = []
    for t in r['turns']:
        by_pair.setdefault(t['pair'], {}).setdefault(t['arm'], []).append(t)
    expected = r['pairs']
    for p in by_pair:
        if not 0 <= p < expected:
            problems.append(f"pair {p} is not in the schedule (0 to {expected - 1})")
    good = []
    for p in range(expected):
        arms = by_pair.get(p, {})
        if any(len(v) > 1 for v in arms.values()):
            problems.append(f"pair {p} has a duplicate turn; left out")
            continue
        w, n = (arms.get('prefetch') or [None])[0], (arms.get('none') or [None])[0]
        if w is None or n is None:
            problems.append(f"pair {p} missing {'prefetch' if w is None else 'control'} turn")
            continue
        keys = ('question', 'block', 'depth')
        if any(w.get(k) != n.get(k) for k in keys) or {w.get('position'), n.get('position')} != {0, 1}:
            problems.append(f"pair {p} does not match its schedule (question/block/depth/position); left out")
            continue
        if stft(w) is None or stft(n) is None:
            bad = w if stft(w) is None else n
            kind = 'not run' if str(bad.get('error', '')).startswith('not run') else 'failed'
            problems.append(f"pair {p} {kind} in the {bad['arm']} arm: {bad.get('error', 'no token')[:70]}")
            continue
        good.append((w, n))
    return good, problems


def cell_name(r: dict) -> str:
    return f"{r['model'].removesuffix('.gguf')} {r['label']} {r['shape']}{' tools' if r['tools'] else ''}"


def effect_row(name: str, r: dict | None, ps: list[tuple[dict, dict]]) -> str:
    if not ps:
        return f"| {name} | 0 | | | | | | | | |"
    diffs = [stft(n) - stft(w) for w, n in ps]
    wins = sum(d > 0 for d in diffs)
    losses = sum(d < 0 for d in diffs)
    if r is not None and r['shape'] == 'multi':
        blocks: dict[int, list[float]] = {}
        for (w, n), d in zip(ps, diffs):
            blocks.setdefault(w.get('block', 0), []).append(d)
        groups = list(blocks.values())
    else:
        groups = [[d] for d in diffs]
    lo, hi = bootstrap(groups) if len(groups) > 1 else (float('nan'), float('nan'))
    faults = statistics.median(n['turn_major_faults'] - w['turn_major_faults'] for w, n in ps)
    total = statistics.median((n['turn_major_faults'] + n['window_major_faults'])
                              - (w['turn_major_faults'] + w['window_major_faults']) for w, n in ps)
    prefill = statistics.median((sum(n['prefill_ms']) - sum(w['prefill_ms'])) / 1000 for w, n in ps)
    cpu = statistics.median(w['window_cpu_ms'] - n['window_cpu_ms'] for w, n in ps)
    mismatched = sum(1 for w, n in ps if w.get('rounds') != n.get('rounds')
                     or w.get('generated_tokens') != n.get('generated_tokens')
                     or w.get('unanswered_calls') != n.get('unanswered_calls'))
    if r is not None and r['shape'] == 'multi':
        test = f"{wins}-{losses}; blocks " + ", ".join(f"{statistics.mean(g):+.2f}" for g in groups)
    else:
        test = f"{wins}-{losses} (p={sign_test(wins, losses):.3f})"
    return (f"| {name} | {len(ps)} | {statistics.median(diffs):+.2f} / {statistics.mean(diffs):+.2f} s | "
            f"[{lo:+.2f}, {hi:+.2f}] | {test} | "
            f"{faults:+,.0f} | {total:+,.0f} | {prefill:+.2f} s | {cpu:+,.0f} ms | {mismatched} |")


def kills(log: Path) -> tuple[list[tuple[float, str]], float] | None:
    """The kills in a cell's log and the time its first line was written. A ring buffer read
    at the end of a cell keeps only its tail, so turns before that first line are not covered."""
    if not log.exists():
        return None
    out = []
    first = None
    for line in log.read_text(errors='replace').splitlines():
        stamp = re.match(r"\s*(\d+\.\d+)\s", line)
        if stamp and first is None:
            first = float(stamp[1])
        m = re.match(r"\s*(\d+\.\d+)\s.*lowmemorykiller.*Kill '([^']*)'", line)
        if m:
            out.append((float(m[1]), m[2]))
    return out, first if first is not None else float('inf')


def main() -> int:
    run = Path(sys.argv[1])
    reports = [json.loads(p.read_text()) for p in sorted(run.glob('*.prefetch.json'))]
    incomplete = []
    expect_models = [m for m in arg('--expect-models', '').split(',') if m]
    expect_levels = [lv for lv in arg('--expect-levels', '').split(',') if lv]
    expect_cells = [c for c in arg('--expect-cells', '').split(',') if c]
    if not (expect_models and expect_levels and expect_cells):
        print('--expect-models, --expect-levels and --expect-cells are all required')
        return 2
    if not reports:
        print('no reports')
        return 2
    runs = {r.get('run') for r in reports}
    if len(runs) != 1:
        incomplete.append(f"reports from more than one run: {sorted(map(str, runs))}")
    seen = {(r['model'], r['label'], r['shape'], str(r['tools']).lower()) for r in reports}
    for m in expect_models:
        for lv in expect_levels:
            for c in expect_cells:
                shape, tools = c.split(':')
                if (m, f"b{lv}", shape, tools) not in seen:
                    incomplete.append(f"no report for {m} b{lv} {shape} tools={tools}")
    status = run / 'cells.tsv'
    invalid_cells = set()
    if status.exists():
        for line in status.read_text().splitlines()[1:]:
            level, cell, _, state = line.split('\t')[:4]
            if not state.startswith('done') or 'balloon' in state:
                invalid_cells.add((level, cell))
                incomplete.append(f"runner: {level} {cell} ended '{state[:60]}'")
    else:
        incomplete.append('no cells.tsv from the runner')

    print('## Completeness\n')
    for r in reports:
        _, problems = pairs_of(r)
        if r.get('error'):
            problems.insert(0, f"cell error: {r['error'][:90]}")
        for p in problems:
            incomplete.append(f"{cell_name(r)}: {p}")
    print('\n'.join(f"- {x}" for x in incomplete) if incomplete else '- every expected pair is present')
    if status.exists():
        print('\nCells as the runner saw them:\n')
        print('```\n' + status.read_text().strip() + '\n```')

    print('\n## Paired effect per cell (without minus with; positive favours the prefetch)\n')
    print('| cell | pairs | send to first token, median / mean | bootstrap 95% (description) | '
          'prefetch won-lost (sign test) | turn faults | window + turn faults | prefill | extra window CPU | '
          'pairs with different work |')
    print('|---|---|---|---|---|---|---|---|---|---|')
    pooled_w = pooled_l = 0
    for r in reports:
        ps, _ = pairs_of(r)
        key = (r['label'], f"{r['shape']}:{str(r['tools']).lower()}")
        print(effect_row(cell_name(r) + (' (runner flagged)' if key in invalid_cells else ''), r, ps))
        diffs = [stft(n) - stft(w) for w, n in ps]
        pooled_w += sum(d > 0 for d in diffs)
        pooled_l += sum(d < 0 for d in diffs)
    print(f"\nAll cells, counts only: the prefetch turn answered sooner in {pooled_w} pairs and later in "
          f"{pooled_l}.")

    print('\n## Exploratory: pairs whose control turn had at least 256 MB of weights out at Send\n')
    print('| cell | pairs | send to first token, median / mean | bootstrap 95% (description) | '
          'prefetch won-lost (sign test) | turn faults | window + turn faults | prefill | extra window CPU | '
          'pairs with different work |')
    print('|---|---|---|---|---|---|---|---|---|---|')
    for r in reports:
        ps, _ = pairs_of(r)
        hot = [(w, n) for w, n in ps if n.get('out_at_send_mb') is not None and n['out_at_send_mb'] >= PRESSURED_MB]
        if hot:
            print(effect_row(cell_name(r), r, hot))

    print('\n## Failures by arm\n')
    print('| cell | prefetch: failed / not run / no token | control: failed / not run / no token |')
    print('|---|---|---|')
    for r in reports:
        f = {(a, k): 0 for a in ('prefetch', 'none') for k in ('failed', 'not run', 'no token')}
        for t in r['turns']:
            if 'error' in t:
                f[(t['arm'], 'not run' if str(t['error']).startswith('not run') else 'failed')] += 1
            elif t.get('send_to_first_token_ms') is None:
                f[(t['arm'], 'no token')] += 1
        print(f"| {cell_name(r)} | {f[('prefetch', 'failed')]} / {f[('prefetch', 'not run')]} / "
              f"{f[('prefetch', 'no token')]} | {f[('none', 'failed')]} / {f[('none', 'not run')]} / "
              f"{f[('none', 'no token')]} |")

    print('\n## Low memory kills, by arm and phase\n')
    print('| cell | other apps: prefetch window / control window | other apps: prefetch turn / control turn | '
          'this benchmark killed | turns before the log begins |')
    print('|---|---|---|---|---|')
    for r in reports:
        tag = f"{r['label']}-{r['shape']}-{'true' if r['tools'] else 'false'}"
        ks = kills(run / f"{tag}.logcat.txt")
        if ks is None:
            print(f"| {cell_name(r)} | unknown (no log) | unknown | unknown | all |")
            continue
        ks, first = ks
        uncovered = 0
        counts = {(a, ph): 0 for a in ('prefetch', 'none') for ph in ('window', 'turn')}
        ours = 0
        for t in r['turns']:
            if 'window_open_wall_ms' not in t:
                continue
            if t['window_open_wall_ms'] / 1000 < first:
                uncovered += 1
            spans = {'window': (t['window_open_wall_ms'], t['send_wall_ms']),
                     'turn': (t['send_wall_ms'], t.get('end_wall_ms', t['send_wall_ms']))}
            for ph, (a, b) in spans.items():
                for when, victim in ks:
                    if a / 1000 <= when < b / 1000:
                        if 'openweights' in victim:
                            ours += 1
                        else:
                            counts[(t['arm'], ph)] += 1
        anywhere = sum(1 for _, victim in ks if 'openweights' in victim)
        print(f"| {cell_name(r)} | {counts[('prefetch', 'window')]} / {counts[('none', 'window')]} | "
              f"{counts[('prefetch', 'turn')]} / {counts[('none', 'turn')]} | {anywhere} in the cell's log | "
              f"{uncovered} of {len(r['turns'])} |")

    print('\n## Every turn\n')
    print('| cell | pair | arm | out at Send MB | brought back MB | walked MB | rounds | send to first token s | '
          'turn faults | window faults | prefill s | battery C | flag |')
    print('|---|---|---|---|---|---|---|---|---|---|---|---|---|')
    for r in reports:
        for t in sorted(r['turns'], key=lambda t: (t['pair'], t['arm'])):
            if 'error' in t and 'send_wall_ms' not in t:
                print(f"| {cell_name(r)} | {t['pair']} | {t['arm']} | {t['error'][:60]} |")
                continue
            s = stft(t)
            print(f"| {cell_name(r)} | {t['pair']} | {t['arm']} | {t.get('out_at_send_mb')} | "
                  f"{t.get('brought_back_mb')} | {t.get('walked_mb', '-')} | {t.get('rounds', '-')} | "
                  f"{'-' if s is None else f'{s:.1f}'} | {t.get('turn_major_faults', 0):,} | "
                  f"{t.get('window_major_faults', 0):,} | {sum(t.get('prefill_ms', [])) / 1000:.1f} | "
                  f"{t.get('battery_temp_c')} | {t.get('paging_flag', '-')} |"
                  + (f" error: {t['error'][:50]}" if 'error' in t else ''))
    return 1 if incomplete else 0


if __name__ == '__main__':
    sys.exit(main())
