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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Collections;

import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonStructure;
import javax.json.stream.JsonParsingException;

public class JanssonSuiteTest {
    
        protected static final JsonReaderFactory readerFactory = Json.createReaderFactory(Collections.EMPTY_MAP);
        
        public static void main(String[] args) {
            
            if (!Charset.defaultCharset().equals(Charset.forName("UTF-8"))) {
                throw new RuntimeException("Default charset is " + Charset.defaultCharset() + ", must must be UTF-8");
            }
            
            File root = new File("/Users/salyh/git/fleece-dev/jansson/test/suites");
            
            walk(root);
            
            
            
        }
    
        
        private static void walk(File file) {
            
            if(file.isFile()) {
                exec(file);
                
            } else if (file.isDirectory()) {
                for(File  f: file.listFiles())
                    walk(f);
            }
            
        }

        
        private static void exec(File file) {
            
            if(file.getName().equals("input")){
            
                boolean shouldFail=file.getAbsolutePath().contains("invalid");
                
                
                try {
                    CharsetDecoder dec = Charset.forName("UTF-8").newDecoder();
                    JsonReader reader = readerFactory.createReader(new InputStreamReader(new FileInputStream(file),dec));
                    JsonStructure struct = reader.read();
                    
                    if(shouldFail)
                        System.out.println("FAIL (false positive) "+file.getAbsolutePath()+" --> "+struct.getValueType());
                    //else
                        //System.out.println("OK "+file.getAbsolutePath()+" --> "+struct.getValueType());    
                    
                }catch (JsonParsingException e) {
                    
                    if(!shouldFail)
                        System.out.println("FAIL (positive false) "+file.getAbsolutePath()+" --> "+e.getMessage());
                    //else
                      //  System.out.println("OK "+file.getAbsolutePath()+" --> "+e.getMessage());  

                } 
                
                catch (Exception e) {
                    // TODO Auto-generated catch block
                    System.out.println("!!!!! "+file.getAbsolutePath());
                    e.printStackTrace();
                }
                
            }
            
            
            
            
        }
}
