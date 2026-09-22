# NPU and CPU matmul harnesses

Two small programs that answer one question on a real phone: **is MediaTek's NPU
faster than the CPU at the multiply this app spends its time in?**

- `npu_matmul_bench.cpp` — one `NEURON_FULLY_CONNECTED` in `QUANT8_ASYMM`
  through NeuronAdapter, pinned to the MDLA.
- `cpu_matmul_bench.cpp` — the same shape through ggml's `MUL_MAT`, loaded via
  the backend registry so it picks the identical CPU variant the app runs.

Results and what they mean are in
[`docs/research/mediatek-npu.md`](../../docs/research/mediatek-npu.md). This file
is only how to run them again.

## What you need

A **Linux x86_64 host**. Not optional: MediaTek's toolchain wheels are
`linux_x86_64` and CPython 3.10 only, so an Apple Silicon Mac cannot run them
natively. The harnesses themselves only need the NDK, but the same host is
needed for anything further with the SDK, so it is worth setting up once.

```sh
mkdir -p ~/openweights-npu && cd ~/openweights-npu

# uv, kept inside this directory rather than installed into ~/.local
export UV_INSTALL_DIR=$PWD/tools/bin UV_UNMANAGED_INSTALL=$PWD/tools/bin
curl -LsSf https://astral.sh/uv/install.sh | sh
export PATH=$PWD/tools/bin:$PATH
uv python install 3.10          # the wheels are cp310 only

# NeuroPilot Express SDK — a public download, no account. The link list is at
# https://neuropilot.mediatek.com/resources/public/npexpress/en/docs/npexpress
# It arrives under an opaque S3 filename and must be renamed before extracting.
mkdir -p sdk && cd sdk
curl -sL -o neuropilot-express-sdk.tar.gz '<link from that page>'
tar -xzf neuropilot-express-sdk.tar.gz && cd ..

# Android NDK r26d, which is the version ExecuTorch pins
curl -sL -o ndk.zip https://dl.google.com/android/repository/android-ndk-r26d-linux.zip
unzip -q ndk.zip -d ndk

# cmake and ninja, no root needed
uv venv --python 3.10 .venv
uv pip install --python .venv/bin/python cmake ninja
```

## Building

```sh
SDK=$HOME/openweights-npu/sdk/neuropilot-express-sdk-8.0.8-build20250925
CXX=$HOME/openweights-npu/ndk/android-ndk-r26d/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang++

# NPU side
$CXX -O2 -std=c++17 -I$SDK/api npu_matmul_bench.cpp -o npu_matmul_bench \
     -L$SDK -lneuronusdk_adapter.mtk

# CPU side: needs a llama.cpp built for Android with the app's own flags, or the
# measurement is of kernels the app never uses. See "llama.cpp for Android" below.
L=$HOME/openweights-npu/llamacpp
$CXX -O2 -std=c++17 -I$L/ggml/include cpu_matmul_bench.cpp -o cpu_matmul_bench \
     -L$L/build-android/bin -lggml -lggml-base
```

## Running on the phone

```sh
adb push npu_matmul_bench cpu_matmul_bench /data/local/tmp/npu/
adb push $SDK/libneuronusdk_adapter.mtk.so $SDK/libneuron_buffer_allocator.so /data/local/tmp/npu/
adb push $L/build-android/bin/libggml*.so /data/local/tmp/npu/

adb shell 'cd /data/local/tmp/npu && cp libneuronusdk_adapter.mtk.so libneuronusdk_adapter.mtk.so.8'
adb shell 'cd /data/local/tmp/npu && LD_LIBRARY_PATH=. LD_PRELOAD=./libneuron_buffer_allocator.so ./npu_matmul_bench'
adb shell 'cd /data/local/tmp/npu && LD_LIBRARY_PATH=. ./cpu_matmul_bench'
```

## Four things that each cost an hour

Every one of these presents as a crash or a wrong number rather than as a
message telling you what is wrong.

**Do not put `/vendor/lib64` on `LD_LIBRARY_PATH`.** It looks like the obvious
thing to do, since that is where the vendor runtime lives. It resolves a symbol
conflict through `/system/lib64/libinput.so`, and the adapter aborts inside
`NeuronCompilation_finish`. With the path left alone the same binary compiles and
runs. The adapter finds what it needs on its own.

**Pin the compilation to the MDLA.** `NeuronCompilation_create` considers every
device, and this phone's GPU and MVPU paths fail to `dlopen` OpenCL and abort
inside the vendor library. `NeuronCompilation_createForDevices` with the device
whose name contains `mdla` is the difference between a crash and a result. The
harness enumerates and selects it; `Neuron_getDeviceCount` reports `mtk-gpu`,
`mtk-dsp` and `mtk-mdla`.

**The adapter's SONAME is `.so.8`.** The file ships as
`libneuronusdk_adapter.mtk.so`, and the loader asks for
`libneuronusdk_adapter.mtk.so.8`. Copy or link it under both names.

**Make stdout unbuffered.** These programs die inside a vendor library, and
`adb shell` gives them a pipe, so a buffered stdout loses every line that would
have said where. The harnesses call `setvbuf` for this reason; keep it.

