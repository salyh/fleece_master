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


public class JsonStreamParserImpl implements JsonChars, EscapedStringAwareJsonParser {

    //TO DO
    //detect invalid surrogate pairs
    
    private final char[] buffer;
    private final Reader in;
    private final BufferStrategy.BufferProvider<char[]> bufferProvider;
    private final BufferStrategy.BufferProvider<char[]> valueProvider;
    private int pointer = -1;
    private char mark;
    private boolean reset;

    private final int maxStringSize;

   
    //private static final byte COLON_EVENT=Byte.MIN_VALUE;
    
    
    
    // current state
    private byte event = 0;
    //private int lastSignificantChar = -1;

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
    private int openObjectCount = 0;
    private int openArrayCount = 0;
    //private boolean lastOpneTagIsArray=false;
    

    public JsonStreamParserImpl(final Reader reader, final int maxStringLength, final BufferStrategy.BufferProvider<char[]> bufferProvider,
            final BufferStrategy.BufferProvider<char[]> valueBuffer) {

        this.maxStringSize = maxStringLength <= 0 ? 4096 : maxStringLength;
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

        if (openArrayCount > 0 || openObjectCount > 0 || (event != END_ARRAY && event != END_OBJECT) || event == 0) {
            //first event
            return true;
        }

   
            //check for garbage at the end of the file
            //System.out.println("maybe end avail:"+avail+"/pointer:"+pointer+" /buflen+"+buffer.length+" -> ");
            if (pointer < avail - 2) {

                final char c = readNextNonWhitespaceChar();

                //System.out.println("read until next non ws "+c);
                //System.out.println("after ws "+avail+"/pointer:"+pointer+" /buflen+"+buffer.length+" -> ");
                if (c == 0) {
                    return false;
                }

                if (c != COMMA && c != END_ARRAY_CHAR && c != END_OBJECT_CHAR) {
                    throw new JsonParsingException("unexpected hex value " + c, createLocation());
                }

            }

            return false;
            /*
            
            //System.out.print("hasNext() avail:"+avail+"/pointer:"+pointer+" /buflen+"+buffer.length+" -> ");
            boolean retval= ((avail>pointer+2)  || event == null || avail==buffer.length);
            //System.out.println(retval);
            return retval;*/
        

    }

    private static boolean isAsciiDigit(final char value) {
        return value <= NINE && value >= ZERO;
    }

    private int parseHexDigit(final char value) {
       
        if (isAsciiDigit(value)){
            return (int)value-48;
        }else if (value <= 'f' && value >= 'a'){
            return ((int)value)-87;
    }else if((value <= 'F' && value >= 'A')){
            return ((int)value)-55;
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
            //System.out.println(" RETURN mark "+mark);
            return mark;
        }

        if (pointer == -1 || (buffer.length - pointer) <= 1) {
            try {
                avail = in.read(buffer, 0, buffer.length);
                //System.out.println("fillbuff "+avail+": "+new String(buffer));
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
        //System.out.println("read('"+buffer[pointer]+"') avail:"+avail+"/pointer:"+pointer+"/buflen:"+buffer.length);
        return buffer[pointer];

    }

    protected void markCurrentChar() {
        mark = buffer[pointer];
    }

    protected void resetToLastMark() {
        reset = true;
        offset--;
        
        if (mark == EOL) {
            line--;
            column = -1; //we dont have this info
        }
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
System.out.println((char)c+" -->"+c);
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
                column=0;
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

        //event = null;
        if (isCurrentNumberIntegral) {
            isCurrentNumberIntegral = false;
        }
        if (currentBigDecimalNumber != null) {
            currentBigDecimalNumber = null;
        }
        if (currentIntegralNumber != null) {
            currentIntegralNumber = null;
        }

        if(valueLength != 0) {
            valueLength = 0;
        }
        

        final char c = readNextNonWhitespaceChar();

        switch (c) {

            case COMMA:

                //lastSignificantChar must one of the following-> " ] } LITERAL
                if (event == START_ARRAY || event == START_OBJECT || event == COMMA_EVENT || event == KEY_NAME) {
                    throw new JsonParsingException("Unexpected character " + c + " (last significant was " + event + ")",
                            createLocation());
                }
                
                
                
                if (event == VALUE_FALSE || event==VALUE_NULL || event==VALUE_TRUE || event==VALUE_NUMBER || event==VALUE_STRING)  {
                   
                    //this is only only allowed in an array
                    
                    //so if we are not in array context we throw an exception
                    
                }
                
                //if (structCount <= 0) {
                //    throw new JsonParsingException("Unexpected character " + c, createLocation());
                //}

                event = COMMA_EVENT;
                //lastSignificantChar = c;

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

        //System.out.println(" --> "+event+" ->'"+getValue()+"'");
        return EVT_MAP[event];

        //throw new JsonParsingException("Unexpected character " + c, createLocation());

    }

    private void handleStartObject(final char c) {

        //lastSignificantChar must one of the following-> : , [
        if (event!=0&&event != KEY_NAME && event!=START_ARRAY && event!=COMMA_EVENT) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + event + ")",
                    createLocation());
        }

        openObjectCount++;
        
  
   

        event = START_OBJECT;

    }

    private void handleEndObject(final char c) {

        //lastSignificantChar must one of the following-> " ] { } LITERAL
        if (event == START_ARRAY || event == COMMA_EVENT) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + event + ")",
                    createLocation());
        }

