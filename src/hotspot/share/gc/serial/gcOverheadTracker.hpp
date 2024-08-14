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

#ifndef SHARE_GC_SERIAL_GCOVERHEADTRACKER_HPP
#define SHARE_GC_SERIAL_GCOVERHEADTRACKER_HPP

#include "memory/allocation.hpp"
#include "utilities/linkedlist.hpp"
#include "gc/serial/gcOverheadPauseTime.hpp"

class GCOverheadTracker: public CHeapObj<mtGC> {
private:
  double _last_young_gc_ending_real_time;
  double _last_young_gc_ending_user_time;
  double _last_young_gc_ending_system_time;

  double _last_full_gc_ending_real_time;
  double _last_full_gc_ending_user_time;
  double _last_full_gc_ending_system_time;

  int _completed_young_collections;
  int _completed_full_collections;

  double _starting_real_time, _starting_user_time, _starting_system_time;
  bool _starting_times_valid;

  LinkedListImpl<GcOverheadPauseTime> _gc_overhead_window;
  double _gc_overhead_window_pause_time;

  void start_collection();
  int end_collection(bool is_full, bool full_collection_required);

public:
  GCOverheadTracker();
  void start_young_collection();
  int end_young_collection(bool full_collection_required);

  void start_full_collection();
  int end_full_collection();

  int add_to_gc_overhead_window(double current_time, double pause_time);
  int completed_full_collections() { return _completed_full_collections; }
};

#endif // SHARE_GC_SERIAL_GCOVERHEADTRACKER_HPP
