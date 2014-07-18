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

import static org.apache.fleece.core.Strings.asEscapedChar;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;

import javax.json.stream.JsonLocation;
import javax.json.stream.JsonParsingException;

public class Copy2OfJsonStreamParserImpl implements JsonChars, EscapedStringAwareJsonParser {

    private final char[] buffer;
    private final Reader in;
    private final BufferStrategy.BufferProvider<char[]> bufferProvider;
    private final BufferStrategy.BufferProvider<char[]> valueProvider;
    private int pointer = -1;
    private char mark;
    private boolean reset;

    private final int maxStringSize;

    // current state
    private Event event = null;
    private int lastSignificantChar = -1;

    protected final char[] currentValue;
    private int valueLength = 0;

    // location
    private int line = 1;
    private int column = 1;
    private int offset = 0;

    private boolean isCurrentNumberIntegral = false;
    private Integer currentIntegralNumber = null; //for number from 0 - 9
    private BigDecimal currentBigDecimalNumber = null;
    private int avail;

    //private int structCount=0;

    public Copy2OfJsonStreamParserImpl(final Reader reader, final int maxStringLength,
            final BufferStrategy.BufferProvider<char[]> bufferProvider, final BufferStrategy.BufferProvider<char[]> valueBuffer) {

        this.maxStringSize = maxStringLength <= 0 ? 8192 : maxStringLength;
        this.currentValue = valueBuffer.newBuffer();

        this.in = reader;
        this.buffer = bufferProvider.newBuffer();
        this.bufferProvider = bufferProvider;
        this.valueProvider = valueBuffer;
    }

    private void appendValue(final char c) {
        if (valueLength >= maxStringSize) {
            throw new JsonParsingException("to many chars", createLocation());
        }

        currentValue[valueLength] = c;
        valueLength++;
    }

    private String getValue() {
        return new String(currentValue, 0, valueLength);
    }

    @Override
    public final boolean hasNext() {

        if (event == null /*|| structCount > 0*/) {
            //first event
            return true;
        }

        if (event != Event.END_ARRAY && event != Event.END_OBJECT) {
            //json can only end with Event.END_ARRAY OR Event.END_OBJECT
            return true;
        } else {
            //maybe the end
            System.out.println("maybe end avail:" + avail + "/pointer:" + pointer + " /buflen+" + buffer.length + " -> ");
            if (pointer < avail - 1) {

                final char c = readNextNonWhitespaceChar();

                System.out.println("read until next non ws " + c);
                System.out.println("after ws " + avail + "/pointer:" + pointer + " /buflen+" + buffer.length + " -> ");
                if (c == 0) {
                    return false;
                }

                if (pointer < avail) {

                    if (c != COMMA && c != END_ARRAY_CHAR && c != END_OBJECT_CHAR) {
                        throw new JsonParsingException("unexpected hex value " + c, createLocation());
                    }

                    markCurrentChar();
                    resetToLastMark();
                    return true;
                }

            }

            return false;
            /*
            
            System.out.print("hasNext() avail:"+avail+"/pointer:"+pointer+" /buflen+"+buffer.length+" -> ");
            boolean retval= ((avail>pointer+2)  || event == null || avail==buffer.length);
            System.out.println(retval);
            return retval;*/
        }

    }

    private static boolean isAsciiDigit(final char value) {
        return value <= NINE && value >= ZERO;
    }

    private char checkHexDigit(final char value) {
        if (isAsciiDigit(value) || (value <= 'f' && value >= 'a') || (value <= 'F' && value >= 'A')) {
            return value;
        } else {
            throw new JsonParsingException("unexpected hex value " + value, createLocation());
        }
    }

    private JsonLocationImpl createLocation() {
        return new JsonLocationImpl(line, column, offset);
    }

    protected int readNextChar() {
        if (reset) {
            reset = false;
            System.out.println(" RETURN mark " + mark);
            return mark;
        }

        if (pointer == -1 || (buffer.length - pointer) <= 1) {
            try {
                avail = in.read(buffer, 0, buffer.length);
                System.out.println("fillbuff " + avail + ": " + new String(buffer));
                if (avail <= 0) {

                    return -1;
                }

            } catch (final Exception e) {
                close();
                throw new JsonParsingException("Unexpected IO Exception", e, createLocation());

            }

            pointer = 0;

        } else {
            pointer++;
        }
        System.out.println("read('" + buffer[pointer] + "') avail:" + avail + "/pointer:" + pointer + "/buflen:" + buffer.length);
        return buffer[pointer];

    }

