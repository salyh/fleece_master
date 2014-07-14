/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fleece.core.jmh.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;

public class Buffers {

    private Buffers() {

    }

    public static final byte[] B_1K = readBytes(1);
    public static final byte[] B_3K = readBytes(3);
    public static final byte[] B_10K = readBytes(10);
    public static final byte[] B_100K = readBytes(100);
    public static final byte[] B_1000K = readBytes(1000);
    public static final byte[] B_100000K = readBytes(100000);

    public static final char[] C_1K = readChars(1);
    public static final char[] C_3K = readChars(3);
    public static final char[] C_10K = readChars(10);
    public static final char[] C_100K = readChars(100);
    public static final char[] C_1000K = readChars(1000);
    public static final char[] C_100000K = readChars(100000);

    private static byte[] readBytes(final int count) {

        InputStream in = null;
        try {
            return IOUtils.toByteArray(in = Buffers.class.getResourceAsStream("/bench/generated_benchmark_test_file_" + count + "kb.json"));
        } catch (final IOException e) {
            return null;
        } finally {
            IOUtils.closeQuietly(in);
        }

    }

    private static char[] readChars(final int count) {

        InputStream in = null;
        try {
            return IOUtils.toCharArray(in = Buffers.class.getResourceAsStream("/bench/generated_benchmark_test_file_" + count + "kb.json"),
                    Charset.forName("UTF-8"));
        } catch (final IOException e) {
            return null;
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    public static void init() {

    }

}
