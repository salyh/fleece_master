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
import java.io.Reader;
import java.math.BigDecimal;
import java.util.NoSuchElementException;

import javax.json.stream.JsonLocation;
import javax.json.stream.JsonParsingException;

/*

Benchmark                                                       Mode   Samples        Score  Score error    Units
o.a.f.c.j.b.BenchmarkStreamParser.parseOnly1000kChars          thrpt         3      101,783        4,515    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.parseOnly100kChars           thrpt         3     1034,848       55,630    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.parseOnly10kChars            thrpt         3    10428,278     6033,344    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.parseOnly1kChars             thrpt         3   105019,057    22400,196    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.parseOnly3kChars             thrpt         3    36579,693     2383,375    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.parseOnlyCombinedChars500    thrpt         3      204,281        5,169    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.read1000kChars               thrpt         3       55,277        8,277    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.read100kChars                thrpt         3      573,250       84,506    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.read10kChars                 thrpt         3     5715,976      966,425    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.read1kChars                  thrpt         3    59745,484    11536,112    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.read3kChars                  thrpt         3    19560,091     3034,515    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.readCombinedChars500         thrpt         3      114,429       38,245    ops/s

*/

public class JsonStreamParserImpl implements JsonChars, EscapedStringAwareJsonParser {

    private final char[] buffer;
    private final Reader in;
    private final BufferStrategy.BufferProvider<char[]> bufferProvider;
    private final BufferStrategy.BufferProvider<char[]> valueProvider;
    private int pointer = -1;
    private boolean unread;

    private final int maxStringSize;

    // current state
    private byte event = 0;

    private final char[] currentValue;
    private int valueLength = 0;

    // location
    private int line = 1;
    private int column = 0;
    private int offset = -1;

    private boolean isCurrentNumberIntegral = false;
    private Integer currentIntegralNumber = null; //for number from 0 - 9
    private BigDecimal currentBigDecimalNumber = null;
    private int avail;
    private int start = -1;
    private int end = -1;

    //we need a stack if we want detect bad formatted json do determine if we are within an array or not
    //example
    //     Streamparser sees: ],1
    //the 1 is only allowed if we are within an array
    //IMHO this can only be determined by build up a stack which tracks the trail of json objects and arrays
    private StructureElement currentStructureElement = null;

    private static final class StructureElement {
        final StructureElement previous;
        final boolean isArray;

        public StructureElement(final StructureElement previous, final boolean isArray) {
            super();
            this.previous = previous;
            this.isArray = isArray;
        }
    }

    public JsonStreamParserImpl(final Reader reader, final int maxStringLength, final BufferStrategy.BufferProvider<char[]> bufferProvider,
            final BufferStrategy.BufferProvider<char[]> valueBuffer) {

        this.maxStringSize = maxStringLength <= 0 ? 8192 : maxStringLength;
        this.currentValue = valueBuffer.newBuffer();

        this.in = reader;
        this.buffer = bufferProvider.newBuffer();
        this.bufferProvider = bufferProvider;
        this.valueProvider = valueBuffer;

        if (currentValue.length < maxStringLength) {
            throw cust("Size of value buffer cannot be smaller than maximum string length");
        }

    }

    private void appendValue(final char c) {
        currentValue[valueLength] = c;
        valueLength++;
    }

    private void copyValues() {

        if ((end - start) > 0) {

            if ((end - start) > maxStringSize) {
                throw tmc();
            }

            System.arraycopy(buffer, start, currentValue, valueLength, (end - start));
            valueLength += (end - start);

        }

        start = end = -1;
    }

    @Override
    public final boolean hasNext() {

        if (currentStructureElement != null || (event != END_ARRAY && event != END_OBJECT) || event == 0) {
            return true;
        }

        //detect garbage at the end of the file after structure is closed
        if (pointer < avail - 2) {

            final char c = readNextNonWhitespaceChar();

            if (c == EOF) {
                return false;
            }

            if (pointer < avail) {
                throw uexc("EOF expected");
            }

        }

        return false;

    }

    private static boolean isAsciiDigit(final char value) {
        return value <= NINE && value >= ZERO;
    }

