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

/*Benchmark                                                       Mode   Samples        Score  Score error    Units
o.a.f.c.j.b.BenchmarkStreamParser.parseOnly1000kChars          thrpt         3       71,351       10,310    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.parseOnlyCombinedChars500    thrpt         3      148,808        5,555    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.read1000kChars               thrpt         3       43,630        8,918    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.readCombinedChars500         thrpt         3       92,332       16,575    ops/s


Filesize: 15534444484 bytes
Duration: 263365 ms
String Events: 420000000
Integral Number Events: 300000000
Big Decimal Events: 60000000
Parsing speed: 58984 bytes/ms
Parsing speed: 59066328 bytes/sec
Parsing speed: 57681 kbytes/sec
Parsing speed: 56 mb/sec
Parsing speed: 450 mbit/sec
*/

public class RightDirectionJsonStreamParserImpl implements JsonChars, EscapedStringAwareJsonParser {

    private final char[] buffer;
    private final Reader in;
    private final BufferStrategy.BufferProvider<char[]> bufferProvider;
    private final BufferStrategy.BufferProvider<char[]> valueProvider;
    private int pointer = -1;
    private char mark;
    private boolean reset;

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

    public RightDirectionJsonStreamParserImpl(final Reader reader, final int maxStringLength, final BufferStrategy.BufferProvider<char[]> bufferProvider,
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
        
        start=end=-1;
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

    private char read() {
        if (reset) {
            reset = false;

            offset++;
            column++;

            if (mark == EOL) {
                line++;

            }

            return mark;
        }

        if (pointer == -1 || (buffer.length - pointer) <= 1) {
            try {
                avail = in.read(buffer, 0, buffer.length);
                if (avail <= 0) {
                    return EOF;
                }
                
                //correct start end mark if neccessary
                if(start > -1) {
                    int savedStart=start;
                    end = buffer.length-1;
                    copyValues();
                    
                    start=0;
                    end=(buffer.length-savedStart);
                     
                }
                
                

            } catch (final IOException e) {
                close();
                throw uexio(e);
            }

            pointer = 0;

        } else {
            pointer++;
        }

        offset++;
        column++;

        return buffer[pointer];

    }

    private void markCurrentChar() {
        mark = buffer[pointer];
    }

    private void resetToLastMark() {
        reset = true;
        offset--;

        if (mark == EOL) {
            line--;
            column = -1; //we don't have this info
        }
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

        if (c == COMMA_CHAR) {

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

            case QUOTE_CHAR:

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

        //boolean esc = false;
        //char highSurrogate = 0;
        
        char n = read(); //first char after the starting quote
        
        if(n==QUOTE_CHAR) {
            return;
        }

        while (true) {

            if (n == ESCAPE_CHAR) {

                n = read();

                //  \ u XXXX -> unicode char
                if (n == 'u') {
                    n = parseUnicodeHexChars();
                    appendValue(n);
                
                // \\ -> \
                } else if (n == ESCAPE_CHAR) {
                    appendValue(n);

                }else   {
                    appendValue(Strings.asEscapedChar(n));
                    
                }
                
                n=read();

            } else if (n == QUOTE_CHAR) {
                return;
            } else if (n == EOL) {
                throw uexc("Unexpected linebreak");

            } else if (n >= '\u0000' && n <= '\u001F') {
                throw uexc("Unescaped control character");

            } else {

                start = pointer;

                while ((n = read()) > '\u001F' && n != ESCAPE_CHAR && n != EOL && n != QUOTE_CHAR) {
                    //read fast

                }
                end = pointer;

                copyValues();

                
                
                //check for invalid surrogates

                //high followed by low
                /*
                if (Character.isHighSurrogate(n)) {

                    if (highSurrogate != 0) {
                        throw uexc("Unexpected high surrogate");
                    }
                    highSurrogate = n;
                } else if (Character.isLowSurrogate(n)) {

                    if (highSurrogate == 0) {
                        throw uexc("Unexpected low surrogate");
                    } else if (!Character.isSurrogatePair(highSurrogate, n)) {
                        throw uexc("Invalid surrogate pair");
                    }
                    highSurrogate = 0;
                } else if (highSurrogate != 0 && !Character.isLowSurrogate(n)) {
                    throw uexc("Expected low surrogate");
                }*/

            }

        }//end while()

    }

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
        readString();
        final char n = readNextNonWhitespaceChar();

        markCurrentChar();

        if (n == KEY_SEPARATOR) {

            return EVT_MAP[event = KEY_NAME];

        } else {

            if (event == COMMA_EVENT) {
                //only allowed within array

                if (stack[stackPointer - 1]) {
                    throw uexc("Not in an array context");
                }

            }

            resetToLastMark();
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

        if (valueLength > 2 && c == MINUS && currentValue[1] == ZERO) {
            throw uexc("Leading zeros with minus not allowed");
        }

        if (valueLength > 1 && c == ZERO) {
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

        if (y == SPACE || y == TAB || y == CR) {

            y = readNextNonWhitespaceChar();

        }

        markCurrentChar();

        if (y == COMMA_CHAR || y == END_ARRAY_CHAR || y == END_OBJECT_CHAR || y == EOL) {
            //end of number
            resetToLastMark();

            //currentValue ['-', DIGIT]
            if (isCurrentNumberIntegral && c == MINUS && valueLength == 2) {

                currentIntegralNumber = -(currentValue[1] - 48); //optimize -0 till -9
            }

            //currentValue [DIGIT]
            if (isCurrentNumberIntegral && c != MINUS && valueLength == 1) {

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
            final Integer retVal = parseIntegerFromChars(buffer, start, end);
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
            final Long retVal = parseLongFromChars(buffer, start, end);
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
            return (currentBigDecimalNumber = new BigDecimal(buffer, start, (end - start)));
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