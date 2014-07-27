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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentMap;

import javax.json.JsonException;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerationException;
import javax.json.stream.JsonGenerator;

class JsonGeneratorImpl implements JsonGenerator, JsonChars, Serializable {
    private static final String NULL_KEY = NULL+KEY_SEPARATOR;
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private final Writer writer;
    private final char[] buffer = new char[8192]; 
    private int bufferPos = 0;
    private final ConcurrentMap<String, String> cache;
    private boolean needComma = false;
    
    private StructureElement currentStructureElement = null;
    private boolean valid = false;
    protected int depth = 0;

    //minimal stack implementation
    private static final class StructureElement {
        final StructureElement previous;
        final boolean isArray;
        

        StructureElement(final StructureElement previous, final boolean isArray) {
            super();
            this.previous = previous;
            this.isArray = isArray;
        }             
    }


   JsonGeneratorImpl(final Writer writer, ConcurrentMap<String, String> cache) {
        this.writer = writer;
        this.cache = cache;
    }
   
   JsonGeneratorImpl(final OutputStream out, final ConcurrentMap<String, String> cache) {
       this(new OutputStreamWriter(out, UTF8_CHARSET), cache);
   }
   
   JsonGeneratorImpl(final OutputStream out, Charset encoding, final ConcurrentMap<String, String> cache) {
       this(new OutputStreamWriter(out, encoding), cache);
   }

    protected void addCommaIfNeeded() {
        if (needComma) {
            justWrite(COMMA_CHAR);
            needComma = false;
        }
        
    }

    // we cache key only since they are generally fixed
    private String key(final String name) {
        if (name == null) {
            return NULL_KEY;
        }
        String k = cache.get(name);
        if (k == null) {
            k = '"' + Strings.escape(name) + "\":";
            cache.putIfAbsent(name, k);
        }
        return k;
    }

    @Override
    public JsonGenerator writeStartObject() {
        
        if(currentStructureElement == null && valid) {
            throw new JsonGenerationException("Method must not be called more than once in no context");
        }  
        
        if(currentStructureElement != null && !currentStructureElement.isArray) {
            throw new JsonGenerationException("Method must not be called within an object context");
        }       
        
      //push upon the stack
        if (currentStructureElement == null) {
            currentStructureElement = new StructureElement(null, false);
        } else {
            final StructureElement localStructureElement = new StructureElement(currentStructureElement, false);
            currentStructureElement = localStructureElement;
        }

       
        
        if(!valid){
            valid=true;
        }
        
        noCheckWrite(START_OBJECT_CHAR);
        depth++;
        return this;
    }

    @Override
    public JsonGenerator writeStartObject(final String name) {
        if(currentStructureElement == null || currentStructureElement.isArray) {
            throw new JsonGenerationException("Method must not be called within an array context");
        }  
        
      //push upon the stack
        if (currentStructureElement == null) {
            currentStructureElement = new StructureElement(null, false);
        } else {
            final StructureElement localStructureElement = new StructureElement(currentStructureElement, false);
            currentStructureElement = localStructureElement;
        }
        
       
        
       noCheckWrite(key(name) + START_OBJECT_CHAR);
       depth++;
       return this;
    }

    @Override
    public JsonGenerator writeStartArray() {
        if(currentStructureElement == null && valid) {
            throw new JsonGenerationException("Method must not be called more than once in no context");
        }  
        
        if(currentStructureElement != null && !currentStructureElement.isArray) {
            throw new JsonGenerationException("Method must not be called within an object context");
        }    
        
      //push upon the stack
        if (currentStructureElement == null) {
            currentStructureElement = new StructureElement(null, true);
        } else {
            final StructureElement localStructureElement = new StructureElement(currentStructureElement, true);
            currentStructureElement = localStructureElement;
        }
        
       
        
        if(!valid){
            valid=true;
        }
        
        noCheckWrite(START_ARRAY_CHAR);
        depth++;
        return this;
    }

