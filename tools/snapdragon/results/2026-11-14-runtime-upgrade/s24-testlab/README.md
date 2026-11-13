# Galaxy S24 (SC-51E, Snapdragon 8 Gen 3), Firebase Test Lab, 2026-11-14 morning

Each run landed on whichever unit was free. `sp080*`/`sp100*` are SpeedProbe with llama.cpp v0.8.0
and v1.0.0 (test APKs that differ only in llama.cpp), three units each. `context-depth.txt` is
ContextLengthBenchmark.measuresWhatAFullWindowCosts, two units each (cl080 in micromarc-research,
cl100 in a second project about 40 minutes later; same device pool). `gpu-offload.txt` is
GpuOffloadTest's load and reply lines on both versions. The two JSONs and `final-build-junit.xml`
are ExecuTorchParityEval and the test outcomes of the eaef5b18 build (llama.cpp v1.0.0, ExecuTorch
1.6.0-openweights.2), before llama.cpp went back to v0.8.0 (5 passed, 2 skipped for a missing
Qwen file). Only the relevant log lines are kept; no logcat is committed.
