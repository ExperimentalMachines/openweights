// A two-pass tool turn through the app's own engine_session.cpp, on the Mac, for reproducing a throw.
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
    // model prompt.json rows.jsonl budget
    std::string err;
    auto * s = Session::load(argv[1], "", 4096, 6, 6, 99, false, false, false, false, 0, 2048, 512, err);
    if (!s) { printf("LOAD FAILED %s\n", err.c_str()); return 1; }
    json prompt = json::parse(std::ifstream(argv[2]));
    std::vector<ToolDefinition> tools;
    for (auto & t : prompt["tools"]) if (t["name"] == "web_search") tools.push_back({t["name"].get<std::string>(), t["description"].get<std::string>(), t["parameters"].is_string() ? t["parameters"].get<std::string>() : t["parameters"].dump()});
    SamplerConfig sc; sc.temperature = 0; sc.top_k = 1; sc.seed = 1; sc.max_tokens = 600; sc.repeat_penalty = 1; sc.reasoning_budget = atoi(argv[4]);
    ReasoningConfig rc; rc.enabled = true;
    std::ifstream f(argv[3]); std::string line; int threw = 0, turns = 0;
    while (std::getline(f, line)) {
        if (line.empty()) continue;
        json row = json::parse(line);
        std::vector<ChatMessage> m = {{"system", prompt["system"].get<std::string>()}, {"user", prompt["firstUser"].get<std::string>()}, {"assistant", "Noted."}, {"user", row["question"].get<std::string>()}};
        std::string id = row["id"];
        for (int pass = 1; pass <= 2; pass++) {
            GenerationStats st; ParsedReply rep; std::string e; turns++;
            try {
                auto why = s->generate(m, tools, sc, rc, [](const char *, float) { return true; }, st, rep, e);
                printf("%s pass %d stop=%d gen=%d err=[%s]\n", id.c_str(), pass, (int) why, (int) st.generated_tokens, e.c_str());
            } catch (const std::exception & x) {
                threw++; printf("%s pass %d THREW %s | history tail [%s]\n", id.c_str(), pass, x.what(), m.back().content.substr(0, 40).c_str()); fflush(stdout); break;
            }
            fflush(stdout);
            if (pass == 1) {
                if (rep.content.find("tool_call") == std::string::npos) break;
                m.push_back({"assistant", rep.content});
                ChatMessage t; t.role = "tool"; t.content = row.contains("result") ? row["result"].get<std::string>() : "no results"; t.tool_call_id = "web_search";
                m.push_back(t);
            }
        }
    }
    printf("turns %d threw %d\n", turns, threw); fflush(stdout);
    delete s;
    _exit(0);
}