    private int parseHexDigit(final char value) {

        if (isAsciiDigit(value)) {
            return value - 48;
        } else if (value <= 'f' && value >= 'a') {
            return (value) - 87;
        } else if ((value <= 'F' && value >= 'A')) {
            return (value) - 55;
        } else {
            throw uexc("Invalid hex character");
        }
    }

    private JsonLocationImpl createLocation() {
        return new JsonLocationImpl(line, column, offset);
    }

    private void unread() {
        unread = true;
    }

    private char read() {

        if (unread && pointer >= 0) {
            unread = false;
            return buffer[pointer];
        }

        if (pointer == -1 || (buffer.length - pointer) <= 1) {
            //fillbuffer

            try {

                //copy content from old buffer to valuebuffer
                //correct start end mark
                if (start > -1) {
                    final int savedStart = start;
                    end = buffer.length;
                    copyValues();

                    start = 0;
                    end = (buffer.length - savedStart);

                }

                avail = in.read(buffer, 0, buffer.length);
                if (avail <= 0) {
                    return EOF;
                }

            } catch (final IOException e) {
                close();
                throw uexio(e);
            }

            pointer = 0;
            //end fillbuffer
        } else {
            pointer++;
        }

        offset++;
        column++;

        return buffer[pointer];
    }

    private char readNextNonWhitespaceChar() {

        int dosCount = 0;
        char c = read();

        while (c == SPACE || c == TAB || c == CR || c == EOL) {

            if (c == EOL) {
                line++;
                column = 0;
            }

            //prevent DOS (denial of service) attack
            if (dosCount >= maxStringSize) {
                throw tmc();
            }
            dosCount++;

            //read next character
            c = read();

        }

        return c;
    }

    @Override
    public final Event next() {

        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        final char c = readNextNonWhitespaceChar();

        if (c == COMMA) {

            //last event must one of the following-> " ] } LITERAL
            if (event == START_ARRAY || event == START_OBJECT || event == COMMA_EVENT || event == KEY_NAME) {
                throw uexc("Expected \" ] } LITERAL");
            }

            event = COMMA_EVENT;
            return next();

        }

        if (!isCurrentNumberIntegral) {
            isCurrentNumberIntegral = true;
        }
        if (currentBigDecimalNumber != null) {
            currentBigDecimalNumber = null;
        }
        if (currentIntegralNumber != null) {
            currentIntegralNumber = null;
        }

        if (valueLength != 0) {
            valueLength = 0;
        }

        switch (c) {

            case START_OBJECT_CHAR:

                return handleStartObject();

            case END_OBJECT_CHAR:

                return handleEndObject();

            case START_ARRAY_CHAR:

                return handleStartArray();

            case END_ARRAY_CHAR:

                return handleEndArray();

            case QUOTE:

                return handleQuote();

            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case MINUS:
            case FALSE_F: // false
            case TRUE_T: // true
            case NULL_N: // null

                return handleLiteral(c);
            default:
                throw uexc("Excpected structural character or digit or 't' or 'n' or 'f' or '-'");

        }

    }

    private Event handleStartObject() {

        //last event must one of the following-> : , [
        if (event != 0 && event != KEY_NAME && event != START_ARRAY && event != COMMA_EVENT) {
            throw uexc("Excpected : , [");
        }

        if (currentStructureElement == null) {
            currentStructureElement = new StructureElement(null, false);
        } else {
            final StructureElement localStructureElement = new StructureElement(currentStructureElement, false);
            currentStructureElement = localStructureElement;
        }

        return EVT_MAP[event = START_OBJECT];

    }

    private Event handleEndObject() {

        //last event must one of the following-> " ] { } LITERAL
        if (event == START_ARRAY || event == COMMA_EVENT || event == KEY_NAME) {
            throw uexc("Expected \" ] { } LITERAL");
        }

        currentStructureElement = currentStructureElement.previous;

        return EVT_MAP[event = END_OBJECT];
    }

