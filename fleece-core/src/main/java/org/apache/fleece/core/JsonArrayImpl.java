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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public final class JsonArrayImpl extends LinkedList<JsonValue> implements JsonArray, Serializable {
    private int hashCode;
    private boolean initialized;

    private <T> T value(final int idx, final Class<T> type) {
        if (idx > size()) {
            throw new IndexOutOfBoundsException(idx + "/" + size());
        }
        return type.cast(get(idx));
    }

    public JsonArrayImpl() {
        super();
        initialized=true;
    }

    public JsonArrayImpl(Collection<? extends JsonValue> jsonValues) {
        super(jsonValues);
        initialized=true;
    }
    
    public JsonArrayImpl(Collection<? extends JsonValue> jsonValues, JsonValue... additionalJsonValues) {
        super(jsonValues);
        super.addAll(size(),Arrays.asList(additionalJsonValues));
        initialized=true;
    }
    
    public JsonArrayImpl(JsonArray jsonArray, JsonValue... additionalJsonValues) {
        super(jsonArray);
        super.addAll(size(), Arrays.asList(additionalJsonValues));
        initialized=true;
    }
    
    public JsonArrayImpl(JsonValue... jsonValues) {
        super(Arrays.asList(jsonValues));
        initialized=true;
    }


    @Override
    public JsonObject getJsonObject(final int index) {
        return value(index, JsonObject.class);
    }

    @Override
    public JsonArray getJsonArray(final int index) {
        return value(index, JsonArray.class);
    }

    @Override
    public JsonNumber getJsonNumber(final int index) {
        return value(index, JsonNumber.class);
    }

    @Override
    public JsonString getJsonString(final int index) {
        return value(index, JsonString.class);
    }

    @Override
    public <T extends JsonValue> List<T> getValuesAs(final Class<T> clazz) {
        return (List<T>) Collections.unmodifiableList(this);
        //or a shallow copy return (List<T>) this.clone();
        //or defense copy -> new LinkedList<JsonValue>(this);
    }

    @Override
    public String getString(final int index) {
        return value(index, JsonString.class).getString();
    }

    @Override
    public String getString(final int index, final String defaultValue) {
        try {
            return getString(index);
        } catch (final IndexOutOfBoundsException ioobe) {
            return defaultValue;
        }
    }

    @Override
    public int getInt(final int index) {
        return value(index, JsonNumber.class).intValue();
    }

    @Override
    public int getInt(final int index, final int defaultValue) {
        try {
            return getInt(index);
        } catch (final IndexOutOfBoundsException ioobe) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(final int index) {
        return value(index, JsonValue.class) == JsonValue.TRUE;
    }

    @Override
    public boolean getBoolean(final int index, final boolean defaultValue) {
        try {
            return getBoolean(index);
        } catch (final IndexOutOfBoundsException ioobe) {
            return defaultValue;
        }
    }

    @Override
    public boolean isNull(final int index) {
        return value(index, JsonValue.class) == JsonValue.NULL;
    }

    @Override
    public ValueType getValueType() {
        return ValueType.ARRAY;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("[");
        final Iterator<JsonValue> it = iterator();
        boolean hasNext = it.hasNext();
        while (hasNext) {
            final JsonValue jsonValue = it.next();
            if (JsonString.class.isInstance(jsonValue)) {
                builder.append(JsonChars.QUOTE).append(jsonValue.toString()).append(JsonChars.QUOTE);
            } else {
                builder.append(jsonValue != JsonValue.NULL ? jsonValue.toString() : JsonChars.NULL);
            }
            hasNext = it.hasNext();
            if (hasNext) {
                builder.append(",");
            }
        }
        return builder.append(']').toString();
    }

    @Override
    public boolean equals(final Object obj) {
        return JsonArray.class.isInstance(obj) && super.equals(obj);
    }

    //make protected if class is supposed to be subclassed
    //make package private otherwise
    //protected void addInternal(final JsonValue value) {
    //    super.add(value);
    //}

    @Override
    public boolean add(final JsonValue element) {
        throw immutable();
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends JsonValue> c) {
        if (initialized) {
            throw immutable();
        } else {
            return super.addAll(index, c);
        }
    }

    @Override
    public boolean remove(final Object o) {
        throw immutable();
    }

    @Override
    public JsonValue remove(final int index) {
        throw immutable();
    }

    @Override
    public void add(final int index, final JsonValue element) {
        throw immutable();
    }

    @Override
    public void clear() {
        throw immutable();
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        throw immutable();
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        throw immutable();
    }

    @Override
    public JsonValue set(final int index, final JsonValue element) {
        throw immutable();
    }
    
    @Override
    public List<JsonValue> subList(int fromIndex, int toIndex) {
        return Collections.unmodifiableList(super.subList(fromIndex, toIndex));
    }

    @Override
    public ListIterator<JsonValue> listIterator(int index) {
        
        final ListIterator<JsonValue> internal = super.listIterator(index);
        
        return new ListIterator<JsonValue>() {

            @Override
            public boolean hasNext() {
                return internal.hasNext();
            }

            @Override
            public JsonValue next() {
                return internal.next();
            }

            @Override
            public boolean hasPrevious() {
                return internal.hasPrevious();
            }

            @Override
            public JsonValue previous() {
                return internal.previous();
            }

            @Override
            public int nextIndex() {
                return internal.nextIndex();
            }

            @Override
            public int previousIndex() {
                return internal.previousIndex();
            }

            @Override
            public void remove() {
                throw immutable();
                
            }

            @Override
            public void set(JsonValue e) {
                throw immutable();
                
            }

            @Override
            public void add(JsonValue e) {
                throw immutable();
                
            }
            
        };
    }

    static UnsupportedOperationException immutable() {
        return new UnsupportedOperationException("JsonArray is immutable. You can create another one thanks to JsonArrayBuilder");
    }

    @Override
    public int hashCode() {
        int h = hashCode;
        if (h == 0) { //just ignore the case that there might be a valid hashcode of 0 (but thats rare)
            h = super.hashCode();
            hashCode=h;
        }
        return h;
    }
}
