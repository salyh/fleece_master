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

import java.math.BigInteger;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class BenchmarkNumberConversion {

    //integral
    private final char[] C_0 = new char[] { '0' };
    private final char[] C_9 = new char[] { '9' };
    private final char[] C_99 = new char[] { '9', '9' };
    private final char[] C__99 = new char[] { '-', '9', '9' };
    private final char[] C_999 = new char[] { '9', '9', '9' };
    private final char[] C_9999 = new char[] { '9', '9', '9', '9' };
    private final char[] C__9999 = new char[] { '-', '9', '9', '9', '9' };

    @Benchmark
    public void convertIntegralJdkparseInt(final Blackhole bh) throws Exception {
        bh.consume(Integer.parseInt(new String(C_0)));
        bh.consume(Integer.parseInt(new String(C_9)));
        bh.consume(Integer.parseInt(new String(C_99)));
        bh.consume(Integer.parseInt(new String(C__99)));
        bh.consume(Integer.parseInt(new String(C_999)));
        bh.consume(Integer.parseInt(new String(C_9999)));
        bh.consume(Integer.parseInt(new String(C__9999)));
    }

    @Benchmark
    public void convertIntegralJdkparseLong(final Blackhole bh) throws Exception {
        bh.consume(Long.parseLong(new String(C_0)));
        bh.consume(Long.parseLong(new String(C_9)));
        bh.consume(Long.parseLong(new String(C_99)));
        bh.consume(Long.parseLong(new String(C__99)));
        bh.consume(Long.parseLong(new String(C_999)));
        bh.consume(Long.parseLong(new String(C_9999)));
        bh.consume(Long.parseLong(new String(C__9999)));
    }

    @Benchmark
    public void convertIntegralBiparseInt(final Blackhole bh) throws Exception {
        bh.consume(new BigInteger(new String(C_0)).intValue());
        bh.consume(new BigInteger(new String(C_9)).intValue());
        bh.consume(new BigInteger(new String(C_99)).intValue());
        bh.consume(new BigInteger(new String(C__99)).intValue());
        bh.consume(new BigInteger(new String(C_999)).intValue());
        bh.consume(new BigInteger(new String(C_9999)).intValue());
        bh.consume(new BigInteger(new String(C__9999)).intValue());
    }

    @Benchmark
    public void convertIntegralBiparseLong(final Blackhole bh) throws Exception {
        bh.consume(new BigInteger(new String(C_0)).longValue());
        bh.consume(new BigInteger(new String(C_9)).longValue());
        bh.consume(new BigInteger(new String(C_99)).longValue());
        bh.consume(new BigInteger(new String(C__99)).longValue());
        bh.consume(new BigInteger(new String(C_999)).longValue());
        bh.consume(new BigInteger(new String(C_9999)).longValue());
        bh.consume(new BigInteger(new String(C__9999)).longValue());
    }

    @Benchmark
    public void convertIntegralSelfparseInt(final Blackhole bh) throws Exception {
        bh.consume((int) parseLongFromChars((C_0)));
        bh.consume((int) parseLongFromChars((C_9)));
        bh.consume((int) parseLongFromChars((C_99)));
        bh.consume((int) parseLongFromChars((C__99)));
        bh.consume((int) parseLongFromChars((C_999)));
        bh.consume((int) parseLongFromChars((C_9999)));
        bh.consume((int) parseLongFromChars((C__9999)));
    }

    @Benchmark
    public void convertIntegralSelfparseLong(final Blackhole bh) throws Exception {
        bh.consume(parseLongFromChars((C_0)));
        bh.consume(parseLongFromChars((C_9)));
        bh.consume(parseLongFromChars((C_99)));
        bh.consume(parseLongFromChars((C__99)));
        bh.consume(parseLongFromChars((C_999)));
        bh.consume(parseLongFromChars((C_9999)));
        bh.consume(parseLongFromChars((C__9999)));
    }

    private static long parseLongFromChars(final char[] chars) {
        return parseLongFromChars(chars, 0, chars.length - 1);
    }

    private static long parseLongFromChars(final char[] chars, final int start, final int end) {

        if (chars == null || chars.length == 0 || start < 0 || end < start || end > chars.length - 1 || start > chars.length - 1) {
            throw new IllegalArgumentException(chars.length + "/" + start + "/" + end);
        }

        long retVal = 0;
        final boolean negative = chars[start] == '-';
        for (int i = negative ? start + 1 : start; i < end; i++) {

            //int this context we know its an integral number, so skip this due to perf reasons
            /*if (chars[i] < ZERO || chars[i] > NINE) {
                throw new IllegalArgumentException("Not a integral number");
            }*/
            retVal = retVal * 10 + (chars[i] - '-');
        }

        return negative ? -retVal : retVal;
    }

}
