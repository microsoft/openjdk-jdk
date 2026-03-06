/*
 * Copyright (c) 2020, Microsoft Corporation. All rights reserved.
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

#ifndef OS_CPU_WINDOWS_AARCH64_ORDERACCESS_WINDOWS_AARCH64_HPP
#define OS_CPU_WINDOWS_AARCH64_ORDERACCESS_WINDOWS_AARCH64_HPP

// Included in orderAccess.hpp header file.
#include <arm64intr.h>
#include "vm_version_aarch64.hpp"
#include "runtime/vm_version.hpp"

// Implementation of class OrderAccess.
//
// Use the MSVC __dmb() intrinsic directly rather than C++ std::atomic_thread_fence().
// Microsoft documents that __dmb() "inserts compiler blocks to prevent instruction
// reordering" in addition to emitting the hardware DMB instruction. This is critical
// because HotSpot uses volatile (non-std::atomic) fields throughout the runtime, and
// std::atomic_thread_fence() is only defined by the C++ standard to order std::atomic
// operations — it may not act as a compiler barrier for volatile/non-atomic accesses
// on ARM64 with /volatile:iso. Using __dmb() ensures correct ordering for the Dekker
// protocol in ObjectMonitor::exit() and similar patterns throughout HotSpot.

inline void OrderAccess::loadload()   { acquire(); }
inline void OrderAccess::storestore() { release(); }
inline void OrderAccess::loadstore()  { acquire(); }
inline void OrderAccess::storeload()  { fence(); }

#define READ_MEM_BARRIER  __dmb(_ARM64_BARRIER_ISHLD)
#define WRITE_MEM_BARRIER __dmb(_ARM64_BARRIER_ISH)
#define FULL_MEM_BARRIER  __dmb(_ARM64_BARRIER_ISH)

inline void OrderAccess::acquire() {
  READ_MEM_BARRIER;
}

inline void OrderAccess::release() {
  WRITE_MEM_BARRIER;
}

inline void OrderAccess::fence() {
  FULL_MEM_BARRIER;
}

inline void OrderAccess::cross_modify_fence_impl() {
  __isb(_ARM64_BARRIER_SY);
}

#endif // OS_CPU_WINDOWS_AARCH64_ORDERACCESS_WINDOWS_AARCH64_HPP
