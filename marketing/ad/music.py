# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "scipy"]
# ///
# The ad's track: 120 BPM bright electro in E major, synthesised from scratch (no samples, nothing
# licensed). Seeded, so every run writes the same file. A bar is two seconds and scene cuts land
# on bar lines; bar() in ad.html uses the same grid, and both read the recordings' moments from
# assets/clips/*.json.
#
# The sound follows the story. The hook is sparse: a pad and a heartbeat under a question. The
# logo drops the groove and plays the sonic logo: three plucks whose lengths follow the mark's
# three bars (1, 0.7, 0.41), falling to the tonic. Under the proof scenes the groove sits lower.
# The switches get a breakdown and one soft click per switch, then the groove returns for the
# Hub, peaks on the build and resolves on the end card.
#   uv run music.py   -> out/music.wav, out/music.json
import json
import wave
from pathlib import Path

import numpy as np
from scipy.signal import butter, sosfilt

HERE = Path(__file__).parent
SR = 48000
BPM = 120
BEAT = 60 / BPM
BAR = 4 * BEAT
BARS = 29
LENGTH = BARS * BAR + 0.9  # the last chord rings out
N = int(LENGTH * SR)
rng = np.random.default_rng(20261011)


def t_of(bar, beat=0.0):
    return (bar * 4 + beat) * BEAT


def clip(name):
    return json.loads((HERE / 'assets/clips' / name).read_text())['clip_marks']


# The story, as ad.html places it.
LOGO, LEASE, TOOLS, FIT, BUILD, END = 3, 5, 12, 16, 20, 25
_lease, _tools, _fit, _canvas = clip('npu-lease.json'), clip('tools.json'), clip('fit.json'), clip('canvas.json')
SEND = t_of(5, 3)
FIRST_TEXT = SEND + (_lease['first_text'] - _lease['sent'])
_from = _tools['off'][0] - t_of(0, 2)
OFF = [t_of(TOOLS) + (c - _from) for c in _tools['off']]
SEARCH = t_of(FIT) + 0.9
VERDICTS = t_of(17, 3)  # the Poco opens its NPU builds
ASK = t_of(20, 2)
LATER = t_of(22, 2)
RUN_TAP = t_of(23, 2)
PAGE = RUN_TAP + (_canvas['page_shown'] - _canvas['run_tapped'])


def empty():
    return np.zeros(N)


def place(buf, start, sig, gain=1.0):
    i = int(round(start * SR))
    if i >= N or i < 0:
        return
    j = min(N, i + len(sig))
    buf[i:j] += sig[: j - i] * gain


def env(n, a=0.002, d=0.2, curve=4.0):
    t = np.arange(n) / SR
    return np.clip(t / max(a, 1e-6), 0, 1) * np.exp(-curve * np.clip(t - a, 0, None) / max(d, 1e-6))


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def filt(sig, kind, f, order=2):
    w = np.clip(np.array(f, dtype=float) / (SR / 2), 1e-4, 0.999)
    return sosfilt(butter(order, w, btype=kind, output='sos'), sig)


def saw(freq, n, phase=0.0):
    return 2 * ((freq * np.arange(n) / SR + phase) % 1.0) - 1


# --- instruments ---------------------------------------------------------------------

def kick(soft=False):
    n = int(0.42 * SR)
    t = np.arange(n) / SR
    f = 46 + (95 if soft else 135) * np.exp(-t * 32)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.002, 0.34, 4.2)
    click = filt(rng.standard_normal(n), 'band', (1800, 6000)) * env(n, 0.0005, 0.004, 6) * (0.07 if soft else 0.2)
    return np.tanh((body + click) * 1.5)


def clap():
    n = int(0.3 * SR)
    noise = filt(rng.standard_normal(n), 'band', (1100, 6000))
    e = np.zeros(n)
    for k, off in enumerate((0, 0.009, 0.019)):
        i = int(off * SR)
        e[i:] += env(n - i, 0.0005, 0.01 if k < 2 else 0.14, 5)
    return noise * e * 0.48


