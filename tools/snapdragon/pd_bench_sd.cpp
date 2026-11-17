// Prefill on one processor, decode on another, for every pairing of CPU, Adreno GPU (OpenCL)
// and Hexagon NPU (HTP), over real conversations replayed turn by turn.
//
// pd_bench_sd: pd_bench for a retail Snapdragon phone with an unrooted shell (Firebase Device
// Streaming), so its results line up with the MediaTek tablet's (tools/mediatek/pd_bench_mtk.cpp):
// heat is read as a shell can (Android's thermal service, each CPU policy's ceiling, the Adreno
// clock files), the cooling gate is the tablet's (AP and SKIN at or under the limit, thermal status
// 0, no CPU ceiling lowered), --window and --pad-batch end a conversation at the same turn the
// tablet's 2,048-token NPU window did, MemAvailable is checked before each load, and the rows carry
// the tablet's field names, so the same analysis and site generator read both.
//
// One llama.cpp build carries all three backends. The model is loaded once per processor; each
// load has its own context. A turn prefills its new tokens on the prefill context, takes the
// first token from that context's logits, copies the sequence state (KV cache, and recurrent
// state on hybrids) into the decode context, and decodes there. The prefill context is never
// touched by decode, so the next turn continues from the conversation as the dataset has it.
//
// History is teacher forced: every assistant turn in the prompt is the dataset's own reply, so
// all nine pairings read exactly the same tokens and decode the same number, and the only
// difference between cells is where each half ran. Decode always writes --gen tokens, past an
// end of turn if the model emits one, so the work is equal; the tokens are kept so the cells
// can be compared against the CPU-only cell. With --gen-ref each turn instead writes as many
// tokens as the dataset's own reply to that turn has (plus one for the end of turn), capped at
// --gen, so decode carries the weight it has in the conversation; each step's time is kept.
//
//   pd_bench -m model.gguf -c conversations.jsonl -o rows.jsonl --cells cpu:cpu,gpu:cpu,...
//            [--gen 128] [--gen-ref] [--ctx 8192] [--threads 6] [--max-turns 8] [--npu-devices HTP0]
//            [--paused-threads 2]
//
// --paused-threads N: a CPU decoder reads the CPU pause switches before every step and decodes
// with N threads while either is on, --threads otherwise; each step's count goes in step_threads.
#include "llama.h"
#include "ggml-backend.h"

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <map>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

using clk = std::chrono::steady_clock;
static double ms_since(clk::time_point t) {
    return std::chrono::duration<double, std::milli>(clk::now() - t).count();
}

// Minimal JSON for the one input shape: {"id":..,"source":..,"messages":[{"role":..,"content":..}]}.
struct Msg { std::string role, content; };
struct Conv { std::string id, source; std::vector<Msg> msgs; };

static size_t skip_ws(const std::string & s, size_t i) { while (i < s.size() && isspace((unsigned char) s[i])) i++; return i; }
static std::string read_str(const std::string & s, size_t & i) {
    std::string out; i++;  // opening quote
    while (i < s.size() && s[i] != '"') {
        if (s[i] == '\\' && i + 1 < s.size()) {
            char e = s[++i];
            if (e == 'n') out += '\n'; else if (e == 't') out += '\t'; else if (e == 'r') out += '\r';
            else if (e == 'b') out += '\b'; else if (e == 'f') out += '\f';
            else if (e == 'u' && i + 4 < s.size()) {
                unsigned cp = std::stoul(s.substr(i + 1, 4), nullptr, 16); i += 4;
                if (cp >= 0xD800 && cp <= 0xDBFF && i + 6 < s.size() && s[i + 1] == '\\' && s[i + 2] == 'u') {
                    unsigned lo = std::stoul(s.substr(i + 3, 4), nullptr, 16); i += 6;
                    cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                }
                if (cp < 0x80) out += (char) cp;
                else if (cp < 0x800) { out += (char) (0xC0 | (cp >> 6)); out += (char) (0x80 | (cp & 0x3F)); }
                else if (cp < 0x10000) { out += (char) (0xE0 | (cp >> 12)); out += (char) (0x80 | ((cp >> 6) & 0x3F)); out += (char) (0x80 | (cp & 0x3F)); }
                else { out += (char) (0xF0 | (cp >> 18)); out += (char) (0x80 | ((cp >> 12) & 0x3F)); out += (char) (0x80 | ((cp >> 6) & 0x3F)); out += (char) (0x80 | (cp & 0x3F)); }
            } else out += e;
            i++;
        } else out += s[i++];
    }
    i++;  // closing quote
    return out;
}
static std::string field(const std::string & obj, const std::string & key) {
    size_t k = obj.find("\"" + key + "\"");
    if (k == std::string::npos) return "";
    size_t i = skip_ws(obj, obj.find(':', k) + 1);
    return obj[i] == '"' ? read_str(obj, i) : "";
}
static Conv parse_conv(const std::string & line) {
    Conv c; c.id = field(line, "id"); c.source = field(line, "source");
    size_t m = line.find("\"messages\"");
    size_t i = line.find('[', m) + 1;
    while (true) {
        i = skip_ws(line, i);
        if (i >= line.size() || line[i] == ']') break;
        if (line[i] == ',') { i++; continue; }
        // one {"role":"..","content":".."} object, keys in either order
        Msg msg; i++;
        while (true) {
            i = skip_ws(line, i);
            if (line[i] == '}') { i++; break; }
            if (line[i] == ',') { i++; continue; }
            std::string key = read_str(line, i);
            i = skip_ws(line, i) + 1;  // colon
            i = skip_ws(line, i);
            std::string val = read_str(line, i);
            if (key == "role") msg.role = val; else if (key == "content") msg.content = val;
        }
        c.msgs.push_back(msg);
    }
    return c;
}
static std::string esc(const std::string & s) {
    std::string o;
    for (unsigned char ch : s) {
        if (ch == '"' || ch == '\\') { o += '\\'; o += ch; }
        else if (ch == '\n') o += "\\n"; else if (ch == '\r') o += "\\r"; else if (ch == '\t') o += "\\t";
        else if (ch < 0x20) { char b[8]; snprintf(b, sizeof b, "\\u%04x", ch); o += b; }
        else o += ch;
    }
    return o;
}

