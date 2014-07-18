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

/*
 * Benchmark                                                       Mode   Samples        Score  Score error    Units
o.a.f.c.j.b.BenchmarkStreamParser.parseOnly1000kChars          thrpt         3       81,120        2,754    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.parseOnlyCombinedChars500    thrpt         3      161,820        7,520    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.read1000kChars               thrpt         3       47,277        4,318    ops/s
o.a.f.c.j.b.BenchmarkStreamParser.readCombinedChars500         thrpt         3       92,759       29,026    ops/s
 */

import static org.apache.fleece.core.Strings.asEscapedChar;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;

import javax.json.stream.JsonLocation;
import javax.json.stream.JsonParsingException;

public class CopyOfJsonStreamParserImpl implements JsonChars, EscapedStringAwareJsonParser {

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

    private int openObjects = 0;
    private int openArrays = 0;

    //private int avail;

    public CopyOfJsonStreamParserImpl(final Reader reader, final int maxStringLength,
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

        return !(openObjects == 0 && openArrays == 0) || event == null;

        //        if (!hasNext) {
        //           
        //            if (pointer < avail - 1) {
        //                final char c = readNextNonWhitespaceChar();
        //
        //                if (c > 0 && pointer < avail - 1) {
        //                    throw new JsonParsingException("unexpected hex value " + c, createLocation());
        //                }
        //
        //            }
        //
        //        }

        //return hasNext;
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

    protected void markCurrentChar() {
        mark = buffer[pointer];
    }

    protected void resetToLastMark() {
        reset = true;
        offset--;

        //        if (mark == EOL) {
        //            line--;
        //            column = -1; //we don't have this info
        //        }

    }

    private char read() {

        if (reset) {
            reset = false;
            return mark;
        }

        if (pointer == -1 || (buffer.length - pointer) <= 1) {
            try {
                in.read(buffer, 0, buffer.length);

                //                if (avail <= 0) {
                //                    throw new JsonParsingException("eof", createLocation());
                //                }

            } catch (final Exception e) {
                close();
                throw new JsonParsingException("Unexpected IO Exception", e, createLocation());

            }

            pointer = 0;

        } else {
            pointer++;
        }

        offset++;
        column++;

        return buffer[pointer];
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
                //                column = 0;
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
                if (lastSignificantChar != -2 && lastSignificantChar != I_QUOTE && lastSignificantChar != I_END_ARRAY_CHAR
                        && lastSignificantChar != I_END_OBJECT_CHAR) {
                    throw new JsonParsingException("Unexpected character " + c + " (last significant was " + lastSignificantChar + ")",
                            createLocation());
                }

                //                if (openObjects == 0 && openArrays == 0) {
                //                    throw new JsonParsingException("Unexpected character " + c, createLocation());
                //                }

                lastSignificantChar = c;

                return next();

            case START_OBJECT_CHAR:

                handleStartObject();

                break;

            case END_OBJECT_CHAR:

                handleEndObject();

                break;
            case START_ARRAY_CHAR:

                handleStartArray();

                break;
            case END_ARRAY_CHAR:

                handleEndArray();
                break;

            case QUOTE: // must be escaped within a value

                handleQuote();

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
            /*case EOF:

                throw new JsonParsingException("Unexpected eof", createLocation());//new NoSuchElementException(); //TODO*/

            default:
                throw new JsonParsingException("Unexpected character " + c, createLocation());

        }

        return event;

    }

    private void handleStartObject() {

        if (lastSignificantChar == -2
                || (lastSignificantChar != -1 && lastSignificantChar != I_KEY_SEPARATOR && lastSignificantChar != I_COMMA && lastSignificantChar != I_START_ARRAY_CHAR)) {
            throw new JsonParsingException("Unexpected character " + START_OBJECT_CHAR + " (last significant was " + lastSignificantChar
                    + ")", createLocation());
        }

        lastSignificantChar = START_OBJECT_CHAR;
        openObjects++;
        event = Event.START_OBJECT;

    }

    private void handleEndObject() {

        if (lastSignificantChar >= 0 && lastSignificantChar != I_START_OBJECT_CHAR && lastSignificantChar != I_END_ARRAY_CHAR
                && lastSignificantChar != I_QUOTE && lastSignificantChar != I_END_OBJECT_CHAR) {
            throw new JsonParsingException("Unexpected character " + END_OBJECT_CHAR + " (last significant was " + lastSignificantChar
                    + ")", createLocation());
        }

        if (openObjects == 0) {
            throw new JsonParsingException("Unexpected character " + END_OBJECT_CHAR, createLocation());
        }

        lastSignificantChar = END_OBJECT_CHAR;
        openObjects--;
        event = Event.END_OBJECT;
    }

    private void handleStartArray() {

        if (lastSignificantChar == -2
                || (lastSignificantChar != -1 && lastSignificantChar != I_KEY_SEPARATOR && lastSignificantChar != I_COMMA && lastSignificantChar != I_START_ARRAY_CHAR)) {
            throw new JsonParsingException("Unexpected character " + START_ARRAY_CHAR + " (last significant was " + lastSignificantChar
                    + ")", createLocation());
        }

        lastSignificantChar = START_ARRAY_CHAR;
        openArrays++;
        event = Event.START_ARRAY;
    }

