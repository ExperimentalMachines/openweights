#!/bin/bash
# The app-flag tools with only KleidiAI switched off, to isolate what KleidiAI costs or
# gains on this phone; everything else as in build_apptools.sh.
cd "$(dirname "$0")"
T=/opt/homebrew/share/android-commandlinetools
for v in 080; do
    $T/cmake/4.1.2/bin/cmake -S src$v -B nok$v -G Ninja -DCMAKE_MAKE_PROGRAM=$T/cmake/4.1.2/bin/ninja \
        -DCMAKE_TOOLCHAIN_FILE=$T/ndk/29.0.14206865/build/cmake/android.toolchain.cmake \
        -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-31 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release \
        "-DCMAKE_CXX_FLAGS=-O3 -fexceptions -frtti" -DBUILD_SHARED_LIBS=ON -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON \
        -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DGGML_CPU_KLEIDIAI=OFF -DGGML_OPENCL=ON \
        -DGGML_OPENCL_EMBED_KERNELS=ON -DGGML_OPENCL_USE_ADRENO_KERNELS=ON -DLLAMA_CURL=OFF -DLLAMA_BUILD_COMMON=ON \
        -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_TOOLS=ON -DLLAMA_BUILD_SERVER=OFF \
        -DOPENCL_ICD_LOADER_HEADERS_DIR=$PWD/tree080/OpenCL-Headers -DOpenCL_INCLUDE_DIR=$PWD/tree080/OpenCL-Headers \
        -DOpenCL_LIBRARY=$PWD/build080/OpenCL-ICD-Loader/libOpenCL.so > nok$v.cfg.log 2>&1 || { echo "NOKCFG$v FAIL"; continue; }
    $T/cmake/4.1.2/bin/ninja -C nok$v llama-bench llama-perplexity > nok$v.build.log 2>&1 || { echo "NOKBUILD$v FAIL"; continue; }
    rm -rf k$v && mkdir -p k$v
    cp nok$v/bin/llama-bench nok$v/bin/llama-perplexity k$v/
    find nok$v/bin -name '*.so' -exec cp {} k$v/ \;
    cp $T/ndk/29.0.14206865/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so k$v/
    rm -f k$v/libOpenCL.so
    tar czf k$v.tgz k$v
    echo "NOK$v OK $(ls k$v | wc -l) files $(du -h k$v.tgz | cut -f1)"
done