    @Override
    public JsonGenerator writeStartArray(final String name) {
        if(currentStructureElement == null || currentStructureElement.isArray) {
            throw new JsonGenerationException("Method must not be called within an array context");
        }  
        
      //push upon the stack
        if (currentStructureElement == null) {
            currentStructureElement = new StructureElement(null, true);
        } else {
            final StructureElement localStructureElement = new StructureElement(currentStructureElement, true);
            currentStructureElement = localStructureElement;
        }
        
        
        
        noCheckWrite(key(name) + START_ARRAY_CHAR);
        depth++;
        return this;
    }

    @Override
    public JsonGenerator write(final String name, final JsonValue value) {
        //if (JsonString.class.isInstance(value)) {
          //  return write(name, value == null ? null : value.toString());
        //}
        checkObject();
        noCheckWriteAndForceComma(key(name)+String.valueOf(value));
        return this;
    }

    @Override
    public JsonGenerator write(final String name, final String value) {
        checkObject();        
        noCheckWriteAndForceComma(key(name)+QUOTE_CHAR+Strings.escape(value)+QUOTE_CHAR);
        return this;
    }

    @Override
    public JsonGenerator write(final String name, final BigInteger value) {
        checkObject();
        noCheckWriteAndForceComma(key(name)+String.valueOf(value));
        return this;
    }

    @Override
    public JsonGenerator write(final String name, final BigDecimal value) {
        checkObject();
        noCheckWriteAndForceComma(key(name)+String.valueOf(value));
        return this;
    }

    @Override
    public JsonGenerator write(final String name, final int value) {
        checkObject();
        noCheckWriteAndForceComma(key(name)+String.valueOf(value));
        return this;
    }

    @Override
    public JsonGenerator write(final String name, final long value) {
        checkObject();
        noCheckWriteAndForceComma(key(name)+String.valueOf(value));
        return this;
    }

    @Override
    public JsonGenerator write(final String name, final double value) {
        checkObject();
        checkDoubleRange(value);
        noCheckWriteAndForceComma(key(name)+String.valueOf(value));
        return this;
    }

    @Override
    public JsonGenerator write(final String name, final boolean value) {
        checkObject();
        noCheckWriteAndForceComma(key(name)+String.valueOf(value));
        return this;
    }

    @Override
    public JsonGenerator writeNull(final String name) {
        checkObject();
        noCheckWriteAndForceComma(key(name)+NULL);
        return this;
    }

    @Override
    public JsonGenerator writeEnd() {       
        if(currentStructureElement==null){
            throw new JsonGenerationException("Method must not be called in no context");
        }  
        
        writeEnd(currentStructureElement.isArray ? END_ARRAY_CHAR : END_OBJECT_CHAR);
        
        //pop from stack
        currentStructureElement = currentStructureElement.previous;
        depth--;
        
        return this;
    }

    @Override
    public JsonGenerator write(final JsonValue value) {
        if(JsonStructure.class.isInstance(value)) {
            valid = true;
        }
        noCheckWriteAndForceComma(String.valueOf(value));
        return this;
    }

    @Override
    public JsonGenerator write(final String value) {
        checkArray();
        noCheckWriteAndForceComma(QUOTE_CHAR+Strings.escape(value)+QUOTE_CHAR);
        return this;
    }

    @Override
    public JsonGenerator write(final BigDecimal value) {
        checkArray();
        noCheckWrite(String.valueOf(value));
        needComma = true;
        return this;
    }

    @Override
    public JsonGenerator write(final BigInteger value) {
        checkArray();
        noCheckWrite(String.valueOf(value));
        needComma = true;
        return this;
    }

    @Override
    public JsonGenerator write(final int value) {
        checkArray();
        noCheckWrite(Integer.toString(value));
        needComma = true;
        return this;
    }

