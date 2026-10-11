# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "scipy"]
# ///
# Sync check against the event manifest (out/music.json). Every picture event, and every note of the
# sonic logo, has its own sound saved alone (out/cues.npz), so each is timed without its neighbours:
# a whoosh by where its envelope peaks, everything else by its onset. The summed effects stem
# its stem is checked too (out/fx.wav for events, out/lead.wav for the logo notes, which are part of
# the lead line), so a cue masked or shifted among its neighbours would still show.
#   uv run sync.py
import json
import wave
from pathlib import Path

import numpy as np

HERE = Path(__file__).parent
m = json.loads((HERE / 'out/music.json').read_text())
cues = np.load(HERE / 'out/cues.npz')


def read(name):
    w = wave.open(str(HERE / f'out/{name}.wav'))
    return w.getframerate(), np.frombuffer(w.readframes(w.getnframes()), dtype='<i2') / 32768


sr, fx = read('fx')
_, lead = read('lead')
win = int(0.004 * sr)


def env(x):
    return np.sqrt(np.convolve(x ** 2, np.ones(win) / win, mode='same'))


def offset(x, t, whoosh):
    e = env(x)
    if whoosh:
        a, b = int((t - 0.25) * sr), int((t + 0.1) * sr)
        return (a + int(np.argmax(e[a:b]))) / sr - t, 0.03
    a, b = int((t - 0.03) * sr), int((t + 0.05) * sr)
    seg = e[a:b]
    return (a + int(np.argmax(seg > 0.25 * seg.max()))) / sr - t, 0.012


rows = [(e, cues[f'e{i}'], fx) for i, e in enumerate(m['events'])]
rows += [({**h, 'sound': 'logo note'}, cues[f'l{i}'], lead) for i, h in enumerate(m['logo_hits'])]
worst, bad = 0.0, 0
for e, cue, stem in rows:
    wh = e['sound'].startswith('whoosh')
    d, tol = offset(cue, e['t'], wh)
    ds, _ = offset(stem, e['t'], wh)
    ok = abs(d) <= tol and abs(ds) <= tol
    worst = max(worst, abs(d), abs(ds)); bad += not ok
    print(f"{e['t']:7.3f}  {e['picture'][:40]:40s} {e['sound'][:26]:26s} alone {d * 1000:+6.1f} ms  in stem {ds * 1000:+6.1f} ms  {'ok' if ok else 'OFF'}")
print(f"{len(rows)} cues ({len(m['events'])} events, {len(m['logo_hits'])} logo notes), {bad} off, worst {worst * 1000:.1f} ms (a 60 fps frame is 16.7 ms)")