struct Proc {
    std::string name;            // cpu, gpu, npu
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    double load_ms = 0;
    std::string error;
};

static ggml_backend_dev_t dev_by_prefix(const char * prefix) {
    for (size_t i = 0; i < ggml_backend_dev_count(); i++) {
        ggml_backend_dev_t d = ggml_backend_dev_get(i);
        if (strncmp(ggml_backend_dev_name(d), prefix, strlen(prefix)) == 0) return d;
    }
    return nullptr;
}

static bool open_proc(Proc & p, const std::string & path, int n_ctx, int threads, const std::string & npu_devs) {
    llama_model_params mp = llama_model_default_params();
    mp.load_mode = LLAMA_LOAD_MODE_NONE;  // as the Snapdragon docs run it (--load-mode none)
    std::vector<ggml_backend_dev_t> devs;
    if (p.name == "cpu") {
        mp.n_gpu_layers = 0;
    } else if (p.name == "gpu") {
        ggml_backend_dev_t d = dev_by_prefix("GPUOpenCL");
        if (!d) { p.error = "no OpenCL device"; return false; }
        devs.push_back(d); mp.n_gpu_layers = -1;
    } else {
        std::stringstream ss(npu_devs); std::string n;
        while (std::getline(ss, n, ',')) {
            ggml_backend_dev_t d = dev_by_prefix(n.c_str());
            if (!d) { p.error = "no device " + n; return false; }
            devs.push_back(d);
        }
        mp.n_gpu_layers = -1;
    }
    devs.push_back(nullptr);  // CPU-only when the list holds nothing but the terminator
    mp.devices = devs.data();
    auto t0 = clk::now();
    p.model = llama_model_load_from_file(path.c_str(), mp);
    if (!p.model) { p.error = "model load failed"; return false; }
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = n_ctx; cp.n_batch = 512; cp.n_ubatch = 512;
    cp.n_threads = threads; cp.n_threads_batch = threads;
    cp.no_perf = true;
    // The CPU instance must stay on the CPU: without this the scheduler may ship a large batch
    // to any registered device that accepts it.
    if (p.name == "cpu") cp.op_offload = false;
    p.ctx = llama_init_from_model(p.model, cp);
    if (!p.ctx) { p.error = "context init failed"; return false; }
    p.load_ms = ms_since(t0);
    return true;
}

static std::vector<llama_token> tokenize(const llama_vocab * v, const std::string & text, bool add_special) {
    int n = -llama_tokenize(v, text.c_str(), text.size(), nullptr, 0, add_special, true);
    std::vector<llama_token> t(n);
    llama_tokenize(v, text.c_str(), text.size(), t.data(), n, add_special, true);
    return t;
}

static std::string render(const llama_model * m, const std::vector<Msg> & msgs, bool add_gen) {
    std::vector<llama_chat_message> cm;
    for (auto & x : msgs) cm.push_back({x.role.c_str(), x.content.c_str()});
    const char * tmpl = llama_model_chat_template(m, nullptr);
    std::vector<char> buf(65536);
    int n = llama_chat_apply_template(tmpl, cm.data(), cm.size(), add_gen, buf.data(), buf.size());
    if (n > (int) buf.size()) { buf.resize(n); n = llama_chat_apply_template(tmpl, cm.data(), cm.size(), add_gen, buf.data(), buf.size()); }
    return n < 0 ? std::string() : std::string(buf.data(), n);  // empty: the caller treats it as an error
}