    @Override
    public JsonGenerator write(final long value) {
        checkArray();
        noCheckWrite(Long.toString(value));
        needComma = true;
        return this;
    }

    @Override
    public JsonGenerator write(final double value) {
        checkArray();
        checkDoubleRange(value);
        noCheckWrite(Double.toString(value));
        needComma = true;
        return this;
    }

    @Override
    public JsonGenerator write(final boolean value) {
        checkArray();
        noCheckWrite(Boolean.toString(value));
        needComma = true;
        return this;
    }

    @Override
    public JsonGenerator writeNull() {
        checkArray();
        noCheckWriteAndForceComma(NULL);
        needComma = true;
        return this;
    }

    @Override
    public void close() {

        try {
            if (currentStructureElement != null || !valid) {

                throw new JsonGenerationException("Invalid json " + currentStructureElement + " " + valid);
            }
        } finally {

            //buffer.close() TODO
            flushBuffer();

            try {
                writer.close();
            } catch (final IOException e) {
                throw new JsonException(e.getMessage(), e);
            }
        }
    }

    @Override
    public void flush() {
        
        flushBuffer();
        
        try {
            writer.flush();
        } catch (final IOException e) {
            throw new JsonException(e.getMessage(), e);
        }
    }

    private JsonGenerator noCheckWriteAndForceComma(final String value) {
        noCheckWrite(value);
        needComma = true;
        return this;
    }
    
    protected  JsonGenerator writeEnd(final char value) {
        justWrite(value);
        needComma = true;
        return this;
    }
    

    protected void noCheckWrite(String value) {
        addCommaIfNeeded();
        justWrite(value);
    }
    
    protected void noCheckWrite(char value) {
        addCommaIfNeeded();
        justWrite(value);
    }

    private void flushBuffer() {
        //System.out.println("flush "+bufferPos+"   "+new String(buffer,0,bufferPos));
        
        try {
            writer.write(buffer,0, bufferPos);
            bufferPos=0;
        } catch (final IOException e) {
            throw new JsonException(e.getMessage(), e);
        }
    }
    
    protected  void justWrite(final String value) {
        final int valueLength = value.length();
        
        //System.out.println("enter");
        
        if (bufferPos+valueLength >= buffer.length) {
            
            int start=0;
            int len=buffer.length-bufferPos;
            
            while(true){
                
                
                int end = start+len;
                if(end > valueLength) {
                    end=valueLength;
                }
                
                //System.out.println(start+" "+(end)+" ("+(end-start)+") "+bufferPos+" "+valueLength+ " "+value);
                
                value.getChars(start, end, buffer, bufferPos);
                //System.out.println("write "+value.substring(start,end));
                               
                bufferPos+=(end-start);
                start+=(len);
                //System.out.println(start+" "+bufferPos);
                
                if(start >= valueLength) {
                    //System.out.println("ret");
                    return;
                }

                if (bufferPos >= buffer.length) {
                    flushBuffer();
                    len=buffer.length;
                }
                
               
                
                
            }
            
            
            
        }else {
            //fits completely into the buffer
            //System.out.println("FIT");
            value.getChars(0, valueLength, buffer, bufferPos);
            bufferPos+=valueLength;
        }
            
        
        
        

    }
    
    protected void justWrite(final char value) {
        
        if (bufferPos >= buffer.length) {
            flushBuffer();
        }
        
      
        
        buffer[bufferPos++] = value;
        
        //System.out.println("one char "+(bufferPos-1)+" "+value);

    }

    private void checkObject() {
        if (currentStructureElement == null || currentStructureElement.isArray) {
            throw new JsonGenerationException("write(name, param) is only valid in objects");
        }
    }

    private void checkArray() {
        if (currentStructureElement == null || !currentStructureElement.isArray) {
            throw new JsonGenerationException("write(param) is only valid in arrays");
        }
    }

    private static void checkDoubleRange(final double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            throw new NumberFormatException("double can't be infinite or NaN");
        }
    }
}
