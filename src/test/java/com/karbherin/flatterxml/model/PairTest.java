package com.karbherin.flatterxml.model;

import org.junit.Assert;
import org.junit.Test;

public class PairTest {
    @Test
    public void test() {
        Pair<String,String> fv =new Pair<String,String>("Hello", "World");
        Assert.assertEquals("Hello", fv.getKey());
        Assert.assertEquals("World", fv.getVal());
    }

    @Test
    public void testSetValue() {
        Pair<String,String> fv =new Pair<String,String>("Hello", "World");
        fv.setVal("People");
        Assert.assertEquals("People", fv.getVal());
    }
}
