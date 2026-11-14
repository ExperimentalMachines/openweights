# The shipping build on real phones, Firebase Test Lab, 2026-11-14 morning

The engine test APK built from main at 25708b1a (llama.cpp v0.8.0, ExecuTorch 1.6.0-openweights.2,
final probe code) ran GpuOffloadTest, LlamaCppEngineTest and ExecuTorchParityEval on a Galaxy S24
(SC-51E, Snapdragon 8 Gen 3, Adreno 750) and a Pixel 9 Pro (caiman, Tensor G4, Mali-G715, the
android_armv9.0_1 CPU variant the POCO also loads). The minified release (.rc build of the same
commit) was crawled by Robo on an OPPO A79 5G (OP573DL1, MediaTek Dimensity, Mali-G57) with LFM2.5
QAD-Q4_0 placed in its models folder. JSON and JUnit as written; log files are the relevant lines only.
