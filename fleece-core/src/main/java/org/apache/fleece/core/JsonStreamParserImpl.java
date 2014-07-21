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
import java.util.Arrays;
import java.util.NoSuchElementException;

import javax.json.stream.JsonLocation;
import javax.json.stream.JsonParsingException;

/*Benchmark                                                       
                                                               Mode   Samples        Score  Score error    Units
o.a.f.c.j.b.BenchmarkStreamParser.parseOnly1000kChars          thrpt         3       97,389        1,181    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.parseOnlyCombinedChars500    thrpt         3      199,216        5,156    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.read1000kChars               thrpt         3       52,697        5,957    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.readCombinedChars500         thrpt         3      107,887       13,950    ops/s
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
    private boolean[] stack = new boolean[256];
    private int stackPointer;

    public JsonStreamParserImpl(final Reader reader, final int maxStringLength, final BufferStrategy.BufferProvider<char[]> bufferProvider,
            final BufferStrategy.BufferProvider<char[]> valueBuffer) {

        this.maxStringSize = maxStringLength <= 0 ? 8192 : maxStringLength;
        this.currentValue = valueBuffer.newBuffer();

        this.in = reader;
        this.buffer = bufferProvider.newBuffer();
        this.bufferProvider = bufferProvider;
        this.valueProvider = valueBuffer;
    }

    private void appendValue(final char c) {
        /*if (valueLength >= maxStringSize) {
            throw tmc();
        }*/

        currentValue[valueLength] = c;
        valueLength++;
    }

    private void copyValues() {
        /*if (valueLength >= maxStringSize) {
            throw tmc();
        }*/

        if ((end - start) > 0) {
            System.arraycopy(buffer, start, currentValue, valueLength, (end - start));
            valueLength += (end - start);

        }

        start = end = -1;
    }

    private String getValue() {
        return new String(currentValue, 0, valueLength);
    }

    @Override
    public final boolean hasNext() {

        if (stackPointer > 0 || (event != END_ARRAY && event != END_OBJECT) || event == 0) {
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

                //correct start end mark if neccessary
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

                if (isCurrentNumberIntegral) {
                    isCurrentNumberIntegral = false;
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
                if (isCurrentNumberIntegral) {
                    isCurrentNumberIntegral = false;
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

        if (stackPointer >= stack.length) {
            stack = Arrays.copyOf(stack, stack.length * 2);
        }

        stack[stackPointer++] = true;

        return EVT_MAP[event = START_OBJECT];

    }

    private Event handleEndObject() {

        //last event must one of the following-> " ] { } LITERAL
        if (event == START_ARRAY || event == COMMA_EVENT) {
            throw uexc("Expected \" ] { } LITERAL");
        }

        if (!stack[stackPointer - 1]) {
            throw uexc("Unbalanced container, expected ]");
        }

        stackPointer--;

        return EVT_MAP[event = END_OBJECT];
    }

    private Event handleStartArray() {

        //last event must one of the following-> : , [
        if (event != 0 && event != KEY_NAME && event != START_ARRAY && event != COMMA_EVENT) {
            throw uexc("Expected : , [");
        }

        if (stackPointer >= stack.length) {
            stack = Arrays.copyOf(stack, stack.length * 2);
        }

        stack[stackPointer++] = false;

        return EVT_MAP[event = START_ARRAY];
    }

    private Event handleEndArray() {

        //last event must one of the following-> [ ] } " LITERAL
        if (event == START_OBJECT || event == COMMA_EVENT) {
            throw uexc("Expected [ ] } \" LITERAL");
        }

        if (stack[stackPointer - 1]) {
            throw uexc("Unbalanced container, expected }");
        }
        stackPointer--;

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

            if (event == COMMA_EVENT) {
                //only allowed within array

                if (stack[stackPointer - 1]) {
                    throw uexc("Not in an array context");
                }

            }

            unread();
            return EVT_MAP[event = VALUE_STRING];
        }

    }

    private void readNumber(final char c) {

        start = pointer;

        char y = 0;
        isCurrentNumberIntegral = true;

        while (isAsciiDigit(y = read())) {
            //no-op
        }

        if ((pointer - start) > 2 && c == MINUS && buffer[start + 1] == ZERO) {
            throw uexc("Leading zeros with minus not allowed");
        }

        if (pointer - start > 1 && c == ZERO) {
            throw uexc("Leading zeros not allowed");
        }

        if (y == DOT) {
            isCurrentNumberIntegral = false;

            while (isAsciiDigit(y = read())) {
                //no-op
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
        copyValues();

        if (y == SPACE || y == TAB || y == CR) {

            y = readNextNonWhitespaceChar();

        }

        if (y == COMMA || y == END_ARRAY_CHAR || y == END_OBJECT_CHAR || y == EOL) {
            //end of number
            unread();

            //currentValue ['-', DIGIT]
            if (isCurrentNumberIntegral && c == MINUS && pointer - start == 2) {

                currentIntegralNumber = -(currentValue[1] - 48); //optimize -0 till -9
            }

            //currentValue [DIGIT]
            if (isCurrentNumberIntegral && c != MINUS && pointer - start == 1) {

                currentIntegralNumber = (currentValue[0] - 48); //optimize 0 till 9
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

        if (event == COMMA_EVENT) {
            //only allowed within array

            if (stack[stackPointer - 1]) {
                throw uexc("Not in an array context");
            }

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
            return getValue();
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
            final Integer retVal = parseIntegerFromChars(currentValue, 0, valueLength);
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
            final Long retVal = parseLongFromChars(currentValue, 0, valueLength);
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
            return (currentBigDecimalNumber = new BigDecimal(currentValue, 0, valueLength));
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
        return Strings.escape(getValue());
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

}