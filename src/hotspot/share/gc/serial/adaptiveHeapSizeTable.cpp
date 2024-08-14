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
#include "gc/serial/adaptiveHeapSizeTable.hpp"
#include "gc/serial/generation.hpp"
#include "gc/shared/genArguments.hpp"

size_t AdaptiveHeapSizeTable::target_size_after_young_collection(int gc_overhead_target, int gc_overhead, size_t new_size_before, size_t max_new_size) {
  static int range[4] = { 0, 5, 10, 101};                           // zones of aggressiveness
  static double resize_table[4] = { -0.10, 0.10, 0.25, 0.50 };      // levels of aggressiveness

  size_t max_heap_size_mb = max_new_size / M;
  assert(new_size_before <= max_new_size, "just checking");

  // All space sizes must be multiples of Generation::GenGrain.
  size_t alignment = Generation::GenGrain;
  size_t current_heap_size_mb = new_size_before / M;
  size_t current_heap_size_mb_prev = current_heap_size_mb;

  size_t target_size;
  int gc_overhead_diff = gc_overhead - gc_overhead_target;

  if (gc_overhead_diff != 0) {
    int index = 0;
    while (gc_overhead_diff >= range[index]) index++;

    size_t available = max_heap_size_mb - current_heap_size_mb;
    int resize_fraction = (int)(((gc_overhead_diff >= 0) ? (double)available : (double)current_heap_size_mb) * resize_table[index]);
    size_t new_heap_size_mb = current_heap_size_mb + resize_fraction;
    current_heap_size_mb = MIN2(new_heap_size_mb, max_heap_size_mb);

    size_t aligned_max = ((max_uintx - alignment) & ~(alignment-1));
    target_size = current_heap_size_mb * M;
    if (target_size <= aligned_max) {
      target_size = align_up(target_size, alignment);
    }
  } else {
    target_size = new_size_before;
  }

  // Adjust new generation size
  size_t min_new_size = 1 * M;
  target_size = clamp(target_size, min_new_size, max_new_size);
  assert(target_size <= max_new_size, "just checking");

  return target_size;
}
