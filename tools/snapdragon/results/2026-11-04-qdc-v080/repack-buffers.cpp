// Loads each model's weights the way the app does (no mmap, CPU, extra buffers on) and prints what
// each llama.cpp buffer holds, so a kernel's repacking can be read without a context or a phone.
#include "llama.h"
#include <cstdio>
#include <string>
#include <sys/stat.h>
static std::string buffers;
int main(int argc, char ** argv) {
    llama_log_set([](ggml_log_level, const char * t, void *) {
        std::string s(t);
        if (s.find("buffer size") != std::string::npos || s.find("kleidi") != std::string::npos) buffers += "    " + s;
    }, nullptr);
    llama_backend_init();
    printf("%s\n", llama_print_system_info());
    for (int a = 1; a < argc; a++) {
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0; mp.load_mode = LLAMA_LOAD_MODE_NONE; mp.use_extra_bufts = true;
        buffers.clear();
        struct stat st; stat(argv[a], &st);
        llama_model * m = llama_model_load_from_file(argv[a], mp);
        std::string n = argv[a]; n = n.substr(n.rfind('/') + 1);
        printf("%s file %lld MiB%s\n%s", n.c_str(), (long long) st.st_size >> 20, m ? "" : " LOAD FAILED", buffers.c_str());
        fflush(stdout);
        if (m) llama_model_free(m);
    }
}
