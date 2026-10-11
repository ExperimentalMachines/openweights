/*
 * Copyright (c) 2026 OpenWeights Authors
 *
 * Licensed under the BSD License (the "License"); you may not use this file
 * except in compliance with the License.
 */

// libneuron_buffer_allocator.so, built from source in place of the NeuroPilot SDK's prebuilt.
//
// ExecuTorch's Neuron backend reaches this library by dlopen and five C entry points
// (backends/mediatek/runtime/NeuronBufferAllocator.cpp). The SDK ships it only as a binary
// linked for 4 KB pages, which Google Play refuses for an app at this target SDK, so it is
// written here and linked for 16 KB pages with the rest of the runtime.
//
// The buffers are ExecuTorch's own MemoryUnit, compiled from the same header the backend
// reads: neuron_buffer_find hands the backend a MemoryUnit*, and the backend calls its inline
// accessors, so the class layout has to be the header's exactly. Built against that header,
// it is by construction.

#include "NeuronBufferAllocator.h"

#include <iterator>
#include <map>
#include <memory>
#include <mutex>

using executorch::backends::neuron::MemoryUnit;

namespace {

// One per process. The backend and the runner library each carry ExecuTorch's wrapper and
// each call create once; the backend has to find the buffers the runner allocated, so both
// get this one. Each wrapper locks only its own mutex, so this locks for itself.
struct Allocator {
  std::mutex lock;
  // By address, so the unit holding an address inside a buffer is the one at or below it:
  // the backend looks buffers up by a tensor's data pointer, which can be an offset into one.
  std::map<char*, std::unique_ptr<MemoryUnit>> units;
};

Allocator* allocatorFrom(void* handle) {
  return static_cast<Allocator*>(handle);
}

Allocator& shared() {
  static Allocator* instance = new Allocator();  // never destroyed: buffers outlive statics
  return *instance;
}

}  // namespace

extern "C" {

__attribute__((visibility("default"))) void* neuron_buffer_allocator_create() {
  return &shared();
}

__attribute__((visibility("default"))) void* neuron_buffer_allocate(void* handle, size_t size) {
  auto* allocator = allocatorFrom(handle);
  if (allocator == nullptr || size == 0) return nullptr;
  std::scoped_lock guard(allocator->lock);
  auto unit = MemoryUnit::Create(size);
  if (!unit) return nullptr;
  auto* address = static_cast<char*>(unit->GetAddress());
  allocator->units[address] = std::move(unit);
  return address;
}

__attribute__((visibility("default"))) bool neuron_buffer_remove(void* handle, void* address) {
  auto* allocator = allocatorFrom(handle);
  if (allocator == nullptr) return false;
  std::scoped_lock guard(allocator->lock);
  return allocator->units.erase(static_cast<char*>(address)) > 0;
}

__attribute__((visibility("default"))) const void* neuron_buffer_find(void* handle, void* address) {
  auto* allocator = allocatorFrom(handle);
  if (allocator == nullptr) return nullptr;
  std::scoped_lock guard(allocator->lock);
  auto* target = static_cast<char*>(address);
  auto above = allocator->units.upper_bound(target);
  if (above == allocator->units.begin()) return nullptr;
  const auto& [base, unit] = *std::prev(above);
  return target < base + unit->GetSize() ? unit.get() : nullptr;
}

__attribute__((visibility("default"))) void neuron_buffer_clear(void* handle) {
  auto* allocator = allocatorFrom(handle);
  if (allocator == nullptr) return;
  std::scoped_lock guard(allocator->lock);
  allocator->units.clear();
}

}  // extern "C"
