# The launch video

A 26-second, 1920×1080 ad for OpenWeights, built from code and real recordings: the phones' own
screen recordings, one HTML page that animates them frame by frame, and an original track
synthesised in Python. The pipeline follows the ExecuServe and PengePassportPH ads.

The finished video is [`out/openweights-ad-16x9.mp4`](out/openweights-ad-16x9.mp4) (60 fps,
H.264 and AAC, -14 LUFS), with a cover image in [`out/thumbnail.png`](out/thumbnail.png). The
rest of `out/` and all of `frames/` are generated and not committed. Upload copy is in
[`youtube.txt`](youtube.txt).

## The idea

One trip, fast. On-device AI is obviously right when there is no signal, so the ad starts by
turning Airplane mode on and then asks the phone for what a traveller needs: a phrase for the
waiter, and what is on the plate. Both answers come from models on the phone, with the
airplane icon in the status bar on screen the whole time. Then the brand, then the one thing no
other on-device app does before you download: a verdict per file for this phone. Then free,
open source, no account, no analytics, and the install.

The first version (59 s: a lease read on the NPU, the "Leaves the device" switches, a page built
behind an approval card) was dropped whole: it overstayed, the use case did not land, the music
was not in sync, and the generated page and its permission card were not worth showing.

The mood is quick and certain: white, ink and the one lime, type that is already on screen on
its beat (it settles from 2.5% larger in 0.16 s rather than flying in), cuts on the bar line,
nothing over four seconds. The music matches: a dry hook, the bass on the first answer, the bed
thinned under the photo and rebuilt on its answer, a modest accent on the logo, a lighter groove
with the logo's motif under the fit verdicts, a build under the two lines, a breath, and the
biggest arrival on the call to action.

## Build it

```sh
cd marketing/ad
npm install
./build.sh      # out/openweights-ad-16x9.mp4 and out/thumbnail.png
uv run sync.py  # every sound against the moment it marks
node checkpic.cjs  # every sound against what is on screen
```

