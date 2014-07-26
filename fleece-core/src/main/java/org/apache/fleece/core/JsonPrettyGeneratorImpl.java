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
import java.io.Writer;
import java.util.concurrent.ConcurrentMap;

class JsonPrettyGeneratorImpl extends JsonGeneratorImpl {
    private static final String DEFAULT_INDENTATION = "  ";
    private final String indent; 

    JsonPrettyGeneratorImpl(final Writer writer, final String prefix,
                                   final ConcurrentMap<String, String> cache) {
        super(writer, cache);
        this.indent = prefix;
    }
    
    JsonPrettyGeneratorImpl(final Writer writer, 
            final ConcurrentMap<String, String> cache) {
    this(writer, DEFAULT_INDENTATION, cache);
    
    }
    
    
    @Override
    protected void addCommaIfNeeded() {
        if (needComma) {
            justWrite(COMMA_CHAR);
            justWrite(EOL);
            needComma = false;
        }
    }


    private void writeEOL() {
        
        try {
            writer.write(EOL);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
    
    private void writeIndent() {
        
       
        
        System.out.println("Indent "+depth);
        
        for(int i=0; i< depth;i++) {
            try {
                writer.write(indent);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void justWrite(String value) {
        // TODO Auto-generated method stub
        writeIndent();
        super.justWrite(value);
        writeEOL();
    }

    @Override
    protected void justWrite(char value) {
        // TODO Auto-generated method stub
        writeIndent();
        super.justWrite(value);
        writeEOL();
    }

    
    
    
    /*
    

    @Override
    public JsonGenerator write(String name, JsonValue value) {
        writeIndent();
        super.write(name, value);
        return this;
    }

    @Override
    public JsonGenerator write(String name, String value) {
        writeIndent();
        super.write(name, value);
        return this;
    }

    @Override
    public JsonGenerator write(String name, BigInteger value) {
        writeIndent();
        super.write(name, value);
        return this;
    }

    @Override
    public JsonGenerator write(String name, BigDecimal value) {
        writeIndent();
        super.write(name, value);
        return this;
    }

    @Override
    public JsonGenerator write(String name, int value) {
        writeIndent();
        super.write(name, value);
        return this;
    }

    @Override
    public JsonGenerator write(String name, long value) {
        writeIndent();
        super.write(name, value);
        return this;
    }

    @Override
    public JsonGenerator write(String name, double value) {
        writeIndent();
        super.write(name, value);
        return this;
    }

    @Override
    public JsonGenerator write(String name, boolean value) {
        writeIndent();
        super.write(name, value);
        return this;
    }

    @Override
    public JsonGenerator writeNull(String name) {
        writeIndent();
        super.writeNull(name);
        return this;
    }

    @Override
    public JsonGenerator write(JsonValue value) {
        writeIndent();
        super.write(value);
        return this;
    }

    @Override
    public JsonGenerator write(String value) {
        writeIndent();
        super.write(value);
        return this;
    }

    @Override
    public JsonGenerator write(BigDecimal value) {
        writeIndent();
        super.write(value);
        return this;
    }

    @Override
    public JsonGenerator write(BigInteger value) {
        writeIndent();
        super.write(value);
        return this;
    }

    @Override
    public JsonGenerator write(int value) {
        writeIndent();
        super.write(value);
        return this;
    }

    @Override
    public JsonGenerator write(long value) {
        writeIndent();
        super.write(value);
        return this;
    }

    @Override
    public JsonGenerator write(double value) {
        writeIndent();
        super.write(value);
        writeEOL();
        return this;
    }

    @Override
    public JsonGenerator write(boolean value) {
        writeIndent();
        super.write(value);
        return this;
    }

    @Override
    public JsonGenerator writeNull() {
        writeIndent();
        super.writeNull();
        return this;
    }

    @Override
    public JsonGenerator writeStartObject() {
        writeIndent();
        super.writeStartObject();
        writeEOL();
        return this;
    }

    @Override
    public JsonGenerator writeStartObject(final String name) {
        //writeIndent();
        super.writeStartObject(name);
        return this;
    }

    @Override
    public JsonGenerator writeStartArray() {
        //writeIndent();
        super.writeStartArray();
        writeEOL();
        return this;
    }

    @Override
    public JsonGenerator writeStartArray(final String name) {
        //writeIndent();
        super.writeStartArray(name);
        return this;
    }

    @Override
    public JsonGenerator writeEnd() {
        writeEOL();
        super.writeEnd();      
        return this;
    }
*/
}
