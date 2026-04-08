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

/* @test
 * @summary Functional test for GetFileInformationByName fast paths
 * @requires os.family == "windows"
 * @run main GetFileInformationByName
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GetFileInformationByName {

    static int failures = 0;

    static void check(boolean cond, String msg) {
        if (!cond) {
            System.err.println("FAIL: " + msg);
            failures++;
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("gfinfo");
        try {
            testRegularFile(dir);
            testDirectory(dir);
            testNonExistent(dir);
            testLargeFile(dir);
            testSymlink(dir);
            testIsSameFile(dir);
        } finally {
            try {
                Files.walk(dir)
                     .sorted(java.util.Comparator.reverseOrder())
                     .forEach(p -> {
                         try { Files.deleteIfExists(p); }
                         catch (IOException ignore) {}
                     });
            } catch (IOException ignore) {}
        }

        if (failures > 0) {
            throw new RuntimeException(failures + " test(s) failed");
        }
    }

    static void testRegularFile(Path dir) throws Exception {
        Path file = dir.resolve("regular.txt");
        Files.writeString(file, "hello");

        File f = file.toFile();
        check(f.exists(),        "regular file should exist");
        check(f.isFile(),        "should be a regular file");
        check(!f.isDirectory(),  "should not be a directory");
        check(f.length() == 5,   "length should be 5, got " + f.length());
        check(f.canRead(),       "should be readable");
        check(f.lastModified() > 0, "lastModified should be positive");
    }

    static void testDirectory(Path dir) throws Exception {
        Path sub = dir.resolve("subdir");
        Files.createDirectory(sub);

        File d = sub.toFile();
        check(d.exists(),        "directory should exist");
        check(d.isDirectory(),   "should be a directory");
        check(!d.isFile(),       "directory should not be a file");
    }

    static void testNonExistent(Path dir) throws Exception {
        File ghost = dir.resolve("no_such_file.txt").toFile();
        check(!ghost.exists(),     "ghost should not exist");
        check(!ghost.isFile(),     "ghost should not be a file");
        check(!ghost.isDirectory(),"ghost should not be a directory");
        check(ghost.length() == 0, "ghost length should be 0");
    }

    static void testLargeFile(Path dir) throws Exception {
        Path large = dir.resolve("large.bin");
        long targetSize = 5L * 1024 * 1024 * 1024; // 5 GiB
        try (var raf = new java.io.RandomAccessFile(large.toFile(), "rw")) {
            raf.setLength(targetSize);
        } catch (IOException e) {
            System.out.println("  Skipped testLargeFile: " + e);
            return;
        }
        try {
            File f = large.toFile();
            check(f.exists(), "large file should exist");
            check(f.length() == targetSize,
                  "large file length should be " + targetSize + ", got " + f.length());
        } finally {
            Files.deleteIfExists(large);
        }
    }

    static void testSymlink(Path dir) throws Exception {
        Path target = dir.resolve("symtarget.txt");
        Files.writeString(target, "symlink content");
        Path link = dir.resolve("symlink.lnk");

        // We may run into unrelated failures when creating symbolic links
        // (e.g. FAT32 does not support symbolic links or that the user may lack
        // the privilge), so if we run into such problems, we just ignore them.
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            System.out.println("  Skipped: cannot create symlinks (" + e + ")");
            return;
        }

        File lf = link.toFile();
        check(lf.exists(),       "symlink should exist (follows target)");
        check(lf.isFile(),       "symlink target should be a file");
        check(!lf.isDirectory(), "symlink target should not be a directory");
        check(lf.length() == 15,
              "symlink target length should be 15, got " + lf.length());

        var attrs = Files.readAttributes(link,
                java.nio.file.attribute.BasicFileAttributes.class);
        check(!attrs.isSymbolicLink(), "followLinks=true: not a symlink");
        check(attrs.isRegularFile(),   "followLinks=true: regular file");
        check(attrs.size() == 15,
              "NIO size should be 15, got " + attrs.size());

        var lattrs = Files.readAttributes(link,
                java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        check(lattrs.isSymbolicLink(), "nofollow: should be a symlink");
    }

    static void testIsSameFile(Path dir) throws Exception {
        Path file1 = dir.resolve("samefile1.txt");
        Path file2 = dir.resolve("samefile2.txt");
        Files.writeString(file1, "a");
        Files.writeString(file2, "b");

        // Same path should be same file
        check(Files.isSameFile(file1, file1),
              "same path should be same file");

        // Different files should not be same
        check(!Files.isSameFile(file1, file2),
              "different files should not be same file");

        // Same file via different path (case variation on NTFS)
        Path upper = dir.resolve("SAMEFILE1.TXT");
        if (Files.exists(upper)) {
            check(Files.isSameFile(file1, upper),
                  "case-variant path should be same file on NTFS");
        }

        // isSameFile through a symlink
        Path slink = dir.resolve("samelink");

        // We may run into unrelated failures when creating symbolic links
        // (e.g. FAT32 does not support symbolic links or that the user may lack
        // the privilge), so if we run into such problems, we just ignore them.
        try {
            Files.createSymbolicLink(slink, file1);
            check(Files.isSameFile(file1, slink),
                  "symlink and target should be same file");
            check(!Files.isSameFile(file2, slink),
                  "symlink to file1 should not be same as file2");
        } catch (UnsupportedOperationException | IOException e) {
            System.out.println("  Symlink part skipped: " + e);
        }
    }
}
