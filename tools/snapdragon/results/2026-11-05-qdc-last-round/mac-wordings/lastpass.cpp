// The last passes of a tool turn through the app's own engine_session.cpp, on the Mac, to choose
// wording before spending phone time. Earlier passes are rebuilt from recorded calls and results.
// usage: lastpass model tools.json rows.jsonl budget1 budget2 note spent out.jsonl
#include <unistd.h>
#include "engine_session.h"
#include <nlohmann/json.hpp>
#include <cstdio>
#include <fstream>
#include <string>
#include <vector>
using json = nlohmann::ordered_json;
using namespace openweights;
int main(int argc, char ** argv) {
    std::string err;
    auto * s = Session::load(argv[1], "", 4096, 6, 6, 99, false, false, false, false, 0, 2048, 512, err);
    if (!s) { printf("LOAD FAILED %s\n", err.c_str()); return 1; }
    json prompt = json::parse(std::ifstream(argv[2]));
    std::vector<ToolDefinition> tools;
    for (auto & t : prompt["tools"]) if (t["name"] == "web_search") tools.push_back({t["name"].get<std::string>(), t["description"].get<std::string>(), t["parameters"].is_string() ? t["parameters"].get<std::string>() : t["parameters"].dump()});
    const int b1 = atoi(argv[4]), b2 = atoi(argv[5]);
    const std::string note = argv[6], spent = argv[7];
    std::ofstream out(argv[8]);
    std::ifstream f(argv[3]); std::string line;
    ReasoningConfig rc; rc.enabled = true;
    while (std::getline(f, line)) {
        if (line.empty()) continue;
        json row = json::parse(line);
        std::vector<ChatMessage> m;
        for (auto & x : row["messages"]) {
            ChatMessage c; c.role = x["role"].get<std::string>(); c.content = x["content"].get<std::string>();
            if (x.contains("tool_call_id")) c.tool_call_id = x["tool_call_id"].get<std::string>();
            m.push_back(c);
        }
        json passes = json::array(); std::string last_raw, last_content; int stop = -1;
        for (int pass = 0; pass < 2; pass++) {
            SamplerConfig sc; sc.temperature = 0; sc.top_k = 1; sc.seed = 1; sc.max_tokens = 600; sc.repeat_penalty = 1;
            sc.reasoning_budget = pass == 0 ? b1 : b2; sc.reasoning_close_note = note;
            std::string raw; GenerationStats st; ParsedReply rep; std::string e;
            auto why = s->generate(m, tools, sc, rc, [&raw](const char * t, float) { raw += t; return true; }, st, rep, e);
            stop = (int) why; last_raw = raw; last_content = rep.content;
            passes.push_back({{"stop", (int) why}, {"gen", (int) st.generated_tokens}, {"calls", (int) rep.tool_calls.size()}, {"err", e}});
            if (rep.tool_calls.empty() || pass == 1) break;
            m.push_back({"assistant", "<think>" + raw});
            ChatMessage t; t.role = "tool"; t.content = spent; t.tool_call_id = rep.tool_calls[0].id; m.push_back(t);
        }
        out << json({{"id", row["id"]}, {"question", row["question"]}, {"answers", row["answers"]}, {"need", row["need"]}, {"passes", passes}, {"raw", last_raw}}).dump() << "\n"; out.flush();
        s->reset();
    }
    delete s;
    _exit(0);
}
