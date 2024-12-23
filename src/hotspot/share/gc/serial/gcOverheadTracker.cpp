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
#include "gc/serial/gcOverheadPauseTime.hpp"
#include "gc/serial/gcOverheadTracker.hpp"
#include "gc/shared/gc_globals.hpp"

GCOverheadTracker::GCOverheadTracker() {
  _last_young_gc_ending_real_time = 0;
  _last_young_gc_ending_user_time = 0;
  _last_young_gc_ending_system_time = 0;

  _last_full_gc_ending_real_time = 0;
  _last_full_gc_ending_user_time = 0;
  _last_full_gc_ending_system_time = 0;

  _completed_young_collections = 0;
  _completed_full_collections = 0;
  _gc_overhead_window_pause_time = 0;
  _survivor_used_after_last_young_collection = 0;
}

void GCOverheadTracker::start_young_collection() {
  start_collection();
}

int GCOverheadTracker::end_young_collection(bool full_collection_required, size_t survivor_used_after_last_young_collection) {
  _survivor_used_after_last_young_collection = survivor_used_after_last_young_collection;
  return end_collection(false /* is_full */, full_collection_required);
}

void GCOverheadTracker::start_full_collection() {
  start_collection();
}

int GCOverheadTracker::end_full_collection() {
  return end_collection(true /* is_full */, false);
}

void GCOverheadTracker::start_collection() {
  if (!UseSerialGCOverheadErgonomics) {
    return;
  }

  _starting_real_time = 0;
  _starting_user_time = 0;
  _starting_system_time = 0;
  _starting_times_valid = os::getTimesSecs(&_starting_real_time, &_starting_user_time, &_starting_system_time);
  if (!_starting_times_valid) {
    char buf[512];
    size_t buf_len = os::lasterror(buf, sizeof(buf));
    warning("Error getting final time values for young GC overhead calculation: %s", buf_len != 0 ? buf : "<unknown error>");
    log_warning(gc, ergo)(SERIAL_GC_AHS_PREFIX "Error getting initial time values for GC overhead calculation");
    // TODO: should we fall back to timestamps?
  }
}

int GCOverheadTracker::end_collection(bool is_full, bool full_collection_required) {
  if (!UseSerialGCOverheadErgonomics) {
    return -1;
  }

  int gc_overhead = -1;
  bool is_first_gc;
  double last_gc_ending_real_time;

  if (is_full) {
    _completed_full_collections++;
    is_first_gc = (_completed_full_collections == 1);
    last_gc_ending_real_time = _last_full_gc_ending_real_time;
  } else {
    _completed_young_collections++;
    is_first_gc = (_completed_young_collections == 1);
    last_gc_ending_real_time = _last_young_gc_ending_real_time;

    if (full_collection_required) {
      return gc_overhead;
    }
  }

  if (!_starting_times_valid) {
    return gc_overhead;
  }

  double real_time, user_time, system_time;
  bool valid = os::getTimesSecs(&real_time, &user_time, &system_time);
  if (valid) {
    // How do noisy neighbors affect this calculation?
    // Which rounding is used when casting double to int?
    if (!is_first_gc) {
      assert(last_gc_ending_real_time >= 0, "Real time at end of last GC must be >= 0");
      assert(real_time >= 0, "Real time at end of current GC must be >= 0");
      assert(real_time >= last_gc_ending_real_time, "Real time at end of current GC must be >= Real time at end of last GC");
      double time_since_last_gc = real_time - last_gc_ending_real_time;
      double gc_user_time = user_time - _starting_user_time;
      double gc_system_time = system_time - _starting_system_time;
      double gc_real_time = real_time - _starting_real_time;
      gc_overhead = add_to_gc_overhead_window(_starting_real_time, gc_real_time);
      log_info(gc, ergo)(SERIAL_GC_AHS_PREFIX "GC Overhead: %d. User=%3.6fs Sys=%3.6fs Real=%3.6fs, Time since last GC=%3.6fs", gc_overhead, gc_user_time, gc_system_time, gc_real_time, time_since_last_gc);
      assert(gc_overhead >= 0, "Computed GC overhead must not be negative. Found %d. User=%3.6fs Sys=%3.6fs Real=%3.6fs, Time since last GC=%3.6fs", gc_overhead, gc_user_time, gc_system_time, gc_real_time, time_since_last_gc);
      assert(gc_overhead <= 100, "Computed GC overhead must not exceed 100. Found %d. User=%3.6fs Sys=%3.6fs Real=%3.6fs, Time since last GC=%3.6fs", gc_overhead, gc_user_time, gc_system_time, gc_real_time, time_since_last_gc);
    }
  } else {
    char buf[512];
    size_t buf_len = os::lasterror(buf, sizeof(buf));
    warning("Error getting final time values for GC overhead calculation: %s", buf_len != 0 ? buf : "<unknown error>");
    log_warning(gc, ergo)(SERIAL_GC_AHS_PREFIX "Error getting final time values for GC overhead calculation");
  }

  if (is_full) {
    _last_full_gc_ending_real_time = real_time;
    _last_full_gc_ending_system_time = system_time;
    _last_full_gc_ending_user_time = user_time;
  } else {
    _last_young_gc_ending_real_time = real_time;
    _last_young_gc_ending_system_time = system_time;
    _last_young_gc_ending_user_time = user_time;
  }

  return gc_overhead;
}

