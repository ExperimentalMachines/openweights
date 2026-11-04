#!/bin/bash
# llama-bench and llama-perplexity built with the app's own CMake flags (KleidiAI, every CPU
# variant loaded at runtime, OpenCL), once per llama.cpp version, packed for the phone.
cd "$(dirname "$0")"
T=/opt/homebrew/share/android-commandlinetools
for v in 105 080; do
    $T/cmake/4.1.2/bin/cmake -S src$v -B app$v -G Ninja -DCMAKE_MAKE_PROGRAM=$T/cmake/4.1.2/bin/ninja \
        -DCMAKE_TOOLCHAIN_FILE=$T/ndk/29.0.14206865/build/cmake/android.toolchain.cmake \
        -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-31 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release \
        "-DCMAKE_CXX_FLAGS=-O3 -fexceptions -frtti" -DBUILD_SHARED_LIBS=ON -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON \
        -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DGGML_CPU_KLEIDIAI=ON -DGGML_OPENCL=ON \
        -DGGML_OPENCL_EMBED_KERNELS=ON -DGGML_OPENCL_USE_ADRENO_KERNELS=ON -DLLAMA_CURL=OFF -DLLAMA_BUILD_COMMON=ON \
        -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_TOOLS=ON -DLLAMA_BUILD_SERVER=OFF \
        -DOPENCL_ICD_LOADER_HEADERS_DIR=$PWD/tree080/OpenCL-Headers -DOpenCL_INCLUDE_DIR=$PWD/tree080/OpenCL-Headers \
        -DOpenCL_LIBRARY=$PWD/build080/OpenCL-ICD-Loader/libOpenCL.so > app$v.cfg.log 2>&1 || { echo "CFG$v FAIL"; continue; }
    $T/cmake/4.1.2/bin/ninja -C app$v llama-bench llama-perplexity > app$v.build.log 2>&1 || { echo "BUILD$v FAIL"; continue; }
    rm -rf a$v && mkdir -p a$v
    cp app$v/bin/llama-bench app$v/bin/llama-perplexity a$v/
    find app$v/bin -name '*.so' -exec cp {} a$v/ \;
    cp $T/ndk/29.0.14206865/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so a$v/
    rm -f a$v/libOpenCL.so
    tar czf a$v.tgz a$v
    echo "APP$v OK $(ls a$v | wc -l) files $(du -h a$v.tgz | cut -f1)"
done
