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

/*
 * @test
 * @summary Verify UseLSE option processing on AArch64.
 * @library /test/lib /
 * @requires os.arch == "aarch64"
 * @requires vm.flagless
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.arguments.TestUseLSEOnAArch64
 */

package compiler.arguments;

import jdk.test.lib.Asserts;
import jdk.test.lib.cli.CommandLineOptionTest;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.cpuinfo.CPUInfo;

public class TestUseLSEOnAArch64 {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final String OPTION_NAME = "UseLSE";
    private static final String WARNING_MESSAGE =
        "UseLSE specified, but not supported on this CPU";

    public static void main(String[] args) throws Throwable {
        boolean hasLSE = CPUInfo.hasFeature("lse");
        Boolean useLSE = WB.getBooleanVMFlag(OPTION_NAME);

        System.out.println("CPU has LSE: " + hasLSE);
        System.out.println("UseLSE flag: " + useLSE);

        if (hasLSE) {
            Asserts.assertTrue(useLSE != null && useLSE,
                "UseLSE should be auto-enabled when CPU supports LSE atomics");
            return;
        }

        Asserts.assertTrue(useLSE == null || !useLSE,
            "UseLSE should be false when CPU does not support LSE atomics");

        CommandLineOptionTest.verifyOptionValueForSameVM(
            OPTION_NAME, "false",
            "UseLSE should be false on unsupported CPU even if enabled",
            CommandLineOptionTest.prepareBooleanFlag(OPTION_NAME, true));
    }
}
