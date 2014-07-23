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
package org.apache.fleece.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;

import javax.json.JsonException;

class UTFEncodingAwareReader extends InputStreamReader {

    private final PushBackCountingInputStream cin;
    
    public UTFEncodingAwareReader(final InputStream in) {
        this(new PushBackCountingInputStream(in));
    }
    
    private UTFEncodingAwareReader(final PushBackCountingInputStream in) {
        super(in, getCharset(in));
        cin=in;
    }
    
    public long getReadCount() {
        return cin.getReadCount();
    }

    /*
        * RFC 4627

          JSON text SHALL be encoded in Unicode.  The default encoding is
          UTF-8.
       
          Since the first two characters of a JSON text will always be ASCII
          characters [RFC0020], it is possible to determine whether an octet
          stream is UTF-8, UTF-16 (BE or LE), or UTF-32 (BE or LE) by looking
          at the pattern of nulls in the first four octets.

          00 00 00 xx  UTF-32BE
          00 xx 00 xx  UTF-16BE
          xx 00 00 00  UTF-32LE
          xx 00 xx 00  UTF-16LE
          xx xx xx xx  UTF-8

        */

    private static Charset getCharset(final PushbackInputStream inputStream) {
        Charset charset = Charset.forName("UTF-8");
        final byte[] utfBytes = new byte[4];

        try {
            final int read = inputStream.read(utfBytes);
            if (read < 2) {
                throw new JsonException("Invalid Json. Valid Json has at least 2 bytes");
            } else {

                if (utfBytes[0] == 0x00) {
                    charset = (utfBytes[1] == 0x00) ? Charset.forName("UTF-32BE") : Charset.forName("UTF-16BE");
                } else if (read > 2 && utfBytes[1] == 0x00) {
                    charset = (utfBytes[2] == 0x00) ? Charset.forName("UTF-32LE") : Charset.forName("UTF-16LE");
                }

            }
            
            
            inputStream.unread(utfBytes);
            

        } catch (final IOException e) {
            throw new JsonException("Unable to detect charset", e);
        }

        return charset;
    }


    private static class PushBackCountingInputStream extends PushbackInputStream{

        private long readCount = 0;
        
        
        public PushBackCountingInputStream(InputStream in) {
            super(in,4);
            
        }


        @Override
        public int read() throws IOException {
            readCount++;
            return super.read();
        }


        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            readCount+=len;
            return super.read(b, off, len);
        }


        public long getReadCount() {
            return readCount;
        }


        @Override
        public void unread(int b) throws IOException {
            readCount--;
            super.unread(b);
        }


        @Override
        public void unread(byte[] b, int off, int len) throws IOException {
            readCount-=len;
            super.unread(b, off, len);
        }


       
        
        
    }

}
