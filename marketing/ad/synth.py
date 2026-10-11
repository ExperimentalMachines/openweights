# The instruments and the mix bus for the ad's track: no samples, nothing licensed, seeded.
# Kept apart from the arrangement (music.py) so the sound can be tuned without moving the timing.
import numpy as np
from scipy.signal import butter, sosfilt, lfilter

SR = 48000
rng = np.random.default_rng(20261012)


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def filt(sig, kind, f, order=2):
    w = np.clip(np.array(f, dtype=float) / (SR / 2), 1e-4, 0.999)
    return sosfilt(butter(order, w, btype=kind, output='sos'), sig)


def fade_ends(sig, ms_in=1.0, ms_out=6.0):
    """Every sound starts and ends at zero, so nothing clicks."""
    a, b = int(ms_in * SR / 1000), int(ms_out * SR / 1000)
    out = sig.copy()
    if a:
        out[:a] *= np.linspace(0, 1, a)
    if b:
        out[-b:] *= np.linspace(1, 0, b)
    return out


def env_ar(n, attack, decay):
    t = np.arange(n) / SR
    return np.minimum(1, t / max(attack, 1e-6)) * np.exp(-np.maximum(0, t - attack) / max(decay, 1e-6))


def polyblep_saw(freq, n, phase=0.0):
    """A band-limited sawtooth: the naive ramp corrected around each wrap, so it does not alias."""
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    dt = f / SR
    p = (np.cumsum(dt) + phase) % 1.0
    out = 2 * p - 1
    # PolyBLEP correction near the discontinuity.
    m1 = p < dt
    x = p[m1] / dt[m1]
    out[m1] -= x + x - x * x - 1
    m2 = p > 1 - dt
    x = (p[m2] - 1) / dt[m2]
    out[m2] -= x * x + x + x + 1
    return out


def tpt_lowpass(sig, cutoff, res=0.0):
    """A topology-preserving state-variable low-pass whose cutoff may move per sample."""
    c = np.broadcast_to(np.asarray(cutoff, dtype=float), sig.shape)
    g = np.tan(np.pi * np.clip(c, 20, SR * 0.45) / SR)
    k = 2 - 2 * np.clip(res, 0, 0.95)
    a1 = 1 / (1 + g * (g + k))
    out = np.empty_like(sig)
    ic1 = ic2 = 0.0
    for i in range(len(sig)):
        v3 = sig[i] - ic2
        v1 = a1[i] * ic1 + g[i] * a1[i] * v3
        v2 = ic2 + g[i] * v1
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        out[i] = v2
    return out


def onepole(sig, cutoff):
    """A one-pole low-pass with a cutoff that may move per sample: continuous sweeps, no seams."""
    c = np.broadcast_to(np.asarray(cutoff, dtype=float), sig.shape)
    a = np.exp(-2 * np.pi * np.clip(c, 20, SR * 0.45) / SR)
    out = np.empty_like(sig)
    y = 0.0
    for i in range(len(sig)):
        y = (1 - a[i]) * sig[i] + a[i] * y
        out[i] = y
    return out


# --- drums ----------------------------------------------------------------------------

def kick():
    n = int(0.26 * SR)
    t = np.arange(n) / SR
    f = 48 + 110 * np.exp(-t * 55)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.085)
    # A little second harmonic so the kick survives a phone speaker.
    body += 0.18 * np.sin(4 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.04)
    click = filt(rng.standard_normal(n), 'band', (2500, 8000)) * np.exp(-t / 0.0025) * 0.3
    return fade_ends(np.tanh((body + click) * 1.15) * 0.95)


def clap():
    """Four separate bursts of fresh noise, the last one longer: a hand clap, not a gated hiss."""
    n = int(0.36 * SR)
    out = np.zeros(n)
    for k, off in enumerate((0.0, 0.0085, 0.0175, 0.027)):
        i = int(off * SR)
        m = n - i
        tt = np.arange(m) / SR
        burst = filt(rng.standard_normal(m), 'band', (1000 + 150 * k, 6500)) * np.exp(-tt / (0.006 if k < 3 else 0.11))
        out[i:] += burst * (0.8 if k < 3 else 1.0)
    return fade_ends(out * 0.5)


def hat(open_=False, vel=1.0, choke=None):
    """Band-passed noise with a metallic resonance, no aliasing oscillators. An open hat is cut
    off with a 5 ms fade at `choke` seconds, where the next closed hat lands, as a real pair would."""
    n = int((choke if choke else (0.19 if open_ else 0.035)) * SR)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    body = filt(noise, 'band', (6500, 14000)) + 0.35 * filt(noise, 'band', (8800, 9600))
    return fade_ends(body * np.exp(-t / (0.07 if open_ else 0.009)) * 0.22 * vel, 0.5, 5 if choke else 4)


def tick():
    """A UI tap: short and bright, felt as a confirmation."""
    n = int(0.03 * SR)
    t = np.arange(n) / SR
    return fade_ends((np.sin(2 * np.pi * 2400 * t) * 0.6 + filt(rng.standard_normal(n), 'high', 4500) * 0.4) * np.exp(-t / 0.004) * 0.4)


