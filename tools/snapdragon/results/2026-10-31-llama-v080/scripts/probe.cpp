// Renders the same conversations through llama.cpp's chat templates the way engine_session.cpp
// does, and prints everything the engine reads back: prompt bytes, generation prompt, format,
// grammar and triggers. Built once per llama.cpp version; the outputs are diffed.
#include "llama.h"
#include "chat.h"
#include "common.h"

#include <cstdio>
#include <functional>
#include <string>
#include <vector>

static std::string to_str(const std::string & s) { return s; }
#ifdef V080
static std::string to_str(const common_chat_prompt & p) { return p.str(); }
#endif

static size_t fnv(const std::string & s) {
    size_t h = 1469598103934665603ull;
    for (unsigned char c : s) { h ^= c; h *= 1099511628211ull; }
    return h;
}

int main(int argc, char ** argv) {
    llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
    llama_backend_init();
    auto mp = llama_model_default_params();
    mp.vocab_only = true;
    llama_model * model = llama_model_load_from_file(argv[1], mp);
    if (!model) { printf("LOAD FAILED\n"); return 1; }
    auto tmpls = common_chat_templates_init(model, "");

    const std::vector<common_chat_tool> tools = {
        {"web_search", "Search the web for current information.",
         R"({"type":"object","properties":{"query":{"type":"string","description":"What to search for"},"count":{"type":"integer","minimum":1,"maximum":10},"freshness":{"type":"number","default":0.5}},"required":["query"]})"},
        {"get_time", "Current local time.", R"({"type":"object","properties":{}})"},
    };
    auto msg = [](std::string role, std::string content, std::string reasoning = "", std::string id = "") {
        common_chat_msg m; m.role = role; m.content = content; m.reasoning_content = reasoning; m.tool_call_id = id; return m;
    };

    struct Case { const char * name; std::vector<common_chat_msg> msgs; bool tools; bool think; bool gen; std::string effort; };
    std::vector<Case> cases = {
        {"plain_think", {msg("system", "You are a helpful assistant."), msg("user", "What is 2+2?")}, false, true, true, ""},
        {"plain_nothink", {msg("system", "You are a helpful assistant."), msg("user", "What is 2+2?")}, false, false, true, ""},
        {"warm_head_tools", {msg("system", "You are a helpful assistant.")}, true, true, false, ""},
        {"multi_turn_tools", {msg("system", "You are a helpful assistant."), msg("user", "Who won the 2022 World Cup?"),
            msg("assistant", "Argentina won it.", "The user asks about football."), msg("user", "And the time now?")}, true, true, true, ""},
        {"tool_result", {msg("system", "You are a helpful assistant."), msg("user", "Search for llama.cpp"),
            msg("assistant", "<|tool_call_start|>[web_search(query=\"llama.cpp\")]<|tool_call_end|>"),
            msg("tool", "llama.cpp is an inference engine.", "", "call_1")}, true, true, true, ""},
        {"effort_low", {msg("user", "Hi")}, false, true, true, "low"},
    };

    for (const auto & c : cases) {
        common_chat_templates_inputs in;
        in.add_generation_prompt = c.gen;
        in.use_jinja = true;
        in.reasoning_format = COMMON_REASONING_FORMAT_AUTO;
        in.enable_thinking = c.think;
        in.chat_template_kwargs["preserve_thinking"] = "true";
        in.chat_template_kwargs["keep_past_thinking"] = "true";
        if (!c.effort.empty()) in.chat_template_kwargs["reasoning_effort"] = "\"" + c.effort + "\"";
        in.messages = c.msgs;
        if (c.tools) in.tools = tools;
        try {
            const auto p = common_chat_templates_apply(tmpls.get(), in);
            const std::string prompt = to_str(p.prompt);
            printf("### %s\n", c.name);
            printf("format=%d gen_prompt=[%s] lazy=%d grammar_hash=%zx grammar_len=%zu triggers=%zu preserved=%zu stops=%zu\n",
                   (int) p.format, p.generation_prompt.c_str(), (int) p.grammar_lazy, fnv(p.grammar), p.grammar.size(),
                   p.grammar_triggers.size(), p.preserved_tokens.size(), p.additional_stops.size());
            for (const auto & t : p.grammar_triggers) printf("  trigger %d [%s]\n", (int) t.type, t.value.c_str());
            if (getenv("DUMP_GRAMMAR") && !p.grammar.empty()) printf("GRAMMAR<<<\n%s\n>>>\n", p.grammar.c_str());
            printf("prompt_hash=%zx len=%zu\n<<<%s>>>\n", fnv(prompt), prompt.size(), prompt.c_str());
        } catch (const std::exception & e) {
            printf("### %s\nTHREW %s\n", c.name, e.what());
        }
    }
    printf("### support_enable_thinking=%d\n", (int) common_chat_templates_support_enable_thinking(tmpls.get()));
    llama_model_free(model);
    return 0;
}
