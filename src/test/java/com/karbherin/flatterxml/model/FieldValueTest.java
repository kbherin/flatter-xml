package com.karbherin.flatterxml.model;

import org.junit.Assert;
import org.junit.Test;

public class FieldValueTest {
    @Test
    public void test() {
        FieldValue<String,String> fv =new FieldValue<String,String>("Hello", "World");
        Assert.assertEquals("Hello", fv.getField());
        Assert.assertEquals("World", fv.getValue());
    }

    @Test
    public void testSetValue() {
        FieldValue<String,String> fv =new FieldValue<String,String>("Hello", "World");
        fv.setValue("People");
        Assert.assertEquals("People", fv.getValue());
    }
}
