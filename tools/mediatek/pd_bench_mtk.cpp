// Prefill on one processor, decode on another, over real conversations replayed turn by turn:
// the MediaTek port of tools/snapdragon/pd_bench.cpp. The CPU and the Mali GPU (Vulkan) run in
// one llama.cpp build; a streamed, unrooted Dimensity unit has no readable thermal zones or
// cooling devices, so heat comes from Android's thermal HAL (dumpsys thermalservice) and the
// throttle state from what the shell can read: each CPU policy's ceiling and the GPU's clocks.
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
#ifdef PD_NPU
#include "npu_engine.h"
#endif

#include <algorithm>
#include <cmath>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <map>
#include <sstream>
#include <string>
#include <thread>
#include <vector>
#include <unistd.h>

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

// The NPU half, when this binary is built without MediaTek's runtime: never opens.
#ifndef PD_NPU
namespace npu {
enum class MemKind { Kv, Iswa, Hybrid };
struct KvLayout { MemKind kind = MemKind::Kv; };
struct Engine {
    std::string error = "built without PD_NPU"; bool attention_only = false, rope_permute = false; size_t vocab = 0, window = 0, n_swa = 0;
    bool open(const std::string &) { return false; }
    size_t pos() const { return 0; } void clear() {}
    int64_t prefill(const std::vector<int32_t> &) { return -1; } int64_t step(int32_t) { return -1; }
    const void * prefill_logits(const std::vector<int32_t> &) { return nullptr; } int64_t argmax(const void *) const { return -1; }
    size_t prefill_span(size_t n) const { return n; } size_t state_bytes(size_t) const { return 0; }
    bool keep_first(size_t) { return false; } void save_conv() {} void restore_conv() {}
    bool to_llama(const KvLayout &, size_t, std::vector<uint8_t> &) { return false; }
    bool from_llama(const uint8_t *, size_t, MemKind) { return false; }
};
static bool parse_layout_only(const std::vector<uint8_t> &, MemKind, KvLayout &, std::string & e) { e = "built without PD_NPU"; return false; }
}
#else
namespace npu {
static bool parse_layout_only(const std::vector<uint8_t> & b, MemKind kind, KvLayout & lay, std::string & e) {
    return parse_state(b.data(), b.size(), kind, lay, e);
}
}
#endif

