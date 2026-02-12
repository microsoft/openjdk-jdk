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

#include "windowopt.hpp"

// Returns true if two constant operands have the same value.
static bool same_constants(MachOper* left_oper, MachOper* right_oper) {
  BasicType left_type = left_oper->type()->basic_type();
  BasicType right_type = right_oper->type()->basic_type();
  if (left_type != right_type) {
    return false;
  }

  switch (left_type) {
    case T_INT:    return left_oper->constant()  == right_oper->constant();
    case T_SHORT:  return left_oper->constantH() == right_oper->constantH();
    case T_LONG:   return left_oper->constantL() == right_oper->constantL();

    // Conservatively avoid optimizing all other constants for now.
    default:       return false;
  }
}

// Returns true if two nodes have the same physical registers assigned for
// n_edges edges starting at edge_idx.
static bool same_registers(PhaseRegAlloc* ra,
                            MachNode* left, MachNode* right,
                            uint edge_idx, uint n_edges) {
  for (uint j = 0; j < n_edges; j++) {
    assert(left->in(edge_idx + j) != nullptr && right->in(edge_idx + j) != nullptr,
           "same rule should imply same non-null input structure");
    if (ra->get_reg_first(left->in(edge_idx + j)) != ra->get_reg_first(right->in(edge_idx + j))) {
      return false;
    }
  }
  return true;
}

// Returns true if two MachNodes have the same rule, registers, and constants
static bool same_ops(PhaseRegAlloc* ra, MachNode* left, MachNode* right) {
  if (left->rule() != right->rule()) {
    return false;
  }

  for (uint i = 1, edge_idx = left->oper_input_base(); i < left->_num_opnds; i++) {
    MachOper* left_oper = left->_opnds[i];
    MachOper* right_oper = right->_opnds[i];
    uint n_edges = left_oper->num_edges();

    if (n_edges == 0) {
      if (!same_constants(left_oper, right_oper)) {
        return false;
      }
    } else {
      if (!same_registers(ra, left, right, edge_idx, n_edges)) {
        return false;
      }
    }

    edge_idx += n_edges;
  }

  return true;
}

// Removes redundant flag-setting operations at the start of a block when an
// identical operation exists at the end of the single predecessor block. This
// pattern commonly arises from switch statements and chained if-else
// comparisons.
bool WindowOpt::inter_block_redundant_flag_ops(Block* block, int block_index,
                                               PhaseCFG* cfg_,
                                               PhaseRegAlloc* ra_,
                                               MachNode* (*new_root)(),
                                               uint inst0_rule) {
  MachNode* current = block->get_node(block_index)->as_Mach();
  assert(current->rule() == inst0_rule, "sanity");

  // Block must have exactly one predecessor.
  if (block->num_preds() != 2) {
    return false;
  }

  Block* pred_block = cfg_->get_block_for_node(block->pred(1));
  if (pred_block == nullptr || pred_block->number_of_nodes() == 0) {
    // If the predecssor block has no nodes, there's no hope of finding any
    // redundant instructions.
    return false;
  }

  // Check if anything between the block head and the redundant operation may
  // clobber RFLAGS
  for (int i = 1; i < block_index; i++) {
    if (!block->get_node(i)->is_MachProj()) {
      return false;
    }
  }

  // Walk backwards over the predecessor's nodes to find redundant operation
  MachNode* maybe_redundant = nullptr;
  for (uint i = pred_block->number_of_nodes() - 1; i > 0; i--) {
    Node* node = pred_block->get_node(i);
    MachNode* maybe_mach = node->isa_Mach();
    if (!maybe_mach) {
      return false;
    }

    if (maybe_mach->is_MachBranch() || maybe_mach->is_MachProj()) {
      // Skip these nodes because they have no impact on RFLAGS
      continue;
    }

    if (same_ops(ra_, current, maybe_mach)) {
      maybe_redundant = maybe_mach;
      break;
    }

    return false;
  }

  if (maybe_redundant == nullptr) {
    // We didn't find any redundant nodes, so we return early.
    return false;
  }

  current->replace_by(maybe_redundant);
  current->set_removed();
  block->remove_node(block_index);
  cfg_->map_node_to_block(current, nullptr);
  return true;
}

#endif // COMPILER2
