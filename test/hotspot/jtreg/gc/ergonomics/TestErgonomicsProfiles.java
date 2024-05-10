/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

package gc.ergonomics;

/*
 * @test TestErgonomicsProfiles
 * @bug 8017462
 * @summary Ensure that ErgonomicsProfiles selects the desired profile
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI gc.ergonomics.TestErgonomicsProfiles
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Platform;
import jdk.test.lib.Utils;

import jtreg.SkippedException;
import jdk.test.whitebox.gc.GC;

/*
 * @test TestErgonomicsProfiles
 * @summary Ensure that ErgonomicsProfile fails with unsupported profile
 * @requires docker.support
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI gc.ergonomics.TestErgonomicsProfiles
 */
public class TestErgonomicsProfiles {

  private final static String imageNameAndTag = "ergoimage:latest";

  private static final String DEDICATED_PROFILE = "dedicated";
  private static final String SHARED_PROFILE = "shared";
  private static final String AUTO_SELECTION = "auto";

  public static void main(String[] args) throws Exception {
    testInvalidProfile();

    // Test manual selection
    testErgonomicProfile(DEDICATED_PROFILE, DEDICATED_PROFILE);
    testErgonomicProfile(SHARED_PROFILE, SHARED_PROFILE);

    testDefaultErgonomicProfile(SHARED_PROFILE); // default profile is shared. Change if default changes.

    // Tests inside containers
    try {
      // Prepare container image
      prepareContainerImage();

      // Test automatic selection and default, inside container
      testProfileInsideContainer(AUTO_SELECTION, DEDICATED_PROFILE);

      // Test GC selection
      // See GC selection in gcConfig.cpp::select_gc_ergonomically_dedicated
      testGCSelection(DEDICATED_PROFILE, "UseSerialGC", 1, 1024); // no matter how much memory, use serial if 1 proc
      testGCSelection(DEDICATED_PROFILE, "UseParallelGC", 2, 1024); // <=2G, use Parallel
      testGCSelection(DEDICATED_PROFILE, "UseG1GC", 2, 2 * 1024 + 1); // above >2GB, use G1

      // Test Heap Size allocation (MaxRAMPercentage)
      // See heap size MaxRAMPercentage ergo selection for dedicated in
      // arguments.cpp::set_ergonomics_profiles_heap_size_max_ram_percentage
      // Test MaxRAMPercentage selection for dedicated profile
      testMaxRAMPercentageSelection(DEDICATED_PROFILE, "50.0", 511); // less than 0.5G, MaxRAMPercentage should be 50.0
      // MaxRAMPercentage should be 75.0 between => 0.5G to <4G
      testMaxRAMPercentageSelection(DEDICATED_PROFILE, "75.0", 512); 
      testMaxRAMPercentageSelection(DEDICATED_PROFILE, "75.0", 4 * 1024 - 1);
      // MaxRAMPercentage should be 80.0 between => 4G to <6G
      testMaxRAMPercentageSelection(DEDICATED_PROFILE, "80.0", 4 * 1024);
      testMaxRAMPercentageSelection(DEDICATED_PROFILE, "80.0", 6 * 1024 - 1);
      // MaxRAMPercentage should be 85.0 between => 6G to <16G
      testMaxRAMPercentageSelection(DEDICATED_PROFILE, "85.0", 6 * 1024);
      testMaxRAMPercentageSelection(DEDICATED_PROFILE, "85.0", 16 * 1024 - 1);
      // MaxRAMPercentage should be 90.0 between => 16G or more
      // testMaxRAMPercentageSelection(DEDICATED_PROFILE, "90.0", 16 * 1024);
    }  finally {
      if (!DockerTestUtils.RETAIN_IMAGE_AFTER_TEST) {
        DockerTestUtils.removeDockerImage(imageNameAndTag);
      }
    }
  }

