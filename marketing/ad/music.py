# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "scipy"]
# ///
# The ad's track: 128 BPM in A major, synthesised from scratch with synth.py (no samples, nothing
# licensed, seeded). Scenes start on beats of this grid and the hits inside them are moments read
# from the recordings (assets/clips/*.json). music.json lists every picture event with the sound
# designed for it; music.py measures each cue's level against the mix around it, and sync.py checks
# the timing in the rendered and the encoded audio.
#
# The arc: a dry question (Airplane mode, a tap); the groove under the first answer, the bass on its
# first words; the bed drops back as the photo goes in and returns with its answer; the sonic logo on
# the brand; a lighter bed with the logo's motif under the model choice; a build through the two
# closing lines into a breath; and the biggest arrival, resolving on A, for the call to action.
#   uv run music.py   -> out/music.wav, out/fx.wav, out/music.json
import json
import wave
from pathlib import Path

import numpy as np

import synth as s

HERE = Path(__file__).parent
SR = s.SR
BPM = 128
BEAT = 60 / BPM
BAR = 4 * BEAT


def at(bar, beat=0.0):
    return (bar * 4 + beat) * BEAT


def marks(name):
    return json.loads((HERE / 'assets/clips' / name).read_text())['clip_marks']


SC = {'hook': at(0), 'chat': at(1), 'photo': at(4), 'logo': at(6), 'fit': at(7),
      'values': at(9), 'end': at(11), 'out': at(13, 2)}
TAIL = 0.8
LENGTH = SC['out'] + TAIL
N = int(LENGTH * SR)
_air, _pae = marks('airplane-bill.json'), marks('paella.json')
MO = {
    'air_tap': at(0, 1),
    'chat_send': at(1, 1),
    'chat_quote': at(2, 3),       # the whole phrase is on the phone by then (clip 13.4 s)
    'photo_send': at(4, 1),
    'photo_cut': at(4, 2),
    'photo_quote': at(5),         # "paella" is on the phone by then
    'fits': at(8),
    'line1': SC['values'],
    'line2': at(10),
}
MO['chat_text'] = MO['chat_send'] + (_air['first_text'] - _air['sent'])
MO['photo_text'] = MO['photo_cut'] + 0.2

A_, E_, Fm, D_ = [57, 61, 64], [52, 56, 59], [54, 57, 61], [50, 54, 57]
ROOT = {id(A_): 33, id(E_): 28, id(Fm): 30, id(D_): 26}
# One chord a bar, written out so the call to action lands on A whatever the bar count.
PROG = [A_, A_, E_, Fm, D_, E_, A_, Fm, D_, D_, E_, A_, A_, A_, A_]


def chord_at(t):
    return PROG[min(len(PROG) - 1, int(t / BAR + 1e-6))]


def root_at(t):
    return ROOT[id(chord_at(t))]


LOGO_NOTES = [(81, 0.62), (76, 0.44), (69, 0.26)]


def buf():
    return np.zeros(N)


def place(b, start, sig, gain=1.0):
    i = int(round(start * SR))
    if i >= N or i < 0:
        return
    j = min(N, i + len(sig))
    b[i:j] += sig[: j - i] * gain


kick_b, clap_b, bass_b, lead_b, fx_b, rise_b, verb_send, delay_send = (buf() for _ in range(8))
hatsS = np.zeros((N, 2)); keysS = np.zeros((N, 2)); leadS = np.zeros((N, 2))
K, CL = s.kick(), s.clap()
kicks = []


def place_st(b, start, sig, p, gain=1.0):
    place(b[:, 0], start, sig, gain * np.cos((p + 1) * np.pi / 4))
    place(b[:, 1], start, sig, gain * np.sin((p + 1) * np.pi / 4))


def beats(t0, t1):
    return [k * BEAT for k in range(int(round(t0 / BEAT)), int(round(t1 / BEAT)))]


def drums(t0, t1, *, clap=True, hats='closed', gain=1.0, kick=True):
    for b in beats(t0, t1):
        k = int(round(b / BEAT)) % 4
        if kick:
            place(kick_b, b, K, 0.95 * gain)
            kicks.append(b)
        if clap and k in (1, 3):
            place(clap_b, b, CL, 0.7 * gain)
            place(verb_send, b, CL, 0.3 * gain)
        if hats in ('closed', 'full'):
            for i, q in enumerate((0.25, 0.75)):
                place_st(hatsS, b + q * BEAT, s.hat(vel=0.55 + 0.15 * i), -0.3 if i == 0 else 0.3, gain)
        if hats == 'full':
            place_st(hatsS, b + 0.5 * BEAT, s.hat(open_=True, vel=0.7, choke=0.25 * BEAT), 0.15, gain)