// The whole rendered prompt as tokens, with exactly one BOS where the vocabulary wants one and
// no EOS after the generation header.
static std::vector<llama_token> tokenize_prompt(const llama_vocab * v, const std::string & text) {
    std::vector<llama_token> t = tokenize(v, text, true);
    llama_token bos = llama_vocab_bos(v), eos = llama_vocab_eos(v);
    while (t.size() >= 2 && t[0] == bos && t[1] == bos) t.erase(t.begin());
    if (!t.empty() && t.back() == eos && llama_vocab_get_add_eos(v)) t.pop_back();
    return t;
}

// Kernels compile and graphs reserve on first use (the OpenCL backend compiled its attention
// kernels inside the first measured turn of the smoke run), so each context runs one full
// batch and a few single-token steps before anything is timed.
static bool warm(llama_context * ctx, const llama_vocab * v) {
    std::vector<llama_token> t(512, llama_vocab_bos(v) >= 0 ? llama_vocab_bos(v) : 1);
    llama_batch b = llama_batch_init(512, 0, 1);
    b.n_tokens = 512;
    for (int i = 0; i < 512; i++) { b.token[i] = t[i]; b.pos[i] = i; b.n_seq_id[i] = 1; b.seq_id[i][0] = 0; b.logits[i] = i == 511; }
    bool ok = llama_decode(ctx, b) == 0;
    for (int i = 0; i < 8 && ok; i++) {
        b.n_tokens = 1; b.token[0] = t[0]; b.pos[0] = 512 + i; b.logits[0] = 1;
        ok = llama_decode(ctx, b) == 0;
    }
    ok = ok && llama_get_logits_ith(ctx, -1) != nullptr;
    llama_batch_free(b);
    llama_memory_clear(llama_get_memory(ctx), true);
    return ok;
}

static long read_long(const std::string & path) {
    std::ifstream f(path); long v = -1; f >> v; return v;
}

// MemAvailable in MB, or -1.
static long mem_available_mb() {
    std::ifstream f("/proc/meminfo"); std::string k, unit; long v;
    while (f >> k >> v >> unit) if (k == "MemAvailable:") return v / 1024;
    return -1;
}

// Android's thermal HAL, read the way an unprivileged shell can: dumpsys thermalservice. Only the
// "Current temperatures from HAL" block is read ("Cached" can be stale). AP is the application
// processor's sensor and SKIN the case on Samsung's HAL. Each CPU policy's ceiling against its
// hardware top is the CPU throttle; the Adreno ceiling is max_gpuclk against the top of
// gpu_available_frequencies, where a shell may read them (-1 where it cannot).
struct Heat { double ap = -1, skin = -1; int status = -1; long gpu_max_khz = -1, gpu_top_khz = -1, gpu_cur_khz = -1;
    std::string cpu_max_mhz; bool cpu_capped = false; bool cpu_unreadable = false;
    bool valid() const { return ap >= 0 && skin >= 0 && status >= 0 && !cpu_max_mhz.empty() && !cpu_unreadable; } };
static std::string run_cmd(const char * cmd) {
    std::string out; FILE * f = popen(cmd, "r");
    if (!f) return out;
    char buf[4096]; size_t n;
    while ((n = fread(buf, 1, sizeof buf, f)) > 0) out.append(buf, n);
    pclose(f);
    return out;
}
static long gpu_top_khz() {
    std::ifstream f("/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies"); long v, top = -1;
    while (f >> v) top = std::max(top, v / 1000);
    return top;
}
static Heat heat(bool hal = true) {
    Heat h;
    std::string d = hal ? run_cmd("dumpsys thermalservice 2>/dev/null") : std::string();
    size_t st = d.find("Thermal Status: ");
    if (st != std::string::npos) h.status = atoi(d.c_str() + st + 16);
    size_t cur = d.find("Current temperatures from HAL");
    if (cur != std::string::npos) {
        size_t endb = d.find("Current cooling devices", cur);
        std::string blk = d.substr(cur, endb == std::string::npos ? std::string::npos : endb - cur);
        size_t i = 0;
        while ((i = blk.find("mValue=", i)) != std::string::npos) {
            double v = atof(blk.c_str() + i + 7);
            size_t nm = blk.find("mName=", i), e = blk.find_first_of(",}", nm);
            std::string name = nm == std::string::npos ? "" : blk.substr(nm + 6, e - nm - 6);
            if (name == "AP") h.ap = v; else if (name == "SKIN") h.skin = v;
            i = nm == std::string::npos ? i + 7 : nm;
        }
    }
    for (int c = 0; c < 8; c++) {
        std::string base = "/sys/devices/system/cpu/cpufreq/policy" + std::to_string(c);
        long cap = read_long(base + "/scaling_max_freq"), top = read_long(base + "/cpuinfo_max_freq");
        // A policy directory exists only for a cluster's first CPU; one that exists must read.
        if (std::ifstream(base + "/related_cpus") && (cap <= 0 || top <= 0)) h.cpu_unreadable = true;
        if (cap > 0 && top > 0 && cap < top) h.cpu_capped = true;
        if (cap > 0) h.cpu_max_mhz += (h.cpu_max_mhz.empty() ? "" : ",") + std::to_string(c) + ":" + std::to_string(cap / 1000) + "/" + std::to_string(top / 1000);
    }
    static const long top = gpu_top_khz();
    h.gpu_top_khz = top;
    long mx = read_long("/sys/class/kgsl/kgsl-3d0/max_gpuclk"), cu = read_long("/sys/class/kgsl/kgsl-3d0/gpuclk");
    h.gpu_max_khz = mx > 0 ? mx / 1000 : -1;
    h.gpu_cur_khz = cu > 0 ? cu / 1000 : -1;
    return h;
}

