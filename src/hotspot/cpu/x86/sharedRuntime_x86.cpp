/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/globalDefinitions.hpp"
#include "vmreg_x86.inline.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif //COMPILER1

#define __ masm->

#ifdef COMPILER1
// ---------------------------------------------------------------------------
// Object.hashCode, System.identityHashCode can pull the hashCode from the
// header word instead of doing a full VM transition once it's been computed.
// Since hashCode is usually polymorphic at call sites we can't do this
// optimization at the call site without a lot of work.
void SharedRuntime::inline_check_hashcode_from_object_header(MacroAssembler* masm,
                                 const methodHandle& method,
                                 Register obj_reg,
                                 Register result) {
  Label slowCase;

  // Unlike for Object.hashCode, System.identityHashCode is static method and
  // gets object as argument instead of the receiver.
  if (method->intrinsic_id() == vmIntrinsics::_identityHashCode) {
    Label Continue;
    // return 0 for null reference input
    __ cmpptr(obj_reg, NULL_WORD);
    __ jcc(Assembler::notEqual, Continue);
    __ xorptr(result, result);
    __ ret(0);
    __ bind(Continue);
  }

  __ movptr(result, Address(obj_reg, oopDesc::mark_offset_in_bytes()));


  if (LockingMode == LM_LIGHTWEIGHT) {
    if (!UseObjectMonitorTable) {
      // check if monitor
      __ testptr(result, markWord::monitor_value);
      __ jcc(Assembler::notZero, slowCase);
    }
  } else {
    // check if locked
    __ testptr(result, markWord::unlocked_value);
    __ jcc(Assembler::zero, slowCase);
  }

  // get hash
#ifdef _LP64
  // Read the header and build a mask to get its hash field.
  // Depend on hash_mask being at most 32 bits and avoid the use of hash_mask_in_place
  // because it could be larger than 32 bits in a 64-bit vm. See markWord.hpp.
  __ shrptr(result, markWord::hash_shift);
  __ andptr(result, markWord::hash_mask);
#else
  __ andptr(result, markWord::hash_mask_in_place);
#endif //_LP64

  // test if hashCode exists
  __ jcc(Assembler::zero, slowCase);
#ifndef _LP64
  __ shrptr(result, markWord::hash_shift);
#endif
  __ ret(0);
  __ bind(slowCase);
}
#endif //COMPILER1

#ifdef _WIN64
const juint  float_sign_mask  = 0x7FFFFFFF;
const juint  float_infinity   = 0x7F800000;
const julong double_sign_mask = CONST64(0x7FFFFFFFFFFFFFFF);
const julong double_infinity  = CONST64(0x7FF0000000000000);
#endif

JRT_LEAF(jfloat, SharedRuntime::frem(jfloat x, jfloat y))
#ifdef _WIN64
  // 64-bit Windows on amd64 returns the wrong values for
  // infinity operands.
  juint xbits = PrimitiveConversions::cast<juint>(x);
  juint ybits = PrimitiveConversions::cast<juint>(y);
  // x Mod Infinity == x unless x is infinity
  if (((xbits & float_sign_mask) != float_infinity) &&
       ((ybits & float_sign_mask) == float_infinity) ) {
    return x;
  }
  return ((jfloat)fmod_winx64((double)x, (double)y));
#else
  assert(StubRoutines::fmod() != nullptr, "");
  jdouble (*addr)(jdouble, jdouble) = (double (*)(double, double))StubRoutines::fmod();
  jdouble dx = (jdouble) x;
  jdouble dy = (jdouble) y;

  return (jfloat) (*addr)(dx, dy);
#endif
JRT_END

JRT_LEAF(jdouble, SharedRuntime::drem(jdouble x, jdouble y))
#ifdef _WIN64
  julong xbits = PrimitiveConversions::cast<julong>(x);
  julong ybits = PrimitiveConversions::cast<julong>(y);
  // x Mod Infinity == x unless x is infinity
  if (((xbits & double_sign_mask) != double_infinity) &&
       ((ybits & double_sign_mask) == double_infinity) ) {
    return x;
  }
  return ((jdouble)fmod_winx64((double)x, (double)y));
#else
  assert(StubRoutines::fmod() != nullptr, "");
  jdouble (*addr)(jdouble, jdouble) = (double (*)(double, double))StubRoutines::fmod();

  return (*addr)(x, y);
#endif
JRT_END