  private static void testInvalidProfile() throws Exception {
    String[] baseArgs = { "-XX:ErgonomicsProfile=invalid", "-XX:+PrintFlagsFinal", "-version" };

    // Base test with invalid ergonomics profile
    OutputAnalyzer output = ProcessTools.executeLimitedTestJava(baseArgs);
    output
        .shouldHaveExitValue(1)
        .stderrContains("Unsupported ErgonomicsProfile: invalid");
  }

  private static void testDefaultErgonomicProfile(String expectedProfile) throws Exception {
    String[] baseArgs = { "-XX:+PrintFlagsFinal", "-version" };

    // Base test with default ergonomics profile
    ProcessTools.executeLimitedTestJava(baseArgs)
        .shouldHaveExitValue(0)
        .stdoutShouldMatch("ErgonomicsProfile.*" + expectedProfile);
  }

  private static void testErgonomicProfile(String ergonomicsProfile, String expectedProfile) throws Exception {
    String[] baseArgs = { "-XX:ErgonomicsProfile=" + ergonomicsProfile, "-XX:+PrintFlagsFinal", "-version" };

    // Base test with selected ergonomics profile
    ProcessTools.executeLimitedTestJava(baseArgs)
        .shouldHaveExitValue(0)
        .stdoutShouldMatch("ErgonomicsProfile.*" + expectedProfile);
  }

  private static void prepareContainerImage() throws Exception {
    if (!DockerTestUtils.canTestDocker()) {
      System.out.println("Docker not available, skipping test");
      throw new SkippedException("Docker not available");
    }

    String dockerfile = "FROM --platform=linux/amd64 ubuntu:latest\n" +
        "COPY /jdk /jdk\n" +
        "ENV JAVA_HOME=/jdk\n" +
        "CMD [\"/bin/bash\"]\n";

    DockerTestUtils.buildJdkContainerImage(imageNameAndTag, dockerfile);
  }

  private static void testProfileInsideContainer(String ergonomicsProfile, String expectedProfile) throws Exception {
    String[] javaOpts = { "-XX:ErgonomicsProfile=" + ergonomicsProfile, "-XX:+PrintFlagsFinal" };

    DockerRunOptions opts = new DockerRunOptions(imageNameAndTag, "/jdk/bin/java", "-version", javaOpts);
    opts.addDockerOpts("--rm");

    DockerTestUtils.dockerRunJava(opts)
        .shouldHaveExitValue(0)
        .stdoutShouldMatch("ErgonomicsProfile.*" + expectedProfile);
  }

  public static void testGCSelection(String profile, String expectedGC, int cpuCount, int memoryInMB) throws Exception {
    String[] javaOpts = { "-XX:ErgonomicsProfile=" + profile, "-XX:+PrintFlagsFinal" };

    DockerRunOptions opts = new DockerRunOptions(imageNameAndTag, "/jdk/bin/java", "-version", javaOpts);
    opts.addDockerOpts("--rm", "--cpus", String.valueOf(cpuCount), "--memory", memoryInMB + "m");

    OutputAnalyzer output = DockerTestUtils.dockerRunJava(opts);
    output.shouldHaveExitValue(0);

    // Check GC
    output.stdoutShouldMatch(expectedGC + ".*true");
  }

  private static void testMaxRAMPercentageSelection(String profile, String expectedMaxRAMPercentage, int physMem)
      throws Exception {
    String[] javaOpts = { "-XX:ErgonomicsProfile=" + profile, "-XX:+PrintFlagsFinal" };

    DockerRunOptions opts = new DockerRunOptions(imageNameAndTag, "/jdk/bin/java", "-version", javaOpts);
    opts.addDockerOpts("--rm", "--memory", physMem + "m");

    // Run JVM with the given arguments
    OutputAnalyzer output = DockerTestUtils.dockerRunJava(opts);
    output.shouldHaveExitValue(0);

    // Check that MaxRAMPercentage is set to the expected value
    output.shouldHaveExitValue(0);
    output.stdoutShouldMatch("MaxRAMPercentage.*" + expectedMaxRAMPercentage);
  }

}
