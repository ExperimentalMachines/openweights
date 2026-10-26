# Snapdragon prefill/decode matrix

Every pairing of CPU, Adreno GPU (OpenCL) and Hexagon NPU for the two halves of a turn, in one
llama.cpp build, over real conversations. Write-up and results:
[`docs/research/snapdragon-pd-matrix.md`](../../docs/research/snapdragon-pd-matrix.md).

## Build (x86_64 Linux with Docker; the Hetzner box was used)

The vendored llama.cpp (`core/engine/src/main/cpp/llama.cpp`), copied without its `.git` and
top-level `build*/` directories (the pattern `build*` also drops `cmake/build-info.cmake`):

```sh
docker run --rm -u $(id -u):$(id -g) --platform linux/amd64 -v $PWD:/workspace \
  ghcr.io/snapdragon-toolchain/arm64-android:v0.7 bash -c '
    cd /workspace/llama.cpp && cp docs/backend/snapdragon/CMakeUserPresets.json . &&
    cmake --preset arm64-android-snapdragon-release -B build-snapdragon &&
    cmake --build build-snapdragon -j 8 && cmake --install build-snapdragon --prefix /workspace/pkg &&
    CXX=$(ls /opt/android-ndk-*/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android31-clang++ | head -1) &&
    $CXX -O2 -std=c++17 -I/workspace/pkg/include /workspace/tools/pd_bench.cpp \
      -L/workspace/pkg/lib -lllama -lggml -lggml-base -o /workspace/pkg/bin/pd_bench &&
    cp $(find /opt/android-ndk-*/toolchains/llvm/prebuilt/linux-x86_64/sysroot -name libc++_shared.so | grep aarch64 | head -1) /workspace/pkg/lib/'
```

Push `pkg/lib/*.so` and `pkg/bin/pd_bench` to `/data/local/tmp/sd/pkg/`, the models to
`/data/local/tmp/sd/gguf/` (the phone downloads them from Hugging Face faster than `adb push`
through the QDC tunnel), and `conversations.jsonl` and `run_matrix.sh` to `/data/local/tmp/sd/`.

## Run

```sh
# on the phone, detached: label, seed, then models (two Hexagon sessions above 2 GB)
BIN=pd_bench COOL=55 COOL_MAX=300 nohup sh run_matrix.sh r4 1 \
  Llama-3.2-1B-Instruct-Q4_0.gguf Qwen3-4B-Q4_0.gguf:HTP0,HTP1 > run-r4.out 2>&1 &
# on the Mac
python3 analyze_pd.py results-r4.jsonl --conversations conversations.jsonl
python3 compare_runs.py results-r4.jsonl results-r5.jsonl   # a second pass against the first
```

`pull_conversations.py` rebuilds `conversations.jsonl` from the Hugging Face datasets server.

At the dataset's own reply lengths, `--gen-ref` makes each turn decode as many tokens as its
reply (capped by `--gen`) and logs every decode step's time; `run_matrix.sh` insists on all nine
cells, so call the binary directly for a subset:

```sh
GGML_HEXAGON_NDEV=1 ./pkg/bin/pd_bench -m gguf/Qwen3-0.6B-Q4_0.gguf -c conversations.jsonl \
  -o results-r9.jsonl --label r9 --cells gpu:cpu,npu:npu,cpu:cpu,npu:cpu --npu-devices HTP0 \
  --gen 2048 --gen-ref --ctx 8192 --threads 6 --max-turns 6 --cool 55 --cool-max 120
python3 analyze_ref.py results-r9.jsonl --first results-r4.jsonl=Qwen3-0.6B-Q4_0   # with the 64-token projection
```

`analyze_ref.py` reports each cell's gate, the common and pairwise cohorts, CPU and GPU caps,
decode steps over 500 ms (stalls), the rate by step range over the same turns, and the
constant-rate projection from a 64-token run. `--first` takes a file, optionally limited to some
models with `=name,name`, and refuses a turn that two baseline files both hold.

## Traps found on the QDC phone

- **It throttles within a minute.** Thermal status 3 and the GPU capped at power level 8 of 17
  (578 MHz against 1.2 GHz) with no gate; even gated to 55 °C, most turns end capped. Read
  `/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel` and the `gpuss-*`, `cpu-*`, `nsphvx-*` zones.
- **`pkill -f run_matrix.sh` inside `adb shell '...'` kills the adb shell itself**, because the
  command line contains the pattern. Kill by PID.
- **Transfers over the tunnel get cut off past about 4 MB** (`adb` exit 255, varying sizes). Copy
  results as `tar czf`, which made them a sixth of the size, and retry.
- **The `~/.ssh/config` ControlMaster takes the forward port.** Run the tunnel with
  `-o ControlMaster=no -o ControlPath=none` in a reconnect loop; it dropped three times in a
  night and the phone's `nohup` run carried on each time.
- **The `cpu-hw-trip-*` zones read a fixed 105 °C.** A gate that matches `cpu*` never opens.
- **After one cell of reply-length turns the gate almost never passes.** The phone stayed above 55 °C with
  the GPU capped and the CPU ceilings at 1.90 and 2.23 GHz after 120 s and after 300 s, so only a
  run's first cell (and once a cell after a 50 s wait) started unthrottled; one order per model then confounds processor with heat.
- **On the 3B and 4B, CPU decode stalls near 107 °C**: single steps of up to 15.9 s. Count them
  (`analyze_ref.py` does) before reading a CPU-decode total.
- **Changing a queue that is running**: never edit the running `sh` script, which reads itself as
  it goes. Write a new script that waits for the running benchmark's PID, then `kill -9` the old
  script's shell only (a plain `kill` is held while it waits on its child) and check with `ps`.
- **The reservation ends without warning**: adb reports "Connection reset by peer", then the
  reservation's key is refused. Copy results off often.

## ExecuTorch's QNN runner

`qnn_llama_runner` from ExecuTorch 1.5.1 builds against the app runtime's installed tree once
`examples/qualcomm/oss_scripts/llama/CMakeLists.txt` renames its `custom_ops` target (it clashes
with the installed one). It needs QNN 2.39's `libQnnHtp.so`, `libQnnSystem.so` and the V81
stub and skeleton for SM8850 (QAIRT 2.37 has no V81; the `com.qualcomm.qti:qnn-runtime:2.39.0`
AAR does). `make_et_prompts.py` writes one tokenized prompt per turn and a manifest, `run_et.sh`
runs them from the manifest behind the same cooling gate, and `analyze_et.py et-e1.txt
results-r5.jsonl --manifest manifest.json` sets the runner's own timings beside llama.cpp's
npu:npu cell on the same turns.
