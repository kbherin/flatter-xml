package com.karbherin.flatterxml.output;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DelimitedFileWriterTest {

    @Test
    public void testSplit() {
        String[] parts = "someval1|someval2##HEADER>#somekey1|somekey2".split("##HEADER>#");
        assertEquals("someval1|someval2", parts[0]);
        assertEquals("somekey1|somekey2", parts[1]);
    }
}
