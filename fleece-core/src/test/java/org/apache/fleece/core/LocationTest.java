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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.json.stream.JsonLocation;
import javax.json.stream.JsonParsingException;

import org.junit.Assert;
import org.junit.Test;

public class LocationTest {
    @Test
    public void failBytesInput() {
  
        try {
            JsonReader reader = Json.createReader(new ByteArrayInputStream("{\"z\":nulll}".getBytes()));
            reader.read();
            Assert.fail("Exception expected");
        } catch (JsonParsingException e) {
            JsonLocation location = e.getLocation();
            Assert.assertNotNull(location);
            Assert.assertEquals(new JsonLocationImpl(1, 10, 10), location);
            
        }
        
        
        try {
            JsonReader reader = Json.createReader(new ByteArrayInputStream("{\"z\":\nnulll}".getBytes()));
            reader.read();
            Assert.fail("Exception expected");
        } catch (JsonParsingException e) {
            JsonLocation location = e.getLocation();
            Assert.assertNotNull(location);
            Assert.assertEquals(new JsonLocationImpl(2, 5, 11), location);
            
        }
        
        try {
            JsonReader reader = Json.createReader(new ByteArrayInputStream("aaa".getBytes()));
            reader.read();
            Assert.fail("Exception expected");
        } catch (JsonParsingException e) {
            JsonLocation location = e.getLocation();
            Assert.assertNotNull(location);
            Assert.assertEquals(new JsonLocationImpl(1, 1, 1), location);
            
        }
    }
}
