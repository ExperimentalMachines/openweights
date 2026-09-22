/*
 * Times one decode step of a CPU ExecuTorch export, with nothing else loaded.
 *
 * This exists to answer one question that the disaggregated runner cannot: is a slow
 * forward() the fault of the libraries this tree builds, or of the Neuron runtime sitting
 * in the same process? Point it at the same .pte the app loads and compare the ms/token
 * it prints against the app's own stat line. No NPU, no handoff, no tokenizer.
 */

#include <executorch/extension/module/module.h>
#include <executorch/extension/threadpool/threadpool.h>
#include <executorch/runtime/platform/log.h>
#include <executorch/runtime/platform/runtime.h>

#include <gflags/gflags.h>

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <iostream>
#include <vector>

DEFINE_string(cpu_model_path, "", "Path to the CPU .pte.");
DEFINE_uint64(steps, 32, "Decode steps to time.");
DEFINE_uint64(start_pos, 0, "Cache position to start from.");
DEFINE_bool(mmap, false, "Load the .pte with mmap instead of reading it into the heap.");

using executorch::aten::ScalarType;
using executorch::aten::Tensor;
using executorch::aten::TensorImpl;
using executorch::extension::Module;
using executorch::runtime::EValue;

int main(int argc, char** argv) {
  executorch::runtime::runtime_init();
  gflags::ParseCommandLineFlags(&argc, &argv, true);

  if (FLAGS_cpu_model_path.empty()) {
    std::cerr << "--cpu_model_path is required" << std::endl;
    return 1;
  }

  std::cout << "threadpool threads: "
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
  return 0;
}
