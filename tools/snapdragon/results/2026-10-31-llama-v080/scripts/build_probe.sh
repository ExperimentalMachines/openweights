#!/bin/bash
# Links probe.cpp against each host build of llama.cpp.
cd "$(dirname "$0")"
for v in 105 080; do
    D=""
    [ "$v" = 080 ] && D="-DV080"
    libs=$(find host$v -name '*.a' | grep -v httplib | tr '\n' ' ')
    clang++ -std=c++17 -O1 $D -Isrc$v/include -Isrc$v/ggml/include -Isrc$v/common -Isrc$v/vendor probe.cpp \
        $libs -framework Accelerate -framework Foundation -o probe$v 2>&1 | grep -E "error|undefined" | head -10
    echo "probe$v link ${PIPESTATUS[0]}"
done
