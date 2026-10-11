# The launch video

A 59-second, 1920×1080 ad for OpenWeights, built from code and real recordings: the phones' own
screen recordings, one HTML page that animates them frame by frame, and an original track
synthesised in Python. The pipeline follows the ExecuServe and PengePassportPH ads.

The finished video is [`out/openweights-ad-16x9.mp4`](out/openweights-ad-16x9.mp4) (60 fps,
H.264 and AAC, -14 LUFS), with a cover image in [`out/thumbnail.png`](out/thumbnail.png). The
rest of `out/` and all of `frames/` are generated and not committed. Upload copy is in
[`youtube.txt`](youtube.txt).

## The idea

The design notes call OpenWeights "an instrument you own: you chose the file, it runs on your
silicon, and you can watch how fast". The ad is that sentence in five proofs, chosen because no
other on-device app shows them: a private document read on the phone's NPU with the runtime's
own numbers; the tools that go online named and switched off; Hugging Face as the model list,
with a verdict on each file for this phone before anything downloads; and a page the model
writes, saves only after an OK, and opens in the app. Chat for its own sake, voice, memory,
branching and the benchmarks are left out on purpose.

The mood is confident and precise rather than magical: white, ink and the one lime, big type on
the beat, phones that come in close enough to read. The music matches: a sparse question for the
hook, the groove on the logo, a lower groove under the proof, a breakdown with one soft click per
switch, the peak on the build, and the sonic logo (three plucks as long as the mark's bars are
long) at both ends.

## Build it

```sh
cd marketing/ad
npm install
./build.sh   # out/openweights-ad-16x9.mp4 and out/thumbnail.png
```

Needs Node 22+, [uv](https://docs.astral.sh/uv/) and ffmpeg. About 6 minutes on an M-series Mac.
A rebuild rewrites the committed video and thumbnail; commit them again with the change.

## How it fits together

| File | What it does |
| --- | --- |
| `ad.html` | The whole ad as one page. `window.renderAt(t)` draws the frame at `t` seconds, waits for each footage frame to decode and never reads the clock, so every frame renders the same every time. Scenes cut on the music's bar lines: `bar(n, beat)` at 120 BPM, so a bar is two seconds. `lens()` moves a phone in on a point of its recording, so the app's own text is readable at a phone's feed width; the screen is never redrawn. |
| `music.py` | The track: 120 BPM bright electro in E major (E, B, C#m, A), 29 bars and a tail, synthesised with numpy and scipy (no samples, nothing licensed). Seeded. It reads the same moments from the clips' JSON as the page. |
| `assets/clips/*.mp4` | The recordings, trimmed and set to 30 fps. Each has a JSON beside it: device, build, model, settings, the prompt where there is one, and the moments read from the frames. |
| `clips.sh` | Turns the clips into frames under `frames/clips/`. |
| `data.cjs` | Bundles the clips' frame counts and moments into `assets/data.js`, and refuses recordings that no longer fit their scenes. |
| `qr.py` | The end card's QR code: the Google Play listing. |
| `render.cjs` | Screenshots `renderAt` frame by frame in parallel Playwright pages; fails on any page error. |
| `build.sh` | Clips, data, QR, music, a 120 fps render, each pair of frames blended into one at 60 fps (motion blur), music set to -14 LUFS, H.264 and AAC. |
| `stills.cjs`, `sheet.sh` | Review: stills at chosen times (`node stills.cjs 3 12 27`) and 2×2 contact sheets. |

## Scenes

| Scene | Bars | Seconds | What it shows |
| --- | --- | --- | --- |
| Hook | 0 to 3 | 0 to 6 | An example lease (the prompt's own text). "Some things shouldn't leave your phone." "So bring the AI to them." |
| Logo | 3 to 5 | 6 to 10 | The mark's bars build on the sonic logo; OpenWeights; "Open-weight AI that runs on your phone."; Free, Open source, No account. |
| The lease, on the NPU | 5 to 12 | 10 to 24 | The Snapdragon reads the lease on its NPU and answers, at recorded speed. The runtime's numbers for that reply: 0.23 s to read its 398 tokens, 31 tokens a second writing. The phone comes in close; the excerpt ends as "Three Biggest Catches" begins. |
| Leaves the device | 12 to 16 | 24 to 32 | The Tools screen's "Leaves the device" switches turned off one by one. "The tools that go online say so. And they switch off." "No account. No analytics." "Search and downloads still use the internet." |
| Fit | 16 to 20 | 32 to 40 | A search on the Snapdragon; then the POCO opens a model's MediaTek NPU builds: runs comfortably at 2k, tight at 4k, will not run at 8k. "See what fits before you download." |
| Build | 20 to 25 | 40 to 50 | "Ask for a page." The model writes it; "About a minute later" the app asks to save it ("It waits for your OK."); Run; the page opens in the Canvas ("Then it opens. Right here."). |
| End card | 25 to end | 50 to 58.9 | Mark and wordmark, "Get it on Google Play", "Free and open source", the source, a QR code to the listing, and the fine print. |

## Where each claim comes from

- Every phone screen is a recording made with `screenrecord` on the phone itself, played at its
  recorded speed: a Snapdragon 8 Elite (SM8750) on Qualcomm Device Cloud and the POCO X8 Pro Max
  (Dimensity 9500). The build is `e2da0095`, the commit released on Google Play as version code
  671, installed as the `.rc` package (same code, debug-signed) so the store install was not touched.
- The lease figures: the runtime's own log for that reply (`npu-lease.json`): 398 prompt tokens
  in 0.227 s, 595 tokens at 31.1 tokens a second. The app's footer for the reply reads
  "1753→31 tok/s 19.3s". Thinking was off and the internet tools were off for that take.
- The answer is the model's and is not checked: it gives the lease a 2026 start the lease never
  states, writes "$2 months of rent", and after the excerpt ends it miscalculates a late fee.
  The close-up says the answer is unedited and unchecked, and the end card says models can make mistakes.
- The switches: the Tools screen's own section and labels. "No account. No analytics." and
  "Search and downloads still use the internet": the README and the privacy policy.
- The verdicts: Discover's own, on that phone. For compiled (`.pte`) files they are a size-based
  estimate, as the README says; the "≈9 tok/s decode" on those cards is the app's estimate too.
- The build: Qwen3-1.7B Q8_0 (GGUF) on the Snapdragon's CPU, a shared folder, the file and
  Canvas tools on and the internet tools off (`canvas.json`). The page has a header, a menu with
  prices and a contact section; it left out the opening hours the prompt asked for. The minute
  between the ask and the request to save is cut, and labelled.
- The only change to any recording: the POCO's status bar (its owner's notification icons) is
  covered by a blank strip.

## Rules for anything made here

- Real or nothing: re-record rather than edit a claim, and keep each clip's JSON with it.
- Lime is a fill carrying ink, never text on white; no other company's logos (Google Play,
  Hugging Face, Qualcomm and MediaTek are named, never drawn).
- Nothing counts up and nothing bounces except the mark's bars; a measurement lands settled.
