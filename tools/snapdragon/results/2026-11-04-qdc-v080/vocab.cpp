// Lists vocabulary pieces that contain a thinking or call tag but are not exactly that tag.
#include "llama.h"
#include "common.h"
#include <cstdio>
#include <string>
int main(int argc, char ** argv) {
    llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
    llama_backend_init();
    for (int a = 1; a < argc; a++) {
        auto mp = llama_model_default_params(); mp.vocab_only = true;
        llama_model * m = llama_model_load_from_file(argv[a], mp);
        if (!m) { printf("%s LOAD FAILED\n", argv[a]); continue; }
        const llama_vocab * v = llama_model_get_vocab(m);
        const std::string tags[] = {"</think>", "<think>", "<tool_call>", "<|tool_call_start|>"};
        int merged = 0, exact = 0;
        std::string show;
        for (int t = 0; t < llama_vocab_n_tokens(v); t++) {
            std::string p = common_token_to_piece(v, t, true);
            for (const auto & g : tags) {
                if (p == g) exact++;
                else if (p.find(g) != std::string::npos) { merged++; if (show.size() < 120) show += "[" + p + "]"; }
            }
        }
        std::string n = argv[a]; n = n.substr(n.rfind('/') + 1);
        printf("%-44s exact=%d merged=%d %s\n", n.c_str(), exact, merged, show.c_str());
        llama_model_free(m);
    }
}
