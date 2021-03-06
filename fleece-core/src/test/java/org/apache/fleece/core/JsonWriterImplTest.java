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

import org.apache.fleece.core.JsonObjectImpl;
import org.apache.fleece.core.JsonStringImpl;
import org.junit.Test;

import javax.json.Json;
import javax.json.JsonWriter;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;

public class JsonWriterImplTest {
    @Test
    public void writer() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final JsonWriter writer = Json.createWriter(out);
        final JsonObjectImpl value = new JsonObjectImpl();
        value.putInternal("a", new JsonStringImpl("b"));
        writer.write(value);
        writer.close();
        assertEquals("{\"a\":\"b\"}", new String(out.toByteArray()));
    }
}