# The bass: syncopated, with rests, root and octave and a fifth.
BASS_PATTERN = [(0, 0), (2, 0), (3, 12), (5, 0), (6, 7), (7, 12)]   # (eighth, interval)


def bassline(t0, t1, gain=1.0):
    for bstart in [k * BAR for k in range(int(t0 / BAR - 1e-6), int(np.ceil(t1 / BAR)))]:
        for e, iv in BASS_PATTERN:
            tt = bstart + e * BEAT / 2
            if t0 - 1e-6 <= tt < t1:
                place(bass_b, tt, s.bass(root_at(tt) + iv, BEAT / 2 * 0.85), 0.85 * gain)


def stabs(t0, t1, gain=1.0):
    for i, b in enumerate(beats(t0, t1)):
        if int(round(b / BEAT)) % 4 in (1, 3):
            st = s.chord([n + 12 for n in chord_at(b)], BEAT * 0.8, vel=0.8)
            place_st(keysS, b + BEAT / 2, st, -0.25 if i % 4 < 2 else 0.25, 0.55 * gain)
            place(verb_send, b + BEAT / 2, st, 0.15 * gain)


def motif(t0, t1, gain=0.14):
    """The sonic logo's three notes as a quiet figure, once a bar: the film's one tune."""
    for bstart in [k * BAR for k in range(int(round(t0 / BAR)), int(round(t1 / BAR)))]:
        for i, (note, dur) in enumerate(LOGO_NOTES):
            p = s.pluck(note - 12, dur * 0.8, bright=0.6)
            place_st(leadS, bstart + (1.5 + i) * BEAT, p, (-0.35, 0.0, 0.35)[i], gain)
            place(delay_send, bstart + (1.5 + i) * BEAT, p, gain * 0.5)


LOGO_HITS = []   # each logo note with the bar it draws, checked like an event


def sonic_logo(t, gain=0.5, key='logo'):
    for i, (note, dur) in enumerate(LOGO_NOTES):
        p = s.pluck(note, dur, bright=1.0)
        place(lead_b, t + i * BEAT, p, gain)
        b = buf(); place(b, t + i * BEAT, p, gain)
        LOGO_HITS.append({'t': round(t + i * BEAT, 4), 'picture': f'{key} bar {i + 1}', 'key': f'{key}{i}', 'cue': b})
        place(verb_send, t + i * BEAT, p, gain * 0.6)
        place(delay_send, t + i * BEAT, p, gain * 0.35)


EVENTS = []
cue_b = {}   # each event's own sound, alone, for the audibility check


def event(t, what, sound, sig, gain=1.0, key=None):
    place(fx_b, t, sig, gain)
    b = buf(); place(b, t, sig, gain); cue_b[len(EVENTS)] = b
    EVENTS.append({'t': round(t, 4), 'picture': what, 'sound': sound, 'key': key})


# --- the film -------------------------------------------------------------------------
def whoosh_at(t, what, gain=0.9, tail=0.22):
    sig = s.whoosh(0.36, tail)
    place(fx_b, t - 0.36, sig, gain)
    b = buf(); place(b, t - 0.36, sig, gain); cue_b[len(EVENTS)] = b
    EVENTS.append({'t': round(t, 4), 'picture': what, 'sound': 'whoosh peak', 'key': 'cut'})


place(kick_b, 0, K, 0.6); kicks.append(0)
place_st(keysS, 0, s.pad(A_, BAR, cutoff=900, attack=0.25), 0, 0.55)
event(MO['air_tap'], 'Airplane mode turns on', 'tick', s.tick(), 1.3, 'tap')


whoosh_at(SC['chat'], 'cut to the chat')
drums(SC['chat'], at(2), clap=False, hats='closed', gain=0.8)
event(MO['chat_send'], 'Send', 'tick', s.tick(), 2.2, 'tap')
event(MO['chat_text'], 'the answer starts; "Still answers."', 'chime + bass entry', s.chime(88), 0.9, 't2')
place(bass_b, MO['chat_text'], s.bass(root_at(MO['chat_text']), at(2) - MO['chat_text'] - 0.01), 0.8)
drums(at(2), SC['photo'], clap=True, hats='closed')
bassline(at(2), SC['photo'])
stabs(at(2), SC['photo'], 0.8)
event(MO['chat_quote'], 'the phrase, word for word', 'soft pluck (logo note)', s.pluck(81, 0.4, bright=0.6), 1.5, 'quote1')