        openObjectCount--;
        
     
            
        
        //lastSignificantChar = c;

        event = END_OBJECT;
    }

    private void handleStartArray(final char c) {

      //lastSignificantChar must one of the following-> : , [
        if (event!=0&&event != KEY_NAME && event != START_ARRAY&& event!=COMMA_EVENT) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + event + ")",
                    createLocation());
        }

     
        
        openArrayCount++;
        //lastSignificantChar = c;

        event = START_ARRAY;
    }

    private void handleEndArray(final char c) {

        //lastSignificantChar must one of the following-> [ ] } " LITERAL
        if (event==START_OBJECT|| event == COMMA_EVENT) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " + event + ")",
                    createLocation());
        }

        openArrayCount--;
       
        //lastSignificantChar = c;

        event = END_ARRAY;
    }

    private void readString() {

        boolean esc = false;
        char highSurrogate = 0;

        while (true) {
            char n = read();

            if (n == ESCAPE_CHAR) {
                if (esc) {
                    esc = false;
                    appendValue(ESCAPE_CHAR);
                    continue;

                } else {
                    esc = true;
                }
            } else if (!esc && n == QUOTE) {
                break;
            } else if (n == EOL) {
                throw new JsonParsingException("Unexpected linebreak ", createLocation());
                
            } else if (n >= '\u0000' && n <= '\u001F') {
                throw new JsonParsingException("Unescaped control character ", createLocation());


            } else {

                
                if (esc) {
                    if (n == 'u') {
                        n = parseUnicodeHexChars();
                    } else {
                        n= (asEscapedChar(n));
                    }

                    esc = false;

                } 
                
                //System.out.println("r codepoint: "+String.valueOf(n).codePointAt(0));
                
                //high followed by low
                if(Character.isHighSurrogate(n)) {
                    
                    if(highSurrogate!=0) throw new JsonParsingException("unexpected high surrogate "+(int)n, null);
                    
                    highSurrogate = n;
                    //System.out.println("hs: "+(int)n);
                } 
                else if(Character.isLowSurrogate(n)) {
                    //System.out.println("ls: "+(int)n);
                    if(highSurrogate==0) throw new JsonParsingException("unexpected low surrogate "+(int)n, null);
                    else if(!Character.isSurrogatePair(highSurrogate, n)) throw new JsonParsingException("invalid surrogate pair", null);
                  
                    highSurrogate=0;
                }else if(highSurrogate!=0 && !Character.isLowSurrogate(n)) {
                    throw new JsonParsingException("expected low surrogate missing "+(int)n, null);
                }
                
                appendValue(n);
                
                

            }

        }//end while()

    }

    private char parseUnicodeHexChars() {       
        // \u08Ac etc       
        return (char) (((parseHexDigit(read())) * 4096) + ((parseHexDigit(read())) * 256)
                + ((parseHexDigit(read())) * 16) + ((parseHexDigit(read()))));


    }

    private void handleQuote(final char c) {

      //lastSignificantChar must one of the following-> : { [ ,
        if (event != KEY_NAME && event != START_OBJECT && event!=START_ARRAY&& event!=COMMA_EVENT) {
            throw new JsonParsingException("Unexpected character " + c + " (last significant was " +event + ")",
                    createLocation());
        }

        
        
        //always the beginning quote of a key or value

        //lastSignificantChar = c;

        readString();
        final char n = readNextNonWhitespaceChar();

        markCurrentChar();

        if (n == KEY_SEPARATOR) {

            event = KEY_NAME;
            //lastSignificantChar = n;

        } else {

           /* if(event==COMMA_EVENT) {
                //only allowed within array
                if(openArrayCount == 0 || openArrayCount%2==0) {
                    throw new JsonParsingException("Unexpected character " + c + " (last significant was " + event + ")",
                            createLocation());
                    }
                
            }*/
            
            
            
            resetToLastMark();
            event = VALUE_STRING;
        }
        
        /**
         * if(openArrayCount <= openObjectCount) {            
            //not directly in an array
            
            if (event!= KEY_NAME && event!=START_ARRAY&& event!=COMMA_EVENT) {
                throw new JsonParsingException("unexpected character " + c + " last significant " + event,
                        createLocation());
            }
            
        }
         */

    }

    private void handleLiteral(final char c) {
        
        //lastSignificantChar must one of the following-> : , [
        if (event!= KEY_NAME && event!=START_ARRAY&& event!=COMMA_EVENT) {
            throw new JsonParsingException("unexpected character " + c + " last significant " + event,
                    createLocation());
        }
        
        /*
        if(event==COMMA_EVENT) {
            //only allowed within array
            if(openArrayCount == 0 || openArrayCount%2==0) {
                throw new JsonParsingException("Unexpected character " + c + " (last significant was " + event + ")",
                        createLocation());
                }
            
        }*/

        
        
        //lastSignificantChar = -2;

        // probe literals
        switch (c) {
            case TRUE_T:

                if (read() != TRUE_R || read() != TRUE_U || read() != TRUE_E) {
                    throw new JsonParsingException("Unexpected literal ", createLocation());
                }
                event = VALUE_TRUE;
                break;
            case FALSE_F:

                if (read() != FALSE_A || read() != FALSE_L || read() != FALSE_S || read() != FALSE_E) {
                    throw new JsonParsingException("Unexpected literal ", createLocation());
                }

                event = VALUE_FALSE;
                break;
            case NULL_N:

                if (read() != NULL_U || read() != NULL_L || read() != NULL_L) {
                    throw new JsonParsingException("Unexpected literal ", createLocation());
                }
                event = VALUE_NULL;
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
                            
                            if (last == EXP_LOWERCASE || last == EXP_UPPERCASE || last == MINUS || last == PLUS || last == DOT) {
                                throw new JsonParsingException("unexpected character " + n + " (" + (int) n + ")", createLocation());
                            } else 
                            {
                            
                            resetToLastMark();

                            isCurrentNumberIntegral = (!dotpassed && !epassed);

                            if (isCurrentNumberIntegral && c == MINUS && valueLength < 3 && last >= '0' && last <= '9') {

                                currentIntegralNumber = -(last - 48); //optimize -0 till -9
                            }

                            if (isCurrentNumberIntegral && c != MINUS && valueLength < 2 && last >= '0' && last <= '9') {

                                currentIntegralNumber = (last - 48); //optimize 0 till 9
                            }

                            event = VALUE_NUMBER;

                            break;
                            }
                        } else {
                            throw new JsonParsingException("unexpected character " + n + " (" + (int) n + ")", createLocation());
                        }

                    }

                    //is one of 0-9 . e E - +

                    // minus only allowed as first char or after e/E
                    if (n == MINUS && valueLength > 0 && last != EXP_LOWERCASE && last != EXP_UPPERCASE) {
                        throw new JsonParsingException("unexpected character " + n, createLocation());
                    } else if (n == PLUS && last != EXP_LOWERCASE && last != EXP_UPPERCASE) {
                     // plus only allowed after e/E
                        throw new JsonParsingException("unexpected character " + n, createLocation());
                    }

                  //positive numbers
                    if (!dotpassed && c == ZERO && valueLength > 0 && n != DOT) {
                        throw new JsonParsingException("unexpected character " + n + " (no leading zeros allowed)", createLocation());
                    } else  if (!dotpassed && c == MINUS && last == ZERO && n != DOT && valueLength <= 2) {
                      //negative numbers
                        throw new JsonParsingException("unexpected character " + n + " (no leading zeros allowed) ", createLocation());
                    }

                    if (n == DOT) {

                        if (dotpassed) {
                            throw new JsonParsingException("more than one dot", createLocation());
                        }

                        if (epassed) {
                            throw new JsonParsingException("no dot allowed here", createLocation());
                        }

                        dotpassed = true;

                    } else if (n == EXP_LOWERCASE || n == EXP_UPPERCASE) {

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
        return  c == DOT || c == MINUS || c == PLUS || c == EXP_LOWERCASE || c == EXP_UPPERCASE || isAsciiDigit(c);
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
/*
    
    
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
*/
}