// The CPU pause switches, named by core mask (thermal-pause-3C pauses cores 2 to 5, thermal-pause-C0
// cores 6 and 7 on SM8850): while they are on, six decode threads crowd onto the two cores left (r19).
// Both or none: one found alone would let a pause on the other go unseen.
static std::vector<std::string> pause_switches() {
    std::vector<std::string> out;
    for (int c = 0; c < 400; c++) {
        std::string base = "/sys/class/thermal/cooling_device" + std::to_string(c);
        std::ifstream ft(base + "/type");
        if (!ft) continue;
        std::string type; ft >> type;
        if (type == "thermal-pause-C0" || type == "thermal-pause-3C") out.push_back(base + "/cur_state");
    }
    if (out.size() != 2) out.clear();
    return out;
}
// 1 paused, 0 not, -1 a switch could not be read (never taken as "not paused").
static int cpu_paused(const std::vector<std::string> & sw) {
    int any = 0;
    for (auto & s : sw) { long v = read_long(s); if (v < 0) return -1; if (v > 0) any = 1; }
    return any;
}

// AP and SKIN at or under the limit, thermal status 0 and no CPU ceiling lowered: the tablet's gate.
static bool gate_ok(const Heat & h, double limit_c) {
    return h.valid() && h.ap <= limit_c && h.skin <= limit_c && h.status == 0 && !h.cpu_capped;
}
static double cool_down(double limit_c, int max_s, Heat & at_start) {
    auto t0 = clk::now();
    while (true) {
        at_start = heat();
        if (gate_ok(at_start, limit_c) || ms_since(t0) / 1000 > max_s) return ms_since(t0) / 1000;
        std::this_thread::sleep_for(std::chrono::seconds(5));
    }
}

// Every HAL temperature, name=value, as one string.
static std::string temps() {
    std::string d = run_cmd("dumpsys thermalservice 2>/dev/null"), out;
    size_t cur = d.find("Current temperatures from HAL");
    if (cur == std::string::npos) return out;
    size_t endb = d.find("Current cooling devices", cur), i = cur;
    while ((i = d.find("mValue=", i)) != std::string::npos && (endb == std::string::npos || i < endb)) {
        std::string v = d.substr(i + 7, d.find(',', i) - i - 7);
        size_t nm = d.find("mName=", i), e = d.find_first_of(",}", nm);
        out += d.substr(nm + 6, e - nm - 6) + "=" + v + " ";
        i = nm;
    }
    return out;
}

// Feeds tokens in n_batch pieces; logits only for the last one.
static bool feed(llama_context * ctx, const std::vector<llama_token> & toks, int pos0) {
    int nb = llama_n_batch(ctx);
    llama_batch b = llama_batch_init(nb, 0, 1);
    for (size_t i = 0; i < toks.size(); i += nb) {
        b.n_tokens = 0;
        for (size_t j = i; j < toks.size() && j < i + nb; j++) {
            b.token[b.n_tokens] = toks[j]; b.pos[b.n_tokens] = pos0 + (int) j;
            b.n_seq_id[b.n_tokens] = 1; b.seq_id[b.n_tokens][0] = 0;
            b.logits[b.n_tokens] = (j == toks.size() - 1);
            b.n_tokens++;
        }
        if (llama_decode(ctx, b) != 0) { llama_batch_free(b); return false; }
    }
    llama_batch_free(b);
    return true;
}

static llama_token argmax(llama_context * ctx, int n_vocab) {
    const float * l = llama_get_logits_ith(ctx, -1);
    int best = 0;
    for (int i = 1; i < n_vocab; i++) if (l[i] > l[best]) best = i;
    return best;
}