def hat(open_=False, accent=1.0):
    n = int((0.22 if open_ else 0.05) * SR)
    return filt(rng.standard_normal(n), 'high', 7500) * env(n, 0.001, 0.12 if open_ else 0.02, 4) * 0.2 * accent


def fm(midi, dur, ratio=2.0, index=1.3, decay=7.0):
    """A clean FM pluck: the sonic logo and the arp."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    mod = np.sin(2 * np.pi * hz(midi) * ratio * t) * index * np.exp(-t * decay)
    return np.sin(2 * np.pi * hz(midi) * t + mod) * env(n, 0.002, dur * 0.7, 4)


def bell(midi, dur=1.2):
    n = int(dur * SR)
    t = np.arange(n) / SR
    mod = np.sin(2 * np.pi * hz(midi) * 3.5 * t) * 1.7 * np.exp(-t * 5)
    return np.sin(2 * np.pi * hz(midi) * t + mod) * env(n, 0.001, dur * 0.6, 4) * 0.25


def pad(notes, dur, cutoff=1600, attack=0.4):
    n = int(dur * SR)
    out = np.zeros(n)
    for m in notes:
        for d in (-0.08, 0.0, 0.08):
            out += saw(hz(m) * 2 ** (d / 12), n, rng.random())
    out = filt(out / (len(notes) * 3), 'low', cutoff)
    return out * env(n, attack, dur, 0.8)


def bass(midi, dur):
    n = int(dur * SR)
    t = np.arange(n) / SR
    tone = np.sin(2 * np.pi * hz(midi) * t) * 0.85 + filt(saw(hz(midi), n), 'low', 600) * 0.38
    return tone * env(n, 0.004, dur * 0.85, 2.4)


def riser(dur):
    n = int(dur * SR)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    for s in range(24):
        a, b = s * n // 24, (s + 1) * n // 24
        lo = 300 + 5200 * (s / 24) ** 2
        out[a:b] = filt(noise[a:b], 'band', (lo, min(lo * 2.2, 18000)))
    return out * (t / dur) ** 2 * 0.33


def swell(dur=0.6):
    n = int(dur * SR)
    t = np.arange(n) / SR
    return filt(rng.standard_normal(n), 'band', (700, 6500)) * np.sin(np.pi * t / dur) ** 2 * 0.15


def thump(size=1.0):
    """A clean hit: a sub drop with a little air, no crash."""
    n = int(1.4 * SR)
    t = np.arange(n) / SR
    sub = np.sin(2 * np.pi * np.cumsum(42 + 54 * np.exp(-t * 8)) / SR) * env(n, 0.002, 1.0, 3)
    air = filt(rng.standard_normal(n), 'band', (2200, 10000)) * env(n, 0.002, 0.45, 4) * 0.12
    return np.tanh((sub + air) * 1.2) * size


def switch():
    """A toggle going off: a small, dry, low click. Felt more than heard."""
    n = int(0.08 * SR)
    t = np.arange(n) / SR
    body = np.sin(2 * np.pi * 900 * t) * env(n, 0.0003, 0.012, 6)
    tick = filt(rng.standard_normal(n), 'band', (2500, 6000)) * env(n, 0.0003, 0.004, 6) * 0.5
    return (body * 0.6 + tick) * 0.5


def tap(at, gain=0.22):
    """A finger on glass."""
    place(fx, at, fm(96, 0.06, index=0.5, decay=60), gain)


# --- arrangement ----------------------------------------------------------------------
# E - B - C#m - A, a chord a bar: bright and moving.
CHORDS = [[64, 68, 71], [59, 63, 66], [61, 64, 68], [57, 61, 64]]
ROOTS = [40, 35, 37, 33]
# The sonic logo: B, G#, E, as long as the mark's bars are long.
LOGO_NOTES = [(83, 0.9), (80, 0.63), (76, 0.37)]
# The arp: the chord, up and back, in sixteenths.
ARP = [0, 1, 2, 1, 2, 1, 0, 1]

drums, low, keys, lead, fx = empty(), empty(), empty(), empty(), empty()
K, KS, CL = kick(), kick(soft=True), clap()


def groove(b, clapping=True, gain=1.0, from_beat=0, to_beat=4):
    start, root, chord = t_of(b), ROOTS[b % 4], CHORDS[b % 4]
    for k in range(from_beat, to_beat):
        place(drums, start + k * BEAT, K, 0.95 * gain)
        place(drums, start + k * BEAT + BEAT / 2, hat(open_=True), 0.8 * gain)
        place(drums, start + k * BEAT + BEAT / 4, hat(accent=0.6), gain)
        place(drums, start + k * BEAT + 3 * BEAT / 4, hat(accent=0.6), gain)
        if clapping and k in (1, 3):
            place(drums, start + k * BEAT, CL, 0.8 * gain)
    for e in range(from_beat * 2, to_beat * 2):
        note = root + (12 if e % 2 else 0)
        place(low, start + e * BEAT / 2, bass(note, BEAT / 2), 0.8 * gain)
    place(keys, start + from_beat * BEAT, pad(chord, BEAT * (to_beat - from_beat), cutoff=2400, attack=0.04), 0.34 * gain)


def arp(b, gain=0.16, octave=12):
    start, chord = t_of(b), CHORDS[b % 4]
    for s in range(16):
        place(lead, start + s * BEAT / 4, fm(chord[ARP[s % 8]] + octave, 0.22, index=0.9, decay=14), gain)


def sonic_logo(at, gain=0.42):
    for i, (note, dur) in enumerate(LOGO_NOTES):
        place(lead, at + i * BEAT, fm(note, dur, index=1.6, decay=5), gain)


for b in range(BARS):
    start, chord, root = t_of(b), CHORDS[b % 4], ROOTS[b % 4]
    if b < LOGO:
        # The question: a pad, and a heartbeat from bar 1; a riser into the logo.
        place(keys, start, pad(chord, BAR, cutoff=900 if b < 2 else 1300, attack=0.6), 0.48)
        if b >= 1:
            place(drums, start, KS, 0.55)
            place(drums, start + 2 * BEAT, KS, 0.45)
        if b == 2:
            place(fx, start + BEAT, riser(BAR - BEAT * 1.4), 0.9)
    elif b < LEASE:
        groove(b)
    elif b < TOOLS:
        # The proof: the groove a step lower, no clap until the answer is streaming.
        groove(b, clapping=b >= LEASE + 2, gain=0.8)
    elif b < FIT:
        # The switches: a breakdown, the bass held, a soft heartbeat; a riser back in.
        place(keys, start, pad(chord, BAR, cutoff=1200, attack=0.3), 0.5)
        place(low, start, bass(root, BAR * 0.9), 0.5)
        place(drums, start, KS, 0.5)
        place(drums, start + 2 * BEAT, KS, 0.4)
        if b == FIT - 1:
            place(fx, start + BEAT, riser(BAR - BEAT * 1.4), 0.8)
    elif b < BUILD:
        groove(b)
        if b >= FIT + 2:
            arp(b, gain=0.12)
    elif b < END:
        # The build: everything.
        groove(b)
        arp(b, gain=0.17)
    elif b < BARS - 1:
        groove(b, clapping=False, gain=0.85)
    # A swell into each cut.
    if b in (LEASE, TOOLS, FIT, BUILD):
        place(fx, start - 0.55, swell(), 1.0)

# The moments.
place(fx, 1.5, swell(0.5), 0.7)                     # the highlighter on the deposit clause
tap(2.5, 0.16)                                      # "So bring the AI to them."
place(fx, t_of(LOGO), thump(1.0), 0.9)
sonic_logo(t_of(LOGO) + 0.12)                       # the mark's bars build on these
place(fx, t_of(LOGO) + 0.9, bell(88, 1.2), 0.4)     # the wordmark
place(fx, t_of(LOGO) + 1.65, bell(83, 0.8), 0.3)    # Free, Open source, No account, together
tap(SEND)
place(fx, FIRST_TEXT, bell(88, 1.4), 0.7)           # the answer starts: 0.23 s for the whole lease
place(fx, FIRST_TEXT + 2.5, bell(83, 1.0), 0.35)    # and how fast it writes
for i, at in enumerate(OFF):                        # one soft click per switch, nothing more
    place(fx, at, switch(), 0.9)
tap(SEARCH, 0.16)
place(fx, VERDICTS, bell(88, 1.0), 0.5)             # the verdicts
tap(ASK)
place(fx, LATER - 0.5, swell(0.5), 0.9)            # a minute later
place(fx, LATER, thump(0.45), 0.5)
tap(RUN_TAP, 0.3)
place(fx, PAGE, thump(0.6), 0.6)                    # and it is live
place(fx, PAGE, bell(88, 1.4), 0.55)
place(fx, t_of(END), thump(0.95), 0.85)
sonic_logo(t_of(END) + 0.12, 0.45)
# The final chord rings out after the last bar.
place(keys, t_of(BARS - 1), pad(CHORDS[0] + [76], BAR + 0.9, cutoff=2600, attack=0.02), 0.6)
place(lead, t_of(BARS - 1), fm(83, 2.2, decay=3), 0.3)
place(fx, t_of(BARS - 1), thump(0.55), 0.55)

# Sidechain: the kick gently ducks the melodic parts wherever the groove runs.
duck = np.ones(N)
for b in range(BARS - 1):
    if b < LOGO or TOOLS <= b < FIT:
        continue
    for k in range(4):
        i, n = int(t_of(b, k) * SR), int(BEAT * SR)
        curve = 1 - 0.45 * np.exp(-np.arange(n) / SR * 12)
        duck[i:i + n] = np.minimum(duck[i:i + n], curve[: len(duck[i:i + n])])

# A dotted-eighth echo on the lead, wider in the right channel.
d = int(BEAT * 0.75 * SR)
echo = np.zeros(N)
echo[d:] = lead[:-d] * 0.3
time = np.arange(N) / SR
mix = drums * 0.85 + (low * 0.85 + keys * 0.7 + lead * 0.8) * duck + fx
mix[(time >= t_of(LOGO) - BEAT / 2) & (time < t_of(LOGO))] *= 0.1  # a breath before the drop
mix = filt(mix, 'high', 28)
left = np.tanh((mix + echo * 0.55) * 1.15)
right = np.tanh((mix + np.roll(echo, d // 2) * 0.55) * 1.15)
fade = np.ones(N)
tail = int(1.6 * SR)
fade[-tail:] = np.linspace(1, 0, tail) ** 2
st = np.stack([left * fade, right * fade], axis=1)
st /= np.max(np.abs(st)) / 10 ** (-1 / 20)

OUT = HERE / 'out'
OUT.mkdir(exist_ok=True)
with wave.open(str(OUT / 'music.wav'), 'wb') as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((st * 32767).astype('<i2').tobytes())
json.dump({'bpm': BPM, 'beat': BEAT, 'bar': BAR, 'bars': BARS, 'length': LENGTH,
           'moments': {'send': SEND, 'first_text': FIRST_TEXT, 'off': OFF, 'verdicts': VERDICTS,
                       'ask': ASK, 'later': LATER, 'run': RUN_TAP, 'page': PAGE}}, open(OUT / 'music.json', 'w'), indent=1)
print(f'music: {LENGTH:.2f} s, {BARS} bars at {BPM} BPM; switches at {[round(x, 2) for x in OFF]}')