    private Event handleStartArray() {

        //last event must one of the following-> : , [
        if (event != 0 && event != KEY_NAME && event != START_ARRAY && event != COMMA_EVENT) {
            throw uexc("Expected : , [");
        }

        if (currentStructureElement == null) {
            currentStructureElement = new StructureElement(null, true);
        } else {
            final StructureElement localStructureElement = new StructureElement(currentStructureElement, true);
            currentStructureElement = localStructureElement;
        }

        return EVT_MAP[event = START_ARRAY];
    }

    private Event handleEndArray() {

        //last event must one of the following-> [ ] } " LITERAL
        if (event == START_OBJECT || event == COMMA_EVENT) {
            throw uexc("Expected [ ] } \" LITERAL");
        }

        currentStructureElement = currentStructureElement.previous;

        return EVT_MAP[event = END_ARRAY];
    }

    private void readString() {

        //char highSurrogate = 0;

        char n = read(); //first char after the starting quote

        if (n == QUOTE) {
            return;
        }

        while (true) {

            if (n == ESCAPE_CHAR) {

                n = read();

                //  \ u XXXX -> unicode char
                if (n == 'u') {
                    n = parseUnicodeHexChars();
                    //highSurrogate = checkSurrogates(n, highSurrogate);

                    appendValue(n);

                    // \\ -> \
                } else if (n == ESCAPE_CHAR) {
                    appendValue(n);

                } else {
                    appendValue(Strings.asEscapedChar(n));

                }

                n = read();

            } else if (n == QUOTE) {

                if (valueLength > maxStringSize) {
                    throw tmc();
                }

                return;
            } else if (n == EOL) {
                throw uexc("Unexpected linebreak");

            } else if (n >= '\u0000' && n <= '\u001F') {
                throw uexc("Unescaped control character");

            } else {

                //char highSurrogate0 = 0;
                start = pointer;

                while ((n = read()) > '\u001F' && n != ESCAPE_CHAR && n != EOL && n != QUOTE) {
                    //read fast
                    //highSurrogate0 = checkSurrogates(n, highSurrogate0);

                }
                end = pointer;

                copyValues();

            }

        }//end while()

    }

    /*
    private char checkSurrogates(char n, char highSurrogate) {
        //check for invalid surrogates
        //high followed by low       
        if (Character.isHighSurrogate(n)) {

            if (highSurrogate != 0) {
                throw uexc("Unexpected high surrogate");
            }
            return n;
        } else if (Character.isLowSurrogate(n)) {

            if (highSurrogate == 0) {
                throw uexc("Unexpected low surrogate");
            } else if (!Character.isSurrogatePair(highSurrogate, n)) {
                throw uexc("Invalid surrogate pair");
            }
            return 0;
        } else if (highSurrogate != 0 && !Character.isLowSurrogate(n)) {
            throw uexc("Expected low surrogate");
        }
        
        return highSurrogate;
    }*/

    private char parseUnicodeHexChars() {
        // \u08Ac etc       
        return (char) (((parseHexDigit(read())) * 4096) + ((parseHexDigit(read())) * 256) + ((parseHexDigit(read())) * 16) + ((parseHexDigit(read()))));

    }

    private Event handleQuote() {

        //always the beginning quote of a key or value  

        //last event must one of the following-> : { [ ,
        if (event != KEY_NAME && event != START_OBJECT && event != START_ARRAY && event != COMMA_EVENT) {
            throw uexc("Expected : { [ ,");
        }
        //starting quote already consumed
        readString();
        //end quote already consumed
        final char n = readNextNonWhitespaceChar();

        if (n == KEY_SEPARATOR) {
            return EVT_MAP[event = KEY_NAME];

        } else {

            if (event == START_OBJECT) {
                //only allowed within array
                throw uexc("Missing key value");
            }

            //check array context
            if ((event == COMMA_EVENT || event == VALUE_STRING) && !currentStructureElement.isArray) {
                //not in array, only allowed within array
                throw uexc("Not in an array context");
            }

            unread();
            return EVT_MAP[event = VALUE_STRING];
        }

    }

