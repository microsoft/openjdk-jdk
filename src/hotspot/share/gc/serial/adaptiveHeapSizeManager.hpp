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

#ifndef SHARE_GC_SERIAL_ADAPTIVEHEAPSIZEMANAGER_HPP
#define SHARE_GC_SERIAL_ADAPTIVEHEAPSIZEMANAGER_HPP

#include "memory/allocation.hpp"
#include "gc/serial/adaptiveHeapSizeTable.hpp"
#include "gc/serial/defNewGeneration.hpp"
#include "gc/serial/serialHeapComponentSizes.hpp"
#include "gc/serial/tenuredGeneration.hpp"

class AdaptiveHeapSizeManager: public CHeapObj<mtGC> {
private:
  DefNewGeneration* _young_gen;
  TenuredGeneration* _old_gen;
  AdaptiveHeapSizeTable* _adaptive_heap_size_table;

  SerialHeapComponentSizes target_heap_component_sizes(int gc_overhead) const;
  SerialHeapComponentSizes heap_component_sizes_for_available_memory(SerialHeapComponentSizes heap_component_sizes, size_t available_memory, int system_memory_pressure);

public:
  AdaptiveHeapSizeManager(DefNewGeneration* young_gen, TenuredGeneration* old_gen);
  void resize_heap_for_target_gc_overhead(int gc_overhead);
};

#endif // SHARE_GC_SERIAL_ADAPTIVEHEAPSIZEMANAGER_HPP
