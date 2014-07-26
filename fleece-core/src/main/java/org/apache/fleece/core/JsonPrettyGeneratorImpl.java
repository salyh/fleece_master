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

import java.io.Writer;
import java.util.concurrent.ConcurrentMap;

import javax.json.stream.JsonGenerator;

final class JsonPrettyGeneratorImpl extends JsonGeneratorImpl {
    private static final String DEFAULT_INDENTATION = "  ";
    private final String indent;

    JsonPrettyGeneratorImpl(final Writer writer, final String prefix, final ConcurrentMap<String, String> cache) {
        super(writer, cache);
        this.indent = prefix;
    }

    JsonPrettyGeneratorImpl(final Writer writer, final ConcurrentMap<String, String> cache) {
        this(writer, DEFAULT_INDENTATION, cache);

    }

    private void writeEOL() {
        justWrite(EOL);
    }

    private void writeIndent(int correctionOffset) {
        for (int i = 0; i < depth + correctionOffset; i++) {
            justWrite(indent);
        }
    }

    
    @Override
    protected JsonGenerator writeEnd(final char value) {
        writeEOL();
        writeIndent(-1);
        return super.writeEnd(value);
    }

    @Override
    protected void noCheckWrite(final String value) {
        addCommaIfNeeded();
        
        if(depth>0){
            writeEOL();
            writeIndent(0);
        }
        
        justWrite(value);
    }

    @Override
    protected void noCheckWrite(final char value) {
        addCommaIfNeeded();
        
        if(depth>0){
            writeEOL();
            writeIndent(0);
        }
        
        justWrite(value);
    }

}