    private void readNumber(final char c) {

        //start can change on any read() if we cross buffer boundary
        start = pointer;

        char y = EOF;

        int digitCount = 0;
        while (isAsciiDigit(y = read())) {

            if (c == ZERO) {
                throw uexc("Leading zeros not allowed");
            }

            if (c == MINUS && digitCount == 48) {
                throw uexc("Leading zeros after minus not allowed");
            }

            digitCount += y;

        }

        if (c == MINUS && digitCount == 0) {

            throw uexc("Unexpected premature end of number");
        }

        if (y == DOT) {
            isCurrentNumberIntegral = false;
            digitCount = 0;
            while (isAsciiDigit(y = read())) {
                digitCount++;
            }

            if (digitCount == 0) {

                throw uexc("Unexpected premature end of number");
            }

        }

        if (y == EXP_LOWERCASE || y == EXP_UPPERCASE) {
            isCurrentNumberIntegral = false;

            y = read(); //+ or - or digit

            if (!isAsciiDigit(y) && y != MINUS && y != PLUS) {
                throw uexc("Expected DIGIT or + or -");
            }

            if (y == MINUS || y == PLUS) {
                y = read();
                if (!isAsciiDigit(y)) {
                    throw uexc("Unexpected premature end of number");
                }

            }

            while (isAsciiDigit(y = read())) {
                //no-op
            }

        }

        end = pointer;

        if (y == SPACE || y == TAB || y == CR) {

            y = readNextNonWhitespaceChar();

        }

        if (y == COMMA || y == END_ARRAY_CHAR || y == END_OBJECT_CHAR || y == EOL) {

            unread();

            //currentValue ['-', DIGIT]
            if (isCurrentNumberIntegral && c == MINUS && digitCount >= 48 && digitCount <= 57) {

                currentIntegralNumber = -(digitCount - 48); //optimize -0 till -9
                return;
            }

            //currentValue [DIGIT]
            if (isCurrentNumberIntegral && c != MINUS && digitCount == 0) {

                currentIntegralNumber = (c - 48); //optimize 0 till 9
                return;
            }

            if (valueLength > 0) {

                //we crossed a buffer boundary, use value buffer
                copyValues();

            } else {
                if ((end - start) >= maxStringSize) {
                    throw tmc();
                }
            }

            return;

        }

        throw uexc("Unexpected premature end of number");

    }

    private Event handleLiteral(final char c) {

        //last event must one of the following-> : , [
        if (event != KEY_NAME && event != START_ARRAY && event != COMMA_EVENT) {
            throw uexc("Excpected : , [");
        }

        if (event == COMMA_EVENT && !currentStructureElement.isArray) {
            //only allowed within array
            throw uexc("Not in an array context");
        }

        // probe literals
        switch (c) {
            case TRUE_T:

                if (read() != TRUE_R || read() != TRUE_U || read() != TRUE_E) {
                    throw uexc("Expected LITERAL: true");
                }
                return EVT_MAP[event = VALUE_TRUE];
            case FALSE_F:

                if (read() != FALSE_A || read() != FALSE_L || read() != FALSE_S || read() != FALSE_E) {
                    throw uexc("Expected LITERAL: false");
                }

                return EVT_MAP[event = VALUE_FALSE];

            case NULL_N:

                if (read() != NULL_U || read() != NULL_L || read() != NULL_L) {
                    throw uexc("Expected LITERAL: null");
                }
                return EVT_MAP[event = VALUE_NULL];

            default:
                readNumber(c);
                return EVT_MAP[event = VALUE_NUMBER];
        }

    }

    @Override
    public String getString() {
        if (event == KEY_NAME || event == VALUE_STRING || event == VALUE_NUMBER) {
            return new String(currentValue, 0, valueLength);//TODO read from buffer in special cases
        } else {
            throw new IllegalStateException(event + " doesn't support getString()");
        }
    }

    @Override
    public boolean isIntegralNumber() {

        if (event != VALUE_NUMBER) {
            throw new IllegalStateException(event + " doesn't support isIntegralNumber()");
        } else {
            return isCurrentNumberIntegral;
        }
    }

