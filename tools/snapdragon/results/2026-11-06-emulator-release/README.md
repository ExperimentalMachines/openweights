# The signed release on a 16 KB emulator, 2026-11-06

Written up in `docs/research/llama-cpp-v080.md`, "When search fails, and the last check of the release".

`es-16k`: Android 16, 16 KB pages, 6 cores, no window. Stands in for a phone on behaviour, not on speed. Every row went through the app's real UI.

| Files | Build | Search |
|---|---|---|
| `emu-dns-lost/` | vc726 | failing by accident, the emulator had lost DNS; the compiled model only |
| `search-working-vc726.txt`, `emu-regress/` | vc726 | working |
| `search-dead-vc726.txt`, `emu-dead-before/` | vc726 | failing, DNS pointed at 127.0.0.1 |
| `fixed-release-search-dead-then-working.txt`, `emu-dead-after/`, `emu-regress-after/` | the fix | failing, then working |

`emu-dead-after/tail-*` are the compiled model's Tokyo row again and the turn after a replaced reply.

Not results, and left out of every count:

- `emu-regress/LFM2.5-1.2B-Instruct-Q4_K_M-1.txt`: only "What is the" was typed, under a Gradle run on the same Mac.
- `emu-regress/LFM3-1.7B-Q4_K_M-1.txt`: never sent. Its rows 2 and 3 were still generating at the driver's 300 s wait; `emu-regress-after/` waited 600 s.
- Any row whose summary line reads "NOT TYPED" or is a summary of a tool approval, reasoning or a screen label.
- `emu-dead-before/` rows 2 and 3 of the GGUF and LFM3: the rate question was not sent, stayed in the box, and went out joined to Bitcoin.

Recorded by hand, with nothing here to show for it: the compiled model's SHA-256 matched the hub's `x-linked-etag` (`99517d410ee813ff...`), an earlier local
copy did not and was not used; QDC refused `sa945510`; lint, detekt and 2,197 unit tests with 10 skipped passed at the commit; the engine's
`ReasoningBudgetOnDeviceTest` passed 6 of 6 on this emulator; the mutation checks and the four Codex reviews.
