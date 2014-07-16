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

public class JsonStreamParserImpl_89_184_47_104 implements JsonChars, EscapedStringAwareJsonParser {
    
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

    public JsonStreamParserImpl_89_184_47_104(final Reader reader, final int maxStringLength, final BufferStrategy.BufferProvider<char[]> bufferProvider,
            final BufferStrategy.BufferProvider<char[]> valueBuffer) {

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

    private void resetValue() {
        valueLength = 0;

    }

    private String getValue() {
        return new String(currentValue, 0, valueLength);
    }

    @Override
    public final boolean hasNext() {
        return !(openObjects == 0 && openArrays == 0) || event == null;
    }

    private static boolean isAsciiDigit(final char value) {
        return value >= ZERO && value <= NINE;
    }

    private static boolean isHexDigit(final char value) {
        return isAsciiDigit(value) || (value >= 'a' && value <= 'f') || (value >= 'A' && value <= 'F');
    }

    private JsonLocationImpl createLocation() {
        return new JsonLocationImpl(line, column, offset);
    }

    protected int readNextChar() {
        if (reset) {
            reset = false;
            return mark;
        }

        if (pointer == -1 || (buffer.length - pointer) <= 1) {
            try {
                final int avail = in.read(buffer, 0, buffer.length);

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

        //if (LOG) {
        //System.out.println("reading: " + (char)c + " -> " + (c));
        //}

        if (c == -1) {

            //TODO
            throw new JsonParsingException("eof", createLocation());
            //NoSuchElementException();
        }

        offset++;
        column++;

        return (char) c;
    }

    private char[] read(final int count) {
        final char[] tmp = new char[count];

        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = read();

        }

        return tmp;
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
        if(isCurrentNumberIntegral) isCurrentNumberIntegral = false;
        if(currentBigDecimalNumber !=null) currentBigDecimalNumber = null;
        if(currentIntegralNumber != null)currentIntegralNumber = null;

        resetValue();

        final char c = readNextNonWhitespaceChar();

        switch (c) {

            case COMMA:
                final char lastSignificant = (char) lastSignificantChar;
                if (lastSignificantChar >= 0 && lastSignificant != QUOTE && lastSignificant != END_ARRAY_CHAR
                        && lastSignificant != END_OBJECT_CHAR) {
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

        if (event != null) {
            //System.out.println(" --> "+event+" ->'"+getValue()+"'");
            return event;
        }

        throw new JsonParsingException("Unexpected character " + c, createLocation());

    }

    private void handleStartObject(final char c) {
        final char significantChar = (char) lastSignificantChar; // cast eagerly means we avoid 2 castings and are slwoer in error case only
        if (lastSignificantChar == -2
                || (lastSignificantChar != -1 && significantChar != KEY_SEPARATOR && significantChar != COMMA && significantChar != START_ARRAY_CHAR)) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + lastSignificantChar + ")",
                    createLocation());
        }

        lastSignificantChar = c;
        openObjects++;
        event = Event.START_OBJECT;

    }

    private void handleEndObject(final char c) {
        final char significantChar = (char) lastSignificantChar;
        if (lastSignificantChar >= 0 && significantChar != START_OBJECT_CHAR && significantChar != END_ARRAY_CHAR
                && significantChar != QUOTE && significantChar != END_OBJECT_CHAR) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + lastSignificantChar + ")",
                    createLocation());
        }

        if (openObjects == 0) {
            throw new JsonParsingException("Unexpected character " + c, createLocation());
        }

        lastSignificantChar = c;
        openObjects--;
        event = Event.END_OBJECT;
    }

