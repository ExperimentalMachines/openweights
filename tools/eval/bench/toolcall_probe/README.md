# Tool-call probe

The scripts behind `docs/research/executorch-tool-calling.md`: first-token probability
of `<|tool_call_start|>` for one prompt across artifacts, on a Mac.

- `prompts.py` rebuilds the decision suite's prompt bytes from a results file's header
  (system text and tool definition checked against the recorded SHA-1s) and picks the
  sample rows; `naming.py` is `NamedSubject` in Python, the note the app attaches.
- `trailer_run.py` renders the rows through Liquid AI's template with the note and asks a
  llama-server (`--port 8098`, the app's own llama.cpp build, `--jinja --temp 0`) for the
  GGUF's top tokens; it writes `sample_prompts_trailer_hf.json` for the other two runners.
- `pte_run.py <prompts.json> <out.json> <file.pte>` runs a `.pte` through the ExecuTorch
  Python runner; `ref_run.py <prompts.json> <out.json>` runs transformers in fp32.
- `piece_diff.py` compares whole-prompt tokenization with the engine's warm pieces.
- `export_*.sh` are the bisection exports; `quantize_skip.patch` is the env-gated filter
  (`OW_SKIP_OUTPUT`, `OW_SKIP_REGEX`) applied to the venv's `quantize.py` for two of them.

Environment: `~/ow-models/etexport/.venv` (executorch 1.4.0, transformers, tokenizers),
`~/ow-models/etexport/hf/LFM2.5-1.2B-Instruct`. Results: `tools/eval/results/toolcall-probe/`.
