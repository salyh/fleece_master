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

import java.io.CharArrayReader;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonStructure;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.json.stream.JsonParserFactory;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class BenchmarkStreamParser {

    protected Charset utf8Charset = Charset.forName("UTF8");

    protected Map<String, ?> getConfig() {
        return Collections.EMPTY_MAP;
    }

    protected Charset getUtfCharset() {
        return utf8Charset;
    }

    public BenchmarkStreamParser() {
        super();
        if (!Charset.defaultCharset().equals(Charset.forName("UTF-8"))) {
            throw new RuntimeException("Default charset is " + Charset.defaultCharset() + ", must must be UTF-8");
        }
    }

    protected final JsonParserFactory parserFactory = Json.createParserFactory(getConfig());
    protected final JsonReaderFactory readerFactory = Json.createReaderFactory(getConfig());

    protected Object parse(final InputStream stream, final Blackhole bh) throws Exception {
        final JsonParser parser = parserFactory.createParser(stream, getUtfCharset());

        while (parser.hasNext()) {
            final Event e = parser.next();
            bh.consume(e);
        }
        parser.close();
        return parser;
    }

    protected Object parse(final Reader reader, final Blackhole bh) throws Exception {
        final JsonParser parser = parserFactory.createParser(reader);

        while (parser.hasNext()) {
            final Event e = parser.next();
            bh.consume(e);
        }
        parser.close();
        return parser;
    }

    protected Object read(final InputStream stream, final Blackhole bh) throws Exception {
        final JsonReader jreader = readerFactory.createReader(stream, getUtfCharset());
        final JsonStructure js = jreader.read();
        bh.consume(js);
        jreader.close();
        return jreader;
    }

    protected Object read(final Reader reader, final Blackhole bh) throws Exception {
        final JsonReader jreader = readerFactory.createReader(reader);
        final JsonStructure js = jreader.read();
        bh.consume(js);
        jreader.close();
        return jreader;
    }

    //-- parse bytes
    /*
        @Benchmark
        public void parseOnly1kBytes(final Blackhole bh) throws Exception {
            bh.consume(parse(new ByteArrayInputStream(Buffers.B_1K), bh));
        }

        @Benchmark
        public void parseOnly3kBytes(final Blackhole bh) throws Exception {
            bh.consume(parse(new ByteArrayInputStream(Buffers.B_3K), bh));
        }

        @Benchmark
        public void parseOnly10kBytes(final Blackhole bh) throws Exception {
            bh.consume(parse(new ByteArrayInputStream(Buffers.B_10K), bh));
        }

        @Benchmark
        public void parseOnly100kBytes(final Blackhole bh) throws Exception {
            bh.consume(parse(new ByteArrayInputStream(Buffers.B_100K), bh));
        }

        @Benchmark
        public void parseOnly1000kBytes(final Blackhole bh) throws Exception {
            bh.consume(parse(new ByteArrayInputStream(Buffers.B_1000K), bh));
        }

        @Benchmark
        public void parseOnly100000kBytes(final Blackhole bh) throws Exception {
            bh.consume(parse(new ByteArrayInputStream(Buffers.B_100000K), bh));
        }
    */
    //-- parse chars

    @Benchmark
    public void parseOnly1kChars(final Blackhole bh) throws Exception {
        bh.consume(parse(new CharArrayReader(Buffers.C_1K), bh));
    }

    @Benchmark
    public void parseOnly3kChars(final Blackhole bh) throws Exception {
        bh.consume(parse(new CharArrayReader(Buffers.C_3K), bh));
    }

    @Benchmark
    public void parseOnly10kChars(final Blackhole bh) throws Exception {
        bh.consume(parse(new CharArrayReader(Buffers.C_10K), bh));
    }

    @Benchmark
    public void parseOnly100kChars(final Blackhole bh) throws Exception {
        bh.consume(parse(new CharArrayReader(Buffers.C_100K), bh));
    }

    @Benchmark
    public void parseOnly1000kChars(final Blackhole bh) throws Exception {
        bh.consume(parse(new CharArrayReader(Buffers.C_1000K), bh));
    }

    @Benchmark
    public void parseOnly100000kChars(final Blackhole bh) throws Exception {
        bh.consume(parse(new CharArrayReader(Buffers.C_100000K), bh));
    }

    //-- read bytes to structure
    /*
        @Benchmark
        public void read1kBytes(final Blackhole bh) throws Exception {
            bh.consume(read(new ByteArrayInputStream(Buffers.B_1K), bh));
        }

        @Benchmark
        public void read_3kBytes(final Blackhole bh) throws Exception {
            bh.consume(read(new ByteArrayInputStream(Buffers.B_3K), bh));
        }

        @Benchmark
        public void read10kBytes(final Blackhole bh) throws Exception {
            bh.consume(read(new ByteArrayInputStream(Buffers.B_10K), bh));
        }

        @Benchmark
        public void read100kBytes(final Blackhole bh) throws Exception {
            bh.consume(read(new ByteArrayInputStream(Buffers.B_100K), bh));
        }

        @Benchmark
        public void read1000kBytes(final Blackhole bh) throws Exception {
            bh.consume(read(new ByteArrayInputStream(Buffers.B_1000K), bh));
        }

        @Benchmark
        public void read100000kBytes(final Blackhole bh) throws Exception {
            bh.consume(read(new ByteArrayInputStream(Buffers.B_100000K), bh));
        }

        //-- read chars to structure

        @Benchmark
        public void read1kChars(final Blackhole bh) throws Exception {
            bh.consume(read(new CharArrayReader(Buffers.C_1K), bh));
        }
    */
    @Benchmark
    public void read3kChars(final Blackhole bh) throws Exception {
        bh.consume(read(new CharArrayReader(Buffers.C_3K), bh));
    }

    @Benchmark
    public void read10kChars(final Blackhole bh) throws Exception {
        bh.consume(read(new CharArrayReader(Buffers.C_10K), bh));
    }

    @Benchmark
    public void read100kChars(final Blackhole bh) throws Exception {
        bh.consume(read(new CharArrayReader(Buffers.C_100K), bh));
    }

    @Benchmark
    public void read1000kChars(final Blackhole bh) throws Exception {
        bh.consume(read(new CharArrayReader(Buffers.C_1000K), bh));
    }

    @Benchmark
    public void read100000kChars(final Blackhole bh) throws Exception {
        bh.consume(read(new CharArrayReader(Buffers.C_100000K), bh));
    }

    @Benchmark
    public void parseOnlyCombinedChars(final Blackhole bh) throws Exception {
        bh.consume(parse(new CharArrayReader(Buffers.C_100K), bh));
        bh.consume(parse(new CharArrayReader(Buffers.C_100K), bh));
        bh.consume(parse(new CharArrayReader(Buffers.C_100K), bh));
        bh.consume(parse(new CharArrayReader(Buffers.C_100K), bh));
        bh.consume(parse(new CharArrayReader(Buffers.C_100K), bh));

    }

    /*   @Benchmark
       public void parseOnlyCombinedBytes(final Blackhole bh) throws Exception {
           bh.consume(parse(new ByteArrayInputStream(Buffers.B_100K), bh));
           bh.consume(parse(new ByteArrayInputStream(Buffers.B_100K), bh));
           bh.consume(parse(new ByteArrayInputStream(Buffers.B_100K), bh));
           bh.consume(parse(new ByteArrayInputStream(Buffers.B_100K), bh));
           bh.consume(parse(new ByteArrayInputStream(Buffers.B_100K), bh));

       }
    */
    @Benchmark
    public void readCombinedChars(final Blackhole bh) throws Exception {
        bh.consume(read(new CharArrayReader(Buffers.C_100K), bh));
        bh.consume(read(new CharArrayReader(Buffers.C_100K), bh));
        bh.consume(read(new CharArrayReader(Buffers.C_100K), bh));
        bh.consume(read(new CharArrayReader(Buffers.C_100K), bh));
        bh.consume(read(new CharArrayReader(Buffers.C_100K), bh));

    }
    /*
        @Benchmark
        public void readCombinedBytes(final Blackhole bh) throws Exception {
            bh.consume(read(new ByteArrayInputStream(Buffers.B_100K), bh));
            bh.consume(read(new ByteArrayInputStream(Buffers.B_100K), bh));
            bh.consume(read(new ByteArrayInputStream(Buffers.B_100K), bh));
            bh.consume(read(new ByteArrayInputStream(Buffers.B_100K), bh));
            bh.consume(read(new ByteArrayInputStream(Buffers.B_100K), bh));

        }
        */
}
