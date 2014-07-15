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

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParsingException;

import java.math.BigDecimal;

public class JsonReaderImpl implements JsonReader {
    private final EscapedStringAwareJsonParser parser;
    private final JsonReaderListenerFactory listenerFactory;
    
    public JsonReaderImpl(final EscapedStringAwareJsonParser parser) {
        this(parser, new JsonListenerFactory());
    }

    public JsonReaderImpl(final EscapedStringAwareJsonParser parser, final JsonReaderListenerFactory listenerFactory) {
        this.parser = parser;
        this.listenerFactory = listenerFactory;
    }

    @Override
    public JsonStructure read() {
        if (!parser.hasNext()) {
            throw new JsonParsingException("Nothing to read", new JsonLocationImpl(1, 1, 0));
        }
        switch (parser.next()) {
            case START_OBJECT:
                final JsonReaderListener subObject = listenerFactory.subObject();
                parseObject(subObject);
                return JsonObject.class.cast(subObject.getObject());
            case START_ARRAY:
                final JsonReaderListener subArray = listenerFactory.subArray();
                parseArray(subArray);
                return JsonArray.class.cast(subArray.getObject());
            default:
                throw new JsonParsingException("Unknown structure: " + parser.next(), parser.getLocation());
        }
    }

    @Override
    public JsonObject readObject() {
        return JsonObject.class.cast(read());
    }

    @Override
    public JsonArray readArray() {
        return JsonArray.class.cast(read());
    }

    @Override
    public void close() {
        parser.close();
    }

    private static class JsonListenerFactory implements JsonReaderListenerFactory {
        @Override
        public JsonReaderListener subObject() {
            return new JsonObjectListener();
        }

        @Override
        public JsonReaderListener subArray() {
            return new JsonArrayListener();
        }
    }

    private static class JsonObjectListener implements JsonReaderListener {
        private JsonObjectBuilder builder = Json.createObjectBuilder();
        private String key = null;

        @Override
        public Object getObject() {
            return builder.build();
        }

        @Override
        public void onKey(final String string) {
            key = string;
        }

        @Override
        public void onValue(final String string, final String escaped) {
            final JsonStringImpl value = new JsonStringImpl(string, escaped);
            builder.add(key, value);
        }

        @Override
        public void onLong(final long aLong) {
            final JsonLongImpl value = new JsonLongImpl(aLong);
            builder.add(key, value);
        }

        @Override
        public void onBigDecimal(final BigDecimal bigDecimal) {
            final JsonNumberImpl value = new JsonNumberImpl(bigDecimal);
            builder.add(key, value);
        }

        @Override
        public void onNull() {
            final JsonValue value = JsonValue.NULL;
            builder.add(key, value);
        }

        @Override
        public void onTrue() {
            final JsonValue value = JsonValue.TRUE;
            builder.add(key, value);
        }

        @Override
        public void onFalse() {
            final JsonValue value = JsonValue.FALSE;
            builder.add(key, value);
        }

        @Override
        public void onObject(final Object obj) {
            final JsonObject jsonObject = JsonObject.class.cast(obj);
            builder.add(key, jsonObject);
        }

        @Override
        public void onArray(final Object arr) {
            final JsonArray jsonArray = JsonArray.class.cast(arr);
            builder.add(key, jsonArray);
        }
    }

    private static class JsonArrayListener implements JsonReaderListener {
        JsonArrayBuilder builder = Json.createArrayBuilder();
  
        @Override
        public Object getObject() {
            return builder.build();
        }

        @Override
        public void onKey(final String string) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onValue(final String string, final String escaped) {
            final JsonStringImpl value = new JsonStringImpl(string, escaped);
            builder.add(value);
        }

        @Override
        public void onLong(final long aLong) {
            final JsonLongImpl value = new JsonLongImpl(aLong);
            builder.add(value);
        }

        @Override
        public void onBigDecimal(final BigDecimal bigDecimal) {
            final JsonNumberImpl value = new JsonNumberImpl(bigDecimal);
            builder.add(value);
        }

        @Override
        public void onNull() {
            final JsonValue value = JsonValue.NULL;
            builder.add(value);
        }

        @Override
        public void onTrue() {
            final JsonValue value = JsonValue.TRUE;
            builder.add(value);
        }

        @Override
        public void onFalse() {
            final JsonValue value = JsonValue.FALSE;
            builder.add(value);
        }

        @Override
        public void onObject(final Object obj) {
            final JsonObject jsonObject = JsonObject.class.cast(obj);
            builder.add(jsonObject);
        }

        @Override
        public void onArray(final Object arr) {
            final JsonArray jsonArry = JsonArray.class.cast(arr);
            builder.add(jsonArry);
        }
    }

    private void parseObject(final JsonReaderListener listener) {
        while (parser.hasNext()) {
            final JsonParser.Event next = parser.next();
            switch (next) {
                case KEY_NAME:
                    listener.onKey(parser.getString());
                    break;

                case VALUE_STRING:
                    listener.onValue(parser.getString(), parser.getEscapedString());
                    break;

                case START_OBJECT:
                    final JsonReaderListener subListenerObject = listenerFactory.subObject();
                    parseObject(subListenerObject);
                    listener.onObject(subListenerObject.getObject());
                    break;

                case START_ARRAY:
                    final JsonReaderListener subListenerArray = listenerFactory.subArray();
                    parseArray(subListenerArray);
                    listener.onArray(subListenerArray.getObject());
                    break;

                case VALUE_NUMBER:
                    if (parser.isIntegralNumber()) {
                        listener.onLong(parser.getLong());
                    } else {
                        listener.onBigDecimal(parser.getBigDecimal());
                    }
                    break;

                case VALUE_NULL:
                    listener.onNull();
                    break;

                case VALUE_TRUE:
                    listener.onTrue();
                    break;

                case VALUE_FALSE:
                    listener.onFalse();
                    break;

                case END_OBJECT:
                    return;

                case END_ARRAY:
                    throw new JsonParsingException("']', shouldn't occur", parser.getLocation());

                default:
                    throw new JsonParsingException(next.name() + ", shouldn't occur", parser.getLocation());
            }
        }
    }

    private void parseArray(final JsonReaderListener listener) {
        while (parser.hasNext()) {
            final JsonParser.Event next = parser.next();
            switch (next) {
                case VALUE_STRING:
                    listener.onValue(parser.getString(), parser.getEscapedString());
                    break;

                case VALUE_NUMBER:
                    if (parser.isIntegralNumber()) {
                        listener.onLong(parser.getLong());
                    } else {
                        listener.onBigDecimal(parser.getBigDecimal());
                    }
                    break;

                case START_OBJECT:
                    final JsonReaderListener subListenerObject = listenerFactory.subObject();
                    parseObject(subListenerObject);
                    listener.onObject(subListenerObject.getObject());
                    break;

                case START_ARRAY:
                    final JsonReaderListener subListenerArray = listenerFactory.subArray();
                    parseArray(subListenerArray);
                    listener.onArray(subListenerArray.getObject());
                    break;

                case END_ARRAY:
                    return;

                case VALUE_NULL:
                    listener.onNull();
                    break;

                case VALUE_TRUE:
                    listener.onTrue();
                    break;

                case VALUE_FALSE:
                    listener.onFalse();
                    break;

                case KEY_NAME:
                    throw new JsonParsingException("array doesn't have keys", parser.getLocation());

                case END_OBJECT:
                    throw new JsonParsingException("'}', shouldn't occur", parser.getLocation());

                default:
                    throw new JsonParsingException(next.name() + ", shouldn't occur", parser.getLocation());
            }
        }
    }
}
