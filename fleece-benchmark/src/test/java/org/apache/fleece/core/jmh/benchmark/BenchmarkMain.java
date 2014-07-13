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

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

public class BenchmarkMain {

    static {

        try {
            CreateJsonTestFiles.create();
        } catch (final Exception e) {
            e.printStackTrace();
        }

        //initialize Buffers
        Buffers.init();
    }

    private static final String REGEX = ".*";
    private static final String MEMORY = "4096m";

    public static void main(final String[] args) throws Exception {
        //run(1,1,1,3);
        run(1, 2, 3, 5); //1 fork, 2 threads, 3 warmup, 5 measurement 
        //run(1, 4, 3, 5);
        //run(2,8,5,10);
        //run(2,16,3,5);
    }

    public static void run(final int forks, final int threads, final int warmupit, final int measureit) throws Exception {

        long start = System.currentTimeMillis();
        Options opt = new OptionsBuilder().include(REGEX).forks(forks).warmupIterations(warmupit).measurementIterations(measureit)
                .threads(threads).mode(Mode.AverageTime).timeUnit(TimeUnit.MICROSECONDS).verbosity(VerboseMode.EXTRA)
                .jvmArgs("-Xmx"+MEMORY, "-Dfile.encoding=utf-8")
                .resultFormat(ResultFormatType.TEXT)
                .result(String.format("avg_benchmark_jmh_result_f%d_t%d_w%d_i%d.txt", forks, threads, warmupit, measureit))
                .output(String.format("avg_benchmark_jmh_log_f%d_t%d_w%d_i%d.txt", forks, threads, warmupit, measureit))

                .build();

        new Runner(opt).run();

        opt = new OptionsBuilder().include(REGEX).forks(forks).warmupIterations(warmupit).measurementIterations(measureit)
                .threads(threads).mode(Mode.Throughput).timeUnit(TimeUnit.SECONDS).verbosity(VerboseMode.EXTRA)
                .jvmArgs("-Xmx" + MEMORY, "-Dfile.encoding=utf-8")
                .resultFormat(ResultFormatType.TEXT)
                .result(String.format("thr_benchmark_jmh_result_f%d_t%d_w%d_i%d.txt", forks, threads, warmupit, measureit))
                .output(String.format("thr_benchmark_jmh_log_f%d_t%d_w%d_i%d.txt", forks, threads, warmupit, measureit))

                .build();

        new Runner(opt).run();
        long end = System.currentTimeMillis();
        
        System.out.println("End. Runtime was "+((end-start)/(60*1000))+" min.");
        
    }

}