struct Proc {
    std::string name;            // cpu, gpu, npu
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    npu::Engine * npu = nullptr; // set for the NPU, which has no llama.cpp model or context
    npu::MemKind kind = npu::MemKind::Kv;  // what memory llama.cpp keeps for this model
    npu::KvLayout layout;        // this context's own state layout, for a handoff into it
    std::string layout_error;    // why a handoff into this context cannot be built, if it cannot
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

static bool open_proc(Proc & p, const std::string & path, int n_ctx, int threads, const std::string & npu_devs, const std::string & npu_dir) {
    if (p.name == "npu") {
        auto t0 = clk::now();
        p.npu = new npu::Engine();
        if (!p.npu->open(npu_dir)) { p.error = "npu: " + p.npu->error; return false; }
        p.load_ms = ms_since(t0);
        return true;
    }
    (void) npu_devs;
    llama_model_params mp = llama_model_default_params();
    mp.load_mode = LLAMA_LOAD_MODE_NONE;  // as the Snapdragon docs run it (--load-mode none)
    std::vector<ggml_backend_dev_t> devs;
    if (p.name == "cpu") {
        mp.n_gpu_layers = 0;
    } else if (p.name == "gpu") {
        ggml_backend_dev_t d = dev_by_prefix("Vulkan");
        if (!d) { p.error = "no Vulkan device"; return false; }
        devs.push_back(d); mp.n_gpu_layers = -1;
    } else {
        p.error = "unknown processor " + p.name; return false;
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

// Sizes of an NPU export's model files (chunks and embedding) named in its runner.txt, in MB.
static long npu_files_mb(const std::string & dir, long & files) {
    std::ifstream f(dir + "/runner.txt"); std::string line; files = 0;
    while (std::getline(f, line)) {
        auto k = line.find('=');
        if (k == std::string::npos) continue;
        std::string key = line.substr(0, k), v = line.substr(k + 1);
        if (key != "token_embedding_path" && key != "model_package_paths" && key != "prompt_model_paths" && key != "gen_model_paths") continue;
        std::stringstream ss(v); std::string x;
        while (std::getline(ss, x, ',')) { std::ifstream g(dir + "/" + x, std::ios::binary | std::ios::ate); if (g) files += (long) g.tellg() / (1024 * 1024); }
    }
    return 0;
}

// MemAvailable in MB, or -1.
static long mem_available_mb() {
    std::ifstream f("/proc/meminfo"); std::string k; long v; std::string unit;
    while (f >> k >> v >> unit) if (k == "MemAvailable:") return v / 1024;
    return -1;
}

// Android's thermal HAL, read the way an unprivileged shell can: dumpsys thermalservice. Its
// "Cached temperatures" block can be stale, so only "Current temperatures from HAL" is read.
// AP is the application-processor sensor and SKIN the case; the HAL gives no per-core, GPU or
// NPU zone on this unit. Each CPU policy's ceiling against its hardware top is the CPU throttle,
// and the GPU's ceiling is /sys/kernel/gpu/gpu_max_clock against the top of its frequency table.
struct Heat { double ap = -1, skin = -1; int status = -1; long gpu_max_khz = -1, gpu_top_khz = -1, gpu_cur_khz = -1;
    std::string cpu_max_mhz; bool cpu_capped = false; bool cpu_unreadable = false;
    // Valid only when the HAL answered and every CPU policy's ceiling and top were read: an
    // unreadable one must not pass as uncapped (Codex review, round two).
    bool valid() const { return ap >= 0 && skin >= 0 && status >= 0 && !cpu_max_mhz.empty() && !cpu_unreadable; }
    bool gpu_capped() const { return gpu_max_khz > 0 && gpu_top_khz > 0 && gpu_max_khz < gpu_top_khz; } };
static std::string run_cmd(const char * cmd) {
    std::string out; FILE * f = popen(cmd, "r");
    if (!f) return out;
    char buf[4096]; size_t n;
    while ((n = fread(buf, 1, sizeof buf, f)) > 0) out.append(buf, n);
    pclose(f);
    return out;
}
static long gpu_top_khz() {
    std::ifstream f("/sys/kernel/gpu/gpu_freq_table"); long v, top = -1;
    while (f >> v) top = std::max(top, v);
    return top;
}
// hal = false skips the thermal HAL (about a second of dumpsys) and reads only the clocks.
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
    h.gpu_max_khz = read_long("/sys/kernel/gpu/gpu_max_clock");
    h.gpu_cur_khz = read_long("/sys/kernel/gpu/gpu_clock");
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

// AP and SKIN at or under the limit, thermal status 0 and no CPU ceiling lowered. The GPU ceiling
// is recorded and not gated on: what it means on this unit is measured, not assumed.
static bool gate_ok(const Heat & h, double limit_c) {
    return h.valid() && h.ap <= limit_c && h.skin <= limit_c && h.status == 0 && !h.cpu_capped;
}
// Waits until the gate passes, or the time runs out.
static double cool_down(double limit_c, int max_s, Heat & at_start) {
    auto t0 = clk::now();
    while (true) {
        at_start = heat();
        bool cool = gate_ok(at_start, limit_c);
        if (cool || ms_since(t0) / 1000 > max_s) return ms_since(t0) / 1000;
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

// The NPU reads a run of a token the way warm() does for llama.cpp: graphs and buffers are
// touched once before anything is timed.
static bool warm_npu(npu::Engine & e) {
    std::vector<int32_t> t(512, 0);
    bool ok = e.prefill(t) >= 0;
    for (int i = 0; i < 8 && ok; i++) ok = e.step(0) >= 0;
    e.clear();
    return ok;
}

// A llama.cpp context's own state layout (V transposed or not, types, row sizes), read from a
// two-token state of its own, so a state built from the NPU's cache matches it exactly.
static void probe_layout(Proc & p) {
    const llama_vocab * v = llama_model_get_vocab(p.model);
    std::vector<llama_token> t = {llama_vocab_bos(v) >= 0 ? llama_vocab_bos(v) : 1, 1};
    if (!feed(p.ctx, t, 0)) { p.layout_error = "probe feed failed"; return; }
    std::vector<uint8_t> b(llama_state_seq_get_size(p.ctx, 0));
    llama_state_seq_get_data(p.ctx, b.data(), b.size(), 0);
    llama_memory_clear(llama_get_memory(p.ctx), true);
    std::string e;
    p.kind = llama_model_is_hybrid(p.model) ? npu::MemKind::Hybrid : llama_model_n_swa(p.model) > 0 ? npu::MemKind::Iswa : npu::MemKind::Kv;
    if (llama_model_is_recurrent(p.model)) { p.layout_error = "a purely recurrent model"; return; }
    if (!npu::parse_layout_only(b, p.kind, p.layout, e)) p.layout_error = e;
}

int main(int argc, char ** argv) {
    std::string model_path, conv_path, out_path, cells_arg = "cpu:cpu", label, npu_dir;
    double cool_limit = 0; int cool_max_s = 900;
    bool gen_ref = false, check_handoff = false;
    int check_conv = 0, check_turn = 0;  // which conversation and user turn the check reads up to
    bool check_incremental = false;      // the NPU reads the history turn by turn, as a cell does
    int gen = 128, n_ctx = 8192, threads = 6, max_turns = 8, max_convs = 1000, paused_threads = 0;
    // One turn limit for every cell of a model, whichever processors a process holds: the NPU
    // export's window and prompt-graph batch, passed to the llama.cpp-only processes too, so all
    // nine cells read the same turns (Codex review, 2026-11-16).
    int window = 0, pad_batch = 1;
    long npu_need_mb = -1, mem_margin_mb = 1024;
    for (int i = 1; i < argc; i++) {
        std::string a = argv[i];
        auto next = [&]() { return std::string(argv[++i]); };
        if (a == "-m") model_path = next(); else if (a == "-c") conv_path = next();
        else if (a == "-o") out_path = next(); else if (a == "--cells") cells_arg = next();
        else if (a == "--gen") gen = std::stoi(next()); else if (a == "--ctx") n_ctx = std::stoi(next());
        else if (a == "--threads") threads = std::stoi(next()); else if (a == "--max-turns") max_turns = std::stoi(next());
        else if (a == "--max-convs") max_convs = std::stoi(next()); else if (a == "--npu-dir") npu_dir = next();
        else if (a == "--label") label = next(); else if (a == "--gen-ref") gen_ref = true;
        else if (a == "--cool") cool_limit = std::stod(next());
        else if (a == "--cool-max") cool_max_s = std::stoi(next());
        else if (a == "--paused-threads") paused_threads = std::stoi(next());
        else if (a == "--check-handoff") check_handoff = true;
        else if (a == "--check-conv") check_conv = std::stoi(next());
        else if (a == "--check-turn") check_turn = std::stoi(next());
        else if (a == "--check-incremental") check_incremental = true;
        else if (a == "--window") window = std::stoi(next());
        else if (a == "--pad-batch") pad_batch = std::max(1, std::stoi(next()));
        else if (a == "--npu-need-mb") npu_need_mb = std::stol(next());
        else if (a == "--mem-margin-mb") mem_margin_mb = std::stol(next());
        else { fprintf(stderr, "unknown argument %s\n", a.c_str()); return 2; }
    }
    if (paused_threads < 0 || paused_threads > threads) { fprintf(stderr, "--paused-threads must be 1 to --threads\n"); return 2; }
    const std::vector<std::string> switches = paused_threads > 0 ? pause_switches() : std::vector<std::string>();
    if (paused_threads > 0 && switches.empty()) { fprintf(stderr, "--paused-threads: the two CPU pause switches were not found\n"); return 2; }
    llama_backend_init();
    ggml_backend_load_all();

    // The chat template and tokenizer for every cell, NPU ones included, come from the GGUF:
    // one vocabulary-only load, so all cells read the same token ids.
    llama_model_params vp = llama_model_default_params();
    vp.vocab_only = true;
    llama_model * vmodel = llama_model_load_from_file(model_path.c_str(), vp);
    if (!vmodel) { fprintf(stderr, "cannot read the vocabulary of %s\n", model_path.c_str()); return 2; }
    const llama_vocab * vocab = llama_model_get_vocab(vmodel);
    const int n_vocab = llama_vocab_n_tokens(vocab);
    char arch[64] = {0};
    llama_model_meta_val_str(vmodel, "general.architecture", arch, sizeof arch);
    const bool rope_permute = std::string(arch) == "llama";

    std::vector<std::pair<std::string, std::string>> cells;
    std::map<std::string, Proc> procs;
    { std::stringstream ss(cells_arg); std::string c;
      while (std::getline(ss, c, ',')) { auto k = c.find(':'); cells.push_back({c.substr(0, k), c.substr(k + 1)});
        procs[c.substr(0, k)].name = c.substr(0, k); procs[c.substr(k + 1)].name = c.substr(k + 1); } }

    FILE * out = fopen(out_path.c_str(), "a");
    if (!out) { fprintf(stderr, "cannot write %s\n", out_path.c_str()); return 2; }
    int failures = 0;
    // The NPU loads first: at a 4k window MediaTek's runtime took 6.5 GB of kernel memory for
    // Qwen3 0.6B on the 12 GB Galaxy Tab S10+, and loading the GPU's copy after it ran the kernel
    // out of memory and panicked the tablet twice (2026-11-16). So the NPU takes its share first,
    // and a llama.cpp processor that would not fit in what is left is refused, not loaded.
    std::vector<std::string> order;
    if (procs.count("npu")) order.push_back("npu");
    for (auto & [name, p] : procs) if (name != "npu") order.push_back(name);
    long gguf_mb = 0;
    { std::ifstream g(model_path, std::ios::binary | std::ios::ate); if (g) gguf_mb = (long) g.tellg() / (1024 * 1024); }
    // With a common window the llama.cpp contexts need no more cells than it. The KV cache is
    // allocated for n_ctx while attention covers the cells in use (padded), so prefill and decode
    // should not change; clearing a destination before a handoff does scale with n_ctx, and is
    // timed on its own (clear_ms). Whether throughput matches the n_ctx 8192 runs is a measurement.
    const int ctx_used = window > 0 ? std::min(n_ctx, window + 256) : n_ctx;
    // The NPU's own load is guarded too. Without a measured figure (--npu-need-mb) the estimate is
    // the export's files plus 1 MB per window position and a margin: Llama 3.2 1B at 2k (2.4 GB of
    // files) took 3.8 GB, Qwen3 0.6B at 4k (1.3 GB of files) took 6.5 GB (Codex review, round two).
    if (procs.count("npu") && npu_need_mb < 0) {
        long files = 0;
        auto kv = npu_files_mb(npu_dir, files);
        npu_need_mb = files + (window > 0 ? window : 4096) + mem_margin_mb + kv;
    }
    for (auto & name : order) {
        Proc & p = procs[name];
        const long avail = mem_available_mb();
        const long need = name == "npu" ? npu_need_mb : gguf_mb * 12 / 10 + mem_margin_mb;
        if (avail < 0) {
            p.error = "MemAvailable unreadable: not loading without a memory check";
        } else if (need > 0 && avail < need) {
            p.error = "insufficient memory: " + std::to_string(avail) + " MB available, " + std::to_string(need) + " MB needed";
        } else {
            open_proc(p, model_path, ctx_used, threads, "", npu_dir);
        }
        if (p.npu) p.npu->rope_permute = rope_permute;
        fprintf(out, "{\"kind\":\"load\",\"label\":\"%s\",\"model\":\"%s\",\"proc\":\"%s\",\"load_ms\":%.1f,\"npu_dir\":\"%s\",\"ctx\":%d,\"avail_before_mb\":%ld,\"avail_after_mb\":%ld,\"error\":\"%s\"}\n",
                esc(label).c_str(), esc(model_path).c_str(), name.c_str(), p.load_ms, esc(p.npu ? npu_dir : "").c_str(), ctx_used, avail, mem_available_mb(), esc(p.error).c_str());
        fflush(out);
        fprintf(stderr, "loaded %s: %s %.0f ms\n", name.c_str(), p.error.empty() ? "ok" : p.error.c_str(), p.load_ms);
        bool warmed = p.error.empty() && (p.npu ? warm_npu(*p.npu) : warm(p.ctx, llama_model_get_vocab(p.model)));
        if (p.error.empty() && !warmed) {
            p.error = "warm-up failed";
            fprintf(out, "{\"kind\":\"load\",\"label\":\"%s\",\"model\":\"%s\",\"proc\":\"%s\",\"error\":\"warm-up failed\"}\n",
                    esc(label).c_str(), esc(model_path).c_str(), name.c_str());
        }
        if (p.error.empty() && !p.npu) probe_layout(p);
        // The vocabulary-only model carries no hyperparameters; a loaded one gives the sliding window.
        if (p.error.empty() && !p.npu && procs.count("npu") && procs["npu"].npu && llama_model_n_swa(p.model) > 0) {
            // The NPU's mask was built with the export's window; a different llama.cpp window would
            // hand over cells the NPU then cannot see, or leave it attending to cells never filled.
            if ((size_t) llama_model_n_swa(p.model) != procs["npu"].npu->n_swa)
                p.layout_error = "sliding window differs: llama.cpp " + std::to_string(llama_model_n_swa(p.model)) + ", NPU export " + std::to_string(procs["npu"].npu->n_swa);
        }
    }

    std::vector<Conv> convs;
    { std::ifstream f(conv_path); std::string line;
      if (!f) { fprintf(stderr, "cannot read %s\n", conv_path.c_str()); return 2; }
      while (std::getline(f, line) && (int) convs.size() < max_convs) if (!line.empty()) convs.push_back(parse_conv(line)); }

    // --check-handoff: the first turn of the first conversation read by the CPU and by the NPU;
    // the NPU's cache converted to the CPU's layout is compared with the CPU's own, layer by
    // layer (cosine of K and of V). A wrong head order, rope permutation or alignment shows as a
    // cosine near zero; the two models' different quantisation as one a little under one.
    if (check_handoff) {
#ifdef PD_NPU
        Proc & C = procs["cpu"]; Proc & N = procs["npu"];
        if (!C.error.empty() || !N.error.empty() || !C.layout_error.empty()) { fprintf(stderr, "check: need cpu and npu: %s %s %s\n", C.error.c_str(), N.error.c_str(), C.layout_error.c_str()); return 2; }
        // The history up to (and including) user turn check_turn of conversation check_conv, with the
        // dataset's replies, as one prompt: long enough to cover positions past 512.
        std::vector<Msg> h;
        int seen = 0;
        for (auto & m : convs[std::min<size_t>(check_conv, convs.size() - 1)].msgs) {
            h.push_back(m);
            if (m.role == "user" && seen++ == check_turn) break;
        }
        std::vector<llama_token> t = tokenize_prompt(vocab, render(vmodel, h, true));
        // With --check-incremental the NPU reads each user turn's new tokens as its own prefill
        // (right-padded pieces after the first), exactly as a cell's prefill side does.
        std::vector<size_t> cuts;
        if (check_incremental) {
            std::vector<Msg> hh;
            for (auto & m : h) {
                hh.push_back(m);
                if (m.role == "user") cuts.push_back(tokenize_prompt(vocab, render(vmodel, hh, true)).size());
            }
        }
        feed(C.ctx, t, 0);
        std::vector<uint8_t> bc(llama_state_seq_get_size(C.ctx, 0));
        llama_state_seq_get_data(C.ctx, bc.data(), bc.size(), 0);
        N.npu->clear();
        if (cuts.size() > 1) {
            size_t from = 0;
            for (size_t c : cuts) { N.npu->prefill(std::vector<int32_t>(t.begin() + from, t.begin() + c)); from = c; }
            fprintf(stderr, "incremental: %zu prefills, %zu tokens\n", cuts.size(), t.size());
        } else {
            N.npu->prefill(std::vector<int32_t>(t.begin(), t.end()));
        }
        std::vector<uint8_t> bn;
        if (!N.npu->to_llama(C.layout, t.size(), bn)) { fprintf(stderr, "check: %s\n", N.npu->error.c_str()); return 2; }
        npu::ParsedState sc, sn; std::string e;
        if (!npu::parse_state(bc.data(), bc.size(), C.kind, sc, e) || !npu::parse_state(bn.data(), bn.size(), C.kind, sn, e)) { fprintf(stderr, "check: %s\n", e.c_str()); return 2; }
        // Section by section, layer by layer, matched by position (the CPU's cells are in cache order).
        for (size_t si = 0; si < sc.kv.size(); si++) {
            const auto & A = sc.kv[si]; const auto & B = sn.kv[si];
            if (A.pos.empty()) continue;
            const size_t row = A.k_row[0] / 2;
            std::map<int32_t, size_t> ia, ib;
            for (size_t c = 0; c < A.pos.size(); c++) ia[A.pos[c]] = c;
            for (size_t c = 0; c < B.pos.size(); c++) ib[B.pos[c]] = c;
            for (size_t l = 0; l < A.n_layer; l++) {
                double ck = 0, cv = 0; size_t m = 0;
                // Also by position range, so an error that grows with position shows.
                double bk[8] = {0}, bv[8] = {0}; size_t bm[8] = {0};
                for (auto & [q, ca] : ia) {
                    auto it = ib.find(q); if (it == ib.end()) continue;
                    double k1 = npu::cos16(A.k[l] + ca * row * 2, B.k[l] + it->second * row * 2, row);
                    double v1 = A.v_trans ? 0 : npu::cos16(A.v[l] + ca * row * 2, B.v[l] + it->second * row * 2, row);
                    ck += k1; cv += v1; m++;
                    int b = std::min(7, q / 256); bk[b] += k1; bv[b] += v1; bm[b]++;
                }
                std::string buckets;
                for (int b = 0; b < 8; b++) if (bm[b]) { char t[96]; snprintf(t, sizeof t, "%s\"%d\":[%.4f,%.4f,%zu]", buckets.empty() ? "" : ",", b * 256, bk[b] / bm[b], bv[b] / bm[b], bm[b]); buckets += t; }
                fprintf(out, "{\"kind\":\"handoff_buckets\",\"label\":\"%s\",\"section\":%zu,\"layer\":%zu,\"by_pos\":{%s}}\n", esc(label).c_str(), si, l, buckets.c_str());
                fprintf(out, "{\"kind\":\"handoff_check\",\"label\":\"%s\",\"model\":\"%s\",\"section\":%zu,\"layer\":%zu,\"positions\":%zu,\"k_cos\":%.5f,\"v_cos\":%.5f,\"v_trans\":%u,\"rope_permute\":%d}\n",
                        esc(label).c_str(), esc(model_path).c_str(), si, l, m, m ? ck / m : -2.0, A.v_trans || !m ? -1.0 : cv / m, A.v_trans, (int) rope_permute);
                fprintf(stderr, "section %zu layer %zu: K cos %.4f V cos %.4f over %zu positions\n", si, l, m ? ck / m : -2.0, A.v_trans || !m ? -1.0 : cv / m, m);
            }
        }
        if (sc.has_rec) {
            for (size_t l = 0; l < sc.rec.r.size(); l++) {
                double c = npu::cos32(sc.rec.r[l], sn.rec.r[l], sc.rec.r_row[l] / 4);
                fprintf(out, "{\"kind\":\"handoff_check\",\"label\":\"%s\",\"model\":\"%s\",\"section\":\"conv\",\"layer\":%zu,\"conv_cos\":%.5f}\n", esc(label).c_str(), esc(model_path).c_str(), l, c);
                fprintf(stderr, "conv %zu: cos %.4f\n", l, c);
            }
        }
        fclose(out);
        fflush(stderr);
        _exit(0);
#else
        fprintf(stderr, "built without PD_NPU\n"); return 2;
#endif
    }

    for (auto & [pn, dn] : cells) {
        Proc & P = procs[pn]; Proc & D = procs[dn];
        const bool same = &D == &P;
        std::string why = P.error.empty() && D.error.empty() ? "" : P.error + " " + D.error;
        // A handoff touching the NPU needs an attention-only export and a plain llama.cpp cache
        // on the other side (not hybrid, not sliding-window): see npu_engine.h.
        if (why.empty() && !same && (P.npu || D.npu)) {
            Proc & L = P.npu ? D : P;
            if (!L.layout_error.empty()) why = "npu handoff: " + L.layout_error;
        }
        if (!why.empty()) {
            fprintf(out, "{\"kind\":\"cell_skipped\",\"label\":\"%s\",\"model\":\"%s\",\"prefill\":\"%s\",\"decode\":\"%s\",\"error\":\"%s\"}\n",
                    esc(label).c_str(), esc(model_path).c_str(), pn.c_str(), dn.c_str(), esc(why).c_str());
            fflush(out); failures++; continue;
        }
        Heat h0; double waited = cool_limit > 0 ? cool_down(cool_limit, cool_max_s, h0) : (h0 = heat(), 0.0);
        fprintf(out, "{\"kind\":\"cooled\",\"label\":\"%s\",\"model\":\"%s\",\"prefill\":\"%s\",\"decode\":\"%s\",\"limit_c\":%.0f,\"max_s\":%d,\"gate\":\"%s\",\"waited_s\":%.0f,\"ap_c\":%.1f,\"skin_c\":%.1f,\"status\":%d,\"gpu_max_mhz\":%ld,\"gpu_top_mhz\":%ld,\"cpu_max_mhz\":\"%s\"}\n",
                esc(label).c_str(), esc(model_path).c_str(), pn.c_str(), dn.c_str(), cool_limit, cool_max_s, cool_limit <= 0 ? "off" : !h0.valid() ? "read_error" : gate_ok(h0, cool_limit) ? "passed" : "timed_out", waited, h0.ap, h0.skin, h0.status, h0.gpu_max_khz / 1000, h0.gpu_top_khz / 1000, h0.cpu_max_mhz.c_str());
        fprintf(out, "{\"kind\":\"cell\",\"when\":\"start\",\"label\":\"%s\",\"model\":\"%s\",\"prefill\":\"%s\",\"decode\":\"%s\",\"gen\":%d,\"ctx\":%d,\"threads\":%d,\"max_turns\":%d,\"npu_dir\":\"%s\",\"temps\":\"%s\"}\n",
                esc(label).c_str(), esc(model_path).c_str(), pn.c_str(), dn.c_str(), gen, ctx_used, threads, max_turns, esc(npu_dir).c_str(), temps().c_str());
        fflush(out);
        std::vector<uint8_t> state, snap;
        auto row_err = [&](const Conv & conv, int turn, const std::string & e) {
            fprintf(out, "{\"kind\":\"turn_error\",\"label\":\"%s\",\"model\":\"%s\",\"prefill\":\"%s\",\"decode\":\"%s\",\"conv\":\"%s\",\"turn\":%d,\"error\":\"%s\"}\n",
                    esc(label).c_str(), esc(model_path).c_str(), pn.c_str(), dn.c_str(), esc(conv.id).c_str(), turn, esc(e).c_str());
            fflush(out);
            failures++;
        };
        auto clear = [](Proc & x) { if (x.npu) x.npu->clear(); else llama_memory_clear(llama_get_memory(x.ctx), true); };
        // A turn that would not fit is the end of that conversation: n_ctx for llama.cpp, and the
        // NPU window with the prompt graph's padding when --window is given (to every process).
        if ((P.npu || D.npu) && window != (int) (P.npu ? P.npu : D.npu)->window) {
            fprintf(stderr, "--window must equal the NPU export's window (%zu)\n", (P.npu ? P.npu : D.npu)->window); return 2;
        }
        const int limit = window > 0 ? std::min(n_ctx, window) : n_ctx;
        auto span = [&](size_t n) { return (int) ((n + pad_batch - 1) / pad_batch * pad_batch); };
        for (auto & conv : convs) {
            clear(P);
            if (!same) clear(D);
            std::vector<Msg> hist;
            std::vector<llama_token> evaluated;  // exactly what the prefill context holds
            int turn = 0;
            for (size_t mi = 0; mi < conv.msgs.size() && turn < max_turns; mi++) {
                hist.push_back(conv.msgs[mi]);
                if (conv.msgs[mi].role != "user") continue;
                std::string full = render(vmodel, hist, true);
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
                if (pos + std::max((int) toks.size(), span(toks.size())) + turn_gen + 8 > limit) break;
                std::string err;

                // On the same llama.cpp processor a hybrid cannot drop the decoded tokens afterwards,
                // so its state is saved before the prefill instead, outside both timed intervals.
                // The NPU drops them from its attention caches; its conv states (if any) are copied
                // after the prefill and put back after the decode, and that copy is subtracted.
                const bool needs_snap = same && !P.npu && (llama_model_is_hybrid(P.model) || llama_model_is_recurrent(P.model));
                snap.clear();
                if (needs_snap) {
                    snap.resize(llama_state_seq_get_size(P.ctx, 0));
                    if (snap.empty() || llama_state_seq_get_data(P.ctx, snap.data(), snap.size(), 0) != snap.size()) {
                        row_err(conv, turn, "snapshot failed"); break;
                    }
                }
                if (P.npu && (int) P.npu->pos() != pos) { row_err(conv, turn, "npu position differs from the history"); break; }

                Heat hb = heat();  // before the turn, outside its timing
                long wall_ms = (long) std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
                auto t_turn = clk::now();
                auto t0 = clk::now();
                bool ok = true;
                llama_token first = 0;
                double prompt_ms = 0;
                if (P.npu) {
                    const void * lg = P.npu->prefill_logits(std::vector<int32_t>(toks.begin(), toks.end()));
                    prompt_ms = ms_since(t0);  // prompt evaluated, as llama.cpp's: argmax after
                    if (!lg) { ok = false; err = "prefill failed: " + P.npu->error; } else first = (llama_token) P.npu->argmax(lg);
                } else {
                    ok = feed(P.ctx, toks, pos);
                    const float * lg = ok ? llama_get_logits_ith(P.ctx, -1) : nullptr;
                    prompt_ms = ms_since(t0);  // prompt evaluated and synchronised
                    if (lg) first = argmax(P.ctx, n_vocab); else { ok = false; err = "prefill failed"; }
                }
                double prefill_ms = ms_since(t0);  // plus choosing the first token
                double snap_ms = 0;
                if (ok && same && P.npu && !P.npu->attention_only) { auto ts = clk::now(); P.npu->save_conv(); snap_ms = ms_since(ts); }

                double get_ms = 0, set_ms = 0, clear_ms = 0; size_t xfer_bytes = 0;
                if (ok && !same) {
                    const size_t n_held = (size_t) pos + toks.size();
                    // Inside turn_ms, as in pd_bench; timed on its own because it scales with the
                    // context's capacity (2,304 cells here, 8,192 on Snapdragon) and not with the turn.
                    if (!D.npu) { auto tc = clk::now(); llama_memory_clear(llama_get_memory(D.ctx), true); clear_ms = ms_since(tc); }
                    // The buffer is sized before the clock starts, as pd_bench does.
                    if (P.npu) state.reserve(P.npu->state_bytes(n_held));
                    else { xfer_bytes = llama_state_seq_get_size(P.ctx, 0); if (state.size() < xfer_bytes) state.resize(xfer_bytes); }
                    auto tg = clk::now();
                    size_t wrote = 0;
                    if (P.npu) {
                        if (!P.npu->to_llama(D.layout, n_held, state)) { ok = false; err = "npu to llama: " + P.npu->error; }
                        wrote = xfer_bytes = state.size();
                    } else {
                        wrote = llama_state_seq_get_data(P.ctx, state.data(), xfer_bytes, 0);
                    }
                    get_ms = ms_since(tg);
                    auto ts = clk::now();
                    if (ok && D.npu) {
                        if (wrote != xfer_bytes || !D.npu->from_llama(state.data(), xfer_bytes, P.kind)) { ok = false; err = "llama to npu: " + D.npu->error; }
                        else if (D.npu->pos() != n_held) { ok = false; err = "npu holds the wrong length after the handoff"; }
                    } else if (ok) {
                        size_t read = wrote == xfer_bytes ? llama_state_seq_set_data(D.ctx, state.data(), xfer_bytes, 0) : 0;
                        if (xfer_bytes == 0 || wrote != xfer_bytes || read != xfer_bytes) { ok = false; err = "state transfer failed"; }
                    }
                    set_ms = ms_since(ts);
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
                        if (D.npu) {
                            int64_t nt = D.npu->step(out_toks.back());
                            if (nt < 0) { ok = false; err = "decode failed: " + D.npu->error; break; }
                            out_toks.push_back((llama_token) nt);
                            p2++;
                        } else {
                            if (switching) {
                                int pz = cpu_paused(switches);
                                if (pz < 0) { ok = false; err = "pause switch unreadable"; break; }
                                int want = pz ? paused_threads : threads;
                                if (want != cur_threads) { llama_set_n_threads(D.ctx, want, want); cur_threads = want; }
                            }
                            b.n_tokens = 1; b.token[0] = out_toks.back(); b.pos[0] = p2++;
                            b.n_seq_id[0] = 1; b.seq_id[0][0] = 0; b.logits[0] = 1;
                            if (llama_decode(D.ctx, b) != 0) { ok = false; err = "decode failed"; break; }
                            out_toks.push_back(argmax(D.ctx, n_vocab));
                        }
                        if (gen_ref) step_ms.push_back(ms_since(tstep));
                        if (switching) step_threads.push_back(cur_threads);  // after success, so it lines up with step_ms
                        steps++;
                    }
                    decode_ms = ms_since(td);
                    llama_batch_free(b);
                    // The next turn's prefill and warm-up use the cell's own count again.
                    if (switching && cur_threads != threads) llama_set_n_threads(D.ctx, threads, threads);
                }
                double turn_ms = ms_since(t_turn) - snap_ms;  // prefill, first token, handoff (with its clear) and decode
                Heat ht = heat(false);  // at the end of the turn, outside its timing: clocks only, the logger has the HAL
                if (same && ok) {
                    // Put the processor back to the dataset's history: drop the decoded tokens, or for
                    // a llama.cpp hybrid restore the snapshot and read this turn's prompt again (untimed).
                    if (P.npu) {
                        if (!P.npu->keep_first((size_t) pos + toks.size())) { ok = false; err = "npu rollback failed"; }
                        if (!P.npu->attention_only) P.npu->restore_conv();
                    } else {
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
                        "\"turn\":%d,\"pos\":%d,\"new_tokens\":%zu,\"prompt_ms\":%.2f,\"prefill_ms\":%.2f,\"xfer_get_ms\":%.2f,\"xfer_set_ms\":%.2f,\"xfer_bytes\":%zu,\"clear_ms\":%.2f,\"snap_ms\":%.2f,"
                        "\"output_tokens\":%zu,\"decode_steps\":%d,\"decode_ms\":%.2f,\"turn_ms\":%.2f,\"wall_ms\":%ld,\"before_ap_c\":%.1f,\"before_skin_c\":%.1f,\"before_status\":%d,\"before_gpu_max_mhz\":%ld,\"before_cpu_capped\":%d,\"cpu_capped\":%d,\"before_cpu_max_mhz\":\"%s\",\"cpu_max_mhz\":\"%s\",\"ap_c\":%.1f,\"skin_c\":%.1f,\"status\":%d,\"gpu_max_mhz\":%ld,\"gpu_cur_mhz\":%ld,\"eog_at\":%d,\"error\":\"%s\",\"turn_gen\":%d,\"ref_tokens\":%d,%s\"step_ms\":[%s],\"tokens\":[%s],\"text\":\"%s\"}\n",
                        esc(label).c_str(), esc(model_path).c_str(), pn.c_str(), dn.c_str(), esc(conv.id).c_str(), esc(conv.source).c_str(),
                        turn, pos, toks.size(), prompt_ms, prefill_ms, get_ms, set_ms, xfer_bytes, clear_ms, snap_ms, out_toks.size(), steps, decode_ms, turn_ms, wall_ms, hb.ap, hb.skin, hb.status, hb.gpu_max_khz / 1000, (int) hb.cpu_capped, (int) ht.cpu_capped, hb.cpu_max_mhz.c_str(), ht.cpu_max_mhz.c_str(), ht.ap, ht.skin, ht.status, ht.gpu_max_khz / 1000, ht.gpu_cur_khz / 1000, eog_at,
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
    // Leave without destructors: MediaTek's runtime aborted at exit on a destroyed mutex
    // ("FORTIFY: pthread_mutex_lock called on a destroyed mutex") after every row was written,
    // which turned a finished run's exit code into a crash. Everything is on disk by now.
    fflush(stderr);
    _exit(failures ? 1 : 0);
}