One more that is not a trap but is worth knowing: `libneuron_runtime.so` is
listed in `/vendor/etc/public.libraries.txt`, and an **app** process still
cannot `dlopen` it. A shell binary reached the runtime through the bundled
adapter instead. Anything shipped in an APK has to bundle
`libneuronusdk_adapter.mtk.so` and `libneuron_buffer_allocator.so`, which the
SDK licence permits in object form as part of an application.

## llama.cpp for Android, on the same host

Needed for `cpu_matmul_bench`, and useful on its own: this takes about
2m40s cold and 50s incremental on an 8-core box.

```sh
git clone --depth 1 https://github.com/ggml-org/llama.cpp.git llamacpp && cd llamacpp
export PATH=$HOME/openweights-npu/.venv/bin:$PATH
cmake -S . -B build-android -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$HOME/openweights-npu/ndk/android-ndk-r26d/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
  -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON -DGGML_NATIVE=OFF \
  -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DGGML_CPU_KLEIDIAI=ON \
  -DLLAMA_CURL=OFF -DLLAMA_BUILD_TOOLS=ON -DLLAMA_BUILD_SERVER=ON
ninja -C build-android llama-app llama-bench
```

`GGML_CPU_ALL_VARIANTS` and `GGML_CPU_KLEIDIAI` are the ones that matter. A plain
`arm64-v8a` build has no dotprod or i8mm kernels and runs about an order of
magnitude slower, which reads as a catastrophic result rather than as a
misconfigured build. `LLAMA_BUILD_SERVER=ON` is required even if you do not want
a server: `llama-app` links against `libllama-server-impl.so`.

Models must live on `/data/local/tmp`, not `/sdcard`. As the shell user the
latter goes through the FUSE emulation layer, which is far slower than the app's
own access to the same file and will dominate any measurement.

## The disaggregated JNI bridge

`mtk_pd_disaggregated_jni.cpp` is the source of `libexecutorch_pd_jni.so`, the library
the app loads when the NPU reads a prompt and the CPU writes the reply. It is kept here
because the built library is git ignored: a hundred megabytes of generated binary does
not belong in the repository, and without the source the repository could not reproduce
its own debug artifact.

It does not build here. It builds inside an ExecuTorch tree, against MediaTek's runner
in `examples/mediatek/executor_runner/llama_runner`, which is where it has to be copied
before building:

```sh
cp tools/npu/mtk_pd_disaggregated_jni.cpp \
   "$EXECUTORCH/examples/mediatek/executor_runner/mtk_pd_disaggregated_jni.cpp"
ninja -C "$EXECUTORCH/cmake-android-ninja/examples/mediatek" executorch_pd_jni
llvm-strip --strip-unneeded \
  -o core/engine/src/debug/jniLibs/arm64-v8a/libexecutorch_pd_jni.so \
  "$EXECUTORCH/cmake-android-ninja/examples/mediatek/libexecutorch_pd_jni.so"
```

**Configure both trees with `-DCMAKE_BUILD_TYPE=Release`, and check it took.** The same
warning three paragraphs up about a plain `arm64-v8a` llama.cpp build applies here and
was paid for twice. `examples/mediatek` is its own CMake project, so a build type set on
the parent tree does not reach it, and an empty `CMAKE_BUILD_TYPE` means CMake adds no
`-O` flag at all rather than defaulting to anything. It reads as a slow model, never as a
misconfigured build:

```sh
cmake -S . -B cmake-android-out -DCMAKE_BUILD_TYPE=Release
cmake -S examples/mediatek -B cmake-android-out/examples/mediatek -DCMAKE_BUILD_TYPE=Release
# then confirm it reached XNNPACK, which is where it matters
python3 -c "import json;cc=json.load(open('cmake-android-out/compile_commands.json'));\
print(next(e['command'] for e in cc if '/XNNPACK/' in e['file']))" | grep -o '\-O3'
```

## Timing one decode step

`cpu_forward_bench.cpp` loads a CPU `.pte` and times a single `forward()` with no Neuron
runtime, no handoff, no tokenizer and no app around it. It exists because a slow decode
has three possible owners here, the loop, the libraries and the NPU sharing the process,
and only this separates them. It links the same libraries as the disaggregated runner, so
the one difference between those binaries is that this never initialises an NPU.

```sh
ninja -C "$EXECUTORCH/cmake-android-ninja/examples/mediatek" cpu_forward_bench
adb -s $SER push cpu_forward_bench /data/local/tmp/npu/
adb -s $SER shell am force-stop io.github.alpharomercoma.openweights.debug
adb -s $SER shell "cd /data/local/tmp/npu && LD_LIBRARY_PATH=. ./cpu_forward_bench \
  --cpu_model_path=<the app's .pte> --steps=30 --start_pos=1024 --mmap=true"
```

`--start_pos` is the point: this model's `forward()` is linear in how much KV cache
attention reads, so a number taken at position 0 says nothing about a real turn. Sweep it.
Force stopping the app first is not optional, because a held model in another process
turned the first reading ever taken here into nonsense.

What it measures, what it costs and why the path ships off is in
[`docs/research/npu-pd-disaggregation.md`](../../docs/research/npu-pd-disaggregation.md).