    protected void markCurrentChar() {
        mark = buffer[pointer];
    }

    protected void resetToLastMark() {
        reset = true;
        offset--;
        column--;
    }

    private char read() {
        final int c = readNextChar();

        if (c == -1) {

            //TODO
            throw new JsonParsingException("eof", createLocation());
            //NoSuchElementException();
        }

        offset++;
        column++;

        return (char) c;
    }

    // Event.START_ARRAY
    // Event.START_OBJECT

    // Event.END_ARRAY
    // Event.END_OBJECT

    // Event.KEY_NAME

    // ** 5 Value Event
    // Event.VALUE_FALSE
    // Event.VALUE_NULL
    // Event.VALUE_NUMBER
    // Event.VALUE_STRING
    // Event.VALUE_TRUE

    // ***********************
    // ***********************
    // Significant chars (8)

    // 0 - start doc
    // " - quote
    // , - comma

    // : - separator
    // { - start obj
    // } - end obj
    // [ - start arr
    // ] - end arr

    private char readNextNonWhitespaceChar() {

        int dosCount = 0;
        char c = read();

        while (c == SPACE || c == TAB || c == CR || c == EOL) {
            c = read();

            if (c == EOL) {
                line++;
            } else {

                if (dosCount >= maxStringSize) {
                    throw new JsonParsingException("max string size reached", createLocation());
                }
                dosCount++;

            }

        }

        return c;

    }

    @Override
    public final Event next() {

        event = null;
        if (isCurrentNumberIntegral) {
            isCurrentNumberIntegral = false;
        }
        if (currentBigDecimalNumber != null) {
            currentBigDecimalNumber = null;
        }
        if (currentIntegralNumber != null) {
            currentIntegralNumber = null;
        }

        valueLength = 0;

        final char c = readNextNonWhitespaceChar();

        switch (c) {

            case COMMA:

                //lastSignificantChar must one of the following: " ] }
                if (lastSignificantChar >= 0 && lastSignificantChar != I_QUOTE && lastSignificantChar != I_END_ARRAY_CHAR
                        && lastSignificantChar != I_END_OBJECT_CHAR) {
                    throw new JsonParsingException("Unexpected character " + c + " (last significant was " + lastSignificantChar + ")",
                            createLocation());
                }

                lastSignificantChar = c;

                return next();

            case START_OBJECT_CHAR:

                handleStartObject(c);

                break;

            case END_OBJECT_CHAR:

                handleEndObject(c);

                break;
            case START_ARRAY_CHAR:

                handleStartArray(c);

                break;
            case END_ARRAY_CHAR:

                handleEndArray(c);
                break;

            case QUOTE: // must be escaped within a value

                handleQuote(c);

                break;

            // non string values (literals)
            //$FALL-THROUGH$
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

                handleLiteral(c);

                break;

            // eof
            case EOF:

                throw new JsonParsingException("Unexpected character " + c, createLocation());//new NoSuchElementException(); //TODO

            default:
                throw new JsonParsingException("Unexpected character " + c, createLocation());

        }

        System.out.println(" --> " + event + " ->'" + getValue() + "'");
        return event;

        //throw new JsonParsingException("Unexpected character " + c, createLocation());

    }