    @Override
    public int getInt() {
        if (event != VALUE_NUMBER) {
            throw new IllegalStateException(event + " doesn't support getInt()");
        } else if (isCurrentNumberIntegral && currentIntegralNumber != null) {
            return currentIntegralNumber;
        } else if (isCurrentNumberIntegral) {
            final Integer retVal = valueLength > 0 ? parseIntegerFromChars(currentValue, 0, valueLength) : parseIntegerFromChars(buffer,
                    start, end);
            if (retVal == null) {
                return getBigDecimal().intValue();
            } else {
                return retVal.intValue();
            }
        } else {
            return getBigDecimal().intValue();
        }
    }

    @Override
    public long getLong() {
        if (event != VALUE_NUMBER) {
            throw new IllegalStateException(event + " doesn't support getLong()");
        } else if (isCurrentNumberIntegral && currentIntegralNumber != null) {
            return currentIntegralNumber;
        } else if (isCurrentNumberIntegral) {
            final Long retVal = valueLength > 0 ? parseLongFromChars(currentValue, 0, valueLength) : parseLongFromChars(buffer, start, end);
            if (retVal == null) {
                return getBigDecimal().longValue();
            } else {
                return retVal.longValue();
            }
        } else {
            return getBigDecimal().longValue();
        }

    }

    @Override
    public BigDecimal getBigDecimal() {
        if (event != VALUE_NUMBER) {
            throw new IllegalStateException(event + " doesn't support getBigDecimal()");
        } else if (currentBigDecimalNumber != null) {
            return currentBigDecimalNumber;
        } else {
            return (currentBigDecimalNumber = valueLength > 0 ? new BigDecimal(currentValue, 0, valueLength) : new BigDecimal(buffer,
                    start, (end - start)));
        }

    }

    @Override
    public JsonLocation getLocation() {
        return createLocation();
    }

    @Override
    public void close() {

        bufferProvider.release(buffer);
        valueProvider.release(currentValue);

        try {

            in.close();
        } catch (final IOException e) {

            //ignore
        }

    }

    @Override
    public String getEscapedString() {
        return Strings.escape(getString());
    }

    //parse a char[] to long while checking overflow
    //if overflowed return null
    //no additional checks since we are sure here that there are no non digits in the array
    private static Long parseLongFromChars(final char[] chars, final int start, final int end) {

        long retVal = 0;
        final boolean negative = chars[start] == MINUS;
        for (int i = negative ? start + 1 : start; i < end; i++) {
            final long tmp = retVal * 10 + (chars[i] - ZERO);
            if (tmp < retVal) { //check overflow
                return null;
            } else {
                retVal = tmp;
            }
        }

        return negative ? -retVal : retVal;
    }

    //parse a char[] to int while checking overflow
    //if overflowed return null
    //no additional checks since we are sure here that there are no non digits in the array
    private static Integer parseIntegerFromChars(final char[] chars, final int start, final int end) {

        int retVal = 0;
        final boolean negative = chars[start] == MINUS;
        for (int i = negative ? start + 1 : start; i < end; i++) {
            final int tmp = retVal * 10 + (chars[i] - ZERO);
            if (tmp < retVal) { //check overflow
                return null;
            } else {
                retVal = tmp;
            }
        }

        return negative ? -retVal : retVal;
    }

    private JsonParsingException uexc(final char c, final String message) {
        final JsonLocation location = createLocation();
        return new JsonParsingException("Unexpected character '" + c + "' (Codepoint: " + String.valueOf(c).codePointAt(0) + ") on line "
                + location.getLineNumber() + ". Reason is [" + message + "]", location);
    }

    private JsonParsingException uexc(final String message) {
        final char c = pointer < 0 ? 0 : buffer[pointer];
        return uexc(c, message);
    }

    private JsonParsingException tmc() {
        final JsonLocation location = createLocation();
        return new JsonParsingException("Too many characters. Maximum string length of " + maxStringSize + " exceeded on line "
                + location.getLineNumber(), location);
    }

    private JsonParsingException uexio(final IOException e) {
        final JsonLocation location = createLocation();
        return new JsonParsingException("Unexpected IO exception on line " + location.getLineNumber(), e, location);
    }

    private JsonParsingException cust(final String message) {
        final JsonLocation location = createLocation();
        return new JsonParsingException("General exception. Reason is [" + message + "]", location);
    }

}