# The photo: the bed drops back (no stabs, no clap, softer kick) as the photo goes in, the cut past
# the wait lands on a whoosh with the bed held, and it rebuilds on the answer.
whoosh_at(SC['photo'], 'cut to the photo', 1.3)
drums(SC['photo'], MO['photo_cut'], clap=False, hats='closed', gain=0.62)
event(MO['photo_send'], 'Send', 'tick', s.tick(), 2.4, 'tap')
whoosh_at(MO['photo_cut'], 'cut past the wait', 1.1, 0.3)
event(MO['photo_text'], 'the paella answer starts; "Still offline."', 'chime', s.chime(91), 1.0, 't4')
drums(at(4, 3), SC['logo'], clap=True, hats='full')
bassline(at(4, 3), SC['logo'])
stabs(at(5), SC['logo'], 0.8)
event(MO['photo_quote'], 'the identification, word for word', 'soft pluck (logo note)', s.pluck(76, 0.4, bright=0.6), 1.5, 'quote2')
place(rise_b, at(5, 1), s.riser(SC['logo'] - at(5, 1)), 0.75)

# The brand: a modest accent (the big one is saved for the call to action) and the sonic logo.
event(SC['logo'], 'brand', 'impact + sonic logo', s.impact(0.6, 0.9), 0.7, 'word')
sonic_logo(SC['logo'] + 0.01, 0.48, 'logo')
drums(SC['logo'] + BEAT, SC['fit'], clap=True, hats='closed', gain=0.85)
bassline(SC['logo'], SC['fit'], 0.85)

# Choose a model: a lighter bed with the logo's motif.
whoosh_at(SC['fit'], 'cut to the model', 1.3)
drums(SC['fit'], SC['values'], clap=True, hats='closed', gain=0.7)
bassline(SC['fit'], SC['values'], 0.7)
motif(SC['fit'], SC['values'], 0.16)
event(MO['fits'], '"It shows what fits."', 'soft pluck (logo note)', s.pluck(81, 0.4, bright=0.6), 1.0, 't3')

# The two lines: the bed builds, a short hit on each, then a breath before the arrival.
drums(SC['values'], at(10, 3), clap=True, hats='full')
bassline(SC['values'], at(10, 3))
stabs(SC['values'], at(10, 3))
motif(SC['values'], SC['end'], 0.2)
event(MO['line1'], '"Free and open source."', 'short hit', s.impact(0.5, 0.45), 0.7, 'line1')
event(MO['line2'], '"No account. No analytics."', 'short hit', s.impact(0.5, 0.45), 0.7, 'line2')
place(rise_b, at(10, 1), s.riser(SC['end'] - at(10, 1)), 1.0)

# The call to action: the biggest arrival, on A, wide and full; then the chord.
event(SC['end'], 'end card: Get it on Google Play', 'big impact + sonic logo + A chord', s.impact(1.0, 1.6), 1.0, 'cta')
sonic_logo(SC['end'] + 0.01, 0.6, 'end')
for p_, v in ((-0.5, 0.75), (0.5, 0.75)):
    st = s.chord([n + 12 for n in A_] + [76], BAR * 0.95, vel=1.0)
    place_st(keysS, SC['end'], st, p_, v)
place(verb_send, SC['end'], s.chord([n + 12 for n in A_] + [76], BAR * 0.95), 0.35)
drums(SC['end'], at(12), clap=True, hats='full', gain=1.0)
bassline(SC['end'], at(12), 1.0)
stabs(SC['end'] + BEAT, at(12), 1.0)
drums(at(12), at(13), clap=False, hats='none', gain=0.55)
place_st(keysS, at(12), s.pad(A_ + [69], SC['out'] - at(12) + TAIL, cutoff=2400, attack=0.05), 0, 0.8)
place(lead_b, at(13), s.pluck(69, 1.4, bright=0.5), 0.3)

# --- the mix ---------------------------------------------------------------------------
# Sidechain: a 3 ms dip after each kick, released over ~120 ms and tapered to exactly unity.
duck = np.ones(N)
for k in kicks:
    i = int(k * SR); n = int(0.3 * SR)
    tt = np.arange(n) / SR
    curve = 1 - 0.35 * np.minimum(1, tt / 0.003) * np.exp(-np.maximum(0, tt - 0.003) / 0.12)
    taper = np.clip((0.3 - tt) / 0.02, 0, 1)
    curve = 1 - (1 - curve) * taper
    j = min(N, i + n)
    duck[i:j] = np.minimum(duck[i:j], curve[: j - i])


def bus(kick, clap, hats, keys, bass, lead, leadst, fx, rise, verb, dly):
    mix = np.zeros((N, 2))
    mix += s.pan(kick, 0) * 0.9 + s.pan(clap, 0) * 0.85 + hats * 0.6
    mix += keys * duck[:, None] * 0.6 + s.pan(bass * duck, 0) * 0.85
    mix += s.pan(lead, 0) * 0.8 + leadst * 0.8 + s.pan(fx, 0) * 0.85 + s.pan(rise, 0.1) * 0.8
    mix += verb * 0.7 + dly * 0.45
    return mix