Needs Node 22+, [uv](https://docs.astral.sh/uv/) and ffmpeg. About 2 minutes on an M-series Mac.
A rebuild rewrites the committed video and thumbnail; commit them again with the change.

## How it fits together

| File | What it does |
| --- | --- |
| `ad.html` | The whole ad as one page. `window.renderAt(t)` draws the frame at `t` seconds, waits for each footage frame to decode and never reads the clock, so every frame renders the same every time. It reads every scene and moment from `music.json` (through `assets/data.js`), so picture and music share one timeline. `lens()` moves a phone in on a point of its recording; `frameCrop()` magnifies a region of the same frame (the airplane icon, the photo answer). The screen is never redrawn. |
| `music.py` | The arrangement: 128 BPM in A major, one chord a bar, 26.1 s. Every picture event is placed through `event()`, which also writes it to the manifest in `out/music.json` with its sound and how far it stands above the rest of the mix. |
| `synth.py` | The instruments and the mix bus: band-limited saws, a filter-envelope pluck, an FM electric piano, kick, clap, hats with a choke, risers, whooshes, impacts, an FDN reverb, a compressor and a look-ahead limiter that never shifts the audio. No samples, nothing licensed, seeded. |
| `sync.py` | Times every cue (16 picture events and the 6 sonic-logo notes) from its own isolated buffer and again inside its stem (`out/fx.wav`, `out/lead.wav`): a whoosh by its peak (within 30 ms), everything else by its onset (within 12 ms). |
| `checkpic.cjs` | For every cue, renders the frame 50 ms later and checks that its picture is there: the element visible through its ancestors, 80% in frame and not under the wipe; for a cut, over 35% of what is drawn changes; for a logo note, its bar has grown past half. |
| `assets/clips/*.mp4` | The recordings, trimmed and set to 30 fps. Each has a JSON beside it: device, build, model, settings, the prompt, the start of the answer, and the moments read from the frames. |
| `clips.sh`, `data.cjs`, `qr.py` | Clips to frames; the clips' frame counts, moments and the music's timeline bundled into `assets/data.js` (refusing recordings that no longer fit); the end card's QR code to the Google Play listing. |
| `render.cjs`, `build.sh` | Playwright screenshots of `renderAt` in parallel pages (fails on any page error); 120 fps blended pairwise to 60 fps (motion blur), a two-pass loudnorm to -14 LUFS, H.264 and AAC. |
| `stills.cjs`, `sheet.sh` | Review: stills at chosen times (`node stills.cjs 3 12 21`) and contact sheets. |

## Scenes

| Scene | Seconds | What it shows |
| --- | --- | --- |
| Airplane mode | 0 to 1.9 | Quick Settings, close on the Internet and Airplane tiles; Airplane mode tapped on. "Airplane mode." |
| The phrase | 1.9 to 7.5 | The question sent; the airplane icon from the status bar magnified beside the phone. "Still answers." as the answer starts; "Qwen3-4B · on this phone"; the phrase quoted word for word: "¿Puedo ver la cuenta, por favor?" |
| The photo | 7.5 to 11.25 | "Show it a photo." A photo of paella attached and "What is this?" sent; the wait cut and labelled ("≈11.1 s wait cut"); "Still offline." as the answer starts; "Qwen2.5-VL-3B · on this phone"; "This is a dish of paella" quoted, beside a crop of the screen with the photo, the question and the first two lines. |
| Brand | 11.25 to 13.1 | The mark's bars build on the sonic logo; OpenWeights. |
| Fit | 13.1 to 16.9 | "Download a model before you go." The POCO, on a model's MediaTek NPU builds. "It shows what fits." "Estimated for this phone"; 2k: Runs comfortably, 4k: Tight, 8k: Will not run, each beside its card. |
| Values | 16.9 to 20.6 | "Free and open source." "No account. No analytics." |
| End card | 20.6 to 26.1 | Mark and wordmark, "Open-weight AI, on your phone.", "Get it on Google Play", "Free · Open source on GitHub", a QR code to the listing, and the fine print. |

## Where each claim comes from

- Every phone screen is a recording made with `screenrecord` on the phone itself, played at its
  recorded speed with cuts: a Snapdragon 8 Elite (SM8750) reference phone on Qualcomm Device
  Cloud and the POCO X8 Pro Max (Dimensity 9500). The build is `e2da0095`, the commit released
  on Google Play as version code 671, installed as the `.rc` package (same code, debug-signed)
  so the store install was not touched.
- Offline: the Snapdragon's adb runs over USB, so Airplane mode cut it off from Wi-Fi without
  cutting the recording. It has no SIM. `settings get global airplane_mode_on` read 1 after the
  take, and the airplane icon is in the status bar through both answers. Every tool was off.
- The phrase: Qwen3-4B-Instruct-2507 Q4_K_M (GGUF) on the CPU (`airplane-bill.json`). The
  quote is the answer's own first line. The cut at 1.9 s skips closing Quick Settings and
  typing (about 3.9 s).
- The photo: Qwen2.5-VL-3B-Instruct Q4_K_M with its projector, image detail Fast (`paella.json`).
  The photo is [Paella_de_fruit_de_mer.jpg](https://commons.wikimedia.org/wiki/File:Paella_de_fruit_de_mer.jpg),
  Wikimedia Commons, CC0. The phone shows "Reading the prompt" for about 11 s; the ad cuts it,
  and the label is computed from the same offsets that make the cut. The answer goes on to guess
  at ingredients; the crop ends before those lines, and the end card says models can make
  mistakes.
- The verdicts: Discover's own on the POCO (`poco-fit.json`): 2k "Runs comfortably" (needs
  4.00 GB), 4k "Runs, but tight: other apps may be closed" (5.37 GB), 8k "Will not run at this
  context length" (8.13 GB). For compiled (`.pte`) files they are a size-based estimate, as the
  README says, hence "Estimated for this phone".
- "Free and open source", "No account. No analytics.": the Apache-2.0 repository, the README
  and the privacy policy. Open source describes the app, not every model it downloads.
- The only change to any recording: the POCO's status bar (its owner's notification icons) is
  covered by a blank strip.

## Rules for anything made here

- Real or nothing: re-record rather than edit a claim, and keep each clip's JSON with it.
- Every sound marks something on screen, and both checks pass before a render ships.
- Lime is a fill carrying ink, never text on white; no other company's logos (Google Play,
  Hugging Face, Qualcomm and MediaTek are named, never drawn).
- Nothing counts up and nothing bounces except the mark's bars; a measurement lands settled.