    private void handleStartObject(final char c) {

        if (lastSignificantChar == -2
                || (lastSignificantChar != -1 && lastSignificantChar != I_KEY_SEPARATOR && lastSignificantChar != I_COMMA && lastSignificantChar != I_START_ARRAY_CHAR)) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + lastSignificantChar + ")",
                    createLocation());
        }

        lastSignificantChar = c;

        event = Event.START_OBJECT;

    }

    private void handleEndObject(final char c) {

        if (lastSignificantChar >= 0 && lastSignificantChar != I_START_OBJECT_CHAR && lastSignificantChar != I_END_ARRAY_CHAR
                && lastSignificantChar != I_QUOTE && lastSignificantChar != I_END_OBJECT_CHAR) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + lastSignificantChar + ")",
                    createLocation());
        }

        lastSignificantChar = c;

        event = Event.END_OBJECT;
    }

    private void handleStartArray(final char c) {

        if (lastSignificantChar == -2
                || (lastSignificantChar != -1 && lastSignificantChar != I_KEY_SEPARATOR && lastSignificantChar != I_COMMA && lastSignificantChar != I_START_ARRAY_CHAR)) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + lastSignificantChar + ")",
                    createLocation());
        }

        lastSignificantChar = c;

        event = Event.START_ARRAY;
    }

    private void handleEndArray(final char c) {

        if (lastSignificantChar >= 0 && lastSignificantChar != I_START_ARRAY_CHAR && lastSignificantChar != I_END_ARRAY_CHAR
                && lastSignificantChar != I_END_OBJECT_CHAR && lastSignificantChar != I_QUOTE) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + lastSignificantChar + ")",
                    createLocation());
        }

        lastSignificantChar = c;

        event = Event.END_ARRAY;
    }

    private void readString() {

        boolean esc = false;

        while (true) {
            final char n = read();

            if (n == ESCAPE_CHAR) {
                if (esc) {
                    esc = false;
                    appendValue(ESCAPE_CHAR);

                } else {
                    esc = true;
                }
            } else if (!esc && n == QUOTE) {
                break;
            } else if (n == EOL) {
                throw new JsonParsingException("Unexpected linebreak ", createLocation());

            } else {

                if (esc) {
                    if (n == 'u') {
                        parseUnicodeHexChars();
                    } else {
                        appendValue(asEscapedChar(n));
                    }

                    esc = false;

                } else {
                    appendValue(n);
                }

            }

        }

    }

    private void parseUnicodeHexChars() {
        // \u08ac etc
        final char decimal = (char) (((checkHexDigit(read()) - 48) * 4096) + ((checkHexDigit(read()) - 48) * 256)
                + ((checkHexDigit(read()) - 48) * 16) + ((checkHexDigit(read()) - 48)));
        appendValue(decimal);

    }

    private void handleQuote(final char c) {

        if (lastSignificantChar >= 0 && lastSignificantChar != I_KEY_SEPARATOR && lastSignificantChar != I_START_OBJECT_CHAR
                && lastSignificantChar != I_START_ARRAY_CHAR && lastSignificantChar != I_COMMA) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + lastSignificantChar + ")",
                    createLocation());
        }

        //always the beginning quote of a key or value

        lastSignificantChar = c;

        readString();
        final char n = readNextNonWhitespaceChar();

        markCurrentChar();

        if (n == KEY_SEPARATOR) {

            event = Event.KEY_NAME;
            lastSignificantChar = n;

        } else {
            resetToLastMark();
            event = Event.VALUE_STRING;
        }

    }

    private void handleLiteral(final char c) {
        if (lastSignificantChar >= 0 && lastSignificantChar != I_KEY_SEPARATOR && lastSignificantChar != I_COMMA
                && lastSignificantChar != I_START_ARRAY_CHAR) {
            throw new JsonParsingException("unexpected character " + c + " last significant " + (char) lastSignificantChar,
                    createLocation());
        }

        lastSignificantChar = -2;

        // probe literals
        switch (c) {
            case TRUE_T:

                if (read() != TRUE_R || read() != TRUE_U || read() != TRUE_E) {
                    throw new JsonParsingException("Unexpected literal ", createLocation());
                }
                event = Event.VALUE_TRUE;
                break;
            case FALSE_F:

                if (read() != FALSE_A || read() != FALSE_L || read() != FALSE_S || read() != FALSE_E) {
                    throw new JsonParsingException("Unexpected literal ", createLocation());
                }

                event = Event.VALUE_FALSE;
                break;
            case NULL_N:

                if (read() != NULL_U || read() != NULL_L || read() != NULL_L) {
                    throw new JsonParsingException("Unexpected literal ", createLocation());
                }
                event = Event.VALUE_NULL;
                break;

            default: // number
                appendValue(c);

                boolean dotpassed = false;
                boolean epassed = false;
                char last = c;

                while (true) {

                    char n = read();

                    if (!isNumber(n)) {

                        if (n == SPACE || n == TAB || n == CR) {

                            n = readNextNonWhitespaceChar();

                        }

                        markCurrentChar();

                        if (n == COMMA || n == END_ARRAY_CHAR || n == END_OBJECT_CHAR || n == EOL) {
                            resetToLastMark();

                            isCurrentNumberIntegral = (!dotpassed && !epassed);

                            if (isCurrentNumberIntegral && c == MINUS && valueLength < 3 && last >= '0' && last <= '9') {

                                currentIntegralNumber = -(last - 48); //optimize -0 till -9
                            }

                            if (isCurrentNumberIntegral && c != MINUS && valueLength < 2 && last >= '0' && last <= '9') {

                                currentIntegralNumber = (last - 48); //optimize 0 till 9
                            }

                            event = Event.VALUE_NUMBER;

                            break;
                        } else {
                            throw new JsonParsingException("unexpected character " + n + " (" + (int) n + ")", createLocation());
                        }

                    }

                    //is one of 0-9 . e E - +

                    // minus only allowed as first char or after e/E
                    if (n == MINUS && valueLength > 0 && last != EXP_LOWERCASE && last != EXP_UPPERCASE) {
                        throw new JsonParsingException("unexpected character " + n, createLocation());
                    }

                    // plus only allowed after e/E
                    if (n == PLUS && last != EXP_LOWERCASE && last != EXP_UPPERCASE) {
                        throw new JsonParsingException("unexpected character " + n, createLocation());
                    }

                    if (!dotpassed && c == ZERO && valueLength > 0 && n != DOT) {
                        throw new JsonParsingException("unexpected character " + n + " (no leading zeros allowed)", createLocation());
                    }

                    if (n == DOT) {

                        if (dotpassed) {
                            throw new JsonParsingException("more than one dot", createLocation());
                        }

                        if (epassed) {
                            throw new JsonParsingException("no dot allowed here", createLocation());
                        }

                        dotpassed = true;

                    }

                    if (n == EXP_LOWERCASE || n == EXP_UPPERCASE) {

                        if (epassed) {
                            throw new JsonParsingException("more than one e/E", createLocation());
                        }

                        epassed = true;
                    }

                    appendValue(n);
                    last = n;

                }

        }

    }

    private static boolean isNumber(final char c) {
        return isAsciiDigit(c) || c == DOT || c == MINUS || c == PLUS || c == EXP_LOWERCASE || c == EXP_UPPERCASE;
    }

    @Override
    public String getString() {
        if (event == Event.KEY_NAME || event == Event.VALUE_STRING || event == Event.VALUE_NUMBER) {
            return getValue();
        }
        throw new IllegalStateException(event + " doesn't support getString()");
    }

    @Override
    public boolean isIntegralNumber() {

        if (event != Event.VALUE_NUMBER) {
            throw new IllegalStateException(event + " doesn't support isIntegralNumber()");
        }

        return isCurrentNumberIntegral;
    }

    @Override
    public int getInt() {
        if (event != Event.VALUE_NUMBER) {
            throw new IllegalStateException(event + " doesn't support getInt()");
        }

        if (isCurrentNumberIntegral && currentIntegralNumber != null) {
            return currentIntegralNumber;
        }

        if (isCurrentNumberIntegral) {
            return (int) parseLongFromChars(currentValue, 0, valueLength);
        }

        return getBigDecimal().intValue();
    }

    @Override
    public long getLong() {
        if (event != Event.VALUE_NUMBER) {
            throw new IllegalStateException(event + " doesn't support getLong()");
        }

        if (isCurrentNumberIntegral && currentIntegralNumber != null) {
            return currentIntegralNumber;
        } // int is ok, its only from 0-9

        if (isCurrentNumberIntegral) {
            return parseLongFromChars(currentValue, 0, valueLength);
        }

        return getBigDecimal().longValue();
    }

    @Override
    public BigDecimal getBigDecimal() {
        if (event != Event.VALUE_NUMBER) {
            throw new IllegalStateException(event + " doesn't support getBigDecimal()");
        }

        if (currentBigDecimalNumber != null) {
            return currentBigDecimalNumber;
        }

        return (currentBigDecimalNumber = new BigDecimal(currentValue, 0, valueLength));
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

    private static long parseLongFromChars(final char[] chars, final int start, final int end) {

        long retVal = 0;
        final boolean negative = chars[start] == MINUS;
        for (int i = negative ? start + 1 : start; i < end; i++) {
            retVal = retVal * 10 + (chars[i] - ZERO);
        }
        return negative ? -retVal : retVal;
    }

}