def chime(midi, dur=0.7):
    """The answer arriving: a short FM tone, distinct from every musical part."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = hz(midi)
    mod = np.sin(2 * np.pi * f * 2.0 * t) * 1.2 * np.exp(-t / 0.08)
    return fade_ends(np.sin(2 * np.pi * f * t + mod) * env_ar(n, 0.002, dur * 0.35) * 0.35)


# --- tonal ----------------------------------------------------------------------------

def epiano(midi, dur, vel=1.0):
    """An FM electric piano: brightness follows velocity, a slightly inharmonic tine on the attack."""
    rel = 0.16
    n = int((dur + rel) * SR)
    t = np.arange(n) / SR
    f = hz(midi) * (1 + rng.normal(0, 0.0008))
    idx = (0.8 + 1.6 * vel) * np.exp(-t / 0.16) + 0.12
    tone = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * t))
    tine = np.sin(2 * np.pi * f * 3.01 * t) * np.exp(-t / 0.03) * 0.25 * vel
    env = np.minimum(1, t / 0.002) * np.exp(-t / 0.9) * np.clip((dur + rel - t) / rel, 0, 1)
    return fade_ends((tone + tine) * env * 0.3 * vel)


def chord(notes, dur, vel=1.0):
    out = sum(epiano(m, dur, vel) for m in notes) / max(1, len(notes)) * 1.6
    return filt(out, 'high', 160)


def pluck(midi, dur, bright=1.0, vel=1.0):
    """Two band-limited saws through a closing low-pass whose floor follows the pitch."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = hz(midi)
    raw = polyblep_saw(f * 2 ** (-0.05 / 12), n) + polyblep_saw(f * 2 ** (0.05 / 12), n, 0.37)
    floor = max(500, 1.2 * f)
    cut = np.minimum(16000, floor + 4 * f * bright * np.exp(-t / 0.10))
    env = np.minimum(1, t / 0.002) * np.exp(-t / (dur * 0.45))
    return fade_ends(tpt_lowpass(raw * 0.5, cut, res=0.25) * env * 0.55 * vel)


def bass(midi, dur, vel=1.0):
    """A mono sub with harmonics at 120 to 300 Hz, so it carries on a small speaker."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = hz(midi)
    sub = np.sin(2 * np.pi * f * t)
    grit = tpt_lowpass(polyblep_saw(f, n), 260 + 700 * np.exp(-t / 0.06))
    sig = np.tanh((sub * 0.85 + grit * 0.5) * 1.6)
    env = np.minimum(1, t / 0.004) * np.clip((dur - t) / 0.02, 0, 1)
    return fade_ends(sig * env * 0.5 * vel)


def pad(notes, dur, cutoff=1800, attack=0.5):
    n = int(dur * SR)
    out = np.zeros(n)
    for m in notes:
        for d in (-0.08, 0.0, 0.08):
            out += polyblep_saw(hz(m) * 2 ** (d / 12), n, rng.random())
    out = filt(out / (len(notes) * 3), 'low', cutoff)
    t = np.arange(n) / SR
    env = np.minimum(1, t / attack) * np.clip((dur - t) / min(0.5, dur * 0.3), 0, 1)
    return fade_ends(filt(out, 'high', 140) * env * 0.32)


# --- transitions -----------------------------------------------------------------------

def riser(dur, top=9000):
    """Noise and a rising tone through one continuously sweeping filter: no seams."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    k = (t / dur) ** 2
    noise = rng.standard_normal(n)
    sweep = onepole(noise, 300 + top * k) - onepole(noise, 150 + top * 0.4 * k)
    tone = np.sin(2 * np.pi * np.cumsum(220 + 1500 * k) / SR) * 0.12
    return fade_ends((sweep * 0.6 + tone) * k * 0.55, 5, 3)


def whoosh(lead=0.36, tail=0.22):
    """Air across a cut. Its loudest instant is the cut itself: `lead` before, `tail` after."""
    n = int((lead + tail) * SR)
    t = np.arange(n) / SR
    up = np.clip(t / lead, 0, 1) ** 2.2
    down = np.exp(-np.maximum(0, t - lead) / (tail / 3))
    e = np.where(t < lead, up, down)
    noise = rng.standard_normal(n)
    cut = 900 + 6000 * e
    band = onepole(noise, cut) - onepole(noise, cut * 0.35)
    return fade_ends(band * e * 0.32, 3, 4)


def impact(size=1.0, length=1.4):
    n = int(length * SR)
    t = np.arange(n) / SR
    sub = np.sin(2 * np.pi * np.cumsum(40 + 70 * np.exp(-t * 10)) / SR) * np.exp(-t / (0.35 * length))
    burst = filt(rng.standard_normal(n), 'band', (1500, 11000)) * np.exp(-t / 0.12) * 0.25
    return fade_ends(np.tanh((sub + burst) * 1.15) * 0.8 * size, 1, 20)


# --- space and the mix bus -------------------------------------------------------------

