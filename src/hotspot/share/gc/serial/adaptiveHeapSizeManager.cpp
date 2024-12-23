/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "gc/serial/adaptiveHeapSizing.hpp"
#include "gc/serial/defNewGeneration.hpp"
#include "gc/serial/generation.hpp"
#include "gc/serial/tenuredGeneration.hpp"
#include "gc/serial/serialHeapComponentSizes.hpp"
#include "gc/shared/space.hpp"
#include "adaptiveHeapSizeManager.hpp"

AdaptiveHeapSizeManager::AdaptiveHeapSizeManager(DefNewGeneration* young_gen, TenuredGeneration* old_gen, GCOverheadTracker* gc_overhead_tracker) {
  _young_gen = young_gen;
  _old_gen = old_gen;
  _adaptive_heap_size_table = new AdaptiveHeapSizeTable();
  _gc_overhead_tracker = gc_overhead_tracker;
}

SerialHeapComponentSizes AdaptiveHeapSizeManager::target_heap_component_sizes(int gc_overhead) const {
  const size_t used_after_full_gc = _old_gen->used();
  const size_t target_old_gen_size = used_after_full_gc * 2;
  const size_t max_new_size = MaxHeapSize - target_old_gen_size;
  const size_t min_survivor_space_size = Generation::GenGrain;

  size_t prev_eden_capacity = _young_gen->eden()->capacity();
  size_t desired_survivor_size = _gc_overhead_tracker->survivor_used_after_last_young_collection() * 2;
  if (desired_survivor_size < min_survivor_space_size) {
    desired_survivor_size = min_survivor_space_size;
  }
  size_t max_eden_capacity = max_new_size - 2 * desired_survivor_size;

  assert(max_eden_capacity <= max_new_size, "eden capacity must not exceed max new size");

  // TODO: Use ZGC resizing algorithm
  size_t desired_eden_size = _adaptive_heap_size_table->target_size_after_young_collection(SerialGCOverheadTarget, gc_overhead, prev_eden_capacity, max_eden_capacity);

  /*
    TODO: Determine survivor space sizes

    The cost of premature promotion is higher in serial than G1/ZGC
    because it affects the cost of the in-place collection (full)
    We need to determine the rate of premature promotion.
    0% premature promotion means we can reduce the size of the survivor spaces.
    5-10% is acceptable. Over 10% means the survivor space is too small.
    When the to space is full, allocation is done directly from tenured.
    This is how to respect the SurvivorRatio
       s = (e + 2s) / (R + 2)
       (R + 2)s = e + 2s
       (R + 2)s - 2s = e
       s(R + 2 - 2) = e
       s = e/R
  size_t desired_survivor_size = desired_eden_size / SurvivorRatio;
  */

  return SerialHeapComponentSizes(desired_eden_size, desired_survivor_size, target_old_gen_size, max_eden_capacity);
}

SerialHeapComponentSizes AdaptiveHeapSizeManager::heap_component_sizes_for_available_memory(SerialHeapComponentSizes heap_component_sizes, size_t available_memory, int system_memory_pressure) {
  size_t desired_young_committed = heap_component_sizes.eden_size() + (2 * heap_component_sizes.survivor_space_size());
  size_t current_young_committed = _young_gen->committed_size();
  size_t current_tenured_committed = _old_gen->committed_size();

  size_t eden_size = heap_component_sizes.eden_size();
  size_t survivor_space_size = heap_component_sizes.survivor_space_size();
  size_t tenured_size = heap_component_sizes.tenured_size();
  size_t max_eden_size = heap_component_sizes.max_eden_size();

  // use at most this % of the available memory
  // TODO: do not expand anything if global memory is critically low.
  const size_t safety_factor_percent = 90;
  available_memory = available_memory * safety_factor_percent / 100;

  if (desired_young_committed > current_young_committed) {
    size_t young_expansion = desired_young_committed - current_young_committed;

    if (heap_component_sizes.tenured_size() > current_tenured_committed) {
      // expanding both young gen and tenured
      size_t tenured_expansion = heap_component_sizes.tenured_size() - current_tenured_committed;
      size_t total_expansion = young_expansion + tenured_expansion;

      if (total_expansion > available_memory) {
        double available_frac = available_memory / (double)total_expansion;
        eden_size *= (1 + available_frac);
        survivor_space_size *= (1 + available_frac);
        tenured_size *= (1 + available_frac);
      }
    } else {
      // expanding the young gen only
      if (young_expansion > available_memory) {
        double available_frac = available_memory / (double)young_expansion;
        eden_size *= (1 + available_frac);
        survivor_space_size *= (1 + available_frac);
      }
    }
  } else if (heap_component_sizes.tenured_size() > current_tenured_committed) {
    // expanding tenured only
    size_t tenured_expansion = heap_component_sizes.tenured_size() - current_tenured_committed;
    if (tenured_expansion > available_memory) {
      tenured_size += available_memory;
    }
  }

  size_t alignment = Generation::GenGrain;
  eden_size = align_down(eden_size, alignment);
  survivor_space_size = align_down(survivor_space_size, alignment);
  max_eden_size = align_down(max_eden_size, alignment);

  tenured_size = align_up(tenured_size, alignment);
  assert(tenured_size >= _old_gen->used(), "cannot shrink below used size");
  return SerialHeapComponentSizes(eden_size, survivor_space_size, tenured_size, max_eden_size);
}

void AdaptiveHeapSizeManager::resize_heap_for_target_gc_overhead(int gc_overhead) {
  SerialHeapComponentSizes initial_target_heap_component_sizes = target_heap_component_sizes(gc_overhead);

  log_info(gc, ergo, heap)(
    SERIAL_GC_AHS_PREFIX
    "Target sizes: eden: " SIZE_FORMAT "K. survivor: " SIZE_FORMAT "K. tenured: " SIZE_FORMAT "K. max eden size: " SIZE_FORMAT "K.",
    initial_target_heap_component_sizes.eden_size() / K,
    initial_target_heap_component_sizes.survivor_space_size() / K,
    initial_target_heap_component_sizes.tenured_size() / K,
    initial_target_heap_component_sizes.max_eden_size() / K);

  // Note that expansion can only happen to the committed size. // TODO: verify this
  // We need to ensure that we know all region sizes before making actual adjustments.
  size_t available_memory = os::available_memory();

  // TODO: account for system pressure
  int system_memory_pressure = 0;
  const SerialHeapComponentSizes heap_component_sizes = heap_component_sizes_for_available_memory(initial_target_heap_component_sizes, available_memory, system_memory_pressure);

  log_info(gc, ergo, heap)(
    SERIAL_GC_AHS_PREFIX
    "Target sizes adjusted for available memory: eden: " SIZE_FORMAT "K. survivor: " SIZE_FORMAT "K. tenured: " SIZE_FORMAT "K. max eden size: " SIZE_FORMAT "K. available memory: " SIZE_FORMAT "K.",
    heap_component_sizes.eden_size() / K,
    heap_component_sizes.survivor_space_size() / K,
    heap_component_sizes.tenured_size() / K,
    heap_component_sizes.max_eden_size() / K,
    available_memory / K);

  bool young_changed = _young_gen->adjust_size(heap_component_sizes.eden_size(), heap_component_sizes.survivor_space_size(), heap_component_sizes.max_eden_size());
  _old_gen->adjust_size(heap_component_sizes.tenured_size());
}
