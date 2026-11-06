# The signed final build on a real phone, 2026-11-06

Written up in `docs/research/llama-cpp-v080.md`, "On a real phone".

SM8650, Android 14. Signed release version 731, SHA-256 `85a75abab964ae86...`, installed from the file built at `317d2444`.
Models were fetched on the phone and checked against the hub's hashes; one GGUF download arrived damaged, was caught and was fetched again.

| Files | What |
|---|---|
| `working/` | The signed release through its real UI, search working |
| `dead/` | The same, with this app's network blocked by `iptables`, so every engine fails and the system's network still reads as up |
| `inst-engine-final.txt`, `inst-app-final.txt` | Device tests. These run the debug build at the same app code as 731 (`git diff 5a91605f..317d2444 -- app core` is empty) |
| `run-output.txt` | What the UI runs printed, including the crash buffer's count after each model. The buffer was not read after the suite, since the reservation ended first |
| `final-against-earlier.txt`, `final-against-earlier-questions.txt` | The 160 questions against `qdc-sm8650-shipped-new-*`, one SM8650 but not necessarily the same unit |

The rows themselves are `tools/eval/results/decisions/qdc-sm8650-shipped-final-*`. These also use the debug build, with the app's prompts and tools. LFM3's are partial: 13 rows before the reservation ended.

Not captured here: what the search returned on each UI row, so a figure in a reply cannot be traced to a snippet from these files.
