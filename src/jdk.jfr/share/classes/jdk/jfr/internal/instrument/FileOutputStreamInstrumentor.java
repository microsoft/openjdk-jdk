/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.internal.instrument;

import java.io.IOException;

import jdk.jfr.events.FileWriteEvent;
import jdk.jfr.events.FileWriteIOStatisticsEvent;
import jdk.jfr.internal.event.EventConfiguration;
import jdk.jfr.events.EventConfigurations;
import jdk.jfr.internal.instrument.FileIOStatistics;

/**
 * See {@link JITracer} for an explanation of this code.
 */
@JIInstrumentationTarget("java.io.FileOutputStream")
final class FileOutputStreamInstrumentor {

    private FileOutputStreamInstrumentor() {
    }

    private String path;

    @JIInstrumentationMethod
    public void write(int b) throws IOException {
        EventConfiguration eventConfiguration = EventConfigurations.FILE_WRITE;
        EventConfiguration fileWriteIOeventConfiguration = EventConfigurations.FILE_WRITE_IO_STATISTICS;
        long bytesWritten = 0;
        long start = 0;
        long duration = 0;

        if (!eventConfiguration.isEnabled()) {
            start = EventConfiguration.timestamp();
            write(b);
            duration = EventConfiguration.timestamp() - start;  
        }
        else{ 
            try {
                start = EventConfiguration.timestamp();
                write(b);
                bytesWritten = 1;
            } finally {
                duration = EventConfiguration.timestamp() - start;
                if (eventConfiguration.shouldCommit(duration)) {
                    FileWriteEvent.commit(start, duration, path, bytesWritten);
                }
            }
        }

        if(fileWriteIOeventConfiguration.isEnabled()){                 
            FileIOStatistics.setTotalWriteBytesForPeriod(1, duration);
        }
    }

    @JIInstrumentationMethod
    public void write(byte b[]) throws IOException {
        EventConfiguration eventConfiguration = EventConfigurations.FILE_WRITE;
        EventConfiguration fileWriteIOeventConfiguration = EventConfigurations.FILE_WRITE_IO_STATISTICS;
        long bytesWritten = 0;
        long start = 0;
        long duration = 0;

        if (!eventConfiguration.isEnabled()) {
            start = EventConfiguration.timestamp();
            write(b);    
            duration = EventConfiguration.timestamp() - start;  
        }
        else {
            try {
                start = EventConfiguration.timestamp();
                write(b);
                bytesWritten = b.length;
            } finally {
                duration = EventConfiguration.timestamp() - start;
                if (eventConfiguration.shouldCommit(duration)) {
                    FileWriteEvent.commit(start, duration, path, bytesWritten);
                }
            }     
        }

        if(fileWriteIOeventConfiguration.isEnabled()){                 
            FileIOStatistics.setTotalWriteBytesForPeriod(b.length, duration);
        }
    }

    @JIInstrumentationMethod
    public void write(byte b[], int off, int len) throws IOException {
        EventConfiguration eventConfiguration = EventConfigurations.FILE_WRITE;
        EventConfiguration fileWriteIOeventConfiguration = EventConfigurations.FILE_WRITE_IO_STATISTICS;
        long bytesWritten = 0;
        long start = 0;
        long duration = 0;

        if (!eventConfiguration.isEnabled()) {
            start = EventConfiguration.timestamp();
            write(b, off, len);
            duration = EventConfiguration.timestamp() - start;  
        }
        else {
            try {
                start = EventConfiguration.timestamp();
                write(b, off, len);
                bytesWritten = len;
            } finally {
                duration = EventConfiguration.timestamp() - start;
                if (eventConfiguration.shouldCommit(duration)) {
                    FileWriteEvent.commit(start, duration, path, bytesWritten);
                }
            }
        }

        if(fileWriteIOeventConfiguration.isEnabled()){                 
            FileIOStatistics.setTotalWriteBytesForPeriod(len, duration);
        }
       
    }
}
