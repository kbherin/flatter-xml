package com.karbherin.flatterxml;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Stack;

public class ScratchPad {
    @Test
    public void testStackAsIterable() {
        Stack<String> st = new Stack<>();
        st.push("One");
        st.push("Two");
        st.push("Three");
        StringBuffer buf = new StringBuffer();
        for (String item: st) {
            buf.append(item);
        }
        assertEquals("Stack iterable returns in order of insertion. BAD API", "OneTwoThree", buf.toString());
    }
}