def allpass(sig, delay_ms, g=0.6):
    d = int(delay_ms * SR / 1000)
    b = np.zeros(d + 1); b[0] = -g; b[d] = 1
    a = np.zeros(d + 1); a[0] = 1; a[d] = -g
    return lfilter(b, a, sig)


def fdn_reverb(sig, decay=0.85, damp=5500, predelay=0.015):
    """Two input allpasses for diffusion, then an 8-line Hadamard FDN with damping."""
    x = filt(sig, 'high', 250)
    x = allpass(allpass(x, 4.7, 0.65), 11.3, 0.6)
    lens = (np.array([1031, 1327, 1523, 1801, 2063, 2339, 2617, 2887]) * 0.9).astype(int)
    g = 10 ** (-3 * lens / (decay * SR))
    H = np.array([[1.0]])
    while H.shape[0] < 8:
        H = np.block([[H, H], [H, -H]])
    H /= np.sqrt(8)
    x = np.concatenate([np.zeros(int(predelay * SR)), x])[: len(sig)]
    n = len(x)
    bufs = [np.zeros(L) for L in lens]
    idx = np.zeros(8, dtype=int)
    lp = np.zeros(8)
    a = np.exp(-2 * np.pi * damp / SR)
    inj = np.array([1, -1, 1, -1, 1, -1, 1, -1]) * 0.35
    outL = np.zeros(n); outR = np.zeros(n)
    block = int(lens.min())
    for start in range(0, n, block):
        end = min(n, start + block); m = end - start
        outs = np.stack([np.take(bufs[k], (idx[k] + np.arange(m)) % lens[k]) for k in range(8)])
        for k in range(8):
            y, zf = lfilter([1 - a], [1, -a], outs[k], zi=[lp[k] * a])
            lp[k] = y[-1]
            outs[k] = y
        fb = (H @ outs) * g[:, None] + x[start:end][None, :] * inj[:, None]
        for k in range(8):
            pos = (idx[k] + np.arange(m)) % lens[k]
            bufs[k][pos] = fb[k]
            idx[k] = (idx[k] + m) % lens[k]
        outL[start:end] = outs[0] - outs[2] + outs[4] - outs[6]
        outR[start:end] = outs[1] - outs[3] + outs[5] - outs[7]
    return outL * 0.3, outR * 0.3


def delay(sig, t, fb=0.32, lp=4500, offset=0.0):
    """A feedback delay with a zero-filled start (no wrap-around); `offset` delays one channel."""
    d = int(t * SR)
    o = int(offset * SR)
    out = np.zeros(len(sig))
    tap = np.concatenate([np.zeros(o), sig])[: len(sig)]
    gain = 1.0
    for _ in range(6):
        tap = np.concatenate([np.zeros(d), tap])[: len(sig)]
        gain *= fb
        out += filt(tap, 'low', lp) * gain
    return out


def pan(sig, p):
    """Constant-power pan, p in -1..1."""
    a = (p + 1) * np.pi / 4
    return np.stack([sig * np.cos(a), sig * np.sin(a)], axis=1)


def compress(st, thresh_db=-18, ratio=1.5, knee_db=5, attack=0.025, release=0.15):
    """A stereo-linked soft-knee compressor with a one-pole power detector (never negative)."""
    p = (st ** 2).mean(axis=1)
    ca = np.exp(-1 / (0.01 * SR))
    pw = lfilter([1 - ca], [1, -ca], p)
    db = 10 * np.log10(np.maximum(pw, 1e-12))
    over = db - thresh_db
    gr = np.where(over <= -knee_db / 2, 0,
                  np.where(over >= knee_db / 2, over * (1 - 1 / ratio),
                           (1 - 1 / ratio) * (over + knee_db / 2) ** 2 / (2 * knee_db)))
    aa, ar = np.exp(-1 / (attack * SR)), np.exp(-1 / (release * SR))
    sm = np.empty_like(gr); s_ = 0.0
    for i in range(len(gr)):
        c = aa if gr[i] > s_ else ar
        s_ = c * s_ + (1 - c) * gr[i]
        sm[i] = s_
    return st * (10 ** (-sm / 20))[:, None], sm


def limiter(st, ceiling_db=-1.5, lookahead=0.003, release=0.08, gain=False):
    """A stereo-linked look-ahead limiter. Offline, the look-ahead reads ahead in the file, so the
    gain is already down when a peak arrives and the audio is never shifted against the picture."""
    from scipy.ndimage import minimum_filter1d
    c = 10 ** (ceiling_db / 20) * 0.94   # a margin for inter-sample peaks after encoding
    la = int(lookahead * SR)
    need = np.minimum(1, c / np.maximum(np.abs(st).max(axis=1), 1e-9))
    g = minimum_filter1d(need, size=2 * la + 1)
    ar = np.exp(-1 / (release * SR))
    out = np.empty_like(g); s_ = 1.0
    for i in range(len(g)):
        s_ = g[i] if g[i] < s_ else ar * s_ + (1 - ar) * g[i]
        out[i] = s_
    return (st * out[:, None], out) if gain else st * out[:, None]
