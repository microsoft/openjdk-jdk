/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifdef COMPILER2

#include "opto/windowopt.hpp"
#include "peephole_aarch64.hpp"
#include "adfiles/ad_aarch64.hpp"

// Removes redundant flag-setting operations at the start of a block when an
// identical operation exists at the end of the single predecessor block. This
// pattern commonly arises from switch statements and chained if-else
// comparisons.
bool Peephole::inter_block_redundant_flag_ops(Block* block, int block_index,
                                              PhaseCFG* cfg_,
                                              PhaseRegAlloc* ra_,
                                              MachNode* (*new_root)(),
                                              uint inst0_rule) {
  return WindowOpt::inter_block_redundant_flag_ops(block, block_index, cfg_,
                                                   ra_, new_root, inst0_rule);
}

#endif // COMPILER2