    private void handleEndArray() {

        if (lastSignificantChar >= 0 && lastSignificantChar != I_START_ARRAY_CHAR && lastSignificantChar != I_END_ARRAY_CHAR
                && lastSignificantChar != I_END_OBJECT_CHAR && lastSignificantChar != I_QUOTE) {
            throw new JsonParsingException(
                    "Unexpected character " + END_ARRAY_CHAR + " (last significant was " + lastSignificantChar + ")", createLocation());
        }

        if (openArrays == 0) {
            throw new JsonParsingException("Unexpected character " + END_ARRAY_CHAR, createLocation());
        }

        lastSignificantChar = END_ARRAY_CHAR;
        openArrays--;

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

                //            } else if (n >= '\u0000' && n <= '\u001F') {
                //                throw new JsonParsingException("Unescaped control character ", createLocation());

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

    private void handleQuote() {

        if (lastSignificantChar >= 0 && lastSignificantChar != I_KEY_SEPARATOR && lastSignificantChar != I_START_OBJECT_CHAR
                && lastSignificantChar != I_START_ARRAY_CHAR && lastSignificantChar != I_COMMA) {
            throw new JsonParsingException("Unexpected character " + QUOTE + " (last significant was " + lastSignificantChar + ")",
                    createLocation());
        }

        //        if (openObjects == 0 && openArrays == 0) {
        //            throw new JsonParsingException("Unexpected character " + QUOTE, createLocation());
        //        }

        //always the beginning quote of a key or value

        lastSignificantChar = QUOTE;

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

        //        if (openObjects == 0 && openArrays == 0) {
        //            throw new JsonParsingException("Unexpected character " + c, createLocation());
        //        }

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

                final boolean dotpassed = false;
                final boolean epassed = false;
                char last = c;

                while (true) {

                    char n = read();

                    if (!isNumber(n)) {

                        if (n == SPACE || n == TAB || n == CR) {

                            n = readNextNonWhitespaceChar();

                        }

                        markCurrentChar();

                        if (n == COMMA || n == END_ARRAY_CHAR || n == END_OBJECT_CHAR || n == EOL) {

                            //                            if (last == EXP_LOWERCASE || last == EXP_UPPERCASE || last == MINUS || last == PLUS || last == DOT) {
                            //                                throw new JsonParsingException("unexpected character " + n + " (" + (int) n + ")", createLocation());
                            //                            } else {

                            resetToLastMark();

                            isCurrentNumberIntegral = (!dotpassed && !epassed);

                            if (isCurrentNumberIntegral && c == MINUS && valueLength < 3 && last >= '0' && last <= '9') {

                                currentIntegralNumber = -(last - 48); //optimize -0 till -9
                            } else if (isCurrentNumberIntegral && c != MINUS && valueLength < 2 && last >= '0' && last <= '9') {

                                currentIntegralNumber = (last - 48); //optimize 0 till 9
                            }

                            event = Event.VALUE_NUMBER;

                            break;
                            //                            }
                        } else {
                            throw new JsonParsingException("unexpected character " + n + " (" + (int) n + ")", createLocation());
                        }

                    }

                    //is one of 0-9 . e E - +

                    //                    // minus only allowed as first char or after e/E
                    //                    if (n == MINUS && valueLength > 0 && last != EXP_LOWERCASE && last != EXP_UPPERCASE) {
                    //                        throw new JsonParsingException("unexpected character " + n, createLocation());
                    //                    } else if (n == PLUS && last != EXP_LOWERCASE && last != EXP_UPPERCASE) {
                    //                     // plus only allowed after e/E
                    //                        throw new JsonParsingException("unexpected character " + n, createLocation());
                    //                    }
                    //
                    //                    //positive numbers
                    //                    if (c == ZERO && !dotpassed && valueLength > 0 && n != DOT) {
                    //                        throw new JsonParsingException("unexpected character " + n + " (no leading zeros allowed)", createLocation());
                    //                    } else  if (c == MINUS && !dotpassed &&  last == ZERO && n != DOT && valueLength <= 2) {
                    //                      //negative numbers
                    //                        throw new JsonParsingException("unexpected character " + n + " (no leading zeros allowed) ", createLocation());
                    //                    }
                    //
                    //                    if (n == DOT) {
                    //
                    //                        if (dotpassed) {
                    //                            throw new JsonParsingException("more than one dot", createLocation());
                    //                        }
                    //
                    //                        if (epassed) {
                    //                            throw new JsonParsingException("no dot allowed here", createLocation());
                    //                        }
                    //
                    //                        dotpassed = true;
                    //
                    //                    } else if (n == EXP_LOWERCASE || n == EXP_UPPERCASE) {
                    //
                    //                        if (epassed) {
                    //                            throw new JsonParsingException("more than one e/E", createLocation());
                    //                        }
                    //
                    //                        epassed = true;
                    //                    }

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
        } else {
            throw new IllegalStateException(event + " doesn't support getString()");
        }
    }

    @Override
    public boolean isIntegralNumber() {

        if (event != Event.VALUE_NUMBER) {
            throw new IllegalStateException(event + " doesn't support isIntegralNumber()");
        } else {
            return isCurrentNumberIntegral;
        }
    }

    @Override
    public int getInt() {
        if (event != Event.VALUE_NUMBER) {
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
        if (event != Event.VALUE_NUMBER) {
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
        if (event != Event.VALUE_NUMBER) {
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

}