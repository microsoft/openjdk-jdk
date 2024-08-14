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

#ifndef SHARE_GC_SERIAL_SERIALHEAPCOMPONENTSIZES_HPP
#define SHARE_GC_SERIAL_SERIALHEAPCOMPONENTSIZES_HPP

// Simple class for storing info about the sizing details of the serial heap
class SerialHeapComponentSizes {
private:
  const size_t _eden_size;
  const size_t _survivor_space_size;
  const size_t _tenured_size;
  const size_t _max_eden_size;

public:
  SerialHeapComponentSizes(size_t eden_size,
                           size_t survivor_space_size,
                           size_t tenured_size,
                           size_t max_eden_size = 0)
      : _eden_size(eden_size),
        _survivor_space_size(survivor_space_size),
        _tenured_size(tenured_size),
        _max_eden_size(max_eden_size) { }

  size_t max_eden_size()       const { return _max_eden_size;       }
  size_t eden_size()           const { return _eden_size;           }
  size_t survivor_space_size() const { return _survivor_space_size; }
  size_t tenured_size()        const { return _tenured_size;        }
};

#endif // SHARE_GC_SERIAL_SERIALHEAPCOMPONENTSIZES_HPP
