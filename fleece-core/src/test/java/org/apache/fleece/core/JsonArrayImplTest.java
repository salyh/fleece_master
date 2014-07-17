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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import javax.json.JsonArray;
import javax.json.JsonValue;

import org.junit.Test;

import static org.junit.Assert.*;

public class JsonArrayImplTest {
    @Test
    public void arrayToString() {
        final JsonArrayImpl object = new JsonArrayImpl(new JsonValue[]{new JsonStringImpl("a"),new JsonStringImpl("b")});
        assertEquals("[\"a\",\"b\"]", object.toString());
    }
    
    @Test
    public void arrayIndex() {
        final JsonArrayImpl object = new JsonArrayImpl(new JsonValue[]{new JsonStringImpl("a"),new JsonStringImpl("b"),new JsonLongImpl(5)});
        final JsonArray array = (JsonArray) object;
        assertFalse(array.isEmpty());
        assertEquals("a", object.getJsonString(0).getString());
        assertEquals("b", object.getJsonString(1).getString());
        assertEquals(5, object.getJsonNumber(2).longValue());
        assertEquals("[\"a\",\"b\",5]", object.toString());
    }
    
    @Test
    public void emptyArray() {
        final JsonArray array = new JsonArrayImpl();
        assertTrue(array.isEmpty());
        assertEquals("[]", array.toString());
    }
    
    @Test(expected=UnsupportedOperationException.class)
    public void immutableAddAll() {
        final JsonArray array = new JsonArrayImpl();
        array.addAll(Collections.EMPTY_LIST);
    }
    
    @Test(expected=UnsupportedOperationException.class)
    public void immutableSublist() {
        final JsonArray array = new JsonArrayImpl(new JsonStringImpl("a"),new JsonStringImpl("b"));
        assertEquals(2, array.size());
        array.subList(0,1).add(null);
    }
    
    @Test(expected=IndexOutOfBoundsException.class)
    public void immutableSublistOOB() {
        final JsonArray array = new JsonArrayImpl(new JsonStringImpl("a"),new JsonStringImpl("b"));
        assertEquals(2, array.size());
        array.subList(0,5);
    }
    
    @Test
    public void immutableListIterator() {
        final JsonArray array = new JsonArrayImpl(new JsonStringImpl("a"),new JsonStringImpl("b"));
        ListIterator<JsonValue> it = array.listIterator();
        assertTrue(it.hasNext());
        assertNotNull(it.next());
        assertNotNull(it.next());
        assertFalse(it.hasNext());
    }
    
    @Test(expected=UnsupportedOperationException.class)
    public void immutableListIteratorRemove() {
        final JsonArray array = new JsonArrayImpl(new JsonStringImpl("a"),new JsonStringImpl("b"));
        ListIterator<JsonValue> it = array.listIterator();
        assertTrue(it.hasNext());
        assertNotNull(it.next());
        it.remove();

    }
    
    //@Test
    public void volatileHashcode() throws Exception{
        final JsonArray array = new JsonArrayImpl(new JsonStringImpl("a"),new JsonStringImpl("b"));
        final List<Integer> hashs = (new ArrayList<Integer>());
       
        Runnable r = new Runnable() {
            
            @Override
            public void run() {
                
                hashs.add(array.hashCode());
                
                
                
                
            }
        };
        
        for(int i=0; i< 10;i++)
        {
            Thread t1 = new Thread(r);
            Thread t2 = new Thread(r);
            Thread t3 = new Thread(r);
            t1.start();
            t2.start();
            t3.start();
        }
   
        
        Thread.sleep(1000);
        
        assertEquals(30, hashs.size());
        System.out.println(hashs);
        assertEquals(hashs.get(0), hashs.get(1));
        assertEquals(hashs.get(0), hashs.get(2));

    }
    
}