int GCOverheadTracker::add_to_gc_overhead_window(double pause_start_time, double pause_duration) {
  assert(UseSerialGCOverheadErgonomics, "UseSerialGCOverheadErgonomics must be enabled");
  assert(pause_start_time >= 0, "pause_start_time must not be negative");
  assert(pause_duration >= 0, "pause_duration must not be negative");

  GcOverheadPauseTime gc_pause_time(pause_start_time, pause_duration);

  // add is the same as push_back.
  _gc_overhead_window.add(gc_pause_time);
  _gc_overhead_window_pause_time += pause_duration;

  int gc_overhead_window_duration_sec = GCOverheadWindowDurationMins * 60;

  GcOverheadPauseTime* back = _gc_overhead_window.head()->data();
  GcOverheadPauseTime* front = _gc_overhead_window.tail()->data();

  // Remove all data points older than GCOverheadWindowDurationMins
  double window_duration_sec = back->start_time + back->duration - front->start_time;
  while (window_duration_sec > gc_overhead_window_duration_sec) {
    auto tail = _gc_overhead_window.tail();
    front = tail->data();
    _gc_overhead_window.remove(tail);
    double pause = front->duration;

    log_debug(gc, ergo)(SERIAL_GC_AHS_PREFIX "Popped entry from front of GC overhead window: pause start time=%.6fs pause duration=%.6fs", front->start_time, pause);
    // Remove the earliest GC pause from the window
    window_duration_sec -= pause;
    if (window_duration_sec <= gc_overhead_window_duration_sec) {
      front->start_time += pause;
      front->duration = 0;
      GcOverheadPauseTime new_front(front->start_time, 0);
      _gc_overhead_window.insert_after(new_front, tail);
      _gc_overhead_window_pause_time -= pause;

      log_debug(gc, ergo)(SERIAL_GC_AHS_PREFIX "Pushed entry to front of GC overhead window: pause start time=%.6fs pause duration=%.6fs", front->start_time, front->duration);
      break;
    }

    front = _gc_overhead_window.tail()->data();
    window_duration_sec = back->start_time + back->duration - front->start_time;
  }

  log_debug(gc, ergo)(SERIAL_GC_AHS_PREFIX "Computing GC overhead from %.3fs window with pause time=%.6fs", window_duration_sec, _gc_overhead_window_pause_time);
  int gc_overhead = (int)(_gc_overhead_window_pause_time * 100.0 / window_duration_sec);
  return gc_overhead;
}