int main(int argc, char ** argv) {
    std::string model_path, conv_path, out_path, cells_arg = "cpu:cpu", npu_devs = "HTP0", label;
    double cool_limit = 0; int cool_max_s = 900;
    bool gen_ref = false;
    int gen = 128, n_ctx = 8192, threads = 6, max_turns = 8, max_convs = 1000, paused_threads = 0;
    // --window: end each conversation where the MediaTek tablet's 2,048-token NPU window did (with
    // --pad-batch, the prompt graph's 128-token padding), so both read the same turns.
    // --mem-margin-mb: MemAvailable needed beyond 1.2 x the GGUF before each copy loads.
    int window = 0, pad_batch = 1; long mem_margin_mb = 1024;
    for (int i = 1; i < argc; i++) {
        std::string a = argv[i];
        auto next = [&]() { return std::string(argv[++i]); };
        if (a == "-m") model_path = next(); else if (a == "-c") conv_path = next();
        else if (a == "-o") out_path = next(); else if (a == "--cells") cells_arg = next();
        else if (a == "--gen") gen = std::stoi(next()); else if (a == "--ctx") n_ctx = std::stoi(next());
        else if (a == "--threads") threads = std::stoi(next()); else if (a == "--max-turns") max_turns = std::stoi(next());
        else if (a == "--max-convs") max_convs = std::stoi(next()); else if (a == "--npu-devices") npu_devs = next();
        else if (a == "--label") label = next(); else if (a == "--gen-ref") gen_ref = true;
        else if (a == "--cool") cool_limit = std::stod(next());
        else if (a == "--cool-max") cool_max_s = std::stoi(next());
        else if (a == "--paused-threads") paused_threads = std::stoi(next());
        else if (a == "--window") window = std::stoi(next());
        else if (a == "--pad-batch") pad_batch = std::max(1, std::stoi(next()));
        else if (a == "--mem-margin-mb") mem_margin_mb = std::stol(next());
    }
    // Read once: the switches' paths do not move while the phone is up.
    if (paused_threads < 0 || paused_threads > threads) { fprintf(stderr, "--paused-threads must be 1 to --threads\n"); return 2; }
    const std::vector<std::string> switches = paused_threads > 0 ? pause_switches() : std::vector<std::string>();
    if (paused_threads > 0 && switches.empty()) { fprintf(stderr, "--paused-threads: the two CPU pause switches were not found\n"); return 2; }
    llama_backend_init();
    ggml_backend_load_all();

    std::vector<std::pair<std::string, std::string>> cells;
    std::map<std::string, Proc> procs;
    { std::stringstream ss(cells_arg); std::string c;
      while (std::getline(ss, c, ',')) { auto k = c.find(':'); cells.push_back({c.substr(0, k), c.substr(k + 1)});
        procs[c.substr(0, k)].name = c.substr(0, k); procs[c.substr(k + 1)].name = c.substr(k + 1); } }

    FILE * out = fopen(out_path.c_str(), "a");
    if (!out) { fprintf(stderr, "cannot write %s\n", out_path.c_str()); return 2; }
    int failures = 0;
    const int ctx_used = window > 0 ? std::min(n_ctx, window + 256) : n_ctx;
    long gguf_mb = -1;
    { std::ifstream f(model_path, std::ios::binary | std::ios::ate); if (f) gguf_mb = (long) (f.tellg() / (1024 * 1024)); }
    for (auto & [name, p] : procs) {
        // Running out of memory restarted the MediaTek tablet twice (its NPU memory was kernel
        // memory); refuse a copy that would leave less than the margin.
        const long avail = mem_available_mb(), need = gguf_mb < 0 ? -1 : (long) (gguf_mb * 1.2) + mem_margin_mb;
        if (avail < 0 || need < 0) p.error = "MemAvailable or the GGUF size unreadable: not loading without a memory check";
        else if (avail < need) p.error = "insufficient memory: " + std::to_string(avail) + " MB available, " + std::to_string(need) + " MB needed";
        else open_proc(p, model_path, ctx_used, threads, npu_devs);
        fprintf(out, "{\"kind\":\"load\",\"label\":\"%s\",\"model\":\"%s\",\"proc\":\"%s\",\"load_ms\":%.1f,\"ctx\":%d,\"avail_before_mb\":%ld,\"avail_after_mb\":%ld,\"error\":\"%s\"}\n",
                esc(label).c_str(), esc(model_path).c_str(), name.c_str(), p.load_ms, ctx_used, avail, mem_available_mb(), esc(p.error).c_str());
        fflush(out);
        fprintf(stderr, "loaded %s: %s %.0f ms\n", name.c_str(), p.error.empty() ? "ok" : p.error.c_str(), p.load_ms);
        if (p.error.empty() && !warm(p.ctx, llama_model_get_vocab(p.model))) {
            p.error = "warm-up failed";
            fprintf(out, "{\"kind\":\"load\",\"label\":\"%s\",\"model\":\"%s\",\"proc\":\"%s\",\"error\":\"warm-up failed\"}\n",
                    esc(label).c_str(), esc(model_path).c_str(), name.c_str());
        }
    }

    std::vector<Conv> convs;
    { std::ifstream f(conv_path); std::string line;
      if (!f) { fprintf(stderr, "cannot read %s\n", conv_path.c_str()); return 2; }
      while (std::getline(f, line) && (int) convs.size() < max_convs) if (!line.empty()) convs.push_back(parse_conv(line)); }

    for (auto & [pn, dn] : cells) {
        Proc & P = procs[pn]; Proc & D = procs[dn];
        if (!P.error.empty() || !D.error.empty()) {
            fprintf(out, "{\"kind\":\"cell_skipped\",\"label\":\"%s\",\"model\":\"%s\",\"prefill\":\"%s\",\"decode\":\"%s\",\"error\":\"%s\"}\n",
                    esc(label).c_str(), esc(model_path).c_str(), pn.c_str(), dn.c_str(), esc(P.error + " " + D.error).c_str());
            fflush(out); failures++; continue;
        }
        Heat h0; double waited = cool_limit > 0 ? cool_down(cool_limit, cool_max_s, h0) : (h0 = heat(), 0.0);
        fprintf(out, "{\"kind\":\"cooled\",\"label\":\"%s\",\"model\":\"%s\",\"prefill\":\"%s\",\"decode\":\"%s\",\"limit_c\":%.0f,\"max_s\":%d,\"gate\":\"%s\",\"waited_s\":%.0f,\"ap_c\":%.1f,\"skin_c\":%.1f,\"status\":%d,\"gpu_max_mhz\":%ld,\"gpu_top_mhz\":%ld,\"cpu_max_mhz\":\"%s\"}\n",
                esc(label).c_str(), esc(model_path).c_str(), pn.c_str(), dn.c_str(), cool_limit, cool_max_s, cool_limit <= 0 ? "off" : !h0.valid() ? "read_error" : gate_ok(h0, cool_limit) ? "passed" : "timed_out", waited, h0.ap, h0.skin, h0.status, h0.gpu_max_khz / 1000, h0.gpu_top_khz / 1000, h0.cpu_max_mhz.c_str());
        fprintf(out, "{\"kind\":\"cell\",\"when\":\"start\",\"label\":\"%s\",\"model\":\"%s\",\"prefill\":\"%s\",\"decode\":\"%s\",\"gen\":%d,\"ctx\":%d,\"threads\":%d,\"max_turns\":%d,\"npu_devices\":\"%s\",\"temps\":\"%s\"}\n",
                esc(label).c_str(), esc(model_path).c_str(), pn.c_str(), dn.c_str(), gen, n_ctx, threads, max_turns, npu_devs.c_str(), temps().c_str());
        fflush(out);
        const llama_vocab * vocab = llama_model_get_vocab(P.model);
        int n_vocab = llama_vocab_n_tokens(vocab);
        std::vector<uint8_t> state, snap;
        auto row_err = [&](const Conv & conv, int turn, const std::string & e) {
            fprintf(out, "{\"kind\":\"turn_error\",\"label\":\"%s\",\"model\":\"%s\",\"prefill\":\"%s\",\"decode\":\"%s\",\"conv\":\"%s\",\"turn\":%d,\"error\":\"%s\"}\n",
                    esc(label).c_str(), esc(model_path).c_str(), pn.c_str(), dn.c_str(), esc(conv.id).c_str(), turn, esc(e).c_str());
            fflush(out);
            failures++;
        };
        const int limit = window > 0 ? std::min(n_ctx, window) : n_ctx;
        auto span = [&](size_t n) { return (int) ((n + pad_batch - 1) / pad_batch * pad_batch); };
        for (auto & conv : convs) {
            llama_memory_clear(llama_get_memory(P.ctx), true);
            if (&D != &P) llama_memory_clear(llama_get_memory(D.ctx), true);
            std::vector<Msg> hist;
            std::vector<llama_token> evaluated;  // exactly what the prefill context holds
            int turn = 0;
            for (size_t mi = 0; mi < conv.msgs.size() && turn < max_turns; mi++) {
                hist.push_back(conv.msgs[mi]);
                if (conv.msgs[mi].role != "user") continue;
                std::string full = render(P.model, hist, true);
                if (full.empty()) { row_err(conv, turn, "chat template failed"); break; }
                std::vector<llama_token> all = tokenize_prompt(vocab, full);
                // Teacher forcing only works if the new prompt extends what is cached, token for token.
                if (all.size() <= evaluated.size() || !std::equal(evaluated.begin(), evaluated.end(), all.begin())) {
                    row_err(conv, turn, "token prefix changed"); break;
                }
                std::vector<llama_token> toks(all.begin() + evaluated.size(), all.end());
                const int pos = (int) evaluated.size();
                int ref_tokens = -1, turn_gen = gen;
                if (gen_ref) {
                    // The reply as plain text, without template markup; +1 stands for its end of turn.
                    if (mi + 1 < conv.msgs.size() && conv.msgs[mi + 1].role == "assistant")
                        ref_tokens = (int) tokenize(vocab, conv.msgs[mi + 1].content, false).size() + 1;
                    turn_gen = ref_tokens < 0 ? std::min(gen, 64) : std::max(2, std::min(gen, ref_tokens));
                }
                // The tablet's rule, so both phones end a conversation at the same turn.
                if (pos + std::max((int) toks.size(), span(toks.size())) + turn_gen + 8 > limit) break;
                std::string err;

                // On the same processor a hybrid cannot drop the decoded tokens afterwards, so its
                // state is saved before the prefill instead, outside both timed intervals.
                const bool same = &D == &P;
                // Only a model that cannot drop tokens needs the snapshot; a transformer drops the
                // decoded tokens with seq_rm, so nothing touches its state before the timed prefill.
                const bool needs_snap = same && (llama_model_is_hybrid(P.model) || llama_model_is_recurrent(P.model));
                snap.clear();
                if (needs_snap) {
                    snap.resize(llama_state_seq_get_size(P.ctx, 0));
                    if (snap.empty() || llama_state_seq_get_data(P.ctx, snap.data(), snap.size(), 0) != snap.size()) {
                        row_err(conv, turn, "snapshot failed"); break;
                    }
                }

                Heat hb = heat();  // before the turn, outside its timing
                long wall_ms = (long) std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
                auto t_turn = clk::now();
                auto t0 = clk::now();
                bool ok = feed(P.ctx, toks, pos);
                const float * lg = ok ? llama_get_logits_ith(P.ctx, -1) : nullptr;
                double prompt_ms = ms_since(t0);  // prompt evaluated and synchronised
                llama_token first = 0;
                if (lg) first = argmax(P.ctx, n_vocab); else { ok = false; err = "prefill failed"; }
                double prefill_ms = ms_since(t0);  // plus choosing the first token

                double get_ms = 0, set_ms = 0; size_t xfer_bytes = 0;
                llama_context * dctx = D.ctx;
                if (ok && !same) {
                    xfer_bytes = llama_state_seq_get_size(P.ctx, 0);
                    if (state.size() < xfer_bytes) state.resize(xfer_bytes);
                    llama_memory_clear(llama_get_memory(D.ctx), true);
                    auto tg = clk::now();
                    size_t wrote = llama_state_seq_get_data(P.ctx, state.data(), xfer_bytes, 0);
                    get_ms = ms_since(tg);
                    auto ts = clk::now();
                    size_t read = wrote == xfer_bytes ? llama_state_seq_set_data(D.ctx, state.data(), xfer_bytes, 0) : 0;
                    set_ms = ms_since(ts);
                    if (xfer_bytes == 0 || wrote != xfer_bytes || read != xfer_bytes) { ok = false; err = "state transfer failed"; }
                }

                std::vector<llama_token> out_toks;
                std::vector<double> step_ms;
                std::vector<int> step_threads;
                double decode_ms = 0; int steps = 0;
                // Only a CPU decoder has threads to change; the switch is read before each step and
                // inside its timing, so a cell that switches pays for the reads it makes.
                const bool switching = paused_threads > 0 && dn == "cpu";
                int cur_threads = -1;
                if (ok) {
                    out_toks.push_back(first);
                    llama_batch b = llama_batch_init(1, 0, 1);
                    auto td = clk::now();
                    int p2 = pos + (int) toks.size();
                    for (int g = 1; g < turn_gen; g++) {
                        auto tstep = clk::now();
                        if (switching) {
                            int p = cpu_paused(switches);
                            if (p < 0) { ok = false; err = "pause switch unreadable"; break; }
                            int want = p ? paused_threads : threads;
                            if (want != cur_threads) { llama_set_n_threads(dctx, want, want); cur_threads = want; }
                        }
                        b.n_tokens = 1; b.token[0] = out_toks.back(); b.pos[0] = p2++;
                        b.n_seq_id[0] = 1; b.seq_id[0][0] = 0; b.logits[0] = 1;
                        if (llama_decode(dctx, b) != 0) { ok = false; err = "decode failed"; break; }
                        out_toks.push_back(argmax(dctx, n_vocab));
                        if (gen_ref) step_ms.push_back(ms_since(tstep));
                        if (switching) step_threads.push_back(cur_threads);  // after success, so it lines up with step_ms
                        steps++;
                    }
                    decode_ms = ms_since(td);
                    llama_batch_free(b);
                    // The next turn's prefill and warm-up use the cell's own count again.
                    if (switching && cur_threads != threads) llama_set_n_threads(dctx, threads, threads);
                }
                double turn_ms = ms_since(t_turn);  // prefill, first token, handoff (with its clear) and decode
                Heat ht = heat();  // at the end of the turn, outside its timing
                if (same && ok) {
                    // Put the context back to the dataset's history: drop the decoded tokens, or for
                    // a hybrid restore the snapshot and read this turn's prompt again (untimed).
                    llama_memory_t mem = llama_get_memory(P.ctx);
                    if (!llama_memory_seq_rm(mem, 0, pos + (int) toks.size(), -1)) {
                        if (snap.empty()) { ok = false; err = "cannot drop decoded tokens"; }
                        llama_memory_clear(mem, true);
                        if (llama_state_seq_set_data(P.ctx, snap.data(), snap.size(), 0) != snap.size() || !feed(P.ctx, toks, pos)) {
                            ok = false; err = "restore failed";
                        }
                    }
                    if (ok && llama_memory_seq_pos_max(mem, 0) != pos + (int) toks.size() - 1) { ok = false; err = "restore position wrong"; }
                }

                std::string step_list;
                for (size_t k = 0; k < step_ms.size(); k++) {
                    char buf[32]; snprintf(buf, sizeof buf, "%s%.2f", k ? "," : "", step_ms[k]); step_list += buf;
                }
                std::string thr_list;
                if (switching) {
                    thr_list = "\"step_threads\":[";
                    for (size_t k = 0; k < step_threads.size(); k++) thr_list += (k ? "," : "") + std::to_string(step_threads[k]);
                    thr_list += "],";
                }
                std::string tok_list;
                for (size_t k = 0; k < out_toks.size(); k++) tok_list += (k ? "," : "") + std::to_string(out_toks[k]);
                int eog_at = -1;
                for (size_t k = 0; k < out_toks.size(); k++) if (llama_vocab_is_eog(vocab, out_toks[k])) { eog_at = (int) k; break; }
                std::string text;
                for (int k = 0; k < (int) out_toks.size() && (eog_at < 0 || k < eog_at) && k < 48; k++) {
                    char buf[256]; int n = llama_token_to_piece(vocab, out_toks[k], buf, sizeof buf, 0, false);
                    if (n > 0) text.append(buf, n);
                }
                fprintf(out,
                        "{\"kind\":\"turn\",\"label\":\"%s\",\"model\":\"%s\",\"prefill\":\"%s\",\"decode\":\"%s\",\"conv\":\"%s\",\"source\":\"%s\","
                        "\"turn\":%d,\"pos\":%d,\"new_tokens\":%zu,\"prompt_ms\":%.2f,\"prefill_ms\":%.2f,\"xfer_get_ms\":%.2f,\"xfer_set_ms\":%.2f,\"xfer_bytes\":%zu,"
                        "\"output_tokens\":%zu,\"decode_steps\":%d,\"decode_ms\":%.2f,\"turn_ms\":%.2f,\"wall_ms\":%ld,\"before_ap_c\":%.1f,\"before_skin_c\":%.1f,\"before_status\":%d,\"before_gpu_max_mhz\":%ld,\"before_cpu_capped\":%d,\"cpu_capped\":%d,\"before_cpu_max_mhz\":\"%s\",\"cpu_max_mhz\":\"%s\",\"ap_c\":%.1f,\"skin_c\":%.1f,\"status\":%d,\"gpu_max_mhz\":%ld,\"gpu_cur_mhz\":%ld,\"eog_at\":%d,\"error\":\"%s\",\"turn_gen\":%d,\"ref_tokens\":%d,%s\"step_ms\":[%s],\"tokens\":[%s],\"text\":\"%s\"}\n",
                        esc(label).c_str(), esc(model_path).c_str(), pn.c_str(), dn.c_str(), esc(conv.id).c_str(), esc(conv.source).c_str(),
                        turn, pos, toks.size(), prompt_ms, prefill_ms, get_ms, set_ms, xfer_bytes, out_toks.size(), steps, decode_ms, turn_ms, wall_ms, hb.ap, hb.skin, hb.status, hb.gpu_max_khz / 1000, (int) hb.cpu_capped, (int) ht.cpu_capped, hb.cpu_max_mhz.c_str(), ht.cpu_max_mhz.c_str(), ht.ap, ht.skin, ht.status, ht.gpu_max_khz / 1000, ht.gpu_cur_khz / 1000, eog_at,
                        esc(err).c_str(), turn_gen, ref_tokens, thr_list.c_str(), step_list.c_str(), tok_list.c_str(), esc(text).c_str());
                fflush(out);
                fprintf(stderr, "%s:%s %s t%d pos %d new %zu prompt %.0f ms xfer %.1f+%.1f ms decode %d steps in %.0f ms %s\n",
                        pn.c_str(), dn.c_str(), conv.id.c_str(), turn, pos, toks.size(), prompt_ms, get_ms, set_ms, steps, decode_ms, err.c_str());
                if (!ok) { failures++; break; }
                evaluated = all;
                turn++;
            }
        }
    }
    fprintf(out, "{\"kind\":\"done\",\"label\":\"%s\",\"model\":\"%s\",\"failures\":%d,\"temps\":\"%s\"}\n",
            esc(label).c_str(), esc(model_path).c_str(), failures, temps().c_str());
    fclose(out);
    for (auto & [n, p] : procs) { if (p.ctx) llama_free(p.ctx); if (p.model) llama_model_free(p.model); }
    return failures ? 1 : 0;
}
