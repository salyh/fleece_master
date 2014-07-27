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

import java.math.BigDecimal;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.json.stream.JsonLocation;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParsingException;

public class JsonParallelParser implements JsonParser, EscapedStringAwareJsonParser {

    final JsonParser parser;

    public JsonParallelParser(final JsonParser parser) {
        super();
        this.parser = parser;
        final Threader t = new Threader();
        t.caller = Thread.currentThread();
        t.start();
    }

    final BlockingQueue<EventPair> queue = new LinkedBlockingQueue<JsonParallelParser.EventPair>(1000);
    volatile EventPair cur;
    volatile Exception ex;
    volatile boolean died;

    @Override
    public void close() {
        parser.close();

    }

    @Override
    public BigDecimal getBigDecimal() {

        if (cur.event != Event.VALUE_NUMBER) {
            throw new IllegalStateException(cur.event + " doesn't support isIntegralNumber()");
        }
        return cur.bdVal;
    }

    @Override
    public int getInt() {
        if (cur.event != Event.VALUE_NUMBER) {
            throw new IllegalStateException(cur.event + " doesn't support isIntegralNumber()");
        }

        return cur.intVal;
    }

    @Override
    public JsonLocation getLocation() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getLong() {
        if (cur.event != Event.VALUE_NUMBER) {
            throw new IllegalStateException(cur.event + " doesn't support isIntegralNumber()");
        }

        return cur.longVal;
    }

    @Override
    public String getString() {
        if (cur.event == Event.KEY_NAME || cur.event == Event.VALUE_STRING || cur.event == Event.VALUE_NUMBER) {
            return cur.stringVal;
        }
        throw new IllegalStateException(cur.event + " doesn't support getString()");

    }

    @Override
    public boolean hasNext() {
        return !(died && queue.isEmpty() && !parser.hasNext());
    }

    @Override
    public boolean isIntegralNumber() {
        if (cur.event != Event.VALUE_NUMBER) {
            throw new IllegalStateException(cur.event + " doesn't support isIntegralNumber()");
        }

        return cur.isIntegral;
    }

    @Override
    public Event next() {
        try {
            //System.out.println("block? "+queue.size());
            cur = queue.take();
            //System.out.println("took "+cur);
            return cur.event;

        } catch (final InterruptedException e) {
            //System.out.println("interrupted");
            throw new JsonParsingException(ex.getMessage(), ex, null);
        }
    }

    static class EventPair {

        BigDecimal bdVal;
        long longVal;
        int intVal;
        boolean isIntegral;
        String stringVal;
        Event event;

        @Override
        public String toString() {
            return "EventPair [bdVal=" + bdVal + ", longVal=" + longVal + ", intVal=" + intVal + ", isIntegral=" + isIntegral
                    + ", stringVal=" + stringVal + ", event=" + event + "]";
        }

    }

    private class Threader extends Thread {

        public Thread caller;

        public Threader() {
            // TODO Auto-generated constructor stub
        }

        @Override
        public void run() {

            try {
                while (parser.hasNext()) {

                    final Event e = parser.next();

                    final EventPair pair = new EventPair();
                    pair.event = e;

                    if (e == Event.KEY_NAME || e == Event.VALUE_STRING) {
                        pair.stringVal = parser.getString();

                    } else if (e == Event.VALUE_NUMBER) {
                        pair.isIntegral = parser.isIntegralNumber();
                        pair.bdVal = parser.getBigDecimal();
                        pair.longVal = parser.getLong();
                        pair.intVal = parser.getInt();
                    }

                    queue.put(pair);
                    //System.out.println("put "+pair);

                }
            } catch (final Exception e) {

                ex = e;
                //System.out.println("EEE "+e.getMessage());
                caller.interrupt();
                //return; // end thread on error

            }
            died = true;
            //System.out.println("die");
        }
    }

    @Override
    public String getEscapedString() {
        // TODO Auto-generated method stub
        return Strings.escape(getString());
    }

}
