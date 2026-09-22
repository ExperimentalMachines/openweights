/*
 * Times one decode step of a CPU ExecuTorch export, with nothing else loaded.
 *
 * This exists to answer one question that the disaggregated runner cannot: is a slow
 * forward() the fault of the libraries this tree builds, or of the Neuron runtime sitting
 * in the same process? Point it at the same .pte the app loads and compare the ms/token
 * it prints against the app's own stat line. No NPU, no handoff, no tokenizer.
 */

#include <executorch/extension/module/module.h>
#include <executorch/extension/threadpool/cpuinfo_utils.h>
#include <executorch/extension/threadpool/threadpool.h>
#include <executorch/runtime/platform/log.h>
#include <executorch/runtime/platform/runtime.h>

#include <gflags/gflags.h>

#include <dirent.h>
#include <unistd.h>

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <iostream>
#include <map>
#include <vector>

DEFINE_string(cpu_model_path, "", "Path to the CPU .pte.");
DEFINE_uint64(steps, 32, "Decode steps to time.");
DEFINE_uint64(start_pos, 0, "Cache position to start from.");
DEFINE_bool(mmap, false, "Load the .pte with mmap instead of reading it into the heap.");
DEFINE_int32(
    threads,
    -1,
    "Delegate threads. -1 sizes the pool as the app does (performant cores minus one), "
    "0 keeps ExecuTorch's default of one per core, N forces N.");

using executorch::aten::ScalarType;
using executorch::aten::Tensor;
using executorch::aten::TensorImpl;
using executorch::extension::Module;
using executorch::runtime::EValue;


// Per-thread time spent running and time spent queued, from the kernel's schedstat.
// Throughput cannot tell a slow kernel from threads the scheduler keeps stacked on a few
// cores; these two numbers can. A healthy pool runs most of the window and queues little.
static std::map<int, std::pair<long long, long long>> schedstat_snapshot() {
  std::map<int, std::pair<long long, long long>> out;
  DIR* dir = opendir("/proc/self/task");
  if (dir == nullptr) return out;
  while (dirent* entry = readdir(dir)) {
    const int tid = atoi(entry->d_name);
    if (tid <= 0) continue;
    char path[64];
    snprintf(path, sizeof(path), "/proc/self/task/%d/schedstat", tid);
    FILE* file = fopen(path, "r");
    if (file == nullptr) continue;
    long long run = 0, wait = 0;
    if (fscanf(file, "%lld %lld", &run, &wait) == 2) out[tid] = {run, wait};
    fclose(file);
  }
  closedir(dir);
  return out;
}

// Mean share of the window the pool's threads spent running and queued.
static std::pair<double, double> pool_run_and_wait(
    const std::map<int, std::pair<long long, long long>>& before,
    const std::map<int, std::pair<long long, long long>>& after,
    double window_s) {
  double run = 0.0, wait = 0.0;
  int threads = 0;
  for (const auto& [tid, now] : after) {
    const auto then = before.find(tid);
    if (then == before.end()) continue;
    const double r = (now.first - then->second.first) / 1e9 / window_s;
    const double w = (now.second - then->second.second) / 1e9 / window_s;
    if (r < 0.05) continue;  // not part of the pool: loaders, logging, idle threads
    run += r;
    wait += w;
    ++threads;
  }
  if (threads == 0) return {0.0, 0.0};
  return {run / threads, wait / threads};
}
int main(int argc, char** argv) {
  executorch::runtime::runtime_init();
  gflags::ParseCommandLineFlags(&argc, &argv, true);

  if (FLAGS_cpu_model_path.empty()) {
    std::cerr << "--cpu_model_path is required" << std::endl;
    return 1;
  }

  // The same policy as the disaggregated JNI and the AAR's LlmModule, so a number from
  // here is a number the app would see. It must run before load_method, which is where
  // XNNPACK captures the pool.
  const int32_t performant =
      static_cast<int32_t>(::executorch::extension::cpuinfo::get_num_performant_cores());
  const int32_t wanted = FLAGS_threads < 0 ? performant - 1 : FLAGS_threads;
  if (wanted > 0) {
    ::executorch::extension::threadpool::get_threadpool()->_unsafe_reset_threadpool(
        static_cast<uint32_t>(wanted));
  }
  std::cout << "performant cores: " << performant << ", threadpool threads: "
            << ::executorch::extension::threadpool::get_threadpool()->get_thread_count()
            << std::endl;

  const auto load_mode = FLAGS_mmap ? Module::LoadMode::Mmap : Module::LoadMode::File;
  auto load_start = std::chrono::high_resolution_clock::now();
  Module module(FLAGS_cpu_model_path, load_mode);
  if (module.load_method("forward") != executorch::runtime::Error::Ok) {
    std::cerr << "could not load forward" << std::endl;
    return 1;
  }
  const double load_s = std::chrono::duration<double>(
                            std::chrono::high_resolution_clock::now() - load_start)
                            .count();
  std::cout << "loaded in " << load_s << " s (" << (FLAGS_mmap ? "mmap" : "file") << ")"
            << std::endl;

  // The two inputs a decode step takes: one token, and where it sits in the cache. They
  // alias these locals, so each step only mutates the two values.
  int64_t token = 1;
  int64_t pos = static_cast<int64_t>(FLAGS_start_pos);

  int32_t t_sizes[] = {1, 1};
  uint8_t t_dim_order[] = {0, 1};
  TensorImpl t_impl(ScalarType::Long, 2, t_sizes, &token, t_dim_order);
  Tensor t_tensor(&t_impl);

  int32_t p_sizes[] = {1};
  uint8_t p_dim_order[] = {0};
  TensorImpl p_impl(ScalarType::Long, 1, p_sizes, &pos, p_dim_order);
  Tensor p_tensor(&p_impl);

  std::vector<EValue> inputs{EValue(t_tensor), EValue(p_tensor)};

  std::vector<double> step_ms;
  step_ms.reserve(FLAGS_steps);
  const auto sched_before = schedstat_snapshot();
  const auto window_start = std::chrono::high_resolution_clock::now();
  for (size_t i = 0; i < FLAGS_steps; ++i) {
    const auto start = std::chrono::high_resolution_clock::now();
    auto res = module.execute("forward", inputs);
    const double ms =
        std::chrono::duration<double, std::milli>(
            std::chrono::high_resolution_clock::now() - start)
            .count();
    if (!res.ok()) {
      std::cerr << "forward failed at step " << i << std::endl;
      return 1;
    }
    step_ms.push_back(ms);
    token = 1;
    pos++;
  }

  const double window_s = std::chrono::duration<double>(
                              std::chrono::high_resolution_clock::now() - window_start)
                              .count();
  const auto [run_share, wait_share] =
      pool_run_and_wait(sched_before, schedstat_snapshot(), window_s);
  // The first step pays for lazily built runtime state, so it is reported apart from the
  // rest rather than folded into a mean that it would dominate.
  std::sort(step_ms.begin() + 1, step_ms.end());
  double sum = 0.0;
  for (size_t i = 1; i < step_ms.size(); ++i) sum += step_ms[i];
  const double mean = sum / static_cast<double>(step_ms.size() - 1);
  std::cout << "first step   : " << step_ms[0] << " ms" << std::endl;
  std::cout << "median step  : " << step_ms[step_ms.size() / 2] << " ms" << std::endl;
  std::cout << "mean step    : " << mean << " ms" << std::endl;
  std::cout << "throughput   : " << (1000.0 / mean) << " tok/s" << std::endl;
  std::cout << "pool threads : running " << run_share << ", queued " << wait_share
            << " of the window each" << std::endl;
  return 0;
}
