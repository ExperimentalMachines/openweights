// Runs LFM3's tool pass the way engine_session.cpp does, greedy, under several reasoning budgets,
// on the app's prompt and recorded search results, and writes what each budget did. Mac only.
#include "llama.h"
#include "chat.h"
#include "common.h"
#include "sampling.h"
#include <nlohmann/json.hpp>
#include <cstdio>
#include <fstream>
#include <regex>
#include <string>
#include <vector>
using json = nlohmann::ordered_json;
static std::string esc(const std::string & s) { return std::regex_replace(s, std::regex(R"([.^$|()*+?\[\]{}\\])"), R"(\$&)"); }
static bool ends_with(const std::string & a, const std::string & b) { return a.size() >= b.size() && a.compare(a.size() - b.size(), b.size(), b) == 0; }
int main(int argc, char ** argv) {
    // model prompt.json rows.jsonl out.jsonl maxrows budgets...
    llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
    llama_backend_init();
    auto mp = llama_model_default_params(); mp.n_gpu_layers = 99;
    llama_model * model = llama_model_load_from_file(argv[1], mp);
    const llama_vocab * vocab = llama_model_get_vocab(model);
    auto tmpls = common_chat_templates_init(model, "");
    auto cp = llama_context_default_params(); cp.n_ctx = 16384; cp.n_batch = 2048; cp.n_threads = 6; cp.n_threads_batch = 6; cp.no_perf = true;
    llama_context * ctx = llama_init_from_model(model, cp);
    json prompt = json::parse(std::ifstream(argv[2]));
    std::vector<common_chat_tool> tools;
    for (auto & t : prompt["tools"]) tools.push_back({t["name"].get<std::string>(), t["description"].get<std::string>(), t["parameters"].is_string() ? t["parameters"].get<std::string>() : t["parameters"].dump()});
    std::vector<json> rows; { std::ifstream f(argv[3]); std::string l; while (std::getline(f, l)) if (!l.empty()) rows.push_back(json::parse(l)); }
    std::ofstream out(argv[4], std::ios::app);
    int maxrows = atoi(argv[5]);
    std::vector<std::string> budgets; for (int i = 6; i < argc; i++) budgets.push_back(argv[i]);
    const std::string NOTE = "\n\nI have thought enough, so I will act on what I have now.";
    auto msg = [](std::string role, std::string content, std::string id = "") { common_chat_msg m; m.role = role; m.content = content; m.tool_call_id = id; return m; };
    int done = 0;
    for (auto & row : rows) {
        if (done++ >= maxrows) break;
        std::vector<std::vector<common_chat_msg>> passes;
        std::vector<common_chat_msg> head = {msg("system", prompt["system"]), msg("user", prompt["firstUser"]), msg("assistant", "Noted."), msg("user", row["question"])};
        passes.push_back(head);
        if (row.contains("result")) {
            auto second = head;
            json callj = row["call"].is_string() ? json::parse(row["call"].get<std::string>()) : row["call"]; std::string q = callj.value("query", "");
            second.push_back(msg("assistant", "<tool_call>\n<function=web_search>\n<parameter=query>\n" + q + "\n</parameter>\n</function>\n</tool_call>"));
            second.push_back(msg("tool", row["result"], "web_search"));
            passes.push_back(second);
        }
        for (size_t pn = 0; pn < passes.size(); pn++) for (const auto & b : budgets) {
            common_chat_templates_inputs in;
            in.add_generation_prompt = true; in.use_jinja = true; in.reasoning_format = COMMON_REASONING_FORMAT_AUTO; in.enable_thinking = true;
            in.chat_template_kwargs["preserve_thinking"] = "true"; in.chat_template_kwargs["keep_past_thinking"] = "true";
            in.messages = passes[pn]; in.tools = tools;
            common_chat_params p;
            try { p = common_chat_templates_apply(tmpls.get(), in); } catch (const std::exception & e) { fprintf(stderr, "render %s\n", e.what()); continue; }
            std::vector<std::string> pats; std::vector<llama_token> trig;
            for (auto & t : p.grammar_triggers) { if (t.type == COMMON_GRAMMAR_TRIGGER_TYPE_WORD) pats.push_back(esc(t.value)); else if (t.type == COMMON_GRAMMAR_TRIGGER_TYPE_PATTERN) pats.push_back(t.value); else if (t.type == COMMON_GRAMMAR_TRIGGER_TYPE_TOKEN) trig.push_back(t.token); }
            std::vector<const char *> cp2; for (auto & s : pats) cp2.push_back(s.c_str());
            llama_sampler * g = llama_sampler_init_grammar_lazy_patterns(vocab, p.grammar.c_str(), "root", cp2.data(), cp2.size(), trig.data(), trig.size());
            llama_sampler * chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
            if (g) llama_sampler_chain_add(chain, g);
            llama_sampler_chain_add(chain, llama_sampler_init_greedy());
            bool note = !b.empty() && b.back() == 'm';
            int budget = b == "none" ? -1 : atoi(b.c_str());
            auto toks = common_tokenize(vocab, p.prompt.str(), true, true);
            llama_memory_clear(llama_get_memory(ctx), true);
            for (size_t i = 0; i < toks.size(); i += 2048) {
                int n = std::min<size_t>(2048, toks.size() - i);
                if (llama_decode(ctx, llama_batch_get_one(toks.data() + i, n))) { fprintf(stderr, "decode failed\n"); break; }
            }
            std::string text; int gen = 0, thought = 0; bool in_think = ends_with(p.prompt.str(), p.thinking_start_tag) && !p.thinking_start_tag.empty();
            int close_at = -1; bool forced = false, eos = false; std::string closed_by;
            const int CAP = 600;
            try {
            while (gen < CAP) {
                if (in_think && budget >= 0 && thought >= budget) {
                    std::string close = (note ? NOTE : "") + p.thinking_end_tags.front() + "\n";
                    auto ct = common_tokenize(vocab, close, false, true);
                    if (llama_decode(ctx, llama_batch_get_one(ct.data(), ct.size()))) break;
                    for (auto t : ct) llama_sampler_accept(chain, t);
                    text += close; in_think = false; forced = true; close_at = gen; closed_by = "budget";
                    continue;
                }
                llama_token t = llama_sampler_sample(chain, ctx, -1);
                if (llama_vocab_is_eog(vocab, t)) { eos = true; break; }
                std::string piece = common_token_to_piece(vocab, t, true);
                text += piece; gen++;
                if (in_think) { thought++; for (auto & e : p.thinking_end_tags) if (ends_with(text, e)) { in_think = false; close_at = gen; closed_by = e; break; } }
                if (llama_decode(ctx, llama_batch_get_one(&t, 1))) break;
            }
            } catch (const std::exception & e) { text += std::string("\n[THREW ") + e.what() + "]"; }
            llama_sampler_free(chain);
            size_t ci = text.find("</think>");
            std::string after = ci == std::string::npos ? "" : text.substr(ci + 8);
            json r = {{"id", row["id"]}, {"pass", pn + 1}, {"budget", b}, {"prompt", toks.size()}, {"generated", gen}, {"thought", thought}, {"close_at", close_at}, {"closed_by", closed_by}, {"forced", forced}, {"eos", eos},
                      {"call", text.find("<tool_call>") != std::string::npos}, {"reasoning_tail", ci == std::string::npos ? text.substr(text.size() > 90 ? text.size() - 90 : 0) : text.substr(ci > 90 ? ci - 90 : 0, std::min<size_t>(ci, 90))}, {"after", after.substr(0, 400)}, {"after_len", after.size()}};
            out << r.dump(-1, ' ', false, json::error_handler_t::replace) << "\n"; out.flush();
            printf("%s p%zu b=%s prompt=%zu gen=%d thought=%d forced=%d eos=%d call=%d\n", row["id"].get<std::string>().c_str(), pn + 1, b.c_str(), toks.size(), gen, thought, (int) forced, (int) eos, r["call"].get<bool>() ? 1 : 0); fflush(stdout);
        }
    }
}