    private void handleStartArray(final char c) {

        final char significantChar = (char) lastSignificantChar;
        if (lastSignificantChar == -2
                || (lastSignificantChar != -1 && significantChar != KEY_SEPARATOR && significantChar != COMMA && significantChar != START_ARRAY_CHAR)) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + lastSignificantChar + ")",
                    createLocation());
        }

        lastSignificantChar = c;
        openArrays++;
        event = Event.START_ARRAY;
    }

    private void handleEndArray(final char c) {

        final char significantChar = (char) lastSignificantChar;
        if (lastSignificantChar >= 0 && significantChar != START_ARRAY_CHAR && significantChar != END_ARRAY_CHAR
                && significantChar != END_OBJECT_CHAR && significantChar != QUOTE) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + lastSignificantChar + ")",
                    createLocation());
        }

        if (openArrays == 0) {
            throw new JsonParsingException("Unexpected character " + c, createLocation());
        }

        lastSignificantChar = c;
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

        final char[] tmp = read(4);

        for (final char aTmp : tmp) {
            if (!isHexDigit(aTmp)) {
                throw new JsonParsingException("unexpected character " + aTmp, createLocation());
            }
        }

        final int decimal = ((tmp[3]) - 48) + ((tmp[2]) - 48) * 16 + ((tmp[1]) - 48) * 256 + ((tmp[0]) - 48) * 4096;
        final char c = (char) decimal;
        appendValue(c);

    }

    private void handleQuote(final char c) {

        //System.out.println("detect quote, last sig "+(char) lastSignificantChar);

        final char significantChar = (char) lastSignificantChar;
        if (lastSignificantChar >= 0 /*&& significantChar != QUOTE*/&& significantChar != KEY_SEPARATOR
                && significantChar != START_OBJECT_CHAR && significantChar != START_ARRAY_CHAR && significantChar != COMMA) {
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
        if (lastSignificantChar >= 0 && lastSignificantChar != KEY_SEPARATOR && lastSignificantChar != COMMA
                && lastSignificantChar != START_ARRAY_CHAR) {
            throw new JsonParsingException("unexpected character " + c + " last significant " + (char) lastSignificantChar,
                    createLocation());
        }

        lastSignificantChar = -2;

     
            // probe literals
            switch (c) {
                case TRUE_T:
                    final char[] tmpt = read(3);
                    if (tmpt[0] != TRUE_R || tmpt[1] != TRUE_U || tmpt[2] != TRUE_E) {
                        throw new JsonParsingException("Unexpected literal " + c + new String(tmpt), createLocation());
                    }
                    event = Event.VALUE_TRUE;
                    break;
                case FALSE_F:
                    final char[] tmpf = read(4);
                    if (tmpf[0] != FALSE_A || tmpf[1] != FALSE_L || tmpf[2] != FALSE_S || tmpf[3] != FALSE_E) {
                        throw new JsonParsingException("Unexpected literal " + c + new String(tmpf), createLocation());
                    }

                    event = Event.VALUE_FALSE;
                    break;
                case NULL_N:
                    final char[] tmpn = read(3);
                    if (tmpn[0] != NULL_U || tmpn[1] != NULL_L || tmpn[2] != NULL_L) {
                        throw new JsonParsingException("Unexpected literal " + c + new String(tmpn), createLocation());
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
                            
                            if(n== SPACE || n==TAB || n==CR) {
                                
                                n = readNextNonWhitespaceChar();
                
                            }
                            

                            markCurrentChar();
                            
                            if (n == COMMA || n == END_ARRAY_CHAR || n == END_OBJECT_CHAR || n == EOL) {
                                resetToLastMark();

                                isCurrentNumberIntegral = (!dotpassed && !epassed);

                                if (isCurrentNumberIntegral && c == MINUS && valueLength < 3 && last >= '0' && last <= '9') {
                       
                                    currentIntegralNumber = -(last - 48); //optimize -0 till -9
                                }

                                if (isCurrentNumberIntegral && c != MINUS  && valueLength < 2 && last >= '0' && last <= '9') {
                                    
                                    currentIntegralNumber = (last - 48); //optimize 0 till 9
                                }

                                event = Event.VALUE_NUMBER;
                     
                                break;
                            }else 
                            {
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

                        if (!dotpassed && c==ZERO && valueLength > 0  && n != DOT) {
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

        return (currentBigDecimalNumber = new BigDecimal(getString()));
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