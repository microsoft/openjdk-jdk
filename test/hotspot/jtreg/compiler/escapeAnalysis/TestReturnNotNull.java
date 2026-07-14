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
 */

package compiler.escapeAnalysis;

import compiler.lib.ir_framework.*;

/**
 * @test
 * @summary Test that C2 eliminates null checks on return values when possible.
 * @library /test/lib /
 * @run driver compiler.escapeAnalysis.TestReturnNotNull
 */
public class TestReturnNotNull {
    static Object leaked;
    static int sideEffect = 5;

    @DontInline
    static Object allocateObject() {
        return new Object();
    }

    @DontInline
    static int[] allocateArray(int len) {
        return new int[len];
    }

    static class Builder {
        int value;

        @DontInline
        Builder setValue(int v) {
            this.value = v;
            return this;
        }

        @DontInline
        int getValue() {
            return value;
        }
    }

    @DontInline
    static Object identity(Object o) {
        return o;
    }

    @DontInline
    static Object allocateOnBothBranches(boolean flag) {
        if (flag) {
            return new Object();
        } else {
            return new Object();
        }
    }

    @DontInline
    static Object maybeNull(boolean flag) {
        return flag ? new Object() : null;
    }

    @DontInline
    static Object allocateButLeaks() {
        Object o = new Object();
        leaked = o;
        return o;
    }

    @Test
    @IR(failOn = IRNode.NULL_CHECK, phase = CompilePhase.FINAL_CODE)
    static int testAllocateObject() {
        Object o = allocateObject();
        return o.hashCode();
    }

    @Test
    @IR(failOn = IRNode.NULL_CHECK, phase = CompilePhase.FINAL_CODE)
    static int testAllocateArray() {
        int[] a = allocateArray(10);
        return a.length;
    }

    @Test
    @IR(failOn = IRNode.NULL_CHECK, phase = CompilePhase.FINAL_CODE)
    static int testAllocateOnBothBranches() {
        Object o = allocateOnBothBranches(sideEffect > 0);
        return o.hashCode();
    }

    @Test
    @IR(failOn = IRNode.NULL_CHECK, phase = CompilePhase.FINAL_CODE)
    static int testReturnsThis() {
        Builder b = new Builder();
        return b.setValue(42).getValue();
    }

    @Test
    @IR(failOn = IRNode.NULL_CHECK, phase = CompilePhase.FINAL_CODE)
    static int testReturnsThisChained() {
        Builder b = new Builder();
        return b.setValue(1).setValue(2).getValue();
    }

    @Test
    @IR(failOn = IRNode.NULL_CHECK, phase = CompilePhase.FINAL_CODE)
    static int testIdentityOnNonNull() {
        Object o = identity(new Object());
        return o.hashCode();
    }

    @Test
    @IR(counts = {IRNode.NULL_CHECK, "1"}, phase = CompilePhase.FINAL_CODE)
    static int testMaybeNull() {
        Object o = maybeNull(sideEffect > 0);
        return o.hashCode();
    }

    @Test
    @IR(counts = {IRNode.NULL_CHECK, "1"}, phase = CompilePhase.FINAL_CODE)
    static int testAllocateButLeaks() {
        Object o = allocateButLeaks();
        return o.hashCode();
    }

    @Test
    @IR(counts = {IRNode.NULL_CHECK, "1"}, phase = CompilePhase.FINAL_CODE)
    static int testIdentityOnMaybeNull() {
        Object o = identity(maybeNull(sideEffect > 0));
        return o.hashCode();
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-TieredCompilation");
    }
}