rvL, rvR = s.fdn_reverb(verb_send)
verb = np.stack([rvL, rvR], axis=1)
dly = np.stack([s.delay(delay_send, BEAT * 0.75), s.delay(delay_send, BEAT * 0.75, offset=BEAT * 0.25)], axis=1)
mix = bus(kick_b, clap_b, hatsS, keysS, bass_b, lead_b, leadS, fx_b, rise_b, verb, dly)
mix = np.stack([s.filt(mix[:, 0], 'high', 28), s.filt(mix[:, 1], 'high', 28)], axis=1)
# Breaths: an eighth before the brand and a quarter before the call to action, ramped, not gated.
tt = np.arange(N) / SR
keep_all = np.ones(N)   # the breaths' gain on everything but the riser, kept for the audibility check
for a0, a1, depth in ((SC['logo'] - BEAT / 2, SC['logo'], 0.7), (SC['end'] - BEAT, SC['end'], 0.85)):
    ramp = np.clip(np.minimum((tt - a0) / 0.02, (a1 - tt) / 0.02), 0, 1)
    keep = np.ones(N) - depth * ramp
    keep_all *= keep
    # Keep the riser through the breath: it is what the breath is for.
    riser_part = s.pan(rise_b, 0.1) * 0.8
    mix = (mix - riser_part) * keep[:, None] + riser_part
level = 0.5 / (np.sqrt((mix ** 2).mean()) * 4 + 1e-9)
mix *= level
mix, gr = s.compress(mix, thresh_db=-20, ratio=1.5, knee_db=5)
mix, lim_g = s.limiter(mix * 1.6, ceiling_db=-1.5, gain=True)
fade = np.ones(N); tail = int(0.9 * SR); fade[-tail:] = np.linspace(1, 0, tail) ** 2
mix *= fade[:, None]

# Audibility: each cue alone through the gains the mix actually applied to it (centre pan, breath,
# level, compressor, limiter, fade), against the rest of the mix with that cue taken out.
chain = 0.85 * np.cos(np.pi / 4) * keep_all * level * 10 ** (-gr / 20) * 1.6 * lim_g * fade
mono = mix.mean(axis=1)
for i, e in enumerate(EVENTS):
    cue = cue_b[i] * chain
    # The window is the cue's own length (a 30 ms tick is not judged over 120 ms).
    w_ = 0.2 if e['sound'].startswith('whoosh') else (0.03 if e['sound'] == 'tick' else 0.12)
    a = int((e['t'] - (0.15 if e['sound'].startswith('whoosh') else 0)) * SR); b = a + int(w_ * SR)
    r_cue = np.sqrt((cue[a:b] ** 2).mean() + 1e-12); r_bed = np.sqrt(((mono[a:b] - cue[a:b]) ** 2).mean() + 1e-12)
    e['cue_to_mix_db'] = round(20 * np.log10(r_cue / r_bed), 1)

OUT = HERE / 'out'
OUT.mkdir(exist_ok=True)
with wave.open(str(OUT / 'music.wav'), 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((np.clip(mix, -1, 1) * 32767).astype('<i2').tobytes())
# The two stems sync.py times cues in: effects (every picture event) and lead (the logo notes).
for name, stem in (('fx', fx_b), ('lead', lead_b)):
    with wave.open(str(OUT / f'{name}.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(stem / (np.abs(stem).max() + 1e-9), -1, 1) * 32767).astype('<i2').tobytes())
# Each cue alone (events, then logo notes), so sync.py times every one without its neighbours.
np.savez_compressed(OUT / 'cues.npz', **{f'e{i}': cue_b[i].astype(np.float32) for i in range(len(EVENTS))},
                    **{f'l{i}': h['cue'].astype(np.float32) for i, h in enumerate(LOGO_HITS)})
json.dump({'bpm': BPM, 'beat': BEAT, 'bar': BAR, 'length': LENGTH, 'scenes': SC, 'moments': MO, 'events': EVENTS, 'logo_hits': [{k: v for k, v in h.items() if k != 'cue'} for h in LOGO_HITS],
           'gain_reduction_db_max': float(gr.max())}, open(OUT / 'music.json', 'w'), indent=1)
quiet = [e for e in EVENTS if e['cue_to_mix_db'] < -12]
print(f'music: {LENGTH:.2f} s at {BPM} BPM; {len(EVENTS)} picture events; max gain reduction {gr.max():.1f} dB; '
      f'cues below -12 dB of the mix: {[(e["picture"], e["cue_to_mix_db"]) for e in quiet] or "none"}')
