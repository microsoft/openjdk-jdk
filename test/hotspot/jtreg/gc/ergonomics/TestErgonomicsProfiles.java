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

  public static void main(String[] args) throws Exception {  
    testInvalidProfile();

    // Test manual selection
    testErgonomicProfile("dedicated", "dedicated");
    testErgonomicProfile("shared", "shared");
    testDefaultErgonomicProfile("shared"); // default profile is shared. Change if default changes.

    // Test automatic selection and default, inside container
    testInsideContainer("auto", "shared"); // default profile is shared even inside containers. Change if default changes.

    // TODO: test GC selection and Heap Size
  }

  private static void testInvalidProfile() throws Exception {
    String[] baseArgs = {"-XX:ErgonomicsProfile=invalid", "-XX:+PrintFlagsFinal", "-version"};

    // Base test with invalid ergonomics profile
    OutputAnalyzer output = ProcessTools.executeLimitedTestJava(baseArgs);
    output
      .shouldHaveExitValue(1)
      .stderrContains("Unsupported ErgonomicsProfile: invalid");
  }

  private static void testDefaultErgonomicProfile(String expectedProfile) throws Exception {
    String[] baseArgs = {"-XX:+PrintFlagsFinal", "-version"};

    // Base test with default ergonomics profile
    ProcessTools.executeLimitedTestJava(baseArgs)
      .shouldHaveExitValue(0)
      .stdoutShouldMatch("ErgonomicsProfile.*" + expectedProfile);
  }

  private static void testErgonomicProfile(String ergonomicsProfile, String expectedProfile) throws Exception {
    String[] baseArgs = {"-XX:ErgonomicsProfile=" + ergonomicsProfile, "-XX:+PrintFlagsFinal", "-version"};

    // Base test with selected ergonomics profile
    ProcessTools.executeLimitedTestJava(baseArgs)
      .shouldHaveExitValue(0)
      .stdoutShouldMatch("ErgonomicsProfile.*" + expectedProfile);
  }

  private static void testInsideContainer(String ergonomicsProfile, String expectedProfile) throws Exception {
    String[] javaOpts = {"-XX:ErgonomicsProfile=" + ergonomicsProfile, "-XX:+PrintFlagsFinal"};

    if (!DockerTestUtils.canTestDocker()) {
      System.out.println("Docker not available, skipping test");
      return;
    }

    final String imageNameAndTag = "ergoimage";

    String dockerfile =
      "FROM --platform=linux/amd64 ubuntu:latest\n" +
      "COPY /jdk /jdk\n" +
      "ENV JAVA_HOME=/jdk\n" +
      "CMD [\"/bin/bash\"]\n";

    DockerTestUtils.buildJdkContainerImage(imageNameAndTag, dockerfile);

    try {
        DockerRunOptions opts =
            new DockerRunOptions(imageNameAndTag, "/jdk/bin/java", "-version", javaOpts);
        opts.addDockerOpts("--platform=linux/amd64");

        DockerTestUtils.dockerRunJava(opts)
            .shouldHaveExitValue(0)
            .stdoutShouldMatch("ErgonomicsProfile.*" + expectedProfile);
    
    } finally {
        if (!DockerTestUtils.RETAIN_IMAGE_AFTER_TEST) {
            DockerTestUtils.removeDockerImage(imageNameAndTag);
        }
    }
  }

}
