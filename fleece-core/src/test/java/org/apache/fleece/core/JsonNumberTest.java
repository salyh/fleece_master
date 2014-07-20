package org.apache.fleece.core;

import java.math.BigInteger;

import javax.json.Json;
import javax.json.JsonArray;

import org.junit.Assert;
import org.junit.Test;



public class JsonNumberTest {
    
    @Test(expected=ArithmeticException.class)
    public void testBigIntegerExact() {
       
        JsonArray array = Json.createArrayBuilder().add(100.0200).build();
        array.getJsonNumber(0).bigIntegerValueExact();

       
    }
    
    @Test
    public void testBigInteger() {
       
        JsonArray array = Json.createArrayBuilder().add(100.0200).build();
        Assert.assertEquals(new BigInteger("100"), array.getJsonNumber(0).bigIntegerValue());

       
    }

}
