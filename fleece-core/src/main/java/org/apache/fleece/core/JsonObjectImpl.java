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

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public final class JsonObjectImpl extends LinkedHashMap<String, JsonValue> implements JsonObject, Serializable {
    private int hashCode;

    private <T> T value(final String name, final Class<T> clazz) {
        final Object v = get(name);
        if (v != null) {
            return clazz.cast(v);
        }
        throw new NullPointerException("no mapping for " + name);
    }

    
    public JsonObjectImpl() {
        super();      
    }

    public JsonObjectImpl(String key, JsonValue value) {
        super();      
        super.put(key, value);
    }
    
    public JsonObjectImpl(Map<? extends String, ? extends JsonValue> m) {
        super(m);
    }



    @Override
    public JsonArray getJsonArray(final String name) {
        return value(name, JsonArray.class);
    }

    @Override
    public JsonObject getJsonObject(final String name) {
        return value(name, JsonObject.class);
    }

    @Override
    public JsonNumber getJsonNumber(final String name) {
        return value(name, JsonNumber.class);
    }

    @Override
    public JsonString getJsonString(final String name) {
        return value(name, JsonString.class);
    }

    @Override
    public String getString(final String name) {
        final JsonString str = getJsonString(name);
        return str != null ? str.getString() : null;
    }

    @Override
    public String getString(final String name, final String defaultValue) {
        try {
            return getJsonString(name).getString();
        } catch (final NullPointerException npe) {
            return defaultValue;
        }
    }

    @Override
    public int getInt(final String name) {
        return getJsonNumber(name).intValue();
    }

    @Override
    public int getInt(final String name, final int defaultValue) {
        try {
            return getJsonNumber(name).intValue();
        } catch (final NullPointerException npe) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(final String name) {
        return value(name, JsonValue.class) == JsonValue.TRUE;
    }

    @Override
    public boolean getBoolean(final String name, final boolean defaultValue) {
        try {
            return getBoolean(name);
        } catch (final NullPointerException npe) {
            return defaultValue;
        }
    }

    @Override
    public boolean isNull(final String name) {
        return value(name, JsonValue.class) == JsonValue.NULL;
    }

    @Override
    public ValueType getValueType() {
        return ValueType.OBJECT;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("{");
        final Iterator<Map.Entry<String, JsonValue>> it = super.entrySet().iterator();
        boolean hasNext = it.hasNext();
        while (hasNext) {
            final Map.Entry<String, JsonValue> entry = it.next();

            builder.append('"').append(entry.getKey()).append("\":");

            final JsonValue value = entry.getValue();
            if (JsonString.class.isInstance(value)) {
                builder.append(JsonChars.QUOTE).append(value.toString()).append(JsonChars.QUOTE);
            } else {
                builder.append(value != JsonValue.NULL ? value.toString() : JsonChars.NULL);
            }

            hasNext = it.hasNext();
            if (hasNext) {
                builder.append(",");
            }
        }
        return builder.append('}').toString();
    }

    @Override
    public boolean equals(final Object obj) {
        return JsonObject.class.isInstance(obj) && super.equals(obj);
    }

    @Override
    public void clear() {
        throw immutable();
    }

    @Override
    public JsonValue put(String key, JsonValue value) {
        throw immutable();
    }

    @Override
    public void putAll(Map<? extends String, ? extends JsonValue> jsonObject) {
        throw immutable();
    }

    @Override
    public JsonValue remove(Object key) {
        throw immutable();
    }
    
    

    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(super.keySet());
    }


    @Override
    public Collection<JsonValue> values() { 
        return Collections.unmodifiableCollection(super.values());
    }


    @Override
    public Set<Entry<String, JsonValue>> entrySet() {
        return new HashSet<Map.Entry<String,JsonValue>>(super.entrySet());
    }


    private static UnsupportedOperationException immutable() {
        return new UnsupportedOperationException("JsonObject is immutable. You can create another one thanks to JsonObjectBuilder");
    }

    @Override
    public int hashCode() {
        int h=hashCode;
        if (h == 0) { //just ignore the case that there might be a valid hashcode of 0 (but thats rare)
            h = super.hashCode();
            hashCode=h;
        }
        return h;
    }
}
