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
 */

/*
 * @test
 * @summary Verify SVE/SVE2 feature detection for both Windows and Linux.
 *
 * @requires os.arch == "aarch64" & vm.compiler2.enabled
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller
 *             jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI compiler.c2.aarch64.TestSVEFeatureDetection
 */

package compiler.c2.aarch64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;

public class TestSVEFeatureDetection {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final String KEY_USE_SVE    = "UseSVE=";
    private static final String KEY_MAX_VECTOR = "MaxVectorSize=";
    private static final String KEY_HAS_SVE    = "has_sve=";
    private static final String KEY_HAS_SVE2   = "has_sve2=";

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("flagCheck")) {
            printFlags();
        } else {
            runDriver();
        }
    }

    private static void printFlags() {
        int sveLevel       = WB.getUintVMFlag("UseSVE").intValue();
        long maxVectorSize = WB.getIntxVMFlag("MaxVectorSize");
        List<String> features = Arrays.asList(WB.getCPUFeatures().split(", "));
        boolean hasSve  = features.contains("sve");
        boolean hasSve2 = features.contains("sve2");

        System.out.println(KEY_USE_SVE    + sveLevel);
        System.out.println(KEY_MAX_VECTOR + maxVectorSize);
        System.out.println(KEY_HAS_SVE    + hasSve);
        System.out.println(KEY_HAS_SVE2   + hasSve2);
    }

    private static void runDriver() throws Exception {
        int sveLevel       = WB.getUintVMFlag("UseSVE").intValue();
        long maxVectorSize = WB.getIntxVMFlag("MaxVectorSize");
        List<String> features = Arrays.asList(WB.getCPUFeatures().split(", "));
        boolean hasSve  = features.contains("sve");
        boolean hasSve2 = features.contains("sve2");

        // If SVE is not present, just verify a consistent disabled state.
        if (!hasSve) {
            Asserts.assertEquals(sveLevel, 0,
                "UseSVE must be 0 when hardware lacks SVE");
            Asserts.assertFalse(hasSve2,
                "sve2 must be absent when sve is absent");
            return;
        }

        Asserts.assertTrue(sveLevel > 0,
            "UseSVE should be auto-set to > 0 when SVE hardware is present");
        Asserts.assertTrue(maxVectorSize >= 16,
            "MaxVectorSize must be >= 16 for SVE, got " + maxVectorSize);
        Asserts.assertTrue(Long.bitCount(maxVectorSize) == 1,
            "MaxVectorSize must be a power of two, got " + maxVectorSize);
        Asserts.assertTrue(maxVectorSize % 16 == 0,
            "MaxVectorSize must be a multiple of 16, got " + maxVectorSize);
        Asserts.assertTrue(maxVectorSize <= 256,
            "MaxVectorSize must be <= 256 (2048 bits), got " + maxVectorSize);

        if (hasSve2) {
            Asserts.assertEquals(sveLevel, 2,
                "UseSVE should be 2 when hardware supports SVE2");
        } else {
            Asserts.assertEquals(sveLevel, 1,
                "UseSVE should be 1 when hardware supports SVE but not SVE2");
        }

        OutputAnalyzer out = spawnFlagCheck("-XX:UseSVE=0");
        out.shouldHaveExitValue(0);
        out.shouldContain(KEY_USE_SVE  + "0");
        out.shouldContain(KEY_HAS_SVE  + "false");
        out.shouldContain(KEY_HAS_SVE2 + "false");

        out = spawnFlagCheck("-XX:UseSVE=1", "-XX:MaxVectorSize=512");
        out.shouldHaveExitValue(0);
        out.shouldContain("warning");

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        out = spawnFlagCheck("-XX:UseSVE=1", "-XX:MaxVectorSize=16");
        out.shouldHaveExitValue(0);
        if (isWindows && maxVectorSize > 16) {
            out.shouldContain("warning");
            out.shouldContain(KEY_MAX_VECTOR + maxVectorSize);
        } else {
            out.shouldContain(KEY_MAX_VECTOR + "16");
        }

        if (hasSve2) {
            out = spawnFlagCheck("-XX:UseSVE=2");
            out.shouldHaveExitValue(0);
            out.shouldContain(KEY_USE_SVE  + "2");
            out.shouldContain(KEY_HAS_SVE  + "true");
            out.shouldContain(KEY_HAS_SVE2 + "true");

            out = spawnFlagCheck("-XX:UseSVE=1");
            out.shouldHaveExitValue(0);
            out.shouldContain(KEY_USE_SVE  + "1");
            out.shouldContain(KEY_HAS_SVE  + "true");
            out.shouldContain(KEY_HAS_SVE2 + "false");
        } else {
            out = spawnFlagCheck("-XX:UseSVE=2");
            out.shouldHaveExitValue(0);
            out.shouldContain("SVE2 specified, but not supported on current CPU");
            out.shouldContain(KEY_USE_SVE  + "1");
            out.shouldContain(KEY_HAS_SVE  + "true");
            out.shouldContain(KEY_HAS_SVE2 + "false");
        }

        out = spawnFlagCheck("-XX:UseSVE=1");
        out.shouldHaveExitValue(0);
        out.shouldContain(KEY_USE_SVE + "1");
        out.shouldContain(KEY_HAS_SVE + "true");
        out.shouldMatch("MaxVectorSize=\\d+");

        if (maxVectorSize >= 32) {
            out = spawnFlagCheck("-XX:UseSVE=1", "-XX:MaxVectorSize=32");
            out.shouldHaveExitValue(0);
            if (isWindows && maxVectorSize > 32) {
                out.shouldContain("warning");
                out.shouldContain(KEY_MAX_VECTOR + maxVectorSize);
            } else {
                out.shouldContain(KEY_MAX_VECTOR + "32");
            }
        }
    }

    private static OutputAnalyzer spawnFlagCheck(String... extraFlags)
            throws Exception {
        List<String> args = new ArrayList<>();
        args.add("-Xbootclasspath/a:.");
        args.add("-XX:+UnlockDiagnosticVMOptions");
        args.add("-XX:+WhiteBoxAPI");
        for (String f : extraFlags) {
            args.add(f);
        }
        args.add(TestSVEFeatureDetection.class.getName());
        args.add("flagCheck");

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                                args.toArray(new String[0]));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        return output;
    }
}
